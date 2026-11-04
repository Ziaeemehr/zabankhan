package com.ziaee.zabankhan.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LemmaLexiconTest {
    @Test fun loadsFormsCommonLemmasAndRawOcrForms() {
        val lexicon = loadLemmaLexicon("yeux\tœil\nÉCOLES\técole\ninvalid\nyeux\toeuil", "un\nÉCOLE", Languages.FRENCH)
        assertEquals(setOf("oeil", "oeuil"), lexicon.lemmas("yeux"))
        assertEquals(setOf("ecole"), lexicon.lemmas("ecoles"))
        assertEquals(setOf("un", "ecole"), lexicon.commonLemmas)
        assertTrue("œil" in lexicon.wordForms)
        assertEquals(setOf("unknown"), lexicon.lemmas("unknown"))
    }

    @Test fun countsInflectedKnownWordsAndCommonLemmas() {
        val lexicon = loadLemmaLexicon("mangeons\tmanger", "nous", Languages.FRENCH)
        val result = ComprehensionAnalyzer(Languages.FRENCH, lexicon)
            .analyze("Nous mangeons ici.", setOf("manger"))
        assertEquals(3, result.totalWords)
        assertEquals(2, result.knownWords)
        assertEquals(200.0 / 3, result.knownWordPercent)
        assertEquals(0.0, ComprehensionAnalyzer(Languages.FRENCH, lexicon).analyze("", emptySet()).knownWordPercent)
    }

    @Test fun normalizesPersianLexiconForms() {
        val lexicon = loadLemmaLexicon("مي‌روم\tرفتن", "من", Languages.PERSIAN)
        assertEquals(setOf("رفتن"), lexicon.lemmas("می‌روم"))
    }
}
