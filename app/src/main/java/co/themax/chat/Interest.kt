package co.themax.chat

import java.time.Duration
import java.time.LocalDate
import kotlin.math.roundToInt

data class InterestDays(val active: Int, val messages: Map<String, Int>, val signals: Map<String, Int>) {
    fun tile(other: String, label: String): Tile {
        val enough = active >= 5 && messages.size == 2 && messages.values.all { it >= 10 }
        val days = signals.getOrDefault(other, 0)
        val detail = if (enough) "${(days * 100.0 / active).roundToInt()} % de los días activos\n$label retomó la charla o hizo una pregunta en $days de $active días."
            else "Datos insuficientes para estimar la iniciativa de $label."
        val note = if (enough) "Cada día cuenta una vez. Retomar significa escribir primero tras 8 horas de pausa. Estas señales muestran iniciativa al conversar. Solo esa persona puede aclarar si siente atracción."
            else "Se necesitan 5 días con conversación y al menos 10 mensajes de cada persona."
        return Tile("Interés en la conversación", 0xffb8f2dc, detail = detail, note = note)
    }
}

internal fun interestDays(input: List<Message>): InterestDays {
    val active = mutableSetOf<LocalDate>()
    val signals = mutableMapOf<String, MutableSet<LocalDate>>()
    val counts = mutableMapOf<String, Int>()
    var previous: Message? = null
    for (message in input.sortedBy { it.time }) {
        val body = visibleBody(message.body)?.takeUnless(systemRecord::matches) ?: continue
        val day = message.time.toLocalDate()
        active.add(day)
        counts.merge(message.sender, 1, Int::plus)
        val restarted = previous?.let { Duration.between(it.time, message.time).toHours() >= 8 } == true
        if (restarted || '?' in body || '¿' in body) signals.getOrPut(message.sender) { mutableSetOf() }.add(day)
        previous = message
    }
    return InterestDays(active.size, counts, signals.mapValues { it.value.size })
}
