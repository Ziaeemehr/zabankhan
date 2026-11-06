package com.ziaee.zabankhan.watch

import android.content.ClipData
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable internal actual fun rememberTranscriptShare(): (text: String, fileName: String?) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text, fileName ->
        val intent = Intent(Intent.ACTION_SEND)
        if (fileName == null) {
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, text)
        } else {
            // The existing backup FileProvider exposes this cache directory.
            val directory = File(context.cacheDir, "backups").apply { mkdirs() }
            val file = File(directory, fileName).apply { writeText(text, Charsets.UTF_8) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.backupfiles", file)
            intent.type = "application/x-subrip"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.clipData = ClipData.newRawUri(fileName, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    } }
}
