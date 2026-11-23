package com.ziaee.zabankhan.statistics

import com.ziaee.zabankhan.data.VocabEntry
import com.ziaee.zabankhan.lang.Languages
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StatisticsAggregatesTest {
    @Test
    fun learnedCountKeepsBoxFourInLearningAndIncludesManualMarks() {
        val boxFour = VocabEntry(lang = Languages.FRENCH.code, word = "bonjour", leitnerBox = 4, lastReviewedAtMs = 1)
        val boxFive = boxFour.copy(word = "merci", leitnerBox = 5)
        val manuallyLearned = boxFour.copy(word = "salut", leitnerBox = 1, learned = true)

        assertEquals(WordStatusCounts(new = 0, learning = 1, known = 2),
            wordStatusCounts(listOf(boxFour, boxFive, manuallyLearned)))
    }

    @Test
    fun zeroTrendFromZeroIsNeutral() {
        assertEquals(TrendDelta(0, false), trendDelta(0.0, 0.0))
    }

    @Test
    fun monthlyTotalsGroupsMinutesByMonthAndIgnoresOtherYears() {
        val days = listOf(
            StudyDayActivity(LocalDate(2025, 1, 3), ActivityMinutes(10.0, 5.0, 0.0), 7),
            StudyDayActivity(LocalDate(2025, 1, 20), ActivityMinutes(0.0, 0.0, 12.0), 0),
            StudyDayActivity(LocalDate(2025, 12, 1), ActivityMinutes(30.0, 0.0, 0.0), 5),
            StudyDayActivity(LocalDate(2024, 1, 3), ActivityMinutes(100.0, 0.0, 0.0), 0),
        )

        val totals = monthlyTotals(days, 2025)

        assertEquals(12, totals.size)
        assertEquals(34.0, totals[0])
        assertEquals(0.0, totals[1])
        assertEquals(35.0, totals[11])
    }
}
