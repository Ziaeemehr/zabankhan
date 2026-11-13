package com.ziaee.zabankhan.ui.reader

import com.ziaee.zabankhan.data.VocabEntry
import com.ziaee.zabankhan.lang.*
import kotlin.test.*

class DictionaryLogicTest {
    @Test fun manualLookupUsesChosenSenseExampleButSourceSentenceWins() {
        assertEquals("Source sentence", dictionaryCardText("Source sentence", "Sense example"))
        assertEquals("Sense example", dictionaryCardText("", "Sense example"))
        assertEquals("Sense example", dictionaryCardText("  ", "Sense example"))
        assertEquals("", dictionaryCardText("", ""))
    }
    private fun card(id: Long, text: Long?, sentence: String, word: String = "Read", lang: String = Languages.ENGLISH.code) =
        VocabEntry(id = id, textId = text, sentence = sentence, word = word, lang = lang, createdAtMs = 0, nextReviewAtMs = 0)

    @Test fun exactContextDoesNotOverwriteOtherTextOrSentence() {
        val entries = listOf(card(1, 7, "Read this."), card(2, 8, "Read this."), card(3, 7, "Read that."))
        val state = dictionarySavedState(entries, Languages.ENGLISH, 7, "read", "Read this.")
        assertEquals(1L, state.exactEntry?.id)
        assertEquals(2, state.otherContextCount)
        assertNull(dictionarySavedState(entries, Languages.ENGLISH, 9, "read", "Read this.").exactEntry)
    }

    @Test fun languageAndFullSentenceArePartOfContextIdentity() {
        val entries = listOf(card(1, 7, "Read this.", lang = Languages.FRENCH.code), card(2, 7, "Read this."))
        assertEquals(2L, dictionarySavedState(entries, Languages.ENGLISH, 7, "READ", "Read this.").exactEntry?.id)
        assertNull(dictionarySavedState(entries, Languages.ENGLISH, 7, "read", "Read this").exactEntry)
        assertEquals(1, dictionarySavedState(entries, Languages.ENGLISH, 7, "read", "Different.").otherContextCount)
    }

    @Test fun unsavedDocumentIdsMatchNullCardsButPositiveDocumentIdsStayDistinct() {
        val entries = listOf(card(1, null, "Read this."), card(2, 7, "Read this."))
        for (unsavedId in listOf(-1L, 0L, null)) {
            val state = dictionarySavedState(entries, Languages.ENGLISH, unsavedId, "read", "Read this.")
            assertEquals(1L, state.exactEntry?.id)
            assertEquals(1, state.otherContextCount)
        }
        assertEquals(2L, dictionarySavedState(entries, Languages.ENGLISH, 7, "read", "Read this.").exactEntry?.id)
    }

    @Test fun identityKeepsAccentsAndNormalizesCompatibilityCharacters() {
        assertEquals(dictionaryIdentityKey(" café "), dictionaryIdentityKey("cafe\u0301"))
        assertEquals("read", dictionaryIdentityKey("ＲＥＡＤ"))
        val entries = listOf(card(1, 7, "Same.", word = "côte", lang = Languages.FRENCH.code))
        assertNull(dictionarySavedState(entries, Languages.FRENCH, 7, "cote", "Same.").exactEntry)
    }

    @Test fun lemmasUseActiveLanguageAndNeverSuggestPhraseOrOriginal() {
        val lexicon = InMemoryLemmaLexicon(mapOf("went" to linkedSetOf("went", "go", "wend", "gone", "going")), emptySet())
        assertEquals(listOf("go", "wend", "gone"), lemmaSuggestions("Went", lexicon, Languages.ENGLISH))
        assertTrue(lemmaSuggestions("went home", lexicon, Languages.ENGLISH).isEmpty())
        assertTrue(lemmaSuggestions("", lexicon, Languages.ENGLISH).isEmpty())
        assertTrue(lemmaSuggestions("word", null, Languages.ENGLISH).isEmpty())
    }
}
