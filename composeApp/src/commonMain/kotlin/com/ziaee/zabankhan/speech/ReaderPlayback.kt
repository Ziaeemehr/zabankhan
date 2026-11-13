package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.sentenceAround
import com.ziaee.zabankhan.lang.splitSentences
import kotlinx.coroutines.flow.*

data class SpokenParagraph(val text: String, val offset: Int, val sourceOffsets: List<Int> = emptyList()) {
    /** Mapping stays in UTF-16, the unit used by Compose selection and stored highlights. */
    fun sourceOffset(displayOffset: Int): Int = sourceOffsets.getOrNull(displayOffset) ?: (offset + displayOffset)
    fun sourceRange(range: IntRange): IntRange = sourceOffset(range.first)..sourceOffset(range.last)
}
/** UTF-16 offsets stay in the original document even across paragraph boundaries. */
class ReaderPlayback(private val tts: Tts, private val parts: List<SpokenParagraph>, private val lang: LanguageSpec, documentId: Long? = null) {
    private val documentOwner = documentId?.let { "reader:$it" }
    init { documentOwner?.let { tts.setAudioDocument(it) } }
    private var generation = 0
    private var index = -1
    private var base = 0
    private var spokenStart = 0
    private var mapSpoken = true
    private val paragraph = MutableStateFlow<Int?>(null)
    val currentParagraph: StateFlow<Int?> = paragraph.asStateFlow()
    private val completion = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = completion.asStateFlow()
    val audioStatus get() = tts.audioStatus
    private val documentProgress = MutableStateFlow<Float?>(null)
    val synthesisProgress: StateFlow<Float?> = documentProgress.asStateFlow()
    fun seekBy(deltaMs: Long): Boolean = tts.seekBy(deltaMs)
    fun setAudioPinned(pinned: Boolean) = tts.setAudioPinned(pinned)
    private val sectionStatus = MutableStateFlow<Map<Int, SpeechAudioStatus>>(emptyMap())
    val paragraphAudioStatuses: StateFlow<Map<Int, SpeechAudioStatus>> = sectionStatus.asStateFlow()
    private var preparedRate: Float? = null
    private var preparedVoice: String? = null
    private var preparationGeneration = 0
    /** Cached readiness belongs to a particular voice and rate, just like the audio cache key. */
    fun configureAudio(rate: Float, voiceId: String?) {
        if (preparedRate == rate && preparedVoice == voiceId) return
        preparedRate = rate; preparedVoice = voiceId
        preparationGeneration++
        sectionStatus.value = emptyMap()
    }
    fun audioStatusChanged(status: SpeechAudioStatus) {
        val currentIndex = paragraph.value ?: return
        if (preparedRate != rate || preparedVoice != voiceId) return
        if (status != SpeechAudioStatus.IDLE) sectionStatus.value = sectionStatus.value + (currentIndex to status)
    }
    private fun completedParagraph(index: Int, token: Int) {
        if (token == generation && preparedRate == rate && preparedVoice == voiceId) {
            sectionStatus.value = sectionStatus.value + (index to SpeechAudioStatus.READY)
        }
    }
    suspend fun prepareParagraph(index: Int, rate: Float, voiceId: String?): Result<Unit> {
        configureAudio(rate, voiceId)
        val preparationToken = preparationGeneration
        val part = parts.getOrNull(index) ?: return Result.success(Unit)
        if (part.text.isBlank()) return Result.success(Unit)
        tts.setVoice(voiceId)
        sectionStatus.value = sectionStatus.value + (index to SpeechAudioStatus.PREPARING)
        val result = try { tts.prepare(part.text, lang.code, rate) }
        catch (e: kotlinx.coroutines.CancellationException) {
            if (preparationToken == preparationGeneration) sectionStatus.value = sectionStatus.value - index
            throw e
        }
        if (preparationToken == preparationGeneration) {
            sectionStatus.value = sectionStatus.value + (index to if (result.isSuccess) SpeechAudioStatus.READY else SpeechAudioStatus.ERROR)
        }
        return result
    }
    suspend fun prepareAll(rate: Float, voiceId: String?, onProgress: (Float) -> Unit = {}): Result<Unit> {
        if (documentProgress.value != null) return Result.success(Unit)
        configureAudio(rate, voiceId)
        val preparationToken = preparationGeneration
        documentProgress.value = 0f
        try {
            val audible = parts.indices.filter { parts[it].text.isNotBlank() }
            for ((index, paragraphIndex) in audible.withIndex()) {
                if (preparationToken != preparationGeneration) return Result.failure(IllegalStateException("Audio configuration changed"))
                val result = prepareParagraph(paragraphIndex, rate, voiceId)
                if (result.isFailure) return result
                documentProgress.value = (index + 1f) / audible.size
                onProgress(documentProgress.value!!)
            }
            return Result.success(Unit)
        } finally { documentProgress.value = null }
    }
    private var spoken = ""
    private var rate = 1f
    private var voiceId: String? = null
    private var pausedOffset: Int? = null
    private var latestSpokenOffset: Int? = null
    private val sentences = parts.flatMap { part -> splitSentences(part.text, lang).map { part.sourceRange(it) } }
    private val current = MutableStateFlow<IntRange?>(null)
    val sentence: StateFlow<IntRange?> = current.asStateFlow()
    val isSpeaking = tts.isSpeaking
    fun rangeChanged(range: IntRange?) {
        if (range == null || spoken.isEmpty() || index < 0) return
        latestSpokenOffset = if (mapSpoken) parts.getOrNull(index)?.sourceOffset(spokenStart + range.first)
            else base + range.first
        val local = sentenceAround(spoken, range.first, lang)
        current.value = if (mapSpoken) parts.getOrNull(index)?.sourceRange((spokenStart + local.first)..(spokenStart + local.last))
            else (base + local.first)..(base + local.last)
    }
    private var repeats = 1
    fun start(paragraph: Int, rate: Float, voiceId: String?, repeats: Int = 1) {
        configureAudio(rate, voiceId)
        stop(); completion.value = false; this.voiceId = voiceId; this.rate = rate; this.repeats = repeats.coerceIn(1, 3); tts.setVoice(voiceId)
        speakParagraph(paragraph, generation)
    }
    private fun speakParagraph(next: Int, token: Int) {
        if (token != generation) return
        val part = parts.getOrNull(next) ?: run { current.value = null; index = -1; paragraph.value = null; completion.value = true; return }
        if (part.text.isBlank()) { speakParagraph(next + 1, token); return }
        index = next; paragraph.value = next; mapSpoken = true; spokenStart = 0
        if (repeats > 1) { speakSentence(next, splitSentences(part.text, lang), 0, 1, token); return }
        base = part.offset; spoken = part.text
        rangeChanged(0..0)
        tts.speak(part.text, lang.code, rate,
            onDone = { completedParagraph(next, token); speakParagraph(next + 1, token) },
            onError = { failed(next, token) })
        // Fetch the next paragraph's audio now so there is no pause between paragraphs.
        parts.getOrNull(next + 1)?.let { tts.prefetch(it.text, lang.code, rate) }
    }
    /** Repeat mode speaks sentence by sentence, each [repeats] times, with the same engine and voice. */
    private fun speakSentence(paragraph: Int, sentences: List<IntRange>, sentence: Int, round: Int, token: Int, startOffset: Int? = null) {
        if (token != generation) return
        val range = sentences.getOrNull(sentence) ?: return speakParagraph(paragraph + 1, token)
        val part = parts[paragraph]
        val start = startOffset ?: range.first
        base = part.sourceOffset(start); spokenStart = start; mapSpoken = true; this.paragraph.value = paragraph; spoken = part.text.substring(start, range.last + 1)
        rangeChanged(0..0)
        tts.speak(spoken, lang.code, rate, onDone = {
            completedParagraph(paragraph, token)
            if (round < repeats) speakSentence(paragraph, sentences, sentence, round + 1, token, startOffset)
            else speakSentence(paragraph, sentences, sentence + 1, 1, token)
        }, onError = { failed(paragraph, token) })
        if (round == 1) sentences.getOrNull(sentence + 1)?.let { tts.prefetch(part.text.substring(it), lang.code, rate) }
    }
    fun sentenceOnly(text: String, offset: Int, rate: Float, voiceId: String?) {
        configureAudio(rate, voiceId)
        stop(); completion.value = false; this.rate = rate; this.voiceId = voiceId; val token = generation; index = 0; paragraph.value = null; mapSpoken = false; base = offset; spoken = text
        tts.setVoice(voiceId); rangeChanged(0..0)
        tts.speak(text, lang.code, rate,
            onDone = { if (token == generation) { current.value = null; index = -1; paragraph.value = null } })
    }
    /** Navigation uses the same sentence boundaries as highlighting and repeat playback. */
    fun previousSentence(paragraph: Int = 0, rate: Float = this.rate, voiceId: String? = this.voiceId, repeats: Int = this.repeats) =
        navigate(-1, paragraph, rate, voiceId, repeats)
    fun nextSentence(paragraph: Int = 0, rate: Float = this.rate, voiceId: String? = this.voiceId, repeats: Int = this.repeats) =
        navigate(1, paragraph, rate, voiceId, repeats)
    private fun navigate(delta: Int, paragraph: Int, rate: Float, voiceId: String?, repeats: Int) {
        if (sentences.isEmpty()) return
        val fallback = parts.getOrNull(paragraph)?.let { part ->
            splitSentences(part.text, lang).firstOrNull()?.let { part.sourceOffset(it.first) }
        }
        val offset = current.value?.first ?: pausedOffset ?: fallback ?: sentences.first().first
        val at = sentences.indexOfLast { it.first <= offset }.coerceAtLeast(0)
        startAt(sentences[(at + delta).coerceIn(sentences.indices)].first, rate, voiceId, repeats)
    }
    fun startAt(offset: Int, rate: Float, voiceId: String?, repeats: Int = 1, resumeFromCurrent: Boolean = false) {
        configureAudio(rate, voiceId)
        stop(); completion.value = false; this.rate = rate; this.voiceId = voiceId; this.repeats = repeats.coerceIn(1, 3); tts.setVoice(voiceId)
        val paragraph = parts.indexOfLast { (it.sourceOffsets.firstOrNull() ?: it.offset) <= offset }.coerceAtLeast(0)
        val part = parts.getOrNull(paragraph) ?: return
        val ranges = splitSentences(part.text, lang)
        val sentence = ranges.indexOfLast { part.sourceOffset(it.first) <= offset }.coerceAtLeast(0)
        index = paragraph
        val sentenceRange = ranges.getOrNull(sentence) ?: return speakParagraph(paragraph + 1, generation)
        val localOffset = if (resumeFromCurrent) {
            (0 until part.text.length).lastOrNull { part.sourceOffset(it) <= offset }
                ?.coerceAtLeast(sentenceRange.first)?.coerceAtMost(sentenceRange.last + 1) ?: sentenceRange.first
        } else sentenceRange.first
        if (repeats > 1) speakSentence(paragraph, ranges, sentence, 1, generation, localOffset)
        else {
            val start = localOffset
            spokenStart = start; mapSpoken = true; this.paragraph.value = paragraph
            base = part.sourceOffset(start); spoken = part.text.substring(start)
            rangeChanged(0..0)
            val token = generation
            tts.speak(spoken, lang.code, rate,
                onDone = { completedParagraph(paragraph, token); speakParagraph(paragraph + 1, token) },
                onError = { failed(paragraph, token) })
            parts.getOrNull(paragraph + 1)?.let { tts.prefetch(it.text, lang.code, rate) }
        }
    }
    /** A failed section stops like a pause, so resume retries it and the text is not marked finished. */
    private fun failed(paragraph: Int, token: Int) {
        if (token != generation) return
        sectionStatus.value = sectionStatus.value + (paragraph to SpeechAudioStatus.ERROR)
        pause()
    }
    /** TTS has no portable pause; resume restarts the interrupted sentence. */
    fun pause() {
        pausedOffset = latestSpokenOffset ?: current.value?.first
        generation++; index = -1; spoken = ""; tts.stop()
    }
    fun resume(paragraph: Int, rate: Float, voiceId: String?, repeats: Int = 1) {
        val offset = pausedOffset
        if (offset == null) start(paragraph, rate, voiceId, repeats) else startAt(offset, rate, voiceId, repeats, resumeFromCurrent = true)
    }
    /** End reader ownership without deleting its persisted offline audio pins. */
    fun close() { stop(); documentOwner?.let { tts.releaseAudioDocument(it) } }
    fun stop() { generation++; index = -1; paragraph.value = null; spoken = ""; pausedOffset = null; latestSpokenOffset = null; current.value = null; completion.value = false; tts.stop() }
}
