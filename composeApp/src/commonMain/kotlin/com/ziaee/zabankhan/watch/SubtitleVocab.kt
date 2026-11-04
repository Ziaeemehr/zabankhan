package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.srs.VocabStatus

fun subtitleVocabMatches(text: String, statuses: Map<String, VocabStatus>, lang: LanguageSpec): List<VocabMatch> =
    VocabHighlighter(statuses.map { SavedVocab(it.key, it.value) }, lang).findMatches(text)
