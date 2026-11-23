package com.ziaee.zabankhan.watch

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.Icons
import com.ziaee.zabankhan.ui.ActionMenuDivider
import com.ziaee.zabankhan.ui.EditorialActionMenu
import com.ziaee.zabankhan.ui.EditorialListRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.EditorialBottomSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Original cue boundaries and line breaks are retained for subtitle export. */
internal fun transcriptSrt(cues: List<SubtitleCue>): String = buildString {
    cues.forEachIndexed { index, cue ->
        append(index + 1).append('\n')
        append(srtTimestamp(cue.startMs)).append(" --> ").append(srtTimestamp(cue.endMs)).append('\n')
        append(cue.text.replace("\r\n", "\n").replace('\r', '\n')).append("\n\n")
    }
}

private fun srtTimestamp(timeMs: Long): String {
    val ms = timeMs.coerceAtLeast(0)
    fun Long.pad(width: Int) = toString().padStart(width, '0')
    return "${(ms / 3_600_000).pad(2)}:${(ms / 60_000 % 60).pad(2)}:${(ms / 1000 % 60).pad(2)},${(ms % 1000).pad(3)}"
}

internal fun transcriptFileName(title: String): String = title.map { char ->
    if (char in "\\/:*?\"<>|" || char.code < 32 || char.code == 127) '_' else char
}.joinToString("").trim().trim('.').take(100).ifBlank { "transcript" } + ".srt"

/** Null filename shares text; a filename shares an SRT attachment. */
@Composable internal expect fun rememberTranscriptShare(): (text: String, fileName: String?) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WatchExportSheet(
    vm: AppViewModel,
    lang: LanguageSpec,
    identity: String,
    videoId: String,
    path: String,
    captions: VideoCaptions,
    existingDocument: TextDocument?,
    dismiss: () -> Unit,
    added: (TextDocument) -> Unit,
) {
    val share = rememberTranscriptShare()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var title by remember(existingDocument?.id, captions.title) { mutableStateOf(existingDocument?.title ?: captions.title) }
    var folderId by remember(existingDocument?.id) { mutableStateOf(existingDocument?.folderId) }
    var folderMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf("") }
    val organizer = vm.container.database.libraryOrganizer()
    val folders by remember(lang.code) { organizer.observeFolders(lang.code) }.collectAsState(emptyList())
    fun export(action: suspend () -> Unit) {
        scope.launch {
            saving = true; failed = false
            try { action(); dismiss() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { saving = false }
        }
    }
    EditorialBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.watch_export), style = MaterialTheme.typography.titleLarge)
            EditorialListRow(stringResource(Res.string.watch_export_library), Icons.Default.Folder, enabled = !saving, onClick = { saveDialog = true })
            ActionMenuDivider()
            EditorialListRow(stringResource(Res.string.watch_export_share), Icons.Default.Share, enabled = !saving, onClick = {
                export { share(transcriptBody(mergeSentenceUnits(captions.cues, lang).units), null) }
            })
            EditorialListRow(stringResource(Res.string.watch_export_srt), Icons.Default.Description, enabled = !saving, onClick = {
                export { share(transcriptSrt(captions.cues), transcriptFileName(captions.title)) }
            })
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(Res.string.watch_export_failed), color = MaterialTheme.colorScheme.error)
        }
    }
    if (saveDialog) AlertDialog(
        onDismissRequest = { saveDialog = false },
        title = { Text(stringResource(Res.string.watch_export_library)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(Res.string.title)) }, singleLine = true)
                Box {
                    TextButton(onClick = { folderMenu = true }) { Text(folders.firstOrNull { it.id == folderId }?.name ?: stringResource(Res.string.folder)) }
                    EditorialActionMenu(folderMenu, { folderMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.folder)) },
                            leadingIcon = { if (folderId == null) Icon(Icons.Default.Check, null) }, onClick = { folderId = null; folderMenu = false })
                        folders.forEach { folder -> DropdownMenuItem(text = { Text(folder.name) },
                            leadingIcon = { if (folder.id == folderId) Icon(Icons.Default.Check, null) }, onClick = { folderId = folder.id; folderMenu = false }) }
                    }
                }
                OutlinedTextField(newFolder, { newFolder = it }, label = { Text(stringResource(Res.string.create_folder)) }, singleLine = true)
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(Res.string.watch_export_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = !saving && title.isNotBlank(), onClick = { export {
            val cleanFolder = newFolder.trim()
            val saved = saveTranscriptToLibrary(vm.container.database, identity, lang, title.trim(),
                if (path.isBlank()) "https://www.youtube.com/watch?v=$videoId" else null,
                mergeSentenceUnits(captions.cues, lang).units, folderId = folderId,
                newFolderName = cleanFolder, legacyCues = captions.cues)
            added(saved); saveDialog = false
        } }) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(enabled = !saving, onClick = { saveDialog = false }) { Text(stringResource(Res.string.cancel)) } },
    )
}
