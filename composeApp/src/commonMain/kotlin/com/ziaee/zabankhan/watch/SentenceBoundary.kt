package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.*

private val frenchAbbreviations = setOf(
    "m.", "mm.", "mme.", "mmes.", "mlle.", "mlles.", "dr.", "drs.", "pr.", "prs.",
    "st.", "ste.", "sts.", "stes.", "cf.", "p.", "pp.", "n°", "n°.", "av.", "apr.",
    "j.-c.", "ex.", "env.", "fig.", "vol.", "éd.", "chap.", "art."
)
private val capitalInitial = Regex("""\p{Lu}\.""")
private const val boundaryQuotes = "»«\"“”‘’')]}"

/** Shared by timed-text splitting and sentence joins; [nextText] supplies token lookahead. */
fun isSentenceEnd(text: String, nextText: String, lang: LanguageSpec): Boolean {
    val end = text.trimEnd { it.isWhitespace() || it in boundaryQuotes }
    val next = nextText.firstOrNull { !it.isWhitespace() && it !in boundaryQuotes && it !in "([{" }
    if (end.endsWith("…") || end.endsWith("...")) return next?.isUpperCase() == true
    if (end.endsWith("!") || end.endsWith("?")) return true
    if (!end.endsWith(".")) return false
    val token = end.takeLastWhile { !it.isWhitespace() }.trimStart { it in boundaryQuotes || it in "([{" }
    if (token.equals("etc.", ignoreCase = true)) {
        return next == null || (!next.isLowerCase() && !next.isDigit())
    }
    if (token.lowercase() in (if (lang.code == Languages.FRENCH.code) frenchAbbreviations else englishAbbreviations) || capitalInitial.matches(token)) return false
    // A timestamped segment can end between the decimal/grouping dot and its digits.
    if (end.getOrNull(end.lastIndex - 1)?.isDigit() == true && next?.isDigit() == true) return false
    return true
}

private val englishAbbreviations = setOf("mr.", "mrs.", "ms.", "dr.", "prof.", "sr.", "jr.", "st.", "vs.", "e.g.", "i.e.", "fig.", "vol.", "no.")
