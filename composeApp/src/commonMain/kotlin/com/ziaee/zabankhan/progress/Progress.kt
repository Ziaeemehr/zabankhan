package com.ziaee.zabankhan.progress

import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.statistics.activityStreak
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A loaded snapshot. Counts without historical completion dates affect totals only. */
data class ProgressInputs(
    val language: String,
    val today: LocalDate,
    val zone: TimeZone,
    val activity: List<ActivityLogEntry> = emptyList(),
    val reviews: List<ReviewLogEntry> = emptyList(),
    val shadowAttempts: List<ShadowAttempt> = emptyList(),
    val learnedWords: Map<String, Long> = emptyMap(),
    val finishedTexts: Map<String, Long> = emptyMap(),
    val exams: List<ProgressExam> = emptyList(),
    val weeklyGoal: Int = DEFAULT_WEEKLY_GOAL,
)
data class ProgressExam(val language: String, val timestampMs: Long)
const val DEFAULT_WEEKLY_GOAL = 300
const val MIN_WEEKLY_GOAL = 1
const val MAX_WEEKLY_GOAL = 10000

@Serializable
data class ProgressBadge(val id: String, val language: String, val unlocked: Boolean, val current: Long, val target: Long) {
    val seenKey: String get() = "$language:$id"
}

@Serializable
data class ProgressSummary(
    val totalPoints: Long,
    val level: Int,
    val pointsInLevel: Long,
    /** Points still needed, rather than the full size of the level. */
    val pointsToNextLevel: Long,
    val levelFraction: Float,
    val weeklyPoints: Long,
    val weeklyGoal: Int,
    val weekStart: String,
    val badges: List<ProgressBadge>,
)

/** Level zero starts at zero. Binary search avoids a floating-point boundary error. */
private fun levelFor(points: Long): Int {
    // Largest n whose threshold fits in Long; real local histories are far smaller.
    var low = 0
    var high = 429_496_729
    while (low < high) {
        val mid = low + (high - low + 1) / 2
        if (threshold(mid) <= points) low = mid else high = mid - 1
    }
    return low
}
private fun threshold(level: Int): Long = 50L * level * (level.toLong() + 1)

/** No clock, persistence, network, or platform dependencies. */
fun computeProgress(inputs: ProgressInputs): ProgressSummary = with(inputs) {
    val monday = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    val sunday = monday.plus(6, DateTimeUnit.DAY)
    fun date(ms: Long) = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date
    fun inWeek(ms: Long) = date(ms) in monday..sunday
    val readingMinutes = activity.sumOf { it.readingMs.coerceAtLeast(0) } / 60_000
    val streak = activityStreak(activity, today, reviews, shadowAttempts, zone)
    val total = readingMinutes + reviews.size.toLong() * 2 + reviews.count { it.knew } +
        learnedWords.values.sum() * 5 + finishedTexts.values.sum() * 20 + exams.size.toLong() * 10 + streak.coerceAtMost(10) * 10L
    val activeToday = activity.any { it.date == today.toString() && it.readingMs + it.listeningMs + it.watchingMs > 0 } ||
        reviews.any { date(it.timestampMs) == today } || shadowAttempts.any { date(it.timestampMs) == today }
    val streakEnd = if (activeToday) today else today.minus(1, DateTimeUnit.DAY)
    // Use calendar days, not elapsed 24-hour periods (DST), and preserve Home's today grace.
    val streakDaysInWeek = if (streak == 0) 0 else (0 until minOf(streak, 7)).count {
        streakEnd.minus(it, DateTimeUnit.DAY) in monday..sunday
    }
    val weekReviews = reviews.filter { inWeek(it.timestampMs) }
    val week = activity.filter { it.date in monday.toString()..sunday.toString() }.sumOf { it.readingMs.coerceAtLeast(0) } / 60_000 +
        weekReviews.size.toLong() * 2 + weekReviews.count { it.knew } + exams.count { inWeek(it.timestampMs) } * 10L +
        minOf(streakDaysInWeek * 10L, 100L)
    val langActivity = activity.filter { it.lang == language }
    val langReviews = reviews.filter { it.lang == language }
    val langStreak = activityStreak(langActivity, today, langReviews, shadowAttempts.filter { it.lang == language }, zone).toLong()
    val learned = learnedWords[language] ?: 0L
    val finished = finishedTexts[language] ?: 0L
    fun badge(id: String, current: Long, target: Long) = ProgressBadge(id, language, current >= target, current, target)
    val badges = listOf(
        badge("first_text", finished, 1), badge("streak_7", langStreak, 7), badge("streak_30", langStreak, 30),
        badge("learned_100", learned, 100), badge("learned_500", learned, 500), badge("learned_1000", learned, 1000),
        badge("reviews_1000", langReviews.size.toLong(), 1000), badge("texts_10", finished, 10),
        badge("reading_600", langActivity.sumOf { it.readingMs.coerceAtLeast(0) } / 60_000, 600),
    )
    val level = levelFor(total)
    val base = threshold(level)
    val next = threshold(level + 1)
    ProgressSummary(total, level, total - base, next - total,
        ((total - base).toDouble() / (next - base)).toFloat().coerceIn(0f, 1f), week,
        weeklyGoal.coerceIn(MIN_WEEKLY_GOAL, MAX_WEEKLY_GOAL), monday.toString(), badges)
}

@Serializable
data class ProgressSeen(val level: Int = 0, val badges: Set<String> = emptySet())

data class ProgressCelebration(val seen: ProgressSeen, val levelIncreased: Boolean, val newBadgeCount: Int) {
    val hasNews: Boolean get() = levelIncreased || newBadgeCount > 0
}

/** Monotonic memory survives streak decay, language switching, deletion, and later recovery. */
fun progressCelebration(summary: ProgressSummary, previous: ProgressSeen): ProgressCelebration {
    val unlocked = summary.badges.filter { it.unlocked }.map { it.seenKey }.toSet()
    return ProgressCelebration(ProgressSeen(maxOf(summary.level, previous.level), previous.badges + unlocked),
        summary.level > previous.level, (unlocked - previous.badges).size)
}
