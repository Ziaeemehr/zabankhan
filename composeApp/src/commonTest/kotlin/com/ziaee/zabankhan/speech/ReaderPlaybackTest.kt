package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.lang.Languages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

private class FakeTts : Tts {
    override val speakingRange = MutableStateFlow<IntRange?>(null)
    override val isSpeaking = MutableStateFlow(false)
    var text = ""
    var done: () -> Unit = {}
    var error: () -> Unit = {}
    var rate = 0f
    override fun speak(text: String, langCode: String, rate: Float, onError: () -> Unit, onDone: () -> Unit) { this.text = text; this.rate = rate; done = onDone; error = onError; isSpeaking.value = true }
    override fun stop() { isSpeaking.value = false }
    override fun voices(langCode: String) = emptyList<TtsVoice>()
    override fun setVoice(id: String?) = Unit
    val prepared = mutableListOf<String>()
    var failPreparation = false
    var seekDelta: Long? = null
    var currentAudioOwner: String? = null
    override fun setAudioDocument(owner: String?) { currentAudioOwner = owner }
    override fun seekBy(deltaMs: Long): Boolean { seekDelta = deltaMs; return true }
    override suspend fun prepare(text: String, langCode: String, rate: Float): Result<Unit> {
        prepared += text
        return if (failPreparation) Result.failure(IllegalStateException("offline")) else Result.success(Unit)
    }
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
    @Test fun navigationCrossesParagraphsAndClampsAtEnds() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("Hello. Goodbye.", 0), SpokenParagraph("Another.", 19)), Languages.ENGLISH)
        playback.start(0, 1.25f, null)
        playback.nextSentence(); assertEquals("Goodbye.", tts.text); assertEquals(7..14, playback.sentence.value)
        val stale = tts.done
        playback.nextSentence(); assertEquals("Another.", tts.text); assertEquals(19..26, playback.sentence.value)
        stale(); assertEquals(19..26, playback.sentence.value)
        playback.nextSentence(); assertEquals(19..26, playback.sentence.value)
        playback.previousSentence(); assertEquals("Goodbye.", tts.text)
        playback.previousSentence(); playback.previousSentence(); assertEquals(0..5, playback.sentence.value)
        assertEquals(1.25f, tts.rate)
    }
    @Test fun pauseResumeAndNavigationKeepRepeatMode() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("Hello. Goodbye.", 0)), Languages.ENGLISH)
        playback.start(0, .75f, null, 2)
        playback.nextSentence()
        playback.pause(); assertFalse(tts.isSpeaking.value)
        playback.resume(0, .75f, null, 2); assertEquals("Goodbye.", tts.text)
        tts.done(); assertEquals("Goodbye.", tts.text)
        tts.done(); assertNull(playback.sentence.value)
    }
    @Test fun pauseResumeContinuesAtLatestReportedSpeechOffset() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("Hello. Goodbye.", 0)), Languages.ENGLISH)
        playback.start(0, 1f, null)
        playback.rangeChanged(9..10)
        playback.pause()
        playback.resume(0, 1f, null)
        assertEquals("odbye.", tts.text)
    }
    @Test fun playbackMarksDocumentCompleteAfterLastParagraph() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("Last line.", 0)), Languages.ENGLISH)
        playback.start(0, 1f, null)
        assertFalse(playback.isComplete.value)
        tts.done()
        assertTrue(playback.isComplete.value)
    }
    @Test fun playbackErrorDoesNotAdvanceOrCompleteDocument() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("Last line.", 0), SpokenParagraph("Next.", 11)), Languages.ENGLISH)
        playback.start(0, 1f, null)
        tts.error()
        assertFalse(playback.isComplete.value)
        assertEquals(0, playback.currentParagraph.value)
        assertEquals(SpeechAudioStatus.ERROR, playback.paragraphAudioStatuses.value[0])
        assertFalse(tts.isSpeaking.value)
        tts.text = ""
        playback.resume(0, 1f, null)
        assertEquals("Last line.", tts.text)
    }
    @Test fun persianNavigationUsesOriginalOffsetsAndEmptyDocumentIsSafe() {
        val tts = FakeTts()
        val body = "سلام. خوبی؟"
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph(body, 12)), Languages.PERSIAN)
        playback.start(0, 1f, null)
        playback.nextSentence(); assertEquals("خوبی؟", tts.text); assertEquals(18..22, playback.sentence.value)
        playback.previousSentence()
        // Navigation restarts at this sentence and keeps the rest of its section seekable.
        assertEquals(body, tts.text)
        assertEquals(12..16, playback.sentence.value)
        playback.rangeChanged(6..10)
        assertEquals(18..22, playback.sentence.value)
        val empty = ReaderPlayback(tts, emptyList(), Languages.PERSIAN)
        empty.nextSentence(); empty.previousSentence(); assertNull(empty.sentence.value)
    }
    @Test fun navigationFromVisibleParagraphSkipsLeadingWhitespace() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("Before.", 0), SpokenParagraph("  First. Second.", 11)), Languages.ENGLISH)
        playback.nextSentence(paragraph = 1)
        assertEquals("Second.", tts.text)
        assertEquals(20..26, playback.sentence.value)
    }
    @Test fun startAtUsesContainingSentenceAndContinuesThroughDocument() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("First. Second.", 3), SpokenParagraph("Third.", 20)), Languages.ENGLISH)
        playback.startAt(13, 1.5f, null)
        assertEquals("Second.", tts.text); assertEquals(10..16, playback.sentence.value)
        tts.done(); assertEquals("Third.", tts.text)
        val stale = tts.done
        playback.stop(); stale(); assertNull(playback.sentence.value)
    }
    @Test fun markdownSourceMappingSurvivesSeekingAndNavigation() {
        val tts = FakeTts()
        // "**Hello.** Goodbye." -> "Hello. Goodbye."; character offsets stay in the raw document.
        val sourceOffsets = (2..7).toList() + (10..18).toList()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("Hello. Goodbye.", 0, sourceOffsets)), Languages.ENGLISH)
        playback.start(0, 1f, null)
        assertEquals(2..7, playback.sentence.value)
        playback.rangeChanged(8..10)
        assertEquals(11..18, playback.sentence.value)
        playback.startAt(14, 1f, null)
        assertEquals("Goodbye.", tts.text)
        assertEquals(11..18, playback.sentence.value)
        playback.previousSentence()
        assertEquals(2..7, playback.sentence.value)
    }
    @Test fun emptyImageBlocksAreSkippedWithoutShiftingParagraphIndex() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("", 0), SpokenParagraph("Hello.", 14)), Languages.ENGLISH)
        playback.start(0, 1f, null)
        assertEquals("Hello.", tts.text)
        assertEquals(1, playback.currentParagraph.value)
        assertEquals(14..19, playback.sentence.value)
    }

    @Test fun preparingDocumentTracksSectionsSkipsImagesAndKeepsFailuresRetryable() = runTest {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("One.", 0), SpokenParagraph("", 8), SpokenParagraph("Two.", 20)), Languages.ENGLISH, documentId = 42)
        assertEquals("reader:42", tts.currentAudioOwner)
        val progress = mutableListOf<Float>()
        assertTrue(playback.prepareAll(1f, null) { progress += it }.isSuccess)
        assertEquals(listOf("One.", "Two."), tts.prepared)
        assertEquals(listOf(.5f, 1f), progress)
        assertEquals(mapOf(0 to SpeechAudioStatus.READY, 2 to SpeechAudioStatus.READY), playback.paragraphAudioStatuses.value)
        tts.failPreparation = true
        assertTrue(playback.prepareParagraph(2, 1f, null).isFailure)
        assertEquals(SpeechAudioStatus.ERROR, playback.paragraphAudioStatuses.value[2])
        tts.failPreparation = false
        assertTrue(playback.prepareParagraph(2, 1f, null).isSuccess)
        assertEquals(SpeechAudioStatus.READY, playback.paragraphAudioStatuses.value[2])
        assertTrue(playback.seekBy(-10000))
        assertEquals(-10000L, tts.seekDelta)
    }

    @Test fun closingReaderReleasesLiveAudioOwnerButStoppingSelectionDoesNot() {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("One.", 0)), Languages.ENGLISH, documentId = 42)
        playback.stop()
        assertEquals("reader:42", tts.currentAudioOwner)
        playback.close()
        assertNull(tts.currentAudioOwner)
    }

    @Test fun successfulPlaybackReplacesFailedPreparationAfterMovingToNextSection() = runTest {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("One.", 0), SpokenParagraph("Two.", 8)), Languages.ENGLISH)
        tts.failPreparation = true
        playback.prepareParagraph(0, 1f, "voice-a")
        assertEquals(SpeechAudioStatus.ERROR, playback.paragraphAudioStatuses.value[0])
        playback.start(0, 1f, "voice-a")
        playback.audioStatusChanged(SpeechAudioStatus.PREPARING)
        playback.audioStatusChanged(SpeechAudioStatus.READY)
        tts.done()
        assertEquals(1, playback.currentParagraph.value)
        assertEquals(SpeechAudioStatus.READY, playback.paragraphAudioStatuses.value[0])
    }
    @Test fun preparedSectionsAreRetainedForSameConfigurationAndInvalidatedByVoiceOrRate() = runTest {
        val tts = FakeTts()
        val playback = ReaderPlayback(tts, listOf(SpokenParagraph("One.", 0), SpokenParagraph("Two.", 8)), Languages.ENGLISH)
        playback.prepareAll(1f, "voice-a")
        playback.start(0, 1f, "voice-a")
        assertEquals(2, playback.paragraphAudioStatuses.value.size)
        val staleCompletion = tts.done
        playback.configureAudio(1f, "voice-b")
        assertTrue(playback.paragraphAudioStatuses.value.isEmpty())
        staleCompletion()
        playback.audioStatusChanged(SpeechAudioStatus.READY)
        assertTrue(playback.paragraphAudioStatuses.value.isEmpty(), "Old-voice playback must not repopulate invalidated readiness")
        playback.prepareAll(1f, "voice-b")
        playback.startAt(8, 1.25f, "voice-b")
        assertTrue(playback.paragraphAudioStatuses.value.isEmpty())
        playback.prepareAll(1.25f, "voice-b")
        assertEquals(2, playback.paragraphAudioStatuses.value.size)
        playback.prepareParagraph(0, .75f, "voice-b")
        assertEquals(mapOf(0 to SpeechAudioStatus.READY), playback.paragraphAudioStatuses.value)
    }

}
