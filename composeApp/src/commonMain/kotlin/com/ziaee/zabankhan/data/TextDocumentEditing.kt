package com.ziaee.zabankhan.data

/** Inline-body KMP port of French Reader's TextBodyStore.updateTextDocumentBody. */
fun editedTextDocument(document: TextDocument, title: String, body: String): TextDocument {
    // A blank editor body must never erase stored text (including title-only edits).
    val changed = body.isNotBlank() && body != document.body
    return document.copy(
        title = title.ifBlank { document.title },
        body = if (changed) body else document.body,
        bodyPath = if (changed) null else document.bodyPath,
        lastOffset = if (changed) 0 else document.lastOffset,
        lastChunkIndex = if (changed) 0 else document.lastChunkIndex,
        lastPositionMs = if (changed) 0 else document.lastPositionMs
    )
}
