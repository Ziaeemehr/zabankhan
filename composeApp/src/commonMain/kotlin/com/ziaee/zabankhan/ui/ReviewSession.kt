package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.srs.*

fun VocabEntry.srsState() = SrsState(leitnerBox, nextReviewAtMs, lastReviewedAtMs, learned)
fun VocabEntry.reviewCard() = ReviewCard(id, srsState(), createdAtMs, listId)
fun VocabEntry.withState(state: SrsState) = copy(leitnerBox = state.leitnerBox, nextReviewAtMs = state.nextReviewAtMs, lastReviewedAtMs = state.lastReviewedAtMs, learned = state.learned)

/** The first recorded review consumes the daily allowance, regardless of subsequent answers. */
fun remainingNewCards(history: List<ReviewLogEntry>, limit: Int, dayStart: Long = VocabSrs.startOfDayMs(nowMs())): Int {
    val first = history.groupBy { it.entryId }.values.mapNotNull { logs -> logs.minOfOrNull { it.timestampMs } }
    return (limit - first.count { it >= dayStart }).coerceAtLeast(0)
}

data class SessionAnswer(val before: VocabEntry, val answer: VocabAnswer, val after: VocabEntry, val logId: Long)
data class ReviewSession(val cards: List<VocabEntry>, val answers: List<SessionAnswer> = emptyList()) {
    val current get() = cards.getOrNull(answers.size)
    val completedCount get() = answers.filter { it.answer != VocabAnswer.FORGOT }.map { it.before.id }.distinct().size
    val accuracy get() = if (answers.isEmpty()) 0 else answers.count { it.answer != VocabAnswer.FORGOT } * 100 / answers.size
    fun answered(answer: SessionAnswer): ReviewSession {
        require(current?.id == answer.before.id)
        val next = if (answer.answer == VocabAnswer.FORGOT) cards.toMutableList().apply {
            add((answers.size + 4).coerceAtMost(size), answer.after)
        } else cards
        return copy(cards = next, answers = answers + answer)
    }
    fun undo(): ReviewSession {
        val last = answers.lastOrNull() ?: return this
        val restored = if (last.answer == VocabAnswer.FORGOT) cards.toMutableList().apply {
            removeAt((answers.size + 3).coerceAtMost(lastIndex))
        } else cards
        return copy(cards = restored, answers = answers.dropLast(1))
    }
}
