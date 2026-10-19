#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 MAIGUS
# Copie le site AYNEHA en ligne dans app/src/main/assets/site/, pour
# l'embarquer dans l'APK (disponible dès l'installation, sans réseau).
#
# Le site publie lui-même manifest.json (liste exacte des fichiers) et
# version.txt : ce script télécharge exactement ces fichiers, comme le fait
# l'application quand elle se met à jour.
#
# À lancer avant de compiler une nouvelle version de l'application.
# Prérequis : curl et python3.

set -euo pipefail

SITE_URL="${1:-https://ayneha-songhay.github.io/}"
DEST_DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/site"
TMP_DIR="$(mktemp -d)"

echo "Lecture de ${SITE_URL}manifest.json ..."
curl -fsSL "${SITE_URL}manifest.json" -o "$TMP_DIR/manifest.json"

python3 - "$TMP_DIR/manifest.json" > "$TMP_DIR/liste.txt" <<'PY'
import json, sys
for f in json.load(open(sys.argv[1]))["files"]:
    print(f)
PY

echo "Téléchargement de $(wc -l < "$TMP_DIR/liste.txt") fichiers ..."
while IFS= read -r f; do
  mkdir -p "$TMP_DIR/site/$(dirname "$f")"
  curl -fsSL "${SITE_URL}${f}" -o "$TMP_DIR/site/$f"
done < "$TMP_DIR/liste.txt"
cp "$TMP_DIR/manifest.json" "$TMP_DIR/site/manifest.json"
curl -fsSL "${SITE_URL}version.txt" -o "$TMP_DIR/site/version.txt"

rm -rf "$DEST_DIR"
mv "$TMP_DIR/site" "$DEST_DIR"
rm -rf "$TMP_DIR"
echo "Terminé : version $(cat "$DEST_DIR/version.txt") copiée dans $DEST_DIR"
