package co.themax.chat

import java.time.Duration
import java.util.Locale

data class PersonStats(
    var questions: Int = 0, var turns: Int = 0, var longPauses: Int = 0,
    var plans: Int = 0, var availability: Int = 0, var pressure: Int = 0,
    var longest: String = "", var longestLength: Int = 0, var tokens: Int = 0,
    val words: MutableMap<String, Int> = mutableMapOf(),
    val phrases: MutableMap<String, Int> = mutableMapOf()
)

data class Insights(val people: Map<String, PersonStats>, val days: Long, val activeDays: Int)

private val word = Regex("""[\p{L}]+""")
private val ignored = setOf("que","por","para","con","las","los","una","del","pero","como","más","ese","esa","esto","está","muy","hay","son","eso","mis","sus","soy","era","sin","pues","porque","también")
private val plans = Regex("""\b(vamos (?:a|por)|salgamos|nos vemos|te invito|quieres salir)\b""")
private val availability = Regex("""\b(te aviso|te escribo luego|hablamos luego|ahora no puedo)\b""")
private val pressure = Regex("""\b(si me quisieras|dame tu contraseña|no te dejo (?:ir|salir)|me las vas a pagar)\b""")
private val placeholder = Regex("""(?i)^(?:<?(?:multimedia omitido|media omitted)>?|se eliminó este mensaje\.?|eliminaste este mensaje\.?)$""")
private const val tokenLimit = 24000

fun collectInsights(rows: List<Message>): Insights {
    val people = rows.map { it.sender }.distinct().associateWith { PersonStats() }
    for ((index, message) in rows.withIndex()) {
        val p = people.getValue(message.sender)
        val previous = rows.getOrNull(index - 1)
        if (previous?.sender != message.sender) p.turns++
        if (previous != null && previous.sender != message.sender &&
            Duration.between(previous.time, message.time).toHours() >= 8) p.longPauses++
        val body = message.body.trim()
        if (body.isEmpty() || placeholder.matches(body)) continue
        val length = body.codePointCount(0, body.length)
        if (length > p.longestLength) { p.longestLength = length; p.longest = body.take(360) }
        if ('?' in body || '¿' in body) p.questions++
        val lower = body.lowercase(Locale.ROOT)
        if (plans.containsMatchIn(lower)) p.plans++
        if (availability.containsMatchIn(lower)) p.availability++
        if (pressure.containsMatchIn(lower)) p.pressure++
        var prev = ""
        for (match in word.findAll(lower)) {
            if (p.tokens >= tokenLimit) break
            val token = match.value
            p.tokens++
            if (token.length in 3..40 && token !in ignored) p.words.merge(token, 1, Int::plus)
            if (prev.isNotEmpty() && token.length <= 40 && (prev !in ignored || token !in ignored) &&
                (prev.length >= 4 || token.length >= 4)) p.phrases.merge("$prev $token",1,Int::plus)
            prev = token.takeIf { it.length <= 40 }.orEmpty()
        }
    }
    val days = rows.map { it.time.toLocalDate() }.toSet()
    return Insights(people, Duration.between(days.min().atStartOfDay(), days.max().atStartOfDay()).toDays()+1, days.size)
}

private fun top(p: PersonStats) = p.words.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .take(5).joinToString(" · ") { "${it.key} (${it.value})" }.ifEmpty { "Sin palabras suficientes" }

