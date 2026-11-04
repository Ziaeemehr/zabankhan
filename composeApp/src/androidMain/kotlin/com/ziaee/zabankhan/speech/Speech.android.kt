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

private lateinit var appContext: Context
fun initializeSpeech(context: Context) { appContext = context.applicationContext }
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
    private var voiceId: String? = null
    private var sequence = 0
    private val engine = TextToSpeech(appContext) { status -> main.post {
        ready = status == TextToSpeech.SUCCESS; failed = !ready
        pending?.invoke(); pending = null
    } }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post { if (id == sequence.toString()) speaking.value = true } }
            override fun onDone(id: String?) = finish(id)
            @Deprecated("Platform callback") override fun onError(id: String?) = finish(id)
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) { main.post {
                if (id == sequence.toString() && end > start) range.value = start until end
            } }
        })
    }
    private fun finish(id: String?) { main.post {
        if (id == sequence.toString()) {
            speaking.value = false; range.value = null
            val callback = done; done = null; callback?.invoke()
        }
    } }
    override fun speak(text: String, langCode: String, rate: Float, onDone: () -> Unit) {
        val lang = learningSpeechLanguage(langCode)
        stop(); val token = sequence
        done = onDone; speaking.value = true
        val start = {
            if (token == sequence) {
                if (failed || engine.setLanguage(Locale.forLanguageTag(lang.ttsLocale)) < 0) finish(token.toString())
                else {
                    voiceId?.let { id -> engine.voices?.firstOrNull { it.name == id && it.locale.language == Locale.forLanguageTag(lang.ttsLocale).language }?.let { engine.voice = it } }
                    engine.setSpeechRate(rate.coerceIn(.5f, 2f))
                    if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, token.toString()) == TextToSpeech.ERROR) finish(token.toString())
                }
            }
        }
        if (ready || failed) start() else pending = start
    }
    override fun stop() { sequence++; pending = null; done = null; engine.stop(); speaking.value = false; range.value = null }
    override fun voices(langCode: String): List<TtsVoice> {
        val language = Locale.forLanguageTag(learningSpeechLanguage(langCode).ttsLocale).language
        return engine.voices.orEmpty().filter { it.locale.language == language }.sortedBy { it.name }.map { TtsVoice(it.name, it.name) }
    }
    override fun setVoice(id: String?) { voiceId = id }
}
private val sharedTts: Tts by lazy { ActivityTts(AndroidTts()) }
actual fun createTts(): Tts = sharedTts

private class AndroidRecognizer : SpeechRecognizer {
    private var engine: android.speech.SpeechRecognizer? = null
    override suspend fun listenOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> = withContext(Dispatchers.Main.immediate) {
        val lang = learningSpeechLanguage(langCode)
        var partial = ""
        val started = TimeSource.Monotonic.markNow()
        var speechStart = started
        var duration: Long? = null
        try {
            withTimeout(maxMs) { suspendCancellableCoroutine { continuation ->
                val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(appContext).also { engine = it }
                fun finish(text: String?, error: Int? = null) {
                    if (!continuation.isActive) return
                    val value = text?.takeIf { it.isNotBlank() } ?: partial
                    continuation.resume(if (error != null && error !in listOf(android.speech.SpeechRecognizer.ERROR_NO_MATCH, android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) && value.isBlank()) Result.failure(IllegalStateException("Recognition error $error"))
                        else Result.success(RecognizedSpeech(value, duration ?: speechStart.elapsedNow().inWholeMilliseconds)))
                }
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() { speechStart = TimeSource.Monotonic.markNow() }
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { duration = speechStart.elapsedNow().inWholeMilliseconds }
                    override fun onError(error: Int) { finish(null, error) }
                    override fun onResults(results: Bundle?) { finish(results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()) }
                    override fun onPartialResults(partialResults: Bundle?) { partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { partial = it } }
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                continuation.invokeOnCancellation { main.post { recognizer.cancel() } }
                recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.sttLocale)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                })
            } }
        } catch (e: TimeoutCancellationException) { if (partial.isNotBlank()) Result.success(RecognizedSpeech(partial, duration ?: speechStart.elapsedNow().inWholeMilliseconds)) else Result.failure(e) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        finally { engine?.cancel(); engine?.destroy(); engine = null }
    }
    override fun stop() { val current = engine; if (Looper.myLooper() == Looper.getMainLooper()) current?.stopListening() else main.post { current?.stopListening() } }
}
actual fun createSpeechRecognizer(): SpeechRecognizer = AndroidRecognizer()
@Composable actual fun rememberMicPermission(): MicPermission {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    return object : MicPermission {
        override val granted = granted
        override fun request() { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }
}
