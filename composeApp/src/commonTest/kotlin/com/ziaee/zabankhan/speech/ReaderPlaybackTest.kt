package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.lang.Languages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

private class FakeTts : Tts {
    override val speakingRange = MutableStateFlow<IntRange?>(null)
    override val isSpeaking = MutableStateFlow(false)
    var text = ""
    var done: () -> Unit = {}
    var rate = 0f
    override fun speak(text: String, langCode: String, rate: Float, onDone: () -> Unit) { this.text = text; this.rate = rate; done = onDone; isSpeaking.value = true }
    override fun stop() { isSpeaking.value = false }
    override fun voices(langCode: String) = emptyList<TtsVoice>()
    override fun setVoice(id: String?) = Unit
    val prefetched = mutableListOf<String>()
    override fun prefetch(text: String, langCode: String, rate: Float) { prefetched += text }
}
class ReaderPlaybackTest {
    @Test fun paragraphSequenceSentenceOffsetsAndStop() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("Hello. Goodbye.", 0), SpokenParagraph("Another.", 17)), Languages.ENGLISH)
        playback.start(0, 1.25f, null)
        assertEquals("Hello. Goodbye.", tts.text); assertEquals(1.25f, tts.rate)
        playback.rangeChanged(8..10); assertEquals(7..14, playback.sentence.value)
        tts.done(); assertEquals("Another.", tts.text); assertEquals(17..24, playback.sentence.value)
        val stale = tts.done
        playback.stop(); stale(); assertNull(playback.sentence.value)
    }
    @Test fun startFromVisibleOrSingleSentence() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("First.", 0), SpokenParagraph("Second.", 8)), Languages.ENGLISH)
        playback.start(1, .75f, null); assertEquals("Second.", tts.text)
        playback.sentenceOnly("Word.", 20, 1f, null); assertEquals(20..24, playback.sentence.value)
        tts.done(); assertNull(playback.sentence.value); assertEquals("Word.", tts.text)
    }
    @Test fun repeatModeSpeaksEachSentenceNTimesThenMovesOn() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("Hello. Goodbye.", 0), SpokenParagraph("Another.", 17)), Languages.ENGLISH)
        playback.start(0, 1f, null, repeats = 2)
        val spoken = mutableListOf(tts.text)
        assertEquals(0..5, playback.sentence.value)
        repeat(4) { tts.done(); spoken += tts.text }
        assertEquals(listOf("Hello.", "Hello.", "Goodbye.", "Goodbye.", "Another."), spoken)
        assertEquals(17..24, playback.sentence.value)
    }
    @Test fun prefetchesNextParagraphWhileSpeaking() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("One.", 0), SpokenParagraph("Two.", 5), SpokenParagraph("Three.", 10)), Languages.ENGLISH)
        playback.start(0, 1f, null); assertEquals(listOf("Two."), tts.prefetched)
        tts.done(); assertEquals(listOf("Two.", "Three."), tts.prefetched)
        tts.done(); assertEquals(2, tts.prefetched.size) // last paragraph: nothing to fetch
    }
}
