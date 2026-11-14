package com.ziaee.zabankhan.ui.reader

import com.ziaee.zabankhan.content.markdownToPlainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownColorTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun colors(block: ParsedBlock): List<Int?> = block.plainText.indices.map { index ->
        block.emphasisSpans.lastOrNull { it.colorArgb != null && index in it.start until it.end }?.colorArgb
    }

    @Test fun caseInsensitiveColorPreservesEmphasisInBothOrders() {
        for (raw in listOf(
            "<SPAN style=\"COLOR: '#f08'\">**word**</SPAN>",
            "**<span style='color: #F08'>word</span>**",
            "<span style='color: #ff0088'>*word*</span>",
            "*<span style='color: #ff0088'>word</span>*"
        )) {
            val block = MarkdownParser.parse(raw)
            assertEquals("word", block.plainText)
            assertEquals(List(4) { 0xFFFF0088.toInt() }, colors(block), raw)
            assertTrue(block.emphasisSpans.any { (it.bold || it.italic) && it.start == 0 && it.end == 4 }, raw)
        }
    }

    @Test fun colorRangesFollowVisibleTextAfterRemovedMarkdownSyntax() {
        val block = MarkdownParser.parse("**before** <span style='color: red'>**red**</span> after")
        assertEquals("before red after", block.plainText)
        assertEquals(List(7) { null } + List(3) { red } + List(6) { null }, colors(block))
    }

    @Test fun nestedColorsRestoreOuterColorAndInvalidColorsInherit() {
        val block = MarkdownParser.parse(
            "<span style='color: red'>a<span style='color: blue'>b</span>" +
                "<span style='color: invalid'>c</span>d</span>e"
        )
        assertEquals("abcde", block.plainText)
        assertEquals(listOf(red, blue, red, red, null), colors(block))
    }

    @Test fun invalidColorsDoNotLeakMarkupIntoDisplayOrSpeech() {
        for (value in listOf("not-a-color", "#xyz", "#12", "#+12345", "rgb(1,2,3)", "red !important")) {
            val block = MarkdownParser.parse("<span style=\"color: $value\">word</span>")
            assertEquals("word", block.plainText)
            assertEquals("word", block.spokenText)
            assertEquals(List(4) { null }, colors(block), value)
        }
    }

    @Test fun unrelatedAttributesDoNotSetColorAndLastValidDeclarationWins() {
        assertEquals(List(4) { null }, colors(MarkdownParser.parse("<span data-style='color: red'>word</span>")))
        val block = MarkdownParser.parse("<span title='a > b' style='background-color: red; color: blue; color: invalid'>word</span>")
        assertEquals("word", block.plainText)
        assertEquals(List(4) { blue }, colors(block))
    }

    @Test fun incompleteAndDeeplyNestedSpansAreBounded() {
        val block = MarkdownParser.parse("<span style='color: red'>word")
        assertEquals("word", block.plainText)
        assertEquals(List(4) { red }, colors(block))
        assertEquals("word", MarkdownParser.parse("<span>".repeat(1000) + "word" + "</span>".repeat(1000)).plainText)
        assertEquals("<span style='color: red'", MarkdownParser.parse("<span style='color: red'").plainText)
    }

    @Test fun tableCellsRetainColorRunsAndSourcePositions() {
        val raw = "| A | B |\n|---|---|\n| <span style='color: blue'>blue</span> | plain |"
        val paragraph = paragraphs(raw).single()
        val block = paragraph.block
        assertEquals(BlockType.TABLE, block.type)
        assertEquals("A\tB\nblue\tplain", block.plainText)
        val cell = block.tableRows[1][0]
        assertEquals(List(4) { blue }, colors(block).subList(cell.start, cell.end))
        val original = raw.indexOf(">blue") + 1
        assertEquals((original until original + 4).toList(), paragraph.sourceOffsets.subList(cell.start, cell.end))
    }

    @Test fun originalOffsetsSkipSpanAttributesForRepeatedLettersAndRtlText() {
        for (text in listOf("#abc", "span red", "متن فارسی")) {
            val raw = "<span style=\"color: #abc\">$text</span>"
            val paragraph = paragraphs(raw).single()
            assertEquals(text, paragraph.block.plainText)
            val start = raw.indexOf('>') + 1
            assertEquals((start until start + text.length).toList(), paragraph.sourceOffsets)
        }
    }

    @Test fun importedMarkdownRetainsColor() {
        val imported = markdownToPlainText("<span style=\"color: red\">**word**</span>")
        val block = paragraphs(imported).single().block
        assertEquals("word", block.plainText)
        assertEquals(List(4) { red }, colors(block))
    }

    @Test fun headingsListsAndLinksKeepColorWithoutColoringFollowingText() {
        for (raw in listOf(
            "# <span style='color: blue'>word</span> after",
            "- <span style='color: blue'>word</span> after",
            "[<span style='color: blue'>**word**</span>](https://example.invalid) after"
        )) {
            val block = MarkdownParser.parse(raw)
            assertEquals("word after", block.plainText)
            assertEquals(List(4) { blue } + List(6) { null }, colors(block), raw)
        }
    }
}
