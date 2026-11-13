package com.ziaee.zabankhan.speech.edge

import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.data.AppSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EdgePlaybackTest {
    @Test fun cancellingPlaybackReleasesCachedAudioAndResetsState() = runTest {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "edge-cancel-${Random.nextLong()}"
        val http = HttpClient(MockEngine { error("Not used") })
        try {
            val prefetchStarted = CompletableDeferred<Unit>()
            val client = object : EdgeTtsClient(http) {
                override suspend fun synthesize(text: String, voice: String, locale: String, ratePercent: Int): EdgeSpeech {
                    if (text == "Salut.") {
                        prefetchStarted.complete(Unit)
                        try { awaitCancellation() }
                        finally { withContext(NonCancellable) { delay(100) } }
                    }
                    return EdgeSpeech(byteArrayOf(1), listOf(Boundary(text, 0, 100, "SentenceBoundary")))
                }
            }
            val player = Player()
            val edge = EdgeTts(client, TtsCache(dir.toString(), maxBytes = 0), player, this)
            val playback = launch { edge.playText("a".repeat(999) + ". Salut.", Languages.FRENCH.code, 1f) }
            player.started.await()
            prefetchStarted.await()
            playback.cancelAndJoin()
            assertTrue(FileSystem.SYSTEM.list(dir).isEmpty(), "Cancelled playback must release its cache lease")
            assertTrue(player.stopped)
            assertFalse(edge.isSpeaking.value)
            assertNull(edge.speakingRange.value)
        } finally { http.close(); FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false) }
    }
    private class Player : AudioPlayer {
        override val position = MutableStateFlow(0L)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var stopped = false
        override suspend fun play(path: String) { started.complete(Unit); finish.await() }
        override fun stop() { stopped = true }
    }
    private class Device : Tts {
        override val speakingRange = MutableStateFlow<IntRange?>(null)
        override val isSpeaking = MutableStateFlow(false)
        var spoken: String? = null
        override fun speak(text: String, langCode: String, rate: Float, onError: () -> Unit, onDone: () -> Unit) { spoken = text; onDone() }
        override fun stop() { isSpeaking.value = false }
        override fun voices(langCode: String) = emptyList<TtsVoice>()
        override fun setVoice(id: String?) = Unit
    }
    @Test fun failureFallsBackAndOfflineChoiceAvoidsNetwork() = runTest {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "edge-play-${Random.nextLong()}"
        val http = HttpClient(MockEngine { error("Not used") })
        try {
            var requests = 0
            val client = object : EdgeTtsClient(http) {
                override suspend fun synthesize(text: String, voice: String, locale: String, ratePercent: Int): EdgeSpeech {
                    requests++; error("offline")
                }
            }
            val device = Device()
            val selected = SelectedTts(EdgeTts(client, TtsCache(dir.toString()), Player(), this), device, this)
            SpeechEngine.onlineUnavailable.value = false
            val done = CompletableDeferred<Unit>()
            selected.speak("Bonjour.", Languages.FRENCH.code, 1f) { done.complete(Unit) }
            done.await()
            assertEquals("Bonjour.", device.spoken)
            assertTrue(SpeechEngine.onlineUnavailable.value)
            assertFalse(selected.isSpeaking.value)
            assertEquals(1, requests)
            selected.configure(AppSettings(voiceEngine = "device"))
            val offlineDone = CompletableDeferred<Unit>()
            selected.speak("Hello.", Languages.ENGLISH.code, 1f) { offlineDone.complete(Unit) }
            offlineDone.await()
            assertEquals("Hello.", device.spoken)
            assertEquals(1, requests)
        } finally { http.close(); FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false) }
    }
    @Test fun stopDuringRequestCancelsWithoutFallbackOrStaleCompletion() = runTest {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "edge-stop-${Random.nextLong()}"
        val http = HttpClient(MockEngine { error("Not used") })
        try {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val client = object : EdgeTtsClient(http) {
                override suspend fun synthesize(text: String, voice: String, locale: String, ratePercent: Int): EdgeSpeech {
                    started.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }
            }
            val device = Device()
            val selected = SelectedTts(EdgeTts(client, TtsCache(dir.toString()), Player(), this), device, this)
            SpeechEngine.onlineUnavailable.value = false
            var completed = false
            selected.speak("Bonjour.", Languages.FRENCH.code, 1f) { completed = true }
            started.await()
            selected.stop()
            cancelled.await()
            runCurrent()
            assertFalse(completed)
            assertNull(device.spoken)
            assertFalse(SpeechEngine.onlineUnavailable.value)
            assertFalse(selected.isSpeaking.value)
        } finally { http.close(); FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false) }
    }
    @Test fun audioPositionDrivesOriginalSentenceRange() = runTest {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "edge-range-${Random.nextLong()}"
        val http = HttpClient(MockEngine { error("Not used") })
        try {
            val client = object : EdgeTtsClient(http) {
                override suspend fun synthesize(text: String, voice: String, locale: String, ratePercent: Int) =
                    EdgeSpeech(byteArrayOf(1), listOf(Boundary("Bonjour.",100,200,"SentenceBoundary"), Boundary("Salut.",400,200,"SentenceBoundary")))
            }
            val player = Player()
            val edge = EdgeTts(client, TtsCache(dir.toString()), player, this)
            val done = CompletableDeferred<Unit>()
            edge.speak("  Bonjour. Salut.", Languages.FRENCH.code, 1f) { done.complete(Unit) }
            player.started.await()
            player.position.value = 100
            runCurrent()
            assertEquals(2..9, edge.speakingRange.value)
            player.position.value = 450
            runCurrent()
            assertEquals(11..16, edge.speakingRange.value)
            player.finish.complete(Unit)
            done.await()
            assertNull(edge.speakingRange.value)
            assertFalse(edge.isSpeaking.value)
        } finally { http.close(); FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false) }
    }
}
