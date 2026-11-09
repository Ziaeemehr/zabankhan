package com.ziaee.zabankhan.statistics

import com.ziaee.zabankhan.data.*
import kotlinx.datetime.*
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

fun localToday(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate = Clock.System.now().toLocalDateTime(zone).date
fun ActivityLogEntry.totalMs(): Long = readingMs + listeningMs + watchingMs

/** Today has grace until its first activity; an empty yesterday breaks the chain. */
fun activityStreak(rows: List<ActivityLogEntry>, today: LocalDate, reviews: List<ReviewLogEntry> = emptyList(), attempts: List<ShadowAttempt> = emptyList(), zone: TimeZone = TimeZone.currentSystemDefault(), manualSessions: List<StudySession> = emptyList(), lang: String? = null): Int {
    fun date(ms: Long) = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date.toString()
    val active = rows.filter { it.totalMs() > 0 && (lang == null || it.lang == lang) }.map { it.date }.toSet() +
        reviews.filter { lang == null || it.lang == lang }.map { date(it.timestampMs) } +
        attempts.filter { lang == null || it.lang == lang }.map { date(it.timestampMs) } +
        manualSessions.filter { it.durationMin > 0 && (lang == null || it.lang == lang) }.map { LocalDate.fromEpochDays(it.date.toInt()).toString() }
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

/** Daily activity keeps the automatic and manually entered portions available for detail views. */
data class StudyDayActivity(
    val date: LocalDate,
    val automatic: ActivityMinutes,
    val manualMinutes: Int,
) {
    val totalMinutes: Double get() = automatic.total + manualMinutes
}

fun combinedStudyDays(
    lang: String,
    rows: List<ActivityLogEntry>,
    sessions: List<StudySession>,
    start: LocalDate,
    end: LocalDate,
): List<StudyDayActivity> {
    require(start <= end)
    val automaticByDate = rows.asSequence()
        .filter { it.lang == lang }
        .groupBy { LocalDate.parse(it.date) }
    val manualByDate = sessions.asSequence()
        .filter { it.lang == lang }
        .groupBy { LocalDate.fromEpochDays(it.date) }
    return (start.toEpochDays()..end.toEpochDays()).map { epochDay ->
        val date = LocalDate.fromEpochDays(epochDay)
        val automatic = automaticByDate[date].orEmpty()
        StudyDayActivity(
            date = date,
            automatic = ActivityMinutes(
                reading = automatic.sumOf { it.readingMs } / 60_000.0,
                listening = automatic.sumOf { it.listeningMs } / 60_000.0,
                watching = automatic.sumOf { it.watchingMs } / 60_000.0,
            ),
            manualMinutes = manualByDate[date].orEmpty().sumOf { it.durationMin },
        )
    }
}

fun studyMonthGrid(
    days: List<StudyDayActivity>,
    month: LocalDate,
    firstDay: DayOfWeek,
    today: LocalDate,
): List<List<StudyDayActivity?>> {
    val firstOfMonth = LocalDate(month.year, month.month, 1)
    val nextMonth = if (month.month == Month.DECEMBER) LocalDate(month.year + 1, Month.JANUARY, 1)
    else LocalDate(month.year, Month.entries[month.month.ordinal + 1], 1)
    val lastOfMonth = nextMonth.minus(1, DateTimeUnit.DAY)
    val firstGridDate = weekStart(firstOfMonth, firstDay)
    val lastGridDate = weekStart(lastOfMonth, firstDay)
    val byDate = days.associateBy { it.date }
    return calendarWeeks(firstGridDate, lastGridDate).map { week ->
        week.map { date ->
            if (date.month != month.month || date.year != month.year || date > today) null
            else byDate[date] ?: StudyDayActivity(date, ActivityMinutes(0.0, 0.0, 0.0), 0)
        }
    }
}

fun studyYearGrid(
    days: List<StudyDayActivity>,
    year: Int,
    firstDay: DayOfWeek,
    today: LocalDate,
): List<List<StudyDayActivity?>> {
    val firstDate = LocalDate(year, 1, 1)
    val lastDate = LocalDate(year, 12, 31)
    val byDate = days.associateBy { it.date }
    return calendarWeeks(weekStart(firstDate, firstDay), weekStart(lastDate, firstDay)).map { week ->
        week.map { date ->
            if (date.year != year || date > today) null
            else byDate[date] ?: StudyDayActivity(date, ActivityMinutes(0.0, 0.0, 0.0), 0)
        }
    }
}

private fun weekStart(date: LocalDate, firstDay: DayOfWeek): LocalDate {
    val daysSinceFirst = (date.dayOfWeek.isoDayNumber - firstDay.isoDayNumber + 7) % 7
    return date.minus(daysSinceFirst, DateTimeUnit.DAY)
}

private fun calendarWeeks(first: LocalDate, last: LocalDate): List<List<LocalDate>> {
    val weeks = mutableListOf<List<LocalDate>>()
    var weekStart = first
    while (weekStart <= last) {
        weeks += (0..6).map { weekStart.plus(it, DateTimeUnit.DAY) }
        weekStart = weekStart.plus(7, DateTimeUnit.DAY)
    }
    return weeks
}
