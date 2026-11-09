package com.ziaee.zabankhan.translate

import com.ziaee.zabankhan.translate.llm.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TranslationRepositoryTest {
    @Test fun llmWinsAndCachesSeparatelyBySourceAndTarget() = runTest {
        var calls = 0
        val http = HttpClient(MockEngine { request ->
            if (request.url.host != "model.test") return@MockEngine respond("""[[],[["noun",["alternative"],null]],"en"]""")
            calls++
            respond("""{"choices":[{"message":{"content":"{\"translations\":[\"translated\"],\"meaning\":\"dictionary\"}"}}]}""")
        })
        try {
            val llm = LlmTranslator(LlmClient(http, "https://model.test", "m", null, true), "model-id")
            val repository = TranslationRepository(http, InMemoryKeyValueCache(), llm)
            repeat(2) { assertEquals("translated", repository.translateText("hello", "en", "fa").getOrThrow()) }
            val sourced = repository.translateTextWithSource("hello", "en", "fa").getOrThrow()
            assertEquals(ExampleSource.MODEL, sourced.source)
            assertEquals("model-id", sourced.engine)
            assertEquals(1, calls)
            assertEquals("translated", repository.translateText("hello", "es", "fa").getOrThrow())
            assertEquals(2, calls)
            val live = repository.lookupWord("hello", "context", "en", "fa").getOrThrow()
            assertEquals("dictionary", live.primary)
            assertEquals("model-id", live.engine)
            assertEquals("alternative", live.senses[1].meanings.single())
            // The live lookup also translates the sentence context for sense ranking.
            assertEquals(4, calls)
            val cached = repository.lookupWord("hello", "context", "en", "fa").getOrThrow()
            assertEquals("dictionary", cached.primary)
            assertEquals("model-id", cached.engine)
            assertEquals(4, calls)
            repository.cloudEnabled = false
            assertEquals("translated", repository.translateText("hello", "en", "fa").getOrThrow())
            assertEquals(4, calls)
            assertEquals("alternative", repository.lookupWord("fresh", null, "en", "fa").getOrThrow().primary)
            assertEquals(4, calls)
        } finally { http.close() }
    }
    @Test fun modelFailureFallsBackAndCacheWorksOffline() = runTest {
        var calls = 0
        val cache = InMemoryKeyValueCache()
        val http = HttpClient(MockEngine { request ->
            calls++
            if (request.url.host == "model.test") respond("{}", HttpStatusCode.Forbidden)
            else respond("""[[["hello","hola"]],null,"es"]""")
        })
        try {
            val model = LlmTranslator(LlmClient(http, "https://model.test", "m", "key", false), "m")
            assertEquals("hello", TranslationRepository(http, cache, model).translateText("hola", "es", "en").getOrThrow())
            val offline = HttpClient(MockEngine { error("offline cache should win") })
            try {
                val sourced = TranslationRepository(offline, cache, null).translateTextWithSource("hola", "es", "en").getOrThrow()
                assertEquals("hello", sourced.text)
                assertEquals(ExampleSource.GOOGLE, sourced.source)
                assertEquals("google-gtx", sourced.engine)
            }
            finally { offline.close() }
            assertEquals(2, calls)
        } finally { http.close() }
    }
    @Test fun englishSourceAndTargetSkipHop() = runTest {
        for ((source, target) in listOf("en" to "fa", "es" to "en")) {
            var calls = 0
            val http = HttpClient(MockEngine { request ->
                calls++
                assertEquals(source, request.url.parameters["sl"])
                assertEquals(target, request.url.parameters["tl"])
                assertEquals(listOf("t", "bd"), request.url.parameters.getAll("dt"))
                respond("""[[],[["noun",["meaning"],null]],"$source"]""")
            })
            try {
                val repository = TranslationRepository(http, InMemoryKeyValueCache(), null)
                repeat(2) { assertEquals("meaning", repository.lookupWord("word", null, source, target).getOrThrow().primary) }
                assertEquals(1, calls)
            } finally { http.close() }
        }
    }
    @Test fun senseTranslationFailureFallsBackToOriginalWord() = runTest {
        val http = HttpClient(MockEngine { request ->
            when (request.url.parameters["q"]) {
                "mot" -> if (request.url.parameters["tl"] == "en") respond("""[[],[["noun",["word"],null]],"es"]""") else respond("""[[["معنی","mot"]],null,"es"]""")
                else -> respond("failed", HttpStatusCode.ServiceUnavailable)
            }
        })
        try { assertEquals("معنی", TranslationRepository(http, InMemoryKeyValueCache(), null).lookupWord("mot", null, "es", "fa").getOrThrow().primary) }
        finally { http.close() }
    }
    @Test fun overridesBeatIdentityAndAllProvidersAndCancellationPropagates() = runTest {
        val cache = InMemoryKeyValueCache()
        TranslationOverrides(cache).putOverride("word", "en", "en", "  correction  ")
        val http = HttpClient(MockEngine { error("override must win") })
        try {
            val repository = TranslationRepository(http, cache, null)
            assertEquals("  correction  ", repository.translateText("word", "en", "en").getOrThrow())
            assertEquals("override", repository.lookupWord("word", null, "en", "en").getOrThrow().engine)
        } finally { http.close() }
        val cancelled = HttpClient(MockEngine { throw CancellationException("cancelled") })
        try {
            val repository = TranslationRepository(cancelled, cache, null)
            assertFailsWith<CancellationException> { repository.translateText("word", "es", "en") }
            assertFailsWith<CancellationException> { repository.lookupWord("word", null, "es", "en") }
        } finally { cancelled.close() }
    }
    @Test fun frenchMasdarRepairIsGatedBySource() {
        assertEquals("ورزش کردن", polishMeaning("faire du sport", "ورزش کن", "fr", "fa"))
        assertEquals("ورزش کن", polishMeaning("faire du sport", "ورزش کن", "en", "fa"))
    }
}
