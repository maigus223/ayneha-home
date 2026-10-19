#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 MAIGUS
"""
Génère manifest.json (et version.txt) pour un dossier contenant une copie
du site AYNEHA.

Usage : python3 generate_manifest.py <dossier_du_site>

Ce script doit être exécuté deux fois à chaque mise à jour du site :
  1) sur DEST_DIR = app/src/main/assets/site  (fait automatiquement par
     mirror_site.sh) -> pour la version embarquée dans l'APK.
  2) sur le dossier que vous publiez en ligne (ex. avant de pousser vers
     GitHub Pages) -> pour que manifest.json soit disponible à la racine du
     site en ligne, et que l'app puisse détecter et télécharger les mises à
     jour (voir SiteRepository.kt côté app Android).

Les DEUX manifests doivent utiliser le même format ; utilisez la même
version (horodatage) pour les deux si vous embarquez et publiez la même
copie en même temps.
"""

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

EXCLUDE_NAMES = {"manifest.json", "version.txt"}


def main() -> None:
    if len(sys.argv) != 2:
        print("Usage: python3 generate_manifest.py <dossier_du_site>")
        sys.exit(1)

    site_dir = Path(sys.argv[1]).resolve()
    if not site_dir.is_dir():
        print(f"Erreur : {site_dir} n'est pas un dossier")
        sys.exit(1)

    files = []
    for path in sorted(site_dir.rglob("*")):
        if path.is_file() and path.name not in EXCLUDE_NAMES:
            rel = path.relative_to(site_dir).as_posix()
            files.append(rel)

    if not files:
        print(f"Attention : aucun fichier trouvé dans {site_dir}")

    version = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")

    manifest = {"version": version, "files": files}

    (site_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (site_dir / "version.txt").write_text(version, encoding="utf-8")

    print(f"manifest.json généré : {len(files)} fichiers, version {version}")


if __name__ == "__main__":
    main()
