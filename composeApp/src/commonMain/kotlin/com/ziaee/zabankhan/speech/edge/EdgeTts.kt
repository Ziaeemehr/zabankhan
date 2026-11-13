package com.ziaee.zabankhan.speech.edge

import com.ziaee.zabankhan.speech.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select

class EdgeTts(
    private val client: EdgeTtsClient,
    private val cache: TtsCache,
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
) : Tts {
    private val range = MutableStateFlow<IntRange?>(null)
    private val speaking = MutableStateFlow(false)
    private val status = MutableStateFlow(SpeechAudioStatus.IDLE)
    private val progress = MutableStateFlow<Float?>(null)
    override val speakingRange = range.asStateFlow()
    override val isSpeaking = speaking.asStateFlow()
    override val audioStatus = status.asStateFlow()
    override val synthesisProgress = progress.asStateFlow()
    private var voice: String? = null
    private var job: Job? = null
    private var seekRequests: Channel<Long>? = null
    private var pinAudio = false
    private var documentOwner = "speech"
    private val documentAudio = mutableMapOf<String, CachedSpeech>()
    override fun voices(langCode: String) = learningSpeechLanguage(langCode).onlineVoices.map { TtsVoice(it, it) }
    override fun setVoice(id: String?) { voice = id }
    override fun seekBy(deltaMs: Long): Boolean = seekRequests?.trySend(deltaMs)?.isSuccess == true
    override fun setAudioDocument(owner: String?) {
        val nextOwner = owner ?: "speech"
        if (documentOwner == nextOwner) return
        documentOwner = nextOwner
        pinAudio = false
        documentAudio.clear()
    }
    override fun releaseAudioDocument(owner: String) {
        if (documentOwner == owner) setAudioDocument(null)
    }
    override fun setAudioPinned(pinned: Boolean) {
        pinAudio = pinned
        val entries = documentAudio.values.toList()
        val owner = documentOwner
        scope.launch {
            if (pinned) entries.forEach { cache.setPinned(it, true, owner) }
            else cache.unpinDocument(owner)
        }
    }
    private fun selectedVoice(langCode: String): String {
        val lang = learningSpeechLanguage(langCode)
        return voice?.takeIf { it in lang.onlineVoices } ?: lang.onlineVoices.first()
    }
    override suspend fun prepare(text: String, langCode: String, rate: Float): Result<Unit> {
        if (text.isBlank()) return Result.success(Unit)
        return try {
            val lang = learningSpeechLanguage(langCode)
            val selected = selectedVoice(langCode)
            val owner = documentOwner
            val chunks = EdgeProtocol.chunks(text, lang)
            progress.value = 0f
            chunks.forEachIndexed { index, chunk ->
                val entry = cache.acquire(chunk.text, selected, EdgeProtocol.ratePercent(rate)) {
                    client.synthesize(chunk.text, selected, lang.ttsLocale, EdgeProtocol.ratePercent(rate))
                }
                try {
                    if (owner == documentOwner) documentAudio[entry.key] = entry
                    if (owner == documentOwner && pinAudio) cache.setPinned(entry, true, owner)
                } finally { withContext(NonCancellable) { cache.release(entry) } }
                progress.value = (index + 1f) / chunks.size
            }
            Result.success(Unit)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        finally { progress.value = null }
    }
    override fun speak(text: String, langCode: String, rate: Float, onError: () -> Unit, onDone: () -> Unit) {
        val previous = job
        stop()
        job = scope.launch {
            previous?.join()
            ensureActive()
            try { playText(text, langCode, rate); onDone() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { status.value = SpeechAudioStatus.ERROR; onError() }
        }
    }
    /** Timing metadata gives each clip its place in this utterance's audio timeline. */
    suspend fun playText(text: String, langCode: String, rate: Float, onChunk: (Int) -> Unit = {}) = coroutineScope {
        val lang = learningSpeechLanguage(langCode)
        val selected = selectedVoice(langCode)
        val percent = EdgeProtocol.ratePercent(rate)
        val owner = documentOwner
        val chunks = EdgeProtocol.chunks(text, lang)
        val held = mutableListOf<CachedSpeech>()
        val requests = Channel<Long>(Channel.CONFLATED)
        seekRequests = requests
        suspend fun load(chunk: SpeechChunk): CachedSpeech {
            val entry = cache.acquire(chunk.text, selected, percent) { client.synthesize(chunk.text, selected, lang.ttsLocale, percent) }
            held += entry
            if (owner == documentOwner) documentAudio[entry.key] = entry
            if (owner == documentOwner && pinAudio) cache.setPinned(entry, true, owner)
            return entry
        }
        fun duration(audio: CachedSpeech): Long = audio.boundaries.maxOfOrNull { it.offsetMs + it.durationMs }?.coerceAtLeast(1) ?: 1
        speaking.value = true
        status.value = SpeechAudioStatus.PREPARING
        try {
            var index = 0
            var pending = chunks.firstOrNull()?.let { async { loadResult(it, ::load) } }
            var startMs = 0L
            while (index < chunks.size) {
                onChunk(chunks[index].start)
                status.value = SpeechAudioStatus.PREPARING
                var audio = requireNotNull(pending).await().getOrThrow()
                // Seeking may cross clip boundaries, including clips not synthesized yet.
                while (startMs < 0 && index > 0) {
                    cache.release(audio); held.remove(audio)
                    index--
                    audio = load(chunks[index])
                    startMs += duration(audio)
                }
                startMs = startMs.coerceAtLeast(0)
                while (startMs >= duration(audio) && index < chunks.lastIndex) {
                    startMs -= duration(audio)
                    cache.release(audio); held.remove(audio)
                    index++
                    audio = load(chunks[index])
                }
                startMs = startMs.coerceAtMost((duration(audio) - 1).coerceAtLeast(0))
                onChunk(chunks[index].start)
                val nextIndex = index + 1
                pending = chunks.getOrNull(nextIndex)?.let { next -> async { loadResult(next, ::load) } }
                val ranges = EdgeProtocol.ranges(chunks[index].text, audio.boundaries, chunks[index].start)
                val tracking = launch { player.position.collect { range.value = EdgeProtocol.rangeAt(ranges, it) } }
                status.value = SpeechAudioStatus.READY
                val playing = async { player.playFrom(audio.path, startMs) }
                val delta = try {
                    select<Long?> {
                        playing.onAwait { null }
                        requests.onReceive { it }
                    }
                } finally { tracking.cancelAndJoin(); range.value = null }
                if (delta != null) {
                    // Read before cancelling: platform players reset their clock on disposal.
                    startMs = player.position.value + delta
                    playing.cancelAndJoin()
                    pending?.cancelAndJoin()
                    // A prefetch can have completed before cancellation; its lease remains in held.
                    pending = async { Result.success(audio) }
                } else {
                    startMs = 0
                    cache.release(audio); held.remove(audio)
                    index = nextIndex
                }
            }
            status.value = SpeechAudioStatus.IDLE
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status.value = SpeechAudioStatus.ERROR; throw e }
        finally {
            if (seekRequests === requests) seekRequests = null
            requests.close()
            val children = coroutineContext[Job]?.children?.toList().orEmpty()
            withContext(NonCancellable) {
                children.forEach { it.cancelAndJoin() }
                try { held.toList().forEach { cache.release(it) }; held.clear() }
                finally { player.stop(); range.value = null; speaking.value = false }
            }
        }
    }
    private suspend fun loadResult(chunk: SpeechChunk, load: suspend (SpeechChunk) -> CachedSpeech): Result<CachedSpeech> =
        try { Result.success(load(chunk)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    override fun prefetch(text: String, langCode: String, rate: Float) {
        val lang = learningSpeechLanguage(langCode)
        val selected = selectedVoice(langCode)
        val percent = EdgeProtocol.ratePercent(rate)
        val owner = documentOwner
        scope.launch {
            val first = EdgeProtocol.chunks(text, lang).firstOrNull() ?: return@launch
            try {
                val entry = cache.acquire(first.text, selected, percent) { client.synthesize(first.text, selected, lang.ttsLocale, percent) }
                try { if (owner == documentOwner) documentAudio[entry.key] = entry; if (owner == documentOwner && pinAudio) cache.setPinned(entry, true, owner) }
                finally { withContext(NonCancellable) { cache.release(entry) } }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Foreground playback retries and reports its own failure. */ }
        }
    }
    override fun stop() { job?.cancel(); job = null; player.stop(); range.value = null; speaking.value = false; status.value = SpeechAudioStatus.IDLE }
}
