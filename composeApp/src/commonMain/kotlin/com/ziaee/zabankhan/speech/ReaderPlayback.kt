package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.sentenceAround
import com.ziaee.zabankhan.lang.splitSentences
import kotlinx.coroutines.flow.*

data class SpokenParagraph(val text: String, val offset: Int)
/** UTF-16 offsets stay in the original document even across paragraph boundaries. */
class ReaderPlayback(private val tts: Tts, private val parts: List<SpokenParagraph>, private val lang: LanguageSpec) {
    private var generation = 0
    private var index = -1
    private var base = 0
    private var spoken = ""
    private var rate = 1f
    private var voiceId: String? = null
    private var pausedOffset: Int? = null
    private val sentences = parts.flatMap { part -> splitSentences(part.text, lang).map { (it.first + part.offset)..(it.last + part.offset) } }
    private val current = MutableStateFlow<IntRange?>(null)
    val sentence: StateFlow<IntRange?> = current.asStateFlow()
    val isSpeaking = tts.isSpeaking
    fun rangeChanged(range: IntRange?) {
        if (range == null || spoken.isEmpty() || index < 0) return
        val local = sentenceAround(spoken, range.first, lang)
        current.value = (base + local.first)..(base + local.last)
    }
    private var repeats = 1
    fun start(paragraph: Int, rate: Float, voiceId: String?, repeats: Int = 1) {
        stop(); this.voiceId = voiceId; this.rate = rate; this.repeats = repeats.coerceIn(1, 3); tts.setVoice(voiceId)
        speakParagraph(paragraph, generation)
    }
    private fun speakParagraph(next: Int, token: Int) {
        if (token != generation) return
        val part = parts.getOrNull(next) ?: run { current.value = null; index = -1; return }
        index = next
        if (repeats > 1) { speakSentence(next, splitSentences(part.text, lang), 0, 1, token); return }
        base = part.offset; spoken = part.text
        rangeChanged(0..0)
        tts.speak(part.text, lang.code, rate) { speakParagraph(next + 1, token) }
        // Fetch the next paragraph's audio now so there is no pause between paragraphs.
        parts.getOrNull(next + 1)?.let { tts.prefetch(it.text, lang.code, rate) }
    }
    /** Repeat mode speaks sentence by sentence, each [repeats] times, with the same engine and voice. */
    private fun speakSentence(paragraph: Int, sentences: List<IntRange>, sentence: Int, round: Int, token: Int) {
        if (token != generation) return
        val range = sentences.getOrNull(sentence) ?: return speakParagraph(paragraph + 1, token)
        val part = parts[paragraph]
        base = part.offset + range.first; spoken = part.text.substring(range)
        rangeChanged(0..0)
        tts.speak(spoken, lang.code, rate) {
            if (round < repeats) speakSentence(paragraph, sentences, sentence, round + 1, token)
            else speakSentence(paragraph, sentences, sentence + 1, 1, token)
        }
        if (round == 1) sentences.getOrNull(sentence + 1)?.let { tts.prefetch(part.text.substring(it), lang.code, rate) }
    }
    fun sentenceOnly(text: String, offset: Int, rate: Float, voiceId: String?) {
        stop(); this.rate = rate; this.voiceId = voiceId; val token = generation; index = 0; base = offset; spoken = text
        tts.setVoice(voiceId); rangeChanged(0..0)
        tts.speak(text, lang.code, rate) { if (token == generation) { current.value = null; index = -1 } }
    }
    /** Navigation uses the same sentence boundaries as highlighting and repeat playback. */
    fun previousSentence(paragraph: Int = 0, rate: Float = this.rate, voiceId: String? = this.voiceId, repeats: Int = this.repeats) =
        navigate(-1, paragraph, rate, voiceId, repeats)
    fun nextSentence(paragraph: Int = 0, rate: Float = this.rate, voiceId: String? = this.voiceId, repeats: Int = this.repeats) =
        navigate(1, paragraph, rate, voiceId, repeats)
    private fun navigate(delta: Int, paragraph: Int, rate: Float, voiceId: String?, repeats: Int) {
        if (sentences.isEmpty()) return
        val fallback = parts.getOrNull(paragraph)?.let { part ->
            splitSentences(part.text, lang).firstOrNull()?.let { it.first + part.offset }
        }
        val offset = current.value?.first ?: pausedOffset ?: fallback ?: sentences.first().first
        val at = sentences.indexOfLast { it.first <= offset }.coerceAtLeast(0)
        startAt(sentences[(at + delta).coerceIn(sentences.indices)].first, rate, voiceId, repeats)
    }
    fun startAt(offset: Int, rate: Float, voiceId: String?, repeats: Int = 1) {
        stop(); this.rate = rate; this.voiceId = voiceId; this.repeats = repeats.coerceIn(1, 3); tts.setVoice(voiceId)
        val paragraph = parts.indexOfLast { it.offset <= offset }.coerceAtLeast(0)
        val part = parts.getOrNull(paragraph) ?: return
        val ranges = splitSentences(part.text, lang)
        val sentence = ranges.indexOfLast { it.first + part.offset <= offset }.coerceAtLeast(0)
        index = paragraph
        speakSentence(paragraph, ranges, sentence, 1, generation)
    }
    /** TTS has no portable pause; resume restarts the interrupted sentence. */
    fun pause() {
        pausedOffset = current.value?.first
        generation++; index = -1; spoken = ""; tts.stop()
    }
    fun resume(paragraph: Int, rate: Float, voiceId: String?, repeats: Int = 1) {
        val offset = pausedOffset
        if (offset == null) start(paragraph, rate, voiceId, repeats) else startAt(offset, rate, voiceId, repeats)
    }
    fun stop() { generation++; index = -1; spoken = ""; pausedOffset = null; current.value = null; tts.stop() }
}
