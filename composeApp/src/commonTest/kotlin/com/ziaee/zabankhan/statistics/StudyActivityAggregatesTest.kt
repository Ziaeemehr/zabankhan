package com.ziaee.zabankhan.statistics

import com.ziaee.zabankhan.data.ActivityLogEntry
import com.ziaee.zabankhan.data.StudySession
import com.ziaee.zabankhan.data.StudySkill
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class StudyActivityAggregatesTest {
    @Test
    fun combinesManualAndAutomaticTimeAndPreservesComponents() {
        val day = LocalDate(2026, 11, 8)
        val activity = ActivityLogEntry(day.toString(), "fr", readingMs = 30 * 60_000L, listeningMs = 15 * 60_000L)
        val session = StudySession(date = day.toEpochDays(), durationMin = 40, skill = StudySkill.CE, lang = "fr")

        val result = combinedStudyDays("fr", listOf(activity), listOf(session), day, day).single()

        assertEquals(45.0, result.automatic.total)
        assertEquals(40, result.manualMinutes)
        assertEquals(85.0, result.totalMinutes)
    }

    @Test
    fun usesPersistedLocalCalendarDatesAndFiltersLanguages() {
        val day = LocalDate(2026, 3, 1)
        val rows = listOf(
            ActivityLogEntry("2026-03-01", "fr", readingMs = 60_000),
            ActivityLogEntry("2026-03-01", "en", watchingMs = 9 * 60_000L),
        )
        val sessions = listOf(
            StudySession(date = day.toEpochDays(), durationMin = 20, skill = StudySkill.VOC, lang = "fr"),
            StudySession(date = day.toEpochDays(), durationMin = 90, skill = StudySkill.EE, lang = "en"),
        )

        val result = combinedStudyDays("fr", rows, sessions, day, day).single()

        assertEquals(day, result.date)
        assertEquals(1.0, result.automatic.total)
        assertEquals(20, result.manualMinutes)
        assertEquals(21.0, result.totalMinutes)
    }

    @Test
    fun monthGridAlignsLeapFebruaryToMondayAndExcludesAdjacentDays() {
        val today = LocalDate(2024, 2, 29)
        val days = combinedStudyDays("fr", emptyList(), emptyList(), LocalDate(2024, 2, 1), today)

        val grid = studyMonthGrid(days, today, DayOfWeek.MONDAY, today)

        assertEquals(5, grid.size)
        assertEquals(7, grid.first().size)
        assertEquals(1, grid.first()[3]?.date?.day)
        assertNull(grid.first()[2])
        assertEquals(29, grid.last()[3]?.date?.day)
        assertNull(grid.last()[4])
    }

    @Test
    fun yearGridContainsLeapDayAndExcludesFutureAndAdjacentDays() {
        val today = LocalDate(2024, 12, 31)
        val days = combinedStudyDays("en", emptyList(), emptyList(), LocalDate(2024, 1, 1), today)

        val grid = studyYearGrid(days, 2024, DayOfWeek.MONDAY, today)

        val cells = grid.flatten()
        assertTrue(cells.any { it?.date == LocalDate(2024, 2, 29) })
        assertTrue(cells.any { it?.date == today })
        assertTrue(cells.filterNotNull().none { it.date.year != 2024 })
        assertEquals(53, grid.size)
    }

    @Test
    fun automaticActivityKeepsItsPersistedDateAcrossTimezoneBoundaries() {
        val timestamp = Instant.parse("2026-03-01T00:30:00Z")
        val parisDay = timestamp.toLocalDateTime(TimeZone.of("Europe/Paris")).date
        val losAngelesDay = timestamp.toLocalDateTime(TimeZone.of("America/Los_Angeles")).date
        val rows = listOf(
            ActivityLogEntry(parisDay.toString(), "fr", readingMs = 10_000),
            ActivityLogEntry(losAngelesDay.toString(), "fr", watchingMs = 20_000),
        )

        val result = combinedStudyDays("fr", rows, emptyList(), LocalDate(2026, 2, 28), LocalDate(2026, 3, 1))

        assertEquals(listOf(LocalDate(2026, 2, 28), LocalDate(2026, 3, 1)), result.map { it.date })
        assertEquals(10_000.0 / 60_000.0, result.last().automatic.reading)
        assertEquals(20_000.0 / 60_000.0, result.first().automatic.watching)
    }
}
