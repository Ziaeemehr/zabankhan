package com.ziaee.zabankhan.speech.edge

import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.*

class TtsCacheSettingsTest {
    @Test fun clearAndAgePruningPreservePinsAndActiveLeases() = runTest {
        val fs = FileSystem.SYSTEM
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "batch-g-cache-${Random.nextLong()}"
        var clock = com.ziaee.zabankhan.data.nowMs()
        val cache = TtsCache(dir.toString(), now = { clock })
        suspend fun clip(text: String) = cache.acquire(text, "en-US", 0) {
            EdgeSpeech(byteArrayOf(1, 2, 3), listOf(Boundary(text, 0, 100, "SentenceBoundary")))
        }
        try {
            val pinned = clip("pinned")
            cache.setPinned(pinned, true, "document:1"); cache.release(pinned)
            val active = clip("active")
            val old = clip("old"); cache.release(old)
            clock += 3L * 24 * 60 * 60 * 1000
            cache.configure(50, 1)
            assertFalse(fs.exists(dir / "${old.key}.mp3"))
            assertTrue(fs.exists(dir / "${pinned.key}.mp3"))
            assertTrue(fs.exists(dir / "${active.key}.mp3"))
            cache.clearCache()
            assertTrue(fs.exists(dir / "${pinned.key}.mp3"))
            assertTrue(fs.exists(dir / "${active.key}.mp3"))
            cache.release(active)
            assertFalse(fs.exists(dir / "${active.key}.mp3"))
            cache.unpinDocument("document:1")
            cache.clearCache()
            assertEquals(0L, cache.cacheSizeBytes())
        } finally { fs.deleteRecursively(dir, mustExist = false) }
    }
}
