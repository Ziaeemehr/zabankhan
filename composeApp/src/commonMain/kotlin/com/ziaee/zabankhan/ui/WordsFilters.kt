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
