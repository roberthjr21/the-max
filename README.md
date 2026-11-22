# The Max para Android

Importa un chat individual de WhatsApp y explora las tarjetas sobre la conversación. El análisis funciona en el teléfono. La vista de ejemplo permite conocer la app antes de abrir un archivo personal.

**[Descargar The Max para Android](https://github.com/roberthjr21/the-max/releases/latest/download/The-Max.apk)** · Android 8 o superior.

En WhatsApp abre el chat, selecciona **Más → Exportar chat → Sin archivos multimedia** y compártelo con The Max. También puedes usar **Importar mi chat** con un TXT o ZIP. El formato de fecha previsto es día/mes/año. El límite es 16 MB de texto. Al compartir la tarjeta de primer mensaje, su texto queda visible en la imagen.

## Tarjetas de conversación

La versión 1.1 incorpora palabras por participante, el mensaje más largo, expresiones compartidas, actividad, posibles frases de presión y sugerencias para responder. El nivel de interés describe tres señales observables de participación. El informe indica qué puede medirse en una exportación y qué requiere preguntarle a la otra persona. El cálculo funciona en el dispositivo, sin servicios de IA.

## Desarrollo

Kotlin, Jetpack Compose, JDK 17 y Android SDK 35. Ejecuta `./gradlew testDebugUnitTest assembleDebug`. Para compilar sin firmar una versión de distribución, usa `./gradlew assembleRelease`.

## Actualizar una instalación

Pulsa **Buscar actualización** al final de la pantalla. La app consulta el archivo público `downloads/latest.json`. Si hay una versión superior, descarga el APK, verifica el SHA-256, el identificador, el código de versión y la firma, y abre el instalador de Android para confirmar. En la primera actualización Android puede pedir autorización para instalar apps desde The Max.

Para distribuir otra versión, aumenta versionCode y versionName en app/build.gradle, ejecuta las pruebas, genera assembleRelease, firma el APK con la misma clave y usa el script de publicación descrito en scripts/package-release.sh. Sube los archivos de downloads juntos en un commit de main. Conserva la clave de firma en privado.
