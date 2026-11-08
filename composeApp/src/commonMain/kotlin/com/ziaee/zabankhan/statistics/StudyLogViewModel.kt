package com.ziaee.zabankhan.statistics

import androidx.lifecycle.ViewModel
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.StudyLogDao
import com.ziaee.zabankhan.data.StudySession
import com.ziaee.zabankhan.data.StudySource
import com.ziaee.zabankhan.data.nowMs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

data class StudyLogUiState(
    val lang: String,
    val sessions: List<StudySession> = emptyList(),
    val allSessions: List<StudySession> = emptyList(),
    val sources: List<StudySource> = emptyList(),
    val sourceTotals: Map<Long?, Int> = emptyMap(),
)

sealed interface StudyLogSaveResult {
    data object Saved : StudyLogSaveResult
    data object InFlight : StudyLogSaveResult
    data class Invalid(val reason: StudyLogValidation) : StudyLogSaveResult
}

enum class StudyLogValidation {
    VALID,
    INVALID_DURATION,
    FUTURE_DATE,
    WRONG_LANGUAGE,
    MISSING_SESSION,
    MISSING_SOURCE,
    ARCHIVED_SOURCE,
    BLANK_SOURCE_NAME,
    DUPLICATE_SOURCE_NAME,
}

enum class StudyLogSourceDeleteResult { DELETED, ARCHIVED, MISSING }

fun parseStudyDuration(hours: String, minutes: String): Int? {
    val h = hours.toIntOrNull() ?: return null
    val m = minutes.toIntOrNull() ?: return null
    if (h < 0 || m !in 0..59) return null
    val total = h.toLong() * 60 + m
    return total.takeIf { it in 1..1440 }?.toInt()
}

fun studySourcePickerOptions(sources: List<StudySource>, selectedSourceId: Long?): List<StudySource> =
    sources.filter { !it.archived || it.id == selectedSourceId }

class StudyLogViewModel(
    private val dao: StudyLogDao,
    val lang: String,
) : ViewModel() {
    constructor(database: AppDatabase, lang: String) : this(database.studyLog(), lang)

    private val saveMutex = Mutex()
    private val sessions = dao.observeSessions(lang, Long.MIN_VALUE, Long.MAX_VALUE)
    private val recent = dao.observeRecent(lang)
    private val sources = dao.observeSources(lang)
    private val sourceTotals = dao.observeAllSourceTotals(lang)

    val uiState: Flow<StudyLogUiState> = combine(recent, sessions, sources, sourceTotals) { recentRows, allRows, sourceRows, totals ->
        StudyLogUiState(
            lang = lang,
            sessions = recentRows,
            allSessions = allRows,
            sources = sourceRows,
            sourceTotals = totals.associate { it.sourceId to it.minutes },
        )
    }

    suspend fun getSession(id: Long): StudySession? = dao.getSession(id)?.takeIf { it.lang == lang }

    suspend fun saveSession(session: StudySession, today: LocalDate = localToday()): StudyLogSaveResult {
        if (!saveMutex.tryLock()) return StudyLogSaveResult.InFlight
        try {
            val existing = if (session.id == 0L) null else dao.getSession(session.id)
            if (session.id != 0L && existing?.lang != lang) {
                return StudyLogSaveResult.Invalid(StudyLogValidation.MISSING_SESSION)
            }
            val validation = validateStudySession(session, lang, today)
            if (validation != StudyLogValidation.VALID) return StudyLogSaveResult.Invalid(validation)
            val source = session.sourceId?.let { dao.getSource(it) }
            if (session.sourceId != null && source?.lang != lang) {
                return StudyLogSaveResult.Invalid(StudyLogValidation.MISSING_SOURCE)
            }
            if (source?.archived == true && existing?.sourceId != source.id) {
                return StudyLogSaveResult.Invalid(StudyLogValidation.ARCHIVED_SOURCE)
            }
            val now = nowMs()
            if (existing == null) {
                dao.insertSession(session.copy(id = 0, lang = lang, createdAt = now, updatedAt = now))
            } else {
                dao.updateSession(session.copy(lang = lang, createdAt = existing.createdAt, updatedAt = now))
            }
            return StudyLogSaveResult.Saved
        } finally {
            saveMutex.unlock()
        }
    }

    suspend fun deleteSession(session: StudySession): Boolean {
        val existing = dao.getSession(session.id)?.takeIf { it.lang == lang } ?: return false
        dao.deleteSession(existing)
        return true
    }

    suspend fun restoreSession(session: StudySession): Boolean {
        if (session.lang != lang) return false
        dao.insertSession(session.copy(id = 0, createdAt = nowMs(), updatedAt = nowMs()))
        return true
    }

    suspend fun saveSource(source: StudySource): StudyLogValidation {
        if (source.lang != lang) return StudyLogValidation.WRONG_LANGUAGE
        val name = source.name.trim()
        if (name.isEmpty()) return StudyLogValidation.BLANK_SOURCE_NAME
        val rows = sources.first()
        if (rows.any { it.id != source.id && it.name.equals(name, ignoreCase = true) }) {
            return StudyLogValidation.DUPLICATE_SOURCE_NAME
        }
        val existing = if (source.id == 0L) null else dao.getSource(source.id)
        if (source.id != 0L && existing?.lang != lang) return StudyLogValidation.MISSING_SOURCE
        val value = source.copy(
            name = name,
            lang = lang,
            createdAt = existing?.createdAt ?: source.createdAt,
        )
        if (existing == null) dao.insertSource(value.copy(id = 0)) else dao.updateSource(value)
        return StudyLogValidation.VALID
    }

    suspend fun archiveSource(id: Long, archived: Boolean = true): Boolean {
        val source = dao.getSource(id)?.takeIf { it.lang == lang } ?: return false
        dao.setSourceArchived(source.id, archived)
        return true
    }

    suspend fun deleteUnusedSource(id: Long): StudyLogSourceDeleteResult {
        val source = dao.getSource(id)?.takeIf { it.lang == lang } ?: return StudyLogSourceDeleteResult.MISSING
        if (dao.countSessionsForSource(source.id) > 0) {
            dao.setSourceArchived(source.id, true)
            return StudyLogSourceDeleteResult.ARCHIVED
        }
        dao.deleteSourceIfUnused(source.id)
        return StudyLogSourceDeleteResult.DELETED
    }
}

fun validateStudySession(session: StudySession, lang: String, today: LocalDate): StudyLogValidation = when {
    session.lang != lang -> StudyLogValidation.WRONG_LANGUAGE
    session.durationMin !in 1..1440 -> StudyLogValidation.INVALID_DURATION
    session.date > today.toEpochDays().toLong() -> StudyLogValidation.FUTURE_DATE
    else -> StudyLogValidation.VALID
}