fun insightCards(report: Report, self: String, other: String, label: String): List<Tile> {
    val data = report.insights
    val a = data.people.getValue(self)
    val b = data.people.getValue(other)
    val shared = a.phrases.keys.asSequence().filter { b.phrases.containsKey(it) }
        .sortedWith(compareByDescending<String> { minOf(a.phrases.getValue(it), b.phrases.getValue(it)) }
            .thenByDescending { a.phrases.getValue(it) + b.phrases.getValue(it) }.thenBy { it })
        .take(3).joinToString("\n") { "«$it» · ${a.phrases[it]} tú / ${b.phrases[it]} contacto" }
    val most = if (a.longestLength >= b.longestLength) a else b
    val author = if (most === a) "Tú" else label
    val suggest = when {
        b.pressure > 0 -> "Si algo te incomodó, puedes decir: «Eso no me hizo sentir bien. ¿Podemos hablarlo?»"
        b.availability > 0 -> "«Dale, cuando tengas un rato me cuentas 😊»"
        b.plans > 0 -> "«Me suena ese plan. ¿Qué día te queda bien?»"
        b.questions > 0 -> "Retoma una pregunta suya y cuéntale algo tuyo antes de preguntarle por su día."
        else -> "«¿Qué fue lo mejor que te pasó esta semana?»"
    }
    val terms = if (maxOf(a.tokens,b.tokens) >= tokenLimit) " Primeras 24.000 palabras por persona." else ""
    fun card(title: String, color: Long, value: String, note: String) = Tile(title,color,detail=value,note=note)
    return listOf(
        card("Nivel de interés",0xffb8f2dc, "Tú hiciste ${a.questions} mensajes con preguntas y propusiste ${a.plans} posibles planes.\n$label: ${b.questions} con preguntas y ${b.plans} posibles planes.", "Señales de participación. Una conversación por sí sola no revela atracción."),
        card("Mensaje más largo",0xffffe6ab, most.longest.ifEmpty { "Sin texto disponible" },
            "$author · ${most.longestLength} caracteres." + if (most.longestLength > 360) " Se muestra el comienzo." else ""),
        card("Red flags",0xffffbfd1, if (a.pressure+b.pressure == 0) "Ninguna coincidencia en las frases revisadas." else "Posible presión en ${a.pressure} mensajes tuyos y ${b.pressure} de $label.",
            "Busca expresiones concretas como «si me quisieras». Una cita o una broma también pueden coincidir."),
        card("Lo que notamos",0xffc7dbfa, "Hubo ${a.turns+b.turns} turnos en ${data.activeDays} días con mensajes.", "Varios mensajes seguidos de la misma persona cuentan como un turno."),
        card("Palabras top",0xfffbd4bc, "Tú: ${top(a)}\n\n$label: ${top(b)}", "Frecuencias individuales, sin palabras comunes.$terms"),
        card("Estilo de apego",0xffe1cef8, "Tú retomaste ${report.starts[self] ?: 0} conversaciones; $label, ${report.starts[other] ?: 0}.",
            "Describe la iniciativa en este chat. Para conocer el apego hacen falta más contexto y la perspectiva de cada persona."),
        card("Cómo fue el chat",0xffffe7b5, "${data.days} días entre el primer y último día, contando ambos. Se escribieron en ${data.activeDays}.", "El resumen cubre únicamente el período exportado."),
        card("¿Te dejó en visto?",0xffffc7dc, "$label volvió a responder tras 8 horas o más en ${b.longPauses} cambios de turno.",
            "La exportación no incluye confirmaciones de lectura. Solo se cuentan las pausas con una respuesta posterior."),
        card("The Move",0xffb2efd6,suggest,"Idea para tu próxima respuesta, basada en patrones del chat."),
        card("Lo que podría querer decirte",0xffc8e4ff, when {
            b.plans > 0 -> "Aparecen posibles propuestas de verse. Podrías preguntar: «¿Te gustaría que cuadráramos ese plan?»"
            b.availability > 0 -> "Mencionó hablar después. Podrías preguntarle cuándo le queda cómodo."
            else -> "No hay una intención explícita que podamos identificar. Puedes preguntar: «¿Cómo te has sentido hablando conmigo?»"
        }, "Lectura posible de sus palabras. Solo esa persona puede confirmar lo que quiso decir."),
        card("Solo ustedes dos dicen esto",0xffe6d2f9,shared.ifEmpty { "Todavía no encontramos una expresión compartida." },
            "Pares de palabras que ambos escribieron. La comparación solo incluye este chat.$terms")
    )
}
