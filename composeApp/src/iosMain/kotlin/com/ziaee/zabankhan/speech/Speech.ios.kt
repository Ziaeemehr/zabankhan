@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.speech

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import platform.AVFAudio.*
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
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
    private var error: (() -> Unit)? = null
    private var voiceId: String? = null

    override fun speak(text: String, langCode: String, rate: Float, onError: () -> Unit, onDone: () -> Unit) {
        val lang = learningSpeechLanguage(langCode)
        stop()
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.voice = voiceId?.let { AVSpeechSynthesisVoice.voiceWithIdentifier(it) }
            ?.takeIf { it.language.substringBefore('-') == lang.code }
            ?: bestVoice(lang.ttsLocale, lang.code)
        utterance.rate = (AVSpeechUtteranceDefaultSpeechRate * rate).coerceIn(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceMaximumSpeechRate)
        current = utterance; done = onDone; error = onError; speaking.value = true
        engine.speakUtterance(utterance)
    }
    override fun stop() { current = null; done = null; error = null; engine.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate); range.value = null; speaking.value = false }
    override fun voices(langCode: String): List<TtsVoice> {
        val code = learningSpeechLanguage(langCode).code
        return AVSpeechSynthesisVoice.speechVoices().filterIsInstance<AVSpeechSynthesisVoice>().filter { it.language.substringBefore('-') == code }
            .sortedByDescending { it.quality }
            .map { TtsVoice(it.identifier, it.name + qualityLabel(it.quality)) }
    }
    override fun setVoice(id: String?) { voiceId = id }
    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, willSpeakRangeOfSpeechString: CValue<NSRange>, utterance: AVSpeechUtterance) {
        if (utterance == current) willSpeakRangeOfSpeechString.useContents { range.value = location.toInt() until (location + length).toInt() }
    }
    @ObjCSignatureOverride
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
        if (didFinishSpeechUtterance == current) {
            current = null; range.value = null; speaking.value = false
            val callback = done; done = null; error = null; callback?.invoke()
        }
    }
    @ObjCSignatureOverride
    override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didCancelSpeechUtterance: AVSpeechUtterance) {
        if (didCancelSpeechUtterance == current) {
            current = null; range.value = null; speaking.value = false
            val callback = error; done = null; error = null; callback?.invoke()
        }
    }
    }
    init { engine.delegate = delegate }
}
private val sharedTts: Tts by lazy { ActivityTts(IosTts()) }
actual fun createDeviceTts(): Tts = sharedTts

private suspend fun authorizeMic(): Boolean {
    if (SFSpeechRecognizer.authorizationStatus() == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized &&
        AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted) return true
    val speech = suspendCancellableCoroutine { continuation ->
        SFSpeechRecognizer.requestAuthorization { status -> if (continuation.isActive) continuation.resume(status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) }
    }
    if (!speech) return false
    return suspendCancellableCoroutine { continuation ->
        AVAudioSession.sharedInstance().requestRecordPermission { granted -> if (continuation.isActive) continuation.resume(granted) }
    }
}
actual fun recognitionSupport(language: com.ziaee.zabankhan.lang.LanguageSpec): RecognitionSupport {
    val recognizer = SFSpeechRecognizer(NSLocale(language.sttLocale))
    return RecognitionSupport(false, recognizer != null, recognizer?.supportsOnDeviceRecognition == true)
}
actual suspend fun changeOfflineModel(directory: String, change: () -> Unit) { change() }

