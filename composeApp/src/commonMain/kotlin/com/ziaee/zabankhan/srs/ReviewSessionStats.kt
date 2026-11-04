package com.ziaee.zabankhan.srs

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

data class ReviewSessionStats(
    val answerCount: Int = 0,
    val correctCount: Int = 0,
    val movedForward: Int = 0,
    val returnedToBoxOne: Int = 0
)

fun ReviewSessionStats.after(answer: VocabAnswer, boxBefore: Int, boxAfter: Int) = copy(
    answerCount = answerCount + 1,
    correctCount = correctCount + if (answer != VocabAnswer.FORGOT) 1 else 0,
    movedForward = movedForward + if (boxAfter > boxBefore) 1 else 0,
    returnedToBoxOne = returnedToBoxOne + if (answer == VocabAnswer.FORGOT && boxBefore > 1) 1 else 0
)

fun reviewStudyTimeByDate(
    startMs: Long, endMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()
): Map<String, Long> {
    val result = linkedMapOf<String, Long>()
    var cursor = startMs
    while (cursor < endMs) {
        val date = Instant.fromEpochMilliseconds(cursor).toLocalDateTime(zone).date
        val nextMidnight = date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
        val end = minOf(endMs, nextMidnight)
        result[date.toString()] = end - cursor
        cursor = end
    }
    return result
}

/** Consumes foreground intervals once, so settings/background time is never counted. */
class ReviewStudyTimer(private val zone: TimeZone? = null) {
    private var startedAtMs: Long? = null

    fun resume(nowMs: Long) {
        if (startedAtMs == null) startedAtMs = nowMs
    }

    fun pause(nowMs: Long): Map<String, Long> {
        val start = startedAtMs ?: return emptyMap()
        startedAtMs = null
        return reviewStudyTimeByDate(start, nowMs, zone ?: TimeZone.currentSystemDefault())
    }
}
