package com.ziaee.zabankhan.pdf

import android.content.ClipData
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable internal actual fun rememberPdfShare(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { path: String ->
        // Reuse the registered cache provider; the source may live outside its exposed root.
        val exported = File(context.cacheDir, "backups/shared.pdf")
        exported.parentFile?.mkdirs()
        File(path).copyTo(exported, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.backupfiles", exported)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    } }
}
