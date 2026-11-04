package com.ziaee.zabankhan.ui.reader

data class Paragraph(val text: String, val offset: Int)
/** Keep original UTF-16 offsets (including CRLF) for highlights and reading position. */
fun paragraphs(body: String): List<Paragraph> = Regex("[^\\r\\n]+(?:\\r?\\n(?!\\s*\\r?\\n)[^\\r\\n]+)*").findAll(body)
    .filter { it.value.isNotBlank() }.map { Paragraph(it.value, it.range.first) }.toList()
fun paragraphIndexAt(paragraphs: List<Paragraph>, offset: Int): Int =
    paragraphs.indexOfLast { it.offset <= offset }.coerceAtLeast(0)
