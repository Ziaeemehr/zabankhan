package com.ziaee.zabankhan.speech

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.*
import com.ziaee.zabankhan.ui.reader.ShadowingAlignment
import com.ziaee.zabankhan.ui.theme.*
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource
import kotlin.coroutines.resume

@Composable
internal fun Modifier.holdToRecord(
    onStart: () -> Boolean,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val start by rememberUpdatedState(onStart)
    val release by rememberUpdatedState(onRelease)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown().consume()
            if (start()) {
                var released = false
                try {
                    val up = waitForUpOrCancellation()
                    released = up != null
                    up?.consume()
                } finally {
                    if (released) release() else cancel()
                }
            }
        }
    }
}

fun Tts.pronounce(text: String, lang: String, settings: AppSettings, done: () -> Unit = {}) {
    setVoice(settings.ttsVoices[lang]); speak(text, lang, settings.speechRate, done)
}
/** Speaker buttons: a second tap while speaking stops playback instead of restarting it. */
fun Tts.togglePronounce(text: String, lang: String, settings: AppSettings) {
    if (isSpeaking.value) stop() else pronounce(text, lang, settings)
}
@Composable fun SpeakerButton(text: String, lang: String, settings: AppSettings) {
    val tts = remember { createTts() }
    EditorialIconButton({ tts.togglePronounce(text, lang, settings) }, Icons.AutoMirrored.Filled.VolumeUp, stringResource(Res.string.speech_listen))
}

