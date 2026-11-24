package co.themax.chat

import java.time.LocalDateTime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiAnalysisTest {
    val lines = listOf(AiLine("A","Hola"), AiLine("B","Dame tu contraseña"))
    val good = """{"porcentaje":35,"resumen":"Se pidió una clave.","redFlags":[{"senal":"Privacidad","mensaje":2,"ejemplo":"tu contraseña"}]}"""
    @Test fun checksQuoteAgainstCorrectSpeakerAndRejectsBadScores() {
        assertEquals(35, parseAi(good,lines).percent)
        for (bad in listOf(good.replace("35","101"), good.replace(":2,",":1,"), good.replace("tu contraseña","tu clave"),
            good.replace("35","0"), good + " explicación", "{}")) {
            assertTrue(runCatching { parseAi(bad,lines) }.isFailure)
        }
        assertTrue(parseAi("""{"porcentaje":0,"resumen":"Sin señales.","redFlags":[]}""",lines).findings.isEmpty())
    }
    @Test fun limitsPreviewAndSkipsSystemRecords() {
        val date = LocalDateTime.of(2026,1,1,0,0)
        val rows = (1..100).map { Message(date.plusMinutes(it.toLong()), if(it%2==1) "Ana" else "Sol", "Texto $it") }
        val sample = fragment(rows + Message(date.plusDays(1),"Ana","Llamada perdida"))
        assertEquals(80,sample.size)
        assertEquals("Texto 21",sample.first().text)
        assertEquals("Texto 100",sample.last().text)
        val big = fragment(rows.map { it.copy(body="x".repeat(600)) })
        assertEquals(12000,big.sumOf { it.text.length })
        assertEquals(500,big.first().text.length)
    }
    @Test fun envelopesAndRolesForThreeProviders() {
        val fixtures = mapOf(Provider.OPENAI to """{"choices":[{"message":{"content":"ok"}}]}""",
            Provider.ANTHROPIC to """{"content":[{"type":"text","text":"ok"}]}""",
            Provider.GEMINI to """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""")
        for ((p,fixture) in fixtures) {
            assertEquals("ok",responseText(p,JSONObject(fixture)))
            val body = aiBody(p,"Instrucción propia",lines)
            assertTrue(body.toString().contains("Instrucción propia"))
            val user = when(p) {
                Provider.OPENAI -> body.getJSONArray("messages").getJSONObject(1).getString("content")
                Provider.ANTHROPIC -> body.getJSONArray("messages").getJSONObject(0).getString("content")
                Provider.GEMINI -> body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
            }
            assertEquals(transcript(lines),user)
        }
    }
}
