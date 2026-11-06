package com.ziaee.zabankhan.ui.reader

import androidx.compose.ui.text.TextRange
import com.ziaee.zabankhan.lang.Languages
import kotlin.test.*

class ReaderPortLogicTest {
    @Test fun markdownBoundariesRetainOriginalOffsetsWithCrLf() {
        val body = "# Heading\r\nA **bold** line.\r\n- first item\r\n- second item\r\n\r\nLast paragraph."
        val blocks = paragraphs(body)
        assertEquals(listOf(BlockType.HEADER, BlockType.PARAGRAPH, BlockType.LIST_ITEM,
            BlockType.LIST_ITEM, BlockType.PARAGRAPH), blocks.map { it.block.type })
        assertEquals(listOf("Heading", "A bold line.", "first item", "second item", "Last paragraph."), blocks.map { it.block.plainText })
        assertEquals(body.indexOf("- second"), blocks[3].offset)
        val bold = blocks[1].block.plainText.indexOf("bold")
        assertEquals(body.indexOf("bold"), blocks[1].sourceOffsets[bold])
        assertEquals(body.indexOf("bold") + 3, blocks[1].sourceOffsets[bold + 3])
    }

    @Test fun linksAndWrappedLinesMapToVisibleTextRatherThanUrl() {
        val body = "[word](https://word.test) after\r\nwrapped **bold**."
        val paragraph = paragraphs(body).single()
        val plain = paragraph.block.plainText
        assertEquals("word after wrapped bold.", plain)
        listOf("word", "after", "wrapped", "bold").forEach { word ->
            val display = plain.indexOf(word)
            assertEquals(body.indexOf(word), paragraph.sourceOffsets[display])
            assertEquals(body.indexOf(word) + word.lastIndex, paragraph.sourceOffsets[display + word.lastIndex])
        }
        assertEquals(1, paragraph.block.emphasisSpans.size)
        assertTrue(paragraph.block.emphasisSpans.single().bold)
    }

    @Test fun pipeTableRetainsCellStructureAndWordSourcePositions() {
        val body = "| Name | Value |\n| --- | --- |\n| one | **two** |"
        val paragraph = paragraphs(body).single()
        assertEquals(BlockType.TABLE, paragraph.block.type)
        assertEquals(2, paragraph.block.tableRows.size)
        assertEquals(2, paragraph.block.tableRows.first().size)
        val display = paragraph.block.plainText.indexOf("two")
        assertEquals(body.indexOf("two"), paragraph.sourceOffsets[display])
        assertEquals("two", paragraph.block.plainText.substring(paragraph.block.tableRows[1][1].start,
            paragraph.block.tableRows[1][1].end))
    }

    @Test fun selectionRemainsSingleWordUntilExtendedToPhrase() {
        val text = "A little reader."
        assertEquals(SelectionKind.Word("little", TextRange(2, 8)), classifySelection(text, TextRange(3, 5), Languages.ENGLISH))
        assertEquals(SelectionKind.Phrase("little reader", TextRange(2, 15)), classifySelection(text, TextRange(4, 13), Languages.ENGLISH))
        assertEquals(classifySelection(text, TextRange(4, 13), Languages.ENGLISH), classifySelection(text, TextRange(13, 4), Languages.ENGLISH))
        assertNull(classifySelection(text, TextRange(2), Languages.ENGLISH))
    }

    @Test fun selectionUsesLanguageElisionAndJoinerRules() {
        assertEquals(SelectionKind.Word("appartement", TextRange(2, 13)),
            classifySelection("l’appartement", TextRange(0, 13), Languages.FRENCH))
        val text = "او می‌رود خانه"
        val word = classifySelection(text, TextRange(4, 6), Languages.PERSIAN) as SelectionKind.Word
        assertEquals("می‌رود", word.word)
    }

    @Test fun followDoesNotMoveFittingParagraphOrOversizedParagraphTooSoon() {
        assertNull(followScrollDelta(100f, 300f, 0f, 1000f, .5f, true))
        assertNull(followScrollDelta(0f, 2000f, 0f, 1000f, .3f, false))
        assertEquals(450f, followScrollDelta(0f, 2000f, 0f, 1000f, .4f, false))
        assertEquals(800f, followScrollDelta(800f, 300f, 0f, 1000f, 0f, true))
    }

    @Test fun overlappingHighlightsIncludePartialOverlapAndExcludeTouchingEdges() {
        val highlights = listOf(
            com.ziaee.zabankhan.data.HighlightEntry(id = 1, textId = 9, startOffset = 2, endOffset = 6, colorKey = "yellow"),
            com.ziaee.zabankhan.data.HighlightEntry(id = 2, textId = 9, startOffset = 6, endOffset = 10, colorKey = "blue"))
        assertEquals(listOf(1L), overlappingHighlightIds(TextRange(0, 6), highlights))
        assertEquals(listOf(1L, 2L), overlappingHighlightIds(TextRange(5, 7), highlights))
    }
    @Test fun raggedTablePaddingNeverConsumesFollowingRowSourceOffsets() {
        val body = "|H1|H2|H3|H4|\r\n|---|---|---|---|\r\n|a|\r\n|b|c|d|e|"
        val paragraph = paragraphs(body).single()
        assertEquals("H1\tH2\tH3\tH4\na\t\t\t\nb\tc\td\te", paragraph.block.plainText)
        for (word in listOf("a", "b", "c", "d", "e")) {
            val display = paragraph.block.plainText.indexOf(word)
            assertEquals(body.indexOf(word), paragraph.sourceOffsets[display], word)
        }
    }

    @Test fun processTextSortingExcludesSelfDisambiguatesAndPinsTranslate() {
        val apps = listOf(
            ReaderProcessTextApp("Translate", "translate.two", "Activity", "Second"),
            ReaderProcessTextApp("Own", "self", "Activity", "Own app"),
            ReaderProcessTextApp("Anki", "anki", "Activity", "Anki"),
            ReaderProcessTextApp("Translate", "com.google.android.apps.translate", "Activity", "Google"))
        val sorted = filterAndSortProcessTextApps(apps, "self")
        assertEquals(listOf("Translate (Google)", "Anki", "Translate (Second)"), sorted.map { it.label })
    }
}
