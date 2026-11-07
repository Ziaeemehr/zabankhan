package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.LibraryFolder
import com.ziaee.zabankhan.data.LibraryTag
import com.ziaee.zabankhan.data.TextDocument

enum class LibraryShelf { READING, FINISHED, ALL }

data class LibraryShelfCounts(val reading: Int = 0, val finished: Int = 0) {
    val all: Int get() = reading + finished
}

internal fun libraryShelfCounts(documents: List<TextDocument>, finishedIds: Set<Long>): LibraryShelfCounts {
    val finished = documents.count { it.id in finishedIds }
    return LibraryShelfCounts(documents.size - finished, finished)
}


sealed interface FolderFilter {
    data object All : FolderFilter
    data object Unfiled : FolderFilter
    data class Folder(val id: Long) : FolderFilter
}



data class LibraryUiState(
    val learningLanguageName: String = "",
    val shelf: LibraryShelf = LibraryShelf.READING,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.NEWEST,
    val documents: List<TextDocument> = emptyList(),
    val allDocuments: List<TextDocument> = documents,
    val folders: List<LibraryFolder> = emptyList(),
    val tags: List<LibraryTag> = emptyList(),
    val tagIdsByText: Map<Long, Set<Long>> = emptyMap(),
    val completionByTextId: Map<Long, Boolean> = emptyMap(),
    val folderFilter: FolderFilter = FolderFilter.All,
    val levelFilter: LevelFilter = LevelFilter.All,
    val selectedTagIds: Set<Long> = emptySet(),
    val selectedIds: Set<Long> = emptySet(),
    val totalDocumentCount: Int = documents.size,
    /** Text id -> context around the match, for texts whose body matches the search. */
    val bodySnippets: Map<Long, String> = emptyMap(),
    val shelfCounts: LibraryShelfCounts = libraryShelfCounts(allDocuments, allDocuments.filter {
        isTextFinished(it, completionByTextId[it.id] == true)
    }.mapTo(mutableSetOf()) { it.id })
) {
    /** Distinguishes "the library has zero saved texts" from "the current
     * search has zero matches" so the empty state can show the right copy. */
    val isSearching: Boolean get() = query.isNotBlank()
    val isFiltering: Boolean get() = levelFilter != LevelFilter.All || folderFilter != FolderFilter.All || selectedTagIds.isNotEmpty()
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
}

internal fun prunedSelection(selected: Set<Long>, existingIds: Set<Long>): Set<Long> = selected intersect existingIds

/** Pure local filter + sort over every saved document -- no network
 * involved, so Library stays fully usable offline. [id] is the final
 * tie-breaker for a stable, deterministic order within equal sort keys. */
internal fun projectLibraryTree(
    documents: List<TextDocument>,
    query: String,
    sort: LibrarySort,
    folderFilter: FolderFilter = FolderFilter.All,
    selectedTagIds: Set<Long> = emptySet(),
    tagIdsByText: Map<Long, Set<Long>> = emptyMap(),
    folders: List<LibraryFolder> = emptyList(),
    bodyMatchIds: Set<Long> = emptySet(),
    shelf: LibraryShelf = LibraryShelf.READING,
    finishedIds: Set<Long> = emptySet(),
    levelFilter: LevelFilter = LevelFilter.All,
    language: com.ziaee.zabankhan.lang.LanguageSpec
): List<TextDocument> {
    val normalizedQuery = com.ziaee.zabankhan.lang.normalizeForMatch(query.trim(), language)
    val includedFolderIds = (folderFilter as? FolderFilter.Folder)?.let {
        descendantIds(folders, it.id)
    }.orEmpty()
    val filtered = documents.filter { doc ->
        val matchesQuery = normalizedQuery.isBlank() ||
            com.ziaee.zabankhan.lang.normalizeForMatch(doc.title, language).contains(normalizedQuery) ||
            doc.sourceName?.let { com.ziaee.zabankhan.lang.normalizeForMatch(it, language).contains(normalizedQuery) } == true ||
            doc.id in bodyMatchIds
        val matchesFolder = when (folderFilter) {
            FolderFilter.All -> true
            FolderFilter.Unfiled -> doc.folderId == null
            is FolderFilter.Folder -> doc.folderId in includedFolderIds
        }
        val matchesTags = tagIdsByText[doc.id].orEmpty().containsAll(selectedTagIds)
        val matchesShelf = when (shelf) {
            LibraryShelf.READING -> doc.id !in finishedIds
            LibraryShelf.FINISHED -> doc.id in finishedIds
            LibraryShelf.ALL -> true
        }
        matchesQuery && matchesFolder && matchesTags && matchesShelf &&
            (levelFilter == LevelFilter.All || doc.level == (levelFilter as LevelFilter.Level).level)
    }
    val comparator: Comparator<TextDocument> = when (sort) {
        LibrarySort.NEWEST -> compareByDescending { it.createdAtMs }
        LibrarySort.TITLE -> compareBy { com.ziaee.zabankhan.lang.normalizeForMatch(it.title, language) }
        LibrarySort.LAST_READ -> compareByDescending { it.lastAccessedAtMs }
    }
    return filtered.sortedWith(comparator.thenBy { it.id })
}

/** Prefix MATCH terms built from Unicode letters/digits without Native-unsupported regex categories. */
internal fun textSearchMatch(query: String): String? = query.lowercase()
    .map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
    .split(' ').filter { it.isNotBlank() }.joinToString(" ") { "${it}*" }.ifBlank { null }
