package com.ziaee.zabankhan.statistics

import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.test.*

/** Adapted from FrenchReader's ActivityTimerTest; clocks remain deterministic on Native. */
class ActivityTimerPortTest {
    private var elapsed = 0L
    private var wall = Instant.parse("2026-10-26T12:00:00Z").toEpochMilliseconds()
    private fun advance(ms: Long) { elapsed += ms; wall += ms }
    private fun timer(reading: Boolean) = ActivityTimer(reading, { elapsed }, { wall }, { TimeZone.UTC })

    @Test fun interactionExtendsReadingAndPauseExcludesTime() {
        val timer = timer(true)
        timer.setEnabled(true); timer.interact(); advance(50_000); timer.interact()
        advance(20_000); timer.setEnabled(false); advance(20_000); timer.setEnabled(true); advance(30_000)
        assertEquals(mapOf("2026-10-26" to 90_000L), timer.drain())
    }
    @Test fun expiredInteractionCanResumeWithoutInventingTime() {
        val timer = timer(true)
        timer.setEnabled(true); advance(20_000)
        assertEquals(emptyMap(), timer.drain())
        timer.interact(); advance(90_000)
        assertEquals(mapOf("2026-10-26" to 60_000L), timer.drain())
        advance(10_000); timer.interact(); advance(5_000)
        assertEquals(mapOf("2026-10-26" to 5_000L), timer.drain())
    }
    @Test fun midnightUsesLocalZoneInsteadOfUtc() {
        wall = Instant.parse("2026-10-26T22:59:50Z").toEpochMilliseconds()
        val timer = ActivityTimer(false, { elapsed }, { wall }, { TimeZone.of("Europe/Paris") })
        timer.setEnabled(true); advance(25_000)
        assertEquals(mapOf("2026-10-26" to 10_000L, "2026-10-27" to 15_000L), timer.drain())
    }
    @Test fun elapsedTimeSurvivesWallClockAdjustment() {
        val timer = timer(false)
        timer.setEnabled(true)
        elapsed += 10_000; wall -= 100_000
        assertEquals(mapOf("2026-10-26" to 10_000L), timer.drain())
    }
}
