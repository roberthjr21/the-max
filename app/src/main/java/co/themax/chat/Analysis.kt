package co.themax.chat

import java.io.InputStream
import java.time.LocalDateTime
import java.time.Duration
import java.util.zip.ZipInputStream

data class Message(val time: LocalDateTime, val sender: String, val body: String)
data class Report(val names: List<String>, val counts: Map<String, Int>, val delays: Map<String, Long>,
    val starts: Map<String, Int>, val first: Message, val peak: String, val words: List<String>)

private val header = Regex("""^\[?(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4}),?\s+(\d{1,2}):(\d{2})(?::\d{2})?\s*([ap]\.?\s*m\.?)?\]?\s*(?:-\s+)?(.+)$""", RegexOption.IGNORE_CASE)
private const val limit = 16 * 1024 * 1024

fun readExport(input: InputStream): String {
    val stream = input.buffered()
    stream.mark(2)
    val zipped = stream.read() == 80 && stream.read() == 75
    stream.reset()
    if (!zipped) return readTextLimited(stream)
    ZipInputStream(stream).use { zip ->
        repeat(32) {
            val entry = zip.nextEntry ?: error("El ZIP no contiene una conversación .txt.")
            if (!entry.isDirectory && entry.name.endsWith(".txt", true)) return readTextLimited(zip)
            require(entry.isDirectory) { "Exporta el chat sin archivos multimedia." }
        }
    }
    error("El ZIP contiene demasiadas entradas.")
}

private fun readTextLimited(input: InputStream): String {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        require(out.size() + n <= limit) { "El chat supera el límite de 16 MB." }
        out.write(buffer, 0, n)
    }
    return out.toString("UTF-8")
}

fun analyze(text: String): Report {
    val rows = mutableListOf<Message>()
    val body = StringBuilder()
    var pending: Message? = null
    fun flush() { pending?.let { rows.add(it.copy(body = body.toString())) }; body.clear(); pending = null }
    for (raw in text.lineSequence()) {
        val line = raw.trimStart('\uFEFF','\u200e','\u200f').replace('\u202f',' ').replace('\u00a0',' ')
        val parts = header.matchEntire(line)?.groupValues
        if (parts == null) {
            if (pending != null) body.append('\n').append(line)
            continue
        }
        flush()
        val payload = parts[7]
        val split = payload.indexOf(": ")
        if (split < 1) continue
        val marker = parts[6].lowercase()
        val hour = parts[4].toInt().let { if (marker.isEmpty()) it else it % 12 + if (marker.startsWith("p")) 12 else 0 }
        val time = runCatching { LocalDateTime.of(parts[3].toInt().let { if (it < 100) 2000 + it else it },
            parts[2].toInt(), parts[1].toInt(), hour, parts[5].toInt()) }.getOrNull() ?: continue
        pending = Message(time, payload.substring(0,split).trim(), "")
        body.append(payload.substring(split+2))
    }
    flush()
    require(rows.isNotEmpty()) { "No se reconocieron mensajes. Usa una exportación con fecha día/mes/año." }
    val counts = rows.groupingBy { it.sender }.eachCount()
    require(counts.size == 2) { "Para este resumen se necesitan dos participantes. Encontramos ${counts.size}." }
    val starts = mutableMapOf(rows.first().sender to 1)
    val delays = mutableMapOf<String, MutableList<Long>>()
    for (i in 1 until rows.size) {
        val prev = rows[i-1]; val msg = rows[i]
        val gap = Duration.between(prev.time,msg.time).toMinutes()
        if (gap >= 480) starts[msg.sender] = (starts[msg.sender] ?: 0) + 1
        else if (gap >= 0 && prev.sender != msg.sender) delays.getOrPut(msg.sender) { mutableListOf() }.add(gap)
    }
    val peak = rows.groupingBy { it.time.toLocalDate() }.eachCount().maxBy { it.value }
    val tokens = Regex("""[\p{L}]{3,}""")
    val stop = setOf("que","con","por","del","para","los","las","una","pero","como","más","multimedia","omitido","archivo","adjunto")
    val words = rows.asSequence().flatMap { tokens.findAll(it.body.lowercase()).map { m -> m.value } }
        .filter { it !in stop }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
    return Report(counts.keys.toList(), counts, delays.mapValues { it.value.average().toLong() }, starts, rows.first(),
        "${peak.key.dayOfMonth}/${peak.key.monthValue}/${peak.key.year} · ${peak.value} mensajes", words)
}
