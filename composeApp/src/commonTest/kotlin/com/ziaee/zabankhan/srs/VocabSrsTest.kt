package com.ziaee.zabankhan.srs

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.*
import kotlin.time.Instant
import com.ziaee.zabankhan.lang.Script

class VocabSrsTest {
    @Test fun `due date after a midnight gap returns to start of target day`() {
        val zone = TimeZone.of("America/Santiago")
        val now = LocalDateTime(2026, 9, 6, 12, 0).toInstant(zone).toEpochMilliseconds()
        val expected = LocalDate(2026, 9, 7).atStartOfDayIn(zone).toEpochMilliseconds()
        assertEquals(expected, VocabSrs.dueAtMs(1, now, VocabSrs.DEFAULT_INTERVAL_DAYS, zone))
    }

    @Test fun `reminder after spring gap returns to configured hour tomorrow`() {
        val now = Instant.parse("2026-03-29T04:00:00+02:00")
        assertEquals(
            Instant.parse("2026-03-30T02:30:00+02:00"),
            nextReviewReminderAt(now, 2, 30, TimeZone.of("Europe/Paris"))
        )
    }

    @Test fun `reminder on spring gap day uses resolved local time`() {
        val now = Instant.parse("2026-03-29T00:00:00+01:00")
        assertEquals(
            Instant.parse("2026-03-29T03:30:00+02:00"),
            nextReviewReminderAt(now, 2, 30, TimeZone.of("Europe/Paris"))
        )
    }

    @Test fun `reminder exactly at scheduled time schedules tomorrow`() {
        val now = Instant.parse("2026-10-24T19:00:00+02:00")
        assertEquals(
            Instant.parse("2026-10-25T19:00:00+01:00"),
            nextReviewReminderAt(now, 19, 0, TimeZone.of("Europe/Paris"))
        )
    }

    @Test fun latinScriptRejectsArabicAndNonLatinFronts() {
        assertTrue(looksLikeScript("bonjour", Script.LATIN))
        assertTrue(looksLikeScript("C'est déjà l'été.", Script.LATIN))
        assertFalse(looksLikeScript("سلام", Script.LATIN))
        assertFalse(looksLikeScript("bonjour سلام", Script.LATIN))
        assertFalse(looksLikeScript("12345", Script.LATIN))
    }
    @Test fun arabicScriptAcceptsPersianAndRejectsMixedLetters() {
        assertTrue(looksLikeScript("می‌روم", Script.ARABIC))
        assertFalse(looksLikeScript("سلام hello", Script.ARABIC))
        assertFalse(looksLikeScript("۱۲۳", Script.ARABIC))
        assertTrue(looksLikeScript("Ελληνικά", Script.OTHER))
    }

    @Test fun callerSuppliedMeaningTagsAreRemoved() {
        val tag = Regex("(?:^|\\s*—\\s*)Lesson \\d+$")
        assertEquals("meaning", stripTrailingMeaningTag("meaning — Lesson 2", tag))
        assertEquals(null, stripTrailingMeaningTag("Lesson 2", tag))
        assertEquals("meaning", stripTrailingMeaningTag("meaning", tag))
        assertEquals(null, stripTrailingMeaningTag(null, tag))
    }

    private val now = 1_000_000L
    private val utc = TimeZone.of("UTC")

    @Test
    fun `again returns card to box one and schedules the next calendar day`() {
        val result = VocabSrs.apply(entry(box = 4, learned = true), VocabAnswer.FORGOT, now, zoneId = utc)

        assertEquals(1, result.leitnerBox)
        assertEquals(VocabSrs.DAY_MS, result.nextReviewAtMs)
        assertEquals(now, result.lastReviewedAtMs)
        assertFalse(result.learned)
    }

    @Test
    fun `hard keeps the box and uses its full interval`() {
        val source = entry(box = 3)

        val result = VocabSrs.apply(source, VocabAnswer.HARD, now, zoneId = utc)

        assertEquals(3, result.leitnerBox)
        assertEquals(4L * VocabSrs.DAY_MS, result.nextReviewAtMs)
        assertEquals(4L, VocabSrs.previewIntervalDays(source, VocabAnswer.HARD))
    }

