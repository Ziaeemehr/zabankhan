package com.ziaee.zabankhan.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Dao
interface TextDao {
    @Query("SELECT * FROM texts")
    fun observeProgressTexts(): Flow<List<TextDocument>>

    @Query("UPDATE texts SET level = :level WHERE id IN (:ids)")
    suspend fun setLevels(ids: List<Long>, level: String?)

    @Query("UPDATE texts SET readMark = :mark WHERE id IN (:ids)")
    suspend fun setReadMarks(ids: List<Long>, mark: Int)

    @Query("SELECT texts.id, snippet(texts_fts, '', '', '…', 1, 12) AS snippet FROM texts_fts JOIN texts ON texts.id = texts_fts.rowid WHERE texts.lang = :lang AND texts_fts MATCH :query LIMIT :limit")
    suspend fun searchFullText(lang: String, query: String, limit: Int = 100): List<TextSearchHit>

    @Query("SELECT * FROM texts WHERE externalKey = :key LIMIT 1")
    suspend fun getByExternalKey(key: String): TextDocument?
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
    @Query("SELECT * FROM review_log")
    fun observeProgressReviews(): Flow<List<ReviewLogEntry>>

    @Query("SELECT * FROM vocab")
    fun observeProgressWords(): Flow<List<VocabEntry>>

    @Query("SELECT * FROM vocab WHERE lang = :lang ORDER BY createdAtMs DESC")
    fun observeAll(lang: String): Flow<List<VocabEntry>>

    @Query("SELECT * FROM vocab WHERE lang = :lang")
    suspend fun getAll(lang: String): List<VocabEntry>

    @Query("SELECT * FROM vocab")
    suspend fun allVocab(): List<VocabEntry>

    @Query("SELECT * FROM vocab WHERE id = :id")
    suspend fun getById(id: Long): VocabEntry?

