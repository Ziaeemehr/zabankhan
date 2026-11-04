@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.speech

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import platform.AVFAudio.*
import platform.Foundation.*
import platform.Speech.*
import platform.darwin.NSObject
import kotlin.coroutines.resume

private class IosTts : Tts {
    private val engine = AVSpeechSynthesizer()
    private val range = MutableStateFlow<IntRange?>(null)
    private val speaking = MutableStateFlow(false)
    override val speakingRange = range.asStateFlow()
    override val isSpeaking = speaking.asStateFlow()
    private var current: AVSpeechUtterance? = null
    private var done: (() -> Unit)? = null
    private var voiceId: String? = null

    override fun speak(text: String, langCode: String, rate: Float, onDone: () -> Unit) {
        val lang = learningSpeechLanguage(langCode)
        stop()
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.voice = voiceId?.let { AVSpeechSynthesisVoice.voiceWithIdentifier(it) }
            ?.takeIf { it.language.substringBefore('-') == lang.code }
            ?: AVSpeechSynthesisVoice.voiceWithLanguage(lang.ttsLocale)
        utterance.rate = (AVSpeechUtteranceDefaultSpeechRate * rate).coerceIn(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceMaximumSpeechRate)
        current = utterance; done = onDone; speaking.value = true
        engine.speakUtterance(utterance)
    }
    override fun stop() { current = null; done = null; engine.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate); range.value = null; speaking.value = false }
    override fun voices(langCode: String): List<TtsVoice> {
        val code = learningSpeechLanguage(langCode).code
        return AVSpeechSynthesisVoice.speechVoices().filterIsInstance<AVSpeechSynthesisVoice>().filter { it.language.substringBefore('-') == code }.map { TtsVoice(it.identifier, it.name) }
    }
    override fun setVoice(id: String?) { voiceId = id }
    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, willSpeakRangeOfSpeechString: CValue<NSRange>, utterance: AVSpeechUtterance) {
        if (utterance == current) willSpeakRangeOfSpeechString.useContents { range.value = location.toInt() until (location + length).toInt() }
    }
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
        if (didFinishSpeechUtterance == current) {
            current = null; range.value = null; speaking.value = false
            val callback = done; done = null; callback?.invoke()
        }
    }
    }
    init { engine.delegate = delegate }
}
private val sharedTts: Tts by lazy { IosTts() }
actual fun createTts(): Tts = sharedTts

private suspend fun authorizeMic(): Boolean {
    val speech = suspendCancellableCoroutine { continuation ->
        SFSpeechRecognizer.requestAuthorization { status -> if (continuation.isActive) continuation.resume(status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) }
    }
    if (!speech) return false
    return suspendCancellableCoroutine { continuation ->
        AVAudioSession.sharedInstance().requestRecordPermission { granted -> if (continuation.isActive) continuation.resume(granted) }
    }
}
private class IosRecognizer : SpeechRecognizer {
    private var audio: AVAudioEngine? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var tapped = false
    override suspend fun listenOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> = withContext(Dispatchers.Main.immediate) {
        val lang = learningSpeechLanguage(langCode)
        val session = AVAudioSession.sharedInstance()
        val previousCategory = session.category
        val previousMode = session.mode
        val previousOptions = session.categoryOptions
        var latest: RecognizedSpeech? = null
        try {
            check(authorizeMic()) { "Microphone or speech permission denied" }
            val recognizer = SFSpeechRecognizer(NSLocale(lang.sttLocale)) ?: error("Speech recognition unavailable")
            check(recognizer.available) { "Speech recognition unavailable" }
            withTimeout(maxMs) {
                suspendCancellableCoroutine { continuation ->
                    session.setCategory(AVAudioSessionCategoryRecord, error = null)
                    session.setMode(AVAudioSessionModeMeasurement, error = null)
                    session.setActive(true, error = null)
                    val engine = AVAudioEngine().also { audio = it }
                    val buffer = SFSpeechAudioBufferRecognitionRequest().also { request = it }
                    buffer.shouldReportPartialResults = true
                    if (recognizer.supportsOnDeviceRecognition) buffer.requiresOnDeviceRecognition = true
                    task = recognizer.recognitionTaskWithRequest(buffer) { result, error ->
                        if (result != null) {
                            val segments = result.bestTranscription.segments.filterIsInstance<SFTranscriptionSegment>()
                            val first = segments.firstOrNull()?.timestamp ?: 0.0
                            val last = segments.lastOrNull()?.let { it.timestamp + it.duration } ?: first
                            latest = RecognizedSpeech(result.bestTranscription.formattedString, ((last - first) * 1000).toLong())
                        }
                        if (continuation.isActive && (error != null || result?.final == true)) {
                            val recognized = latest
                            continuation.resume(if (recognized != null) Result.success(recognized)
                                else Result.failure(IllegalStateException(error?.localizedDescription ?: "Recognition failed")))
                        }
                    }
                    val input = engine.inputNode
                    input.installTapOnBus(0u, bufferSize = 1024u, format = input.outputFormatForBus(0u)) { pcm, _ -> if (pcm != null) buffer.appendAudioPCMBuffer(pcm) }
                    tapped = true
                    engine.prepare()
                    if (!engine.startAndReturnError(null)) continuation.resume(Result.failure(IllegalStateException("Microphone unavailable")))
                }
            }
        } catch (e: TimeoutCancellationException) { latest?.let { Result.success(it) } ?: Result.failure(e) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        finally {
            audio?.stop()
            if (tapped) audio?.inputNode?.removeTapOnBus(0u)
            tapped = false; request?.endAudio(); task?.cancel()
            audio = null; request = null; task = null
            session.setActive(false, error = null)
            session.setCategory(previousCategory, mode = previousMode, options = previousOptions, error = null)
        }
    }
    override fun stop() { audio?.stop(); request?.endAudio() }
}
actual fun createSpeechRecognizer(): SpeechRecognizer = IosRecognizer()
@Composable actual fun rememberMicPermission(): MicPermission {
    var granted by remember { mutableStateOf(SFSpeechRecognizer.authorizationStatus() == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized && AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted) }
    val scope = rememberCoroutineScope()
    return object : MicPermission {
        override val granted = granted
        override fun request() { scope.launch { granted = authorizeMic() } }
    }
}
