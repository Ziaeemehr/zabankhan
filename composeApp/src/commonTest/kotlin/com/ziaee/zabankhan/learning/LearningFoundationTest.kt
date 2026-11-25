package com.ziaee.zabankhan.learning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearningFoundationTest {
    @Test
    fun textAnswersNormalizeNfcApostrophesAndSpacingButPreserveAccents() {
        val answers = setOf("C’était déjà fini.", "C’était terminé.")
        val policy = TextAnswerPolicy(ignoreCase = true, ignoreFinalPunctuation = true)
        assertTrue(matchesLearningAnswer("  C'E\u0301TAIT  DÉJÀ FINI  ", answers, policy))
        assertTrue(matchesLearningAnswer("C'était terminé!", answers, policy))
        assertFalse(matchesLearningAnswer("C'etait deja fini", answers, policy))
        assertFalse(matchesLearningAnswer("C'était déjà fini?", answers))
    }

    @Test
    fun selectionRequiresExactDistinctOptionIds() {
        assertTrue(matchesLearningSelection(listOf("b", "a"), setOf("a", "b")))
        assertFalse(matchesLearningSelection(listOf("a", "a", "b"), setOf("a", "b")))
        assertFalse(matchesLearningSelection(listOf("a"), setOf("a", "b")))
    }

    @Test
    fun offlineCatalogFiltersByLanguageAndRequiresPersian() {
        val repository = LocalLearningResources.fromJson(catalog)
        assertEquals(listOf("fr.magazine.sample"), repository.entries("fr").map { it.id })
        assertTrue(repository.entries("de").isEmpty())
        assertTrue(repository.entries("fr").single().illustrated)
        assertFailsWith<IllegalArgumentException> {
            LocalLearningResources.fromJson(catalog.replace("\"fa\"", "\"en\""))
        }
        assertFailsWith<IllegalArgumentException> {
            LocalLearningResources.fromJson(catalog.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))
        }
    }

    private val catalog = """
        {"schemaVersion":1,"entries":[{
          "id":"fr.magazine.sample","language":"fr","type":"magazine",
          "audience":"teenagers","illustrated":true,"series":"Example","levels":["B2"],
          "descriptions":{"fa":"مجله"},"provenance":"Development fixture",
          "rights":"unknown","generation":"llm","status":"draft"
        }]}
    """.trimIndent()
}