    @Test
    fun `knew advances the box and uses the new box interval`() {
        val result = VocabSrs.apply(entry(box = 2), VocabAnswer.KNEW, now, zoneId = utc)

        assertEquals(3, result.leitnerBox)
        assertEquals(4L * VocabSrs.DAY_MS, result.nextReviewAtMs)
        assertFalse(result.learned)
    }

    @Test
    fun `good caps at box five and graduates it`() {
        val promoted = VocabSrs.apply(entry(box = 4), VocabAnswer.KNEW, now, zoneId = utc)
        val capped = VocabSrs.apply(entry(box = 5), VocabAnswer.KNEW, now, zoneId = utc)

        assertEquals(5, promoted.leitnerBox)
        assertTrue(promoted.learned)
        assertEquals(5, capped.leitnerBox)
        assertEquals(16L * VocabSrs.DAY_MS, capped.nextReviewAtMs)
        assertTrue(capped.learned)
    }

    @Test
    fun `custom intervals are applied from the start of the current day`() {
        val intervals = listOf(2L, 5L, 9L, 20L, 40L)

        val later = now + 3 * VocabSrs.DAY_MS
        val source = entry(box = 1, reviewedAt = now)

        val result = VocabSrs.apply(source, VocabAnswer.KNEW, later, intervals, utc)

        assertEquals(2, result.leitnerBox)
        assertEquals(8L * VocabSrs.DAY_MS, result.nextReviewAtMs)
        assertEquals(5L, VocabSrs.previewIntervalDays(source, VocabAnswer.KNEW, intervals, later, utc))
    }

    @Test
    fun `good on a new card schedules its first review for tomorrow`() {
        val source = entry(box = 1)

        val result = VocabSrs.apply(source, VocabAnswer.KNEW, now, zoneId = utc)

        assertEquals(1, result.leitnerBox)
        assertEquals(VocabSrs.DAY_MS, result.nextReviewAtMs)
        assertEquals(1L, VocabSrs.previewIntervalDays(source, VocabAnswer.KNEW, nowMs = now, zoneId = utc))
    }

    @Test
    fun `good after again the same day still waits for tomorrow`() {
        val forgotten = VocabSrs.apply(entry(box = 4, reviewedAt = 1L), VocabAnswer.FORGOT, now, zoneId = utc)

        val result = VocabSrs.apply(forgotten, VocabAnswer.KNEW, now + 60_000, zoneId = utc)

        assertEquals(1, result.leitnerBox)
        assertEquals(VocabSrs.DAY_MS, result.nextReviewAtMs)
    }

    @Test
    fun `learning step covers new and same-day box one cards only`() {
        assertTrue(VocabSrs.isLearningStep(entry(box = 1), now, utc))
        assertTrue(VocabSrs.isLearningStep(entry(box = 1, reviewedAt = now - 1), now, utc))
        assertFalse(VocabSrs.isLearningStep(entry(box = 1, reviewedAt = now - VocabSrs.DAY_MS), now, utc))
        assertFalse(VocabSrs.isLearningStep(entry(box = 2, reviewedAt = now - 1), now, utc))
    }

    @Test
    fun `overdue timing does not shorten or extend the configured interval`() {
        val muchLater = 20L * VocabSrs.DAY_MS + now

        val result = VocabSrs.apply(entry(box = 2), VocabAnswer.HARD, muchLater, zoneId = utc)

        assertEquals(22L * VocabSrs.DAY_MS, result.nextReviewAtMs)
    }

    @Test
    fun `status maps learning stages and honors learned flag`() {
        assertEquals(VocabStatus.NEW, entry(box = 1).status())
        assertEquals(VocabStatus.LEARNING, entry(box = 2, reviewedAt = 1L).status())
        assertEquals(VocabStatus.REVIEWING, entry(box = 4, reviewedAt = 1L).status())
        assertEquals(VocabStatus.LEARNED, entry(box = 5, reviewedAt = 1L).status())
        assertEquals(VocabStatus.LEARNED, entry(box = 1, reviewedAt = 1L, learned = true).status())
    }

    private fun entry(
        box: Int,
        reviewedAt: Long? = null,
        learned: Boolean = false
    ) = SrsState(leitnerBox = box, lastReviewedAtMs = reviewedAt, learned = learned)
}
