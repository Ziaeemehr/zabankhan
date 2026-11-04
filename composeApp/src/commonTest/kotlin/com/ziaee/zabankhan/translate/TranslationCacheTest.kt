package com.ziaee.zabankhan.translate

import com.ziaee.zabankhan.translate.llm.LlmCache
import com.ziaee.zabankhan.translate.llm.LlmError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslationCacheTest {
    @Test fun stableHashSeparatesEveryFieldAndDelimiterBoundaries() {
        val key = translationCacheKey("engine", "en", "fa", "hello")
        assertEquals(key, translationCacheKey("engine", "en", "fa", "hello"))
        assertTrue(Regex("[0-9a-f]{16}").matches(key))
        assertNotEquals(key, translationCacheKey("other", "en", "fa", "hello"))
        assertNotEquals(key, translationCacheKey("engine", "de", "fa", "hello"))
        assertNotEquals(key, translationCacheKey("engine", "en", "de", "hello"))
        assertNotEquals(key, translationCacheKey("engine", "en", "fa", "سلام"))
        assertNotEquals(translationCacheKey("a|b", "c", "d", "e"), translationCacheKey("a", "b|c", "d", "e"))
    }

    @Test fun cachePolishesOnlyPersianAndSeparatesSourceAndEngine() = runTest {
        val cache = TranslationCache(InMemoryKeyValueCache())
        cache.put("hello", "en", "fa", "one", "مي خواهم")
        assertEquals("می‌خواهم", cache.get("hello", "en", "fa", "one"))
        assertNull(cache.get("hello", "de", "fa", "one"))
        assertNull(cache.get("hello", "en", "fa", "two"))
        cache.put("hello", "en", "de", "one", "مي خواهم")
        assertEquals("مي خواهم", cache.get("hello", "en", "de", "one"))
    }

    @Test fun overridesStayVerbatimAndRemovalHidesStoredValue() = runTest {
        val overrides = TranslationOverrides(InMemoryKeyValueCache())
        overrides.putOverride("hello", "en", "fa", "  مي خواهم  ")
        assertEquals("  مي خواهم  ", overrides.getOverride("hello", "en", "fa"))
        assertNull(overrides.getOverride("hello", "de", "fa"))
        overrides.removeOverride("hello", "en", "fa")
        assertNull(overrides.getOverride("hello", "en", "fa"))
    }

    @Test fun modelCacheSeparatesContextRegenerationAndModel() = runTest {
        val cache = LlmCache(InMemoryKeyValueCache())
        cache.put("hello", "en", "fa", "one", "سلام")
        cache.putMeaning("hello", null, "en", "fa", "one", "درود")
        cache.putRegenerated("hello", "en", "fa", "one", "خوش آمدید")
        assertEquals("سلام", cache.get("hello", "en", "fa", "one"))
        assertEquals("درود", cache.getMeaning("hello", null, "en", "fa", "one"))
        assertEquals("خوش آمدید", cache.getRegenerated("hello", "en", "fa", "one"))
        assertNull(cache.getMeaning("hello", "hello world", "en", "fa", "one"))
        assertNull(cache.get("hello", "en", "fa", "two"))
    }

    @Test fun errorReasonsPreserveReferenceClassification() {
        val expected = listOf(TranslationErrorReason.MISSING_KEY, TranslationErrorReason.KEY,
            TranslationErrorReason.PAYMENT, TranslationErrorReason.FORBIDDEN, TranslationErrorReason.MODEL,
            TranslationErrorReason.RATE, TranslationErrorReason.UNREACHABLE, TranslationErrorReason.RESPONSE)
        assertEquals(expected, LlmError.Kind.entries.map(::translationErrorReason))
        assertEquals(TranslationErrorReason.RESPONSE, translationErrorReason(null))
    }
}
