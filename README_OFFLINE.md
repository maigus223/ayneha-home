# AYNEHA Home - fonctionnement hors connexion

## Principe

1. L'APK contient une copie complète du site (`app/src/main/assets/site/`),
   avec son `manifest.json` et son `version.txt`.
2. Au premier lancement, cette copie est recopiée dans le stockage interne de
   l'application, puis affichée par la WebView (via `WebViewAssetLoader`).
3. Quand il y a du réseau, l'application lit `manifest.json` sur le site en
   ligne. Si sa version est **plus récente** que la copie locale, elle
   télécharge tous les fichiers listés dans un dossier temporaire, puis
   bascule d'un coup. Si un seul fichier échoue, la copie locale n'est pas
   touchée.

Les versions sont des horodatages `AAAAMMJJhhmmss` : la comparaison est
simple et sûre. Une copie embarquée plus récente que le site en ligne n'est
jamais remplacée par une version plus ancienne.

## Côté site

Le générateur du site (`_src/build.py` du dépôt ayneha-songhay.github.io)
produit **lui-même** `manifest.json` et `version.txt` à chaque construction.
Il n'y a rien à faire à la main.

Les fichiers de `assets/telechargements/` (PDF, police, APK) ne sont pas dans
le manifest, pour garder l'application légère. Dans l'application, ces liens
s'ouvrent dans le navigateur, depuis le site en ligne.

## Avant de compiler une nouvelle version de l'application

Recopier le site embarqué :

```
bash tools/mirror_site.sh
```

(ou copier les fichiers listés dans `manifest.json` du site construit).

## Fichiers

| Fichier | Rôle |
|---|---|
| `SiteRepository.kt` | Copie initiale, mise à jour réseau « tout ou rien » |
| `MainActivity.kt` | WebView, liens externes, téléchargements, pont `AynehaApp` |
| `tools/mirror_site.sh` | Recopie le site en ligne dans les assets |
| `tools/generate_manifest.py` | Génère un manifest pour un dossier quelconque (secours) |
