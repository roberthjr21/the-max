"""Prueba de navegación en un emulador limpio, sin claves de API ni chats personales."""
import re
import subprocess
import time
from pathlib import Path
from xml.etree import ElementTree

def adb(*args):
    return subprocess.check_output(["adb", *args])

def node(text):
    for _ in range(15):
        adb("shell", "uiautomator", "dump", "/sdcard/phase1.xml")
        root = ElementTree.fromstring(adb("exec-out", "cat", "/sdcard/phase1.xml"))
        for item in root.iter("node"):
            if item.get("text") == text:
                return item
        time.sleep(1)
    raise AssertionError(text)

def tap(text):
    bounds = list(map(int, re.findall(r"\d+", node(text).get("bounds"))))
    adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))

def screenshot(name):
    folder = Path("build/phase1-ui")
    folder.mkdir(parents=True, exist_ok=True)
    (folder / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))

apk, = Path("app/build/outputs/apk/release").glob("*.apk")
adb("install", "-r", str(apk))
adb("shell", "cmd", "uimode", "night", "yes")
adb("shell", "am", "start", "-W", "-n", "co.themax.chat/.MainActivity")
node("VISTA DE EJEMPLO")
screenshot("principal")
tap("Menú")
tap("Ajustes de IA")
node("OpenAI")
screenshot("ajustes")
tap("Cancelar")
tap("Menú")
tap("Buscar actualización")
node("Tienes la versión más reciente.")
screenshot("actualizacion")
