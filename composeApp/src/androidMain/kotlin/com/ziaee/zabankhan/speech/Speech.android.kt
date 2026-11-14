package com.ziaee.zabankhan.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.TimeSource

internal lateinit var speechContext: Context
fun initializeSpeech(context: Context) { speechContext = context.applicationContext }
private val main = Handler(Looper.getMainLooper())
private class AndroidTts : Tts {
    private val range = MutableStateFlow<IntRange?>(null)
    private val speaking = MutableStateFlow(false)
    override val speakingRange = range.asStateFlow()
    override val isSpeaking = speaking.asStateFlow()
    private var ready = false
    private var failed = false
    private var pending: (() -> Unit)? = null
    private var done: (() -> Unit)? = null
    private var error: (() -> Unit)? = null
    private var voiceId: String? = null
    private var sequence = 0
    private val engine = TextToSpeech(speechContext) { status -> main.post {
        ready = status == TextToSpeech.SUCCESS; failed = !ready
        pending?.invoke(); pending = null
    } }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post { if (id == sequence.toString()) speaking.value = true } }
            override fun onDone(id: String?) = finish(id, true)
            @Deprecated("Platform callback") override fun onError(id: String?) = finish(id, false)
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) { main.post {
                if (id == sequence.toString() && end > start) range.value = start until end
            } }
        })
    }
    private fun finish(id: String?, success: Boolean) { main.post {
        if (id == sequence.toString()) {
            speaking.value = false; range.value = null
            val callback = if (success) done else error
            done = null; error = null; callback?.invoke()
        }
    } }
    override fun speak(text: String, langCode: String, rate: Float, onError: () -> Unit, onDone: () -> Unit) {
        val lang = learningSpeechLanguage(langCode)
        stop(); val token = sequence
        done = onDone; error = onError; speaking.value = true
        val start = {
            if (token == sequence) {
                if (failed || engine.setLanguage(Locale.forLanguageTag(lang.ttsLocale)) < 0) finish(token.toString(), false)
                else {
                    voiceId?.let { id -> engine.voices?.firstOrNull { it.name == id && it.locale.language == Locale.forLanguageTag(lang.ttsLocale).language }?.let { engine.voice = it } }
                    engine.setSpeechRate(rate.coerceIn(.5f, 2f))
                    if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, token.toString()) == TextToSpeech.ERROR) finish(token.toString(), false)
                }
            }
        }
        if (ready || failed) start() else pending = start
    }
    override fun stop() { sequence++; pending = null; done = null; error = null; engine.stop(); speaking.value = false; range.value = null }
    override fun voices(langCode: String): List<TtsVoice> {
        val language = Locale.forLanguageTag(learningSpeechLanguage(langCode).ttsLocale).language
        return engine.voices.orEmpty().filter { it.locale.language == language }.sortedBy { it.name }.map { TtsVoice(it.name, it.name) }
    }
    override fun setVoice(id: String?) { voiceId = id }
}
private val sharedTts: Tts by lazy { ActivityTts(AndroidTts()) }
actual fun createDeviceTts(): Tts = sharedTts

private class AndroidRecognizer : SpeechRecognizer {
    private var engine: AndroidSpeechEngine? = null
    private var released: CompletableDeferred<Unit>? = null
    private var recording: ShortArray? = null
    override val hasRecording get() = recording != null
    override suspend fun listenOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> = withContext(Dispatchers.Main.immediate) {
        val current = AndroidSpeechEngine(speechContext, learningSpeechLanguage(langCode).sttLocale)
        val end = CompletableDeferred<Unit>()
        engine = current; released = end; clearRecording()
        try {
            current.start()
            withTimeoutOrNull(maxMs) { end.await() }
            val result = withTimeout(15_000) { current.stop() }
            recording = result.pcm
            Result.success(RecognizedSpeech(result.transcript, result.durationMs))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        finally { current.release(); if (engine === current) { engine = null; released = null } }
    }
    override fun stop() { released?.complete(Unit) }
    override fun replay() { recording?.let(PcmPlayer::play) }
    override fun stopReplay() { PcmPlayer.stop() }
    override fun clearRecording() { stopReplay(); recording = null }
}
private class SelectedRecognizer : SpeechRecognizer {
    private var current: SpeechRecognizer? = null
    override val hasRecording get() = current?.hasRecording == true
    override suspend fun listenOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> {
        current?.clearRecording()
        val language = learningSpeechLanguage(langCode)
        // Offline selected but model not downloaded yet: use the system recognizer instead of failing.
        val next = if (RecognitionSettings.engine == "offline" && language.offlineSpeechModel != null && VoskModelManager(language).isInstalled()) VoskRecognizer() else AndroidRecognizer()
        current = next
        return next.listenOnce(langCode, maxMs)
    }
    override fun stop() { current?.stop() }
    override fun replay() { current?.replay() }
    override fun stopReplay() { current?.stopReplay() }
    override fun clearRecording() { current?.clearRecording() }
}
actual fun createSpeechRecognizer(): SpeechRecognizer = SelectedRecognizer()
private class AndroidVoiceRecorder : SpeechRecognizer {
    private var recorder: MicRecorder? = null
    private var stopped: CompletableDeferred<Unit>? = null
    private var recording: ShortArray? = null
    override val hasRecording get() = recording != null
    override suspend fun listenOnce(langCode: String, maxMs: Long) = recordOnce(langCode, maxMs)
    override suspend fun recordOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> = withContext(Dispatchers.IO) {
        val mic = MicRecorder()
        val end = CompletableDeferred<Unit>()
        stopped?.complete(Unit) // a second call ends the first recording instead of orphaning it
        recorder = mic; stopped = end; clearRecording()
        try {
            mic.start()
            withTimeoutOrNull(maxMs.coerceIn(1, 30_000)) { end.await() }
            val pcm = mic.stop()
            recording = pcm
            Result.success(RecognizedSpeech("", durationMsOf(pcm.size)))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        finally { mic.cancel(); if (recorder === mic) { recorder = null; stopped = null } }
    }
    override fun stop() { stopped?.complete(Unit) }
    override fun replay() { recording?.let(PcmPlayer::play) }
    override fun stopReplay() { PcmPlayer.stop() }
    override fun clearRecording() { stopReplay(); recording = null }
}
actual fun createVoiceRecorder(): SpeechRecognizer = AndroidVoiceRecorder()
actual fun recognitionSupport(language: com.ziaee.zabankhan.lang.LanguageSpec) = RecognitionSupport(
    downloadableModels = true,
    systemAvailable = android.speech.SpeechRecognizer.isRecognitionAvailable(speechContext),
    onDeviceAvailable = language.offlineSpeechModel != null,
)
@Composable actual fun rememberMicPermission(): MicPermission {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    return object : MicPermission {
        override val granted = granted
        override fun request() { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }
}
@Composable actual fun rememberVoiceRecordingPermission(): MicPermission = rememberMicPermission()

actual fun createLocalTts(): Tts? = com.ziaee.zabankhan.speech.local.LocalTts(speechContext, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
actual val localTtsSupported: Boolean = true
@Composable actual fun LocalTtsSettings(language: com.ziaee.zabankhan.lang.LanguageSpec) = com.ziaee.zabankhan.speech.local.LocalTtsSettings(language)
