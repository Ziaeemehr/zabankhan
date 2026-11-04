package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.sentenceAround
import kotlinx.coroutines.flow.*

data class SpokenParagraph(val text: String, val offset: Int)
/** UTF-16 offsets stay in the original document even across paragraph boundaries. */
class ReaderPlayback(private val tts: Tts, private val parts: List<SpokenParagraph>, private val lang: LanguageSpec) {
    private var generation = 0
    private var index = -1
    private var base = 0
    private var spoken = ""
    private var rate = 1f
    private val current = MutableStateFlow<IntRange?>(null)
    val sentence: StateFlow<IntRange?> = current.asStateFlow()
    val isSpeaking = tts.isSpeaking
    fun rangeChanged(range: IntRange?) {
        if (range == null || spoken.isEmpty() || index < 0) return
        val local = sentenceAround(spoken, range.first, lang)
        current.value = (base + local.first)..(base + local.last)
    }
    fun start(paragraph: Int, rate: Float, voiceId: String?) {
        stop(); this.rate = rate; tts.setVoice(voiceId)
        speakParagraph(paragraph, generation)
    }
    private fun speakParagraph(next: Int, token: Int) {
        if (token != generation) return
        val part = parts.getOrNull(next) ?: run { current.value = null; index = -1; return }
        index = next; base = part.offset; spoken = part.text
        rangeChanged(0..0)
        tts.speak(part.text, lang.code, rate) { speakParagraph(next + 1, token) }
        // Fetch the next paragraph's audio now so there is no pause between paragraphs.
        parts.getOrNull(next + 1)?.let { tts.prefetch(it.text, lang.code, rate) }
    }
    fun sentenceOnly(text: String, offset: Int, rate: Float, voiceId: String?) {
        stop(); val token = generation; index = 0; base = offset; spoken = text
        tts.setVoice(voiceId); rangeChanged(0..0)
        tts.speak(text, lang.code, rate) { if (token == generation) { current.value = null; index = -1 } }
    }
    fun stop() { generation++; index = -1; current.value = null; tts.stop() }
}
