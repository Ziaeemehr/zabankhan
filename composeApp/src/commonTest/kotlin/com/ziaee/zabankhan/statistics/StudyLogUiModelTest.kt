package com.ziaee.zabankhan.statistics

import com.ziaee.zabankhan.data.SourceKind
import com.ziaee.zabankhan.data.StudySkill
import com.ziaee.zabankhan.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyLogUiModelTest {
    @Test
    fun everyStudySkillHasALocalizedResourceIncludingLegacyCombinedSkill() {
        assertEquals(Res.string.study_skill_co, studySkillLabelResource(StudySkill.CO))
        assertEquals(Res.string.study_skill_ce, studySkillLabelResource(StudySkill.CE))
        assertEquals(Res.string.study_skill_ee, studySkillLabelResource(StudySkill.EE))
        assertEquals(Res.string.study_skill_eo, studySkillLabelResource(StudySkill.EO))
        assertEquals(Res.string.study_skill_vocabulary, studySkillLabelResource(StudySkill.VOC))
        assertEquals(Res.string.study_skill_grammar, studySkillLabelResource(StudySkill.GRAM))
        assertEquals(Res.string.study_skill_legacy, studySkillLabelResource(StudySkill.LEX))
    }

    @Test
    fun everySourceKindHasALocalizedResource() {
        assertEquals(Res.string.study_source_book, studySourceKindLabelResource(SourceKind.BOOK))
        assertEquals(Res.string.study_source_notes, studySourceKindLabelResource(SourceKind.NOTES))
        assertEquals(Res.string.study_source_audio, studySourceKindLabelResource(SourceKind.AUDIO))
        assertEquals(Res.string.study_source_video, studySourceKindLabelResource(SourceKind.VIDEO))
        assertEquals(Res.string.study_source_website, studySourceKindLabelResource(SourceKind.WEBSITE))
        assertEquals(Res.string.study_source_app, studySourceKindLabelResource(SourceKind.APP))
        assertEquals(Res.string.study_source_mock_test, studySourceKindLabelResource(SourceKind.MOCK_TEST))
        assertEquals(Res.string.study_source_other, studySourceKindLabelResource(SourceKind.OTHER))
    }
}
