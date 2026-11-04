package com.ziaee.zabankhan.lang

/** [start] is inclusive, [end] exclusive, in the original string's UTF-16 indices. */
data class Token(val text: String, val start: Int, val end: Int)

private fun isMark(c: Char): Boolean = c.category == CharCategory.NON_SPACING_MARK ||
    c.category == CharCategory.COMBINING_SPACING_MARK || c.category == CharCategory.ENCLOSING_MARK
private fun isJoiner(c: Char): Boolean = c == '\'' || c == '’' || c == 'ʼ' || c == '-'
private fun isBody(c: Char, lang: LanguageSpec): Boolean = c.isLetter() || isMark(c) ||
    (lang.script == Script.ARABIC && c == '\u200C')

fun tokenizeSurface(text: String, lang: LanguageSpec): List<Token> = buildList {
    var index = 0
    while (index < text.length) {
        if (!text[index].isLetter()) { index++; continue }
        val start = index++
        while (index < text.length) {
            if (isBody(text[index], lang)) index++
            else if (isJoiner(text[index]) && index + 1 < text.length && text[index + 1].isLetter()) index++
            else break
        }
        // The original tokenizer retained a terminal elision apostrophe.
        if (index < text.length && text[index] in "'’ʼ") index++
        add(Token(text.substring(start, index), start, index))
    }
}

fun tokenize(text: String, lang: LanguageSpec): List<Token> = tokenizeSurface(text, lang).map { token ->
    var start = token.start
    while (start < token.end) {
        val normalized = normalizeForMatch(text.substring(start, token.end), lang)
        val prefix = lang.elisionPrefixes.sortedByDescending { it.length }.firstOrNull {
            normalized.startsWith(normalizeForMatch(it, lang)) && normalized.length > it.length
        } ?: break
        start += prefix.length
    }
    Token(text.substring(start, token.end), start, token.end)
}

fun wordAt(text: String, offset: Int, lang: LanguageSpec): Token? {
    if (text.isEmpty()) return null
    val position = offset.coerceIn(0, text.length)
    val tokens = tokenize(text, lang)
    return tokens.firstOrNull { position >= it.start && position < it.end }
        ?: tokens.firstOrNull { position == it.end }
}

fun sentenceAround(text: String, offset: Int, lang: LanguageSpec): IntRange {
    val ranges = splitSentences(text, lang)
    val position = offset.coerceIn(0, text.length)
    return ranges.firstOrNull { position in it }
        ?: ranges.firstOrNull { it.first > position }
        ?: ranges.lastOrNull() ?: IntRange.EMPTY
}
