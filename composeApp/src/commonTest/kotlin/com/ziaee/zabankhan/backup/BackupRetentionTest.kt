package com.ziaee.zabankhan.backup

import kotlinx.datetime.LocalDateTime
import kotlin.test.*

class BackupRetentionTest {
    @Test fun guardsEveryUserDataCountWithBothThresholds() {
        assertTrue(isLargeLoss(BackupCounts(25, 0, 0), BackupCounts(19, 0, 0)))
        assertFalse(isLargeLoss(BackupCounts(25, 0, 0), BackupCounts(20, 0, 0)))
        assertFalse(isLargeLoss(BackupCounts(10, 0, 0), BackupCounts(6, 0, 0)))
        assertTrue(isLargeLoss(BackupCounts(0, 25, 0), BackupCounts(0, 19, 0)))
        assertTrue(isLargeLoss(BackupCounts(0, 0, 25), BackupCounts(0, 0, 19)))
        assertFalse(isLargeLoss(BackupCounts(0, 0, 0), BackupCounts(5, 5, 5)))
    }
    @Test fun burstOfClosesCannotRotateAwayStartOfDayOrPriorWeeks() {
        val now = LocalDateTime.parse("2026-11-07T20:00:00")
        val first = LocalDateTime.parse("2026-11-07T01:00:00")
        val previousWeek = LocalDateTime.parse("2026-10-29T10:00:00")
        val oldest = LocalDateTime.parse("2026-09-01T10:00:00")
        val burst = (10..19).map { LocalDateTime.parse("2026-11-07T${it}:00:00") }
        val future = LocalDateTime.parse("2026-11-08T10:00:00")
        val keep = selectBackupsToKeep(burst + listOf(first, previousWeek, oldest, future), now)
        assertTrue(first in keep)
        assertTrue(previousWeek in keep)
        assertTrue(future in keep)
        assertTrue(oldest !in keep)
        assertTrue(burst.takeLast(2).all { it in keep })
        assertTrue(burst.first() !in keep)
    }
    @Test fun restoreWithoutPdfsPreservesExistingPdfs() {
        assertFalse("pdfs" in restoreFileDirectories())
        assertTrue("text_images" in restoreFileDirectories())
    }
    @Test fun protectedRotationKeepsFiveAndIgnoresIncompleteFiles() {
        val names = (1L..8L).map { "zabankhan-keep-${(1_780_000_000_000L + it).toString().padStart(16, '0')}.zip" }
        assertEquals(names.take(3).reversed(), selectProtectedBackupsToDelete(names + "zabankhan-keep-bad.zip" + "zabankhan-keep-123.zip.tmp"))
    }
    @Test fun englishEntitiesKeepTheirLanguageAndGenericQuestion() {
        val attempt = com.ziaee.zabankhan.data.ExamAttempt(examId = "ielts", questionId = "1", question = "Describe your town", answer = "", score = null, note = "", createdAtMs = 1, updatedAtMs = 1, lang = "en")
        assertEquals("en", attempt.lang)
        assertEquals("Describe your town", attempt.question)
    }
}
