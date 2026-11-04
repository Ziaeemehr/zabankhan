package com.ziaee.zabankhan.lang

import kotlin.test.assertEquals
import kotlin.test.Test

class ComprehensionAnalyzerTest {
    private val lexicon = InMemoryLemmaLexicon(
        forms = mapOf(
            "mangeons" to setOf("manger"),
            "yeux" to setOf("oeil"),
            "arc-en-ciel" to setOf("arc-en-ciel")
        ),
        commonLemmas = setOf("je", "nous", "le", "l'", "d'", "un", "une")
    )

    @Test
    fun tokenizerNormalizesLigaturesAndTypographicApostrophesAndSplitsElisions() {
        val tokens = tokenizeForComprehension("L’œil, jʼaime l'œuvre.", Languages.FRENCH, lexicon)

        assertEquals(listOf("l'", "oeil", "j'", "aime", "l'", "oeuvre"), tokens.map { it.surface })
    }

    @Test
    fun tokenizerKeepsKnownHyphenatedFormsAndSplitsUnknownOnes() {
        val tokens = tokenizeForComprehension("Un arc-en-ciel bleu-vert.", Languages.FRENCH, lexicon)

        assertEquals(listOf("un", "arc-en-ciel", "bleu", "vert"), tokens.map { it.surface })
    }

    @Test
    fun tokenizerDropsDigitsAndPunctuationAndSkipsMarkdownImageLines() {
        val tokens = tokenizeForComprehension("Bonjour 2026 !\n![chat](images/chat.jpg)\nNous parlons.", Languages.FRENCH, lexicon)

        assertEquals(listOf("bonjour", "nous", "parlons"), tokens.map { it.surface })
    }

    @Test
    fun properNounsAreExcludedExceptAtSentenceStart() {
        val tokens = tokenizeForComprehension("Marie arrive. Je vois Paul. « Luc répond.\"", Languages.FRENCH, lexicon)

        assertEquals(listOf("marie", "arrive", "je", "vois", "luc", "repond"), tokens.map { it.surface })
    }

    @Test
    fun lineStartsAndDialogueDashesStartSentences() {
        val tokens = tokenizeForComprehension("Ailes magiques\n\nUn jour, il part.\n« Oui » Elle rit. – Non, dit Paul.", Languages.FRENCH, lexicon)

        assertEquals(
            listOf("ailes", "magiques", "un", "jour", "il", "part", "oui", "elle", "rit", "non", "dit"),
            tokens.map { it.surface }
        )
    }

    @Test
    fun lemmaMatchingRecognizesInflectionsAndNormalizedLigatures() {
        val tokens = tokenizeForComprehension("mangeons yeux œil", Languages.FRENCH, lexicon)
        assertEquals(listOf(setOf("manger"), setOf("oeil"), setOf("oeil")), tokens.map { it.lemmas })
    }
}
