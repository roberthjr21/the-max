package co.themax.chat

import java.text.Normalizer
import java.util.Locale

private val invisible = Regex("""[\p{Cf}\p{Z}\s\uFE0E\uFE0F]+""")
private val marker = Regex("""(?i)^(?:<[^>]*(?:omitid[oa]|omitted)>|(?:imagen|foto|video|vídeo|audio|sticker|documento|multimedia|gif|media) (?:omitid[oa]|omitted)|(?:se eliminó este mensaje|eliminaste este mensaje|this message was deleted)\.?)$""")

fun visibleBody(raw: String): String? {
    val text = raw.trim { it.isWhitespace() || it == '\u200e' || it == '\u200f' || it == '\uFEFF' }
    return text.takeUnless { it.isEmpty() || invisible.matches(it) || marker.matches(it) }
}


private val marks = Regex("""\p{M}+""")
internal fun normalizeChat(text: String): String {
    val lower = text.lowercase(Locale.ROOT)
    if (lower.all { it.code < 128 }) return lower
    return marks.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
}
private val links = Regex("""(?i)(?:https?://|www\.)\S+|\b[\p{L}\d-]+(?:\.[\p{L}\d-]+)*\.(?:com|co|org|net|edu|io)(?:/\S*)?""")
private val letters = Regex("""[\p{L}]+""")
internal val ignored = "que con por del para los las una pero como mas ese esa esto esta este entre porque tambien desde hasta cuando donde quien tengo tiene todos todas hay mis tus sus muy asi soy somos fue son era ser estar hemos han han sido les unos unas sobre aunque pero sin al el la lo te se de en un me tu si no mi es ya yo le nos su es eso ese estos esos algo nada aqui alla cada mismo misma todo multimedia omitido omitida archivo adjunto mensaje mensajes eliminado elimino eliminaste eliminada llamada perdida https http www com".split(' ').toSet()
internal fun wordTokens(normalized: String): Sequence<String> {
    val text = if ('.' in normalized || "://" in normalized) links.replace(normalized, " ") else normalized
    return letters.findAll(text).map { it.value }
}
