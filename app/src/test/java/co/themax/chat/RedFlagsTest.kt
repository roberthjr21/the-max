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
    private fun report(vararg lines: String, padded: Boolean = true): FlagReport {
        val rows = if (padded) chat() else emptyList()
        return analyzeFlags(rows + lines.mapIndexed { i, line ->
            Message(noon.plusDays(1).plusMinutes(i.toLong()), if (i % 2 == 0) "A" else "B", line)
        })
    }
    @Test fun detectsEachCategoryAndCreditsTheSpeaker() {
        val cases = listOf(
            listOf("Mándame una foto", "No.", "Mándamela"),
            listOf("Estabas en línea y no me contestaste"),
            listOf("Tienes que pedirme permiso para salir"),
            listOf("Dame tu contraseña"),
            listOf("Si de verdad me quisieras, me mandarías esa foto"),
            listOf("Eres un inútil", "Eso me duele", "Eres un inútil"),
            listOf("Tú me hiciste hablarte así"),
            listOf("Si me dejas, publico tus fotos")
        )
        cases.forEachIndexed { index, lines ->
            val hit = report(*lines.toTypedArray()).readings.withIndex().single { it.value.matches }
            assertEquals(index, hit.index)
            assertTrue(hit.value.bySender.getValue("A").startsWith("1 de"))
            assertTrue(hit.value.bySender.getValue("B").startsWith("0 de"))
        }
    }
    @Test fun singleThreatOverridesSampleAndStaysPrivate() {
        val r = report("Hola", "Te voy a hacer daño", padded = false)
        assertTrue(r.tile("A", "Contacto").detail.contains("amenaza"))
        assertFalse(r.tile("A", "Contacto").toString().contains("Te voy a hacer daño"))
        assertTrue(r.readings.single().bySender.getValue("B").startsWith("1 de"))
    }
    @Test fun consentTopicSpeakerAndQuotedSpeechMatter() {
        for (lines in listOf(listOf("No quiero salir", "Mándame una foto"),
            listOf("Mándame una foto", "No gracias", "Está bien"),
            listOf("Eres un inútil", "Eso me duele", "Perdón"),
            listOf("Me dijo: «Te voy a hacer daño»", "Nunca te voy a pegar"),
            listOf("¿Dónde estás?", "Contesta cuando puedas"), listOf("Dame tu contraseña del wifi"))) {
            assertFalse(report(*lines.toTypedArray()).readings.any { it.matches })
        }
        val rows = chat() + listOf(Message(noon.plusDays(1),"A","No quiero mandar una foto"),
            Message(noon.plusDays(1).plusMinutes(1),"A","Mándame una foto"),
            Message(noon.plusDays(2),"B","Mándame una foto"))
        assertFalse(analyzeFlags(rows).readings.any { it.matches })
    }
}
