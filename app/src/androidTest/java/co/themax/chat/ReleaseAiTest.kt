package co.themax.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class ReleaseAiTest {
    @Test fun privateFixturePassesThroughAllThreeClients() {
        val model = """{"porcentaje":0,"resumen":"Sin señales en la muestra.","redFlags":[]}"""
        fun objects(value: JSONObject) = JSONArray().put(value)
        val sent = mutableListOf<ByteArrayOutputStream>()
        // Solo en el proceso de instrumentación. Nunca hay conexión a un proveedor.
        URL.setURLStreamHandlerFactory { protocol ->
            if (protocol != "https") null else object : URLStreamHandler() {
                override fun openConnection(url: URL): URLConnection {
                    val body = when (url.host) {
                        "api.openai.com" -> JSONObject().put("choices", objects(JSONObject()
                            .put("message",JSONObject().put("content",model))))
                        "api.anthropic.com" -> JSONObject().put("content", objects(JSONObject().put("type","text").put("text",model)))
                        "generativelanguage.googleapis.com" -> JSONObject().put("candidates", objects(JSONObject()
                            .put("content",JSONObject().put("parts",objects(JSONObject().put("text",model))))))
                        else -> error("Destino inesperado en prueba")
                    }
                    return object : HttpsURLConnection(url) {
                        val request = ByteArrayOutputStream().also { sent += it }
                        override fun getOutputStream() = request
                        override fun getInputStream() = ByteArrayInputStream(body.toString().toByteArray())
                        override fun getResponseCode() = 200
                        override fun connect() {}
                        override fun disconnect() {}
                        override fun usingProxy() = false
                        override fun getCipherSuite() = "prueba"
                        override fun getLocalCertificates(): Array<Certificate>? = null
                        override fun getServerCertificates() = emptyArray<Certificate>()
                    }
                }
            }
        }
        for (provider in Provider.values()) {
            val result = analyzeOnline(provider,"clave-ficticia",DEFAULT_PROMPT,listOf(AiLine("A","Hola de prueba")))
            assertEquals(0,result.percent)
            assertTrue(result.findings.isEmpty())
            assertTrue(sent.last().toString("UTF-8").contains("Hola de prueba"))
        }
        assertEquals(3,sent.size)
    }
}
