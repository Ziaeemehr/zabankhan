package com.ziaee.zabankhan.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.files.FileKind
import com.ziaee.zabankhan.files.rememberFilePicker
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable fun BackupSettings(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var tooLarge by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf(false) }
    val picker = rememberFilePicker(listOf(FileKind.ZIP)) { file ->
        if (file != null) scope.launch {
            busy = true; failed = false; tooLarge = false; exported = false
            try { validateBackup(file.path); pending = file.path } catch (_: BackupTooLargeException) { tooLarge = true } catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    Column {
        Text(stringResource(Res.string.backup_heading), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.backup_automatic_help))
        TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true; failed = false; tooLarge = false; exported = false
            try { val path = createLocalBackup(container); shareBackup(path); exported = true }
            catch (_: BackupTooLargeException) { tooLarge = true } catch (_: Exception) { failed = true } finally { busy = false }
        } }) { Text(stringResource(Res.string.backup_export)) }
        TextButton(enabled = !busy, onClick = picker) { Text(stringResource(Res.string.backup_restore)) }
        if (busy) CircularProgressIndicator()
        if (tooLarge) Text(stringResource(Res.string.backup_too_large), color = MaterialTheme.colorScheme.error)
        if (failed) Text(stringResource(Res.string.backup_failed), color = MaterialTheme.colorScheme.error)
        if (exported) Text(stringResource(Res.string.backup_exported))
    }
    if (pending != null) AlertDialog(
        onDismissRequest = { if (!busy) pending = null },
        title = { Text(stringResource(Res.string.backup_restore)) },
        text = { Text(stringResource(Res.string.backup_replace_warning)) },
        confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true; failed = false
            try { restoreLocalBackup(container, requireNotNull(pending)); pending = null }
            catch (_: Exception) { failed = true; pending = null }
            finally { busy = false }
        } }) { Text(stringResource(Res.string.backup_restore)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text(stringResource(Res.string.cancel)) } },
    )
}
