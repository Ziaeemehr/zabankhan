package com.ziaee.zabankhan.pdf

import com.ziaee.zabankhan.lang.Languages
import kotlin.test.*

class PdfWordsTest {
    private fun glyphs(text: String) = text.mapIndexed { i, c ->
        PdfCharacter(c.toString(), PdfRect(i / 100f, .1f, (i + 1) / 100f, .2f))
    }
    @Test fun groupsApostrophesCombiningMarksAndPersianZwnj() {
        val words = groupPdfCharacters(glyphs("l’école, cafe\u0301 می‌روم!"))
        assertEquals(listOf("l’école", "cafe\u0301", "می‌روم!"), words.map { it.text })
        assertEquals(listOf(0, 1, 2), words.map { it.index })
        assertEquals(.0f, words.first().rect.left)
        assertEquals(.07f, words.first().rect.right)
    }
    @Test fun explicitGlyphGapsAndLigaturesProduceStableWordIndices() {
        val b = PdfRect(.1f,.2f,.3f,.4f)
        val words = groupPdfCharacters(listOf(PdfCharacter("fi", b), PdfCharacter("n", b), PdfCharacter("suite", b, true)))
        assertEquals(listOf("fin","suite"), words.map { it.text })
        assertEquals(b, words.first().rect)
    }
    @Test fun retainsSentenceBoundariesInBothScripts() {
        val words = groupPdfCharacters(glyphs("Hello! Next. چرا؟ بله."))
        assertEquals("Hello!", sentenceAround(words, 0))
        assertEquals("Next.", sentenceAround(words, 1))
        assertEquals("چرا؟", sentenceAround(words, 2))
    }
    @Test fun cacheRoundTripAndInvalidCoordinates() {
        val words = listOf(PdfWord("می‌روم", PdfRect(.1f,.2f,.3f,.4f), 0))
        assertEquals(words, decodePdfWords(encodePdfWords(words)))
        assertTrue(decodePdfWords("NaN\t0\t1\t1\tbad\n0\t0\t2\t1\tbad\nbroken").isEmpty())
    }
    @Test fun defaultCorrectorNeedsNoLexiconAndEnglishHasNoFrenchSpacing() {
        assertEquals("Hello!", OcrTextCorrector(language = Languages.ENGLISH).correct("Hello!").text)
        assertEquals("سلام!", OcrTextCorrector(language = Languages.PERSIAN).correct("سلام!").text)
    }
    @Test fun progressIsClamped() {
        assertEquals(0f, PdfReadingProgress(-1, 12).fraction)
        assertEquals(1f, PdfReadingProgress(20, 12).fraction)
    }
}

class PdfLookupWordTest {
    @kotlin.test.Test fun stripsElisionAndPunctuation() {
        kotlin.test.assertEquals("appartement", pdfLookupWord("L'appartement", "fr"))
        kotlin.test.assertEquals("grand", pdfLookupWord("grand.", "fr"))
        kotlin.test.assertEquals("house", pdfLookupWord("house,", "en"))
    }
}
