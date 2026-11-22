package co.themax.chat

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.*

class AnalysisTest {
    val text = """21/11/26, 9:00 a. m. - Aviso del sistema
21/11/26, 9:01 a. m. - Ana: Hola
segunda línea
21/11/26, 9:03 a. m. - Ana: ¿Un café?
21/11/26, 9:06 a. m. - Sol: Listo
[22/11/2026, 14:12:02] Sol: ¡Hola!"""
    @Test fun countsTurnsAndSessions() {
        val report = analyze(text)
        assertEquals(mapOf("Ana" to 2,"Sol" to 2),report.counts)
        assertEquals(3L,report.delays["Sol"])
        assertEquals(mapOf("Ana" to 1,"Sol" to 1),report.starts)
        assertEquals("Hola\nsegunda línea",report.first.body)
    }
    @Test fun zippedChat() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("_chat.txt")); it.write(text.toByteArray()) }
        assertEquals(text,readExport(out.toByteArray().inputStream()))
    }
    @Test fun thirtyMessages() {
        val chat = (1..30).joinToString("\n") { "21/11/26, 09:%02d - %s: hola".format(it,if (it%2==0) "Ana" else "Sol") }
        assertEquals(mapOf("Ana" to 15,"Sol" to 15),analyze(chat).counts)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyChat() { analyze("") }
}
