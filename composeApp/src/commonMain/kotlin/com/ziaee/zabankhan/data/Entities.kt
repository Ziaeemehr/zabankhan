package com.ziaee.zabankhan.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Clock

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
@Entity(tableName = "vocab", indices = [Index("lang"), Index("nextReviewAtMs")])
data class VocabEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lang: String,
    val word: String,
    val sentence: String = "",
    val textId: Long? = null,
    val meaning: String? = null,
    /** Language the meaning is written in ("fa", "en"). */
    val meaningLang: String = "fa",
    val listId: Long? = null,
    val learned: Boolean = false,
    val createdAtMs: Long = nowMs(),
    val leitnerBox: Int = 1,
    val nextReviewAtMs: Long = nowMs(),
    val lastReviewedAtMs: Long? = null,
)

@Entity(tableName = "vocab_lists", indices = [Index("lang")])
data class VocabList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lang: String,
    val name: String,
    val createdAtMs: Long = nowMs(),
)

/** One row per review answer; never updated. */
@Entity(tableName = "review_log", indices = [Index("timestampMs")])
data class ReviewLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val lang: String,
    val timestampMs: Long,
    val knew: Boolean,
    val boxBefore: Int,
    val boxAfter: Int,
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
    val matched: Int,
    val total: Int,
    val paceRatio: Float?,
    val timestampMs: Long = nowMs(),
)
