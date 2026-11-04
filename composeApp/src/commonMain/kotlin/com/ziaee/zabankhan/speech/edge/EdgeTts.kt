package com.ziaee.zabankhan.speech.edge

import com.ziaee.zabankhan.speech.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class EdgeTts(
    private val client: EdgeTtsClient,
    private val cache: TtsCache,
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
) : Tts {
    private val range = MutableStateFlow<IntRange?>(null)
    private val speaking = MutableStateFlow(false)
    override val speakingRange = range.asStateFlow()
    override val isSpeaking = speaking.asStateFlow()
    private var voice: String? = null
    private var job: Job? = null
    override fun voices(langCode: String) = learningSpeechLanguage(langCode).onlineVoices.map { TtsVoice(it, it) }
    override fun setVoice(id: String?) { voice = id }
    override fun speak(text: String, langCode: String, rate: Float, onDone: () -> Unit) {
        val previous = job
        stop()
        job = scope.launch {
            previous?.join()
            ensureActive()
            try { playText(text, langCode, rate); onDone() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { onDone() }
        }
    }
    /** onChunk records the point to resume from if an online request fails mid-document. */
    suspend fun playText(text: String, langCode: String, rate: Float, onChunk: (Int) -> Unit = {}) = coroutineScope {
        val lang = learningSpeechLanguage(langCode)
        val selected = voice?.takeIf { it in lang.onlineVoices } ?: lang.onlineVoices.first()
        val percent = EdgeProtocol.ratePercent(rate)
        val chunks = EdgeProtocol.chunks(text, lang)
        val held = mutableListOf<CachedSpeech>()
        suspend fun load(chunk: SpeechChunk): CachedSpeech {
            val entry = cache.acquire(chunk.text, selected, percent) { client.synthesize(chunk.text, selected, lang.ttsLocale, percent) }
            held += entry
            return entry
        }
        speaking.value = true
        try {
            var pending = chunks.firstOrNull()?.let { async { loadResult(it, ::load) } }
            for ((index, chunk) in chunks.withIndex()) {
                onChunk(chunk.start)
                val audio = requireNotNull(pending).await().getOrThrow()
                // A failed prefetch must not cancel audio already playing.
                pending = chunks.getOrNull(index + 1)?.let { next -> async { loadResult(next, ::load) } }
                val ranges = EdgeProtocol.ranges(chunk.text, audio.boundaries, chunk.start)
                val tracking = launch { player.position.collect { range.value = EdgeProtocol.rangeAt(ranges, it) } }
                try { player.play(audio.path) } finally { tracking.cancelAndJoin(); range.value = null }
                cache.release(audio); held.remove(audio)
            }
        } finally {
            // Cancel and join the prefetch before releasing its lease.
            coroutineContext[Job]?.children?.toList()?.forEach { it.cancelAndJoin() }
            withContext(NonCancellable) { held.toList().forEach { cache.release(it) }; held.clear() }
            player.stop(); range.value = null; speaking.value = false
        }
    }
    private suspend fun loadResult(chunk: SpeechChunk, load: suspend (SpeechChunk) -> CachedSpeech): Result<CachedSpeech> =
        try { Result.success(load(chunk)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    override fun prefetch(text: String, langCode: String, rate: Float) {
        scope.launch {
            val lang = learningSpeechLanguage(langCode)
            val selected = voice?.takeIf { it in lang.onlineVoices } ?: lang.onlineVoices.first()
            val percent = EdgeProtocol.ratePercent(rate)
            // Only the first chunk: playText prefetches the rest itself while this one plays.
            val first = EdgeProtocol.chunks(text, lang).firstOrNull() ?: return@launch
            runCatching { cache.release(cache.acquire(first.text, selected, percent) { client.synthesize(first.text, selected, lang.ttsLocale, percent) }) }
        }
    }
    override fun stop() { job?.cancel(); job = null; player.stop(); range.value = null; speaking.value = false }
}
