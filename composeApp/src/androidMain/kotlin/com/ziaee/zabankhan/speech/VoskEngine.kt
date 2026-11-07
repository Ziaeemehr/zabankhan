package com.ziaee.zabankhan.speech

import java.io.File
import kotlinx.coroutines.*
import org.vosk.Recognizer
import kotlinx.serialization.json.*

data class Recording(val transcript: String, val pcm: ShortArray?, val durationMs: Long)
internal fun parseVoskText(json: String): String = runCatching {
    Json.parseToJsonElement(json).jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
}.getOrDefault("").trim()

class VoskEngine(private val modelDir: File) {
    private val recorder = MicRecorder()

    fun start() = recorder.start()

    suspend fun stop(): Recording {
        val pcm = normalizeGain(recorder.stop())
        val text = withContext(Dispatchers.Default) {
            VoskModels.forDir(modelDir).withModel { model ->
                Recognizer(model, SAMPLE_RATE.toFloat()).use { rec ->
                    rec.acceptWaveForm(pcm, pcm.size)
                    parseVoskText(rec.finalResult)
                }
            }
        }
        return Recording(text, pcm, durationMsOf(pcm.size))
    }

    fun cancel() = recorder.cancel()
    fun release() = recorder.cancel()
}


internal class VoskRecognizer : SpeechRecognizer {
    private var engine: VoskEngine? = null
    private var released: CompletableDeferred<Unit>? = null
    private var recording: ShortArray? = null
    override val hasRecording get() = recording != null
    override suspend fun listenOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> {
        val manager = VoskModelManager(learningSpeechLanguage(langCode))
        val current = VoskEngine(File(manager.modelDir.toString()))
        val end = CompletableDeferred<Unit>()
        released = end; engine = current; clearRecording()
        return try {
            check(manager.isInstalled()) { "Offline speech model is not installed" }
            current.start()
            withTimeoutOrNull(maxMs) { end.await() }
            val result = current.stop()
            recording = result.pcm
            Result.success(RecognizedSpeech(result.transcript, result.durationMs))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        finally { current.release(); if (engine === current) { engine = null; released = null } }
    }
    override fun stop() { released?.complete(Unit) }
    override fun replay() { recording?.let(PcmPlayer::play) }
    override fun clearRecording() { PcmPlayer.stop(); recording = null }
}
