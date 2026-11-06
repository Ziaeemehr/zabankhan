package com.ziaee.zabankhan.grammar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.*

class GrammarRenderingTest {
    @Test fun persianExplanationStartingWithLearningTermKeepsRtlDirection() {
        assertTrue(grammarIsRtlText("bien que: با اینکه، هرچند"))
        assertTrue(grammarIsRtlText("`bien que` یعنی با اینکه"))
        assertFalse(grammarIsRtlText("Bien que + subjonctif"))
    }

    @Test fun longFormulasLiftOutOfPersianProseAndKeepShortInlineCode() {
        val segments = splitLongCode("ساختار `je suis` و `sujet + auxiliaire + participe passé` است.")
        assertEquals(3, segments.size)
        assertEquals("ساختار `je suis` و", segments[0].text)
        assertEquals(ProseSegment("sujet + auxiliaire + participe passé", true), segments[1])
        assertFalse(segments[2].longCode)
    }

    @Test fun codeInPersianHasDirectionalIsolatesAndNonbreakingSpaces() {
        val isolated = isolateGrammarText("مثال `je suis` است", rtl = true, markdown = true)
        assertTrue(isolated.text.contains("\u2066je\u00A0suis\u2069"))
        assertFalse('`' in isolated.text)
        val range = isolated.code.single()
        assertEquals("je\u00A0suis", isolated.text.substring(range.start, range.end))
    }

    @Test fun highlightedLearningSpanKeepsCorrectOffsetsInsideRtlText() {
        val cell = GrammarCell("مثال bonjour است", listOf(HighlightRange(5, 12)))
        val text = grammarAnnotatedText(cell, true, Color.Red, Color.Gray)
        val span = text.spanStyles.single()
        assertTrue(text.text.substring(span.start, span.end).startsWith("bonjour"))
        assertEquals(Color.Red, span.item.color)
        assertEquals(FontWeight.Bold, span.item.fontWeight)
    }

    @Test fun rtlLearningCodeUsesRtlIsolate() {
        val isolated = isolateGrammarText("Example `سلام دنیا`", rtl = false, markdown = true)
        assertTrue("\u2067سلام\u00A0دنیا\u2069" in isolated.text)
    }

    @Test fun inlineCodeUsesTargetAndBackgroundColors() {
        val text = grammarAnnotatedText(GrammarCell("با `un ami`"), true, Color.Red, Color.Gray, markdown = true)
        val span = text.spanStyles.single()
        assertEquals("un\u00A0ami", text.text.substring(span.start, span.end))
        assertEquals(Color.Red, span.item.color)
        assertEquals(Color.Gray, span.item.background)
    }

    @Test fun overlappingHighlightsMergeInMarkdownAndTablesEscapePipes() {
        assertEquals("**abcde**f", markdownGrammarCell(GrammarCell("abcdef", listOf(HighlightRange(0,3), HighlightRange(2,5)))))
        val labels = GrammarExportLabels("Usage", "Structure", "Examples", "Mistake", "More", "Comparison", "Timeline", "Decision", "Steps", "Correct", "Wrong", "Zabankhan")
        val note = GrammarNote("one", "basics", "A1", "Title", "عنوان", emptyList(), emptyList(), emptyList(), listOf(
            GrammarBlock.Infographic("comparison", listOf(GrammarCell("A|B")), listOf(listOf(GrammarCell("line\nbreak"))))) )
        val markdown = grammarNoteToMarkdown(note, labels)
        assertTrue("| A\\|B |" in markdown)
        assertTrue("line<br>break" in markdown)
    }
}
