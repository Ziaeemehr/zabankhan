package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.normalizeForMatch

data class WordResult(val word: String, val matched: Boolean)
data class AlignmentResult(val words: List<WordResult>, val matched: Int, val total: Int)

/** Word-level comparison of a sentence with what the recognizer heard. Pure, no Android deps. */
object TranscriptAligner {
    private fun displayTokens(text: String, lang: LanguageSpec): List<String> =
        text.split(Regex("[\\s\\-–—]+")).flatMap { raw ->
            val word = raw.trim { !it.isLetterOrDigit() && it !in "'’ʼ`´" }
            val unified = word.replace('’', '\'').replace('ʼ', '\'').replace('`', '\'').replace('´', '\'')
            val prefix = lang.elisionPrefixes.sortedByDescending { it.length }.firstOrNull {
                unified.startsWith(it, ignoreCase = true) && unified.length > it.length
            }
            if (prefix == null) listOf(word) else listOf(word.take(prefix.length), word.drop(prefix.length))
        }.filter { key(it, lang).isNotEmpty() }

    private fun key(token: String, lang: LanguageSpec): String =
        normalizeForMatch(token.replace('`', '\'').replace('´', '\''), lang).filter { it.isLetterOrDigit() }

    fun normalizeTokens(text: String, lang: LanguageSpec): List<String> = displayTokens(text, lang).map { key(it, lang) }

    fun align(original: String, transcript: String, lang: LanguageSpec): AlignmentResult {
        val display = displayTokens(original, lang)
        val a = display.map { key(it, lang) }
        val b = normalizeTokens(transcript, lang)
        // LCS table: extra spoken words cost nothing, and each original word is matched or not.
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) {
                dp[i + 1][j + 1] + 1
            } else {
                maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val matched = BooleanArray(a.size)
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> {
                    matched[i] = true
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        val words = display.mapIndexed { idx, w -> WordResult(w, matched[idx]) }
        return AlignmentResult(words, matched.count { it }, words.size)
    }
}
