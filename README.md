# AYNEHA Home - Application Android (version 2.0.0)

Application Android officielle pour le site **AYNEHA - L'alphabet Songhay**  
URL : https://ayneha-songhay.github.io/

## Fonctionnalités

- Démarre toujours sur la page **Accueil**
- Icône personnalisable
- Écran de démarrage (splash)
- **Mode hors-ligne** : le contenu déjà visité reste accessible
- Dès que la connexion internet revient, l’application recharge automatiquement les mises à jour
- Pull-to-refresh (tirer vers le bas pour actualiser)
- Barre de progression
- Navigation interne (bouton retour = historique du site)
- Thème sombre adapté au site
- Bouton « i » (coin bas-droit) : page de confidentialité de l'application

## Package

`com.maigus.ayneha.app`

## Comment ouvrir le projet

1. Ouvre **Android Studio**
2. **File → Open** et sélectionne le dossier `AYNEHA`
3. Laisse Gradle synchroniser (peut prendre 1-2 minutes la première fois)
4. Branche ton téléphone ou lance un émulateur
5. Clique sur **Run** (▶️)

## Personnaliser l’icône

1. **File → New → Image Asset**
2. Choisis **Launcher Icons (Adaptive and Legacy)**
3. Importe ton logo AYNEHA
4. Génère

Pour un meilleur splash, remplace le contenu de `res/drawable/splash_background.xml` ou ajoute une image `ic_splash_logo.png` dans `drawable`.

## Compiler un APK

- **Build → Build Bundle(s) / APK(s) → Build APK(s)**
- L’APK se trouve dans `app/build/outputs/apk/debug/`

## Notes techniques

- Minimum Android 7.0 (API 24)
- Target SDK 34
- Kotlin + WebView
- Cache intelligent pour le mode hors-ligne
- Surveillance réseau pour les mises à jour automatiques

## Version 2.0.0

- Nouveau nom : **AYNEHA Home** (le nom du paquet `com.maigus.ayneha.app` ne change pas,
  les téléphones qui ont la version 1.x reçoivent donc une mise à jour normale).
- `versionCode` 3 / `versionName` 2.0.0.
- Site embarqué remplacé par le nouveau site AYNEHA (français et anglais, lexique,
  Enseigner l'AYNEHA, carte, police AYNEHA Pro, plan 16 U+10FF7C-U+10FFDD).
- Mise à jour réseau seulement si le site en ligne est **plus récent** que la copie locale.
- Liens de téléchargement (PDF, police, APK) : ouverts dans le navigateur depuis le site en ligne.
- Liens e-mail : ouverts directement dans l'application de messagerie.
- Pont JavaScript `AynehaApp` : le site peut enregistrer une image dans Pictures/AYNEHA
  (Android 10 et plus) ou ouvrir le partage Android (carte, texte en AYNEHA).
- `FileProvider` pour partager les images ; aucune nouvelle permission.
- `tools/mirror_site.sh` réécrit : il copie exactement les fichiers du `manifest.json` du site.

## Version 1.1.0

- Ajout du bouton « i » et de la page de confidentialité (`PrivacyActivity`).
- Kit de licences commun aux applications AYNEHA.
- `versionCode` 2 / `versionName` 1.1.0.
- Le contenu du site embarqué (`assets/site/`) n'a pas changé : il est mis à jour
  par `tools/mirror_site.sh` et par le téléchargement en ligne (voir `README_OFFLINE.md`).
  Les pages du site gardent donc leurs glyphes actuels (ancienne plage U+E000-U+E061 et
  `ayneha_regular.ttf`) jusqu'à la migration du site vers le plan 16 (U+10FF7C-U+10FFDD) et
  la police `ayneha_pro.ttf`. Il faudra alors remirrorer le site avant de compiler.

## Licences

- **Code de l'application** : GNU GPL v3 ou ultérieure (fichier `LICENSE`), en-têtes SPDX
  `GPL-3.0-or-later`. Copyright (C) 2026 MAIGUS.
- **Police AYNEHA** : SIL Open Font License 1.1 (fichier `OFL.txt`), nom réservé « AYNEHA ».
- **Logos, images et textes du site** : CC BY-SA 4.0. Le nom et le logo AYNEHA sont réservés.
- Développé par MAIGUS avec l'aide d'outils d'IA (Claude, Anthropic).

Logiciel libre (GPL v3). Code source : github.com/maigus223/ayneha-home

Contact : gwokmt2q@duck.com

Créé pour MAIGUS / AYNEHA - 2026
