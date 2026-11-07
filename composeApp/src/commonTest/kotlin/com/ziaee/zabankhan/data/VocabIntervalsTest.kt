package com.ziaee.zabankhan.data

import com.ziaee.zabankhan.srs.VocabSrs
import kotlin.test.*

class VocabIntervalsTest {
    @Test fun restoreRejectsPartialMalformedAndOutOfRangeSchedules() {
        listOf(null, "", "1,2,4", "1,2,x,8,16", "0,2,4,8,16", "1,2,4,8,366")
            .forEach { assertEquals(VocabSrs.DEFAULT_INTERVAL_DAYS, restoreReviewIntervals(it)) }
    }
    @Test fun customScheduleSurvivesStorage() {
        val days = listOf(2L, 3L, 7L, 14L, 30L)
        assertEquals(days, restoreReviewIntervals(days.joinToString(",")))
    }
}
