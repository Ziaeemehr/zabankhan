package com.ziaee.zabankhan.watch

import kotlin.test.*
import com.ziaee.zabankhan.lang.Languages


class SubtitleTest {
    @Test fun srtHandlesBomMultilineAndEntities() {
        val cues = SrtParser.parse("\uFEFF1\r\n00:00:01,200 --> 00:00:03,000\r\n<b>Salut</b> &amp; toi\r\nça va ?\r\n\r\n2\r\ninvalid\r\nignored")
        assertEquals(listOf(SubtitleCue(1200, 3000, "Salut & toi\nça va ?")), cues)
    }

    @Test fun vttSkipsMetadataAndStripsInlineTimestamps() {
        val text = """
            WEBVTT
            Kind: captions

            NOTE hidden
            ignore me

            STYLE
            ::cue { color: lime; }

            first
            00:01.000 --> 00:02.500 align:start position:10%
            <c.green>l’école</c> <00:00:01.500>&#39; &eacute;

            00:03.000 --> 00:04.000
            fin
        """.trimIndent()
        assertEquals(listOf(SubtitleCue(1000, 2500, "l’école ' é"), SubtitleCue(3000, 4000, "fin")), VttParser.parse(text))
    }

    @Test fun json3MergesSegmentsAndClipsRollingOverlap() {
        val input = """{"events":[
            {"tStartMs":0,"dDurationMs":2500,"segs":[{"utf8":"Bonjour "},{"utf8":"à tous"}]},
            {"tStartMs":1000,"dDurationMs":2000,"segs":[{"utf8":"Bonjour à tous !"}]},
            {"tStartMs":1200,"dDurationMs":10,"segs":[{"utf8":"\n"}]},
            {"tStartMs":3000,"dDurationMs":500,"segs":[]}] }"""
        assertEquals(listOf(SubtitleCue(0, 1000, "Bonjour à tous"), SubtitleCue(1000, 3000, "Bonjour à tous !")), YoutubeTimedTextParser.parse(input, Languages.FRENCH))
    }

    @Test fun cueLookupUsesExclusiveEndAndHandlesGaps() {
        val cues = listOf(SubtitleCue(100, 200, "a"), SubtitleCue(300, 400, "b"))
        assertEquals(-1, findCueIndex(cues, 99))
        assertEquals(0, findCueIndex(cues, 100))
        assertEquals(-1, findCueIndex(cues, 200))
        assertEquals(-1, findCueIndex(cues, 299))
        assertEquals(1, findCueIndex(cues, 300))
        assertEquals(-1, findCueIndex(cues, 400))
        assertEquals(-1, findCueIndex(emptyList(), 100))
    }

    @Test fun tokenizerKeepsFrenchApostrophes() {
        assertEquals(listOf("l’", "école", " ", "qu'", "il", " ", "aime", ", ", "aujourd’hui", "."),
            tokenizeSubtitle("l’école qu'il aime, aujourd’hui.", Languages.FRENCH).map { it.text })
    }
}

class SubtitleOverlapTest {
    @Test fun simultaneousSrtCuesAreKeptTogether() {
        val srt = "1\n00:00:01,000 --> 00:00:03,000\n- Salut !\n\n2\n00:00:01,000 --> 00:00:03,500\n- Bonjour.\n"
        val cues = SrtParser.parse(srt)
        assertEquals(1, cues.size)
        assertEquals("- Salut !\n- Bonjour.", cues[0].text)
        assertEquals(3500L, cues[0].endMs)
    }
}

class YoutubeOffsetRegressionTest {
    @Test fun wordOffsetsSplitSentencesAndMergeFragments() {
        val input = """{
  "events": [
    {"tStartMs": 1000, "dDurationMs": 4000, "segs": [
      {"utf8": "la ", "tOffsetMs": 0},
      {"utf8": "suite. ", "tOffsetMs": 400},
      {"utf8": "La ", "tOffsetMs": 1200},
      {"utf8": "deuxième", "tOffsetMs": 1600}
    ]},
    {"tStartMs": 5000, "dDurationMs": 1000, "segs": [{"utf8": "arrive."}]}
  ]
}
"""
        val cues = YoutubeTimedTextParser.parse(input, Languages.FRENCH)
        assertEquals(listOf(SubtitleCue(1000, 2200, "la suite."), SubtitleCue(2200, 5000, "La deuxième"), SubtitleCue(5000, 6000, "arrive.")), cues)
        assertEquals(listOf(SubtitleCue(1000, 2200, "la suite."), SubtitleCue(2200, 6000, "La deuxième arrive.")), mergeIntoSentences(cues, Languages.FRENCH))
    }
    @Test fun rollingEventsIgnoreWindowsAndClipOverlap() {
        val input = """{"wireMagic": "pb3", "events": [
 {"tStartMs": 0, "dDurationMs": 50000, "id": 1, "wpWinPosId": 1, "wsWinStyleId": 1},
 {"tStartMs": 654, "dDurationMs": 2020, "segs": [{"utf8": "[musique]"}]},
 {"tStartMs": 4150, "aAppend": 1, "segs": [{"utf8": "\n"}]},
 {"tStartMs": 4160, "dDurationMs": 4160, "segs": [{"utf8": "Alors,"}, {"utf8": " là", "tOffsetMs": 400}, {"utf8": " on est en", "tOffsetMs": 800}]},
 {"tStartMs": 6600, "dDurationMs": 2370, "aAppend": 1, "segs": [{"utf8": "\n"}]},
 {"tStartMs": 6610, "dDurationMs": 4840, "segs": [{"utf8": "plein milieu de l'été"}]},
 {"tStartMs": 9000, "dDurationMs": 2490, "aAppend": 1, "segs": [{"utf8": "\n"}]},
 {"tStartMs": 9010, "dDurationMs": 3000, "segs": [{"utf8": "et vous devriez l&#39;écouter"}]}
]}
"""
        val cues = YoutubeTimedTextParser.parse(input, Languages.FRENCH)
        assertEquals(listOf("[musique]", "Alors, là on est en", "plein milieu de l'été", "et vous devriez l'écouter"), cues.map { it.text })
        assertEquals(6610L, cues[1].endMs)
        cues.zipWithNext().forEach { (a,b) -> assertTrue(a.endMs <= b.startMs) }
    }
    @Test fun noOffsetsKeepTheEventTogether() {
        val input = """{"events":[{"tStartMs":1000,"dDurationMs":4000,"segs":[{"utf8":"la suite. "},{"utf8":"La deuxième"}]}]}"""
        assertEquals(listOf(SubtitleCue(1000,5000,"la suite. La deuxième")), YoutubeTimedTextParser.parse(input, Languages.FRENCH))
    }
}