    @Query("SELECT * FROM vocab WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): VocabEntry?

    @Query("SELECT * FROM vocab WHERE lang = :lang AND lower(word) = lower(:word) LIMIT 1")
    suspend fun findByWord(lang: String, word: String): VocabEntry?

    @Query("SELECT COUNT(*) FROM vocab WHERE lang = :lang AND learned = 0 AND nextReviewAtMs <= :until")
    fun observeDueCount(lang: String, until: Long): Flow<Int>

    @Insert suspend fun insertRaw(entry: VocabEntry): Long
    @Update suspend fun updateRaw(entry: VocabEntry)
    @Delete suspend fun deleteRaw(entry: VocabEntry)

    @Query("SELECT * FROM vocab WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<VocabEntry>

    @Query("SELECT * FROM vocab WHERE listId = :listId")
    suspend fun getByListId(listId: Long): List<VocabEntry>

    @Insert suspend fun insertListRaw(list: VocabList): Long
    @Update suspend fun updateListRaw(list: VocabList)
    @Delete suspend fun deleteListRaw(list: VocabList)

    @Query("SELECT * FROM vocab_lists WHERE id = :id")
    suspend fun getListById(id: Long): VocabList?

    @Query("SELECT * FROM vocab_lists WHERE syncId = :syncId LIMIT 1")
    suspend fun getListBySyncId(syncId: String): VocabList?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSyncMetadata(entry: SyncMetadataEntry)

    @Query("SELECT value FROM sync_metadata WHERE `key` = :key")
    suspend fun getSyncMetadata(key: String): String?

    @Query("DELETE FROM sync_metadata WHERE `key` = :key")
    suspend fun clearSyncMetadata(key: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun appendSyncJournal(entry: SyncJournalEntry): Long

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun appendLocalOperation(type: String, syncId: String, baseRevision: Long?, operation: String, payload: String?) {
        if (getSyncMetadata("suppress_journal") == "1") return
        val seq = (getSyncMetadata("journal_sequence")?.toLongOrNull() ?: 0L) + 1L
        setSyncMetadata(SyncMetadataEntry("journal_sequence", seq.toString()))
        appendSyncJournal(SyncJournalEntry(
            operationId = Uuid.random().toString().replace("-", ""),
            deviceId = getSyncMetadata("device_id") ?: "local",
            sequence = seq,
            entityType = type,
            entitySyncId = syncId,
            baseRevision = baseRevision,
            operationType = operation,
            payload = payload,
        ))
    }

    private suspend fun cardPayload(entry: VocabEntry): String {
        val listSyncId = entry.listId?.let { getListById(it)?.syncId }
        return Json.encodeToString(buildJsonObject {
        put("syncId", entry.syncId); put("revision", entry.syncRevision); put("lang", entry.lang)
        put("word", entry.word); put("sentence", entry.sentence); entry.meaning?.let { put("meaning", it) }
        entry.dictionaryUrl?.let { put("dictionaryUrl", it) }; put("meaningLang", entry.meaningLang)
        put("listSyncId", listSyncId)
        put("learned", entry.learned); put("createdAtMs", entry.createdAtMs)
        put("leitnerBox", entry.leitnerBox); put("nextReviewAtMs", entry.nextReviewAtMs)
        entry.lastReviewedAtMs?.let { put("lastReviewedAtMs", it) }
        })
    }

    private fun listPayload(list: VocabList, revision: Long = list.syncRevision): String = Json.encodeToString(buildJsonObject {
        put("syncId", list.syncId); put("revision", revision); put("lang", list.lang)
        put("name", list.name); put("createdAtMs", list.createdAtMs)
    })

    @Transaction
    suspend fun insert(entry: VocabEntry): Long {
        val id = insertRaw(entry)
        val saved = getById(id) ?: entry.copy(id = id)
        appendLocalOperation("card", saved.syncId, null, "upsert", cardPayload(saved))
        return id
    }

    @Transaction
    suspend fun update(entry: VocabEntry) {
        val old = getById(entry.id)
        val updated = if (old == null) entry else entry.copy(syncId = old.syncId, syncRevision = old.syncRevision + 1)
        updateRaw(updated)
        appendLocalOperation("card", updated.syncId, old?.syncRevision, "upsert", cardPayload(updated))
    }

    @Transaction
    suspend fun delete(entry: VocabEntry) {
        val old = getById(entry.id) ?: entry
        deleteRaw(old)
        appendLocalOperation("card", old.syncId, old.syncRevision, "delete", null)
    }

    @Transaction
    suspend fun deleteIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        val rows = getByIds(ids)
        deleteIdsRaw(ids)
        rows.forEach { appendLocalOperation("card", it.syncId, it.syncRevision, "delete", null) }
    }

    @Transaction
    suspend fun resolveCard(syncId: String, desired: VocabEntry?, baseRevision: Long) {
        val existing = getBySyncId(syncId)
        if (desired == null) {
            if (existing != null) deleteRaw(existing)
            appendLocalOperation("card", syncId, baseRevision, "resolve_delete", null)
        } else {
            val resolved = desired.copy(
                id = existing?.id ?: 0L,
                syncId = syncId,
                syncRevision = baseRevision + 1L,
                textId = existing?.textId,
            )
            if (existing == null) insertRaw(resolved) else updateRaw(resolved)
            appendLocalOperation("card", syncId, baseRevision, "resolve_upsert", cardPayload(resolved))
        }
    }

    @Transaction
    suspend fun resolveList(syncId: String, desired: VocabList?, baseRevision: Long) {
        val existing = getListBySyncId(syncId)
        if (desired == null) {
            if (existing != null) deleteListRaw(existing)
            appendLocalOperation("list", syncId, baseRevision, "resolve_delete", null)
        } else {
            val resolved = desired.copy(id = existing?.id ?: 0L, syncId = syncId, syncRevision = baseRevision + 1L)
            if (existing == null) insertListRaw(resolved) else updateListRaw(resolved)
            appendLocalOperation("list", syncId, baseRevision, "resolve_upsert", listPayload(resolved))
        }
    }

    @Query("DELETE FROM vocab WHERE id IN (:ids)")
    suspend fun deleteIdsRaw(ids: List<Long>)

    @Transaction
    suspend fun moveToList(ids: List<Long>, listId: Long?) {
        if (ids.isEmpty()) return
        val rows = getByIds(ids)
        rows.forEach { row -> update(row.copy(listId = listId, syncRevision = row.syncRevision + 1)) }
    }

    @Query("SELECT * FROM vocab_lists WHERE lang = :lang ORDER BY name COLLATE NOCASE")
    fun observeLists(lang: String): Flow<List<VocabList>>

    @Query("SELECT * FROM vocab_lists")
    suspend fun allLists(): List<VocabList>

    @Transaction
    suspend fun insertList(list: VocabList): Long {
        val id = insertListRaw(list)
        val saved = getListById(id) ?: list.copy(id = id)
        appendLocalOperation("list", saved.syncId, null, "upsert", listPayload(saved))
        return id
    }

    @Transaction
    suspend fun renameList(id: Long, name: String) {
        val old = getListById(id) ?: return
        val updated = old.copy(name = name, syncRevision = old.syncRevision + 1)
        updateListRaw(updated)
        appendLocalOperation("list", updated.syncId, old.syncRevision, "upsert", listPayload(updated))
    }

    @Transaction
    suspend fun deleteCardsInList(listId: Long) {
        val rows = getByListId(listId)
        deleteCardsInListRaw(listId)
        rows.forEach { appendLocalOperation("card", it.syncId, it.syncRevision, "delete", null) }
    }

    @Query("DELETE FROM vocab WHERE listId = :listId")
    suspend fun deleteCardsInListRaw(listId: Long)

    @Transaction
    suspend fun clearList(listId: Long) {
        val rows = getByListId(listId)
        rows.forEach { update(it.copy(listId = null, syncRevision = it.syncRevision + 1)) }
    }

    @Transaction
    suspend fun deleteListAndCards(listId: Long, deleteCards: Boolean) {
        if (deleteCards) deleteCardsInList(listId) else clearList(listId)
        deleteList(listId)
    }

    @Transaction
    suspend fun deleteList(id: Long) {
        val list = getListById(id) ?: return
        deleteListRaw(list)
        appendLocalOperation("list", list.syncId, list.syncRevision, "delete", null)
    }

    @Transaction
    suspend fun recordReview(entry: VocabEntry, log: ReviewLogEntry): Long {
        val old = getById(entry.id)
        val updated = if (old == null) entry else entry.copy(syncId = old.syncId, syncRevision = old.syncRevision + 1)
        updateRaw(updated)
        val logId = insertReviewLogRaw(log.copy(scheduleSnapshot = cardPayload(updated)))
        appendLocalOperation("card", updated.syncId, old?.syncRevision, "review_state", cardPayload(updated))
        val review = getReviewById(logId) ?: log.copy(id = logId)
        val reviewPayload = reviewPayload(review, updated)
        appendLocalOperation("review", review.syncId, null, "upsert", reviewPayload)
        return logId
    }

    private fun reviewPayload(review: ReviewLogEntry, card: VocabEntry) = Json.encodeToString(buildJsonObject {
            put("syncId", review.syncId); put("cardSyncId", card.syncId); put("lang", review.lang)
            put("timestampMs", review.timestampMs); put("knew", review.knew)
            put("boxBefore", review.boxBefore); put("boxAfter", review.boxAfter)
            review.scheduleSnapshot?.let { put("scheduleSnapshot", it) }
            put("leitnerBox", card.leitnerBox); put("nextReviewAtMs", card.nextReviewAtMs)
            card.lastReviewedAtMs?.let { put("lastReviewedAtMs", it) }; put("learned", card.learned)
        })

    @Transaction
    suspend fun undoReview(entry: VocabEntry, logId: Long) {
        val old = getById(entry.id)
        val updated = if (old == null) entry else entry.copy(syncId = old.syncId, syncRevision = old.syncRevision + 1)
        updateRaw(updated)
        val removedLog = getReviewById(logId)
        deleteReviewLogRaw(logId)
        appendLocalOperation("card", updated.syncId, old?.syncRevision, "review_state", cardPayload(updated))
        removedLog?.let { appendLocalOperation("review", it.syncId, null, "delete", cardPayload(updated)) }
    }

    @Query("SELECT * FROM review_log WHERE id = :id")
    suspend fun getReviewById(id: Long): ReviewLogEntry?

    @Query("SELECT * FROM review_log WHERE syncId = :syncId LIMIT 1")
    suspend fun getReviewBySyncId(syncId: String): ReviewLogEntry?

    @Query("SELECT * FROM review_log")
    suspend fun allReviews(): List<ReviewLogEntry>

    @Query("DELETE FROM review_log WHERE id = :id")
    suspend fun deleteReviewLogRaw(id: Long)

    @Insert suspend fun insertReviewLogRaw(entry: ReviewLogEntry): Long

    @Update suspend fun updateReviewLogRaw(entry: ReviewLogEntry)

    @Transaction
    suspend fun seedInitialSyncJournal() {
        if (getSyncMetadata("initial_sync_seeded") == "1") return
        allLists().forEach { appendLocalOperation("list", it.syncId, null, "upsert", listPayload(it)) }
        allVocab().forEach { appendLocalOperation("card", it.syncId, null, "upsert", cardPayload(it)) }
        allReviews().forEach { review ->
            val card = getById(review.entryId) ?: return@forEach
            val payload = reviewPayload(review, card)
            appendLocalOperation("review", review.syncId, null, "upsert", payload)
        }
        setSyncMetadata(SyncMetadataEntry("initial_sync_seeded", "1"))
    }

    @Transaction
    suspend fun reemitReviewsForCard(card: VocabEntry): Set<String> {
        val reviews = allReviews().filter { it.entryId == card.id }
        reviews.forEach { review -> appendLocalOperation("review", review.syncId, null, "upsert", reviewPayload(review, card)) }
        return reviews.mapTo(mutableSetOf()) { it.syncId }
    }

    @Transaction
    suspend fun reemitReview(review: ReviewLogEntry, card: VocabEntry) {
        appendLocalOperation("review", review.syncId, null, "upsert", reviewPayload(review, card))
    }

    @Query("SELECT * FROM review_log WHERE lang = :lang AND timestampMs >= :since ORDER BY timestampMs")
    suspend fun reviewLogSince(lang: String, since: Long): List<ReviewLogEntry>
}

@Dao
interface SyncDao {
    @Query("SELECT COUNT(*) FROM sync_journal")
    fun observeJournalCount(): Flow<Int>

    @Query("SELECT value FROM sync_metadata WHERE `key` = :key")
    suspend fun getMetadata(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMetadata(entry: SyncMetadataEntry)

    @Query("DELETE FROM sync_metadata WHERE `key` = :key")
    suspend fun clearMetadata(key: String)

    @Query("UPDATE sync_journal SET deviceId = :deviceId WHERE deviceId = 'local'")
    suspend fun assignUnattributedOperations(deviceId: String)

    @Query("SELECT * FROM sync_journal WHERE deviceId = :deviceId ORDER BY sequence")
    suspend fun operationsForDevice(deviceId: String): List<SyncJournalEntry>

    @Query("SELECT * FROM sync_journal ORDER BY deviceId, sequence")
    suspend fun allOperations(): List<SyncJournalEntry>

    @Query("SELECT MAX(sequence) FROM sync_journal WHERE deviceId = :deviceId")
    suspend fun latestSequence(deviceId: String): Long?

    @Query("SELECT value FROM sync_metadata WHERE `key` = :key")
    fun observeMetadata(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun append(entry: SyncJournalEntry): Long

    @Query("SELECT * FROM sync_journal ORDER BY deviceId, sequence")
    suspend fun allJournalEntries(): List<SyncJournalEntry>

    @Query("SELECT * FROM sync_journal WHERE deviceId = :deviceId ORDER BY sequence")
    suspend fun journalForDevice(deviceId: String): List<SyncJournalEntry>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markApplied(operation: AppliedSyncOperation): Long

    @Query("SELECT EXISTS(SELECT 1 FROM sync_applied_operations WHERE operationId = :operationId)")
    suspend fun wasApplied(operationId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConflict(conflict: SyncConflict)

    @Query("SELECT * FROM sync_conflicts WHERE resolvedAtMs IS NULL ORDER BY createdAtMs")
    fun observeUnresolvedConflicts(): Flow<List<SyncConflict>>

    @Query("SELECT * FROM sync_conflicts WHERE resolvedAtMs IS NULL ORDER BY createdAtMs")
    suspend fun unresolvedConflicts(): List<SyncConflict>

    @Query("SELECT * FROM sync_conflicts WHERE conflictId = :conflictId")
    suspend fun getConflict(conflictId: String): SyncConflict?

    @Query("UPDATE sync_conflicts SET resolvedAtMs = :resolvedAtMs WHERE conflictId = :conflictId")
    suspend fun markConflictResolved(conflictId: String, resolvedAtMs: Long)
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
    @Query("SELECT * FROM activity_log")
    fun observeProgressActivity(): Flow<List<ActivityLogEntry>>

    @Query("SELECT * FROM activity_log WHERE lang = :lang ORDER BY date")
    fun observe(lang: String): Flow<List<ActivityLogEntry>>

    @Query("SELECT * FROM activity_log WHERE date = :date AND lang = :lang")
    suspend fun get(date: String, lang: String): ActivityLogEntry?

    @Upsert suspend fun upsert(e: ActivityLogEntry)
}

@Dao
interface CacheDao {
    // Translation keys are 16-character hexadecimal hashes; retain grammar and other named preferences.
    @Query("DELETE FROM kv_cache WHERE (length(`key`) = 16 AND `key` NOT GLOB '*[^0-9a-f]*') OR (`key` LIKE 'regenerated-%' AND length(`key`) = 28)")
    suspend fun clearTranslations()

    @Query("SELECT value FROM kv_cache WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: CacheEntry)
}

@Dao
interface PdfHighlightDao {
    @Query("SELECT * FROM pdf_highlights WHERE textId = :textId ORDER BY page, startWord")
    fun observeForText(textId: Long): Flow<List<PdfHighlight>>
    @Insert suspend fun insert(highlight: PdfHighlight): Long
    @Query("DELETE FROM pdf_highlights WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ShadowAttemptDao {
    @Query("SELECT * FROM shadow_attempts")
    fun observeProgressShadow(): Flow<List<ShadowAttempt>>

    @Query("SELECT * FROM shadow_attempts WHERE lang = :lang ORDER BY timestampMs")
    fun observe(lang: String): Flow<List<ShadowAttempt>>
    @Insert suspend fun insert(attempt: ShadowAttempt): Long
    @Query("SELECT * FROM shadow_attempts WHERE lang = :lang AND textId = :textId ORDER BY timestampMs, id")
    suspend fun forText(lang: String, textId: Long): List<ShadowAttempt>
}

@Dao
interface PdfInkDao {
    @Query("SELECT * FROM pdf_ink WHERE textId = :textId ORDER BY page")
    fun observeForText(textId: Long): Flow<List<PdfInk>>
    // REPLACE resolves the unique page key even when the caller has no row id yet.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ink: PdfInk)
    @Query("DELETE FROM pdf_ink WHERE textId = :textId AND page = :page")
    suspend fun delete(textId: Long, page: Int)
}

@Dao
interface PdfTextNoteDao {
    @Query("SELECT * FROM pdf_text_notes WHERE textId = :textId ORDER BY page, startWord, endWord, id")
    fun observeForText(textId: Long): Flow<List<PdfTextNote>>

    @Query("SELECT * FROM pdf_text_notes WHERE textId = :textId AND page = :page AND startWord = :startWord AND endWord = :endWord LIMIT 1")
    suspend fun find(textId: Long, page: Int, startWord: Int, endWord: Int): PdfTextNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: PdfTextNote)

    @Query("DELETE FROM pdf_text_notes WHERE textId = :textId AND page = :page AND startWord = :startWord AND endWord = :endWord")
    suspend fun delete(textId: Long, page: Int, startWord: Int, endWord: Int)
}
