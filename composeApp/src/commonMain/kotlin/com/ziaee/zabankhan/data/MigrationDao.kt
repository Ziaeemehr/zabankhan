package com.ziaee.zabankhan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.ziaee.zabankhan.backup.FrenchReaderSnapshot
import com.ziaee.zabankhan.backup.MigrationSummary
import com.ziaee.zabankhan.lang.Languages

/** Inserts the complete legacy snapshot atomically, preserving relationships after deduplication. */
@Dao
abstract class MigrationDao {
    @Query("SELECT id FROM texts WHERE lang = :lang AND title = :title AND body = :body AND (pdfPath = :pdfPath OR (pdfPath IS NULL AND :pdfPath IS NULL)) LIMIT 1")
    abstract suspend fun findText(lang: String, title: String, body: String, pdfPath: String?): Long?
    @Query("SELECT id FROM vocab WHERE lang = :lang AND lower(word) = lower(:word) LIMIT 1")
    abstract suspend fun findWord(lang: String, word: String): Long?
    @Query("SELECT id FROM library_folders WHERE lang = :lang AND name = :name AND (parentId = :parent OR (parentId IS NULL AND :parent IS NULL)) LIMIT 1")
    abstract suspend fun findFolder(lang: String, name: String, parent: Long?): Long?
    @Query("SELECT id FROM vocab_lists WHERE lang = :lang AND name = :name LIMIT 1")
    abstract suspend fun findList(lang: String, name: String): Long?
    @Query("SELECT COUNT(*) FROM highlights WHERE textId = :text AND startOffset = :start AND endOffset = :end AND colorKey = :color")
    abstract suspend fun hasHighlight(text: Long, start: Int, end: Int, color: String): Int
    @Query("SELECT COUNT(*) FROM review_log WHERE entryId = :entry AND timestampMs = :time AND knew = :knew AND boxBefore = :before AND boxAfter = :after")
    abstract suspend fun hasReview(entry: Long, time: Long, knew: Boolean, before: Int, after: Int): Int
    @Insert abstract suspend fun insertText(value: TextDocument): Long
    @Insert abstract suspend fun insertWord(value: VocabEntry): Long
    @Insert abstract suspend fun insertFolder(value: LibraryFolder): Long
    @Insert abstract suspend fun insertList(value: VocabList): Long
    @Insert abstract suspend fun insertHighlight(value: HighlightEntry): Long
    @Insert abstract suspend fun insertReview(value: ReviewLogEntry): Long

    @Transaction
    open suspend fun importSnapshot(snapshot: FrenchReaderSnapshot): MigrationSummary {
        val lang = Languages.FRENCH.code
        val folders = mutableMapOf<Long, Long>()
        val pending = snapshot.folders.toMutableList()
        while (pending.isNotEmpty()) {
            val ready = pending.filter { it.parentId == null || it.parentId in folders || snapshot.folders.none { f -> f.id == it.parentId } }
            require(ready.isNotEmpty()) { "Cyclic library folders" }
            ready.forEach { old ->
                val parent = folders[old.parentId]
                folders[old.id] = findFolder(lang, old.name, parent) ?: insertFolder(old.copy(id = 0, parentId = parent))
                pending.remove(old)
            }
        }
        val lists = snapshot.lists.associate { old -> old.id to (findList(lang, old.name) ?: insertList(old.copy(id = 0))) }
        var textsAdded = 0
        var wordsAdded = 0
        var skipped = 0
        val texts = snapshot.texts.associate { old ->
            val existing = findText(lang, old.title, old.body, old.pdfPath)
            old.id to if (existing != null) { skipped++; existing } else {
                textsAdded++
                insertText(old.copy(id = 0, folderId = folders[old.folderId]))
            }
        }
        val words = snapshot.words.associate { old ->
            val existing = findWord(lang, old.word)
            old.id to if (existing != null) { skipped++; existing } else {
                wordsAdded++
                insertWord(old.copy(id = 0, textId = texts[old.textId], listId = lists[old.listId]))
            }
        }
        var highlightsAdded = 0
        snapshot.highlights.forEach { old -> texts[old.textId]?.let { id ->
            if (hasHighlight(id, old.startOffset, old.endOffset, old.colorKey) == 0) {
                insertHighlight(old.copy(id = 0, textId = id)); highlightsAdded++
            }
        } }
        var reviewsAdded = 0
        snapshot.reviews.forEach { old -> words[old.entryId]?.let { id ->
            if (hasReview(id, old.timestampMs, old.knew, old.boxBefore, old.boxAfter) == 0) {
                insertReview(old.copy(id = 0, entryId = id)); reviewsAdded++
            }
        } }
        return MigrationSummary(textsAdded, wordsAdded, highlightsAdded, reviewsAdded, skipped)
    }
}
