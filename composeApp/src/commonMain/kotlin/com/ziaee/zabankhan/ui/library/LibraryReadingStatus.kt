package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.pdf.PdfReadingProgress
import com.ziaee.zabankhan.pdf.isScannedPdfPlaceholder
import com.ziaee.zabankhan.ui.reader.paragraphs
import com.ziaee.zabankhan.ui.reader.BlockType

/** KMP reader stores a UTF-16 paragraph offset, rather than French Reader's chunk index. */
internal fun libraryBodyCompleted(document: TextDocument, body: String): Boolean {
    val last = paragraphs(body).lastOrNull { it.block.type != BlockType.IMAGE } ?: return false
    return document.lastOffset >= last.offset
}

internal class LibraryCompletionCache {
    private data class Key(
        val id: Long,
        val body: String,
        val bodyPath: String?,
        val lastOffset: Int
    )

    private data class Entry(val key: Key, val completed: Boolean)

    private val entries = mutableMapOf<Long, Entry>()

    suspend fun completionFor(
        documents: List<TextDocument>,
        readBody: suspend (TextDocument) -> String,
        pdfProgress: (Long) -> PdfReadingProgress? = { null }
    ): Map<Long, Boolean> {
        val liveIds = documents.mapTo(mutableSetOf()) { it.id }
        entries.keys.retainAll(liveIds)
        return documents.associate { document ->
            if (document.pdfPath != null && pdfProgress(document.id)?.finished == true) {
                document.id to true
            } else if (isTextNotStarted(document)) {
                entries.remove(document.id)
                document.id to false
            } else {
                val key = Key(
                    id = document.id,
                    body = document.body,
                    bodyPath = document.bodyPath,
                    lastOffset = document.lastOffset
                )
                val cached = entries[document.id]?.takeIf { it.key == key }
                // A PDF-backed text finishes from either view, but a scanned PDF's one-line
                // placeholder body says nothing about reading progress.
                val completed = cached?.completed ?: readBody(document).let { body ->
                    !(document.pdfPath != null && isScannedPdfPlaceholder(body)) && libraryBodyCompleted(document, body)
                }.also {
                    entries[document.id] = Entry(key, it)
                }
                document.id to completed
            }
        }
    }
}

