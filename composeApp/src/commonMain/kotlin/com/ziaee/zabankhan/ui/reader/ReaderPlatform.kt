package com.ziaee.zabankhan.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import com.ziaee.zabankhan.pdf.AppFiles
import com.ziaee.zabankhan.ui.loadImportImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

/** Local imports only: remote Markdown images never trigger network requests. */
internal fun resolveReaderImage(filesDir: String, reference: String): String? {
    if (reference.isBlank() || reference.contains("..") || '\\' in reference) return null
    val root = filesDir.toPath().normalized()
    val candidates = listOf(
        root / "text_images" / "epub_$reference",
        root / "text_images" / reference,
        root / reference
    )
    return candidates.firstOrNull { path ->
        path.normalized().toString().startsWith(root.toString() + "/") &&
            FileSystem.SYSTEM.metadataOrNull(path)?.isRegularFile == true
    }?.toString()
}

@Composable
internal fun rememberReaderImage(reference: String): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, reference) {
        value = withContext(Dispatchers.IO) {
            runCatching { resolveReaderImage(AppFiles.filesDir, reference)?.let(::loadImportImage) }.getOrNull()
        }
    }
    return bitmap
}

@Composable
internal expect fun rememberReaderProcessTextActions(text: String): List<ProcessTextAction>

internal expect fun formatReaderDate(timestampMs: Long): String
internal expect fun formatReaderNumber(number: Int): String

/** ACTION_PROCESS_TEXT sorting is copied from the reference, including duplicate labels. */
internal data class ReaderProcessTextApp(val label: String, val packageName: String,
    val activityName: String, val appLabel: String)
internal fun filterAndSortProcessTextApps(apps: List<ReaderProcessTextApp>, ownPackageName: String): List<ReaderProcessTextApp> {
    val filtered = apps.filter { it.packageName != ownPackageName }
    val labelCounts = filtered.groupingBy { it.label }.eachCount()
    return filtered.map { app ->
        if (labelCounts.getValue(app.label) > 1) app.copy(label = "${app.label} (${app.appLabel})") else app
    }.sortedWith(compareBy<ReaderProcessTextApp> { it.packageName != "com.google.android.apps.translate" }.thenBy { it.label.lowercase() })
}
