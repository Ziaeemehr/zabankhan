package com.ziaee.zabankhan.files

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import java.io.File
import okio.ByteString.Companion.toByteString

@Composable actual fun rememberFilePicker(types: List<FileKind>, onPicked: (PickedFile?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) callback(null) else scope.launch {
            val picked = withContext(Dispatchers.IO) { runCatching {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: uri.lastPathSegment.orEmpty()
                val extension = name.substringAfterLast('.', "").lowercase()
                require(types.any { extension in it.extensions }) { "Unsupported file type" }
                val kind = types.first { extension in it.extensions }
                val directory = File(context.filesDir, when (kind) {
                    FileKind.PDF -> "pdfs"; FileKind.VIDEO, FileKind.SUBTITLE -> "videos"; else -> "imports"
                }).apply { mkdirs() }
                val temporary = File.createTempFile("import-", ".$extension", directory)
                try {
                    context.contentResolver.openInputStream(uri)!!.use { input -> temporary.outputStream().use { input.copyTo(it) } }
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    temporary.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
                    }
                    val target = File(directory, digest.digest().toByteString().hex() + ".$extension")
                    if (!target.exists()) check(temporary.renameTo(target))
                    File(target.path + ".title").writeText(name.substringBeforeLast('.'))
                    PickedFile(target.absolutePath, name)
                } finally { temporary.delete() }
            }.getOrNull() }
            callback(picked)
        }
    }
    return { launcher.launch(types.flatMap { kind -> when (kind) {
        FileKind.PDF -> listOf("application/pdf")
        FileKind.EPUB -> listOf("application/epub+zip", "application/zip")
        FileKind.TEXT -> listOf("text/plain", "text/markdown", "text/x-markdown")
        FileKind.IMAGE -> listOf("image/*")
        FileKind.CSV -> listOf("text/*", "application/csv", "application/vnd.ms-excel")
        FileKind.JSON -> listOf("application/json", "text/plain")
        FileKind.VIDEO -> listOf("video/*")
        FileKind.SUBTITLE -> listOf("text/*", "application/x-subrip")
        FileKind.ZIP -> listOf("application/zip", "application/x-zip-compressed")
    } }.distinct().toTypedArray()) }
}
