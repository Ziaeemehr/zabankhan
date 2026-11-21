package com.ziaee.zabankhan.speech.local

import android.util.Base64
import com.ziaee.zabankhan.net.LocalServerFinder
import com.ziaee.zabankhan.net.LocalServerPrefs
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.speech.edge.EdgeProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class LocalTts(private val context: android.content.Context, private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) : Tts {
    private val player = createAudioPlayer()
    private val range = MutableStateFlow<IntRange?>(null)
    private val speaking = MutableStateFlow(false)
    private val status = MutableStateFlow(SpeechAudioStatus.IDLE)
    private val progress = MutableStateFlow<Float?>(null)
    override val speakingRange = range.asStateFlow()
    override val isSpeaking = speaking.asStateFlow()
    override val audioStatus = status.asStateFlow()
    override val synthesisProgress = progress.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    @Volatile private var selectedSpeaker: String? = null
    @Volatile private var speakersCache: List<String> = emptyList()
    private var speakersFetch: Job? = null
    private val cache = mutableMapOf<String, File>()

    override fun voices(langCode: String): List<TtsVoice> {
        if (speakersCache.isEmpty() && speakersFetch?.isActive != true) {
            speakersFetch = scope.launch {
                val speakers = runCatching { withContext(Dispatchers.IO) { speakers() } }.getOrDefault(emptyList())
                if (speakers.isNotEmpty()) { updateSpeakers(speakers); selectDefaultSpeaker(speakers, langCode) }
            }
        }
        return speakersCache.map { TtsVoice(it, it) }
    }
    override fun setVoice(id: String?) {
        selectedSpeaker = id?.takeIf(String::isNotBlank)
        selectedSpeaker?.let { LocalTtsPrefs.setSpeaker(context, it) }
    }
    fun updateSpeakers(value: List<String>) { speakersCache = value }
    fun speakers(): List<String> {
        val base = serverBase() ?: return emptyList()
        val connection = (URL("$base/speakers").openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 10000 }
        return try {
            if (!LocalTtsPrefs.usePaired(context)) LocalTtsPrefs.token(context).takeIf(String::isNotBlank)?.let { connection.setRequestProperty("X-Token", it) }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = org.json.JSONArray(body)
            List(array.length()) { array.getString(it) }.also { speakersCache = it }
        } finally { connection.disconnect() }
    }
    fun ping(): Boolean = runCatching {
        val base = serverBase() ?: return@runCatching false
        val connection = (URL("$base/health").openConnection() as HttpURLConnection).apply { connectTimeout = 3000; readTimeout = 3000 }
        try { connection.responseCode in 200..299 } finally { connection.disconnect() }
    }.getOrDefault(false)
    override suspend fun prepare(text: String, langCode: String, rate: Float): Result<Unit> = runCatching { synthesize(text, langCode, rate) }
    override fun speak(text: String, langCode: String, rate: Float, onError: () -> Unit, onDone: () -> Unit) {
        stop(); val token = generation
        val requestedSpeaker = selectedSpeaker
        status.value = SpeechAudioStatus.PREPARING
        job = scope.launch {
            speaking.value = true
            try {
                val file = withContext(Dispatchers.IO) { synthesize(text, langCode, rate, requestedSpeaker) }
                if (token != generation) return@launch
                status.value = SpeechAudioStatus.READY
                player.play(file.absolutePath)
                onDone()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { status.value = SpeechAudioStatus.ERROR; onError() }
            finally { if (token == generation) { speaking.value = false; range.value = null; progress.value = null } }
        }
    }
    override fun stop() { generation++; job?.cancel(); job = null; player.stop(); speaking.value = false; range.value = null; progress.value = null }

    private fun synthesize(text: String, langCode: String, rate: Float, requestedSpeaker: String? = selectedSpeaker): File {
        val prefs = LocalTtsPrefs
        val pairPrefs = LocalServerPrefs(context)
        val usePair = prefs.usePaired(context) && pairPrefs.paired != null
        val base = (if (usePair) pairPrefs.ttsUrl().orEmpty() else prefs.serverUrl(context)).trim().trimEnd('/')
        require(base.isNotBlank()) { "Set a local TTS server address or pair a computer first" }
        val speaker = requestedSpeaker?.takeIf { it.isNotBlank() && it != "local" }
            ?: prefs.speaker(context).takeIf(String::isNotBlank)
            ?: selectDefaultSpeaker(speakersCache.ifEmpty { speakers() }, langCode).orEmpty()
        require(speaker.isNotBlank()) { "Local TTS server has no speakers" }
        val cacheKey = "$base|$langCode|$speaker|$rate|$text"
        cache[cacheKey]?.takeIf(File::exists)?.let { return it }
        val conn = (URL("$base/synthesize").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 180000; requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!usePair) prefs.token(context).takeIf(String::isNotBlank)?.let { setRequestProperty("X-Token", it) }
        }
        try {
            val body = JSONObject().put("text", text).put("speaker", speaker).put("rate_percent", EdgeProtocol.ratePercent(rate)).put("language", langCode)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val payload = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "Local TTS server returned HTTP $code: $payload" }
            val json = JSONObject(payload)
            val file = File(context.cacheDir, "local_tts/${cacheKey.hashCode()}.mp3")
            file.parentFile?.mkdirs(); file.writeBytes(Base64.decode(json.getString("audio_b64"), Base64.DEFAULT))
            cache[cacheKey] = file
            return file
        } catch (error: Exception) { if (error is IOException) retryPairedAddress(pairPrefs, base); throw error }
        finally { conn.disconnect() }
    }

    private fun selectDefaultSpeaker(speakers: List<String>, langCode: String): String? {
        if (speakers.isEmpty()) return null
        val saved = LocalTtsPrefs.speaker(context).takeIf(String::isNotBlank)
        val selected = saved?.takeIf { it in speakers } ?: speakers.firstOrNull { speakerMatchesLanguage(it, langCode) } ?: speakers.first()
        selectedSpeaker = selected
        LocalTtsPrefs.setSpeaker(context, selected)
        return selected
    }

    private fun speakerMatchesLanguage(name: String, langCode: String): Boolean {
        val language = langCode.substringBefore('-').substringBefore('_').lowercase()
        if (language.isBlank()) return false
        val normalized = name.lowercase()
        val hint = when (language) {
            "fa" -> listOf("persian", "farsi", "فارسی")
            "fr" -> listOf("french", "français", "francais")
            "en" -> listOf("english")
            "es" -> listOf("spanish", "español", "espanol")
            "de" -> listOf("german", "deutsch")
            "it" -> listOf("italian", "italiano")
            "ar" -> listOf("arabic", "العربية")
            else -> listOf(language)
        }
        return hint.any(normalized::contains)
    }

    private fun serverBase(): String? {
        val pairPrefs = LocalServerPrefs(context)
        val base = (if (LocalTtsPrefs.usePaired(context) && pairPrefs.paired != null) pairPrefs.ttsUrl().orEmpty() else LocalTtsPrefs.serverUrl(context)).trim().trimEnd('/')
        return base.takeIf(String::isNotBlank)
    }

    private fun retryPairedAddress(prefs: LocalServerPrefs, base: String) {
        val paired = prefs.paired ?: return
        runBlocking {
            val found = LocalServerFinder.discover(context, 4000).firstOrNull { it.id == paired.id } ?: return@runBlocking
            if (prefs.paired?.id == paired.id && base != "http://${found.host}:${found.port}/tts") prefs.update(found.host, found.port)
        }
    }
}
