package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.translate.MeaningOption
import kotlin.test.*

class ReviewMeaningTest {
    @Test fun replaceOneScriptKeepsOtherTranslationAndPackSuffix() {
        val result = replaceMeaningLine("خانه\nhouse — Lesson 2", "home", Regex("\\s*— Lesson \\d+$"))
        assertEquals("خانه\nhome — Lesson 2", result.meaning)
        assertEquals("house", result.previousLine)
    }
    @Test fun absentScriptIsAppended() {
        assertEquals("خانه\nhome", replaceMeaningLine("خانه", "home").meaning)
    }
    @Test fun previousAlternativeCanBeRestoredWithoutDuplicatePills() {
        val options = listOf("خانه", "منزل", "house").map { MeaningOption(it, null, null) }
        val result = reviewMeaningOptions(options, "منزل\nhouse", "منزل", "خانه")
        assertEquals(listOf("منزل", "خانه"), result.map { it.text })
    }
}
