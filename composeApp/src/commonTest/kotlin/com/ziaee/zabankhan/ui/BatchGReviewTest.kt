package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.Languages
import kotlin.test.*

class BatchGReviewTest {
    @Test fun autoplaySidesAndLegacyMigration() {
        assertFalse(AudioAutoplay.OFF.front); assertFalse(AudioAutoplay.OFF.back)
        assertTrue(AudioAutoplay.FRONT.front); assertFalse(AudioAutoplay.FRONT.back)
        assertFalse(AudioAutoplay.BACK.front); assertTrue(AudioAutoplay.BACK.back)
        assertTrue(AudioAutoplay.BOTH.front); assertTrue(AudioAutoplay.BOTH.back)
        assertEquals(AudioAutoplay.FRONT, AudioAutoplay.restored(null, true))
        assertEquals(AudioAutoplay.OFF, AudioAutoplay.restored(null, false))
        assertEquals(AudioAutoplay.BACK, AudioAutoplay.restored("BACK", true))
        assertEquals(AudioAutoplay.FRONT, AudioAutoplay.restored("invalid", true))
    }
    @Test fun englishCardUsesEnglishSentenceAndRejectsPersianFront() {
        val card = VocabEntry(lang = Languages.ENGLISH.code, word = "hello", meaning = "سلام", sentence = "Hello there!")
        assertEquals("Hello there!", flashcardShadowText(card))
        assertEquals("hello", flashcardWordAudioText(card))
        assertNull(flashcardWordAudioText(card.copy(word = "سلام")))
        assertEquals("hello", flashcardShadowText(card.copy(sentence = "سلام")))
        assertNotEquals(Languages.FRENCH.offlineSpeechModel?.url, Languages.ENGLISH.offlineSpeechModel?.url)
        assertNull(Languages.PERSIAN.offlineSpeechModel)
    }
    @Test fun cacheSizeKeepsReferenceOneDecimalPrecision() {
        assertEquals("0.0", cacheMegabytes(0))
        assertEquals("1.5", cacheMegabytes(1572864))
    }
    @Test fun pronunciationSummaryWeightsWordsAcrossAttempts() {
        assertNull(CardPronunciationStats().accuracy)
        assertEquals(75, CardPronunciationStats().after(1, 2).after(5, 6).accuracy)
    }
}
