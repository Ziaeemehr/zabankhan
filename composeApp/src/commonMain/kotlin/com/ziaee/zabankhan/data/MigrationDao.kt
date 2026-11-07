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

    @Query("SELECT * FROM library_tags WHERE lang = :lang AND name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findTag(lang: String, name: String): LibraryTag?
    @Query("SELECT * FROM exam_attempts WHERE lang = :lang AND examId = :exam AND questionId = :question AND createdAtMs = :created AND answer = :answer LIMIT 1")
    abstract suspend fun findAttempt(lang: String, exam: String, question: String, created: Long, answer: String): ExamAttempt?
    @Query("SELECT * FROM study_source WHERE lang = :lang AND name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findStudySource(lang: String, name: String): StudySource?
    @Query("SELECT * FROM study_session WHERE lang = :lang AND date = :date AND created_at = :created AND source_id IS :source AND duration_min = :minutes AND skill = :skill AND note IS :note LIMIT 1")
    abstract suspend fun findStudySession(lang: String, date: Long, created: Long, source: Long?, minutes: Int, skill: StudySkill, note: String?): StudySession?
    @Query("SELECT * FROM resources WHERE lang = :lang AND url = :url LIMIT 1")
    abstract suspend fun findResource(lang: String, url: String): ResourceLink?
    @Query("SELECT COUNT(*) FROM shadow_attempts WHERE lang = :lang AND textId IS :text AND timestampMs = :time AND chunkIndex = :chunk AND sentenceIndex = :sentence")
    abstract suspend fun hasShadow(lang: String, text: Long?, time: Long, chunk: Int, sentence: Int): Int
    @Insert abstract suspend fun insertTag(value: LibraryTag): Long
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) abstract suspend fun insertTagRef(value: TextTagCrossRef)
    @Insert abstract suspend fun insertAttempt(value: ExamAttempt): Long
    @Insert abstract suspend fun insertStudySource(value: StudySource): Long
    @Insert abstract suspend fun insertStudySession(value: StudySession): Long
    @Insert abstract suspend fun insertResource(value: ResourceLink): Long
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) abstract suspend fun insertHeadline(value: HeadlineEntity): Long
    @androidx.room.Upsert abstract suspend fun insertActivity(value: ActivityLogEntry)
    @Insert abstract suspend fun insertShadow(value: ShadowAttempt): Long

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
        var tagsAdded = 0
        val tags = snapshot.tags.associate { old -> old.id to (findTag(old.lang, old.name)?.id ?: insertTag(old.copy(id = 0)).also { tagsAdded++ }) }
        snapshot.textTags.forEach { old ->
            val text = texts[old.textId]; val tag = tags[old.tagId]
            if (text != null && tag != null) insertTagRef(TextTagCrossRef(text, tag))
        }
        var attemptsAdded = 0
        snapshot.examAttempts.forEach { old ->
            if (findAttempt(old.lang, old.examId, old.questionId, old.createdAtMs, old.answer) == null) { insertAttempt(old.copy(id = 0)); attemptsAdded++ }
        }
        val sources = snapshot.studySources.associate { old -> old.id to (findStudySource(old.lang, old.name)?.id ?: insertStudySource(old.copy(id = 0))) }
        var sessionsAdded = 0
        snapshot.studySessions.forEach { old ->
            val source = sources[old.sourceId]
            if (findStudySession(old.lang, old.date, old.createdAt, source, old.durationMin, old.skill, old.note) == null) { insertStudySession(old.copy(id = 0, sourceId = source)); sessionsAdded++ }
        }
        var resourcesAdded = 0
        snapshot.resources.forEach { old -> if (findResource(old.lang, old.url) == null) { insertResource(old.copy(id = 0)); resourcesAdded++ } }
        var headlinesAdded = 0
        snapshot.headlines.forEach { if (insertHeadline(it) != -1L) headlinesAdded++ }
        snapshot.activity.forEach { insertActivity(it) }
        snapshot.shadowAttempts.forEach { old ->
            val text = texts[old.textId]
            if (hasShadow(old.lang, text, old.timestampMs, old.chunkIndex, old.sentenceIndex) == 0) insertShadow(old.copy(id = 0, textId = text))
        }
        return MigrationSummary(textsAdded, wordsAdded, highlightsAdded, reviewsAdded, skipped, tags = tagsAdded, examAttempts = attemptsAdded, studySessions = sessionsAdded, resources = resourcesAdded, headlines = headlinesAdded)
    }
}
