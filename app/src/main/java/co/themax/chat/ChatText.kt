package co.themax.chat

import java.text.Normalizer
import java.util.Locale

private val invisible = Regex("""[\p{Cf}\p{Z}\s\uFE0E\uFE0F]""")
private val marker = Regex("""(?i)^(?:<[^>]*(?:omitid[oa]|omitted)>|(?:imagen|foto|video|vídeo|audio|sticker|documento|multimedia|gif|media) (?:omitid[oa]|omitted)|(?:se eliminó este mensaje|eliminaste este mensaje|this message was deleted)\.?)$""")

fun visibleBody(raw: String): String? {
    val text = raw.trim { it.isWhitespace() || it == '\u200e' || it == '\u200f' || it == '\uFEFF' }
    return text.takeUnless { invisible.replace(it, "").isEmpty() || marker.matches(it) }
}

private val flagRules = linkedMapOf(
    "Amenazas" to Regex("""\b(?:me las vas a pagar|si me dejas (?:publico|voy a publicar) tus fotos|te (?:voy a|voy a hacer) (?:pegar|golpear|hacer dano)|te vas a arrepentir si)\b"""),
    "Control" to Regex("""\b(?:no te (?:dejo|permito) (?:ir|salir|hablar)|(?:tienes que|debes) pedirme permiso|te prohibo (?:salir|hablar|ver)|no (?:puedes|vas a) salir con tus amig[oa]s)\b"""),
    "Presión emocional" to Regex("""\b(?:si (?:de verdad )?me (?:amaras|quisieras|quisieses)|si me quieres (?:demuestramelo|tienes que)|si me dejas (?:es|sera) tu culpa)\b"""),
    "Invasión de privacidad" to Regex("""\b(?:dame|pasame|mandame|enviame) (?:tu|la) (?:contrasena|clave de (?:tu )?(?:celular|telefono|instagram|whatsapp))\b""")
)
private val accent = Regex("""\p{M}+""")
private val quotations = Regex("""[«“"][^»”"\n]*[»”"]""")
private val reported = Regex("""\b(?:me dijo|me dijeron|dijo que|no (?:digas|dije|me digas)|por ejemplo|un ejemplo)\s*[:;,]?\s*$""")

private val negation = Regex("""\b(?:nunca|jamas|no)\s*$""")

fun flagReasons(body: String): List<String> {
    val normalized = accent.replace(Normalizer.normalize(body.lowercase(Locale.ROOT), Normalizer.Form.NFD), "")
    val direct = quotations.replace(normalized, " ").replace(Regex("""[ \t]+"""), " ")
    return flagRules.filterValues { pattern ->
        pattern.findAll(direct).any { match ->
            val prefix = direct.substring(maxOf(0, match.range.first - 50), match.range.first)
            !reported.containsMatchIn(prefix) && !negation.containsMatchIn(prefix)
        }
    }.keys.toList()
}
