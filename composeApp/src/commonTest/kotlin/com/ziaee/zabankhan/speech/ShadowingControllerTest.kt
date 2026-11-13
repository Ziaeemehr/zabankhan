package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.data.ShadowAttempt
import com.ziaee.zabankhan.lang.Languages
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

private class FakeRecognizer(var next: RecognizedSpeech = RecognizedSpeech("le chat", 1000)) : SpeechRecognizer {
    var calls = 0
    var failure = false
    var pending: CompletableDeferred<RecognizedSpeech>? = null
    override suspend fun listenOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> {
        calls++
        return if (failure) Result.failure(IllegalStateException("unavailable")) else Result.success(pending?.await() ?: next)
    }
    override fun stop() = Unit
}
private class FakeVoiceRecorder(var next: RecognizedSpeech = RecognizedSpeech("", 1000)) : SpeechRecognizer {
    var calls = 0
    var recorded = false
    var pending: CompletableDeferred<RecognizedSpeech>? = null
    override val hasRecording get() = recorded
    override suspend fun listenOnce(langCode: String, maxMs: Long) = error("ASR must not run")
    override suspend fun recordOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> {
        calls++
        return Result.success(pending?.await() ?: next).also { recorded = true }
    }
    override fun stop() = Unit
}
@OptIn(ExperimentalCoroutinesApi::class)
class ShadowingControllerTest {
    @Test fun recordingOnlyCompletesWithoutScoringOrSaving() = runTest {
        val recorder = FakeVoiceRecorder()
        var saves = 0
        val c = ShadowingController(this, recorder, { saves++ }, scoreRecording = false)
        c.target(Languages.ENGLISH, 2, "hello"); c.played(1000)
        assertTrue(c.listen()); advanceUntilIdle()
        assertEquals(ShadowPhase.Recorded, c.phase.value)
        assertTrue(c.hasRecording); assertEquals(1, recorder.calls); assertEquals(0, saves)
    }
    @Test fun recordingOnlyIgnoresShortRecordingsAndDropsCancelledGeneration() = runTest {
        val recorder = FakeVoiceRecorder(RecognizedSpeech("", 200))
        val c = ShadowingController(this, recorder, {}, scoreRecording = false)
        c.target(Languages.ENGLISH, null, "hello"); c.listen(); advanceUntilIdle()
        assertEquals(ShadowPhase.Idle, c.phase.value)
        recorder.next = RecognizedSpeech("", 1000); recorder.pending = CompletableDeferred()
        c.listen(); runCurrent(); c.cancel(); recorder.pending!!.complete(RecognizedSpeech("", 1000)); advanceUntilIdle()
        assertEquals(ShadowPhase.Idle, c.phase.value)
    }
    @Test fun retryWaitsForCancelledMicrophoneTeardown() = runTest {
        var sessions = 0
        var cleaning = false
        val engine = object : SpeechRecognizer {
            override suspend fun listenOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> {
                assertFalse(cleaning, "Previous microphone session is still being released")
                sessions++
                try { awaitCancellation() }
                finally {
                    withContext(NonCancellable) {
                        cleaning = true
                        delay(100)
                        cleaning = false
                    }
                }
            }
            override fun stop() = Unit
        }
        val controller = ShadowingController(this, engine, {})
        controller.target(Languages.ENGLISH, null, "hello")
        controller.listen(); runCurrent()
        controller.retry(); controller.listen(); runCurrent()
        assertEquals(1, sessions)
        advanceTimeBy(100); runCurrent()
        assertEquals(2, sessions)
        controller.cancel(); advanceUntilIdle()
    }
    @Test fun scoresSavesAndMeasuresPace() = runTest {
        val saved = mutableListOf<ShadowAttempt>()
        val c = ShadowingController(this, FakeRecognizer(), { saved += it }, { 1234 })
        c.target(Languages.FRENCH, 7, "le chat dort"); c.played(1000)
        assertTrue(c.listen()); assertFalse(c.listen()); advanceUntilIdle()
        val r = c.phase.value as ShadowPhase.Result
        assertEquals(2, r.alignment.matched); assertEquals(3, r.alignment.total)
        assertEquals(PaceRating.GOOD, r.pace!!.rating)
        assertEquals(ShadowAttempt(lang = Languages.FRENCH.code, textId = 7, sentence = "le chat dort", matched = 2, total = 3, paceRatio = 1f, timestampMs = 1234), saved.single())
    }
    @Test fun shortEmptyAndFailedRecordingsNeverSave() = runTest {
        val saved = mutableListOf<ShadowAttempt>()
        val engine = FakeRecognizer(RecognizedSpeech("le", 120))
        val c = ShadowingController(this, engine, { saved += it })
        c.target(Languages.FRENCH, 7, "le chat dort")
        c.listen(); advanceUntilIdle(); assertEquals(ShadowPhase.Idle, c.phase.value)
        engine.next = RecognizedSpeech("", 1000)
        c.listen(); advanceUntilIdle(); assertEquals(ShadowPhase.Error(true), c.phase.value)
        engine.failure = true
        c.listen(); advanceUntilIdle(); assertEquals(ShadowPhase.Error(false), c.phase.value)
        assertTrue(saved.isEmpty())
    }
    @Test fun playbackBlocksMicAndCancellationDropsResults() = runTest {
        val saved = mutableListOf<ShadowAttempt>()
        val engine = FakeRecognizer().apply { pending = CompletableDeferred() }
        val c = ShadowingController(this, engine, { saved += it })
        c.target(Languages.FRENCH, 7, "le chat dort"); c.playing()
        assertFalse(c.listen()); assertEquals(0, engine.calls)
        c.played(1000); c.listen(); runCurrent(); c.cancel()
        engine.pending!!.complete(RecognizedSpeech("le chat dort", 1000)); advanceUntilIdle()
        assertTrue(saved.isEmpty()); assertEquals(ShadowPhase.Idle, c.phase.value)
        c.target(Languages.ENGLISH, null, "hello"); assertEquals(ShadowPhase.Idle, c.phase.value)
    }
    @Test fun absentPlaybackHasNoPaceAndSaveFailureIsRetryable() = runTest {
        val c = ShadowingController(this, FakeRecognizer(), {})
        c.target(Languages.FRENCH, null, "le chat")
        c.listen(); advanceUntilIdle(); assertNull((c.phase.value as ShadowPhase.Result).pace)
        c.retry(); assertEquals(ShadowPhase.Idle, c.phase.value)
        val failed = ShadowingController(this, FakeRecognizer(), { error("disk full") })
        failed.target(Languages.FRENCH, null, "le chat"); failed.listen(); advanceUntilIdle()
        assertEquals(ShadowPhase.Error(false), failed.phase.value)
    }
    @Test fun waitsForMicrophoneTeardownBeforePlayback() = runTest {
        var cleaned = false
        val engine = object : SpeechRecognizer {
            override suspend fun listenOnce(langCode: String, maxMs: Long): Result<RecognizedSpeech> {
                try { awaitCancellation() }
                finally { withContext(NonCancellable) { delay(100); cleaned = true } }
            }
            override fun stop() = Unit
        }
        val controller = ShadowingController(this, engine, {})
        controller.target(Languages.ENGLISH, null, "hello")
        controller.listen(); runCurrent()
        // A retry can itself be cancelled while the first session still owns the microphone.
        controller.retry(); controller.listen(); runCurrent()
        val playback = launch { controller.preparePlayback(); assertTrue(cleaned) }
        runCurrent(); assertFalse(playback.isCompleted)
        advanceUntilIdle(); assertTrue(playback.isCompleted)
        assertEquals(ShadowPhase.Playing, controller.phase.value)
    }
    @Test fun languageRulesComeFromSpec() {
        val r = TranscriptAligner.align("It's a test", "its a test", Languages.ENGLISH)
        assertEquals(3, r.total); assertEquals(3, r.matched)
        assertEquals(listOf("l", "ecole"), TranscriptAligner.normalizeTokens("L’ÉCOLE", Languages.FRENCH))
        assertFailsWith<IllegalArgumentException> { learningSpeechLanguage(Languages.PERSIAN.code) }
    }
}
