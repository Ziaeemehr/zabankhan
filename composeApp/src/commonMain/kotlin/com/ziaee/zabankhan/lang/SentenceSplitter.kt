package com.ziaee.zabankhan.lang

/** Nonempty trimmed sentence ranges into the original text. */
fun splitSentences(text: String, lang: LanguageSpec): List<IntRange> = buildList {
    var start = 0
    fun emit(end: Int) {
        var first = start
        var last = end
        while (first < last && text[first].isWhitespace()) first++
        while (last > first && text[last - 1].isWhitespace()) last--
        if (first < last) add(first until last)
        start = end
    }
    var index = 0
    while (index < text.length) {
        if (text[index] == '\n' || text[index] == '\r') {
            var next = index + 1
            while (next < text.length && text[next].isWhitespace()) next++
            val breaks = text.substring(index, next).count { it == '\n' }
            if (breaks >= 2) { emit(index); start = next; index = next; continue }
        }
        if (text[index] in ".!?…؟۔") {
            var end = index + 1
            while (end < text.length && text[end] in ".!?…؟۔\"'»”’)]}") end++
            var next = end
            while (next < text.length && text[next].isWhitespace()) next++
            val nextChar = text.getOrNull(next)
            val canStart = nextChar == null || nextChar.isUpperCase() || nextChar.isDigit() ||
                nextChar in "\"'«“‘([{" || (lang.script != Script.LATIN && nextChar.isLetter())
            if (next == text.length || (next > end && canStart)) {
                emit(end); start = next; index = next; continue
            }
        }
        index++
    }
    emit(text.length)
}
