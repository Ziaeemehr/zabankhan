package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.translate.InMemoryKeyValueCache
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchHistoryTest {
    @Test fun shadowPracticeIsLanguageScopedAndRoundTripsSeparately() = runTest {
        val history = WatchHistory(InMemoryKeyValueCache())
        val entry = RecentVideo("abc", "Clip", "Channel", 4200, subs = "captions.srt")
        history.recordShadow(Languages.FRENCH, entry)
        assertEquals(entry, history.readShadow(Languages.FRENCH))
        assertNull(history.readShadow(Languages.ENGLISH))
        assertEquals(emptyList(), history.read(Languages.FRENCH))
    }
}
