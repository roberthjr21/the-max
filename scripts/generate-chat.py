"""Genera exportaciones ficticias deterministas de hasta el tamaño en MiB indicado."""
import argparse
from datetime import datetime, timedelta
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("mib", type=int, choices=[1, 4, 15])
parser.add_argument("output", type=Path)
args = parser.parse_args()
bodies = ["¿Vamos por café mañana? Podemos vernos después de clase.",
          "Sí, me queda bien. Luego hablamos para cuadrar el horario 😊",
          "Consulta https://www.ejemplo.com/agenda y me cuentas.",
          "Listo, tengo el cuaderno y los apuntes para estudiar."]
start = datetime(2026, 1, 1)
with args.output.open("wb") as out:
    count = 0
    while True:
        t = start + timedelta(minutes=count // 3)
        sender = ("Ana", "Sol")[count % 2]
        line = f"{t:%d/%m/%Y, %H:%M} - {sender}: {bodies[count % len(bodies)]}\n".encode()
        if out.tell() + len(line) > args.mib * 1024**2:
            break
        out.write(line)
        count += 1
    print(f"{out.tell()} bytes, {count} mensajes")
