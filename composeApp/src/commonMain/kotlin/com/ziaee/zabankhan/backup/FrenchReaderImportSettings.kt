package com.ziaee.zabankhan.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.files.FileKind
import com.ziaee.zabankhan.files.rememberFilePicker
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable fun FrenchReaderImportSettings(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<MigrationSummary?>(null) }
    val picker = rememberFilePicker(listOf(FileKind.ZIP)) { file ->
        if (file != null && !busy) scope.launch {
            busy = true; failed = false; summary = null
            try { summary = importFrenchReaderBackup(container, file.path) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    Column {
        TextButton(enabled = !busy, onClick = picker) { Text(stringResource(Res.string.migration_import)) }
        if (busy) CircularProgressIndicator()
        if (failed) Text(stringResource(Res.string.migration_failed), color = MaterialTheme.colorScheme.error)
        summary?.let {
            Text(stringResource(Res.string.migration_summary, it.texts, it.words, it.highlights, it.reviews, it.skipped))
            if (it.missingPdfs > 0) Text(stringResource(Res.string.migration_missing_pdfs, it.missingPdfs))
        }
    }
}
