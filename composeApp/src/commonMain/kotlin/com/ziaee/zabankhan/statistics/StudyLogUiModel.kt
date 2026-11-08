package com.ziaee.zabankhan.statistics

import com.ziaee.zabankhan.data.SourceKind
import com.ziaee.zabankhan.data.StudySkill
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.StringResource

fun studySkillLabelResource(skill: StudySkill): StringResource = when (skill) {
    StudySkill.CO -> Res.string.study_skill_co
    StudySkill.CE -> Res.string.study_skill_ce
    StudySkill.EE -> Res.string.study_skill_ee
    StudySkill.EO -> Res.string.study_skill_eo
    StudySkill.VOC -> Res.string.study_skill_vocabulary
    StudySkill.GRAM -> Res.string.study_skill_grammar
    StudySkill.LEX -> Res.string.study_skill_legacy
}

fun studySourceKindLabelResource(kind: SourceKind): StringResource = when (kind) {
    SourceKind.BOOK -> Res.string.study_source_book
    SourceKind.NOTES -> Res.string.study_source_notes
    SourceKind.AUDIO -> Res.string.study_source_audio
    SourceKind.VIDEO -> Res.string.study_source_video
    SourceKind.WEBSITE -> Res.string.study_source_website
    SourceKind.APP -> Res.string.study_source_app
    SourceKind.MOCK_TEST -> Res.string.study_source_mock_test
    SourceKind.OTHER -> Res.string.study_source_other
}
