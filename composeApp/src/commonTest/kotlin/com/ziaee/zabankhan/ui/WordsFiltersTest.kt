package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.VocabEntry
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.srs.VocabStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class WordsFiltersTest {
    @Test fun englishPreviewAudioUsesLearningLanguageScript() {
        assertEquals("book", flashcardWordAudioText(VocabEntry(lang = Languages.ENGLISH.code, word = "book")))
        assertEquals(null, flashcardWordAudioText(VocabEntry(lang = Languages.ENGLISH.code, word = "کتاب")))
    }

    @Test fun englishLearningLanguageMatchesMeaningAndSentence() {
        val words = listOf(
            VocabEntry(id = 1, lang = Languages.ENGLISH.code, word = "book", meaning = "کتاب", sentence = "I read every morning.", listId = 7),
            VocabEntry(id = 2, lang = Languages.ENGLISH.code, word = "walk", meaning = "راه رفتن", listId = 8),
        )
        assertEquals(listOf(1L), filterVocabulary(words, -1, "MORNING", null, Languages.ENGLISH).map { it.id })
        assertEquals(listOf(1L), filterVocabulary(words, 7, "کتاب", VocabStatus.NEW, Languages.ENGLISH).map { it.id })
        assertEquals(emptyList(), filterVocabulary(words, 8, "book", null, Languages.ENGLISH))
    }
    @Test fun statusAndUnfiledFiltersIntersect() {
        val words = listOf(
            VocabEntry(id = 1, lang = Languages.ENGLISH.code, word = "new"),
            VocabEntry(id = 2, lang = Languages.ENGLISH.code, word = "known", learned = true),
            VocabEntry(id = 3, lang = Languages.ENGLISH.code, word = "filed", learned = true, listId = 7),
        )
        assertEquals(listOf(2L), filterVocabulary(words, 0, "", VocabStatus.LEARNED, Languages.ENGLISH).map { it.id })
        assertEquals(listOf(1L), filterVocabulary(words, -1, "", VocabStatus.NEW, Languages.ENGLISH).map { it.id })
    }
}
