package com.ziaee.zabankhan.files

import androidx.compose.runtime.Composable

enum class FileKind(val extensions: List<String>) {
    PDF(listOf("pdf")), EPUB(listOf("epub")), TEXT(listOf("txt", "md", "markdown")),
    IMAGE(listOf("jpg", "jpeg", "png", "heic", "webp")), CSV(listOf("csv", "tsv")), JSON(listOf("json")),
    VIDEO(listOf("mp4", "m4v", "mov")), SUBTITLE(listOf("srt", "vtt")), ZIP(listOf("zip"))
}
data class PickedFile(val path: String, val displayName: String)

/** Copies security-scoped/provider content into private storage before returning. */
@Composable expect fun rememberFilePicker(types: List<FileKind>, onPicked: (PickedFile?) -> Unit): () -> Unit
