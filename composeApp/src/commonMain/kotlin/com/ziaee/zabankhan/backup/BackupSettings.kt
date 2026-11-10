package com.ziaee.zabankhan.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.sync.ConflictChoice
import com.ziaee.zabankhan.sync.DriveSyncCoordinator
import com.ziaee.zabankhan.data.SyncConflict
import com.ziaee.zabankhan.files.FileKind
import com.ziaee.zabankhan.files.rememberFilePicker
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.*
import okio.Path
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock

/** Backup sections copied from French Reader ui/SettingsScreen.kt. */
@Composable fun BackupSettings(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drive = rememberDriveAuth()
    var lastBackupAtMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(container) { lastBackupAtMs = container.settings.backupPrefs.getLastBackupAtMs() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.batch_a_backup_overview_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.batch_a_backup_overview_last, formatLastBackupLabel(lastBackupAtMs)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.batch_a_backup_overview_description), style = MaterialTheme.typography.bodySmall)
            }
        }
        BackupMethodCard { AutoBackupSection(container, scope, snackbarHostState) }
        BackupMethodCard { LocalBackupSection(container, scope, snackbarHostState) { lastBackupAtMs = it } }
        if (drive.configured) BackupMethodCard { CloudBackupSection(container, scope, snackbarHostState, drive) { lastBackupAtMs = it } }
        if (drive.configured) container.driveSync?.let { coordinator ->
            BackupMethodCard { CloudSyncSection(coordinator, scope, snackbarHostState, drive) }
        }
        SnackbarHost(snackbarHostState)
    }
}

