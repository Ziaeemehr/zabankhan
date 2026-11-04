package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.translate.InMemoryKeyValueCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class YoutubeLogicTest {
    private val id = "dQw4w9WgXcQ"
    @Test fun urlsAcceptAllSupportedFormsAndRejectLookalikes() {
        for (url in listOf(id, "https://www.youtube.com/watch?v=$id&t=30", "https://youtu.be/$id?si=test", "youtube.com/shorts/$id", "https://m.youtube.com/watch?feature=share&v=$id", "https://youtube.com/embed/$id", "https://youtube.com/live/$id", "https://www.youtube-nocookie.com/embed/$id")) assertEquals(id, YoutubeUrl.videoId(url), url)
        for (url in listOf("", "abc", "https://youtube.com.evil.org/watch?v=$id", "https://evil.org/$id", "https://youtube.com/watch?v=short", "https://youtu.be/${id}extra", "ftp://youtube.com/watch?v=$id")) assertNull(YoutubeUrl.videoId(url), url)
    }
    private fun response(tracks: String) = Json.parseToJsonElement("""{"videoDetails":{"title":"Title"},"captions":{"playerCaptionsTracklistRenderer":{"captionTracks":[$tracks]}}}""").jsonObject
    @Test fun selectedLanguageManualThenAsrThenTranslatable() {
        val manual = """{"baseUrl":"https://example.test/manual","languageCode":"en-GB"}"""
        val asr = """{"baseUrl":"https://example.test/asr","languageCode":"en","kind":"asr"}"""
        val other = """{"baseUrl":"https://example.test/other","languageCode":"fr","isTranslatable":true}"""
        assertEquals("en-GB", selectCaptionTrack(response("$other,$asr,$manual"), Languages.ENGLISH)?.languageCode)
        assertEquals("asr", selectCaptionTrack(response("$other,$asr"), Languages.ENGLISH)?.kind)
        assertEquals("en", selectCaptionTrack(response(other), Languages.ENGLISH)?.translateTo)
        assertNull(selectCaptionTrack(response("""{"baseUrl":"https://example.test","languageCode":"de"}"""), Languages.ENGLISH))
        assertFailsWith<IllegalArgumentException> { selectCaptionTrack(response(other), Languages.PERSIAN) }
    }
    @Test fun formatsAndLanguageAreReplacedInTimedTextUrl() {
        val url = Url(timedTextUrl(CaptionTrack("https://example.test?fmt=srv3&lang=de&tlang=de", "de", "", "en")))
        assertEquals(listOf("json3"), url.parameters.getAll("fmt")); assertEquals("en", url.parameters["tlang"])
        val request = Json.parseToJsonElement(innertubePlayerRequest(innertubeClients.first(), id, Languages.ENGLISH)).jsonObject
        assertEquals(id, request.string("videoId")); assertEquals("en", request["context"]!!.jsonObject["client"]!!.jsonObject.string("hl"))
    }
    @Test fun balancedPlayerResponseIgnoresQuotedBraces() {
        val json = extractPlayerResponse("""<script>var ytInitialPlayerResponse = {"videoDetails":{"title":"a } { \" b"}}; more();</script>""")
        assertEquals("a } { \" b", playerVideoTitle(json))
    }
    @Test fun englishSentencesAndMalformedInput() {
        assertFalse(isSentenceEnd("Dr.", "Jones", Languages.ENGLISH))
        assertTrue(isSentenceEnd("Hello!", "Next", Languages.ENGLISH))
        val json = """{"events":[{"tStartMs":0,"dDurationMs":2000,"segs":[{"utf8":"Hello! "},{"utf8":"Next.","tOffsetMs":1000}]}]}"""
        assertEquals(listOf("Hello!", "Next."), parseSubtitleFile(null, json, Languages.ENGLISH).map { it.text })
        assertTrue(YoutubeTimedTextParser.parse("not json", Languages.ENGLISH).isEmpty())
        assertTrue(SrtParser.parse("1\n00:99:00,000 --> 00:00:01,000\nBad").isEmpty())
        assertEquals(listOf("don't", " ", "stop"), tokenizeSubtitle("don't stop", Languages.ENGLISH).map { it.text })
        val matcher = IdiomMatcher(InMemoryIdiomLexicon(listOf(IdiomEntry("break the ice", "verb", "start talking", Languages.ENGLISH))), Languages.ENGLISH)
        assertEquals("break the ice", matcher.match("Let's break the ice.").single().expression)
        assertTrue(matcher.match("break. The ice").isEmpty())
    }
    @Test fun fetcherTriesIosAfterAndroidThenCachesByLanguage() = runTest {
        val requests = mutableListOf<String>()
        var posts = 0
        val engine = MockEngine { request ->
            requests += request.url.toString()
            if (request.method == HttpMethod.Post) {
                posts++
                respond(if (posts == 1) "{}" else response("""{"baseUrl":"https://captions.test/track","languageCode":"en"}""").toString())
            } else respond("""{"events":[{"tStartMs":0,"dDurationMs":1000,"segs":[{"utf8":"Hello!"}]}]}""")
        }
        val http = HttpClient(engine)
        try {
            val cache = CaptionCache(InMemoryKeyValueCache())
            val fetcher = YoutubeCaptionFetcher(http)
            assertEquals("Hello!", cache.load(id, Languages.ENGLISH, fetcher).cues.single().text)
            assertEquals(2, posts)
            cache.load(id, Languages.ENGLISH, fetcher)
            assertEquals(2, posts)
            assertTrue(requests.last().contains("fmt=json3"))
            assertNull(cache.read(id, Languages.FRENCH))
        } finally { http.close() }
    }
    @Test fun fetcherScrapesWatchPageAfterBothMobileClientsFail() = runTest {
        var posts = 0
        val http = HttpClient(MockEngine { request ->
            when {
                request.method == HttpMethod.Post -> { posts++; respond("{}") }
                request.url.host == "www.youtube.com" -> respond("var ytInitialPlayerResponse = ${response("""{"baseUrl":"https://captions.test/track","languageCode":"en"}""")};")
                else -> respond("""{"events":[{"tStartMs":0,"dDurationMs":1000,"segs":[{"utf8":"Fallback."}]}]}""")
            }
        })
        try { assertEquals("Fallback.", YoutubeCaptionFetcher(http).fetch(id, Languages.ENGLISH).cues.single().text); assertEquals(2, posts) } finally { http.close() }
    }
}

class SeekTimeGateTest {
    @Test fun samplesBeforeLatestSeekAndCachedPositionsAreDropped() {
        val gate = SeekTimeGate { 0 }
        assertEquals(9000L, gate.acceptTime(0, 9000))
        gate.seekTo(10000)
        assertNull(gate.acceptTime(0, 10000)); assertNull(gate.acceptTime(1, 9800)); assertNull(gate.acceptTime(1, 11000))
        assertEquals(10200L, gate.acceptTime(1, 10200)); assertEquals(9000L, gate.acceptTime(1, 9000))
        gate.seekTo(30000); assertNull(gate.acceptTime(1, 30000)); assertEquals(30100L, gate.acceptTime(2, 30100))
    }
    @Test fun shortLandingIsClampedAndDeadlineReleasesImpossibleTarget() {
        var now = 0L
        val gate = SeekTimeGate { now }
        gate.seekTo(60050)
        assertEquals(60050L, gate.acceptTime(1, 60000))
        now = 1999; assertNull(gate.acceptTime(1, 50000))
        now = 2000; assertEquals(60000L, gate.acceptTime(1, 60000))
        assertEquals(60000L, gate.acceptTime(1, 60000))
    }
}
