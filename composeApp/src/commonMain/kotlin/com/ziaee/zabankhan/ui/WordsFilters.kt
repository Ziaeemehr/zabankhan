package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.VocabEntry
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.normalizeForMatch
import com.ziaee.zabankhan.srs.VocabStatus
import com.ziaee.zabankhan.srs.status

/** Scope, status and text filters are intersected; counts stay scoped to the selected deck. */
fun filterVocabulary(words: List<VocabEntry>, deck: Long, query: String, status: VocabStatus?, language: LanguageSpec): List<VocabEntry> {
    val needle = normalizeForMatch(query, language)
    return words.filter { entry ->
        (deck == -1L || (entry.listId ?: 0L) == deck) &&
            (status == null || entry.srsState().status() == status) &&
            normalizeForMatch(entry.word + " " + entry.meaning.orEmpty() + " " + entry.sentence, language).contains(needle)
    }
}

internal fun vocabularyCompletions(words: List<VocabEntry>, query: String, language: LanguageSpec, lexiconWords: Set<String> = emptySet()): List<String> {
    val prefix = normalizeForMatch(query.trim(), language)
    if (prefix.isBlank()) return emptyList()
    return (words.asSequence().map { it.word } + lexiconWords.asSequence()).distinct().filter { word ->
        val normalized = normalizeForMatch(word, language)
        normalized.startsWith(prefix) && normalized.length > prefix.length
    }.take(8).toList()
}

/** Spreadsheet examples follow the active pack's language, including English learning screens. */
internal fun wordsCsvSample(language: String): String = when (language) {
    "fr" -> """word,meaning,sentence,deck
bonjour,hello / سلام,"Bonjour, comment ça va ?",Basics
merci,thank you / مرسی,Merci beaucoup !,Basics
la gare,train station / ایستگاه قطار,Je vais à la gare.,Voyage
"""
    else -> """word,meaning,sentence,deck
hello,سلام,"Hello, how are you?",Basics
thank you,متشکرم,Thank you very much!,Basics
the station,ایستگاه,I am going to the station.,Travel
"""
}
