#!/usr/bin/env bash
# Publica la compilación verificada de The Max 1.2.0 desde el equipo del propietario.
set -euo pipefail
umask 077
backup=${1:?Indica la ruta del ZIP privado de firma}
if [[ ! -f "$backup" || ! -r "$backup" ]]; then
  printf 'No encuentro el respaldo de firma en %s\n' "$backup" >&2
  exit 1
fi
backup=$(realpath "$backup")
trap 'rc=$?; printf "El proceso se detuvo en la línea %s (código %s).\n" "$LINENO" "$rc" >&2' ERR
repo=roberthjr21/the-max
run=45076069890
digest=d8c2e586358b28aa5815d93879c901d2ce63c760b78c3f36b380472452beec05
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
cd "$work"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if ! command -v apksigner >/dev/null || ! command -v aapt >/dev/null; then
  build=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
  export PATH="$build:$PATH"
fi
for tool in apksigner aapt gh git unzip python3; do command -v "$tool" >/dev/null || { echo "Falta $tool" >&2; exit 1; }; done
gh auth status --hostname github.com >/dev/null
gh repo clone "$repo" source -- --depth 1 --branch main
if python3 -c 'import json,sys; sys.exit(json.load(open("source/downloads/latest.json"))["versionCode"] < 3)'; then
  echo 'El canal ya anuncia la versión 1.2.0 o una posterior.'; exit 0
fi
gh api "repos/$repo/actions/artifacts/14775177198/zip" > artifact.zip
echo "$digest  artifact.zip" | sha256sum -c -
echo 'Extrayendo el APK y el respaldo privado…'
unzip -p artifact.zip app-release-unsigned.apk > unsigned.apk
unzip -p "$backup" the-max.p12 > key.p12
unzip -p "$backup" password.txt > password
echo 'Firmando el APK…'
apksigner sign --ks key.p12 --ks-pass file:password --ks-key-alias the-max --v4-signing-enabled false --out signed.apk unsigned.apk
certificate() { apksigner verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p'; }
echo 'Comprobando la firma con la versión anterior…'
previous=$(certificate source/downloads/The-Max-2.apk)
current=$(certificate signed.apk)
[[ -n "$current" && "$current" == "$previous" ]] || { echo "La firma difiere de la 1.1.0."; exit 1; }
cd source
bash scripts/package-release.sh ../signed.apk
python3 - <<'PY'
import json
m=json.load(open('downloads/latest.json'))
assert (m['versionCode'],m['versionName']) == (3,'1.2.0')
PY
git add -- downloads/The-Max-3.apk downloads/latest.json
git -c user.name="The Max Release" -c user.email="release@users.noreply.github.com" commit -m 'Publicar actualización firmada The Max 1.2.0'
gh auth setup-git --hostname github.com
echo 'Subiendo la actualización a GitHub…'
git push origin HEAD:main
echo "Canal publicado. Abre The Max y pulsa Buscar actualización."
