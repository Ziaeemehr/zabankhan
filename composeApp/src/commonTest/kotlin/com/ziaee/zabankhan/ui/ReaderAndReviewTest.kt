package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.srs.*
import com.ziaee.zabankhan.ui.reader.*
import kotlin.test.*
import kotlinx.datetime.TimeZone

class ReaderAndReviewTest {
    @Test fun paragraphOffsetsPreserveOriginalNewlinesAndUnicode() {
        val body = "\r\nBonjour !\r\nUne ligne.\r\n\r\nسلام دنیا\n\nLast."
        val parts = paragraphs(body)
        assertEquals(listOf("Bonjour !\r\nUne ligne.", "سلام دنیا", "Last."), parts.map { it.text })
        parts.forEachIndexed { index, p ->
            assertEquals(p.text, body.substring(p.offset, p.offset + p.text.length))
            assertEquals(index, paragraphIndexAt(parts, p.offset + 2))
        }
        assertEquals(0, paragraphIndexAt(parts, -50))
        assertEquals(2, paragraphIndexAt(parts, Int.MAX_VALUE))
        assertTrue(paragraphs(" \n\n").isEmpty())
    }
    // Adapted from FrenchReader's ReadingScreenSelectionTest to language-aware wordAt.
    @Test fun readerTapDropsElidedArticleAndFindsSentence() {
        val sentence = "Le chat noir dort dans l'appartement."
        assertEquals("chat", wordAt(sentence, 4, Languages.FRENCH)?.text)
        assertEquals("appartement", wordAt(sentence, sentence.indexOf("appartement") + 2, Languages.FRENCH)?.text)
        assertEquals(sentence, sentence.substring(sentenceAround(sentence, 4, Languages.FRENCH)))
    }
    @Test fun undoRestoresCurrentCardAndStatistics() {
        val first = VocabEntry(id = 1, lang = Languages.ENGLISH.code, word = "one", createdAtMs = 0, nextReviewAtMs = 0)
        val second = first.copy(id = 2, word = "two")
        val original = ReviewSession(listOf(first, second))
        val changed = first.withState(VocabSrs.apply(first.srsState(), VocabAnswer.HARD, 1000, zoneId = TimeZone.UTC))
        val answered = original.answered(SessionAnswer(first, VocabAnswer.HARD, changed, 42))
        assertEquals(second, answered.current)
        assertEquals(100, answered.accuracy)
        assertEquals(original, answered.undo())
        val complete = answered.answered(SessionAnswer(second, VocabAnswer.KNEW, second, 43))
        assertEquals(2, complete.completedCount)
        assertNull(complete.current)
        assertEquals(100, complete.accuracy)
        assertEquals(second, complete.undo().current)
    }
    @Test fun forgotRequeuesAfterThreeCardsAndUndoRemovesTheRepeat() {
        val cards = (1L..5L).map { VocabEntry(id = it, lang = Languages.ENGLISH.code, word = "word$it") }
        val original = ReviewSession(cards)
        val failed = cards[0].copy(nextReviewAtMs = 200, lastReviewedAtMs = 200)
        val after = original.answered(SessionAnswer(cards[0], VocabAnswer.FORGOT, failed, 1))
        assertEquals(listOf(2L, 3L, 4L, 1L, 5L), after.cards.drop(after.answers.size).map { it.id })
        assertEquals(0, after.completedCount)
        assertEquals(original, after.undo())
        val only = ReviewSession(listOf(cards[0])).answered(SessionAnswer(cards[0], VocabAnswer.FORGOT, failed, 1))
        assertEquals(failed, only.current)
        assertEquals(ReviewSession(listOf(cards[0])), only.undo())
    }
    @Test fun dailyAllowanceOnlyCountsFirstReviewOfEachCard() {
        fun log(id: Long, time: Long) = ReviewLogEntry(entryId = id, lang = Languages.ENGLISH.code, timestampMs = time, knew = true, boxBefore = 1, boxAfter = 1)
        val history = listOf(log(1, 1), log(1, 200), log(2, 201), log(2, 202), log(3, 203))
        assertEquals(1, remainingNewCards(history, 3, 100))
        assertEquals(0, remainingNewCards(history, 1, 100))
        assertEquals(3, remainingNewCards(emptyList(), 3, 100))
    }
}
