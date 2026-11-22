#!/bin/bash
set -eu
code=$(jq -r .versionCode downloads/latest.json)
adb install "downloads/The-Max-${code}.apk"
adb shell am start -W -n co.themax.chat/.MainActivity
contains() {
  for attempt in {1..10}; do
    adb shell uiautomator dump /sdcard/themax-ui.xml >/dev/null
    adb exec-out cat /sdcard/themax-ui.xml > /tmp/themax-ui.xml
    if grep -Fq "$1" /tmp/themax-ui.xml; then return 0; fi
    sleep 1
  done
  return 1
}
contains 'Importar mi chat'
cat > /tmp/the-max-ejemplo.txt <<'CHAT'
22/11/26, 09:00 - Ana: Buenos días
22/11/26, 09:02 - Sol: ¡Hola!
22/11/26, 09:08 - Ana: Me encanta el café
CHAT
adb push /tmp/the-max-ejemplo.txt /sdcard/Download/the-max-ejemplo.txt
adb shell am start -W -a android.intent.action.SEND -t text/plain --grant-read-uri-permission --eu android.intent.extra.STREAM content://com.android.externalstorage.documents/document/primary%3ADownload%2Fthe-max-ejemplo.txt -n co.themax.chat/.MainActivity
contains '¿Cuál de los dos eres tú?'
read -r x y < <(python3 - <<'PY'
import re, xml.etree.ElementTree as ET
node=next(n for n in ET.parse('/tmp/themax-ui.xml').iter('node') if n.get('text')=='Ana')
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
print((x1+x2)//2,(y1+y2)//2)
PY
)
adb shell input tap "$x" "$y"
contains 'Compartir tarjeta'
grep -Fq 'text="2"' /tmp/themax-ui.xml
grep -Fq 'text="1"' /tmp/themax-ui.xml
