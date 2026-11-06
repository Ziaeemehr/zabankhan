package com.ziaee.zabankhan.ui.reader

data class Paragraph(val text: String, val offset: Int)
/** Keep original UTF-16 offsets (including CRLF) for highlights and reading position. */
fun paragraphs(body: String): List<Paragraph> = Regex("[^\\r\\n]+(?:\\r?\\n(?!\\s*\\r?\\n)[^\\r\\n]+)*").findAll(body)
    .filter { it.value.isNotBlank() }.map { Paragraph(it.value, it.range.first) }.toList()
fun paragraphIndexAt(paragraphs: List<Paragraph>, offset: Int): Int =
    paragraphs.indexOfLast { it.offset <= offset }.coerceAtLeast(0)

/** Literal, non-overlapping matches; UTF-16 ranges remain in the source document. */
fun findTextMatches(body: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return buildList {
        var from = 0
        while (from <= body.length - needle.length) {
            val at = body.indexOf(needle, from, ignoreCase = true)
            if (at < 0) break
            add(at until at + needle.length)
            from = at + needle.length
        }
    }
}

/** Same word snapping as the watch transcript, with invalid anchors rejected. */
internal fun phraseSelectionRange(words: List<IntRange>, anchor: Int, target: Int): IntRange? {
    if (anchor !in words.indices || target !in words.indices) return null
    return com.ziaee.zabankhan.watch.transcriptSelectionRange(words, anchor, target)
}
