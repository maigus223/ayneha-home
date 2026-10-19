// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 MAIGUS

package com.maigus.ayneha.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gère le site "hors-ligne d'abord" :
 *  1) au premier lancement, copie le site embarqué dans les assets de l'APK
 *     (app/src/main/assets/site/) vers le stockage interne de l'app — c'est
 *     cette copie qui est servie au WebView, jamais les assets directement.
 *  2) quand il y a du réseau, tente de télécharger une version plus récente
 *     depuis le site en ligne, dans un dossier temporaire, et ne remplace la
 *     copie locale que si TOUT le téléchargement a réussi (tout ou rien).
 *
 * En cas de mauvaise connexion (coupure en cours de téléchargement, timeout,
 * etc.), la copie locale existante n'est jamais touchée : l'app continue de
 * fonctionner avec la dernière version valide dont elle dispose.
 */
object SiteRepository {

    // Racine du site en ligne. Doit contenir un fichier manifest.json à jour
    // (voir tools/generate_manifest.py) listant tous les fichiers du site.
    // Quand le domaine ayneha.org sera relié, cette adresse redirige vers lui :
    // HttpURLConnection suit la redirection (https vers https).
    const val REMOTE_BASE_URL = "https://ayneha-songhay.github.io/"
    private const val MANIFEST_NAME = "manifest.json"
    private const val VERSION_FILE = "version.txt"

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    fun localSiteDir(context: Context): File = File(context.filesDir, "site")

    /**
     * Copie les assets embarqués vers le stockage interne si c'est le premier
     * lancement, ou si la version embarquée dans l'APK (mise à jour de l'app
     * via le Play Store, une clé USB, etc.) est plus récente que ce qui est
     * déjà installé localement. Ne touche à rien si la copie locale existante
     * est déjà à jour ou plus récente (ex. mise à jour réseau déjà appliquée).
     */
    suspend fun ensureBootstrapped(context: Context) = withContext(Dispatchers.IO) {
        val localDir = localSiteDir(context)
        val localVersion = readVersion(File(localDir, VERSION_FILE))
        val bundledVersion = readAssetVersion(context)

        val needsBootstrap = !localDir.exists() ||
                localDir.listFiles()?.isEmpty() != false ||
                (bundledVersion != null && (localVersion == null || bundledVersion > localVersion))

        if (needsBootstrap) {
            val tmp = File(context.filesDir, "site_bootstrap_tmp")
            tmp.deleteRecursively()
            tmp.mkdirs()
            copyAssetDir(context, "site", tmp)
            swapInto(localDir, tmp)
        }
    }

    sealed class UpdateResult {
        object UpToDate : UpdateResult()
        object Updated : UpdateResult()
        data class Failed(val reason: String) : UpdateResult()
    }

    /**
     * Tente une mise à jour complète depuis le réseau. Ne lève jamais
     * d'exception vers l'appelant : tout échec redescend en [UpdateResult.Failed]
     * et la copie locale existante reste intacte.
     */
    suspend fun tryUpdateFromNetwork(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val manifestJson = fetchText("$REMOTE_BASE_URL$MANIFEST_NAME")
                ?: return@withContext UpdateResult.Failed("manifest introuvable")

            val manifest = JSONObject(manifestJson)
            val remoteVersion = manifest.optString("version", "")
            val files = manifest.optJSONArray("files") ?: return@withContext UpdateResult.Failed("manifest invalide")

            val localVersion = readVersion(File(localSiteDir(context), VERSION_FILE))
            // On ne télécharge que si le site en ligne est PLUS RÉCENT que la copie
            // locale (versions = horodatages AAAAMMJJhhmmss). Ainsi, une copie
            // embarquée plus récente que le site en ligne n'est jamais remplacée
            // par une version plus ancienne.
            if (remoteVersion.isNotEmpty() && localVersion != null && remoteVersion <= localVersion) {
                return@withContext UpdateResult.UpToDate
            }

            val tmp = File(context.filesDir, "site_update_tmp")
            tmp.deleteRecursively()
            tmp.mkdirs()

            for (i in 0 until files.length()) {
                val relativePath = files.getString(i)
                val ok = downloadFile("$REMOTE_BASE_URL$relativePath", File(tmp, relativePath))
                if (!ok) {
                    tmp.deleteRecursively()
                    return@withContext UpdateResult.Failed("échec sur $relativePath")
                }
            }

            // Toujours écrire la version reçue, même si le manifest ne la
            // fournit pas explicitement (fallback: horodatage).
            File(tmp, VERSION_FILE).writeText(
                remoteVersion.ifEmpty { System.currentTimeMillis().toString() }
            )

            swapInto(localSiteDir(context), tmp)
            UpdateResult.Updated
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: "erreur inconnue")
        }
    }

    // --- Aide interne ---------------------------------------------------

    private fun readVersion(file: File): String? =
        if (file.exists()) file.readText().trim().ifEmpty { null } else null

    private fun readAssetVersion(context: Context): String? = try {
        context.assets.open("site/$VERSION_FILE").bufferedReader().use { it.readText().trim() }
    } catch (e: Exception) {
        null
    }

    private fun copyAssetDir(context: Context, assetPath: String, destDir: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            // Fichier feuille
            destDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destDir.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        destDir.mkdirs()
        for (child in children) {
            copyAssetDir(context, "$assetPath/$child", File(destDir, child))
        }
    }

    private fun swapInto(liveDir: File, newDir: File) {
        val backup = File(liveDir.parentFile, "${liveDir.name}_old")
        backup.deleteRecursively()
        if (liveDir.exists()) liveDir.renameTo(backup)
        val renamed = newDir.renameTo(liveDir)
        if (!renamed) {
            // Repli : si le renommage échoue (ex. partitions différentes),
            // on copie manuellement puis on nettoie.
            newDir.copyRecursively(liveDir, overwrite = true)
            newDir.deleteRecursively()
        }
        backup.deleteRecursively()
    }

    private fun fetchText(urlStr: String): String? = try {
        (URL(urlStr).openConnection() as HttpURLConnection).run {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            useCaches = false
            connect()
            if (responseCode == 200) {
                inputStream.bufferedReader().use { it.readText() }
            } else null
        }
    } catch (e: Exception) {
        null
    }

    private fun downloadFile(urlStr: String, destFile: File): Boolean = try {
        (URL(urlStr).openConnection() as HttpURLConnection).run {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            connect()
            if (responseCode == 200) {
                destFile.parentFile?.mkdirs()
                inputStream.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                true
            } else false
        }
    } catch (e: Exception) {
        false
    }
}
