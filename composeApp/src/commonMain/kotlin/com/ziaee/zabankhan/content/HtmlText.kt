package com.ziaee.zabankhan.content

/** Small non-network XML/HTML tokenizer: never expands declarations or external entities. */
data class MarkupTag(val name: String, val closing: Boolean, val attributes: Map<String, String>)
private val tagPattern = Regex("<!--.*?-->|<!\\[CDATA\\[.*?]]>|<[^>]+>", setOf(RegexOption.DOT_MATCHES_ALL))
fun markupTags(text: String): List<MarkupTag> = tagPattern.findAll(text).mapNotNull { parseTag(it.value) }.toList()
private fun parseTag(raw: String): MarkupTag? {
    val inside = raw.removePrefix("<").removeSuffix(">").trim()
    if (inside.startsWith('!') || inside.startsWith('?')) return null
    val closing = inside.startsWith('/')
    val body = inside.removePrefix("/").trim()
    val name = body.takeWhile { !it.isWhitespace() && it != '/' }.substringAfterLast(':').lowercase()
    val attrs = Regex("([\\w:.-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')").findAll(body).associate {
        it.groupValues[1].lowercase() to decodeEntities(it.groupValues[2].ifEmpty { it.groupValues[3] })
    }
    return MarkupTag(name, closing, attrs)
}
fun decodeEntities(text: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z]+);").replace(text) { match ->
    val key = match.groupValues[1]
    val number = when { key.startsWith("#x") -> key.drop(2).toIntOrNull(16); key.startsWith('#') -> key.drop(1).toIntOrNull(); else -> null }
    if (number != null && number in 1..0x10ffff && number !in 0xd800..0xdfff) {
        if (number <= 0xffff) number.toChar().toString() else {
            val n = number - 0x10000
            "${(0xd800 + (n shr 10)).toChar()}${(0xdc00 + (n and 1023)).toChar()}"
        }
    } else when (key) {
        "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
        "eacute" -> "é"; "egrave" -> "è"; "agrave" -> "à"; "ecirc" -> "ê"; "ccedil" -> "ç"; "ocirc" -> "ô"
        "laquo" -> "«"; "raquo" -> "»"; "mdash" -> "—"; "ndash" -> "–"; "hellip" -> "…"; "rsquo" -> "’"; "lsquo" -> "‘"
        "ldquo" -> "“"; "rdquo" -> "”"; else -> match.value
    }
}
fun htmlToParagraphs(html: String): String {
    val output = StringBuilder()
    val ignored = mutableListOf<String>()
    var cursor = 0
    fun text(end: Int) { if (ignored.isEmpty()) output.append(decodeEntities(html.substring(cursor, end)).map { if (it.isWhitespace()) ' ' else it }.joinToString("").replace(Regex("\\s+"), " ")) }
    for (match in tagPattern.findAll(html)) {
        text(match.range.first)
        val tag = parseTag(match.value)
        if (tag != null) {
            if (tag.name in setOf("script", "style", "head", "nav")) {
                if (tag.closing) { if (ignored.lastOrNull() == tag.name) ignored.removeAt(ignored.lastIndex) } else ignored += tag.name
            }
            if (ignored.isEmpty() && tag.name in setOf("p", "div", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "br")) output.append("\n\n")
        }
        cursor = match.range.last + 1
    }
    text(html.length)
    return output.toString().split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
}
internal fun elementText(xml: String, name: String): String? = Regex("<(?:[\\w.-]+:)?$name(?:\\s[^>]*)?>(.*?)</(?:[\\w.-]+:)?$name\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    .find(xml)?.groupValues?.get(1)?.let(::htmlToParagraphs)?.takeIf { it.isNotBlank() }
