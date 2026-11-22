#!/usr/bin/env bash
# Uso: scripts/package-release.sh /ruta/al/apk-firmado.apk
# Requiere Android SDK Build Tools en PATH. Ejecutar desde la raíz del proyecto.
set -euo pipefail
apk="${1:?Indica el APK firmado}"
apksigner verify "$apk"
badging=$(aapt dump badging "$apk")
package=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging")
[[ "$package" == co.themax.chat ]]
code=$(sed -n "s/^package: .*versionCode='\([0-9]*\)'.*/\1/p" <<<"$badging")
name=$(sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p" <<<"$badging")
mkdir -p downloads
python3 - "$apk" "$code" "$name" <<'PY'
import hashlib,json,shutil,sys
from pathlib import Path
apk,code,name=sys.argv[1:]
manifest=Path('downloads/latest.json')
if manifest.exists() and int(code)<=json.loads(manifest.read_text())['versionCode']:
    sys.exit('Aumenta versionCode para publicar otra versión.')
dest=manifest.parent/f'The-Max-{int(code)}.apk'
shutil.copyfile(apk,dest)
manifest.write_text(json.dumps(dict(versionCode=int(code),versionName=name,sha256=hashlib.sha256(dest.read_bytes()).hexdigest()),indent=2)+'\n')
PY
