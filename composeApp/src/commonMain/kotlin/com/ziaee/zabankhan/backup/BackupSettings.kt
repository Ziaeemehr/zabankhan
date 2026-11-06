package com.ziaee.zabankhan.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.files.FileKind
import com.ziaee.zabankhan.files.rememberFilePicker
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.stringResource

@Composable fun BackupSettings(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var tooLarge by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf(false) }
    val drive = rememberDriveAuth()
    var connected by remember { mutableStateOf(drive.connected) }
    var driveFiles by remember { mutableStateOf<List<DriveBackupFile>?>(null) }
    var driveFailed by remember { mutableStateOf(false) }
    var driveSaved by remember { mutableStateOf(false) }
    val http = remember { io.ktor.client.HttpClient() }
    DisposableEffect(http) { onDispose { http.close() } }
    val client = remember(http) { DriveBackupClient(http) }
    fun driveAction(action: suspend (String) -> Unit) {
        scope.launch {
            busy = true; driveFailed = false; driveSaved = false
            try {
                val token = drive.accessToken()
                if (token != null) { connected = true; action(token) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { driveFailed = true }
            finally { busy = false }
        }
    }
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
        if (drive.configured) {
            Text(stringResource(Res.string.drive_heading), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.drive_help))
            Text(stringResource(if (connected) Res.string.drive_connected else Res.string.drive_disconnected))
            if (!connected) TextButton(enabled = !busy, onClick = { driveAction {} }) {
                Text(stringResource(Res.string.drive_connect))
            }
            if (connected) {
                TextButton(enabled = !busy, onClick = { driveAction { token ->
                    val path = createLocalBackup(container)
                    val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        okio.FileSystem.SYSTEM.read(path.toPath()) { readByteArray() }
                    }
                    client.upload(token, bytes, kotlin.time.Clock.System.now().toEpochMilliseconds())
                    driveSaved = true
                } }) { Text(stringResource(Res.string.drive_upload)) }
                TextButton(enabled = !busy, onClick = { driveAction { token -> driveFiles = client.list(token).take(5) } }) {
                    Text(stringResource(Res.string.drive_restore))
                }
                TextButton(enabled = !busy, onClick = { scope.launch {
                    busy = true; driveFailed = false
                    try { drive.disconnect(); connected = false; driveFiles = null }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { driveFailed = true }
                    finally { busy = false }
                } }) { Text(stringResource(Res.string.drive_disconnect)) }
            }
            if (driveFailed) Text(stringResource(Res.string.drive_failed), color = MaterialTheme.colorScheme.error)
            if (driveSaved) Text(stringResource(Res.string.drive_saved))
        }
        if (busy) CircularProgressIndicator()
        if (tooLarge) Text(stringResource(Res.string.backup_too_large), color = MaterialTheme.colorScheme.error)
        if (failed) Text(stringResource(Res.string.backup_failed), color = MaterialTheme.colorScheme.error)
        if (exported) Text(stringResource(Res.string.backup_exported))
    }
    driveFiles?.let { files -> AlertDialog(
        onDismissRequest = { if (!busy) driveFiles = null },
        title = { Text(stringResource(Res.string.drive_restore)) },
        text = { Column {
            if (files.isEmpty()) Text(stringResource(Res.string.drive_empty))
            files.forEach { file -> TextButton(enabled = !busy, onClick = { driveAction { token ->
                val path = (backupPaths().exports.toPath() / "drive-restore.zip").toString()
                client.download(token, file.id, path)
                pending = path; driveFiles = null
            } }) { Text(file.createdTime.replace('T', ' ').removeSuffix("Z") + " UTC") } }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { driveFiles = null }) { Text(stringResource(Res.string.cancel)) } },
    ) }
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