@Composable
private fun CloudSyncSection(
    coordinator: DriveSyncCoordinator,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    drive: DriveAuth,
) {
    val state by coordinator.state.collectAsState()
    val busy = state.syncing

    @Composable fun syncButton(action: suspend (String) -> Unit) {
        OutlinedButton(enabled = !busy, onClick = {
            scope.launch {
                try {
                    val token = drive.accessToken()
                    if (token != null) {
                        action(token)
                        snackbarHostState.showSnackbar(getString(Res.string.batch_a_drive_sync_success))
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    snackbarHostState.showSnackbar(error.message ?: getString(Res.string.batch_a_backup_failure))
                }
            }
        }) { Text(stringResource(Res.string.batch_a_drive_sync_connect)) }
    }

    Text(stringResource(Res.string.batch_a_drive_sync_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(Res.string.batch_a_drive_sync_description), style = MaterialTheme.typography.bodySmall)
    Text(
        stringResource(
            Res.string.batch_a_drive_sync_last,
            state.lastSuccessfulSyncMs?.let { formatBackupTimestamp(it) } ?: stringResource(Res.string.batch_a_drive_sync_never),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    if (busy) Text(stringResource(Res.string.batch_a_drive_sync_syncing), style = MaterialTheme.typography.bodySmall)
    if (!state.error.isNullOrBlank()) Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    if (!drive.connected) {
        syncButton { token -> coordinator.syncNow(token) }
    } else {
        Button(enabled = !busy, onClick = {
            scope.launch {
                try {
                    val token = drive.accessToken()
                    if (token != null) {
                        coordinator.syncNow(token)
                        snackbarHostState.showSnackbar(getString(Res.string.batch_a_drive_sync_success))
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { snackbarHostState.showSnackbar(error.message ?: getString(Res.string.batch_a_backup_failure)) }
            }
        }) { Text(stringResource(Res.string.batch_a_drive_sync_now)) }
    }
    if (state.conflicts.isNotEmpty()) {
        Text(stringResource(Res.string.batch_a_drive_sync_conflicts, state.conflicts.size), style = MaterialTheme.typography.titleSmall)
        state.conflicts.forEach { conflict ->
            SyncConflictCard(conflict, busy) { choice ->
                scope.launch {
                    try {
                        val token = drive.accessToken() ?: return@launch
                        coordinator.resolveConflict(conflict.conflictId, choice, token)
                        snackbarHostState.showSnackbar(getString(Res.string.batch_a_drive_sync_resolved))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { snackbarHostState.showSnackbar(error.message ?: getString(Res.string.batch_a_backup_failure)) }
                }
            }
        }
    }
}

@Composable
private fun SyncConflictCard(conflict: SyncConflict, busy: Boolean, onResolve: (ConflictChoice) -> Unit) {
    val local = conflictDisplay(conflict.localPayload).ifBlank { stringResource(Res.string.batch_a_drive_sync_deleted) }
    val remote = conflictDisplay(conflict.remotePayload).ifBlank { stringResource(Res.string.batch_a_drive_sync_deleted) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.batch_a_drive_sync_conflict_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(Res.string.batch_a_drive_sync_local_version, local), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(Res.string.batch_a_drive_sync_cloud_version, remote), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !busy, onClick = { onResolve(ConflictChoice.KEEP_LOCAL) }) {
                Text(stringResource(Res.string.batch_a_drive_sync_keep_local))
            }
            OutlinedButton(enabled = !busy, onClick = { onResolve(ConflictChoice.KEEP_DRIVE) }) {
                Text(stringResource(Res.string.batch_a_drive_sync_keep_cloud))
            }
            if (conflict.entityType == "card" && conflict.localPayload.isNotBlank() && conflict.remotePayload.isNotBlank()) {
                TextButton(enabled = !busy, onClick = { onResolve(ConflictChoice.KEEP_BOTH) }) {
                    Text(stringResource(Res.string.batch_a_drive_sync_keep_both))
                }
            }
        }
    }
}

private fun conflictDisplay(payload: String): String = runCatching {
    val value = kotlinx.serialization.json.Json.parseToJsonElement(payload).jsonObject
    value["word"]?.jsonPrimitive?.contentOrNull ?: value["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
}.getOrDefault("")
@Composable private fun BackupMethodCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
@Composable private fun formatLastBackupLabel(atMs: Long?): String =
    if (atMs == null) stringResource(Res.string.batch_a_backup_never_backed_up) else formatBackupTimestamp(atMs)
private suspend fun pdfSizeBytes(): Long = withContext(Dispatchers.IO) {
    val fs = okio.FileSystem.SYSTEM
    val root = backupPaths().files.toPath() / "pdfs"
    if (!fs.exists(root)) 0L else fs.listRecursively(root).sumOf { fs.metadata(it).size ?: 0L }
}
@Composable
private fun AutoBackupSection(
    container: AppContainer,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    var enabled by remember { mutableStateOf(true) }
    var showBackups by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Path?>(null) }
    var backups by remember { mutableStateOf(AutoBackup.listBackups()) }
    val latest = remember { AutoBackup.latest() }
    LaunchedEffect(container) { enabled = container.settings.backupPrefs.isAutoBackupEnabled() }

    fun showMessage(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    Text(
        stringResource(Res.string.batch_a_auto_backup_section_title),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    SettingsSwitchRow(stringResource(Res.string.batch_a_auto_backup_toggle), enabled) {
        enabled = it
        scope.launch { container.settings.backupPrefs.setAutoBackupEnabled(it) }
    }
    Text(stringResource(Res.string.batch_a_auto_backup_description), style = MaterialTheme.typography.bodySmall)
    Text(
        stringResource(
            Res.string.batch_a_auto_backup_last,
            formatLastBackupLabel(latest?.let { backupEpoch(it.name) })
        ),
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedButton(enabled = backups.isNotEmpty(), onClick = { backups = AutoBackup.listBackups(); showBackups = true }) {
        Text(stringResource(Res.string.batch_a_auto_backup_restore))
    }

    if (showBackups) {
        BackupPickerDialog(
            backups.map { file ->
                val timestamp = backupEpoch(file.name)
                val label = timestamp?.let { formatBackupTimestamp(it) } ?: file.name
                val size = formatBackupSize(stringResource(Res.string.batch_a_backup_size_mb), okio.FileSystem.SYSTEM.metadata(file).size ?: 0L)
                label + "\n" + size + if (isProtectedBackupFileName(file.name))
                    "\n" + stringResource(Res.string.batch_a_backup_protected_label) else ""
            },
            onDismiss = { showBackups = false },
            onSelect = { selected = backups[it]; showBackups = false }
        )
    }
    val chosen = selected
    if (chosen != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(stringResource(Res.string.batch_a_auto_backup_restore_confirm_title)) },
            text = { Text(stringResource(Res.string.batch_a_local_backup_restore_confirm_message)) },
            confirmButton = {
                OutlinedButton(onClick = {
                    selected = null
                    scope.launch {
                        try {
                            restoreLocalBackup(container, chosen.toString())
                        } catch (cancelled: CancellationException) { throw cancelled } catch (e: Exception) {
                            showMessage(getString(Res.string.batch_a_local_backup_restore_failure))
                        }
                    }
                }) { Text(stringResource(Res.string.batch_a_backup_restore)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { selected = null }) {
                    Text(stringResource(Res.string.batch_a_accessibility_back))
                }
            }
        )
    }
}

@Composable
private fun LocalBackupSection(
    container: AppContainer,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onBackupSaved: (Long) -> Unit
) {
    var restoreUri by remember { mutableStateOf<String?>(null) }
    var backUpPdfs by remember { mutableStateOf(false) }
    val pdfBytes by produceState(0L, container) {
        value = withContext(Dispatchers.IO) { pdfSizeBytes() }
    }


    fun showMessage(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    LaunchedEffect(container) { backUpPdfs = container.settings.backupPrefs.backUpPdfFiles() }
    val importLauncher = rememberFilePicker(listOf(FileKind.ZIP)) { file -> restoreUri = file?.path }
    fun exportBackup() { scope.launch {
        try {
            shareBackup(createLocalBackup(container, embedPdfs = true))
            val savedAt = Clock.System.now().toEpochMilliseconds()
            container.settings.backupPrefs.setLastBackupAtMs(savedAt)
            onBackupSaved(savedAt)
            showMessage(getString(Res.string.batch_a_local_backup_save_success))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { showMessage(getString(Res.string.batch_a_local_backup_save_failure)) }
    } }

    Text(
        stringResource(Res.string.batch_a_local_backup_section_title),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.batch_a_backup_pdf_files))
            Text(formatFileSize(pdfBytes), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(Res.string.batch_a_backup_pdf_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = backUpPdfs, onCheckedChange = {
            backUpPdfs = it
            scope.launch { container.settings.backupPrefs.setBackUpPdfFiles(it) }
        })
    }
    Button(onClick = {
        exportBackup()
    }) {
        Text(stringResource(Res.string.batch_a_local_backup_save))
    }
    OutlinedButton(onClick = { importLauncher() }) {
        Text(stringResource(Res.string.batch_a_local_backup_restore))
    }

    if (restoreUri != null) {
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(stringResource(Res.string.batch_a_local_backup_restore_confirm_title)) },
            text = { Text(stringResource(Res.string.batch_a_local_backup_restore_confirm_message)) },
            confirmButton = {
                OutlinedButton(onClick = {
                    val uri = restoreUri ?: return@OutlinedButton
                    restoreUri = null
                    scope.launch {
                        try {
                            restoreLocalBackup(container, uri)
                        } catch (cancelled: CancellationException) { throw cancelled } catch (e: Exception) {
                            showMessage(getString(Res.string.batch_a_local_backup_restore_failure))
                        }
                    }
                }) { Text(stringResource(Res.string.batch_a_local_backup_restore)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { restoreUri = null }) {
                    Text(stringResource(Res.string.batch_a_accessibility_back))
                }
            }
        )
    }
}

@Composable
private fun CloudBackupSection(container: AppContainer, scope: CoroutineScope, snackbarHostState: SnackbarHostState, drive: DriveAuth, onBackupSaved: (Long) -> Unit) {
    if (!drive.configured) return
    var connected by remember { mutableStateOf(drive.connected) }
    var lastBackupAtMs by remember { mutableStateOf<Long?>(null) }
    var backups by remember { mutableStateOf<List<DriveBackupFile>?>(null) }
    var selected by remember { mutableStateOf<DriveBackupFile?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(container) { lastBackupAtMs = container.settings.backupPrefs.getLastDriveUploadAtMs() }
    fun withDriveAccessToken(action: suspend (String) -> Unit) { scope.launch {
        busy = true
        try { drive.accessToken()?.let { connected = true; action(it) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { snackbarHostState.showSnackbar(getString(Res.string.batch_a_backup_failure)) }
        finally { busy = false }
    } }
    val http = remember { io.ktor.client.HttpClient() }
    DisposableEffect(http) { onDispose { http.close() } }
    val client = remember(http) { DriveBackupClient(http) }
    Text(
        stringResource(Res.string.batch_a_backup_section_title),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
    if (!connected) {
        OutlinedButton(enabled = !busy, onClick = { withDriveAccessToken {} }) {
            Text(stringResource(Res.string.batch_a_backup_sign_in))
        }
    } else {
        Text(formatLastBackupLabel(lastBackupAtMs))
        Button(enabled = !busy, onClick = {
            withDriveAccessToken { token ->
                val archive = createLocalBackup(container)
                val bytes = withContext(Dispatchers.IO) { okio.FileSystem.SYSTEM.read(archive.toPath()) { readByteArray() } }
                client.uploadBackup(token, bytes, Clock.System.now().toEpochMilliseconds(), backupPaths().files + "/pdfs")
                val now = Clock.System.now().toEpochMilliseconds()
                container.settings.backupPrefs.setLastBackupAtMs(now)
                lastBackupAtMs = now
                onBackupSaved(now)
                snackbarHostState.showSnackbar(getString(Res.string.batch_a_backup_success))
            }
        }) { Text(stringResource(Res.string.batch_a_backup_now)) }
        OutlinedButton(enabled = !busy, onClick = {
            withDriveAccessToken { token ->
                val files = client.list(token)
                if (files.isEmpty()) snackbarHostState.showSnackbar(getString(Res.string.batch_a_backup_restore_not_found))
                else backups = files
            }
        }) { Text(stringResource(Res.string.batch_a_backup_restore)) }
        OutlinedButton(enabled = !busy, onClick = { scope.launch {
            try { drive.disconnect(); connected = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { snackbarHostState.showSnackbar(getString(Res.string.batch_a_backup_failure)) }
        } }) { Text(stringResource(Res.string.batch_a_backup_sign_out)) }
    }
    backups?.let { files ->
        BackupPickerDialog(
            files.map { file -> runCatching { formatBackupTimestamp(kotlin.time.Instant.parse(file.createdTime).toEpochMilliseconds()) }.getOrDefault(file.name) },
            onDismiss = { backups = null },
            onSelect = { selected = files[it]; backups = null }
        )
    }
    selected?.let { chosen ->
        AlertDialog(
            onDismissRequest = { if (!busy) selected = null },
            title = { Text(stringResource(Res.string.batch_a_backup_restore_confirm_title)) },
            text = { Text(stringResource(Res.string.batch_a_backup_restore_confirm_message)) },
            confirmButton = {
                OutlinedButton(enabled = !busy, onClick = {
                    withDriveAccessToken { token ->
                        val path = (backupPaths().exports.toPath() / "drive-restore.zip").toString()
                        client.download(token, chosen.id, path)
                        selected = null
                        restoreLocalBackup(container, path, token)
                        BackupRuntime.lastFailure?.let { snackbarHostState.showSnackbar(it) }
                    }
                }) { Text(stringResource(Res.string.batch_a_backup_restore)) }
            },
            dismissButton = {
                OutlinedButton(enabled = !busy, onClick = { selected = null }) { Text(stringResource(Res.string.batch_a_accessibility_back)) }
            }
        )
    }
}
@Composable
private fun BackupPickerDialog(labels: List<String>, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.batch_a_backup_choose)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                labels.forEachIndexed { index, label ->
                    TextButton(onClick = { onSelect(index) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.batch_a_accessibility_back)) } }
    )
}
@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
