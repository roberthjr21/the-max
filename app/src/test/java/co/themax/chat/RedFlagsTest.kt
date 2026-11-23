package co.themax.chat

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class RedFlagsTest {
    private val noon = LocalDateTime.of(2026, 1, 1, 12, 0)
    private fun chat(n: Int = 60) = List(n) { i ->
        Message(noon.plusMinutes(i.toLong()), if (i % 2 == 0) "A" else "B", "hola")
    }
    @Test fun minimumAndNoise() {
        val noise = listOf("Se eliminó este mensaje", "Llamada perdida", "<Multimedia omitido>",
            "Los mensajes y las llamadas están cifrados de extremo a extremo.")
        val report = analyzeFlags(chat(59) + noise.map { Message(noon, "A", it) })
        assertEquals(59, report.counts.values.sum())
        assertTrue(report.readings.isEmpty())
        assertEquals(8, analyzeFlags(chat()).readings.size)
        assertTrue(analyzeFlags(chat().mapIndexed { i, m -> m.copy(sender = if (i < 9) "A" else "B") }).readings.isEmpty())
    }
    @Test fun exportFormats() {
        for ((stamp, expectedDay) in listOf("[2026-02-3, 00:04:52]" to 3, "2/13/26, 12:04:52 AM -" to 13,
            "[13.02.2026, 12:04:52 a. m.]" to 13, "03/02/26, 00:04:52 -" to 3)) {
            val r = analyze("$stamp A: hola\n$stamp B: hola")
            assertEquals(LocalDateTime.of(2026, 2, expectedDay, 0, 4, 52), r.first!!.time)
        }
        assertEquals(13, analyze("2/3/26, 1:00 p. m. - A: hola\n2/15/26, 17:00 - B: hola").first!!.time.hour)
        assertEquals(2, analyze("2/3/26, 1:00 p. m. - A: hola\n2/15/26, 17:00 - B: hola").first!!.time.monthValue)
    }
    @Test fun tiesAndRepeatedGaps() {
        val rows = chat(70).mapIndexed { i, m -> m.copy(time = noon.plusDays(2L * (i / 10)).plusMinutes(i % 10L),
            sender = if ((i / 10 + i % 10) % 2 == 0) "A" else "B") }
        val r = analyzeFlags(rows)
        assertFalse(r.readings[0].matches)
        assertTrue(r.readings[0].bySender.values.all { "50 %" in it })
        assertTrue(r.readings[3].matches)
        assertTrue(r.readings[3].bySender.values.all { "al volver: 3" in it })
        assertTrue(r.tile("B","Contacto").detail.contains("Sin combinación"))
    }
    @Test fun exactDayAndLastQuestion() {
        val rows = chat()
        val last = rows.last().copy(time = rows.last().time.plusDays(1), body = "¿Hola?")
        val r = analyzeFlags(rows + last)
        assertFalse(r.readings[3].matches)
        assertEquals("sin muestra", r.readings[4].bySender["B"])
    }
    @Test fun pressureRequiresContextAndTwoDays() {
        fun result(line: String) = analyzeFlags(chat().mapIndexed { i, m -> m.copy(time = m.time.plusDays((i / 30).toLong()),
            body = if (m.sender == "A") line else "hola") }).readings[5].matches
        assertFalse(result("¿Dónde estás?"))
        assertFalse(result("Me dijo: contesta ya"))
        assertTrue(result("Contéstame ya"))
    }
    @Test fun contrastingPatternsAndSharedPrivacy() {
        val r = analyzeFlags(chat().mapIndexed { i, m -> m.copy(time = noon.plusMinutes((i / 2) * 125L + (i % 2) * 5L),
            body = if (i % 2 == 0) "Palabra privada ".repeat(8) else "👍") })
        assertTrue(r.readings[1].matches)
        assertTrue(r.readings[2].matches)
        val tile = r.tile("A","Contacto")
        assertTrue(tile.detail.contains("media"))
        assertFalse(tile.toString().contains("Palabra privada"))
    }
    @Test fun recentDropNightActivityAndOpenQuestions() {
        val early = chat(80).mapIndexed { i, m ->
            m.copy(time = noon.minusHours(11).plusDays((i / 10).toLong()).plusMinutes(i % 10L))
        }
        val later = List(5) { i -> Message(noon.plusDays(16L + i), "A", "¿Cuándo?") }
        val end = Message(noon.plusDays(28), "B", "Hola")
        val r = analyzeFlags(early + later + end)
        assertTrue(r.readings[4].matches)
        assertEquals("5 de 5 (100 %)", r.readings[4].bySender["A"])
        assertTrue(r.readings[6].matches)
        assertTrue(r.readings[7].matches)
        assertTrue(r.tile("B", "Contacto").detail.contains("alta"))
    }
}
