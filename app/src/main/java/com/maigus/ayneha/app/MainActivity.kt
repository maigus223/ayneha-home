// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 MAIGUS

package com.maigus.ayneha.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var assetLoader: WebViewAssetLoader

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var updateInProgress = false

    companion object {
        // Le site est toujours servi localement, jamais depuis le réseau
        // directement. Ce domaine "virtuel" est celui utilisé par
        // WebViewAssetLoader pour mapper vers le stockage interne.
        private const val LOCAL_DOMAIN = "appassets.androidplatform.net"
        private const val START_URL = "https://$LOCAL_DOMAIN/site/index.html"

        // Fichiers volontairement absents de la copie locale (trop lourds) :
        // ils sont ouverts dans le navigateur, depuis le site en ligne.
        private const val DOSSIER_TELECHARGEMENTS = "/site/assets/telechargements/"
        private val EXTENSIONS_A_TELECHARGER = listOf(".pdf", ".apk", ".zip", ".ttf", ".otf", ".docx", ".aar")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)

        assetLoader = WebViewAssetLoader.Builder()
            .setDomain(LOCAL_DOMAIN)
            .addPathHandler(
                "/site/",
                WebViewAssetLoader.InternalStoragePathHandler(this, SiteRepository.localSiteDir(this))
            )
            .build()

        setupWebView()
        setupSwipeRefresh()
        setupNetworkCallback()
        setupBackButton()

        findViewById<TextView>(R.id.btn_info).setOnClickListener {
            startActivity(Intent(this, PrivacyActivity::class.java))
        }

        // 1) On s'assure que le site embarqué dans l'APK est copié en local
        //    (disponible dès l'installation, sans réseau), puis on l'affiche.
        // 2) On tente ensuite silencieusement une mise à jour si du réseau
        //    est déjà disponible au lancement.
        lifecycleScope.launch {
            SiteRepository.ensureBootstrapped(this@MainActivity)
            webView.loadUrl(START_URL)

            if (isNetworkAvailable()) {
                attemptBackgroundUpdate(showToastOnSuccess = false)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = false // le site est servi via WebViewAssetLoader, pas file://
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.setSupportZoom(true)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mediaPlaybackRequiresUserGesture = false

        // Le site est désormais toujours servi localement : ce mode de cache
        // HTTP ne sert plus qu'à d'éventuelles ressources externes,
        // il n'est plus la source de vérité pour le hors-ligne.
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                swipeRefresh.isRefreshing = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false

                // Fichiers à télécharger (PDF, police, APK...) : ils ne sont pas
                // dans la copie locale, on les ouvre depuis le site en ligne
                // dans le navigateur, qui sait les télécharger.
                if (uri.host == LOCAL_DOMAIN && estATelecharger(uri)) {
                    ouvrirDehors(versSiteEnLigne(uri))
                    return true
                }

                // Pages du site local (servies via WebViewAssetLoader) :
                // on garde la navigation à l'intérieur de l'application.
                if (uri.host == LOCAL_DOMAIN) {
                    return false
                }

                // E-mail : ACTION_SENDTO ouvre directement l'application de messagerie.
                if (uri.scheme == "mailto") {
                    ouvrirDehors(uri, Intent.ACTION_SENDTO)
                    return true
                }

                // Tout le reste (site externe, WhatsApp, e-mail, store,
                // blog...) s'ouvre avec l'application par défaut de
                // l'utilisateur (navigateur, WhatsApp, client mail, etc.)
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (e: ActivityNotFoundException) {
                    // Aucune application capable d'ouvrir ce lien : on ne
                    // fait rien plutôt que de faire planter l'app.
                    true
                }
            }
        }

        // Lien de téléchargement que la WebView ne sait pas traiter : navigateur.
        webView.setDownloadListener { url, _, _, _, _ ->
            val uri = Uri.parse(url)
            when {
                uri.scheme == "blob" || uri.scheme == "data" -> Unit // géré par le pont AynehaApp
                uri.host == LOCAL_DOMAIN -> ouvrirDehors(versSiteEnLigne(uri))
                else -> ouvrirDehors(uri)
            }
        }

        // Pont JavaScript : le site s'en sert (s'il existe) pour enregistrer ou
        // partager les images qu'il fabrique (carte, texte en AYNEHA), car une
        // WebView ne sait pas télécharger un « blob ».
        webView.addJavascriptInterface(PontImages(), "AynehaApp")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress >= 100) {
                    progressBar.visibility = View.GONE
                } else {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun estATelecharger(uri: Uri): Boolean {
        val chemin = uri.path ?: return false
        if (chemin.startsWith(DOSSIER_TELECHARGEMENTS)) return true
        val bas = chemin.lowercase()
        return EXTENSIONS_A_TELECHARGER.any { bas.endsWith(it) }
    }

    /** https://appassets.../site/x/y.pdf  ->  https://ayneha-songhay.github.io/x/y.pdf */
    private fun versSiteEnLigne(uri: Uri): Uri {
        val chemin = (uri.path ?: "/").removePrefix("/site/").removePrefix("/")
        return Uri.parse(SiteRepository.REMOTE_BASE_URL + chemin)
    }

    private fun ouvrirDehors(uri: Uri, action: String = Intent.ACTION_VIEW) {
        try {
            startActivity(Intent(action, uri))
        } catch (e: ActivityNotFoundException) {
            if (action != Intent.ACTION_VIEW) {
                try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) { }
            }
        }
    }

    /** Méthodes appelées par le JavaScript du site (window.AynehaApp). */
    inner class PontImages {

        private fun octets(dataUrl: String): ByteArray? = try {
            val virgule = dataUrl.indexOf(',')
            if (!dataUrl.startsWith("data:image/png") || virgule < 0) null
            else Base64.decode(dataUrl.substring(virgule + 1), Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }

        private fun nomPropre(nom: String): String {
            val n = nom.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
            return if (n.endsWith(".png")) n else "$n.png"
        }

        /** true : enregistrerImage range l'image dans Pictures/AYNEHA (Android 10+) ;
         *  false : elle ouvre le partage Android, où l'on choisit où l'enregistrer. */
        @JavascriptInterface
        fun enregistreDansGalerie(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        /** Enregistre l'image dans Pictures/AYNEHA. Renvoie true si c'est fait. */
        @JavascriptInterface
        fun enregistrerImage(dataUrl: String, nom: String): Boolean {
            val donnees = octets(dataUrl) ?: return false
            val fichier = nomPropre(nom)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val valeurs = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fichier)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AYNEHA")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, valeurs)
                        ?: return false
                    contentResolver.openOutputStream(uri)?.use { it.write(donnees) } ?: return false
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, getString(R.string.image_enregistree), Toast.LENGTH_SHORT).show()
                    }
                    true
                } else {
                    // Android 7 à 9 : enregistrer dans la galerie demanderait une
                    // permission ; on ouvre plutôt le partage (« Enregistrer », Drive...).
                    partager(donnees, fichier, "")
                }
            } catch (e: Exception) {
                false
            }
        }

        /** Ouvre le partage Android avec l'image. Renvoie true si c'est fait. */
        @JavascriptInterface
        fun partagerImage(dataUrl: String, nom: String, texte: String): Boolean {
            val donnees = octets(dataUrl) ?: return false
            return partager(donnees, nomPropre(nom), texte)
        }

        private fun partager(donnees: ByteArray, nom: String, texte: String): Boolean = try {
            val dossier = File(cacheDir, "partage").apply { mkdirs() }
            dossier.listFiles()?.forEach { it.delete() }
            val fichier = File(dossier, nom)
            fichier.writeBytes(donnees)
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fichiers", fichier)
            val envoi = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                if (texte.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, texte)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runOnUiThread {
                try {
                    startActivity(Intent.createChooser(envoi, getString(R.string.partager_image)))
                } catch (_: ActivityNotFoundException) { }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            resources.getColor(R.color.ayneha_primary, theme)
        )
        swipeRefresh.setOnRefreshListener {
            if (isNetworkAvailable()) {
                lifecycleScope.launch {
                    attemptBackgroundUpdate(showToastOnSuccess = true, showToastOnFailure = true)
                    swipeRefresh.isRefreshing = false
                }
            } else {
                swipeRefresh.isRefreshing = false
                Toast.makeText(this, getString(R.string.offline_message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Télécharge une éventuelle nouvelle version du site en arrière-plan.
     * Ne remplace la copie locale que si le téléchargement est intégralement
     * réussi ; en cas d'échec (mauvaise connexion), l'app continue avec la
     * dernière version valide déjà installée, sans interruption pour
     * l'utilisateur.
     */
    private suspend fun attemptBackgroundUpdate(
        showToastOnSuccess: Boolean,
        showToastOnFailure: Boolean = false
    ) {
        if (updateInProgress) return
        updateInProgress = true
        try {
            when (val result = SiteRepository.tryUpdateFromNetwork(this)) {
                is SiteRepository.UpdateResult.Updated -> {
                    webView.loadUrl(START_URL)
                    if (showToastOnSuccess) {
                        Toast.makeText(this, getString(R.string.connection_restored), Toast.LENGTH_SHORT).show()
                    }
                }
                is SiteRepository.UpdateResult.UpToDate -> {
                    // Rien à faire, la copie locale est déjà la plus récente.
                }
                is SiteRepository.UpdateResult.Failed -> {
                    if (showToastOnFailure) {
                        Toast.makeText(this, getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } finally {
            updateInProgress = false
        }
    }

    private fun setupNetworkCallback() {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    lifecycleScope.launch {
                        attemptBackgroundUpdate(showToastOnSuccess = true)
                    }
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback?.let {
            connectivityManager.registerNetworkCallback(request, it)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onDestroy() {
        try {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {
        }

        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
