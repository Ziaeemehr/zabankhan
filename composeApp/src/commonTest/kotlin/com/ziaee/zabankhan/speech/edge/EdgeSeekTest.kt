package com.ziaee.zabankhan.speech.edge

import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.speech.AudioPlayer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.*

class EdgeSeekTest {
    private class SeekingPlayer : AudioPlayer {
        override val position = MutableStateFlow(0L)
        val starts = Channel<Pair<String, Long>>(Channel.UNLIMITED)
        override suspend fun play(path: String) = playFrom(path, 0)
        override suspend fun playFrom(path: String, startMs: Long) {
            position.value = startMs
            starts.send(path to startMs)
            try { awaitCancellation() } finally { position.value = 0 }
        }
        override fun stop() { position.value = 0 }
    }
    @Test fun tenSecondSeekCrossesSynthesizedClipsAndRewindsToPreviousClip() = runTest {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "edge-seek-${Random.nextLong()}"
        val http = HttpClient(MockEngine { error("Not used") })
        try {
            val client = object : EdgeTtsClient(http) {
                override suspend fun synthesize(text: String, voice: String, locale: String, ratePercent: Int) =
                    EdgeSpeech(byteArrayOf(1), listOf(Boundary(text, 0, 15000, "SentenceBoundary")))
            }
            val player = SeekingPlayer()
            val edge = EdgeTts(client, TtsCache(dir.toString()), player, this)
            val playback = launch { edge.playText("a".repeat(999) + ". Second clip.", Languages.ENGLISH.code, 1f) }
            val first = player.starts.receive()
            player.position.value = 8000
            assertTrue(edge.seekBy(10000))
            val next = player.starts.receive()
            assertNotEquals(first.first, next.first)
            assertEquals(3000L, next.second)
            assertTrue(edge.seekBy(-10000))
            val previous = player.starts.receive()
            assertEquals(first.first, previous.first)
            assertEquals(8000L, previous.second)
            playback.cancelAndJoin()
            assertFalse(edge.seekBy(10000))
        } finally { http.close(); FileSystem.SYSTEM.deleteRecursively(dir, mustExist = false) }
    }
    @Test fun sharedAudioRemainsPinnedUntilEveryDocumentReleasesItsOwnPin() = runTest {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "edge-pin-${Random.nextLong()}"
        val fs = FileSystem.SYSTEM
        try {
            val cache = TtsCache(dir.toString(), maxBytes = 0)
            var synthesized = 0
            suspend fun acquire() = cache.acquire("Hello.", "voice", 0) {
                synthesized++
                EdgeSpeech(byteArrayOf(1), listOf(Boundary("Hello.", 0, 15000, "SentenceBoundary")))
            }
            val clip = acquire()
            cache.setPinned(clip, true, "reader:1")
            cache.setPinned(clip, true, "reader:2")
            cache.release(clip)
            cache.unpinDocument("reader:2")
            val reused = acquire()
            assertEquals(1, synthesized, "Unpinning the second document must preserve the first document's shared audio")
            cache.release(reused)
            cache.unpinDocument("reader:1")
            val regenerated = acquire()
            assertEquals(2, synthesized)
            cache.release(regenerated)
        } finally { fs.deleteRecursively(dir, mustExist = false) }
    }

    @Test fun releasingReaderOwnershipDoesNotPinLaterSpeechOrUnpinItsOfflineAudio() = runTest {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "edge-owner-${Random.nextLong()}"
        val fs = FileSystem.SYSTEM
        val http = HttpClient(MockEngine { error("Not used") })
        try {
            val client = object : EdgeTtsClient(http) {
                override suspend fun synthesize(text: String, voice: String, locale: String, ratePercent: Int) =
                    EdgeSpeech(byteArrayOf(1), listOf(Boundary(text, 0, 15000, "SentenceBoundary")))
            }
            val edge = EdgeTts(client, TtsCache(dir.toString(), maxBytes = 0), SeekingPlayer(), this)
            edge.setAudioDocument("reader:42")
            edge.setAudioPinned(true)
            assertTrue(edge.prepare("Reader audio.", Languages.ENGLISH.code, 1f).isSuccess)
            val pinned = fs.list(dir).filter { it.name.endsWith(".pin") }
            assertEquals(1, pinned.size)
            edge.releaseAudioDocument("reader:42")
            assertTrue(edge.prepare("Flashcard audio.", Languages.ENGLISH.code, 1f).isSuccess)
            assertEquals(pinned, fs.list(dir).filter { it.name.endsWith(".pin") })
            assertEquals(1, fs.list(dir).count { it.name.endsWith(".mp3") })
        } finally { http.close(); fs.deleteRecursively(dir, mustExist = false) }
    }

}
