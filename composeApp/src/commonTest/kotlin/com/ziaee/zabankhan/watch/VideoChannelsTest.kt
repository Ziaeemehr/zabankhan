package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.lang.Languages
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class VideoChannelsTest {
    @Test fun channelUrlValidationRequiresYoutubeChannelOrHandle() {
        assertEquals("UCKqmnQFF2PgokPDdX-L2fwQ", youtubeChannelId("https://youtube.com/channel/UCKqmnQFF2PgokPDdX-L2fwQ"))
        assertTrue(validateYoutubeChannelUrl("https://youtube.com/@streamtvfilms?si=abc"))
        assertTrue(validateYoutubeChannelUrl("@streamtvfilms"))
        assertNull(youtubeChannelId("https://youtube.com/@streamtvfilms"))
        assertFalse(validateYoutubeChannelUrl("https://youtube.com/watch?v=abcdefghijk"))
        assertFalse(validateYoutubeChannelUrl("https://evil.example/@streamtvfilms"))
        assertFalse(validateYoutubeChannelUrl("https://youtube.com.evil.example/@streamtvfilms"))
        assertFalse(validateYoutubeChannelUrl("https://youtube.com:8443/@streamtvfilms"))
        assertFalse(validateYoutubeChannelUrl("https://user@youtube.com/@streamtvfilms"))
        assertFalse(validateYoutubeChannelUrl("https://youtube.com/@name/extra"))
        assertTrue(validateYoutubeChannelUrl("https://youtube.com/@café"))
        assertTrue(validateYoutubeChannelUrl("https://youtube.com/@caf%C3%A9"))
    }

    private fun page(metadata: String) = """<script>var ytInitialData = $metadata;</script>"""

    @Test fun resolvesCanonicalMetadataIgnoringDecoysAndDecodesTitle() = runTest {
        val id = "UC1234567890123456789012"
        val data = """{"decoy":{"channelId":"UCabcdefghijABCDEFGHIJKL","title":"Wrong"},"metadata":{"channelMetadataRenderer":{"externalId":"$id","title":"L'été \"écoute\" 🌿"}}}"""
        val client = HttpClient(MockEngine { request ->
            assertEquals("https", request.url.protocol.name)
            assertEquals("www.youtube.com", request.url.host)
            respond(page(data), HttpStatusCode.OK)
        })
        assertEquals(VideoChannel(id, "L'été \"écoute\" 🌿"), resolveYoutubeChannel(client, "http://youtube.com/@test"))
        client.close()
    }

    @Test fun explicitChannelIdIsFetchedAndMetadataMustMatch() = runTest {
        val id = "UC1234567890123456789012"
        var requested = ""
        val client = HttpClient(MockEngine { request ->
            requested = request.url.toString()
            respond(page("""{"metadata":{"channelMetadataRenderer":{"externalId":"$id","title":"Canonical"}}}"""))
        })
        assertEquals(VideoChannel(id, "Canonical"), resolveYoutubeChannel(client, "https://youtube.com/channel/$id"))
        assertTrue(requested.startsWith("https://www.youtube.com/channel/$id"))
        client.close()

        val mismatch = HttpClient(MockEngine {
            respond(page("""{"metadata":{"channelMetadataRenderer":{"externalId":"UCabcdefghijABCDEFGHIJKL","title":"Wrong"}}}"""))
        })
        assertFailsWith<IllegalArgumentException> { resolveYoutubeChannel(mismatch, "https://youtube.com/channel/$id") }
        mismatch.close()
    }

    @Test fun rejectsInvalidMetadataAndPropagatesCancellation() = runTest {
        for (renderer in listOf(
            """{"externalId":"UCshort","title":"No"}""",
            """{"externalId":"UC1234567890123456789012","title":"  "}""",
            """{"externalId":"UC1234567890123456789012"}""",
        )) {
            val client = HttpClient(MockEngine { respond(page("""{"metadata":{"channelMetadataRenderer":$renderer}}""")) })
            assertFailsWith<IllegalStateException> { resolveYoutubeChannel(client, "@test") }
            client.close()
        }
        val cancelled = HttpClient(MockEngine { throw CancellationException("cancelled") })
        assertFailsWith<CancellationException> { resolveYoutubeChannel(cancelled, "@test") }
        cancelled.close()
    }

    @Test fun customChannelsFilterByTopicAndDeduplicateByPermanentId() {
        val curated = listOf(
            VideoChannel("UC1234567890123456789012", "Course", category = "education"),
            VideoChannel("UCabcdefghijABCDEFGHIJKL", "Cinema", category = "films"),
        )
        val channels = mergeVideoChannels(curated, listOf(curated.last().copy(personal = true)))
        val films = filterVideoChannelsByCategory(channels, "films")
        assertEquals(listOf("Cinema"), films.map { it.title })
        assertEquals(2, channels.size)
        assertTrue(searchVideoChannels(channels, "", null, emptyMap(), Languages.FRENCH).containsAll(films))
    }

    @Test fun streamTvFilmsIsCuratedInFrenchFilmCategory() {
        val channel = VideoCatalog.channels(Languages.FRENCH).single { it.channelId == "UCKqmnQFF2PgokPDdX-L2fwQ" }
        assertEquals("films", channel.category)
        assertEquals("StreamTVFilms", channel.title)
    }
}
