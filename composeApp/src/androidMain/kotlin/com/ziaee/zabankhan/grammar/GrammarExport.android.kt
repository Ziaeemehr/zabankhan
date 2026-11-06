package com.ziaee.zabankhan.grammar

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.ziaee.zabankhan.resources.*
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun rememberGrammarExport(): (String, String?, Boolean) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = stringResource(Res.string.grammar_save_success)
    val saveFailure = stringResource(Res.string.grammar_save_failure)
    val shareFailure = stringResource(Res.string.grammar_share_failure)
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val text = pending
        pending = null
        if (uri != null && text != null) scope.launch {
            val success = runCatching {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Unable to open destination")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
            }.isSuccess
            Toast.makeText(context, if (success) saved else saveFailure, Toast.LENGTH_SHORT).show()
        }
    }
    return { text, fileName, save ->
        if (save) {
            pending = text
            runCatching { launcher.launch(grammarExportFileName(fileName, "note.md")) }.onFailure {
                pending = null
                Toast.makeText(context, saveFailure, Toast.LENGTH_SHORT).show()
            }
        } else scope.launch {
            runCatching {
                val intent = if (fileName == null) Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                } else withContext(Dispatchers.IO) {
                    grammarShareFileIntent(context, grammarExportFileName(fileName, "note.md"), "text/markdown") {
                        it.write(text.toByteArray(Charsets.UTF_8))
                    }
                }
                context.startActivity(Intent.createChooser(intent, null))
            }.onFailure { Toast.makeText(context, shareFailure, Toast.LENGTH_SHORT).show() }
        }
    }
}

@Composable
actual fun rememberGrammarPdfExport(): (List<GrammarPdfBlock>, String, String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failure = stringResource(Res.string.grammar_share_failure)
    return { blocks, fileName, appName ->
        scope.launch {
            runCatching {
                val intent = withContext(Dispatchers.IO) {
                    grammarShareFileIntent(context, grammarExportFileName(fileName, "note.pdf"), "application/pdf") {
                        writeGrammarPdf(context, blocks, listOf(appName), it)
                    }
                }
                context.startActivity(Intent.createChooser(intent, null))
            }.onFailure { Toast.makeText(context, failure, Toast.LENGTH_SHORT).show() }
        }
    }
}

/** Same FileProvider and cache directory used by the existing backup export. */
private fun grammarShareFileIntent(context: Context, name: String, mime: String, write: (OutputStream) -> Unit): Intent {
    val directory = File(context.cacheDir, "backups").apply { check(mkdirs() || isDirectory) }
    val staleBefore = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    directory.listFiles()?.filter { it.name.startsWith("grammar-") && it.lastModified() < staleBefore }?.forEach { it.delete() }
    val file = File(directory, "grammar-$name")
    file.outputStream().use(write)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.backupfiles", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, name)
        clipData = ClipData.newRawUri(name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
