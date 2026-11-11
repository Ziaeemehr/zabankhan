package com.ziaee.zabankhan.ui

import com.ziaee.zabankhan.data.VocabEntry
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewScopeTest {
    private val entries = listOf(
        VocabEntry(id = 1, lang = "fr", word = "bonjour", listId = 7),
        VocabEntry(id = 2, lang = "fr", word = "merci", listId = 8),
        VocabEntry(id = 3, lang = "fr", word = "salut"),
    )

    @Test fun nullIdsKeepsWholeSelectedDeck() {
        assertEquals(listOf(1L), scopeReviewEntries(entries, 7L, null).map { it.id })
    }

    @Test fun selectedIdsStayInsideSelectedDeck() {
        assertEquals(listOf(1L), scopeReviewEntries(entries, 7L, setOf(1L, 2L)).map { it.id })
    }

    @Test fun emptyIdsNeverFallsBackToWholeDeck() {
        assertEquals(emptyList(), scopeReviewEntries(entries, 7L, emptySet()).map { it.id })
    }

    @Test fun selectedIdsCanLimitUnfiledCards() {
        assertEquals(listOf(3L), scopeReviewEntries(entries, VOCAB_SCOPE_UNFILED, setOf(3L)).map { it.id })
    }
}
