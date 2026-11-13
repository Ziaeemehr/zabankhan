package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.speech.edge.EdgeTts
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resume

object SpeechEngine {
    internal var current: SelectedTts? = null
    val onlineUnavailable = MutableStateFlow(false)
    fun configure(settings: AppSettings) { RecognitionSettings.engine = settings.speechRecognitionEngine; current?.configure(settings) }
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
    override val audioStatus get() = if (settings.voiceEngine == "device") device.audioStatus else online.audioStatus
    override val synthesisProgress get() = online.synthesisProgress
    override fun seekBy(deltaMs: Long): Boolean = settings.voiceEngine != "device" && online.seekBy(deltaMs)
    override fun setAudioDocument(owner: String?) = online.setAudioDocument(owner)
    override fun releaseAudioDocument(owner: String) = online.releaseAudioDocument(owner)
    override fun setAudioPinned(pinned: Boolean) = online.setAudioPinned(pinned)
    override suspend fun prepare(text: String, langCode: String, rate: Float): Result<Unit> {
        if (settings.voiceEngine == "device") return Result.failure(IllegalStateException("Device speech has no downloadable audio"))
        online.setVoice(voice)
        return online.prepare(text, langCode, rate)
    }
    fun configure(value: AppSettings) {
        if (value.voiceEngine != settings.voiceEngine) stop()
        settings = value
    }
    override fun voices(langCode: String) = if (settings.voiceEngine == "device") device.voices(langCode) else online.voices(langCode)
    override fun setVoice(id: String?) { voice = id }
    override fun prefetch(text: String, langCode: String, rate: Float) {
        if (settings.voiceEngine != "device") { online.setVoice(voice); online.prefetch(text, langCode, rate) }
    }
    override fun speak(text: String, langCode: String, rate: Float, onError: () -> Unit, onDone: () -> Unit) {
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
                    suspendCancellableCoroutine<Boolean> { continuation ->
                            device.speak(remaining, langCode, rate,
                                onError = { if (continuation.isActive) continuation.resume(false) },
                                onDone = { if (continuation.isActive) continuation.resume(true) })
                        }.also { completed = it }
                    } finally { tracking.cancelAndJoin() }
                }
            } finally {
                if (token == generation) { speaking.value = false; range.value = null }
            }
            if (token == generation) if (completed) onDone() else onError()
        }
    }
    override fun stop() {
        generation++; job?.cancel(); job = null
        online.stop(); device.stop(); range.value = null; speaking.value = false
    }
}
