package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.srs.*
import kotlin.test.*

class ReviewOverviewTest {
    private val day = 86_400_000L
    private val dayStart = 10 * day
    private val now = dayStart + 3_600_000L
    private fun card(id: Long, box: Int = 1, next: Long = 0, last: Long? = null, learned: Boolean = false) =
        VocabEntry(id = id, lang = "en", word = "w$id", leitnerBox = box, nextReviewAtMs = next, lastReviewedAtMs = last, learned = learned, createdAtMs = id)

    @Test fun overviewCountsDueNewBoxesAndLearned() {
        val entries = listOf(
            card(1, box = 2, next = now - 1, last = dayStart - day),   // due
            card(2, box = 2, next = now + day, last = dayStart - day), // not yet
            card(3), card(4), card(5),                                 // new
            card(6, box = 5, last = 1, learned = true)
        )
        // One new card already started today: 2 of the limit of 3 remain.
        val logs = listOf(ReviewLogEntry(entryId = 9, lang = "en", timestampMs = dayStart + 5, knew = true, boxBefore = 1, boxAfter = 1))
        val o = reviewOverview(entries, logs, newLimit = 3, nowMs = now, dayStartMs = dayStart)
        assertEquals(1, o.dueCount)
        assertEquals(2, o.newCount)
        assertFalse(o.newCapReached)
        assertEquals(1, o.reviewedToday)
        assertEquals(1, o.learnedCount)
        assertEquals(listOf(1, 2, 3, 4), o.boxes.map { it.box })
        assertEquals(3, o.boxes[0].count)
        assertEquals(0, o.boxes[0].dueCount)
        assertNull(o.boxes[0].nextReviewAtMs)
        assertEquals(1, o.boxes[1].dueCount)
        assertEquals(now - 1, o.boxes[1].nextReviewAtMs)
        assertEquals(2L, o.boxes[1].intervalDays)
        assertEquals(1, o.estimatedMinutes)
        assertTrue(reviewOverview(listOf(card(3)), logs, newLimit = 1, nowMs = now, dayStartMs = dayStart).newCapReached)
        assertEquals(0, reviewOverview(emptyList(), emptyList(), 3, now, dayStart).estimatedMinutes)
    }

    @Test fun dayTotalsCountTodayOnly() {
        fun log(id: Long, t: Long, knew: Boolean, b: Int, a: Int) = ReviewLogEntry(entryId = id, lang = "en", timestampMs = t, knew = knew, boxBefore = b, boxAfter = a)
        val totals = reviewDayTotals(listOf(
            log(1, dayStart - 1, true, 1, 2), log(1, dayStart + 1, true, 2, 3),
            log(2, dayStart + 2, false, 3, 1), log(2, dayStart + 3, true, 1, 1)
        ), dayStart)
        assertEquals(ReviewDayTotals(cards = 2, movedForward = 1, returnedToBoxOne = 1), totals)
    }

    @Test fun meaningSplitsIntoMainAndAlternatives() {
        assertEquals("سگ" to listOf("تازی", "dog"), splitMeaning("سگ\n  تازی \n\ndog"))
        assertEquals(null to emptyList<String>(), splitMeaning(null))
        assertEquals(null to emptyList<String>(), splitMeaning(" \n "))
    }

    @Test fun learnedReviewForgotIsNotRequeuedAndUndoes() {
        val cards = listOf(card(1, learned = true), card(2, learned = true))
        val s = ReviewSession(cards).answered(SessionAnswer(cards[0], VocabAnswer.FORGOT, cards[0], 1, requeued = false))
        assertEquals(cards, s.cards)
        assertEquals(cards[1], s.current)
        assertEquals(ReviewSession(cards), s.undo())
        assertEquals(2, s.progressPosition)
    }

    @Test fun progressCountsDistinctCards() {
        val cards = listOf(card(1), card(2))
        val again = ReviewSession(cards).answered(SessionAnswer(cards[0], VocabAnswer.FORGOT, cards[0], 1))
        assertEquals(2, again.totalCards)
        assertEquals(1, again.progressPosition)
        val done = again.answered(SessionAnswer(cards[1], VocabAnswer.KNEW, cards[1], 2))
        assertEquals(2, done.progressPosition)
    }
}
