package co.themax.chat

import java.time.Duration
import java.text.Normalizer
import kotlin.math.roundToInt

data class FlagReading(val title: String, val matches: Boolean, val explanation: String,
    val bySender: Map<String, String> = emptyMap())

data class FlagReport(val counts: Map<String, Int>, val readings: List<FlagReading>, val sample: List<AiLine> = emptyList(), val flaggedSenders: Set<String> = emptySet()) {
    fun tile(self: String, contact: String): Tile {
        val hits = readings.count { it.matches }
        val summary = when {
            readings.any { it.matches && it.title == Signal.THREAT.title } -> "Se detectó una expresión de amenaza o chantaje"
            readings.isEmpty() -> "Datos insuficientes · ${counts.values.sum()} mensajes válidos"
            hits == 0 -> "Sin coincidencias con las expresiones revisadas"
            hits == 1 -> "Una señal para revisar en contexto"
            hits == 2 -> "Dos señales para revisar en contexto"
            else -> "$hits señales para revisar en contexto"
        }
        val note = if (readings.isEmpty()) "Se necesitan 60 mensajes con contenido y al menos 10 de cada persona."
            else "Las coincidencias describen expresiones del chat. Una amenaza explícita se muestra incluso en chats cortos."
        return Tile("Red flags", 0xffffbfd1, detail = listOf(summary, readings.filter { it.matches }.joinToString(" · ") { it.title }).filter { it.isNotEmpty() }.joinToString("\n"), note = "$note Todo se calcula en tu teléfono.",
            readings = readings.sortedByDescending { it.matches }.map { r ->
                buildString {
                    if (r.matches) append("Coincide · ")
                    appendLine(r.title)
                    append(r.explanation)
                    r.bySender.forEach { (sender, value) ->
                        append("\n").append(if (sender == self) "Tú" else contact).append(" · ").append(value)
                    }
                }
            })
    }
}

internal val systemRecord = Regex("""(?i)^(?:(?:llamada|videollamada)(?: de (?:voz|video))? (?:perdida|sin respuesta)|missed (?:voice |video )?call|you deleted this message|este mensaje fue eliminado|(?:los mensajes y las llamadas|messages and calls) (?:están cifrados de extremo a extremo|are end-to-end encrypted).*?)[.!]?$""")

private fun rule(pattern: String) = Regex("""\b(?:$pattern)\b""")

private enum class Signal(val title: String, val description: String, pattern: String? = null) {
    PERSIST("Insistencia después de un no", "Se repitió una petición después de una negativa sobre el mismo tema."),
    ANSWER("Exigencia de respuesta inmediata", "Apareció una exigencia de contestar ya, un reclamo por estar en línea o una exigencia de pruebas.",
        """(?:responde|contesta)(?:me)? (?:ya|ahora mismo)|(?:te vi|estabas|estas) en linea.{0,35}(?:no (?:me )?(?:contest(?:as|aste)|respond(?:es|iste))|contestame)|(?:mandame|muestrame|enviame) pruebas de (?:que|lo que|donde) estabas|(?:idiota|imbecil|estupid[oa]).{0,35}(?:por (?:tardar|no contestar)|por que no (?:me )?(?:contestas|respondes))"""),
    CONTROL("Control de amistades o salidas", "Apareció una orden sobre contactos, amistades o permisos para salir.",
        """(?:te prohibo|no te permito|no te dejo) (?:salir|hablar|ver)|(?:tienes que|debes) pedirme permiso para salir|(?:tienes que|quiero que) bloque(?:ar|es) a|(?:bloquea|deja de hablarle) a (?:tu|ese|esa)|alejate de tus amig[oa]s|no (?:puedes|vas a) salir con tus amig[oa]s"""),
    PRIVACY("Exigencia de acceso privado", "Se pidió acceso a claves personales o a conversaciones privadas.",
        """(?:dame|pasame|mandame|enviame|muestrame) (?:tu contrasena(?! (?:del?|para) (?:el )?(?:wifi|wi-fi))|(?:tu|la) clave (?:de (?:tu|tus)|del) (?:celular|telefono|instagram|whatsapp)|tus (?:chats|conversaciones|mensajes privados))|dejame (?:revisar|leer) tus (?:chats|conversaciones)"""),
    GUILT("Presión mediante culpa", "La frase condiciona el cariño o la confianza a que la otra persona haga algo.",
        """si (?:de verdad )?me (?:quisieras|amaras|quieres|amas).{0,45}(?:me (?:mandarias|enviarias|darias|mandas|darias acceso)|demuestramelo|tienes que)|si confiaras en mi.{0,30}me (?:darias|mostrarias)"""),
    MOCK("Burla que continúa después de expresar dolor", "Se repitió una burla personal después de que la otra persona expresó que le dolía."),
    BLAME("Culpa por el maltrato", "Quien escribe atribuye a la otra persona su propia forma de insultar o tratarla.",
        """(?:tu )?me (?:hiciste|obligaste a) (?:hablarte asi|insultarte|tratarte asi)|por tu culpa (?:te (?:trate asi|insulte)|tuve que insultarte)"""),
    THREAT("Amenaza o chantaje", "Se encontró una expresión directa de daño o de divulgar material privado. Una sola coincidencia activa esta señal.",
        """te (?:voy a (?:hacer dano|pegar|golpear|matar)|hare dano)|voy a (?:publicar|difundir|filtrar|compartir) tus (?:fotos (?:privadas|intimas)|(?:chats|conversaciones) privad[oa]s?)|si (?:me (?:dejas|terminas)|no .{1,45})[, ]+(?:voy a (?:publicar|filtrar|difundir)|publico|filtro|difundo) tus (?:fotos|chats|conversaciones)""");
    val direct = pattern?.let(::rule)
}

