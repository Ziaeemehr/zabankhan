package com.ziaee.zabankhan.data

import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
private fun newSyncId(): String = Uuid.random().toString().replace("-", "")

fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

/** A text in the library. [lang] is the learning-language code of its content ("fr", "en", "fa"). */
@Entity(tableName = "texts", indices = [Index("lang"), Index(value = ["externalKey"], unique = true)])
data class TextDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lang: String,
    val title: String,
    val body: String,
    val createdAtMs: Long = nowMs(),
    val lastAccessedAtMs: Long = 0,
    /** Character offset of the first visible paragraph, to resume reading. */
    val lastOffset: Int = 0,
    val folderId: Long? = null,
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val author: String? = null,
    val license: String? = null,
    val publishedAt: Long? = null,
    val imagePath: String? = null,
    val bodyPath: String? = null,
    @ColumnInfo(defaultValue = "0") val lastChunkIndex: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastPositionMs: Long = 0,
    @ColumnInfo(defaultValue = "''") val voice: String = "",
    @ColumnInfo(defaultValue = "0") val ratePercent: Int = 0,
    @ColumnInfo(defaultValue = "'fa'") val translationLang: String = "fa",
    val level: String? = null,
    /** Local file path for PDF documents (body is then extracted text, may be empty). */
    val pdfPath: String? = null,
    val externalKey: String? = null,
    val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val readMark: Int = 0,
)

@Entity(tableName = "library_folders", indices = [Index("lang")])
data class LibraryFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lang: String,
    val name: String,
    val parentId: Long? = null,
    val createdAtMs: Long = nowMs(),
)

/** A saved word or phrase with its sentence, meaning and Leitner state. */
@Entity(tableName = "vocab", indices = [Index("lang"), Index("nextReviewAtMs"), Index("syncId")])
data class VocabEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lang: String,
    val word: String,
    val sentence: String = "",
    val textId: Long? = null,
    val meaning: String? = null,
    val dictionaryUrl: String? = null,
    /** Language the meaning is written in ("fa", "en"). */
    val meaningLang: String = "fa",
    val listId: Long? = null,
    val learned: Boolean = false,
    val createdAtMs: Long = nowMs(),
    val leitnerBox: Int = 1,
    val nextReviewAtMs: Long = nowMs(),
    val lastReviewedAtMs: Long? = null,
    @ColumnInfo(defaultValue = "''") val syncId: String = newSyncId(),
    @ColumnInfo(defaultValue = "0") val syncRevision: Long = 0,
)

@Entity(tableName = "vocab_lists", indices = [Index("lang"), Index("syncId")])
data class VocabList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lang: String,
    val name: String,
    val createdAtMs: Long = nowMs(),
    @ColumnInfo(defaultValue = "''") val syncId: String = newSyncId(),
    @ColumnInfo(defaultValue = "0") val syncRevision: Long = 0,
)

/** One row per review answer; never updated. */
@Entity(tableName = "review_log", indices = [Index("timestampMs"), Index("syncId")])
data class ReviewLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val lang: String,
    val timestampMs: Long,
    val knew: Boolean,
    val boxBefore: Int,
    val boxAfter: Int,
    @ColumnInfo(defaultValue = "''") val syncId: String = newSyncId(),
    val scheduleSnapshot: String? = null,
)

/** Append-only logical changes created by this installation. Payload excludes local source IDs and files. */
@Entity(
    tableName = "sync_journal",
    indices = [Index(value = ["deviceId", "sequence"]), Index(value = ["entityType", "entitySyncId"])],
)
data class SyncJournalEntry(
    @PrimaryKey val operationId: String,
    val deviceId: String,
    val sequence: Long,
    val entityType: String,
    val entitySyncId: String,
    val baseRevision: Long?,
    val operationType: String,
    val payload: String?,
    val createdAtMs: Long = nowMs(),
)

/** Small durable key/value state for local counters and per-device remote cursors. */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntry(
    @PrimaryKey val key: String,
    val value: String,
)

/** Records operations already applied so downloads can be retried safely. */
@Entity(tableName = "sync_applied_operations", indices = [Index(value = ["deviceId", "sequence"])])
data class AppliedSyncOperation(
    @PrimaryKey val operationId: String,
    val deviceId: String,
    val sequence: Long,
)

/** Both sides are retained until the user explicitly resolves a concurrent edit. */
@Entity(tableName = "sync_conflicts", indices = [Index(value = ["entityType", "entitySyncId"])])
data class SyncConflict(
    @PrimaryKey val conflictId: String,
    val entityType: String,
    val entitySyncId: String,
    val localOperationId: String,
    val remoteOperationId: String,
    val localPayload: String,
    val remotePayload: String,
    @ColumnInfo(defaultValue = "0") val baseRevision: Long = 0,
    @ColumnInfo(defaultValue = "'upsert'") val remoteOperationType: String = "upsert",
    val createdAtMs: Long = nowMs(),
    val resolvedAtMs: Long? = null,
)

