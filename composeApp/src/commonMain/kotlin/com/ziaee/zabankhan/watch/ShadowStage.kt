package com.ziaee.zabankhan.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.ui.reader.ShadowingAlignment
import com.ziaee.zabankhan.ui.EditorialActionMenu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Video shadowing view: sentence card, record card and a fixed control bar. [replay] plays one sentence of the video. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ShadowStage(
    container: AppContainer, lang: LanguageSpec, textId: Long?, sentences: List<String>, index: Int, onIndexChange: (Int) -> Unit,
    replay: suspend (Int) -> Long, rate: Float, onRateChange: (Float) -> Unit, autoPause: Boolean, onAutoPauseChange: (Boolean) -> Unit,
    translationOn: Boolean, onTranslationToggle: () -> Unit,
    pauseOriginal: () -> Unit, sentenceContent: @Composable (String) -> Unit, translation: @Composable (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val recognizer = remember { createSpeechRecognizer() }
    val controller = remember { ShadowingController(scope, recognizer, { container.database.shadowAttempts().insert(it) }) }
    val phase by controller.phase.collectAsState()
    val permission = rememberMicPermission()
    var replayJob by remember { mutableStateOf<Job?>(null) }
    var repeats by remember { mutableIntStateOf(1) }
    var speedMenu by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    val sentence = sentences.getOrNull(index).orEmpty()
    val busy = phase == ShadowPhase.Recording || phase == ShadowPhase.Saving
    fun startRecording(): Boolean {
        if (controller.phase.value == ShadowPhase.Playing || busy) return false
        pauseOriginal()
        if (permission.granted) return controller.listen()
        permission.request()
        return false
    }
    fun playOriginal() {
        replayJob?.cancel()
        replayJob = scope.launch {
            controller.preparePlayback()
            try {
                var duration = 0L
                for (i in 0 until repeats) { duration = replay(index); if (duration < 0) break }
                if (duration < 0) controller.playbackFailed() else controller.played(duration)
            } catch (c: CancellationException) { throw c } catch (_: Exception) { controller.playbackFailed() }
        }
    }
    LaunchedEffect(index, lang, textId) { replayJob?.cancel(); controller.target(lang, textId, sentence) }
    DisposableEffect(Unit) { onDispose { replayJob?.cancel(); controller.cancel() } }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
        replayJob?.cancel(); controller.cancel()
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.shadow_counter, index + 1, sentences.size), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FilterChip(selected = translationOn, onClick = onTranslationToggle, label = { Text(stringResource(Res.string.watch_toolbar_translate)) },
                            leadingIcon = { Icon(Icons.Default.Translate, null, Modifier.size(18.dp)) })
                    }
                    sentenceContent(sentence)
                    translation(sentence)
                }
            }
            val status = when (val p = phase) {
                ShadowPhase.Recording -> stringResource(Res.string.shadowing_listening)
                ShadowPhase.Saving -> stringResource(Res.string.shadowing_recognizing)
                ShadowPhase.Playing -> stringResource(Res.string.speech_listen)
                is ShadowPhase.Result -> stringResource(Res.string.shadowing_score, p.alignment.matched, p.alignment.total)
                is ShadowPhase.Error -> stringResource(if (p.notHeard) Res.string.shadowing_not_heard else Res.string.shadowing_engine_failed)
                else -> stringResource(Res.string.shadowing_hold_to_speak)
            }
            Text(status, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                color = if (phase is ShadowPhase.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            (phase as? ShadowPhase.Result)?.let { result ->
                result.pace?.let { pace ->
                    Text(stringResource(when (pace.rating) { PaceRating.FAST -> Res.string.shadowing_pace_fast; PaceRating.SLOW -> Res.string.shadowing_pace_slow; PaceRating.GOOD -> Res.string.shadowing_pace_good },
                        pace.ratio.toString()), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                ShadowingAlignment(result.alignment)
            }
            if (phase is ShadowPhase.Result || phase is ShadowPhase.Error || phase == ShadowPhase.Recorded) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = controller::retry, Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(Res.string.shadowing_retry), maxLines = 1)
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp, shape = MaterialTheme.shapes.large.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { if (index > 0) onIndexChange(index - 1) }, enabled = index > 0 && !busy, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipPrevious, stringResource(Res.string.watch_shadow_previous)) }
                    FilledTonalIconButton(onClick = {
                        if (phase == ShadowPhase.Playing) { replayJob?.cancel(); pauseOriginal(); controller.cancel() } else playOriginal()
                    }, enabled = !busy, modifier = Modifier.size(56.dp)) {
                        Icon(if (phase == ShadowPhase.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            stringResource(if (phase == ShadowPhase.Playing) Res.string.watch_pause else Res.string.watch_play), Modifier.size(30.dp))
                    }
                    val recording = phase == ShadowPhase.Recording
                    Surface(
                        modifier = Modifier.size(64.dp).holdToRecord(::startRecording, controller::finishRecording, controller::cancel),
                        shape = CircleShape,
                        color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Mic, stringResource(Res.string.shadowing_hold_to_speak), Modifier.size(32.dp))
                        }
                    }
                    FilledTonalIconButton(onClick = { pauseOriginal(); controller.replayMine() },
                        enabled = controller.hasRecording && !busy && phase != ShadowPhase.Playing, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Headphones, stringResource(Res.string.shadowing_replay_mine))
                    }
                    FilledTonalIconButton(onClick = { if (index < sentences.lastIndex) onIndexChange(index + 1) }, enabled = index < sentences.lastIndex && !busy, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.SkipNext, stringResource(Res.string.speech_next)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { speedMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.Default.Speed, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("${rate}×", maxLines = 1) }
                        EditorialActionMenu(speedMenu, onDismissRequest = { speedMenu = false }) {
                            listOf(.5f, .75f, 1f).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("$s×") },
                                    leadingIcon = { if (rate == s) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { onRateChange(s); speedMenu = false },
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { repeatMenu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Icon(Icons.Default.Repeat, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("${repeats}×", maxLines = 1) }
                        EditorialActionMenu(repeatMenu, onDismissRequest = { repeatMenu = false }) {
                            listOf(1, 2, 3, 5).forEach { n ->
                                DropdownMenuItem(
                                    text = { Text("$n×") },
                                    leadingIcon = { if (repeats == n) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { repeats = n; repeatMenu = false },
                                )
                            }
                        }
                    }
                    val toggleColors = if (autoPause) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else ButtonDefaults.outlinedButtonColors()
                    OutlinedButton(onClick = { onAutoPauseChange(!autoPause) }, Modifier.weight(1f), colors = toggleColors, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(if (autoPause) Icons.Default.PauseCircle else Icons.Default.PlayCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.shadow_auto_pause_short), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
