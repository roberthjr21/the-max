package co.themax.chat

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class PhaseOneTest {
    private val fixture get() = javaClass.getResource("/chat-sistema.txt")!!.readText()
    @Test fun noticesUrlsMedianAndAccents() {
        val report = analyze(fixture)
        assertEquals(listOf("Ana","Sol"), report.names)
        assertEquals(3L, report.delays["Sol"])
        assertEquals("cafe", report.words.first())
        val words = report.insights.people.getValue("Ana").words.keys + report.words
        assertTrue(words.none { it in setOf("www","https","com","compras","ejemplo") })
    }
    @Test fun sparseNoiseAndActualGroup() {
        assertEquals(2, analyze(fixture + "21/11/26, 17:00 - Servicio: aviso").names.size)
        val group = (1..6).joinToString("\n") { "21/11/26, 17:00 - Tercero: Hola" }
        assertTrue(runCatching { analyze(fixture + group) }.isFailure)
    }
    @Test fun stagesAndDurations() = runBlocking {
        val stages = mutableListOf<String>()
        analyzeChat(fixture) { stages.add(it) }
        assertEquals(listOf("Analizando señales","Preparando tarjetas"), stages)
        assertEquals("6 h 52 min", delayText(412))
        assertEquals("6 h", delayText(360))
        assertEquals("<1 min", delayText(0))
        assertEquals("Sin datos", delayText(null))
        assertEquals(2L, medianMinutes(listOf(1,3)))
    }
    @Test fun senderAdviceUsesDetectedThreat() {
        val report = analyze(fixture + "21/11/26, 17:00 - Sol: Te voy a pegar")
        assertTrue(insightCards(report,"Ana","Sol","Sol").single { it.title == "Qué podrías responder" }.detail.contains("incomodó"))
        assertFalse(displayError(IllegalStateException("Failed requirement")).contains("requirement"))
    }
}
