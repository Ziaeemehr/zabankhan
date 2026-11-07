package com.ziaee.zabankhan.data

import kotlin.test.Test
import kotlin.test.assertEquals

class TextDocumentEditingTest {
    private val document = TextDocument(id = 7, lang = "en", title = "Story", body = "An English story.",
        lastOffset = 8, lastChunkIndex = 2, lastPositionMs = 900, createdAtMs = 1)

    @Test fun bodyEditRestartsReadingAndPreservesSourceMetadata() {
        val original = document.copy(author = "Author", license = "CC BY", publishedAt = 123)
        val edited = editedTextDocument(original, "New title", "New story.")
        assertEquals("New story.", edited.body)
        assertEquals(0, edited.lastOffset)
        assertEquals(0, edited.lastChunkIndex)
        assertEquals(0L, edited.lastPositionMs)
        assertEquals(original.author, edited.author)
        assertEquals(original.license, edited.license)
        assertEquals(original.publishedAt, edited.publishedAt)
    }

    @Test fun renamePreservesReadingPositionAndBlankInputCannotEraseText() {
        assertEquals(document.copy(title = "Renamed"), editedTextDocument(document, "Renamed", document.body))
        assertEquals(document, editedTextDocument(document, "  ", "\n"))
    }
}
