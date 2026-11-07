package com.ziaee.zabankhan.progress

import com.ziaee.zabankhan.data.*
import kotlinx.datetime.*
import kotlinx.serialization.json.Json
import kotlin.test.*

class ProgressTest {
    private val today = LocalDate(2026, 11, 7)
    private val zone = TimeZone.UTC
    private fun input() = ProgressInputs(language = "fr", today = today, zone = zone)
    private fun review(lang: String = "fr", correct: Boolean = true, date: String = "2026-11-07T12:00:00Z") =
        ReviewLogEntry(entryId = 1, lang = lang, knew = correct, timestampMs = kotlin.time.Instant.parse(date).toEpochMilliseconds(), boxBefore = 1, boxAfter = 2)
    @Test fun emptyAndSerialization() {
        val result = computeProgress(input())
        assertEquals(0L, result.totalPoints)
        assertEquals(0, result.level)
        assertEquals(100L, result.pointsToNextLevel)
        assertEquals(300, result.weeklyGoal)
        assertTrue(result.badges.none { it.unlocked })
        assertEquals(result, Json.decodeFromString<ProgressSummary>(Json.encodeToString(result)))
    }
    @Test fun weightsGlobalTotalsAndWeeklyExclusions() {
        val result = computeProgress(input().copy(
            activity = listOf(ActivityLogEntry("2026-11-07", "fr", readingMs = 119_999)),
            reviews = listOf(review(), review("en", false)),
            learnedWords = mapOf("fr" to 2L, "en" to 3L), finishedTexts = mapOf("fr" to 1L, "en" to 2L),
            exams = listOf(ProgressExam("en", review().timestampMs))))
        // reading 1 + reviews 5 + learned 25 + finished 60 + exam 10 + streak 10
        assertEquals(111L, result.totalPoints)
        assertEquals(26L, result.weeklyPoints)
        assertEquals(1L, result.badges.first { it.id == "first_text" }.current)
    }
    @Test fun exactLevelBoundaries() {
        for ((points, level) in listOf(0L to 0, 99L to 0, 100L to 1, 299L to 1, 300L to 2, 599L to 2, 600L to 3)) {
            // Old reading date avoids a current streak bonus.
            val result = computeProgress(input().copy(activity = listOf(ActivityLogEntry("2020-01-01", "en", readingMs = points * 60_000))))
            assertEquals(level, result.level, "points=$points")
            assertTrue(result.levelFraction in 0f..1f)
            assertEquals(100L * level * (level + 1) / 2, result.totalPoints - result.pointsInLevel)
        }
    }
    @Test fun streakCapAndMondayGrace() {
        val monday = LocalDate(2026, 11, 9)
        val days = (1..40).map { ActivityLogEntry(monday.minus(it, DateTimeUnit.DAY).toString(), "fr", listeningMs = 1) }
        val result = computeProgress(input().copy(today = monday, activity = days))
        assertEquals(100L, result.totalPoints)
        assertEquals(0L, result.weeklyPoints)
        assertTrue(result.badges.first { it.id == "streak_30" }.unlocked)
    }
    @Test fun everyBadgeBoundaryAndLanguageScope() {
        for (above in listOf(false, true)) {
            val count = if (above) 1000L else 99L
            val days = (0 until if (above) 30 else 6).map { ActivityLogEntry(today.minus(it, DateTimeUnit.DAY).toString(), "fr", readingMs = if (it == 0) (if (above) 600L else 599L) * 60_000 else 0, listeningMs = 1) }
            val result = computeProgress(input().copy(activity = days, learnedWords = mapOf("fr" to count, "en" to 5000), finishedTexts = mapOf("fr" to if (above) 10L else 0L), reviews = List(if (above) 1000 else 999) { review() }))
            assertEquals(9, result.badges.size)
            assertTrue(result.badges.all { it.unlocked == above })
        }
        for (target in listOf(100L, 500L, 1000L)) for (value in listOf(target - 1, target)) {
            val badge = computeProgress(input().copy(learnedWords = mapOf("fr" to value))).badges.first { it.id == "learned_$target" }
            assertEquals(value >= target, badge.unlocked)
        }
    }
    @Test fun localWeekUsesTimestampTimezoneAndExcludesNextMonday() {
        val result = computeProgress(input().copy(zone = TimeZone.of("Europe/Paris"), reviews = listOf(
            review(date = "2026-11-01T22:59:59Z"), // Sunday local, previous week
            review(date = "2026-11-01T23:00:00Z"), // Monday local
            review(date = "2026-11-08T22:59:59Z"), // Sunday local
            review(date = "2026-11-08T23:00:00Z") // next Monday
        )))
        assertEquals(6L, result.weeklyPoints)
        assertEquals("2026-11-02", result.weekStart)
    }
    @Test fun eachScoreComponentUsesLongArithmetic() {
        assertEquals(2L, computeProgress(input().copy(activity = listOf(ActivityLogEntry("2020-01-01", "en", readingMs = 120_000, listeningMs = 9_000_000, watchingMs = 9_000_000)))).totalPoints)
        assertEquals(2L, computeProgress(input().copy(reviews = listOf(review(correct = false, date = "2020-01-01T12:00:00Z")))).totalPoints)
        assertEquals(3L, computeProgress(input().copy(reviews = listOf(review(date = "2020-01-01T12:00:00Z")))).totalPoints)
        assertEquals(5L, computeProgress(input().copy(learnedWords = mapOf("en" to 1L))).totalPoints)
        assertEquals(20L, computeProgress(input().copy(finishedTexts = mapOf("en" to 1L))).totalPoints)
        assertEquals(10L, computeProgress(input().copy(exams = listOf(ProgressExam("en", 0)))).totalPoints)
        val big = computeProgress(input().copy(learnedWords = mapOf("en" to 1_000_000_000L)))
        assertEquals(5_000_000_000L, big.totalPoints)
        assertTrue(big.pointsToNextLevel > 0)
    }
    @Test fun streakBadgesAtExactThresholdAndWeeklyStreakCap() {
        for (target in listOf(7, 30)) for (count in listOf(target - 1, target)) {
            val rows = (0 until count).map { ActivityLogEntry(today.minus(it, DateTimeUnit.DAY).toString(), "fr", listeningMs = 1) }
            val summary = computeProgress(input().copy(activity = rows))
            assertEquals(count >= target, summary.badges.first { it.id == "streak_$target" }.unlocked)
            assertEquals(minOf(count, 10) * 10L, summary.totalPoints)
            // Saturday, so at most Monday through Saturday can contribute.
            assertEquals(minOf(count, 6) * 10L, summary.weeklyPoints)
        }
    }
    @Test fun weeklyGoalBounds() {
        assertEquals(MIN_WEEKLY_GOAL, computeProgress(input().copy(weeklyGoal = -1)).weeklyGoal)
        assertEquals(MAX_WEEKLY_GOAL, computeProgress(input().copy(weeklyGoal = Int.MAX_VALUE)).weeklyGoal)
    }
    @Test fun celebrationsAreMonotonicAndLanguageQualified() {
        val summary = computeProgress(input().copy(learnedWords = mapOf("fr" to 100L)))
        val first = progressCelebration(summary, ProgressSeen())
        assertTrue(first.levelIncreased)
        assertEquals(1, first.newBadgeCount)
        assertFalse(progressCelebration(summary, first.seen).hasNews)
        val decayed = progressCelebration(computeProgress(input()), first.seen)
        assertFalse(decayed.hasNews)
        assertFalse(progressCelebration(summary, decayed.seen).hasNews)
        val english = computeProgress(input().copy(language = "en", learnedWords = mapOf("en" to 100L)))
        val switched = progressCelebration(english, first.seen)
        assertFalse(switched.levelIncreased)
        assertEquals(1, switched.newBadgeCount)
        assertEquals(setOf("fr:learned_100", "en:learned_100"), switched.seen.badges)
        assertEquals(switched.seen, Json.decodeFromString<ProgressSeen>(Json.encodeToString(switched.seen)))
    }

}
