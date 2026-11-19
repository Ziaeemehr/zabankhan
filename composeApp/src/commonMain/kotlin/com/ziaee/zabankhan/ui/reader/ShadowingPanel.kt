package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.speech.PaceRating

import com.ziaee.zabankhan.speech.holdToRecord
import com.ziaee.zabankhan.speech.ShadowPhase

import com.ziaee.zabankhan.ui.theme.ReadingPalette

private val MatchedColor = Color(0xFF2E7D32)
private val MissedColor = Color(0xFFC62828)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShadowingPanel(
    state: ShadowingState,
    isPlaying: Boolean,
    palette: ReadingPalette,
    onPlayOriginal: () -> Unit,
    onReplayMine: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onPressStart: () -> Boolean,
    onPressEnd: () -> Unit,
    onPressCancel: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // French sentence always LTR, even in the Persian UI.
        CompositionLocalProvider(LocalLayoutDirection provides if (com.ziaee.zabankhan.lang.isRtlText(state.sentenceText)) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            val result = state.phase as? ShadowPhase.Result
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result == null) {
                    Text(state.sentenceText, color = palette.ink, style = MaterialTheme.typography.bodyLarge)
                } else {
                    ShadowingAlignment(result.alignment)
                }
            }
        }
        val status = when (val p = state.phase) {
            ShadowPhase.Recording -> stringResource(Res.string.shadowing_listening)
            ShadowPhase.Saving -> stringResource(Res.string.shadowing_recognizing)
            is ShadowPhase.Result -> stringResource(Res.string.shadowing_score, p.alignment.matched, p.alignment.total)
            ShadowPhase.Recorded -> stringResource(Res.string.shadowing_replay_mine)
            is ShadowPhase.Error -> stringResource(
                if (p.notHeard) Res.string.shadowing_not_heard else Res.string.shadowing_engine_failed
            )
            ShadowPhase.Playing -> ""
            ShadowPhase.Idle -> if (state.sentenceText.isEmpty()) stringResource(Res.string.shadowing_waiting_for_audio) else ""
        }
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.titleMedium, color = palette.accent,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
        }
        (state.phase as? ShadowPhase.Result)?.pace?.let { pace ->
            val ratio = ((pace.ratio * 10).toInt() / 10f).toString()
            Text(
                stringResource(
                    when (pace.rating) {
                        PaceRating.FAST -> Res.string.shadowing_pace_fast
                        PaceRating.GOOD -> Res.string.shadowing_pace_good
                        PaceRating.SLOW -> Res.string.shadowing_pace_slow
                    },
                    ratio
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (pace.rating == PaceRating.GOOD) MatchedColor else palette.inkFaded,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onPlayOriginal) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null); Spacer(Modifier.width(4.dp)); Text(stringResource(Res.string.shadowing_play_original))
            }
            TextButton(onClick = onReplayMine, enabled = state.hasRecording && state.phase != ShadowPhase.Playing &&
                state.phase != ShadowPhase.Recording && state.phase != ShadowPhase.Saving) {
                Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(4.dp)); Text(stringResource(Res.string.shadowing_replay_mine))
            }
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text(stringResource(Res.string.shadowing_retry))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            val recording = state.phase == ShadowPhase.Recording
            Surface(
                shape = CircleShape,
                color = if (recording) MissedColor else palette.accent.copy(alpha = if (isPlaying) 0.4f else 1f),
                modifier = Modifier.size(72.dp).holdToRecord(onPressStart, onPressEnd, onPressCancel)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Mic, stringResource(Res.string.shadowing_hold_to_speak), tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onNext) {
                    Text(stringResource(Res.string.shadowing_next)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}

/** Shared word result for reading and video shadowing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShadowingAlignment(alignment: com.ziaee.zabankhan.speech.AlignmentResult) {
    CompositionLocalProvider(LocalLayoutDirection provides if (com.ziaee.zabankhan.lang.isRtlText(alignment.words.joinToString(" ") { it.word })) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            alignment.words.forEach { word ->
                Text(word.word, color = if (word.matched) MatchedColor else MissedColor,
                    fontWeight = if (word.matched) FontWeight.Normal else FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}


private data class ShadowingState(val sentenceText: String, val phase: ShadowPhase, val hasRecording: Boolean)

/** Host wiring for the copied inline panel; practice storage remains shared with Watch. */
@Composable
internal fun ReaderShadowingPanel(container: com.ziaee.zabankhan.app.AppContainer,
    settings: com.ziaee.zabankhan.data.AppSettings, lang: com.ziaee.zabankhan.lang.LanguageSpec,
    textId: Long, sentences: List<String>, startIndex: Int, palette: ReadingPalette, onFinished: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val tts = androidx.compose.runtime.remember { com.ziaee.zabankhan.speech.createTts() }
    val recognizer = androidx.compose.runtime.remember { com.ziaee.zabankhan.speech.createSpeechRecognizer() }
    val controller = androidx.compose.runtime.remember {
        com.ziaee.zabankhan.speech.ShadowingController(scope, recognizer, { container.database.shadowAttempts().insert(it) })
    }
    val permission = com.ziaee.zabankhan.speech.rememberMicPermission()
    var index by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(startIndex.coerceIn(0, sentences.lastIndex.coerceAtLeast(0))) }
    val phase by controller.phase.collectAsState()
    val speaking by tts.isSpeaking.collectAsState()
    val sentence = sentences.getOrNull(index).orEmpty()
    fun play() {
        scope.launch {
            controller.preparePlayback()
            val started = kotlin.time.TimeSource.Monotonic.markNow()
            tts.setVoice(settings.ttsVoices[lang.code])
            tts.speak(sentence, lang.code, settings.speechRate,
                onError = { controller.playbackFailed() },
                onDone = { controller.played(started.elapsedNow().inWholeMilliseconds) })
        }
    }
    androidx.compose.runtime.LaunchedEffect(index) { controller.target(lang, textId, sentence); play() }
    androidx.compose.runtime.DisposableEffect(controller) { onDispose { controller.cancel(); tts.stop() } }
    ShadowingPanel(ShadowingState(sentence, phase, controller.hasRecording), speaking, palette,
        onPlayOriginal = ::play, onReplayMine = controller::replayMine, onRetry = { controller.retry(); play() },
        onNext = { if (index < sentences.lastIndex) index++ else onFinished() },
        onPressStart = { if (!permission.granted) { permission.request(); false } else controller.listen() },
        onPressEnd = controller::finishRecording, onPressCancel = controller::cancel)
}
