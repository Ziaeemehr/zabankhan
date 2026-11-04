package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.translate.InMemoryKeyValueCache
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class VideoCatalogTest {
    @Test fun feedsUseEntryFieldsDecodeEntitiesAndDeduplicate() {
        val entry = """<entry><yt:videoId>abcdefghijk</yt:videoId><title>École &amp; culture</title><published>2026-10-23</published><media:group><media:title>Wrong</media:title><media:thumbnail url="https://example.test/image?a=1&amp;b=2"/></media:group></entry>"""
        val feed = "<feed>" + entry + entry + "<entry><yt:videoId>invalid</yt:videoId><title>Wrong</title></entry>" +
            "<entry><yt:videoId>lmnopqrstuv</yt:videoId><title><![CDATA[Next & more]]></title></entry></feed>"
        val videos = parseYoutubeFeed(feed)
        assertEquals(2, videos.size)
        assertEquals("École & culture", videos.first().title)
        assertEquals("2026-10-23", videos.first().published)
        assertEquals("https://example.test/image?a=1&b=2", videos.first().thumbnail)
        assertEquals("https://i.ytimg.com/vi/lmnopqrstuv/mqdefault.jpg", videos.last().thumbnail)
        assertEquals("Next & more", videos.last().title)
    }
    @Test fun validEmptyFeedsAndInvalidPages() {
        assertTrue(parseYoutubeFeed("<feed xmlns=\"http://www.w3.org/2005/Atom\"/>").isEmpty())
        assertFailsWith<IllegalArgumentException> { parseYoutubeFeed("<html>Offline</html>") }
        assertFailsWith<IllegalArgumentException> { parseYoutubeFeed("<!DOCTYPE feed><feed/>") }
    }
    @Test fun catalogsAreLanguageSpecificAndNeverPersian() {
        assertTrue(VideoCatalog.channels(Languages.FRENCH).size > 20)
        assertEquals(6, VideoCatalog.channels(Languages.ENGLISH).size)
        assertTrue(VideoCatalog.channels(Languages.PERSIAN).isEmpty())
        for (lang in Languages.learnable) {
            val channels = VideoCatalog.channels(lang)
            assertEquals(channels.size, channels.distinctBy { it.channelId }.size)
            assertTrue(channels.all { Regex("UC[A-Za-z0-9_-]{22}").matches(it.channelId) })
        }
    }
    @Test fun recentsDeduplicateMoveToFrontMergeMetadataAndKeepTwenty() {
        val old = (0..19).map { RecentVideo("id$it", "Title $it", "Channel", it.toLong()) }
        val updated = updateRecentVideos(old, RecentVideo("id12", "", "", 4000))
        assertEquals(20, updated.size); assertEquals("id12", updated.first().videoId)
        assertEquals("Title 12", updated.first().title); assertEquals("Channel", updated.first().channel)
        assertEquals(4000L, updated.first().positionMs)
        val inserted = updateRecentVideos(updated, RecentVideo("new", "New", "", -10))
        assertEquals(20, inserted.size); assertEquals(0L, inserted.first().positionMs)
        assertFalse(inserted.any { it.videoId == "id19" })
        val locals = updateRecentVideos(listOf(RecentVideo("", "a", "", 0, "/a.mp4")), RecentVideo("", "b", "", 0, "/b.mp4"))
        assertEquals(2, locals.size)
    }
    @Test fun historyPersistsSeparatelyByLanguageAndCapsToTwenty() = runTest {
        val cache = InMemoryKeyValueCache()
        val history = WatchHistory(cache)
        repeat(22) { history.record(Languages.ENGLISH, RecentVideo("id$it", "Title $it", "", it.toLong())) }
        assertEquals(20, WatchHistory(cache).read(Languages.ENGLISH).size)
        assertEquals("id21", history.read(Languages.ENGLISH).first().videoId)
        assertTrue(history.read(Languages.FRENCH).isEmpty())
    }
}
