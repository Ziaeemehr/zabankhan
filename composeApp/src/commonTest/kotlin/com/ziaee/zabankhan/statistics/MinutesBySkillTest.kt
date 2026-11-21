package com.ziaee.zabankhan.statistics

import com.ziaee.zabankhan.data.StudySession
import com.ziaee.zabankhan.data.StudySkill
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MinutesBySkillTest {
    private val to = LocalDate(2026, 1, 10)
    private fun s(day: LocalDate, min: Int, skill: StudySkill) = StudySession(date = day.toEpochDays().toLong(), durationMin = min, skill = skill, lang = "fr")

    @Test
    fun sumsPerSkillInRangeSortedAndDropsZero() {
        val rows = listOf(s(to, 10, StudySkill.CO), s(LocalDate(2026, 1, 4), 30, StudySkill.CO), s(to, 20, StudySkill.EE),
            s(to, 0, StudySkill.VOC), s(LocalDate(2026, 1, 3), 99, StudySkill.GRAM))
        assertEquals(listOf(StudySkill.CO to 40, StudySkill.EE to 20), minutesBySkill(rows, LocalDate(2026, 1, 4), to))
    }
}
