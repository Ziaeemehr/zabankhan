package com.ziaee.zabankhan.reminder

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class ReviewReminderTest {
    private val paris = TimeZone.of("Europe/Paris")
    @Test fun springGapResolvesLocalTime() {
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), nextReviewReminderAt(Instant.parse("2026-03-28T23:00:00Z"), 2, 30, paris))
    }
    @Test fun tomorrowDoesNotKeepSpringGapAdjustment() {
        assertEquals(Instant.parse("2026-03-30T00:30:00Z"), nextReviewReminderAt(Instant.parse("2026-03-29T02:00:00Z"), 2, 30, paris))
    }
    @Test fun exactTimeSchedulesTomorrowAcrossFallDst() {
        assertEquals(Instant.parse("2026-10-25T18:00:00Z"), nextReviewReminderAt(Instant.parse("2026-10-24T17:00:00Z"), 19, 0, paris))
    }
    @Test fun laterTimeTodayIsUsed() {
        assertEquals(Instant.parse("2026-11-04T18:15:00Z"), nextReviewReminderAt(Instant.parse("2026-11-04T10:00:00Z"), 19, 15, paris))
    }
    @Test fun invalidTimesAreRejected() {
        assertFailsWith<IllegalArgumentException> { nextReviewReminderAt(Instant.parse("2026-11-04T10:00:00Z"), 24, 0, paris) }
        assertFailsWith<IllegalArgumentException> { nextReviewReminderAt(Instant.parse("2026-11-04T10:00:00Z"), 0, 60, paris) }
    }
}
