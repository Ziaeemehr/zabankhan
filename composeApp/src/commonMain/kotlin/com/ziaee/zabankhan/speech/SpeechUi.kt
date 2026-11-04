package com.ziaee.zabankhan.speech

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.*
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource
import kotlin.math.roundToInt

fun Tts.pronounce(text: String, lang: String, settings: AppSettings, done: () -> Unit = {}) {
    setVoice(settings.ttsVoices[lang]); speak(text, lang, settings.speechRate, done)
}
@Composable fun SpeakerButton(text: String, lang: String, settings: AppSettings) {
    val tts = remember { createTts() }
    IconButton(onClick = { tts.pronounce(text, lang, settings) }) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, stringResource(Res.string.speech_listen))
    }
}

/** [replay] supplies video-cue playback instead of TTS, returning its measured duration. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun ShadowingPanel(container: AppContainer, settings: AppSettings, lang: LanguageSpec,
    textId: Long?, sentences: List<String>, startIndex: Int = 0, dismiss: () -> Unit,
    autoListen: Boolean = false, replay: (suspend (Int) -> Long)? = null) {
    val scope = rememberCoroutineScope()
    val tts = remember { createTts() }
    val recognizer = remember { createSpeechRecognizer() }
    var attempts by remember { mutableStateOf(emptyList<ShadowAttempt>()) }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, sentences.lastIndex.coerceAtLeast(0))) }
    var summary by remember { mutableStateOf(false) }
    var replayJob by remember { mutableStateOf<Job?>(null) }
    val controller = remember { ShadowingController(scope, recognizer, { attempt ->
        container.database.shadowAttempts().insert(attempt)
        attempts = attempts + attempt
    }) }
    val phase by controller.phase.collectAsState()
    val permission = rememberMicPermission()
    val sentence = sentences.getOrNull(index).orEmpty()
    fun play() {
        replayJob?.cancel(); tts.stop()
        replayJob = scope.launch {
            controller.preparePlayback()
            if (replay != null) {
                try { controller.played(replay(index)); if (autoListen && permission.granted) controller.listen() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { controller.playbackFailed() }
            } else {
                val started = TimeSource.Monotonic.markNow()
                tts.pronounce(sentence, lang.code, settings) { controller.played(started.elapsedNow().inWholeMilliseconds) }
            }
        }
    }
    LaunchedEffect(index, if (autoListen) permission.granted else true) {
        controller.target(lang, textId, sentence)
        if (autoListen && !permission.granted) permission.request() else play()
    }
    DisposableEffect(Unit) { onDispose { replayJob?.cancel(); controller.cancel(); tts.stop() } }
    fun finish() { replayJob?.cancel(); controller.cancel(); tts.stop(); if (attempts.isEmpty()) dismiss() else summary = true }
    if (!summary) ModalBottomSheet(onDismissRequest = ::finish) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Heading(stringResource(Res.string.speech_shadow))
            Text(sentence, style = MaterialTheme.typography.titleLarge.merge(contentStyle(sentence)))
            val result = phase as? ShadowPhase.Result
            if (result != null) {
                val matchedColor = MaterialTheme.colorScheme.primary
                val missedColor = MaterialTheme.colorScheme.error
                Text(buildAnnotatedString { result.alignment.words.forEach { word ->
                    pushStyle(SpanStyle(color = if (word.matched) matchedColor else missedColor)); append(word.word); pop(); append(" ")
                } }, style = contentStyle(sentence))
                val score = if (result.alignment.total == 0) 0 else (result.alignment.matched * 100f / result.alignment.total).roundToInt()
                Text(stringResource(Res.string.speech_score, score))
                result.pace?.let { Text(stringResource(when (it.rating) { PaceRating.FAST -> Res.string.speech_fast; PaceRating.SLOW -> Res.string.speech_slow; PaceRating.GOOD -> Res.string.speech_good })) }
            }
            if (phase is ShadowPhase.Error) Text(stringResource(if ((phase as ShadowPhase.Error).notHeard) Res.string.speech_not_heard else Res.string.speech_error), color = MaterialTheme.colorScheme.error)
            if (!permission.granted) Text(stringResource(Res.string.speech_permission))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = ::play, enabled = phase != ShadowPhase.Saving) { Text(stringResource(Res.string.speech_listen)) }
                Button(enabled = phase != ShadowPhase.Playing && phase != ShadowPhase.Saving, onClick = {
                    if (!permission.granted) permission.request()
                    else if (phase == ShadowPhase.Recording) recognizer.stop() else controller.listen()
                }) { Text(stringResource(if (phase == ShadowPhase.Recording) Res.string.speech_stop_mic else Res.string.speech_mic)) }
                if (phase is ShadowPhase.Result || phase is ShadowPhase.Error) TextButton(onClick = { controller.retry(); play() }) { Text(stringResource(Res.string.retry)) }
                TextButton(onClick = { if (index < sentences.lastIndex) index++ else finish() }) { Text(stringResource(Res.string.speech_next)) }
                TextButton(onClick = ::finish) { Text(stringResource(Res.string.speech_finish)) }
            }
        }
    }
    if (summary) shadowTextSummary(attempts)?.let { value ->
        AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(Res.string.speech_summary)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.speech_sentences, value.sentences))
                Text(stringResource(Res.string.speech_score, value.accuracyPercent))
                value.goodPacePercent?.let { Text(stringResource(Res.string.speech_good_percent, it)) }
                value.weakest.forEach { weak ->
                    TextButton(onClick = {
                        val target = sentences.indexOf(weak.sentence)
                        if (target >= 0) { summary = false; if (index == target) { controller.retry(); play() } else index = target }
                    }) { Text("${weak.accuracyPercent}%  ${weak.sentence}", style = contentStyle(weak.sentence)) }
                }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.speech_finish)) } })
    }
}
