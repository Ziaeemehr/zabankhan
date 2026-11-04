package com.ziaee.zabankhan.speech.edge

import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.*

class TtsCacheTest {
    private val fs = FileSystem.SYSTEM
    private fun dir() = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "edge-cache-test-${Random.nextLong()}"
    private fun speech(text: String) = EdgeSpeech(ByteArray(16) { it.toByte() }, listOf(Boundary(text,0,100,"SentenceBoundary")))
    @Test fun cacheRoundtripAndIdentity() = runTest {
        val dir = dir()
        try {
            val cache = TtsCache(dir.toString())
            val a = cache.acquire("hello", "voice", 0) { speech("hello") }
            cache.release(a)
            val hit = cache.acquire("hello", "voice", 0) { error("Cache miss") }
            assertEquals(a, hit)
            assertEquals(16L, fs.metadata(hit.path.toPath()).size)
            assertNotEquals(cache.key("hello", "voice", 0), cache.key("hello", "voice", 25))
            assertNotEquals(cache.key("hello", "voice", 0), cache.key("hello", "other", 0))
            cache.release(hit)
        } finally { fs.deleteRecursively(dir, mustExist = false) }
    }
    // Adapted from FrenchReader TtsCacheTest: active leases replace its pinned entries.
    @Test fun capProtectsPlayingAndPrefetchedFilesThenEvictsReleased() = runTest {
        val dir = dir()
        try {
            val cache = TtsCache(dir.toString(), maxBytes = 0)
            val a = cache.acquire("playing", "voice", 0) { speech("playing") }
            val b = cache.acquire("prefetched", "voice", 0) { speech("prefetched") }
            assertTrue(fs.exists(dir / "${a.key}.mp3"))
            assertTrue(fs.exists(dir / "${b.key}.mp3"))
            cache.release(a)
            assertFalse(fs.exists(dir / "${a.key}.mp3"))
            assertFalse(fs.exists(dir / "${a.key}.json"))
            assertTrue(fs.exists(dir / "${b.key}.mp3"))
            cache.release(b)
            assertTrue(fs.list(dir).isEmpty())
        } finally { fs.deleteRecursively(dir, mustExist = false) }
    }
    @Test fun corruptMetadataResynthesizesAndFailedSynthesisDoesNotCommit() = runTest {
        val dir = dir()
        try {
            val cache = TtsCache(dir.toString())
            val a = cache.acquire("hello", "voice", 0) { speech("hello") }
            cache.release(a)
            fs.write(dir / "${a.key}.json") { writeUtf8("broken") }
            var called = false
            val fixed = cache.acquire("hello", "voice", 0) { called = true; speech("hello") }
            assertTrue(called)
            cache.release(fixed)
            assertFailsWith<IllegalStateException> { cache.acquire("fail", "voice", 0) { error("offline") } }
            assertFalse(fs.exists(dir / "${cache.key("fail", "voice", 0)}.json"))
        } finally { fs.deleteRecursively(dir, mustExist = false) }
    }
}
