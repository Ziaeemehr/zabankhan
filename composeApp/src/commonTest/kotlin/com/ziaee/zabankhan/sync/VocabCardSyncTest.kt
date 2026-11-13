package com.ziaee.zabankhan.sync

import com.ziaee.zabankhan.data.VocabEntry
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VocabCardSyncTest {
    @Test fun sentenceAndMeaningParticipateInCardConflictEquality() {
        val card = VocabEntry(lang = "en", word = "bank", sentence = "Source", meaning = "financial institution")
        assertTrue(sameCardContent(card, card.copy()))
        assertFalse(sameCardContent(card, card.copy(sentence = "Another source")))
        assertFalse(sameCardContent(card, card.copy(meaning = "river edge")))
    }
}
