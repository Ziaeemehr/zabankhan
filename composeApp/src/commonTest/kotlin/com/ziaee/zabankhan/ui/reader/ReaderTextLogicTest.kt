package com.ziaee.zabankhan.ui.reader

import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.lang.tokenizeSurface
import kotlin.test.*

class ReaderTextLogicTest {
    @Test fun phraseSelectionSnapsBothDirectionsAndKeepsPunctuation() {
        val text = "l’ami,  vient!"
        val words = tokenizeSurface(text, Languages.FRENCH).map { it.start until it.end }
        assertEquals(0..12, phraseSelectionRange(words, 0, 1))
        assertEquals(0..12, phraseSelectionRange(words, 1, 0))
        assertEquals("l’ami,  vient", text.substring(phraseSelectionRange(words, 1, 0)!!))
        assertNull(phraseSelectionRange(words, -1, 0))
        assertNull(phraseSelectionRange(emptyList(), 0, 0))
    }
    @Test fun persianPhraseKeepsJoinersAndOriginalOffsets() {
        val text = "او می‌رود، خانه."
        val words = tokenizeSurface(text, Languages.PERSIAN).map { it.start until it.end }
        assertEquals("می‌رود، خانه", text.substring(phraseSelectionRange(words, 2, 1)!!))
    }
    @Test fun findIsLiteralCaseInsensitiveAndUsesUtf16Offsets() {
        assertEquals(listOf(3..6, 8..11), findTextMatches("😀 Café CAFÉ", "café"))
        assertEquals(listOf(0..2, 5..7), findTextMatches("سلام سلام", "سلا"))
        assertEquals(listOf(2..3), findTextMatches("a .* b", ".*"))
        assertEquals(emptyList(), findTextMatches("abc", "  "))
        assertEquals(emptyList(), findTextMatches("abc", "missing"))
        assertEquals(listOf(0..1, 2..3), findTextMatches("aaaa", "aa"))
    }
}
