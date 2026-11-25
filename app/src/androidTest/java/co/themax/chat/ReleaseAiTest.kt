package co.themax.chat

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject

class ReleaseAiTest {
    fun privateFixturePassesThroughAllThreeClients() {
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
            check(result.percent == 0)
            check(result.findings.isEmpty())
            check(sent.last().toString("UTF-8").contains("Hola de prueba"))
        }
        check(sent.size == 3)
    }
}

/** Ejecuta la misma prueba en la copia reducida sin cargar la infraestructura de AndroidX. */
class ReleaseAiInstrumentation : android.app.Instrumentation() {
    override fun onCreate(arguments: android.os.Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = android.os.Bundle()
        try {
            ReleaseAiTest().privateFixturePassesThroughAllThreeClients()
            result.putString("resultado", "tres proveedores verificados")
            finish(android.app.Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("fallo", android.util.Log.getStackTraceString(error))
            finish(android.app.Activity.RESULT_CANCELED, result)
        }
    }
}
