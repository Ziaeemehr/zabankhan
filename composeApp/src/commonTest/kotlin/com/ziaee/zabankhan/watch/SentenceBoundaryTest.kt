package com.ziaee.zabankhan.watch

import kotlin.test.*
import com.ziaee.zabankhan.lang.Languages


class SentenceBoundaryTest {
    @Test fun abbreviationsAndInitialsStayWithFollowingWords() {
        for (token in listOf("M.", "MM.", "Mme.", "Mmes.", "Mlle.", "Dr.", "Pr.", "St.", "Ste.",
                "cf.", "p.", "n°", "av.", "apr.", "J.-C.", "ex.", "J.", "É.")) {
            assertFalse(isSentenceEnd("Voici $token", "Dupont arrive.", Languages.FRENCH), token)
        }
    }

    @Test fun etcUsesFollowingToken() {
        assertFalse(isSentenceEnd("etc.", "puis on continue", Languages.FRENCH))
        assertFalse(isSentenceEnd("etc.", "2 personnes", Languages.FRENCH))
        assertTrue(isSentenceEnd("etc.", "Ensuite", Languages.FRENCH))
        assertTrue(isSentenceEnd("etc.", "", Languages.FRENCH))
    }

    @Test fun numbersAreNotSentenceBoundaries() {
        assertFalse(isSentenceEnd("3.5", "Ensuite", Languages.FRENCH))
        assertFalse(isSentenceEnd("1.000", "Ensuite", Languages.FRENCH))
        assertFalse(isSentenceEnd("3.", "5 euros", Languages.FRENCH))
        assertFalse(isSentenceEnd("1.", "000 euros", Languages.FRENCH))
        assertTrue(isSentenceEnd("Il coûte 3.5.", "Ensuite", Languages.FRENCH))
    }

    @Test fun ellipsesRequireUppercaseLookahead() {
        for (ending in listOf("…", "...")) {
            assertTrue(isSentenceEnd("Alors$ending", "« Écoute", Languages.FRENCH))
            assertFalse(isSentenceEnd("Alors$ending", "puis", Languages.FRENCH))
            assertFalse(isSentenceEnd("Alors$ending", "2 fois", Languages.FRENCH))
            assertFalse(isSentenceEnd("Alors$ending", "", Languages.FRENCH))
        }
    }

    @Test fun punctuationAndQuotesStillEndSentences() {
        for (ending in listOf(".", "!", "?", ". »", "?\"", " ! »")) {
            assertTrue(isSentenceEnd("Terminé$ending", "Suite", Languages.FRENCH))
        }
        assertFalse(isSentenceEnd("sans ponctuation", "Suite", Languages.FRENCH))
    }

    @Test fun parserLooksPastWhitespaceSegmentsForEtc() {
        val json = """{"events":[{"tStartMs":0,"dDurationMs":1000,"segs":[
            {"utf8":"etc."},{"utf8":" ","tOffsetMs":300},{"utf8":"puis","tOffsetMs":500}]}]}"""
        assertEquals(listOf(SubtitleCue(0, 1000, "etc. puis")), YoutubeTimedTextParser.parse(json, Languages.FRENCH))
    }

    @Test fun parserAndMergerShareBoundaries() {
        for ((left, right, split) in listOf(
            Triple("M.", "Dupont arrive.", false),
            Triple("etc.", "2 exemples.", false),
            Triple("etc.", "Ensuite.", true),
            Triple("Alors...", "puis.", false),
            Triple("Alors…", "Écoute.", true),
            Triple("3.", "5 euros.", false),
            Triple("1.000", "euros.", false)
        )) {
            val expected = if (split) 2 else 1
            val json = """{"events":[{"tStartMs":0,"dDurationMs":1000,"segs":[
                {"utf8":"$left"},{"utf8":" $right","tOffsetMs":500}]}]}"""
            assertEquals(expected, YoutubeTimedTextParser.parse(json, Languages.FRENCH).size, "$left $right parser")
            assertEquals(expected, mergeIntoSentences(listOf(
                SubtitleCue(0, 500, left), SubtitleCue(500, 1000, right)), Languages.FRENCH).size, "$left $right merger")
        }
    }
}
