package com.ziaee.zabankhan.translate

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TranslationServiceTest {
    @Test fun myMemoryRejectsProviderErrorsReturnedWithHttpSuccess() = runTest {
        for (status in listOf(403, 429, 500)) {
            val http = HttpClient(MockEngine { request ->
                if (request.url.host == "api.mymemory.translated.net")
                    respond("""{"responseStatus":$status,"responseData":{"translatedText":"QUERY LENGTH LIMIT EXCEEDED"}}""")
                else respond("blocked", HttpStatusCode.Forbidden)
            })
            try {
                assertFailsWith<IllegalStateException> { TranslationService(http).translate("hello", "fa", "en") }
            } finally { http.close() }
        }
    }

    @Test fun googleRejectsNonStringSegments() {
        for (value in listOf("null", "123", "true")) {
            assertFailsWith<IllegalStateException> { parseGoogleResponse("[[[$value,\"hello\"]],null,\"en\"]") }
        }
    }

    @Test fun gtxJoinsSegmentsAndPreservesLanguageParameters() = runTest {
        val http = HttpClient(MockEngine { request ->
            assertEquals("es", request.url.parameters["sl"])
            assertEquals("en", request.url.parameters["tl"])
            assertEquals("hola mundo", request.url.parameters["q"])
            assertEquals("gtx", request.url.parameters["client"])
            respond("""[[["Hello ","hola"],["world","mundo"]],null,"es"]""")
        })
        try { assertEquals("Hello world", TranslationService(http).translate("hola mundo", "en", "es")) }
        finally { http.close() }
    }
    @Test fun providerFallbackOrderAndChromeNestedShape() = runTest {
        val paths = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            paths += request.url.host + request.url.encodedPath
            if (request.url.encodedPath.endsWith("/t")) respond("""[["hello","es"]]""")
            else respond("blocked", HttpStatusCode.Forbidden)
        }) { expectSuccess = true }
        try {
            assertEquals("hello", TranslationService(http).translate("hola", "en", "es"))
            assertEquals(listOf("translate.googleapis.com/translate_a/single", "clients5.google.com/translate_a/single", "clients5.google.com/translate_a/t"), paths)
        } finally { http.close() }
    }
    @Test fun myMemoryReceivesPairAndPolishesPersian() = runTest {
        val http = HttpClient(MockEngine { request ->
            if (request.url.host == "api.mymemory.translated.net") {
                assertEquals("en|fa", request.url.parameters["langpair"])
                respond("""{"responseStatus":200,"responseData":{"translatedText":"مي روم"}}""")
            } else respond("blocked", HttpStatusCode.Forbidden)
        })
        try { assertEquals("می‌روم", TranslationService(http).translate("I go", "fa", "en")) }
        finally { http.close() }
    }
    @Test fun detectionSkipsTargetLanguageAndCancellationNeverFallsBack() = runTest {
        val http = HttpClient(MockEngine { respond("""[[["different","original"]],null,"fa-IR"]""") })
        try { assertNull(TranslationService(http).translateUnlessSameLanguage("original", "fa", "auto")) }
        finally { http.close() }
        var count = 0
        val cancelled = HttpClient(MockEngine { count++; throw CancellationException("cancel") })
        try { assertFailsWith<CancellationException> { TranslationService(cancelled).translate("text", "en", "es") }; assertEquals(1, count) }
        finally { cancelled.close() }
    }
}
