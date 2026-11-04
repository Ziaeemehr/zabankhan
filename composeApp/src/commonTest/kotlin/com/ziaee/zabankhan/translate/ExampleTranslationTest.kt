package com.ziaee.zabankhan.translate

import com.ziaee.zabankhan.translate.llm.LlmError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.test.Test

class ExampleTranslationTest {
    private fun forbidden(): Nothing = error("Lower priority provider was called")

    @Test fun bundledBeatsLiveAndGoogleWithoutNetwork() = runTest {
        assertEquals(ExampleTranslation("included", ExampleSource.BUNDLED), resolveExampleTranslation({ null }, { "included" }, { null },
            { forbidden() }, { forbidden() }))
    }
    @Test fun overrideBeatsBundledAndPersonalCache() = runTest {
        assertEquals(ExampleTranslation("edited", ExampleSource.EDITED), resolveExampleTranslation({ "edited" }, { forbidden() }, { forbidden() },
            { forbidden() }, { forbidden() }))
    }
    @Test fun explicitlyRegeneratedPersonalCacheBeatsBundled() = runTest {
        assertEquals(ExampleTranslation("my model", ExampleSource.MODEL), resolveExampleTranslation({ null }, { forbidden() }, { "my model" },
            { forbidden() }, { forbidden() }))
    }
    @Test fun failuresExposeReason() = runTest {
        val result = resolveExampleTranslation({ null }, { null }, { null },
            { throw kotlinx.io.IOException() }, { "fallback" })
        assertEquals(ExampleTranslation("fallback", ExampleSource.GOOGLE,
            LlmError.Kind.UNREACHABLE), result)
        val bad = resolveExampleTranslation({ null }, { null }, { null },
            { error("bad") }, { "fallback" })
        assertEquals(LlmError.Kind.BAD_RESPONSE, bad.modelError)
    }
    @Test fun forceSkipsLowerProvidersAndPersonal() = runTest {
        assertEquals(ExampleTranslation("fresh", ExampleSource.MODEL),
            resolveExampleTranslation({ null }, { forbidden() }, { forbidden() }, { "fresh" }, { forbidden() }, true))
        assertEquals(ExampleTranslation("edited", ExampleSource.EDITED),
            resolveExampleTranslation({ "edited" }, { forbidden() }, { forbidden() }, { forbidden() }, { forbidden() }, true))
        try {
            resolveExampleTranslation({ null }, { forbidden() }, { forbidden() },
                { throw kotlinx.io.IOException() }, { forbidden() }, true)
            fail("Expected model failure")
        } catch (error: LlmError) {
            assertEquals(LlmError.Kind.UNREACHABLE, error.kind)
        }
    }
    @Test fun modelErrorKindsArePreserved() = runTest {
        for (kind in LlmError.Kind.values()) {
            val failure = LlmError(kind, "provider detail")
            val result = resolveExampleTranslation({ null }, { null }, { null },
                { throw LlmError(kind) }, { "google" })
            assertEquals(ExampleTranslation("google", ExampleSource.GOOGLE, kind), result)
            try {
                resolveExampleTranslation({ null }, { forbidden() }, { forbidden() },
                    { throw failure }, { forbidden() }, true)
                fail("Expected model failure")
            } catch (error: LlmError) {
                assertSame(failure, error)
            }
        }
    }
    @Test fun forceWithoutModelFailsWithoutGoogle() = runTest {
        try {
            resolveExampleTranslation({ null }, { forbidden() }, { forbidden() }, { null }, { forbidden() }, true)
            fail("Expected model failure")
        } catch (error: LlmError) {
            assertEquals(LlmError.Kind.UNREACHABLE, error.kind)
        }
    }
    @Test fun cancellationIsRethrown() = runTest {
        for (forceModel in listOf(false, true)) {
            try {
                resolveExampleTranslation({ null }, { null }, { null },
                    { throw CancellationException() }, { forbidden() }, forceModel)
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
        }
    }
    @Test fun lazyProvidersPreserveCancellation() = runTest {
        for (provider in 0..2) {
            try {
                resolveExampleTranslation(
                    { if (provider == 0) throw CancellationException() else null },
                    { if (provider == 2) throw CancellationException() else null },
                    { if (provider == 1) throw CancellationException() else null },
                    { forbidden() }, { forbidden() })
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
        }
    }
    @Test fun overrideSavedDuringModelRequestWins() = runTest {
        for (forceModel in listOf(false, true)) {
            for (fails in listOf(false, true)) {
                var edited: String? = null
                val result = resolveExampleTranslation({ edited }, { null }, { null }, {
                    edited = "new edit"
                    if (fails) throw LlmError(LlmError.Kind.RATE_LIMIT)
                    "model"
                }, { forbidden() }, forceModel)
                assertEquals(ExampleTranslation("new edit", ExampleSource.EDITED), result)
            }
        }
    }
    @Test fun overrideSavedDuringGoogleRequestWins() = runTest {
        for (fails in listOf(false, true)) {
            var edited: String? = null
            val result = resolveExampleTranslation({ edited }, { null }, { null }, { null }, {
                edited = "new edit"
                if (fails) throw kotlinx.io.IOException()
                "google"
            })
            assertEquals(ExampleTranslation("new edit", ExampleSource.EDITED), result)
        }
    }
    @Test fun noModelUsesGoogleWithoutModelError() = runTest {
        val result = resolveExampleTranslation({ null }, { null }, { null }, { null }, { "google" })
        assertEquals(ExampleSource.GOOGLE, result.source)
        assertNull(result.modelError)
    }
    @Test fun missesUseLiveThenGoogle() = runTest {
        assertEquals(ExampleTranslation("live", ExampleSource.MODEL), resolveExampleTranslation({ null }, { null }, { null }, { "live" }, { forbidden() }))
        assertEquals(ExampleTranslation("google", ExampleSource.GOOGLE), resolveExampleTranslation({ null }, { null }, { null }, { null }, { "google" }))
    }
}
