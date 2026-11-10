package com.ziaee.zabankhan.sync.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

object LibraryKinds {
    const val FOLDER = "folder"
    const val TEXT = "text"
    const val POSITION = "pos"
    const val TAG = "tag"
    const val TEXT_TAG = "textTag"
    const val INK = "ink"
    const val NOTE = "note"
    const val PDF_HIGHLIGHT = "pdfHighlight"
    const val HIGHLIGHT = "highlight"
    val all = setOf(FOLDER, TEXT, POSITION, TAG, TEXT_TAG, INK, NOTE, PDF_HIGHLIGHT, HIGHLIGHT)
}

const val LIBRARY_FILE_PREFIX = "zabankhan-library-"
const val LIBRARY_SCHEMA_VERSION = 1
const val MAX_LIBRARY_FILE_BYTES = 64 * 1024 * 1024
const val TOMBSTONE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

@Serializable
data class LibraryItem(
    val kind: String,
    val key: String,
    val updatedAtMs: Long,
    val deleted: Boolean = false,
    val payload: JsonObject? = null,
    val blobs: List<String> = emptyList(),
    val deviceId: String = "",
)

@Serializable
data class LibraryStateFile(val schemaVersion: Int, val accountId: String, val deviceId: String, val items: List<LibraryItem>)
