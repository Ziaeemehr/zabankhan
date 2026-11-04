package com.ziaee.zabankhan.lang

import com.ziaee.zabankhan.srs.VocabStatus

data class SavedVocab(val word: String, val status: VocabStatus)
data class VocabMatch(val range: IntRange, val status: VocabStatus)

fun normalizeVocabWord(word: String, lang: LanguageSpec): String = normalizeForMatch(word, lang).trim()

fun buildVocabStatusMap(entries: Iterable<SavedVocab>, lang: LanguageSpec): Map<String, VocabStatus> {
    val result = linkedMapOf<String, VocabStatus>()
    for (entry in entries) {
        val word = normalizeVocabWord(entry.word, lang)
        if (word.isEmpty()) continue
        val previous = result[word]
        if (previous == null || entry.status.ordinal < previous.ordinal) result[word] = entry.status
    }
    return result
}

class VocabHighlighter(entries: Iterable<SavedVocab>, private val lang: LanguageSpec) {
    private val prefixes = lang.elisionPrefixes.map { normalizeForMatch(it, lang).removeSuffix("'") }.toSet()
    private val candidatesByFirstChar = buildVocabStatusMap(entries, lang).entries
        .groupBy({ it.key.first() }, { it.key to it.value })
        .mapValues { (_, candidates) -> candidates.sortedByDescending { it.first.length } }

    fun findMatches(text: String): List<VocabMatch> {
        if (candidatesByFirstChar.isEmpty() || text.isEmpty()) return emptyList()
        val normalized = foldWithOffsets(text, lang)
        val matches = mutableListOf<VocabMatch>()
        var index = 0
        while (index < normalized.text.length) {
            val c = normalized.text[index]
            if (!c.isLetterOrDigit() || !isStartBoundary(normalized.text, index)) {
                index++
                continue
            }
            val match = candidatesByFirstChar[c]?.firstOrNull { (word, _) ->
                normalized.text.regionMatches(index, word, 0, word.length) &&
                    isEndBoundary(normalized.text, index + word.length)
            }
            if (match == null) {
                index++
                continue
            }
            val end = index + match.first.length - 1
            matches += VocabMatch(normalized.originalOffsets[index] until normalized.originalEndOffsets[end], match.second)
            index = end + 1
        }
        return matches
    }

    private fun isStartBoundary(text: String, index: Int): Boolean {
        if (index == 0) return true
        if (text[index - 1] != '\'') return !isWordPart(text[index - 1])
        var prefixStart = index - 2
        while (prefixStart >= 0 && text[prefixStart].isLetter()) prefixStart--
        return text.substring(prefixStart + 1, index - 1) in prefixes
    }
    private fun isEndBoundary(text: String, endExclusive: Int): Boolean =
        endExclusive == text.length || !isWordPart(text[endExclusive])
    private fun isWordPart(c: Char): Boolean = c.isLetterOrDigit() || c == '\'' || c == '-' || c == '\u200C'
}
