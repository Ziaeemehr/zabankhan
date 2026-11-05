package com.ziaee.zabankhan.pdf

import kotlin.test.assertEquals
import kotlin.test.Test

class PdfTextCleanerTest {
    @Test
    fun preservesRepeatedBodyLines() {
        assertEquals(
            "First paragraph. Repeated quotation. First ending.\n\nSecond paragraph. Repeated quotation. Second ending.",
            PdfTextCleaner.cleanPages(listOf(
                "First paragraph.\nRepeated quotation.\nFirst ending.",
                "Second paragraph.\nRepeated quotation.\nSecond ending."
            ))
        )
    }
    @Test
    fun joinsWordsHyphenatedAcrossLineBreaks() {
        assertEquals("Un apprentissage utile.", PdfTextCleaner.clean("Un appren-\ntissage utile."))
    }

    @Test
    fun continuesParagraphAndHyphenatedWordAcrossPages() {
        assertEquals(
            "Une phrase qui continue ici. Fin.",
            PdfTextCleaner.cleanPages(listOf("Une phrase qui con-", "tinue ici. Fin."))
        )
        assertEquals(
            "Premier.\n\nSecond.",
            PdfTextCleaner.cleanPages(listOf("Premier.", "Second."))
        )
    }

    @Test
    fun keepsHyphenBeforeUppercaseLineStart() {
        assertEquals("axe-Nord", PdfTextCleaner.clean("axe-\nNord"))
    }

    @Test
    fun mergesSoftWrappedLinesButKeepsParagraphBreaks() {
        assertEquals(
            "Première ligne continuée ici.\n\nNouveau paragraphe.",
            PdfTextCleaner.clean("Première ligne\ncontinuée ici.\n\nNouveau paragraphe.")
        )
    }

    @Test
    fun removesRepeatedHeadersFootersAndPageNumbers() {
        val pages = listOf(
            "Le Petit Livre\nPremier contenu.\n1\nÉditions Exemple",
            "Le Petit Livre\nDeuxième contenu.\n2\nÉditions Exemple",
            "Le Petit Livre\nTroisième contenu.\n3\nÉditions Exemple"
        )

        assertEquals(
            "Premier contenu.\n\nDeuxième contenu.\n\nTroisième contenu.",
            PdfTextCleaner.cleanPages(pages)
        )
    }

    @Test
    fun blankInputProducesBlankOutput() {
        assertEquals("", PdfTextCleaner.clean("\r\n\u000c  \n"))
        assertEquals("", PdfTextCleaner.cleanPages(emptyList()))
    }
}
