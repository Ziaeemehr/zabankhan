package com.ziaee.zabankhan.speech

import androidx.compose.runtime.Composable
import com.ziaee.zabankhan.lang.Languages
import kotlinx.coroutines.flow.StateFlow

data class TtsVoice(val id: String, val name: String)
interface Tts {
    val speakingRange: StateFlow<IntRange?>
    val isSpeaking: StateFlow<Boolean>
    fun speak(text: String, langCode: String, rate: Float, onDone: () -> Unit)
    fun stop()
    fun voices(langCode: String): List<TtsVoice>
    fun setVoice(id: String?)
    /** Warm caches for text likely spoken next (online voice); no-op for device voices. */
    fun prefetch(text: String, langCode: String, rate: Float) {}
}
expect fun createDeviceTts(): Tts
fun createTts(): Tts = SpeechEngine.current ?: createDeviceTts()

data class RecognizedSpeech(val text: String, val durationMs: Long)
interface SpeechRecognizer {
    suspend fun listenOnce(langCode: String, maxMs: Long = 15000): Result<RecognizedSpeech>
    fun stop()
}
expect fun createSpeechRecognizer(): SpeechRecognizer
interface MicPermission {
    val granted: Boolean
    fun request()
}
@Composable expect fun rememberMicPermission(): MicPermission

fun learningSpeechLanguage(code: String) = requireNotNull(Languages.learnable.firstOrNull { it.code == code }) {
    "Speech is available only for learning languages"
}
