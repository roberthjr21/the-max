package co.themax.chat

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

data class FlagReading(val title: String, val matches: Boolean, val explanation: String,
    val bySender: Map<String, String> = emptyMap())

data class FlagReport(val counts: Map<String, Int>, val readings: List<FlagReading>) {
    fun tile(self: String, contact: String): Tile {
        val hits = readings.count { it.matches }
        val summary = when {
            readings.isEmpty() -> "Datos insuficientes · ${counts.values.sum()} mensajes válidos"
            hits < 2 -> "Sin combinación de señales · $hits de 8"
            hits == 2 -> "Coincidencia media · 2 de 8"
            else -> "Coincidencia alta · $hits de 8"
        }
        val note = if (readings.isEmpty()) "Se necesitan 60 mensajes con contenido y al menos 10 de cada persona."
            else "Dos patrones dan nivel medio y tres o más, alto. El nivel describe el chat y no mide la intención de nadie."
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

private val systemRecord = Regex("""(?i)^(?:(?:llamada|videollamada)(?: de (?:voz|video))? (?:perdida|sin respuesta)|missed (?:voice |video )?call|you deleted this message|este mensaje fue eliminado|(?:los mensajes y las llamadas|messages and calls) (?:están cifrados de extremo a extremo|are end-to-end encrypted).*?)[.!]?$""")
private val demand = Regex("""\b(?:contesta(?:me)? ya|responde(?:me)? ya|por que no me contestas.{0,30}(?:ahora mismo|te estoy esperando)|(?:donde estas|con quien estas).{0,45}(?:dime ya|responde ya|exijo saberlo))\b""")
private val context = Regex("""\b(?:me dijo|me dijeron|por ejemplo|no digas|no dije|no me digas|la cancion dice)\b""")
private val quotes = Regex("""[«“"][^»”"]*[»”"]""")
private fun pressure(text: String): Boolean {
    val direct = quotes.replace(java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(combiningMarks, ""), " ")
    return !context.containsMatchIn(direct) && (flagReasons(direct).isNotEmpty() || demand.containsMatchIn(direct))
}
private val combiningMarks = Regex("""\p{M}+""")
private fun fraction(part: Int, total: Int) = if (total == 0) "sin muestra"
    else "$part de $total (${(part * 100.0 / total).roundToInt()} %)"

fun analyzeFlags(input: List<Message>): FlagReport {
    val rows = input.mapNotNull { m -> visibleBody(m.body)?.takeUnless(systemRecord::matches)?.let { m.copy(body = it) } }
        .sortedBy { it.time }
    val people = rows.groupBy { it.sender }
    val counts = people.mapValues { it.value.size }
    if (rows.size < 60 || counts.size != 2 || counts.values.min() < 10) return FlagReport(counts, emptyList())
    val names = people.keys.toList()
    val evidence = mutableListOf<FlagReading>()
    fun add(title: String, active: Boolean, description: String, detail: (String) -> String) {
        evidence += FlagReading(title, active, description, names.associateWith(detail))
    }
    val starts = names.associateWith { 0 }.toMutableMap()
    val delays = names.associateWith { mutableListOf<Long>() }
    val breaks = names.associateWith { 0 }.toMutableMap()
    val before = breaks.toMutableMap()
    for (i in 1 until rows.size) {
        val last = rows[i - 1]
        val next = rows[i]
        val gap = Duration.between(last.time, next.time)
        if (gap >= Duration.ofHours(8)) starts[next.sender] = starts.getValue(next.sender) + 1
        if (gap <= Duration.ofDays(1) && last.sender != next.sender) delays.getValue(next.sender).add(gap.seconds)
        if (gap > Duration.ofDays(1)) {
            before[last.sender] = before.getValue(last.sender) + 1
            breaks[next.sender] = breaks.getValue(next.sender) + 1
        }
    }
    val sessions = starts.values.sum()
    add("Quién retoma la charla", sessions >= 6 && starts.values.max() * 4 > sessions * 3,
        "Contamos reinicios tras 8 horas de silencio. Coincide si alguien supera el 75 % con al menos 6 reinicios.") {
        fraction(starts.getValue(it), sessions)
    }
    fun consistent(name: String, check: (Long) -> Boolean): Boolean {
        val times = delays.getValue(name)
        return times.size >= 6 && times.count(check) * 10 >= times.size * 7
    }
    add("Ritmos de respuesta", names.any { consistent(it) { t -> t <= 15 * 60 } } && names.any { consistent(it) { t -> t >= 120 * 60 } },
        "Comparamos cambios de turno de hasta 24 h. Coincide con 6 respuestas por persona y al menos 70 % en ritmos opuestos.") {
        val times = delays.getValue(it)
        "En 15 min o menos: ${fraction(times.count { t -> t <= 15 * 60 }, times.size)}. En 2 h o más: ${fraction(times.count { t -> t >= 120 * 60 }, times.size)}."
    }
    val short = people.mapValues { (_, msgs) -> msgs.count { it.body.codePointCount(0, it.body.length) <= 12 } }
    val long = people.mapValues { (_, msgs) -> msgs.count { it.body.codePointCount(0, it.body.length) >= 60 } }
    add("Longitud de los mensajes", names.any { short.getValue(it) >= counts.getValue(it) * .6 } &&
        names.any { long.getValue(it) >= counts.getValue(it) * .6 },
        "Coincide si en una persona predominan los mensajes cortos y en la otra los largos (60 % o más en ambos casos).") {
        "Hasta 12 caracteres: ${fraction(short.getValue(it), counts.getValue(it))}. Desde 60: ${fraction(long.getValue(it), counts.getValue(it))}."
    }
    val gaps = breaks.values.sum()
    add("Pausas de más de un día", gaps >= 2 && gaps * 4 >= sessions,
        "Reinicios tras más de 24 h: ${fraction(gaps, sessions)}. Coincide desde 2 pausas y el 25 % de los reinicios. Solo sabemos quién escribió antes y después.") {
        "Último mensaje antes de la pausa: ${before[it]}. Primer mensaje al volver: ${breaks[it]}."
    }
    val questions = names.associateWith { 0 }.toMutableMap()
    val missed = questions.toMutableMap()
    val nextTime = mutableMapOf<String, LocalDateTime>()
    for (msg in rows.asReversed()) {
        val other = if (msg.sender == names.first()) names.last() else names.first()
        if ('?' in msg.body || '¿' in msg.body) {
            val deadline = msg.time.plusDays(1)
            val replied = nextTime[other]?.let { it <= deadline } == true
            if (replied || deadline <= rows.last().time) {
                questions[msg.sender] = questions.getValue(msg.sender) + 1
                if (!replied) missed[msg.sender] = missed.getValue(msg.sender) + 1
            }
        }
        nextTime[msg.sender] = msg.time
    }
    add("Preguntas sin un mensaje de vuelta", names.any { questions.getValue(it) >= 5 && missed.getValue(it) >= 3 && missed.getValue(it) * 2 >= questions.getValue(it) },
        "Mensajes con ¿ o ? sin otro mensaje de la otra persona en 24 h. Coincide desde 3 casos y el 50 % de al menos 5 preguntas evaluables. No sabemos si las leyeron.") {
        fraction(missed.getValue(it), questions.getValue(it))
    }
    val demands = people.mapValues { (_, msgs) -> msgs.filter { pressure(it.body) } }
    add("Frases de presión", demands.values.any { list -> list.map { it.time.toLocalDate() }.distinct().size >= 2 },
        "Coincide al encontrar expresiones directas en dos días distintos de una persona. «¿Dónde estás?» por sí sola puede ser una pregunta para cuadrar un plan.") {
        "${fraction(demands.getValue(it).size, counts.getValue(it))} mensajes."
    }
    val nights = people.mapValues { (_, msgs) -> msgs.filter { it.time.hour < 5 } }
    add("Mensajes de madrugada", names.any { nights.getValue(it).size >= counts.getValue(it) * .25 &&
        nights.getValue(it).map { m -> m.time.toLocalDate() }.distinct().size >= 3 },
        "Entre medianoche y las 4:59 a. m., según el archivo. Coincide desde 3 madrugadas y el 25 % de los mensajes de una persona.") {
        fraction(nights.getValue(it).size, counts.getValue(it))
    }
    val end = rows.last().time.toLocalDate()
    val cut = end.minusDays(14)
    val start = cut.minusDays(14)
    val older = rows.filter { it.time.toLocalDate() >= start && it.time.toLocalDate() < cut }
    val newer = rows.count { it.time.toLocalDate() >= cut && it.time.toLocalDate() < end }
    val enough = rows.first().time.toLocalDate() <= start && older.size >= 20 && older.map { it.time.toLocalDate() }.distinct().size >= 4
    val drop = if (enough) ((older.size - newer) * 100.0 / older.size).roundToInt() else 0
    evidence += FlagReading("Cambio de ritmo", enough && newer * 2 <= older.size, if (enough)
        "De ${older.size} a $newer mensajes en dos bloques de 14 días completos. ${if (drop > 0) "Bajó $drop %" else if (drop < 0) "Subió ${-drop} %" else "Sin cambio"}. Coincide con una caída del 50 % o más."
        else "Falta historial comparable: 28 días completos y al menos 20 mensajes en 4 días del primer bloque. Se excluye el último día del archivo.")
    return FlagReport(counts, evidence)
}
