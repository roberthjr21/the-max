#!/bin/bash
set -eu
code=$(jq -r .versionCode downloads/latest.json)
if [ "$code" -gt 1 ]; then adb install downloads/The-Max-1.apk; fi
adb install -r "downloads/The-Max-${code}.apk"
adb shell am start -W -n co.themax.chat/.MainActivity
contains() {
  for attempt in {1..10}; do
    adb shell uiautomator dump /sdcard/themax-ui.xml >/dev/null
    adb exec-out cat /sdcard/themax-ui.xml > /tmp/themax-ui.xml
    if grep -Fq "$1" /tmp/themax-ui.xml; then return 0; fi
    sleep 1
  done
  cat /tmp/themax-ui.xml
  adb logcat -d -s AndroidRuntime:E ActivityManager:W
  return 1
}
contains 'Importar mi chat'
cat > /tmp/the-max-ejemplo.txt <<'CHAT'
22/11/26, 09:00 - Ana: Buenos días
22/11/26, 09:02 - Sol: ¡Hola!
22/11/26, 09:08 - Ana: Me encanta el café
CHAT
tap_text() {
  contains "$1"
  read -r x y < <(python3 - "$1" <<'PYTHON'
import re,sys,xml.etree.ElementTree as ET
node=next(n for n in ET.parse('/tmp/themax-ui.xml').iter('node') if n.get('text')==sys.argv[1] or n.get('content-desc')==sys.argv[1])
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
print((x1+x2)//2,(y1+y2)//2)
PYTHON
)
  adb shell input tap "$x" "$y"
}
adb push /tmp/the-max-ejemplo.txt /sdcard/Download/the-max-ejemplo.txt
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/the-max-ejemplo.txt
tap_text 'Importar mi chat'
if ! contains 'the-max-ejemplo.txt'; then
  adb shell input keyevent KEYCODE_MENU
  tap_text 'Downloads'
fi
tap_text 'the-max-ejemplo.txt'
tap_text 'Ana'
contains 'Compartir tarjeta'
grep -Fq 'text="2"' /tmp/themax-ui.xml
grep -Fq 'text="1"' /tmp/themax-ui.xml

if [ "$code" -ge 2 ]; then
  size=$(adb shell wm size | tail -1 | awk '{print $NF}' | tr -d '\r')
  width=${size%x*}
  height=${size#*x}
  for page in {1..30}; do
    adb shell input swipe "$((width/2))" "$((height*3/4))" "$((width/2))" "$((height/3))" 500
    adb shell uiautomator dump /sdcard/themax-ui.xml >/dev/null
    adb exec-out cat /sdcard/themax-ui.xml >> /tmp/themax-scroll.xml
    if grep -Fq 'Solo ustedes dos dicen esto' /tmp/themax-scroll.xml; then break; fi
  done
  for title in 'Nivel de interés' 'Mensaje más largo' 'Red flags' 'Lo que notamos' 'Palabras top' 'Estilo de apego' 'Cómo fue el chat' '¿Te dejó en visto?' 'The Move' 'Lo que podría querer decirte' 'Solo ustedes dos dicen esto'; do
    grep -Fq "$title" /tmp/themax-scroll.xml
  done
fi
