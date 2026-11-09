package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.translate.InMemoryKeyValueCache
import com.ziaee.zabankhan.translate.TranslationOverrides
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WatchTranslationsTest {
    @Test fun correctionPrecedesTranslatorAndRemovingItReloads() = runTest {
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
