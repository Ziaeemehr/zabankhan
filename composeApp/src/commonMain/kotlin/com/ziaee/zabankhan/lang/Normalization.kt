package com.ziaee.zabankhan.lang

/** UTF-16 offsets; end offsets also include any removed trailing combining marks. */
data class FoldedText(val text: String, val originalOffsets: IntArray, val originalEndOffsets: IntArray)

private val latinFolds: Map<Char, String> = buildMap {
    for ((letters, folded) in listOf(
        "àáâãäåāăąǎǟǡǻȁȃȧ" to "a", "çćĉċč" to "c", "ďđð" to "d",
        "èéêëēĕėęěȅȇȩ" to "e", "ĝğġģǧ" to "g", "ĥħȟ" to "h",
        "ìíîïĩīĭįıǐȉȋ" to "i", "ĵǰ" to "j", "ķĸǩ" to "k",
        "ĺļľŀł" to "l", "ñńņňŉŋǹ" to "n", "òóôõöøōŏőǒǫǭǿȍȏȯ" to "o",
        "ŕŗřȑȓ" to "r", "śŝşšșſ" to "s", "ţťŧț" to "t",
        "ùúûüũūŭůűųǔǖǘǚǜȕȗ" to "u", "ŵẁẃẅ" to "w",
        "ýÿŷȳ" to "y", "źżž" to "z", "œ" to "oe", "æǣǽ" to "ae", "þ" to "th",
    )) for (letter in letters) put(letter, folded)
}

private fun isCombiningMark(c: Char): Boolean = c.category in setOf(
    CharCategory.NON_SPACING_MARK, CharCategory.COMBINING_SPACING_MARK, CharCategory.ENCLOSING_MARK,
)

private fun foldChar(c: Char, lang: LanguageSpec): String {
    if (isCombiningMark(c)) return ""
    if (lang.script == Script.ARABIC) return when (c) {
        'ي' -> "ی"
        'ك' -> "ک"
        'ـ' -> ""
        else -> c.lowercase()
    }
    val lower = c.lowercase()
    return latinFolds[lower.singleOrNull()] ?: when (c) {
        '’', '‘', 'ʼ', '`' -> "'"
        else -> lower
    }
}

fun normalizeForMatch(text: String, lang: LanguageSpec): String = buildString {
    for (c in text) append(foldChar(c, lang))
}

fun foldWithOffsets(text: String, lang: LanguageSpec): FoldedText {
    val result = StringBuilder()
    val starts = mutableListOf<Int>()
    val ends = mutableListOf<Int>()
    text.forEachIndexed { index, c ->
        val piece = foldChar(c, lang)
        if (piece.isEmpty()) {
            // Extend every output character belonging to the preceding source character.
            if (ends.isNotEmpty()) {
                val previousStart = starts.last()
                var i = ends.lastIndex
                while (i >= 0 && starts[i] == previousStart) { ends[i] = index + 1; i-- }
            }
        } else for (out in piece) {
            result.append(out); starts += index; ends += index + 1
        }
    }
    return FoldedText(result.toString(), starts.toIntArray(), ends.toIntArray())
}
