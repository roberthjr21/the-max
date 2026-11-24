package co.themax.chat

import java.time.Duration
import java.util.Locale

data class PersonStats(
    var questions: Int = 0, var turns: Int = 0, var longPauses: Int = 0,
    var plans: Int = 0, var availability: Int = 0,
    var longest: String = "", var longestLength: Int = 0, var tokens: Int = 0,
    val words: MutableMap<String, Int> = mutableMapOf(),
    val phrases: MutableMap<String, Int> = mutableMapOf()
)

data class Insights(val people: Map<String, PersonStats>, val days: Long, val activeDays: Int, val flags: FlagReport, val interest: InterestDays)

private val plans = Regex("""\b(vamos (?:a|por)|salgamos|nos vemos|te invito|quieres salir)\b""")
private val availability = Regex("""\b(te aviso|te escribo luego|hablamos luego|ahora no puedo)\b""")
private const val tokenLimit = 24000

fun collectPeople(rows: List<Message>): Map<String, PersonStats> {
    val people = rows.map { it.sender }.distinct().associateWith { PersonStats() }
    for ((index, message) in rows.withIndex()) {
        val p = people.getValue(message.sender)
        val previous = rows.getOrNull(index - 1)
        if (previous?.sender != message.sender) p.turns++
        if (previous != null && previous.sender != message.sender &&
            Duration.between(previous.time, message.time).toHours() >= 8) p.longPauses++
        val body = visibleBody(message.body) ?: continue
        val length = body.codePointCount(0, body.length)
        if (length > p.longestLength) { p.longestLength = length; p.longest = body.take(360) }
        if ('?' in body || '¿' in body) p.questions++
        val lower = message.normalized
        if (plans.containsMatchIn(lower)) p.plans++
        if (availability.containsMatchIn(lower)) p.availability++
        var prev = ""
        for (token in wordTokens(lower)) {
            if (p.tokens >= tokenLimit) break
            p.tokens++
            if (token.length in 3..40 && token !in ignored) p.words.merge(token, 1, Int::plus)
            if (prev.isNotEmpty() && token.length <= 40 && (prev !in ignored || token !in ignored) &&
                (prev.length >= 4 || token.length >= 4)) p.phrases.merge("$prev $token",1,Int::plus)
            prev = token.takeIf { it.length <= 40 }.orEmpty()
        }
    }
    return people
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
        other in data.flags.flaggedSenders -> "Si algo te incomodó, puedes decir: «Eso no me hizo sentir bien. ¿Podemos hablarlo?»"
        b.availability > 0 -> "«Dale, cuando tengas un rato me cuentas 😊»"
        b.plans > 0 -> "«Me suena ese plan. ¿Qué día te queda bien?»"
        b.questions > 0 -> "Retoma una pregunta suya y cuéntale algo tuyo antes de preguntarle por su día."
        else -> "«¿Qué fue lo mejor que te pasó esta semana?»"
    }
    val terms = if (maxOf(a.tokens,b.tokens) >= tokenLimit) " Primeras 24.000 palabras por persona." else ""
    val initiative = report.starts[self] ?: 0
    val theirs = report.starts[other] ?: 0
    val rhythm = when {
        initiative + theirs < 3 -> "Aún hay pocos reinicios para describir su dinámica."
        initiative == theirs -> "Los dos suelen retomar el contacto."
        initiative > theirs -> "Tú retomas el contacto con más frecuencia."
        else -> "$label retoma el contacto con más frecuencia."
    }
    fun card(title: String, color: Long, value: String, note: String) = Tile(title,color,detail=value,note=note)
    return listOf(
        card("Mensaje más largo",0xffffe6ab, most.longest.ifEmpty { "Sin texto disponible" },
            "$author · ${most.longestLength} caracteres." + if (most.longestLength > 360) " Se muestra el comienzo." else ""),
        data.flags.tile(self, label),
        card("Cuántas veces cambiaron de turno",0xffc7dbfa, "La conversación pasó de una persona a otra ${a.turns+b.turns-1} veces.", "Varios mensajes seguidos de la misma persona cuentan como un turno."),
        card("Palabras top",0xfffbd4bc, "Tú: ${top(a)}\n\n$label: ${top(b)}", "Frecuencias individuales, sin palabras comunes.$terms"),
        card("Quién suele dar el primer paso",0xffe1cef8, "$rhythm Inicios: $initiative tuyos y $theirs de $label.",
            "Describe la iniciativa en este chat. Para conocer el apego hacen falta más contexto y la perspectiva de cada persona."),
        card("Cómo fue el chat",0xffffe7b5, "${data.days} días entre el primer y último día, contando ambos. Se escribieron en ${data.activeDays}.", "El resumen cubre únicamente el período exportado."),
        card("Respuestas después de una pausa",0xffffc7dc, "$label volvió a responder tras 8 horas o más en ${b.longPauses} cambios de turno.",
            "La exportación no incluye confirmaciones de lectura. Solo se cuentan las pausas con una respuesta posterior."),
        card("Qué podrías responder",0xffb2efd6,suggest,"Idea para tu próxima respuesta, basada en patrones del chat."),
        card("Lo que podría querer decirte",0xffc8e4ff, when {
            b.plans > 0 -> "Aparecen posibles propuestas de verse. Podrías preguntar: «¿Te gustaría que cuadráramos ese plan?»"
            b.availability > 0 -> "Mencionó hablar después. Podrías preguntarle cuándo le queda cómodo."
            else -> "No hay una intención explícita que podamos identificar. Puedes preguntar: «¿Cómo te has sentido hablando conmigo?»"
        }, "Lectura posible de sus palabras. Solo esa persona puede confirmar lo que quiso decir."),
        card("Expresiones que ambos usan",0xffe6d2f9,shared.ifEmpty { "Todavía no encontramos una expresión compartida." },
            "Pares de palabras que ambos escribieron. La comparación solo incluye este chat.$terms"),
        data.interest.tile(other, label)
    )
}
