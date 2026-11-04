package com.ziaee.zabankhan.lang

import kotlin.test.*

class LanguageTest {
    @Test fun lookupAndSurfaceElisions() {
        val text = "L’appartement est grand."
        assertEquals("appartement", tokenize(text, Languages.FRENCH).first().text)
        assertEquals("L’appartement", tokenizeSurface(text, Languages.FRENCH).first().text)
        assertEquals(Token("appartement", 2, 13), wordAt(text, 5, Languages.FRENCH))
    }
    @Test fun englishWordsKeepContractionsAndHyphens() {
        assertEquals(listOf("Don't", "re-enter"), tokenize("Don't re-enter!", Languages.ENGLISH).map { it.text })
    }
    @Test fun persianWordsKeepJoinersAndMarks() {
        assertEquals(listOf("می‌روم", "خانه"), tokenize("می‌روم، خانه", Languages.PERSIAN).map { it.text })
        assertEquals("یک کتاب", normalizeForMatch("يَك كِتابـ", Languages.PERSIAN))
    }
    @Test fun foldsAccentsAndPreservesOffsets() {
        val folded = foldWithOffsets("Œuf e\u0301té", Languages.FRENCH)
        assertEquals("oeuf ete", folded.text)
        assertEquals(listOf(0, 0, 1, 2, 3, 4, 6, 7), folded.originalOffsets.toList())
        assertEquals(6, folded.originalEndOffsets[5])
        assertEquals("lodz aesir", normalizeForMatch("Łódź Æsir", Languages.ENGLISH))
    }
    @Test fun sentenceRangesRetainSourceOffsets() {
        val text = "سلام؟ خوبی۔\n\nبله!"
        assertEquals(listOf("سلام؟", "خوبی۔", "بله!"), splitSentences(text, Languages.PERSIAN).map { text.substring(it) })
        val english = "Hello. Émile smiles! 42 birds… «Next»"
        assertEquals(4, splitSentences(english, Languages.ENGLISH).size)
        assertEquals(IntRange.EMPTY, sentenceAround("", 0, Languages.ENGLISH))
    }
    @Test fun dictionaryWordsAreEncodedAsUtf8() {
        assertEquals("a%20b%2F%C3%A9", percentEncode("a b/é"))
        assertTrue(Languages.PERSIAN.dictionaryLinks.first().urlFor("کتاب").contains("%"))
        assertEquals(Languages.ENGLISH, Languages.byCode("EN"))
    }
}
