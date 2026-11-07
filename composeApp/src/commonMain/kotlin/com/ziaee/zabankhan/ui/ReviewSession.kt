package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.srs.*
import kotlin.math.roundToInt

fun VocabEntry.srsState() = SrsState(leitnerBox, nextReviewAtMs, lastReviewedAtMs, learned)
fun VocabEntry.reviewCard() = ReviewCard(id, srsState(), createdAtMs, listId)
fun VocabEntry.withState(state: SrsState) = copy(leitnerBox = state.leitnerBox, nextReviewAtMs = state.nextReviewAtMs, lastReviewedAtMs = state.lastReviewedAtMs, learned = state.learned)

/** The first recorded review consumes the daily allowance, regardless of subsequent answers. */
fun remainingNewCards(history: List<ReviewLogEntry>, limit: Int, dayStart: Long = VocabSrs.startOfDayMs(nowMs())): Int {
    val first = history.groupBy { it.entryId }.values.mapNotNull { logs -> logs.minOfOrNull { it.timestampMs } }
    return (limit - first.count { it >= dayStart }).coerceAtLeast(0)
}

/** [requeued]: the card comes back later this session (FrenchReader repeats "Again", except in learned-word review). */
data class SessionAnswer(val before: VocabEntry, val answer: VocabAnswer, val after: VocabEntry, val logId: Long,
    val requeued: Boolean = answer == VocabAnswer.FORGOT)
data class ReviewSession(val cards: List<VocabEntry>, val answers: List<SessionAnswer> = emptyList()) {
    val current get() = cards.getOrNull(answers.size)
    val completedCount get() = answers.filter { !it.requeued }.map { it.before.id }.distinct().size
    /** FrenchReader's "n of total": distinct cards, the current one included. */
    val totalCards get() = cards.map { it.id }.distinct().size
    val progressPosition get() = if (totalCards == 0) 0 else (completedCount + 1).coerceAtMost(totalCards)
    val accuracy get() = if (answers.isEmpty()) 0 else answers.count { it.answer != VocabAnswer.FORGOT } * 100 / answers.size
    fun answered(answer: SessionAnswer): ReviewSession {
        require(current?.id == answer.before.id)
        val next = if (answer.requeued) cards.toMutableList().apply {
            add((answers.size + 4).coerceAtMost(size), answer.after)
        } else cards
        return copy(cards = next, answers = answers + answer)
    }
    fun undo(): ReviewSession {
        val last = answers.lastOrNull() ?: return this
        val restored = if (last.requeued) cards.toMutableList().apply {
            removeAt((answers.size + 3).coerceAtMost(lastIndex))
        } else cards
        return copy(cards = restored, answers = answers.dropLast(1))
    }
}

/** FrenchReader's review overview: box ladder rows are boxes 1..4, box 5 is the learned-words card. */
data class ReviewBoxSummary(val box: Int, val count: Int, val intervalDays: Long, val dueCount: Int, val nextReviewAtMs: Long?)
data class ReviewOverview(
    val dueCount: Int, val newCount: Int, val newCapReached: Boolean, val reviewedToday: Int,
    val boxes: List<ReviewBoxSummary>, val learnedCount: Int
) {
    /** FrenchReader estimates 12 seconds per card, at least one minute when anything is due. */
    val estimatedMinutes get() = ((dueCount + newCount) * 12 / 60.0).roundToInt().coerceAtLeast(if (dueCount + newCount > 0) 1 else 0)
}

fun reviewOverview(
    entries: List<VocabEntry>, logs: List<ReviewLogEntry>, newLimit: Int, nowMs: Long,
    dayStartMs: Long = VocabSrs.startOfDayMs(nowMs), intervals: List<Long> = VocabSrs.DEFAULT_INTERVAL_DAYS
): ReviewOverview {
    val active = entries.filter { !it.learned }
    val remaining = remainingNewCards(logs, newLimit, dayStartMs)
    val availableNew = active.count { it.lastReviewedAtMs == null }
    return ReviewOverview(
        dueCount = reviewableCount(active.map { it.reviewCard() }, nowMs, 0),
        newCount = availableNew.coerceAtMost(remaining),
        newCapReached = availableNew > 0 && remaining == 0,
        reviewedToday = logs.count { it.timestampMs >= dayStartMs },
        boxes = (1..4).map { box ->
            val inBox = active.filter { it.leitnerBox == box }
            val seen = inBox.filter { it.lastReviewedAtMs != null }
            ReviewBoxSummary(box, inBox.size, intervals[box - 1], seen.count { it.nextReviewAtMs <= nowMs }, seen.minOfOrNull { it.nextReviewAtMs })
        },
        learnedCount = entries.count { it.learned }
    )
}

/** Today's totals for the summary screen, from the review log (FrenchReader's ReviewLogDao counts). */
data class ReviewDayTotals(val cards: Int, val movedForward: Int, val returnedToBoxOne: Int)
fun reviewDayTotals(logs: List<ReviewLogEntry>, dayStartMs: Long): ReviewDayTotals {
    val today = logs.filter { it.timestampMs >= dayStartMs }
    return ReviewDayTotals(today.map { it.entryId }.distinct().size, today.count { it.boxAfter > it.boxBefore },
        today.count { !it.knew && it.boxBefore > 1 })
}

/** First non-blank line is the meaning; further stored lines are alternative meanings. */
fun splitMeaning(meaning: String?): Pair<String?, List<String>> {
    val lines = meaning.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
    return lines.firstOrNull() to lines.drop(1)
}
