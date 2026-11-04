package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.data.ShadowAttempt
import com.ziaee.zabankhan.data.nowMs
import com.ziaee.zabankhan.lang.LanguageSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface ShadowPhase {
    data object Idle : ShadowPhase
    data object Playing : ShadowPhase
    data object Recording : ShadowPhase
    data object Saving : ShadowPhase
    data class Result(val alignment: AlignmentResult, val pace: PaceResult?) : ShadowPhase
    data class Error(val notHeard: Boolean) : ShadowPhase
}
/** One target owns one recognition job; changing/retrying a target invalidates late results. */
class ShadowingController(
    private val scope: CoroutineScope,
    private val recognizer: SpeechRecognizer,
    private val saveAttempt: suspend (ShadowAttempt) -> Unit,
    private val now: () -> Long = ::nowMs,
) {
    private val mutablePhase = MutableStateFlow<ShadowPhase>(ShadowPhase.Idle)
    val phase: StateFlow<ShadowPhase> = mutablePhase.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    private var expectedMs = 0L
    private var sentence = ""
    private var textId: Long? = null
    private var lang: LanguageSpec? = null
    fun target(lang: LanguageSpec, textId: Long?, sentence: String) {
        learningSpeechLanguage(lang.code)
        cancel()
        this.lang = lang; this.textId = textId; this.sentence = sentence; expectedMs = 0
    }
    fun playing() { cancel(); mutablePhase.value = ShadowPhase.Playing }
    suspend fun preparePlayback() {
        val previous = job
        cancel()
        previous?.join() // native microphone/audio-session teardown must precede new audio
        mutablePhase.value = ShadowPhase.Playing
    }
    fun playbackFailed() { mutablePhase.value = ShadowPhase.Error(false) }
    fun played(durationMs: Long) { expectedMs = durationMs; mutablePhase.value = ShadowPhase.Idle }
    fun listen(): Boolean {
        val language = lang ?: return false
        if (phase.value in listOf(ShadowPhase.Playing, ShadowPhase.Recording, ShadowPhase.Saving)) return false
        val token = ++generation
        val target = sentence
        val id = textId
        mutablePhase.value = ShadowPhase.Recording
        job = scope.launch {
            try {
                val recording = recognizer.listenOnce(language.code).getOrThrow()
                if (token != generation) return@launch
                if (recording.durationMs < 300) { mutablePhase.value = ShadowPhase.Idle; return@launch }
                if (recording.text.isBlank()) { mutablePhase.value = ShadowPhase.Error(true); return@launch }
                val alignment = TranscriptAligner.align(target, recording.text, language)
                val pace = if (expectedMs > 0) {
                    val ratio = recording.durationMs.toFloat() / expectedMs
                    PaceResult(ratio, PaceAnalyzer.rate(ratio))
                } else null
                mutablePhase.value = ShadowPhase.Saving
                saveAttempt(ShadowAttempt(lang = language.code, textId = id, sentence = target,
                    matched = alignment.matched, total = alignment.total, paceRatio = pace?.ratio, timestampMs = now()))
                if (token == generation) mutablePhase.value = ShadowPhase.Result(alignment, pace)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (token == generation) mutablePhase.value = ShadowPhase.Error(false) }
        }
        return true
    }
    fun cancel() { generation++; job?.cancel(); recognizer.stop(); mutablePhase.value = ShadowPhase.Idle }
    fun retry() = cancel()
}
