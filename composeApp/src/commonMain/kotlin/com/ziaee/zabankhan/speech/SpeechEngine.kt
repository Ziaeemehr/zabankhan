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
class SelectedTts(private val online: EdgeTts, private val device: Tts, private val scope: CoroutineScope, private val local: Tts? = null) : Tts {
    private val range = MutableStateFlow<IntRange?>(null)
    private val speaking = MutableStateFlow(false)
    override val speakingRange = range.asStateFlow()
    override val isSpeaking = speaking.asStateFlow()
    private var settings = AppSettings()
    private var voice: String? = null
    private var job: Job? = null
    private var generation = 0
    private val selected get() = when (settings.voiceEngine) { "device" -> device; "local" -> local ?: device; else -> online }
    override val audioStatus get() = selected.audioStatus
    override val synthesisProgress get() = selected.synthesisProgress
    override fun seekBy(deltaMs: Long): Boolean = settings.voiceEngine == "online" && online.seekBy(deltaMs)
    override fun setAudioDocument(owner: String?) { if (settings.voiceEngine == "online") online.setAudioDocument(owner) }
    override fun releaseAudioDocument(owner: String) = online.releaseAudioDocument(owner)
    override fun setAudioPinned(pinned: Boolean) { if (settings.voiceEngine == "online") online.setAudioPinned(pinned) }
    override suspend fun prepare(text: String, langCode: String, rate: Float): Result<Unit> {
        if (settings.voiceEngine == "device" || settings.voiceEngine == "local") return Result.failure(IllegalStateException("This voice engine has no downloadable audio"))
        online.setVoice(voice)
        return online.prepare(text, langCode, rate)
    }
    fun configure(value: AppSettings) {
        // configure runs on every App recomposition; only an engine switch invalidates the voice id.
        if (value.voiceEngine != settings.voiceEngine) { stop(); voice = null }
        settings = value
    }
    override fun voices(langCode: String) = selected.voices(langCode)
    override fun setVoice(id: String?) { voice = id }
    override fun prefetch(text: String, langCode: String, rate: Float) {
        if (settings.voiceEngine == "online") { online.setVoice(voice); online.prefetch(text, langCode, rate) }
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
                if (settings.voiceEngine == "local") {
                    val active = local ?: device
                    val tracking = launch { active.speakingRange.collect { range.value = it } }
                    try {
                        suspendCancellableCoroutine<Boolean> { continuation ->
                            active.setVoice(requestedVoice?.takeIf { id -> active.voices(langCode).any { it.id == id } })
                            active.speak(text, langCode, rate,
                                onError = { if (continuation.isActive) continuation.resume(false) },
                                onDone = { if (continuation.isActive) continuation.resume(true) })
                        }.also { completed = it }
                    } finally { tracking.cancelAndJoin() }
                } else if (settings.voiceEngine == "online") {
                    online.setVoice(requestedVoice)
                    val tracking = launch { online.speakingRange.collect { range.value = it } }
                    try {
                        online.playText(text, langCode, rate) { resumeAt = it }
                        completed = true
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { SpeechEngine.onlineUnavailable.value = true }
                    finally { tracking.cancelAndJoin(); range.value = null }
                }
                if (!completed && settings.voiceEngine != "local") {
                    val remaining = text.substring(resumeAt)
                    // Device and online identifiers have different formats; validate against its catalog.
                    val active = device
                    active.setVoice(requestedVoice?.takeIf { id -> active.voices(langCode).any { it.id == id } })
                    val tracking = launch { active.speakingRange.collect { localRange -> range.value = localRange?.let { (it.first + resumeAt)..(it.last + resumeAt) } } }
                    try {
                    suspendCancellableCoroutine<Boolean> { continuation ->
                            active.speak(remaining, langCode, rate,
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
        online.stop(); device.stop(); local?.stop(); range.value = null; speaking.value = false
    }
}
