package com.ziaee.zabankhan.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import com.ziaee.zabankhan.data.HighlightEntry
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.lang.tokenizeSurface
import kotlin.test.*

class ReaderTextLogicTest {
    @Test fun selectionKeepsElidedPrefixesAtLineStart() {
        listOf("s'arranger", "l’ami", "d’accord", "qu’il", "j’aime").forEach { word ->
            val text = "Avant\n$word"
            val start = text.indexOf('\n') + 1
            val selection = classifySelection(text, TextRange(start + 3, text.length), Languages.FRENCH)
            assertEquals(TextRange(start, text.length), (selection as SelectionKind.Word).range)
        }
    }

    @Test fun copyUsesRenderedTextAcrossParagraphsAndListItems() {
        val body = "Intro text\n\n- first [link](https://example.com)\n- second item"
        val range = body.indexOf("text") until body.indexOf(" item")
        assertEquals("text\nfirst link\nsecond", selectedReaderText(paragraphs(body), range))
    }

    @Test fun highlightsUseContrastingInkForLegacyAndCustomColors() {
        fun style(key: String) = applyHighlights("word",
            listOf(HighlightEntry(textId = 1, startOffset = 0, endOffset = 4, colorKey = key)),
            surface = Color.Black).spanStyles.single().item
        assertEquals(Color.Black, style("yellow").color)
        val navy = Color(0xFF26456E)
        assertEquals("#FF26456E", navy.toHighlightKey())
        assertEquals(navy, style(navy.toHighlightKey()).background)
        assertEquals(Color.White, style(navy.toHighlightKey()).color)
    }

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
