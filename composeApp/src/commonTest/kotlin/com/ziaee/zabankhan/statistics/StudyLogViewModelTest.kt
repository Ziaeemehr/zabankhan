package com.ziaee.zabankhan.statistics

import com.ziaee.zabankhan.data.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyLogViewModelTest {
    @Test
    fun addsEditsDeletesAndRestoresSession() = runTest {
        val dao = FakeStudyLogDao()
        val viewModel = StudyLogViewModel(dao, "fr")
        val sourceId = dao.insertSource(StudySource(name = "Reader", lang = "fr"))
        val date = LocalDate(2026, 11, 7).toEpochDays().toLong()
        val original = StudySession(date = date, durationMin = 25, skill = StudySkill.CE, sourceId = sourceId, lang = "fr")

        assertEquals(StudyLogSaveResult.Saved, viewModel.saveSession(original, LocalDate(2026, 11, 8)))
        val saved = dao.observeSessions("fr", Long.MIN_VALUE, Long.MAX_VALUE).first().single()
        assertEquals(25, saved.durationMin)
        assertEquals("fr", saved.lang)
        assertEquals(StudyLogSaveResult.Saved, viewModel.saveSession(saved.copy(durationMin = 40), LocalDate(2026, 11, 8)))
        assertEquals(40, dao.getSession(saved.id)?.durationMin)

        val deleted = viewModel.deleteSession(saved.copy(durationMin = 40))
        assertTrue(deleted)
        assertTrue(dao.observeSessions("fr", Long.MIN_VALUE, Long.MAX_VALUE).first().isEmpty())
        assertTrue(viewModel.restoreSession(saved.copy(durationMin = 40)))
        assertEquals(40, dao.observeSessions("fr", Long.MIN_VALUE, Long.MAX_VALUE).first().single().durationMin)
    }

    @Test
    fun rejectsInvalidDurationFutureDateAndCrossLanguageWrites() = runTest {
        val dao = FakeStudyLogDao()
        val viewModel = StudyLogViewModel(dao, "fr")
        val today = LocalDate(2026, 11, 8)
        val yesterday = today.minus(1, kotlinx.datetime.DateTimeUnit.DAY).toEpochDays().toLong()

        assertEquals(
            StudyLogSaveResult.Invalid(StudyLogValidation.INVALID_DURATION),
            viewModel.saveSession(StudySession(date = yesterday, durationMin = 0, skill = StudySkill.CE, lang = "fr"), today)
        )
        assertEquals(
            StudyLogSaveResult.Invalid(StudyLogValidation.FUTURE_DATE),
            viewModel.saveSession(StudySession(date = today.toEpochDays().toLong() + 1L, durationMin = 30, skill = StudySkill.CE, lang = "fr"), today)
        )
        assertEquals(
            StudyLogSaveResult.Invalid(StudyLogValidation.WRONG_LANGUAGE),
            viewModel.saveSession(StudySession(date = yesterday, durationMin = 30, skill = StudySkill.CE, lang = "en"), today)
        )
    }

    @Test
    fun sourceNamesAreNormalizedAndSourcesStayLanguageScoped() = runTest {
        val dao = FakeStudyLogDao()
        val viewModel = StudyLogViewModel(dao, "fr")

        assertEquals(StudyLogValidation.VALID, viewModel.saveSource(StudySource(name = "  Book A  ", lang = "fr")))
        assertEquals(StudyLogValidation.DUPLICATE_SOURCE_NAME, viewModel.saveSource(StudySource(name = "book a", lang = "fr")))
        assertEquals(StudyLogValidation.WRONG_LANGUAGE, viewModel.saveSource(StudySource(name = "English Book", lang = "en")))
        assertEquals("Book A", dao.observeSources("fr").first().single().name)
        assertTrue(dao.observeSources("en").first().isEmpty())
        dao.insertSession(StudySession(date = 20, durationMin = 10, skill = StudySkill.EE, lang = "en"))
        dao.insertSession(StudySession(date = 20, durationMin = 15, skill = StudySkill.CE, lang = "fr"))
        val state = viewModel.uiState.first { it.sources.isNotEmpty() && it.allSessions.isNotEmpty() }
        assertEquals("fr", state.lang)
        assertEquals(listOf("fr"), state.allSessions.map { it.lang }.distinct())
    }

    @Test
    fun deletingUsedSourceArchivesItAndKeepsHistoricalAssociation() = runTest {
        val dao = FakeStudyLogDao()
        val viewModel = StudyLogViewModel(dao, "fr")
        viewModel.saveSource(StudySource(name = "Workbook", kind = SourceKind.BOOK, lang = "fr"))
        val source = dao.observeSources("fr").first().single()
        val date = LocalDate(2026, 11, 7).toEpochDays().toLong()
        viewModel.saveSession(StudySession(date = date, durationMin = 20, skill = StudySkill.GRAM, sourceId = source.id, lang = "fr"), LocalDate(2026, 11, 8))

        assertEquals(StudyLogSourceDeleteResult.ARCHIVED, viewModel.deleteUnusedSource(source.id))
        assertTrue(dao.getSource(source.id)?.archived == true)
        assertEquals(source.id, dao.observeSessions("fr", Long.MIN_VALUE, Long.MAX_VALUE).first().single().sourceId)
        assertFalse(studySourcePickerOptions(dao.observeSources("fr").first(), selectedSourceId = null).any { it.id == source.id })
        assertTrue(studySourcePickerOptions(dao.observeSources("fr").first(), selectedSourceId = source.id).any { it.id == source.id })
    }

    @Test
    fun concurrentSavesReturnInFlightInsteadOfWritingTwice() = runTest {
        val dao = FakeStudyLogDao()
        val gate = CompletableDeferred<Unit>()
        dao.insertGate = gate
        val viewModel = StudyLogViewModel(dao, "fr")
        val date = LocalDate(2026, 11, 7).toEpochDays().toLong()
        val session = StudySession(date = date, durationMin = 20, skill = StudySkill.VOC, lang = "fr")

        val first = async { viewModel.saveSession(session, LocalDate(2026, 11, 8)) }
        while (dao.insertCalls == 0) kotlinx.coroutines.yield()
        assertEquals(StudyLogSaveResult.InFlight, viewModel.saveSession(session, LocalDate(2026, 11, 8)))
        gate.complete(Unit)
        assertEquals(StudyLogSaveResult.Saved, first.await())
        assertEquals(1, dao.sessions.value.size)
    }

    @Test
    fun parsesDurationWithinOneDayAndRejectsInvalidParts() {
        assertEquals(90, parseStudyDuration("1", "30"))
        assertEquals(1440, parseStudyDuration("24", "0"))
        assertEquals(null, parseStudyDuration("24", "1"))
        assertEquals(null, parseStudyDuration("1", "60"))
        assertEquals(null, parseStudyDuration("x", "10"))
    }
}