@Entity(tableName = "highlights", indices = [Index("textId")])
data class HighlightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textId: Long,
    val startOffset: Int,
    val endOffset: Int,
    val colorKey: String,
    val createdAtMs: Long = nowMs(),
)

/** Per-day measured activity; [date] is yyyy-MM-dd. */
@Entity(tableName = "activity_log", primaryKeys = ["date", "lang"])
data class ActivityLogEntry(
    val date: String,
    val lang: String,
    val readingMs: Long = 0,
    val listeningMs: Long = 0,
    val watchingMs: Long = 0,
)

/** Generic string cache (translations, lookups). */
@Entity(tableName = "kv_cache")
data class CacheEntry(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAtMs: Long = nowMs(),
)

@Entity(tableName = "pdf_highlights", indices = [Index("textId")])
data class PdfHighlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textId: Long,
    val page: Int,
    val startWord: Int,
    val endWord: Int,
    val colorKey: String,
    val createdAtMs: Long = nowMs(),
)

@Entity(tableName = "shadow_attempts", indices = [Index(value = ["lang", "textId"])])
data class ShadowAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lang: String,
    val textId: Long?,
    val sentence: String,
    @ColumnInfo(defaultValue = "0") val chunkIndex: Int = 0,
    @ColumnInfo(defaultValue = "0") val sentenceIndex: Int = 0,
    @ColumnInfo(defaultValue = "''") val engine: String = "",
    val matched: Int,
    val total: Int,
    val paceRatio: Float?,
    val timestampMs: Long = nowMs(),
)

@Entity(tableName = "pdf_ink",
    indices = [Index("textId"), Index(value = ["textId", "page"], unique = true)],
    foreignKeys = [ForeignKey(entity = TextDocument::class, parentColumns = ["id"],
        childColumns = ["textId"], onDelete = ForeignKey.CASCADE)])
data class PdfInk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textId: Long,
    val page: Int,
    val strokes: String,
)

@Entity(
    tableName = "pdf_text_notes",
    indices = [Index(value = ["textId", "page", "startWord", "endWord"], unique = true)],
    foreignKeys = [ForeignKey(entity = TextDocument::class, parentColumns = ["id"],
        childColumns = ["textId"], onDelete = ForeignKey.CASCADE)],
)
data class PdfTextNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textId: Long,
    val page: Int,
    val startWord: Int,
    val endWord: Int,
    val selectedText: String,
    val body: String,
)

@Entity(
    tableName = "library_tags",
    indices = [Index(value = ["lang", "name"], unique = true)]
)
data class LibraryTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long = nowMs(),
    val lang: String,
)

@Entity(
    tableName = "text_tags",
    primaryKeys = ["textId", "tagId"],
    indices = [Index("tagId")]
)
data class TextTagCrossRef(
    val textId: Long,
    val tagId: Long
)

@Entity(
    tableName = "headlines",
    primaryKeys = ["lang", "sourceId", "externalId"],
    indices = [Index("publishedAtMs"), Index("articleUrl")]
)
data class HeadlineEntity(
    val sourceId: String,
    val sourceLabel: String,
    val externalId: String,
    val title: String,
    val snippet: String,
    val articleUrl: String,
    val imageUrl: String?,
    val publishedAtMs: Long?,
    val cachedAtMs: Long,
    val lang: String,
)

/** A user-managed web bookmark shown on the Resources screen. */
@Entity(
    tableName = "resources",
    indices = [Index(value = ["lang", "url"], unique = true)]
)
data class ResourceLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val imageUrl: String? = null,
    val createdAtMs: Long = nowMs(),
    @ColumnInfo(defaultValue = "other") val category: String = "other",
    /** User-written; built-in resources leave these null and show their catalog text instead. */
    val description: String? = null,
    val level: String? = null,
    val lang: String,
)

@Entity(tableName = "exam_attempts", indices = [Index(value = ["examId", "questionId"])])
data class ExamAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: String,
    val questionId: String,
    val question: String,
    val answer: String,
    val score: Double?,
    val note: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val lang: String,
)

@androidx.room.Fts4(contentEntity = TextDocument::class, tokenizer = androidx.room.FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "texts_fts")
data class TextSearchEntry(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val title: String,
    val body: String
)

data class TextSearchHit(val id: Long, val snippet: String)
