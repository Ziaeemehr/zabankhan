package com.ziaee.zabankhan.srs

import com.ziaee.zabankhan.lang.Script
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

enum class VocabAnswer { FORGOT, HARD, KNEW }
enum class VocabStatus { NEW, LEARNING, REVIEWING, LEARNED }

data class SrsState(
    val leitnerBox: Int = 1,
    val nextReviewAtMs: Long = 0,
    val lastReviewedAtMs: Long? = null,
    val learned: Boolean = false
)

fun SrsState.status(): VocabStatus = when {
    learned -> VocabStatus.LEARNED
    lastReviewedAtMs == null -> VocabStatus.NEW
    leitnerBox in 1..2 -> VocabStatus.LEARNING
    leitnerBox in 3..4 -> VocabStatus.REVIEWING
    leitnerBox >= 5 -> VocabStatus.LEARNED
    else -> VocabStatus.LEARNING
}

/** Rejects text mixing another script, and requires at least one letter. */
fun looksLikeScript(text: String, script: Script): Boolean {
    fun scriptOf(c: Char): Script = when {
        c in 'A'..'Z' || c in 'a'..'z' || c.code in 0x00C0..0x024F || c.code in 0x1E00..0x1EFF -> Script.LATIN
        c.code in 0x0600..0x06FF || c.code in 0x0750..0x077F || c.code in 0x08A0..0x08FF ||
            c.code in 0xFB50..0xFDFF || c.code in 0xFE70..0xFEFF -> Script.ARABIC
        else -> Script.OTHER
    }
    val letters = text.filter(Char::isLetter)
    return letters.isNotEmpty() && letters.all { scriptOf(it) == script }
}

object VocabSrs {
    const val DAY_MS = 86_400_000L
    val DEFAULT_INTERVAL_DAYS = listOf(1L, 2L, 4L, 8L, 16L)

    fun isLearningStep(entry: SrsState, nowMs: Long, zoneId: TimeZone = TimeZone.currentSystemDefault()): Boolean =
        entry.leitnerBox <= 1 &&
            (entry.lastReviewedAtMs == null || entry.lastReviewedAtMs >= startOfDayMs(nowMs, zoneId))

    private fun nextBox(entry: SrsState, answer: VocabAnswer, nowMs: Long, zoneId: TimeZone): Int = when (answer) {
        VocabAnswer.FORGOT -> 1
        VocabAnswer.HARD -> entry.leitnerBox
        VocabAnswer.KNEW -> if (isLearningStep(entry, nowMs, zoneId)) 1 else (entry.leitnerBox + 1).coerceAtMost(5)
    }

    fun apply(
        entry: SrsState,
        answer: VocabAnswer,
        nowMs: Long,
        intervalDays: List<Long> = DEFAULT_INTERVAL_DAYS,
        zoneId: TimeZone = TimeZone.currentSystemDefault()
    ): SrsState {
        val newBox = nextBox(entry, answer, nowMs, zoneId)
        return entry.copy(
            leitnerBox = newBox,
            nextReviewAtMs = dueAtMs(newBox, nowMs, intervalDays, zoneId),
            lastReviewedAtMs = nowMs,
            learned = newBox == 5
        )
    }

    fun previewIntervalDays(
        entry: SrsState,
        answer: VocabAnswer,
        intervalDays: List<Long> = DEFAULT_INTERVAL_DAYS,
        nowMs: Long = Clock.System.now().toEpochMilliseconds(),
        zoneId: TimeZone = TimeZone.currentSystemDefault()
    ): Long = intervalDays[(nextBox(entry, answer, nowMs, zoneId) - 1).coerceIn(0, 4)]

    fun startOfDayMs(nowMs: Long, zoneId: TimeZone = TimeZone.currentSystemDefault()): Long =
        Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zoneId).date.atStartOfDayIn(zoneId).toEpochMilliseconds()

    fun dueAtMs(box: Int, nowMs: Long, intervals: List<Long>, zoneId: TimeZone = TimeZone.currentSystemDefault()): Long {
        val days = intervals[box.coerceIn(1, 5) - 1]
        require(days in 0..Int.MAX_VALUE.toLong())
        return Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zoneId).date
            .plus(DatePeriod(days = days.toInt())).atStartOfDayIn(zoneId).toEpochMilliseconds()
    }
}

/** Rebuild each candidate from the wall time so a DST gap does not shift tomorrow's reminder. */
fun nextReviewReminderAt(
    now: Instant, hour: Int, minute: Int, zone: TimeZone = TimeZone.currentSystemDefault()
): Instant {
    val time = LocalTime(hour, minute)
    val today = now.toLocalDateTime(zone).date
    val next = LocalDateTime(today, time).toInstant(zone)
    return if (next > now) next else LocalDateTime(today.plus(DatePeriod(days = 1)), time).toInstant(zone)
}

/** Removes a caller-specified trailing bookkeeping label, independent of the learning language. */
fun stripTrailingMeaningTag(meaning: String?, tag: Regex): String? =
    meaning?.replace(tag, "")?.trim()?.takeIf(String::isNotBlank)