/** Inline video-shadow controls; [replay] plays the selected video cue instead of TTS. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ShadowingPanel(container: AppContainer, settings: AppSettings, lang: LanguageSpec,
    textId: Long?, sentences: List<String>, startIndex: Int = 0, dismiss: () -> Unit,
    replay: (suspend (Int) -> Long)? = null, rate: Float = 1f, onRateChange: (Float) -> Unit = {},
    onStep: (Int) -> Unit = {}, pauseOriginal: () -> Unit = {}, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val tts = remember { createTts() }
    val recognizer = remember { createSpeechRecognizer() }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, sentences.lastIndex.coerceAtLeast(0))) }
    var replayJob by remember { mutableStateOf<Job?>(null) }
    var autoRecord by remember { mutableStateOf(false) }
    var pendingRecording by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    val currentAutoRecord by rememberUpdatedState(autoRecord)
    val controller = remember { ShadowingController(scope, recognizer, { attempt ->
        container.database.shadowAttempts().insert(attempt)
    }) }
    val phase by controller.phase.collectAsState()
    val permission = rememberMicPermission()
    val sentence = sentences.getOrNull(index).orEmpty()
    val speedDescription = stringResource(Res.string.watch_shadow_speed, rate.toString())
    fun startRecording(fromAutoRecord: Boolean = false): Boolean {
        if (controller.phase.value == ShadowPhase.Recording) return !fromAutoRecord
        if (controller.phase.value == ShadowPhase.Playing || controller.phase.value == ShadowPhase.Saving) return false
        pauseOriginal()
        if (permission.granted) return controller.listen()
        pendingRecording = fromAutoRecord
        permission.request()
        return false
    }
    fun playOriginal() {
        replayJob?.cancel(); tts.stop()
        replayJob = scope.launch {
            controller.preparePlayback()
            try {
                val duration = if (replay != null) replay(index) else {
                    val started = TimeSource.Monotonic.markNow()
                    suspendCancellableCoroutine<Long> { continuation ->
                        tts.setVoice(settings.ttsVoices[lang.code])
                        tts.speak(sentence, lang.code, settings.speechRate,
                            onError = { if (continuation.isActive) continuation.resume(-1L) },
                            onDone = { if (continuation.isActive) continuation.resume(started.elapsedNow().inWholeMilliseconds) })
                        continuation.invokeOnCancellation { tts.stop() }
                    }
                }
                if (duration < 0) controller.playbackFailed() else {
                    controller.played(duration)
                    if (currentAutoRecord) startRecording(fromAutoRecord = true)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { controller.playbackFailed() }
        }
    }
    LaunchedEffect(index, lang, textId) {
        replayJob?.cancel(); tts.stop()
        controller.target(lang, textId, sentence)
    }
    LaunchedEffect(startIndex) { index = startIndex.coerceIn(0, sentences.lastIndex.coerceAtLeast(0)) }
    LaunchedEffect(permission.granted, pendingRecording) {
        if (permission.granted && pendingRecording) {
            pendingRecording = false
            controller.listen()
        }
    }
    DisposableEffect(Unit) { onDispose { replayJob?.cancel(); controller.cancel(); tts.stop() } }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
        pendingRecording = false
        replayJob?.cancel(); controller.cancel(); tts.stop()
    }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(Res.string.speech_shadow), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (!permission.granted) Text(stringResource(Res.string.speech_permission), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                IconButton(onClick = { replayJob?.cancel(); controller.cancel(); tts.stop(); dismiss() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.accessibility_close))
                }
            }
            when (val current = phase) {
                ShadowPhase.Playing -> Text(stringResource(Res.string.speech_listen), style = MaterialTheme.typography.bodySmall)
                ShadowPhase.Recording -> Text(stringResource(Res.string.shadowing_listening), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
                ShadowPhase.Saving -> Text(stringResource(Res.string.shadowing_recognizing), style = MaterialTheme.typography.bodySmall)
                ShadowPhase.Recorded -> Text(stringResource(Res.string.shadowing_replay_mine), style = MaterialTheme.typography.bodySmall)
                is ShadowPhase.Error -> Text(stringResource(if (current.notHeard) Res.string.shadowing_not_heard else Res.string.shadowing_engine_failed),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                is ShadowPhase.Result -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(stringResource(Res.string.shadowing_score, current.alignment.matched, current.alignment.total),
                            style = MaterialTheme.typography.bodySmall)
                        current.pace?.let { pace ->
                            Text(stringResource(when (pace.rating) { PaceRating.FAST -> Res.string.shadowing_pace_fast; PaceRating.SLOW -> Res.string.shadowing_pace_slow; PaceRating.GOOD -> Res.string.shadowing_pace_good },
                                pace.ratio.toString()), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ShadowingAlignment(current.alignment)
                }
                ShadowPhase.Idle -> Unit
            }
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = { if (index > 0) { index--; onStep(index) } }, enabled = index > 0 && phase != ShadowPhase.Recording && phase != ShadowPhase.Saving, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(Res.string.watch_shadow_previous))
                    }
                    IconButton(onClick = {
                        if (phase == ShadowPhase.Playing) { replayJob?.cancel(); tts.stop(); pauseOriginal(); controller.cancel() }
                        else playOriginal()
                    }, enabled = phase != ShadowPhase.Recording && phase != ShadowPhase.Saving, modifier = Modifier.size(40.dp)) {
                        Icon(if (phase == ShadowPhase.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (phase == ShadowPhase.Playing) Res.string.watch_shadow_stop else Res.string.shadowing_play_original))
                    }
                    IconButton(onClick = ::playOriginal, enabled = phase != ShadowPhase.Recording && phase != ShadowPhase.Saving, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Replay, contentDescription = stringResource(Res.string.watch_shadow_repeat))
                    }
                    IconButton(onClick = { if (index < sentences.lastIndex) { index++; onStep(index) } },
                        enabled = index < sentences.lastIndex && phase != ShadowPhase.Recording && phase != ShadowPhase.Saving,
                        modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = stringResource(Res.string.speech_next))
                    }
                    Box {
                        IconButton(onClick = { speedMenu = true }, modifier = Modifier.size(40.dp), enabled = phase != ShadowPhase.Recording) {
                            Text("${rate}×", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.semantics { contentDescription = speedDescription })
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            listOf(.5f, .75f, 1f).forEach { speed -> DropdownMenuItem(
                                text = { Text("${speed}×") }, onClick = { onRateChange(speed); speedMenu = false }) }
                        }
                    }
                    IconToggleButton(checked = autoRecord, onCheckedChange = { autoRecord = it },
                        enabled = sentences.isNotEmpty(), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Autorenew, contentDescription = stringResource(Res.string.watch_shadow_auto_record),
                            tint = if (autoRecord) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current)
                    }
                    val recording = phase == ShadowPhase.Recording
                    Box(Modifier.size(40.dp).background(
                        if (recording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape).holdToRecord({ startRecording() }, controller::finishRecording, controller::cancel),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Mic, contentDescription = stringResource(Res.string.shadowing_hold_to_speak),
                            tint = if (recording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(onClick = { pauseOriginal(); controller.replayMine() },
                        enabled = controller.hasRecording && phase != ShadowPhase.Playing && phase != ShadowPhase.Recording && phase != ShadowPhase.Saving,
                        modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Hearing, contentDescription = stringResource(Res.string.shadowing_replay_mine))
                    }
                    if (phase is ShadowPhase.Result || phase is ShadowPhase.Error) IconButton(onClick = controller::retry, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.shadowing_retry))
                    }
                }
            }
        }
    }
}
