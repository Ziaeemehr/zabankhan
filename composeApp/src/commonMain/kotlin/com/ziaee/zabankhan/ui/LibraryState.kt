package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.foldWithOffsets
import com.ziaee.zabankhan.lang.normalizeForMatch
import kotlin.math.ceil

// Ported from French Reader's ui/library/LibraryUiState.kt and LibraryReadingStatus.kt.

enum class LibrarySort { NEWEST, TITLE, LAST_READ }

sealed interface LevelFilter {
    data object All : LevelFilter
    data class Level(val level: String?) : LevelFilter
}

const val READ_MARK_AUTOMATIC = 0
const val READ_MARK_FINISHED = 1
const val READ_MARK_NOT_FINISHED = 2

enum class ReadingStatus { NOT_STARTED, IN_PROGRESS, READ }

internal fun toggleSelection(current: Set<Long>, id: Long): Set<Long> =
    current.toMutableSet().apply { if (!add(id)) remove(id) }

internal fun selectAllVisible(current: Set<Long>, visibleIds: Set<Long>): Set<Long> =
    if (visibleIds.isNotEmpty() && visibleIds.all(current::contains)) current - visibleIds else current + visibleIds

/** Local filter + sort. [folder] is -1 for all and 0 for unfiled; [id] breaks ties for a stable order. */
internal fun projectLibrary(
    documents: List<TextDocument>, query: String, sort: LibrarySort, folder: Long,
    levelFilter: LevelFilter, lang: LanguageSpec,
): List<TextDocument> {
    val normalizedQuery = normalizeForMatch(query.trim(), lang)
    val filtered = documents.filter { doc ->
        val matchesQuery = normalizedQuery.isBlank() ||
            normalizeForMatch(doc.title, lang).contains(normalizedQuery) ||
            doc.sourceName?.let { normalizeForMatch(it, lang).contains(normalizedQuery) } == true ||
            normalizeForMatch(doc.body, lang).contains(normalizedQuery)
        matchesQuery && (folder == -1L || (doc.folderId ?: 0L) == folder) &&
            (levelFilter == LevelFilter.All || doc.level == (levelFilter as LevelFilter.Level).level)
    }
    val comparator: Comparator<TextDocument> = when (sort) {
        LibrarySort.NEWEST -> compareByDescending { it.createdAtMs }
        LibrarySort.TITLE -> compareBy { normalizeForMatch(it.title, lang) }
        LibrarySort.LAST_READ -> compareByDescending { it.lastAccessedAtMs }
    }
    return filtered.sortedWith(comparator.thenBy { it.id })
}

/** About a line of body context around the first match, like French Reader's FTS snippet (12 tokens). */
internal fun bodySnippet(body: String, query: String, lang: LanguageSpec, radius: Int = 48): String? {
    val needle = normalizeForMatch(query.trim(), lang)
    if (needle.isEmpty()) return null
    val folded = foldWithOffsets(body, lang)
    val at = folded.text.indexOf(needle)
    if (at < 0) return null
    val matchStart = folded.originalOffsets[at]
    val matchEnd = folded.originalEndOffsets[at + needle.length - 1]
    var start = (matchStart - radius).coerceAtLeast(0)
    var end = (matchEnd + radius).coerceAtMost(body.length)
    while (start > 0 && !body[start - 1].isWhitespace() && matchStart - start < radius + 16) start--
    while (end < body.length && !body[end].isWhitespace() && end - matchEnd < radius + 16) end++
    val text = body.substring(start, end).replace(Regex("\\s+"), " ").trim()
    return (if (start > 0) "…" else "") + text + (if (end < body.length) "…" else "")
}

/** Character offsets are meaningful for extracted text, not PDF page positions. */
internal fun readingFraction(doc: TextDocument): Float =
    if (doc.pdfPath != null || doc.body.isEmpty()) 0f else (doc.lastOffset.toFloat() / doc.body.length).coerceIn(0f, 1f)

internal fun isTextNotStarted(document: TextDocument): Boolean = document.lastAccessedAtMs == 0L && document.lastOffset == 0

// shortcut: the resume offset is the first visible paragraph, so 95% counts as completed; store an explicit finish if this misjudges.
internal fun isTextCompleted(document: TextDocument): Boolean = readingFraction(document) >= .95f

internal fun isTextFinished(document: TextDocument, completedAutomatically: Boolean = isTextCompleted(document)): Boolean =
    when (document.readMark) {
        READ_MARK_FINISHED -> true
        READ_MARK_NOT_FINISHED -> false
        else -> completedAutomatically
    }

internal fun readingStatus(document: TextDocument): ReadingStatus = when {
    isTextFinished(document) -> ReadingStatus.READ
    isTextNotStarted(document) -> ReadingStatus.NOT_STARTED
    else -> ReadingStatus.IN_PROGRESS
}

data class HomeReadingMetrics(val progressFraction: Float, val progressPercent: Int, val estimatedTotalMinutes: Int, val estimatedRemainingMinutes: Int)

internal fun homeReadingMetrics(doc: TextDocument, wordsPerMinute: Int = 200): HomeReadingMetrics {
    val words = doc.body.split(Regex("\\s+")).count { it.isNotBlank() }
    val totalMinutes = ceil(words.toDouble() / wordsPerMinute).toInt()
    val fraction = readingFraction(doc)
    val remaining = if (fraction >= 1f || totalMinutes == 0) 0 else ceil(totalMinutes * (1 - fraction).toDouble()).toInt().coerceAtLeast(1)
    return HomeReadingMetrics(fraction, (fraction * 100).toInt(), totalMinutes, remaining)
}

internal fun readingStatus(document: TextDocument, completed: Boolean): ReadingStatus = when {
    isTextFinished(document, completed) -> ReadingStatus.READ
    isTextNotStarted(document) -> ReadingStatus.NOT_STARTED
    else -> ReadingStatus.IN_PROGRESS
}

internal fun readMarkToStore(chosen: Int, completedAutomatically: Boolean): Int =
    if ((chosen == READ_MARK_FINISHED) == completedAutomatically) READ_MARK_AUTOMATIC else chosen
