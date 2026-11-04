package com.ziaee.zabankhan.srs

data class ReviewCard(
    val id: Long,
    val state: SrsState,
    val createdAtMs: Long,
    val listId: Long? = null
)

const val VOCAB_SCOPE_ALL = -1L
const val VOCAB_SCOPE_UNFILED = -2L

fun reviewableCount(entries: List<ReviewCard>, nowMs: Long, remainingNew: Int): Int =
    entries.count { !it.state.learned && it.state.lastReviewedAtMs != null && it.state.nextReviewAtMs <= nowMs } +
        entries.count { !it.state.learned && it.state.lastReviewedAtMs == null }.coerceAtMost(remainingNew.coerceAtLeast(0))

fun buildReviewQueue(
    entries: List<ReviewCard>, nowMs: Long, dayStartMs: Long, newLimit: Int, box: Int? = null
): List<ReviewCard> {
    val active = entries.filter { !it.state.learned }
    val scoped = if (box == null) active else active.filter { it.state.leitnerBox == box }
    val overdue = scoped.filter { it.state.lastReviewedAtMs != null && it.state.nextReviewAtMs < dayStartMs }.sortedBy { it.state.nextReviewAtMs }
    val today = scoped.filter { it.state.lastReviewedAtMs != null && it.state.nextReviewAtMs in dayStartMs..nowMs }.sortedBy { it.state.nextReviewAtMs }
    val fresh = if (box != null) emptyList()
    else active.filter { it.state.lastReviewedAtMs == null }.sortedBy { it.createdAtMs }.take(newLimit.coerceAtLeast(0))
    return overdue + today + fresh
}

data class LearnedReviewQueue(
    val cards: List<ReviewCard>, val total: Int, val reviewed: Int, val resetCursor: Boolean
)

fun buildLearnedReviewQueue(
    entries: List<ReviewCard>, cursor: Long, scope: Long
): LearnedReviewQueue {
    val learned = entries.asSequence().filter { it.state.learned }.filter {
        when (scope) {
            VOCAB_SCOPE_ALL -> true
            VOCAB_SCOPE_UNFILED -> it.listId == null
            else -> it.listId == scope
        }
    }.sortedBy { it.id }.toList()
    val remaining = learned.filter { it.id > cursor }
    val reset = cursor > 0 && learned.isNotEmpty() && remaining.isEmpty()
    return LearnedReviewQueue(
        cards = if (reset) learned else remaining,
        total = learned.size,
        reviewed = if (reset) 0 else learned.count { it.id <= cursor },
        resetCursor = reset
    )
}

fun boxBarFractions(counts: List<Int>): List<Float> {
    val max = maxOf(counts.maxOrNull() ?: 0, 1)
    return counts.map { it.toFloat() / max }
}

fun restoreQueueAfterUndo(queue: List<ReviewCard>, current: ReviewCard?, requeued: ReviewCard?): List<ReviewCard> {
    val rest = queue.filter { it.id != requeued?.id }
    return if (current != null && current.id != requeued?.id) listOf(current.copy()) + rest else rest
}
