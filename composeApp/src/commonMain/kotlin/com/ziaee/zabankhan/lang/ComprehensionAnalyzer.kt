package com.ziaee.zabankhan.lang

data class ComprehensionToken(val surface: String, val lemmas: Set<String>)

private val markdownImageLine = Regex("^\\s*!\\[[^]]*]\\([^)]*\\)\\s*$")
private val sentenceInitialPredecessors = setOf('.', '!', '?', '…', '؟', '۔', '«', '»', '"', '—', '–', '-', ':', ';')

/** Unlike lookup tokenization, comprehension counts elided articles and pronouns. */
fun tokenizeForComprehension(text: String, lang: LanguageSpec, lexicon: LemmaLexicon): List<ComprehensionToken> {
    val filtered = text.lineSequence().filterNot { markdownImageLine.matches(it) }.joinToString("\n")
    val prefixes = lang.elisionPrefixes.map { normalizeForMatch(it, lang) }.sortedByDescending(String::length)
    fun splitElision(word: String): List<String> {
        val prefix = prefixes.firstOrNull { word.startsWith(it) && word.length > it.length }
            ?: return listOf(word)
        return listOf(prefix) + splitElision(word.substring(prefix.length))
    }
    return buildList {
        for (token in tokenizeSurface(filtered, lang)) {
            if (token.text.firstOrNull()?.isUpperCase() == true) {
                val previous = (token.start - 1 downTo 0).firstOrNull { !filtered[it].isWhitespace() }
                if (previous != null && '\n' !in filtered.substring(previous, token.start) &&
                    filtered[previous] !in sentenceInitialPredecessors) continue
            }
            for (part in splitElision(normalizeForMatch(token.text, lang))) {
                val forms = if ('-' in part && !lexicon.containsForm(part)) part.split('-').filter(String::isNotBlank)
                    else listOf(part)
                for (form in forms) add(ComprehensionToken(form, lexicon.lemmas(form)))
            }
        }
    }
}

data class ComprehensionResult(val totalWords: Int, val knownWords: Int) {
    val knownWordPercent: Double get() = if (totalWords == 0) 0.0 else knownWords * 100.0 / totalWords
}

class ComprehensionAnalyzer(private val lang: LanguageSpec, private val lexicon: LemmaLexicon) {
    fun analyze(text: String, knownWords: Set<String>): ComprehensionResult {
        val known = (knownWords + lexicon.commonLemmas).flatMap {
            lexicon.lemmas(normalizeForMatch(it, lang))
        }.toSet()
        val tokens = tokenizeForComprehension(text, lang, lexicon)
        return ComprehensionResult(tokens.size, tokens.count { token -> token.lemmas.any { it in known } })
    }
}
