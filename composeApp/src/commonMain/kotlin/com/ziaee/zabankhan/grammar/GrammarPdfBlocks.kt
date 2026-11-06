package com.ziaee.zabankhan.grammar

/**
 * Text with bold ranges (UTF-16 indices). [rtl] fixes the paragraph direction; left null, the text
 * is right-to-left as soon as it holds any Persian letter, because a Persian sentence often starts
 * with a French term.
 */
data class GrammarPdfText(val text: String, val bold: List<IntRange> = emptyList(), val rtl: Boolean? = null) {
    val isRtl: Boolean get() = rtl ?: text.any {
        it.isLetter() && (it.code in 0x0590..0x08FF || it.code in 0xFB1D..0xFDFF || it.code in 0xFE70..0xFEFF)
    }
}

/** What a PDF export is made of. Pure data, so building it from content is unit-testable. */
sealed interface GrammarPdfBlock {
    data class Heading(val text: String, val level: Int) : GrammarPdfBlock
    data class Paragraph(val text: GrammarPdfText, val quote: Boolean = false) : GrammarPdfBlock
    data class Table(val header: List<GrammarPdfText>, val rows: List<List<GrammarPdfText>>) : GrammarPdfBlock
}

/**
 * Turns `code` spans into left-to-right isolates, so a French span inside a Persian line keeps its
 * word order, and drops the backticks.
 */
fun pdfInline(text: String): String = buildString {
    var i = 0
    while (i < text.length) {
        val end = if (text[i] == '`') text.indexOf('`', i + 1) else -1
        if (end > i) {
            append('⁦').append(text, i + 1, end).append('⁩')
            i = end + 1
        } else append(text[i++])
    }
}
