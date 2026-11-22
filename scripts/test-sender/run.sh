#!/usr/bin/env bash
# Instala un emisor de pruebas independiente que posee el archivo compartido.
set -euo pipefail
source_dir=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cd "$work"
platform="$ANDROID_HOME/platforms/android-35/android.jar"
build="$ANDROID_HOME/build-tools/35.0.0"
mkdir classes dex
javac -encoding UTF-8 -source 8 -target 8 -cp "$platform" -d classes "$source_dir"/*.java
"$build/d8" --lib "$platform" --min-api 26 --output dex classes/co/themax/fixture/*.class
"$build/aapt" package -f -I "$platform" -M "$source_dir/AndroidManifest.xml" -F unsigned.apk
(cd dex && zip -q ../unsigned.apk classes.dex)
"$build/zipalign" -f 4 unsigned.apk aligned.apk
keytool -genkeypair -alias fixture -keystore test.p12 -storepass android -keypass android -dname CN=Fixture -keyalg RSA -validity 2 >/dev/null
"$build/apksigner" sign --ks test.p12 --ks-pass pass:android --out sender.apk aligned.apk
adb install sender.apk
adb shell am start -W -n co.themax.fixture/.SendChat
