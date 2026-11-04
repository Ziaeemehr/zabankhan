package com.ziaee.zabankhan.statistics

import com.ziaee.zabankhan.data.*
import kotlinx.datetime.*
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

fun localToday(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate = Clock.System.now().toLocalDateTime(zone).date
fun ActivityLogEntry.totalMs(): Long = readingMs + listeningMs + watchingMs

/** Today has grace until its first activity; an empty yesterday breaks the chain. */
fun activityStreak(rows: List<ActivityLogEntry>, today: LocalDate, reviews: List<ReviewLogEntry> = emptyList(), attempts: List<ShadowAttempt> = emptyList(), zone: TimeZone = TimeZone.currentSystemDefault()): Int {
    fun date(ms: Long) = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date.toString()
    val active = rows.filter { it.totalMs() > 0 }.map { it.date }.toSet() + reviews.map { date(it.timestampMs) } + attempts.map { date(it.timestampMs) }
    var day = if (today.toString() in active) today else today.minus(1, DateTimeUnit.DAY)
    var count = 0
    while (day.toString() in active) { count++; day = day.minus(1, DateTimeUnit.DAY) }
    return count
}

data class ActivityMinutes(val reading: Double, val listening: Double, val watching: Double) {
    val total get() = reading + listening + watching
}
fun activityMinutes(rows: List<ActivityLogEntry>, today: LocalDate, days: Int): ActivityMinutes {
    require(days > 0)
    val start = today.minus(days - 1, DateTimeUnit.DAY).toString()
    val end = today.toString()
    val selected = rows.filter { it.date in start..end }
    return ActivityMinutes(selected.sumOf { it.readingMs } / 60_000.0, selected.sumOf { it.listeningMs } / 60_000.0, selected.sumOf { it.watchingMs } / 60_000.0)
}
fun lastDays(today: LocalDate, days: Int): List<LocalDate> = (days - 1 downTo 0).map { today.minus(it, DateTimeUnit.DAY) }
data class DailyReviews(val date: LocalDate, val count: Int, val accuracy: Int)
fun dailyReviews(logs: List<ReviewLogEntry>, today: LocalDate, days: Int, zone: TimeZone = TimeZone.currentSystemDefault()): List<DailyReviews> {
    val groups = logs.groupBy { Instant.fromEpochMilliseconds(it.timestampMs).toLocalDateTime(zone).date }
    return lastDays(today, days).map { date ->
        val group = groups[date].orEmpty()
        DailyReviews(date, group.size, if (group.isEmpty()) 0 else (100.0 * group.count { it.knew } / group.size).roundToInt())
    }
}
fun shadowAverage(rows: List<ShadowAttempt>): Int = rows.filter { it.total > 0 }.let { valid ->
    if (valid.isEmpty()) 0 else (valid.map { 100.0 * it.matched.coerceIn(0, it.total) / it.total }.average()).roundToInt()
}
