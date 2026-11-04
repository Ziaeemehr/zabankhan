package com.ziaee.zabankhan.translate.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.*
import kotlin.test.*

class LlmClientTest {
    private val success = """{"choices":[{"message":{"content":"{\"meaning\":\"ok\"}"}}]}"""
    @Test fun jsonModeRejectionRetriesWithoutParameter() = runTest {
        var count = 0
        val http = HttpClient(MockEngine { request ->
            count++
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            if (count == 1) {
                assertNotNull(body["response_format"])
                respond("""{"error":{"message":"response_format unsupported"}}""", HttpStatusCode.BadRequest)
            } else { assertNull(body["response_format"]); respond(success) }
        }) { expectSuccess = true }
        try {
            assertEquals("ok", parseMeaning(LlmClient(http, "https://example.org/v1", "model", "secret", false).complete(LlmPrompt("s", "u"))))
            assertEquals(2, count)
        } finally { http.close() }
    }
    @Test fun statusesMapToErrorsAndDoNotRediscover() = runTest {
        for ((status, kind) in listOf(401 to LlmError.Kind.UNAUTHORIZED, 402 to LlmError.Kind.PAYMENT,
            403 to LlmError.Kind.FORBIDDEN, 404 to LlmError.Kind.NOT_FOUND, 429 to LlmError.Kind.RATE_LIMIT, 500 to LlmError.Kind.BAD_RESPONSE)) {
            val http = HttpClient(MockEngine { respond("{}", HttpStatusCode.fromValue(status)) }) { expectSuccess = true }
            try {
                val client = LlmClient(http, "https://old.test", "m", null, true, refindBaseUrl = { error("must not rediscover") })
                assertEquals(kind, assertFailsWith<LlmError> { client.complete(LlmPrompt("s", "u")) }.kind)
            } finally { http.close() }
        }
    }
    @Test fun rediscoveryKeepsNewAddressAndCanConfirmSameAddress() = runTest {
        for (same in listOf(false, true)) {
            var count = 0
            var discoveries = 0
            val http = HttpClient(MockEngine { request ->
                count++
                if (count == 1) throw IOException("offline")
                assertEquals(if (same) "old.test" else "new.test", request.url.host)
                respond(success)
            })
            try {
                val client = LlmClient(http, "https://old.test", "m", null, true, refindBaseUrl = {
                    discoveries++; if (same) "https://old.test" else "https://new.test"
                })
                repeat(2) { assertEquals("ok", parseMeaning(client.complete(LlmPrompt("s", "u")))) }
                assertEquals(1, discoveries)
                assertEquals(3, count)
            } finally { http.close() }
        }
    }
    @Test fun cancellationAndMalformedResponse() = runTest {
        val http = HttpClient(MockEngine { throw CancellationException("cancelled") })
        try { assertFailsWith<CancellationException> { LlmClient(http, "https://h.test", "m", null, true).complete(LlmPrompt("s", "u")) } }
        finally { http.close() }
        for (raw in listOf("{}", "{\"meaning\":{}}", "{\"meaning\":[]}"))
            assertEquals(LlmError.Kind.BAD_RESPONSE, assertFailsWith<LlmError> { parseMeaning(parseLlmContent(raw)) }.kind)
    }
    @Test fun promptsNameSourceLanguage() {
        assertTrue(linesPrompt(listOf("Hello"), null, null, "fa", "en").system.contains("English into"))
        assertFalse(meaningPrompt("hello", null, "fa", "en").system.contains("French"))
        assertTrue(ocrFixPrompt("متن", "fa").system.contains("Persian"))
    }
}
