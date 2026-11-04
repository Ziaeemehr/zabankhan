package com.ziaee.zabankhan.lang

import com.ziaee.zabankhan.srs.VocabStatus
import kotlin.test.assertEquals
import kotlin.test.Test

class VocabHighlighterTest {
    @Test
    fun matchesIgnoringAccentsAndCase() {
        val highlighter = VocabHighlighter(listOf(SavedVocab("ecole", VocabStatus.NEW)), Languages.FRENCH)

        assertEquals(
            listOf(VocabMatch(2..6, VocabStatus.NEW)),
            highlighter.findMatches("L'ÉCOLE ouvre.")
        )
    }

    @Test
    fun matchesAfterFrenchElisionAndUnifiesApostrophes() {
        val highlighter = VocabHighlighter(
            listOf(
                SavedVocab("exemple", VocabStatus.LEARNING),
                SavedVocab("aujourd'hui", VocabStatus.REVIEWING)
            ), Languages.FRENCH
        )

        assertEquals(
            listOf(
                VocabMatch(2..8, VocabStatus.LEARNING),
                VocabMatch(11..21, VocabStatus.REVIEWING)
            ),
            highlighter.findMatches("L’exemple, aujourd’hui.")
        )
    }

    @Test
    fun doesNotMatchInsideLongerWords() {
        val highlighter = VocabHighlighter(listOf(SavedVocab("chat", VocabStatus.NEW)), Languages.FRENCH)

        assertEquals(
            listOf(VocabMatch(11..14, VocabStatus.NEW)),
            highlighter.findMatches("Un chaton, chat !")
        )
    }

    @Test
    fun matchesLongestPhraseBeforeItsWords() {
        val highlighter = VocabHighlighter(
            listOf(
                SavedVocab("pomme", VocabStatus.NEW),
                SavedVocab("pomme de terre", VocabStatus.REVIEWING)
            ), Languages.FRENCH
        )

        assertEquals(
            listOf(VocabMatch(3..16, VocabStatus.REVIEWING)),
            highlighter.findMatches("La pomme de terre pousse.")
        )
    }

    @Test
    fun duplicateSavedFormsKeepLeastLearnedStatus() {
        val highlighter = VocabHighlighter(
            listOf(
                SavedVocab("bonjour", VocabStatus.LEARNED),
                SavedVocab("Bonjour", VocabStatus.REVIEWING),
                SavedVocab("BONJOUR", VocabStatus.NEW),
                SavedVocab("bonjour", VocabStatus.LEARNING)
            ), Languages.FRENCH
        )

        assertEquals(
            listOf(VocabMatch(0..6, VocabStatus.NEW)),
            highlighter.findMatches("Bonjour !")
        )
    }
    @Test
    fun persianHalfSpaceIsNotAWordBoundary() {
        val highlighter = VocabHighlighter(listOf(SavedVocab("روم", VocabStatus.NEW)), Languages.PERSIAN)
        assertEquals(emptyList(), highlighter.findMatches("می‌روم"))
    }

    @Test
    fun persianDiacriticsAndArabicLetterVariantsMatchOriginalOffsets() {
        val highlighter = VocabHighlighter(listOf(SavedVocab("کتاب", VocabStatus.NEW)), Languages.PERSIAN)
        assertEquals(listOf(VocabMatch(0..4, VocabStatus.NEW)), highlighter.findMatches("كِتاب"))
    }

    @Test
    fun englishLigatureExpansionKeepsOriginalOffsets() {
        val highlighter = VocabHighlighter(listOf(SavedVocab("oeuvre", VocabStatus.NEW)), Languages.ENGLISH)
        assertEquals(listOf(VocabMatch(0..4, VocabStatus.NEW)), highlighter.findMatches("œuvre"))
    }
}
