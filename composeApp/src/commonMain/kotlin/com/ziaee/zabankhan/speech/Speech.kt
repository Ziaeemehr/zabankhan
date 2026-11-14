package com.ziaee.zabankhan.speech

import androidx.compose.runtime.Composable
import com.ziaee.zabankhan.lang.Languages
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class TtsVoice(val id: String, val name: String)
enum class SpeechAudioStatus { IDLE, PREPARING, READY, ERROR }

private val idleAudioStatus = MutableStateFlow(SpeechAudioStatus.IDLE)
private val idleSynthesisProgress = MutableStateFlow<Float?>(null)

interface Tts {
    val audioStatus: StateFlow<SpeechAudioStatus> get() = idleAudioStatus
    val synthesisProgress: StateFlow<Float?> get() = idleSynthesisProgress
    /** Device speech APIs do not expose a seekable audio timeline. */
    fun seekBy(deltaMs: Long): Boolean = false
    suspend fun prepare(text: String, langCode: String, rate: Float): Result<Unit> = Result.success(Unit)
    fun setAudioDocument(owner: String?) {}
    fun releaseAudioDocument(owner: String) { setAudioDocument(null) }
    fun setAudioPinned(pinned: Boolean) {}
    val speakingRange: StateFlow<IntRange?>
    val isSpeaking: StateFlow<Boolean>
    fun speak(text: String, langCode: String, rate: Float, onDone: () -> Unit) =
        speak(text, langCode, rate, onError = {}, onDone = onDone)
    fun speak(text: String, langCode: String, rate: Float, onError: () -> Unit, onDone: () -> Unit)
    fun stop()
    fun voices(langCode: String): List<TtsVoice>
    fun setVoice(id: String?)
    /** Warm caches for text likely spoken next (online voice); no-op for device voices. */
    fun prefetch(text: String, langCode: String, rate: Float) {}
}
expect fun createDeviceTts(): Tts
expect fun createLocalTts(): Tts?
fun createTts(): Tts = SpeechEngine.current ?: createDeviceTts()

data class RecognizedSpeech(val text: String, val durationMs: Long)
interface SpeechRecognizer {
    suspend fun listenOnce(langCode: String, maxMs: Long = 15000): Result<RecognizedSpeech>
    suspend fun recordOnce(langCode: String, maxMs: Long = 15000): Result<RecognizedSpeech> =
        Result.failure(UnsupportedOperationException("Recording is unavailable"))
    fun stop()
    val hasRecording: Boolean get() = false
    fun replay() {}
    fun stopReplay() {}
    fun clearRecording() {}
}
expect fun createSpeechRecognizer(): SpeechRecognizer
expect fun createVoiceRecorder(): SpeechRecognizer
interface MicPermission {
    val granted: Boolean
    fun request()
}
@Composable expect fun rememberMicPermission(): MicPermission
@Composable expect fun rememberVoiceRecordingPermission(): MicPermission

fun learningSpeechLanguage(code: String) = requireNotNull(Languages.learnable.firstOrNull { it.code == code }) {
    "Speech is available only for learning languages"
}