private class IosRecognizer : SpeechRecognizer {
    private var audio: AVAudioEngine? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var tapped = false
    private var recordedFile: AVAudioFile? = null
    private var replayPlayer: AVAudioPlayer? = null
    private val recordingUrl = NSURL.fileURLWithPath("${com.ziaee.zabankhan.pdf.AppFiles.cacheDir}/shadow-${NSUUID().UUIDString}.caf")
    private var recorded = false
    override val hasRecording get() = recorded
    override fun replay() {
        if (!recorded) return
        stopReplay()
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)
        replayPlayer = AVAudioPlayer(contentsOfURL = recordingUrl, error = null).also { it.play() }
    }
    override fun stopReplay() { replayPlayer?.stop(); replayPlayer = null }
    override fun clearRecording() {
        stopReplay(); recorded = false
        NSFileManager.defaultManager.removeItemAtURL(recordingUrl, error = null)
    }
    override suspend fun listenOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> = withContext(Dispatchers.Main.immediate) {
        val lang = learningSpeechLanguage(langCode)
        val session = AVAudioSession.sharedInstance()
        val previousCategory = session.category
        val previousMode = session.mode
        val previousOptions = session.categoryOptions
        clearRecording()
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
                    val offline = RecognitionSettings.engine == "offline"
                    check(!offline || recognizer.supportsOnDeviceRecognition) { "On-device recognition unavailable for this language" }
                    buffer.requiresOnDeviceRecognition = offline
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
                    val format = input.outputFormatForBus(0u)
                    check(format.sampleRate > 0 && format.channelCount > 0u) { "Microphone unavailable" }
                    recordedFile = AVAudioFile(forWriting = recordingUrl, settings = format.settings, error = null)
                    input.installTapOnBus(0u, bufferSize = 1024u, format = format) { pcm, _ ->
                        if (pcm != null) {
                            buffer.appendAudioPCMBuffer(pcm)
                            if (recordedFile?.writeFromBuffer(pcm, error = null) == true) recorded = true
                        }
                    }
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
            recordedFile = null
            tapped = false; request?.endAudio(); task?.cancel()
            audio = null; request = null; task = null
            session.setActive(false, error = null)
            session.setCategory(previousCategory, mode = previousMode, options = previousOptions, error = null)
        }
    }
    override fun stop() { audio?.stop(); request?.endAudio() }
}
actual fun createSpeechRecognizer(): SpeechRecognizer = IosRecognizer()
private class IosVoiceRecorder : SpeechRecognizer {
    private val recordingUrl get() = NSURL.fileURLWithPath("${com.ziaee.zabankhan.pdf.AppFiles.cacheDir}/voice-${NSUUID().UUIDString}.m4a")
    private var recorder: AVAudioRecorder? = null
    private var stopped: CompletableDeferred<Unit>? = null
    private var file: NSURL? = null
    override val hasRecording get() = file != null
    override suspend fun listenOnce(langCode: String, maxMs: Long) = recordOnce(langCode, maxMs)
    override suspend fun recordOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> = withContext(Dispatchers.Main.immediate) {
        val session = AVAudioSession.sharedInstance()
        val oldCategory = session.category; val oldMode = session.mode; val oldOptions = session.categoryOptions
        clearRecording()
        var output: NSURL? = null
        var succeeded = false
        try {
            check(authorizeVoiceRecording()) { "Microphone permission denied" }
            session.setCategory(AVAudioSessionCategoryRecord, error = null)
            session.setMode(AVAudioSessionModeMeasurement, error = null)
            session.setActive(true, error = null)
            val path = recordingUrl; output = path
            val settings = mapOf<Any?, Any?>(AVFormatIDKey to kAudioFormatMPEG4AAC, AVSampleRateKey to 16000.0,
                AVNumberOfChannelsKey to 1, AVEncoderAudioQualityKey to AVAudioQualityHigh)
            val native = AVAudioRecorder(uRL = path, settings = settings, error = null) ?: error("Microphone unavailable")
            check(native.prepareToRecord() && native.record()) { "Microphone unavailable" }
            val end = CompletableDeferred<Unit>(); recorder = native; stopped = end
            withTimeoutOrNull(maxMs.coerceAtLeast(1)) { end.await() }
            val duration = (native.currentTime * 1000).toLong().coerceAtLeast(0)
            native.stop(); file = path; succeeded = true
            Result.success(RecognizedSpeech("", duration))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        finally {
            recorder?.stop(); recorder = null; stopped = null
            if (!succeeded) output?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
            session.setActive(false, error = null)
            session.setCategory(oldCategory, mode = oldMode, options = oldOptions, error = null)
        }
    }
    override fun stop() { stopped?.complete(Unit) }
    private var replayPlayer: AVAudioPlayer? = null
    override fun replay() { stopReplay(); file?.let { path ->
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)
        replayPlayer = AVAudioPlayer(contentsOfURL = path, error = null).also { it.play() }
    } }
    override fun stopReplay() { replayPlayer?.stop(); replayPlayer = null }
    override fun clearRecording() { stopReplay(); file?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }; file = null }
}
actual fun createVoiceRecorder(): SpeechRecognizer = IosVoiceRecorder()
private suspend fun authorizeVoiceRecording(): Boolean {
    val session = AVAudioSession.sharedInstance()
    if (session.recordPermission == AVAudioSessionRecordPermissionGranted) return true
    return suspendCancellableCoroutine { continuation ->
        session.requestRecordPermission { granted -> if (continuation.isActive) continuation.resume(granted) }
    }
}
@Composable actual fun rememberMicPermission(): MicPermission {
    var granted by remember { mutableStateOf(SFSpeechRecognizer.authorizationStatus() == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized && AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted) }
    val scope = rememberCoroutineScope()
    return object : MicPermission {
        override val granted = granted
        override fun request() { scope.launch { granted = authorizeMic() } }
    }
}
@Composable actual fun rememberVoiceRecordingPermission(): MicPermission {
    var granted by remember { mutableStateOf(AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted) }
    val scope = rememberCoroutineScope()
    return object : MicPermission {
        override val granted = granted
        override fun request() { scope.launch { granted = authorizeVoiceRecording() } }
    }
}

// The default voice for a language is the low-quality "compact" one; prefer Premium/Enhanced when installed.
private fun bestVoice(locale: String, code: String): AVSpeechSynthesisVoice? {
    val all = AVSpeechSynthesisVoice.speechVoices().filterIsInstance<AVSpeechSynthesisVoice>()
    fun pick(match: (AVSpeechSynthesisVoice) -> Boolean) = all.filter(match).maxByOrNull { it.quality }
    return pick { it.language == locale } ?: pick { it.language.substringBefore('-') == code } ?: AVSpeechSynthesisVoice.voiceWithLanguage(locale)
}

private fun qualityLabel(q: AVSpeechSynthesisVoiceQuality): String = when (q) {
    AVSpeechSynthesisVoiceQualityPremium -> " (Premium)"
    AVSpeechSynthesisVoiceQualityEnhanced -> " (Enhanced)"
    else -> ""
}
