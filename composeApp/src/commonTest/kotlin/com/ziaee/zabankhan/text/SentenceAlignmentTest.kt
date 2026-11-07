package com.ziaee.zabankhan.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SentenceAlignmentTest {
    @Test fun preservesSymbolsWithFollowingSentence() {
        assertEquals(listOf("Hello!", "✅ Next."), alignSentencesToDisplay("Hello! ✅ Next.", "Hello! Next.", listOf("Hello!", "Next.")))
    }
    @Test fun unlocatableEngineSentenceFallsBack() {
        assertNull(alignSentencesToDisplay("✅ Hello!", "Hello!", listOf("different")))
    }
}
