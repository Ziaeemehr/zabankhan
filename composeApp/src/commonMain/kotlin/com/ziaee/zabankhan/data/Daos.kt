package com.ziaee.zabankhan.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TextDao {
    @Query("SELECT * FROM texts WHERE lang = :lang ORDER BY pinned DESC, createdAtMs DESC")
    fun observeAll(lang: String): Flow<List<TextDocument>>

    @Query("SELECT * FROM texts WHERE lang = :lang AND lastAccessedAtMs > 0 ORDER BY lastAccessedAtMs DESC LIMIT :limit")
    fun observeRecent(lang: String, limit: Int = 5): Flow<List<TextDocument>>

    // shortcut: LIKE search, no accent folding; upgrade to FTS4 unicode61 when library search matters.
    @Query("SELECT * FROM texts WHERE lang = :lang AND (title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%') ORDER BY createdAtMs DESC")
    fun observeSearch(lang: String, q: String): Flow<List<TextDocument>>

    @Query("SELECT * FROM texts WHERE id = :id")
    suspend fun getById(id: Long): TextDocument?

    @Query("SELECT * FROM texts WHERE id = :id")
    fun observeById(id: Long): Flow<TextDocument?>

    @Insert suspend fun insert(text: TextDocument): Long
    @Update suspend fun update(text: TextDocument)
    @Delete suspend fun delete(text: TextDocument)

    @Query("UPDATE texts SET lastOffset = :offset, lastAccessedAtMs = :now WHERE id = :id")
    suspend fun savePosition(id: Long, offset: Int, now: Long = nowMs())

    @Query("UPDATE texts SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    @Query("UPDATE texts SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("SELECT * FROM library_folders WHERE lang = :lang ORDER BY name COLLATE NOCASE")
    fun observeFolders(lang: String): Flow<List<LibraryFolder>>

    @Insert suspend fun insertFolder(folder: LibraryFolder): Long

    @Query("UPDATE library_folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("UPDATE texts SET folderId = NULL WHERE folderId = :id")
    suspend fun clearFolder(id: Long)

    @Query("DELETE FROM library_folders WHERE id = :id")
    suspend fun deleteFolderRow(id: Long)
}

@Dao
interface VocabDao {
    @Query("SELECT * FROM vocab WHERE lang = :lang ORDER BY createdAtMs DESC")
    fun observeAll(lang: String): Flow<List<VocabEntry>>

    @Query("SELECT * FROM vocab WHERE lang = :lang")
    suspend fun getAll(lang: String): List<VocabEntry>

    @Query("SELECT * FROM vocab WHERE id = :id")
    suspend fun getById(id: Long): VocabEntry?

    @Query("SELECT * FROM vocab WHERE lang = :lang AND lower(word) = lower(:word) LIMIT 1")
    suspend fun findByWord(lang: String, word: String): VocabEntry?

    @Query("SELECT COUNT(*) FROM vocab WHERE lang = :lang AND learned = 0 AND nextReviewAtMs <= :until")
    fun observeDueCount(lang: String, until: Long): Flow<Int>

    @Insert suspend fun insert(entry: VocabEntry): Long
    @Update suspend fun update(entry: VocabEntry)
    @Delete suspend fun delete(entry: VocabEntry)

    @Query("DELETE FROM vocab WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("UPDATE vocab SET listId = :listId WHERE id IN (:ids)")
    suspend fun moveToList(ids: List<Long>, listId: Long?)

    @Query("SELECT * FROM vocab_lists WHERE lang = :lang ORDER BY name COLLATE NOCASE")
    fun observeLists(lang: String): Flow<List<VocabList>>

    @Insert suspend fun insertList(list: VocabList): Long

    @Query("DELETE FROM vocab_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Insert suspend fun insertReviewLog(entry: ReviewLogEntry): Long

    @Query("SELECT * FROM review_log WHERE lang = :lang AND timestampMs >= :since ORDER BY timestampMs")
    suspend fun reviewLogSince(lang: String, since: Long): List<ReviewLogEntry>
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE textId = :textId ORDER BY startOffset")
    fun observeForText(textId: Long): Flow<List<HighlightEntry>>

    @Insert suspend fun insert(h: HighlightEntry): Long

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_log WHERE lang = :lang ORDER BY date")
    fun observe(lang: String): Flow<List<ActivityLogEntry>>

    @Query("SELECT * FROM activity_log WHERE date = :date AND lang = :lang")
    suspend fun get(date: String, lang: String): ActivityLogEntry?

    @Upsert suspend fun upsert(e: ActivityLogEntry)
}

@Dao
interface CacheDao {
    @Query("SELECT value FROM kv_cache WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: CacheEntry)
}
