package co.themax.chat

import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

private fun obj(vararg values: Pair<String, Any>) = JSONObject().apply { values.forEach { put(it.first, it.second) } }
private fun array(vararg values: Any) = JSONArray().apply { values.forEach { put(it) } }
class AiFailure(message: String): Exception(message)

internal fun transcript(lines: List<AiLine>) = JSONArray().apply {
    lines.forEachIndexed { i, line -> put(obj("mensaje" to i+1, "persona" to line.speaker, "texto" to line.text)) }
}.toString()

internal fun aiBody(p: Provider, instructions: String, lines: List<AiLine>): JSONObject {
    val system = "$instructions\n\n$CONTRACT"
    val user = transcript(lines)
    val msg = obj("role" to "user", "content" to user)
    return when (p) {
        Provider.OPENAI -> obj("model" to "gpt-4.1-mini", "store" to false, "max_completion_tokens" to 1800,
            "response_format" to obj("type" to "json_object"), "messages" to array(obj("role" to "system","content" to system),msg))
        Provider.ANTHROPIC -> obj("model" to "claude-haiku-4-5", "system" to system, "max_tokens" to 1800, "messages" to array(msg))
        Provider.GEMINI -> obj("systemInstruction" to obj("parts" to array(obj("text" to system))),
            "contents" to array(obj("role" to "user","parts" to array(obj("text" to user)))),
            "generationConfig" to obj("responseMimeType" to "application/json","maxOutputTokens" to 2400))
    }
}

internal fun responseText(p: Provider, body: JSONObject): String = when (p) {
    Provider.OPENAI -> body.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    Provider.ANTHROPIC -> body.getJSONArray("content").let { parts ->
        (0 until parts.length()).map { parts.getJSONObject(it) }.filter { it.optString("type") == "text" }.joinToString("") { it.getString("text") }
    }
    Provider.GEMINI -> body.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").let { parts ->
        (0 until parts.length()).map { parts.getJSONObject(it) }.filterNot { it.optBoolean("thought") }.joinToString("") { it.getString("text") }
    }
}

private fun limited(stream: InputStream): String = stream.bufferedReader().use { reader ->
    val buf = CharArray(4096)
    buildString {
        while (true) {
            val n = reader.read(buf)
            if (n < 0) break
            if (length + n > 100000) throw AiFailure("El proveedor devolvió una respuesta demasiado larga.")
            append(buf, 0, n)
        }
    }
}

fun analyzeOnline(p: Provider, key: String, instructions: String, lines: List<AiLine>): AiResult {
    val url = when (p) {
        Provider.OPENAI -> "https://api.openai.com/v1/chat/completions"
        Provider.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
        Provider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"
    }
    val conn = URL(url).openConnection() as HttpsURLConnection
    try {
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.useCaches = false
        conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
        when (p) {
            Provider.OPENAI -> conn.setRequestProperty("Authorization","Bearer $key")
            Provider.GEMINI -> conn.setRequestProperty("x-goog-api-key",key)
            Provider.ANTHROPIC -> {
                conn.setRequestProperty("x-api-key",key)
                conn.setRequestProperty("anthropic-version","2023-06-01")
            }
        }
        val bytes = aiBody(p,instructions,lines).toString().toByteArray()
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val invalid = code == 400 && conn.errorStream?.let(::limited)?.contains("API_KEY_INVALID") == true
            throw AiFailure(when {
                invalid || code in listOf(401,403) -> "Revisa tu clave y los permisos del proveedor en Ajustes."
                code == 429 || code == 402 -> "Se alcanzó un límite de uso o de saldo del proveedor."
                code == 404 -> "Tu cuenta no tiene acceso al modelo configurado."
                else -> "El proveedor no pudo completar el análisis (HTTP $code)."
            })
        }
        val data = limited(conn.inputStream)
        return try { parseAi(responseText(p,JSONObject(data)),lines) }
        catch (_: Exception) { throw AiFailure("La respuesta llegó incompleta, con formato incorrecto o con una cita que no se pudo verificar.") }
    } catch (_: SocketTimeoutException) { throw AiFailure("El proveedor tardó demasiado en responder.") }
    catch (_: IOException) { throw AiFailure("No pudimos conectar. Revisa tu conexión a internet.") }
    finally { conn.disconnect() }
}
