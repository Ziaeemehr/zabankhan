package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.*

data class IdiomEntry(val expression: String, val pos: String, val gloss: String, val lang: LanguageSpec) {
    val words: List<String> = tokenizeSubtitle(expression, lang).filter { it.isWord }.map { normalizeForMatch(it.text, lang) }
}

class InMemoryIdiomLexicon(entries: List<IdiomEntry>) {
    val byFirstWord: Map<String, List<IdiomEntry>> = entries.filter { it.words.size >= 2 }.groupBy { it.words.first() }
}

/** Character offsets are UTF-16, with an exclusive end, like Compose annotations. */
data class IdiomMatch(val startChar: Int, val endChar: Int, val expression: String, val gloss: String)

class IdiomMatcher(private val idioms: InMemoryIdiomLexicon, private val lang: LanguageSpec, private val lemmas: LemmaLexicon? = null) {
    private data class Token(val surface: String, val lemmas: Set<String>, val start: Int, val end: Int, val segment: Int)

    fun match(text: String): List<IdiomMatch> {
        var offset = 0
        // An expression never spans a sentence end, a line break (speaker change) or a number.
        var segment = 0
        val tokens = tokenizeSubtitle(text, lang).mapNotNull { token ->
            val start = offset
            offset += token.text.length
            if (!token.isWord) {
                if (token.text.any { it in ".!?…;:\n" || it.isDigit() }) segment++
                null
            } else {
                val surface = normalizeForMatch(token.text, lang)
                Token(surface, lemmas?.lemmas(surface).orEmpty().map { normalizeForMatch(it, lang) }.toSet(), start, offset, segment)
            }
        }
        return buildList {
            var start = 0
            while (start < tokens.size) {
                val first = tokens[start]
                val keys = (first.lemmas + first.surface).toMutableSet()
                if (lang.code == Languages.FRENCH.code && first.surface in reflexives) keys.addAll(listOf("se", "s'"))
                val candidates = keys.flatMap { idioms.byFirstWord[it].orEmpty() }.distinct()
                var best: IdiomEntry? = null
                var bestEnd = -1
                for (entry in candidates) {
                    // Common nominal pairs are noisy; no exceptions are currently needed by the data.
                    if (entry.words.size == 2 && entry.pos != "verb" && entry.words.all { it in lemmas?.commonLemmas.orEmpty() }) continue
                    val gaps = entry.words.size != 2 || entry.pos == "verb"
                    fun walk(word: Int, previous: Int): Int {
                        if (word == entry.words.size) return previous
                        var end = -1
                        for (next in previous + 1..minOf(previous + if (gaps) 3 else 1, tokens.lastIndex)) {
                            if (tokens[next].segment != first.segment) break
                            if (next > previous + 1 && !isGap(tokens[next - 1])) break
                            if (matches(entry.words[word], tokens[next])) end = maxOf(end, walk(word + 1, next))
                        }
                        return end
                    }
                    if (!matches(entry.words.first(), first)) continue
                    val end = walk(1, start)
                    if (end >= 0 && (best == null || entry.words.size > best.words.size ||
                                (entry.words.size == best.words.size && end > bestEnd))) {
                        best = entry
                        bestEnd = end
                    }
                }
                val chosen = best
                if (chosen == null) start++ else {
                    add(IdiomMatch(first.start, tokens[bestEnd].end, chosen.expression, chosen.gloss))
                    start = bestEnd + 1
                }
            }
        }
    }

    private fun matches(word: String, token: Token): Boolean = when (word) {
        "se", "s'" -> if (lang.code == Languages.FRENCH.code) token.surface in reflexives else token.surface == word
        "en", "y" -> token.surface == word
        else -> word == token.surface || word in token.lemmas
    }

    private fun isGap(token: Token): Boolean = if (lang.code == Languages.FRENCH.code) token.surface in gapWords || token.lemmas.any { it == "avoir" || it == "etre" } else token.surface in setOf("not", "never", "really", "already", "always", "still", "very")

    private companion object {
        val reflexives = setOf("me", "m'", "te", "t'", "se", "s'", "nous", "vous")
        val gapWords = reflexives + setOf("le", "la", "les", "l'", "lui", "leur", "en", "y", "ne", "n'", "pas", "plus", "jamais", "rien",
            "bien", "deja", "vraiment", "toujours", "encore", "trop", "tres")
    }
}

fun loadIdiomLexicon(tsv: String, lang: LanguageSpec): InMemoryIdiomLexicon = InMemoryIdiomLexicon(tsv.lineSequence().mapNotNull { line ->
    val parts = line.split('\t', limit = 3)
    if (parts.size == 3) IdiomEntry(parts[0], parts[1], parts[2], lang) else null
}.toList())
