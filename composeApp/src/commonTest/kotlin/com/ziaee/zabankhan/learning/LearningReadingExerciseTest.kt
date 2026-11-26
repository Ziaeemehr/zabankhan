package com.ziaee.zabankhan.learning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearningReadingExerciseTest {
    @Test
    fun onlyTheKeyScoresCorrectAndEachChoiceGetsFeedback() {
        val exercise = LearningReadingExercise.fromJson(fixture)
        assertEquals("fr.a1.where-i-live.001", exercise.id)
        assertTrue(exercise.evaluate("city_lyon")?.isCorrect == true)
        val incorrect = requireNotNull(exercise.evaluate("city_paris"))
        assertFalse(incorrect.isCorrect)
        assertEquals("متن می‌گوید لینا در لیون زندگی می‌کند، نه پاریس.", incorrect.feedback)
        assertNull(exercise.evaluate("unknown"))
    }

    @Test
    fun rejectsUnknownKeysAndEvidenceMissingFromTheText() {
        assertFailsWith<IllegalArgumentException> {
            LearningReadingExercise.fromJson(fixture.replace("[\"city_lyon\"]", "[\"missing\"]"))
        }
        assertFailsWith<IllegalArgumentException> {
            LearningReadingExercise.fromJson(fixture.replace("\"evidence\":\"Lina habite à Lyon.\"", "\"evidence\":\"Lina habite à Paris.\""))
        }
    }

    private val fixture = """
        {"id":"fr.a1.where-i-live.001","revision":1,"level":"A1","targetLanguage":"fr","objective":{"fa":"درک اطلاعات صریح دربارهٔ محل زندگی یک شخص"},"stimulus":"Lina habite à Lyon. Elle aime le café.","prompt":{"fa":"لینا در کدام شهر زندگی می‌کند؟"},"options":{"city_lyon":"Lyon","city_paris":"Paris","city_lille":"Lille"},"acceptedOptionIds":["city_lyon"],"evidence":"Lina habite à Lyon.","feedback":{"fa":"پاسخ درست است؛ متن می‌گوید لینا در لیون زندگی می‌کند."},"distractorFeedback":{"city_paris":"متن می‌گوید لینا در لیون زندگی می‌کند، نه پاریس.","city_lille":"متن می‌گوید لینا در لیون زندگی می‌کند، نه لیل."}}
    """.trimIndent()
}