private val quotedText = Regex("""[«“"‘'][^»”"’'\n]*[»”"’']""")
private val reportedSpeech = rule("""(?:me|te|le) (?:dijo|dijeron|escribio)|por ejemplo|un ejemplo|no (?:digas|dije|me digas)|si (?:alguien|te) (?:te )?dice|la cancion dice""")
private val negated = Regex("""\b(?:no|nunca|jamas)(?: (?:te|yo))*\s*$""")
private val clauses = Regex("""[.!?;\n]+""")
private fun directText(plain: String): String {
    return quotedText.replace(plain, " ").split(clauses)
        .filterNot { it.trimStart().startsWith(">") || reportedSpeech.containsMatchIn(it) }.filter { it.isNotBlank() }.joinToString("\n").trim()
}

private fun Regex.asserted(text: String) = findAll(text).any { match ->
    !negated.containsMatchIn(text.substring(maxOf(0, match.range.first - 25), match.range.first))
}
private data class Topic(val subject: Regex, val request: Regex)
private val topics = listOf(
    Topic(rule("foto[s]?"), rule("""(?:mandame|enviame|pasame) (?:una |la |esa |otra )?foto|manda(?:me)?la|envia(?:me)?la""")),
    Topic(rule("salir|salgamos|sal conmigo"), rule("salgamos|sal conmigo|acepta salir")),
    Topic(rule("hablar|hablemos|habla de"), rule("hablemos de (?:eso|ese tema)|habla de eso|tienes que hablar de"))
)
private val refusal = Regex("""(?:^\s*[¿¡]?\s*no\s*$|\b(?:no quiero|ya (?:te )?dije que no|prefiero no|no voy a|no gracias)\b)""")
private val accepted = rule("esta bien|cambio de opinion|cambie de opinion|mejor si|ahora si")
private val hurt = rule("me duele|me dolio|me lastima|deja de burlarte|no te burles de mi")
private val mockery = listOf(rule("eres (?:un |una )?(?:gord[oa]|gord[oa] asqueros[oa])|que gord[oa] (?:eres|estas)"),
    rule("eres (?:un |una )?(?:inutil|fracasad[oa])"), rule("que (?:nariz|cara) tan (?:fea|horrible)"))
private data class Turn(val sender: String, val text: String, val time: java.time.LocalDateTime)

fun analyzeFlags(input: List<Message>): FlagReport {
    val counts = mutableMapOf<String, Int>()
    val hits = Signal.values().associateWith { mutableMapOf<String, Int>() }
    var previous: Turn? = null
    var earlier: Turn? = null
    val ordered = input.sortedBy { it.time }
    for (message in ordered) {
        val body = visibleBody(message.body)?.takeUnless(systemRecord::matches) ?: continue
        val sender = message.sender
        counts[sender] = counts.getOrDefault(sender, 0) + 1
        val text = directText(message.normalized)
        val last = previous
        val before = earlier
        val reply = last != null && last.sender != sender && Duration.between(last.time, message.time) <= Duration.ofHours(2)
        val exchange = reply && before != null && before.sender == sender &&
            Duration.between(before.time, message.time) <= Duration.ofHours(2)
        for (signal in Signal.values()) {
            val matched = when (signal) {
                Signal.PERSIST -> reply && refusal.containsMatchIn(last!!.text) && !accepted.containsMatchIn(last.text) && topics.any { topic ->
                    topic.request.asserted(text) && (topic.subject.containsMatchIn(last.text) ||
                        (exchange && last.text.trim() in setOf("no", "no gracias", "ya te dije que no") && topic.request.asserted(before!!.text)))
                }
                Signal.MOCK -> exchange && hurt.containsMatchIn(last!!.text) && mockery.any { it.asserted(before!!.text) && it.asserted(text) }
                else -> signal.direct!!.asserted(text)
            }
            if (matched) hits.getValue(signal).merge(sender, 1, Int::plus)
        }
        earlier = last
        previous = Turn(sender, text, message.time)
    }
    val sufficient = counts.size == 2 && counts.values.sum() >= 60 && counts.values.all { it >= 10 }
    val signals = if (sufficient) Signal.values().toList() else listOf(Signal.THREAT).filter { hits.getValue(it).isNotEmpty() }
    return FlagReport(counts, signals.map { signal ->
        val matches = hits.getValue(signal)
        FlagReading(signal.title, matches.isNotEmpty(), signal.description, counts.mapValues { (sender, total) ->
            val n = matches.getOrDefault(sender, 0)
            "$n de $total mensajes (${(100.0 * n / total).roundToInt()} %)"
        })
    }, fragment(ordered), hits.values.flatMap { it.keys }.toSet())
}
