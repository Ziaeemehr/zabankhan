package com.ziaee.zabankhan.statistics

import com.ziaee.zabankhan.data.*
import kotlinx.datetime.*
import kotlin.time.Instant
import kotlin.test.*

class ActivityTest {
    private var elapsed = 0L
    private var wall = Instant.parse("2026-10-26T23:59:50Z").toEpochMilliseconds()
    private fun advance(ms: Long) { elapsed += ms; wall += ms }
    @Test fun midnightPauseAndDrain() {
        val timer = ActivityTimer(false, { elapsed }, { wall }, { TimeZone.UTC })
        timer.setEnabled(true)
        advance(25_000)
        assertEquals(mapOf("2026-10-26" to 10_000L, "2026-10-27" to 15_000L), timer.drain())
        timer.setEnabled(false)
        advance(60_000)
        assertTrue(timer.drain().isEmpty())
        timer.setEnabled(true)
        advance(2_000)
        assertEquals(mapOf("2026-10-27" to 2_000L), timer.drain())
        assertTrue(timer.drain().isEmpty())
    }
    @Test fun readingInteractionExpires() {
        val timer = ActivityTimer(true, { elapsed }, { wall }, { TimeZone.UTC })
        timer.setEnabled(true); advance(20_000)
        assertTrue(timer.drain().isEmpty())
        timer.interact(); advance(90_000)
        assertEquals(60_000L, timer.drain().values.sum())
    }
    @Test fun screenRequiresForegroundAndEligibility() {
        val saved = mutableMapOf<String, Long>()
        val screen = ScreenActivityTimer(ActivityTimer(false, { elapsed }, { wall }, { TimeZone.UTC })) { rows -> rows.forEach { (d, ms) -> saved[d] = (saved[d] ?: 0) + ms } }
        screen.setEligible(true); advance(10_000)
        screen.setResumed(true); advance(5_000); screen.setResumed(false)
        assertEquals(5_000L, saved.values.sum())
        advance(20_000); screen.setResumed(true); screen.setEligible(false); advance(20_000); screen.flush()
        assertEquals(5_000L, saved.values.sum())
    }
    @Test fun streakAllowsTodayGraceAndIgnoresEmptyRows() {
        val today = LocalDate(2026, 10, 26)
        val rows = listOf(ActivityLogEntry("2026-10-25", "en", readingMs = 1), ActivityLogEntry("2026-10-24", "en", watchingMs = 1), ActivityLogEntry("2026-10-23", "en"))
        assertEquals(2, activityStreak(rows, today))
        assertEquals(3, activityStreak(rows + ActivityLogEntry(today.toString(), "en", listeningMs = 1), today))
        assertEquals(0, activityStreak(rows.drop(1), today))
    }
    @Test fun reviewAndShadowOnlyDaysKeepStreakInLocalZone() {
        val day = LocalDate(2026, 10, 26)
        val time = Instant.parse("2026-10-25T23:30:00Z").toEpochMilliseconds()
        val review = ReviewLogEntry(entryId = 1, lang = "en", timestampMs = time, knew = true, boxBefore = 1, boxAfter = 2)
        val attempt = ShadowAttempt(lang = "en", textId = null, sentence = "Hello", matched = 1, total = 1, paceRatio = null, timestampMs = time - 86_400_000)
        assertEquals(2, activityStreak(emptyList(), day, listOf(review), listOf(attempt), TimeZone.of("Europe/Paris")))
        assertEquals(100, shadowAverage(listOf(attempt)))
        assertEquals(0, shadowAverage(emptyList()))
    }
    @Test fun streakCanBeScopedToTheActiveLearningLanguage() {
        val today = LocalDate(2026, 10, 26)
        val rows = listOf(
            ActivityLogEntry("2026-10-25", "en", readingMs = 1),
            ActivityLogEntry("2026-10-24", "en", readingMs = 1),
            ActivityLogEntry("2026-10-23", "fr", readingMs = 1),
        )
        assertEquals(2, activityStreak(rows, today, lang = "en"))
    }
    @Test fun aggregatesIncludeBoundaryExcludeFutureAndPreserveFractions() {
        val today = LocalDate(2026, 10, 26)
        val rows = listOf(ActivityLogEntry("2026-10-20", "en", 30_000, 90_000, 120_000), ActivityLogEntry("2026-10-19", "en", 600_000), ActivityLogEntry("2026-10-27", "en", 600_000))
        assertEquals(ActivityMinutes(.5, 1.5, 2.0), activityMinutes(rows, today, 7))
        assertEquals(ActivityMinutes(0.0, 0.0, 0.0), activityMinutes(rows, today, 1))
        val time = Instant.parse("2026-10-26T12:00:00Z").toEpochMilliseconds()
        val logs = listOf(true, false, true).map { ReviewLogEntry(entryId = 1, lang = "en", timestampMs = time, knew = it, boxBefore = 1, boxAfter = 2) }
        assertEquals(DailyReviews(today, 3, 67), dailyReviews(logs, today, 1, TimeZone.UTC).single())
    }
}
