package com.ziaee.zabankhan.watch

import kotlin.test.*
import com.ziaee.zabankhan.lang.Languages


class CaptionCachePolicyTest {
    private val old = VideoCaptions("Imported", listOf(SubtitleCue(0, 1000, "Original.")), "Channel")
    private val fresh = VideoCaptions("YouTube", listOf(SubtitleCue(0, 500, "Fresh.")))

    @Test fun currentEntriesAreUsedAsIsRegardlessOfSource() {
        for (source in listOf("youtube", "file")) {
            val cached = CachedCaptions(old, 2, source)
            assertFalse(shouldFetchCaptions(cached))
            assertEquals(CaptionCacheDecision(old, source, false), chooseCaptions(cached, fresh))
        }
    }

    @Test fun legacyEntriesAreNeverReplacedByYoutubeCaptions() {
        val cached = CachedCaptions(old, null, null)
        assertFalse(shouldFetchCaptions(cached))
        assertEquals(CaptionCacheDecision(old, "file", true), chooseCaptions(cached, fresh))
    }

    @Test fun legacyEntriesPreserveImportedCuesOnEmptyOrFailedFetch() {
        val cached = CachedCaptions(old, null, null)
        for (fetched in listOf(null, fresh.copy(cues = emptyList()))) {
            assertEquals(CaptionCacheDecision(old, "file", true), chooseCaptions(cached, fetched))
        }
    }

    @Test fun uncachedFetchOnlyWritesNonemptyCaptions() {
        assertTrue(shouldFetchCaptions(null))
        assertEquals(CaptionCacheDecision(fresh, "youtube", true), chooseCaptions(null, fresh))
        assertEquals(null, chooseCaptions(null, null))
        val empty = fresh.copy(cues = emptyList())
        assertEquals(CaptionCacheDecision(empty, "youtube", false), chooseCaptions(null, empty))
    }
}

class CaptionCacheStorageTest {
    @kotlin.test.Test fun importedCaptionsAreNeverRefetchedAndLegacyIsUpgraded() = kotlinx.coroutines.test.runTest {
        val store = com.ziaee.zabankhan.translate.InMemoryKeyValueCache()
        store.put("watch:captions:en:dQw4w9WgXcQ", """{"title":"Imported","cues":[{"startMs":0,"endMs":1000,"text":"Original."}]}""")
        val http = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { error("Should not fetch imported captions") })
        try {
            val cache = CaptionCache(store)
            val loaded = cache.load("dQw4w9WgXcQ", com.ziaee.zabankhan.lang.Languages.ENGLISH, YoutubeCaptionFetcher(http))
            assertEquals("Original.", loaded.cues.single().text)
            val saved = cache.read("dQw4w9WgXcQ", com.ziaee.zabankhan.lang.Languages.ENGLISH)!!
            assertEquals(2, saved.version); assertEquals("file", saved.source)
        } finally { http.close() }
    }
    @kotlin.test.Test fun invalidCacheIsIgnoredAndLanguagesDoNotAlias() = kotlinx.coroutines.test.runTest {
        val store = com.ziaee.zabankhan.translate.InMemoryKeyValueCache()
        store.put("watch:captions:en:id", "invalid")
        val cache = CaptionCache(store)
        assertNull(cache.read("id", com.ziaee.zabankhan.lang.Languages.ENGLISH))
        cache.write("id", com.ziaee.zabankhan.lang.Languages.ENGLISH, VideoCaptions("title", listOf(SubtitleCue(0,1000,"Hello"))), "file")
        assertNull(cache.read("id", com.ziaee.zabankhan.lang.Languages.FRENCH))
    }
}