private class FakeStudyLogDao : StudyLogDao {
    val sessions = MutableStateFlow<List<StudySession>>(emptyList())
    private val sources = MutableStateFlow<List<StudySource>>(emptyList())
    private var nextSessionId = 1L
    private var nextSourceId = 1L
    var insertGate: CompletableDeferred<Unit>? = null
    var insertCalls = 0

    override fun observeFirstSessionDate(lang: String): Flow<Long?> = sessions.map { rows -> rows.filter { it.lang == lang }.minOfOrNull { it.date } }
    override fun observeSessions(lang: String, start: Long, end: Long): Flow<List<StudySession>> = sessions.map { rows -> rows.filter { it.lang == lang && it.date in start..end }.sortedByDescending { it.date } }
    override fun observeRecent(lang: String, offset: Int): Flow<List<StudySession>> = sessions.map { rows -> rows.filter { it.lang == lang }.sortedByDescending { it.date }.take(offset + 1) }
    override fun observeSources(lang: String): Flow<List<StudySource>> = sources.map { rows -> rows.filter { it.lang == lang }.sortedBy { it.name } }
    override suspend fun getSession(id: Long): StudySession? = sessions.value.firstOrNull { it.id == id }
    override suspend fun getSource(id: Long): StudySource? = sources.value.firstOrNull { it.id == id }
    override suspend fun insertSession(session: StudySession): Long {
        insertCalls++
        insertGate?.await()
        val id = nextSessionId++
        sessions.value = sessions.value + session.copy(id = id)
        return id
    }
    override suspend fun updateSession(session: StudySession) { sessions.value = sessions.value.map { if (it.id == session.id) session else it } }
    override suspend fun deleteSession(session: StudySession) { sessions.value = sessions.value.filterNot { it.id == session.id } }
    override suspend fun insertSource(source: StudySource): Long {
        val id = nextSourceId++
        sources.value = sources.value + source.copy(id = id)
        return id
    }
    override suspend fun updateSource(source: StudySource) { sources.value = sources.value.map { if (it.id == source.id) source else it } }
    override suspend fun updateSourceDetails(id: Long, name: String, kind: SourceKind, defaultSkill: StudySkill?): Int {
        val source = getSource(id) ?: return 0
        updateSource(source.copy(name = name, kind = kind, defaultSkill = defaultSkill))
        return 1
    }
    override suspend fun setSourceArchived(id: Long, archived: Boolean) { sources.value = sources.value.map { if (it.id == id) it.copy(archived = archived) else it } }
    override suspend fun deleteSource(source: StudySource) { sources.value = sources.value.filterNot { it.id == source.id } }
    override suspend fun deleteSourceIfUnused(sourceId: Long) {
        if (sessions.value.none { it.sourceId == sourceId }) sources.value = sources.value.filterNot { it.id == sourceId }
    }
    override suspend fun countSessionsForSource(sourceId: Long): Int = sessions.value.count { it.sourceId == sourceId }
    override fun observeAllSourceTotals(lang: String): Flow<List<SourceMinutes>> = sessions.map { rows ->
        rows.filter { it.lang == lang }.groupBy { it.sourceId }.map { (id, values) -> SourceMinutes(id, values.sumOf { it.durationMin }) }
    }
    override suspend fun sumMinutesForDateExcluding(lang: String, date: Long, excludeId: Long): Int =
        sessions.value.filter { it.lang == lang && it.date == date && it.id != excludeId }.sumOf { it.durationMin }
}
