package com.ziaee.zabankhan.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SourceMinutes(
    @ColumnInfo(name = "source_id") val sourceId: Long?,
    val minutes: Int
)

@Dao
interface StudyLogDao {
    @Query("SELECT MIN(date) FROM study_session WHERE lang = :lang")
    fun observeFirstSessionDate(lang: String): Flow<Long?>

    @Query("SELECT * FROM study_session WHERE lang = :lang AND date BETWEEN :start AND :end ORDER BY date DESC, created_at DESC")
    fun observeSessions(lang: String, start: Long, end: Long): Flow<List<StudySession>>

    @Query(
        "SELECT * FROM study_session WHERE lang = :lang AND date >= COALESCE((" +
            "SELECT date FROM study_session WHERE lang = :lang ORDER BY date DESC, created_at DESC LIMIT 1 OFFSET :offset" +
            "), (SELECT MIN(date) FROM study_session WHERE lang = :lang)) ORDER BY date DESC, created_at DESC"
    )
    fun observeRecent(lang: String, offset: Int = 99): Flow<List<StudySession>>

    @Query("SELECT * FROM study_source WHERE lang = :lang ORDER BY archived, name COLLATE NOCASE")
    fun observeSources(lang: String): Flow<List<StudySource>>

    @Query("SELECT * FROM study_session WHERE id = :id")
    suspend fun getSession(id: Long): StudySession?

    @Query("SELECT * FROM study_source WHERE id = :id")
    suspend fun getSource(id: Long): StudySource?

    @Insert
    suspend fun insertSession(session: StudySession): Long

    @Update
    suspend fun updateSession(session: StudySession)

    @Delete
    suspend fun deleteSession(session: StudySession)

    @Insert
    suspend fun insertSource(source: StudySource): Long

    @Update
    suspend fun updateSource(source: StudySource)

    @Query("UPDATE study_source SET name = :name, kind = :kind, default_skill = :defaultSkill WHERE id = :id")
    suspend fun updateSourceDetails(id: Long, name: String, kind: SourceKind, defaultSkill: StudySkill?): Int

    @Query("UPDATE study_source SET archived = :archived WHERE id = :id")
    suspend fun setSourceArchived(id: Long, archived: Boolean)

    @Delete
    suspend fun deleteSource(source: StudySource)

    @Query("DELETE FROM study_source WHERE id = :sourceId AND NOT EXISTS (SELECT 1 FROM study_session WHERE source_id = :sourceId)")
    suspend fun deleteSourceIfUnused(sourceId: Long)

    @Query("SELECT COUNT(*) FROM study_session WHERE source_id = :sourceId")
    suspend fun countSessionsForSource(sourceId: Long): Int

    @Query("SELECT source_id, COALESCE(SUM(duration_min), 0) AS minutes FROM study_session WHERE lang = :lang GROUP BY source_id")
    fun observeAllSourceTotals(lang: String): Flow<List<SourceMinutes>>

    @Query("SELECT COALESCE(SUM(duration_min), 0) FROM study_session WHERE lang = :lang AND date = :date AND id != :excludeId")
    suspend fun sumMinutesForDateExcluding(lang: String, date: Long, excludeId: Long = -1): Int
}
