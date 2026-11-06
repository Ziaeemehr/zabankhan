package com.ziaee.zabankhan.watch

import kotlin.test.*

class TranscriptExportTest {
    @Test fun srtKeepsOriginalTimingAndNormalizesLineEndings() {
        assertEquals("1\n00:00:00,000 --> 01:02:03,004\nfirst\nsecond\nthird\n\n2\n01:02:04,005 --> 01:02:05,006\nمتن\n\n",
            transcriptSrt(listOf(SubtitleCue(-1, 3_723_004, "first\r\nsecond\rthird"), SubtitleCue(3_724_005, 3_725_006, "متن"))))
    }
    @Test fun srtEmptyHasNoBlocks() { assertEquals("", transcriptSrt(emptyList())) }
    @Test fun filenamesCannotIntroducePathsAndRetainUnicode() {
        assertEquals("a_b_c_.srt", transcriptFileName("a/b\\c?"))
        assertEquals("transcript.srt", transcriptFileName("..."))
        assertEquals("متن.srt", transcriptFileName("متن"))
        assertEquals(104, transcriptFileName("a".repeat(200)).length)
    }
}
