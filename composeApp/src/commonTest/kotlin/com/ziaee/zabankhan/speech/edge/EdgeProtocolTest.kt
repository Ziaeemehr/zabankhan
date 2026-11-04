package com.ziaee.zabankhan.speech.edge

import com.ziaee.zabankhan.lang.Languages
import kotlin.test.*
import kotlin.time.Instant

class EdgeProtocolTest {
    @Test fun fixedGecMatchesPython() {
        // Python drm.py: unix=1735689600; +11644473600=13380163200;
        // rounded to 300s unchanged; *10000000=133801632000000000.
        // hashlib.sha256(b"1338016320000000006A5AA1D4EAFF4E9FB37E23D68491D6F4").hexdigest().upper()
        val expected = "B0EDD22C7C09868E2F24C10264A8A3EB877773A7B6040B68AFA4FBCBABEA0238"
        assertEquals(expected, EdgeProtocol.gec(Instant.parse("2025-01-01T00:00:00Z")))
        assertEquals(expected, EdgeProtocol.gec(Instant.parse("2025-01-01T00:04:59.999Z")))
        assertNotEquals(expected, EdgeProtocol.gec(Instant.parse("2025-01-01T00:05:00Z")))
    }
    @Test fun escapedSsmlAndRate() {
        val voice = Languages.FRENCH.onlineVoices.first()
        val ssml = EdgeProtocol.ssml("L'eau & <air> \"oui\"\u000b", voice, Languages.FRENCH.ttsLocale, -25, 3, 5)
        assertTrue(ssml.contains("L&apos;eau &amp; &lt;air&gt; &quot;oui&quot; "))
        assertTrue(ssml.contains("rate='-25%'"))
        assertTrue(ssml.contains("pitch='+3Hz'"))
        assertTrue(ssml.contains("volume='+5%'"))
        assertEquals(-25, EdgeProtocol.ratePercent(.75f))
        assertEquals(25, EdgeProtocol.ratePercent(1.25f))
        val config = EdgeProtocol.config("time")
        assertTrue(config.contains("\"sentenceBoundaryEnabled\":\"true\""))
        assertTrue(config.contains("\"wordBoundaryEnabled\":\"true\""))
        assertEquals("Wed Jan 01 2025 00:00:00 GMT+0000 (Coordinated Universal Time)", EdgeProtocol.timestamp(Instant.parse("2025-01-01T00:00:00Z")))
    }
    private fun binary(headers: String, audio: ByteArray): ByteArray {
        val bytes = headers.encodeToByteArray()
        return byteArrayOf((bytes.size shr 8).toByte(), bytes.size.toByte()) + bytes + audio
    }
    @Test fun binaryHeaderLengthStartsAfterPrefix() {
        val mp3 = byteArrayOf(0xff.toByte(), 0xf3.toByte(), 7, 0)
        assertContentEquals(mp3, EdgeProtocol.binaryAudio(binary("X-RequestId:123\r\nContent-Type:audio/mpeg\r\nPath:audio\r\n", mp3)))
        assertContentEquals(byteArrayOf(), EdgeProtocol.binaryAudio(binary("Path:audio\r\n", byteArrayOf())))
        assertFailsWith<IllegalArgumentException> { EdgeProtocol.binaryAudio(byteArrayOf(0)) }
        assertFailsWith<IllegalArgumentException> { EdgeProtocol.binaryAudio(byteArrayOf(1, 0, 0)) }
        assertFailsWith<IllegalArgumentException> { EdgeProtocol.binaryAudio(binary("Path:other\r\n", mp3)) }
        assertFailsWith<IllegalArgumentException> { EdgeProtocol.binaryAudio(binary("Path:audio\r\n", mp3)) }
    }
    @Test fun metadataParsesAllKindsAndConvertsTicks() {
        val values = EdgeProtocol.metadata("""{"Metadata":[
            {"Type":"SentenceBoundary","Data":{"Offset":1230000,"Duration":4560000,"text":{"Text":"Bonjour &amp; salut."}}},
            {"Type":"WordBoundary","Data":{"Offset":1300000,"Duration":500000,"text":{"Text":"Bonjour"}}},
            {"Type":"SessionEnd"}]}""")
        assertEquals(listOf(Boundary("Bonjour & salut.",123,456,"SentenceBoundary"), Boundary("Bonjour",130,50,"WordBoundary")),values)
    }
    @Test fun chunkingPreservesOriginalCoordinates() {
        val text = "  Bonjour le monde.  " + "Encore une phrase.\n".repeat(150)
        val chunks = EdgeProtocol.chunks(text, Languages.FRENCH)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.text.length <= 1000); assertEquals(it.text, text.substring(it.start, it.start + it.text.length)) }
        assertEquals(text.filterNot { it.isWhitespace() }, chunks.joinToString("") { it.text }.filterNot { it.isWhitespace() })
        assertTrue(chunks.dropLast(1).all { it.text.endsWith('.') })
        assertTrue(EdgeProtocol.chunks("x".repeat(2001), Languages.ENGLISH).all { it.text.length <= 1000 })
        val emoji = "😀".repeat(1001)
        assertTrue(EdgeProtocol.chunks(emoji, Languages.ENGLISH, 999).all { !it.text.last().isHighSurrogate() && !it.text.first().isLowSurrogate() })
        assertTrue(EdgeProtocol.chunks(" \n ", Languages.ENGLISH).isEmpty())
    }
    @Test fun repeatedSentencesAndWhitespaceMapMonotonically() {
        val text = "Salut   à tous. Salut à tous."
        val boundaries = listOf(Boundary("Salut à tous.",100,300,"SentenceBoundary"), Boundary("Salut",100,50,"WordBoundary"), Boundary("Salut à tous.",500,200,"SentenceBoundary"))
        val ranges = EdgeProtocol.ranges(text, boundaries, 20)
        assertEquals(listOf(20..34, 36..48), ranges.map { it.range })
        assertNull(EdgeProtocol.rangeAt(ranges, 99))
        assertEquals(20..34, EdgeProtocol.rangeAt(ranges, 100))
        assertNull(EdgeProtocol.rangeAt(ranges, 400))
        assertEquals(36..48, EdgeProtocol.rangeAt(ranges, 500))
        assertEquals(0..4, EdgeProtocol.ranges("Salut", listOf(Boundary("Salut",0,100,"WordBoundary"))).single().range)
    }
}
