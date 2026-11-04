package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.speech.edge.EdgeTts
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resume

object SpeechEngine {
    internal var current: SelectedTts? = null
    val onlineUnavailable = MutableStateFlow(false)
    fun configure(settings: AppSettings) { current?.configure(settings) }
}

/** A single router is used by every speech caller, including dictionary and shadowing. */
class SelectedTts(private val online: EdgeTts, private val device: Tts, private val scope: CoroutineScope) : Tts {
    private val range = MutableStateFlow<IntRange?>(null)
    private val speaking = MutableStateFlow(false)
    override val speakingRange = range.asStateFlow()
    override val isSpeaking = speaking.asStateFlow()
    private var settings = AppSettings()
    private var voice: String? = null
    private var job: Job? = null
    private var generation = 0
    fun configure(value: AppSettings) {
        if (value.voiceEngine != settings.voiceEngine) stop()
        settings = value
    }
    override fun voices(langCode: String) = if (settings.voiceEngine == "device") device.voices(langCode) else online.voices(langCode)
    override fun setVoice(id: String?) { voice = id }
    override fun prefetch(text: String, langCode: String, rate: Float) {
        if (settings.voiceEngine != "device") { online.setVoice(voice); online.prefetch(text, langCode, rate) }
    }
    override fun speak(text: String, langCode: String, rate: Float, onDone: () -> Unit) {
        learningSpeechLanguage(langCode)
        val previous = job
        stop()
        SpeechActivity.speaking(langCode)
        val token = generation
        val requestedVoice = voice
        speaking.value = true
        job = scope.launch {
            previous?.join()
            ensureActive()
            var completed = false
            var resumeAt = 0
            try {
                if (settings.voiceEngine != "device") {
                    online.setVoice(requestedVoice)
                    val tracking = launch { online.speakingRange.collect { range.value = it } }
                    try {
                        online.playText(text, langCode, rate) { resumeAt = it }
                        completed = true
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { SpeechEngine.onlineUnavailable.value = true }
                    finally { tracking.cancelAndJoin(); range.value = null }
                }
                if (!completed) {
                    val remaining = text.substring(resumeAt)
                    // Device and online identifiers have different formats; validate against its catalog.
                    device.setVoice(requestedVoice?.takeIf { id -> device.voices(langCode).any { it.id == id } })
                    val tracking = launch { device.speakingRange.collect { local -> range.value = local?.let { (it.first + resumeAt)..(it.last + resumeAt) } } }
                    try {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            device.speak(remaining, langCode, rate) { if (continuation.isActive) continuation.resume(Unit) }
                        }
                    } finally { tracking.cancelAndJoin() }
                }
                completed = true
            } finally {
                if (token == generation) { speaking.value = false; range.value = null }
            }
            if (completed && token == generation) onDone()
        }
    }
    override fun stop() {
        generation++; job?.cancel(); job = null
        online.stop(); device.stop(); range.value = null; speaking.value = false
    }
}
