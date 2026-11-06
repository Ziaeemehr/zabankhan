package com.ziaee.zabankhan.watch

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.resources.*
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
    dismiss: () -> Unit,
    added: () -> Unit,
) {
    val share = rememberTranscriptShare()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun export(action: suspend () -> Unit) {
        scope.launch {
            saving = true; failed = false
            try { action(); dismiss() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { saving = false }
        }
    }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.watch_export), style = MaterialTheme.typography.titleLarge)
            TextButton(enabled = !saving, onClick = { export {
                saveTranscript(vm.container.texts, identity, lang, captions.title,
                    if (path.isBlank()) "https://www.youtube.com/watch?v=$videoId" else null, mergeSentenceUnits(captions.cues, lang).units, legacyCues = captions.cues)
                added()
            } }) { Text(stringResource(Res.string.watch_export_library)) }
            TextButton(enabled = !saving, onClick = { export { share(transcriptBody(mergeSentenceUnits(captions.cues, lang).units), null) } }) {
                Text(stringResource(Res.string.watch_export_share))
            }
            TextButton(enabled = !saving, onClick = { export { share(transcriptSrt(captions.cues), transcriptFileName(captions.title)) } }) {
                Text(stringResource(Res.string.watch_export_srt))
            }
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(Res.string.watch_export_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}
