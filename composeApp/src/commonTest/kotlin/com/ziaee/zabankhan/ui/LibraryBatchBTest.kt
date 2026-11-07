package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.Languages
import kotlin.test.*

class LibraryBatchBTest {
    @Test fun englishLibraryIntersectsNestedFolderTagLevelAndShelfFilters() {
        val folders = listOf(LibraryFolder(1, "en", "Stories"), LibraryFolder(2, "en", "A2", parentId = 1))
        val docs = listOf(
            TextDocument(1, "en", "The garden", "", folderId = 2, level = "A2"),
            TextDocument(2, "en", "A garden", "", folderId = 1, level = "A2"),
            TextDocument(3, "en", "The garden", "", folderId = null, level = "A2"))
        assertEquals(listOf(1L), projectLibraryTree(docs, "garden", LibrarySort.TITLE,
            FolderFilter.Folder(1), setOf(10), mapOf(1L to setOf(10), 2L to setOf(10)), folders,
            shelf = LibraryShelf.FINISHED, finishedIds = setOf(1), levelFilter = LevelFilter.Level("A2"),
            language = Languages.ENGLISH).map { it.id })
    }

    @Test fun ftsQueryStripsOperatorsAndUsesPrefixTerms() {
        assertEquals("hello* world*", textSearchMatch("hello OR?".replace("OR", "world")))
        assertEquals("سلام* ۱۲۳*", textSearchMatch("سلام +۱۲۳"))
        assertNull(textSearchMatch("* + - \""))
    }

    @Test fun explicitReadingStatusReturnsToAutomaticWhenItMatchesProgress() {
        assertEquals(READ_MARK_AUTOMATIC, readMarkToStore(READ_MARK_FINISHED, true))
        assertEquals(READ_MARK_FINISHED, readMarkToStore(READ_MARK_FINISHED, false))
        assertEquals(READ_MARK_AUTOMATIC, readMarkToStore(READ_MARK_NOT_FINISHED, false))
        assertEquals(READ_MARK_NOT_FINISHED, readMarkToStore(READ_MARK_NOT_FINISHED, true))
    }
}

class LibraryCompletionBatchBTest {
    @Test fun reachingLastTextParagraphCompletesBeforeTheTrailingImage() {
        val body = "First paragraph\n\nLast paragraph\n\n![cover](epubimg:cover.jpg)"
        val document = TextDocument(1, "en", "Story", body, lastOffset = body.indexOf("Last paragraph"))
        assertTrue(libraryBodyCompleted(document, body))
        assertFalse(libraryBodyCompleted(document.copy(lastOffset = 0), body))
    }
}
