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
        assertEquals("Hola\nsegunda línea",report.first?.body)
    }
    @Test fun zippedChat() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("_chat.txt")); it.write(text.toByteArray()) }
        assertEquals(text,readExport(out.toByteArray().inputStream()))
    }
    @Test fun insightsKeepEachSpeakersWordsAndEvidenceSeparate() {
        val chat = """21/11/26, 09:00 - Ana: luna azul luna azul
21/11/26, 09:05 - Sol: luna azul ¿vamos por café?
21/11/26, 19:05 - Ana: ahora no puedo"""
        val report = analyze(chat)
        val ana = report.insights.people.getValue("Ana")
        val sol = report.insights.people.getValue("Sol")
        assertEquals(2,ana.words["luna"])
        assertEquals(1,sol.words["luna"])
        assertEquals(1,sol.questions)
        assertEquals(1,sol.plans)
        assertEquals(1,ana.longPauses)
        assertEquals(0,sol.longPauses)
        val cards = insightCards(report,"Sol","Ana","Otra persona")
        assertEquals(11,cards.size)
        assertTrue(cards.last().detail.contains("luna azul"))
        assertTrue(cards.first { it.title == "Mensaje más largo" }.detail.contains("café"))
    }
    @Test fun invisibleAndDeletedEntriesDoNotHideTheFirstText() {
        val prefix = "21/11/26, 09:00 - Ana: \u200b\n21/11/26, 09:01 - Sol: <Multimedia omitido>"
        assertNull(analyze(prefix).first)
        val report = analyze(prefix + "\n21/11/26, 09:03 - Sol: 👋")
        assertEquals("👋",report.first?.body)
        assertEquals("Sol",report.first?.sender)
        assertEquals(3,report.counts.values.sum())
    }
    @Test fun categoriesRespectCaseAccentsAndQuotedWarnings() {
        assertEquals(listOf("Invasión de privacidad"),flagReasons("¡PÁSAME tu contraseña!"))
        assertTrue(flagReasons("Me dijo: «dame tu contraseña»").isEmpty())
        assertTrue(flagReasons("No digas dame tu contraseña").isEmpty())
        assertTrue(flagReasons("¿Me pasas la clave del wifi?").isEmpty())
        assertEquals(2,flagReasons("Te prohíbo salir. Me las vas a pagar.").size)
    }
    @Test fun explainsSignalsAndKeepsExamplesSeparateFromSharedSummary() {
        val report = analyze("""21/11/26, 09:00 - Ana: Hola
21/11/26, 09:01 - Sol: Nunca te voy a pegar
22/11/26, 09:01 - Sol: Pásame tu contraseña
22/11/26, 09:02 - Sol: Dame tu contraseña""")
        val tiles = insightCards(report,"Ana","Sol","Otra persona")
        val flags = tiles.single { it.title == "Red flags" }
        assertEquals(2,flags.comparison!!.rows.single().other)
        assertEquals(1,flags.examples.size)
        assertTrue(flags.examples.single().contains("Otra persona"))
        assertFalse(flags.detail.contains("Pásame"))
        val interest = tiles.single { it.title == "Nivel de interés" }
        assertEquals(Metric("Inicios de conversación",1,1),interest.comparison!!.rows.first())
        assertTrue(interest.detail.contains("Los dos"))
    }
    @Test fun thirtyMessages() {
        val chat = (1..30).joinToString("\n") { "21/11/26, 09:%02d - %s: hola".format(it,if (it%2==0) "Ana" else "Sol") }
        assertEquals(mapOf("Ana" to 15,"Sol" to 15),analyze(chat).counts)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyChat() { analyze("") }
}
