package com.ziaee.zabankhan.watch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchRouteTest {
    @Test fun shadowEntryUsesTheSameWatchRouteAndEncodesLocalPaths() {
        val normal = watchRoute("abcdefghijk", "/videos/a b.mp4", "/videos/a&b.srt")
        assertTrue(normal.startsWith("watch?videoId=abcdefghijk"))
        assertTrue(normal.contains("a%20b.mp4"))
        assertTrue(normal.contains("a%26b.srt"))
        assertEquals("$normal&shadow=true", watchRoute("abcdefghijk", "/videos/a b.mp4", "/videos/a&b.srt", true))
    }
}
