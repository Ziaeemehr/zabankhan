package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.translate.InMemoryKeyValueCache
import com.ziaee.zabankhan.translate.TranslationOverrides
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WatchTranslationPrefetchTest {
    private val units = listOf("one", "two", "three", "four").mapIndexed { i, text -> SubtitleCue(i * 1000L, (i + 1) * 1000L, text) }

    @Test fun windowStartsAtCurrentAndClampsAtEnd() {
        assertEquals(listOf("two", "three", "four"), translationPrefetchWindow(units, 1))
        assertEquals(listOf("four"), translationPrefetchWindow(units, 3))
        assertEquals(emptyList(), translationPrefetchWindow(units, -1))
        assertEquals(emptyList(), translationPrefetchWindow(units, 4))
    }

    @Test fun windowDeduplicatesOnlyWithinThreeUnits() {
        assertEquals(listOf("one", "three"), translationPrefetchWindow(listOf(units[0], units[0], units[2], units[3]), 0))
    }

    @Test fun correctionPrecedesTranslatorAndAutomaticRestoresIt() = runTest {
        val overrides = TranslationOverrides(InMemoryKeyValueCache())
        overrides.putOverride("one", "source", "target", "corrected")
        var calls = 0
        val translations = WatchTranslations(overrides, "source", "target") { calls++; Result.success("automatic") }
        translations.load("one")
        assertEquals(0, calls)
        assertEquals("corrected", translations.states["one"]?.text)
        assertTrue(translations.states["one"]!!.corrected)
        translations.save("one", null)
        assertEquals(1, calls)
        assertEquals("automatic", translations.states["one"]?.text)
        assertFalse(translations.states["one"]!!.corrected)
    }

    @Test fun cacheAvoidsRepeatsAndFailureRequiresRetry() = runTest {
        var calls = 0
        val translations = WatchTranslations(TranslationOverrides(InMemoryKeyValueCache()), "a", "b") {
            calls++
            if (calls == 1) Result.failure(IllegalStateException()) else Result.success("done")
        }
        translations.load("one"); translations.load("one")
        assertEquals(1, calls)
        assertTrue(translations.states["one"]!!.failed)
        translations.load("one", retry = true)
        translations.load("one")
        assertEquals(2, calls)
        assertEquals("done", translations.states["one"]?.text)
    }
}
