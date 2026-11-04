package com.ziaee.zabankhan.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextChunkerTest {
    private fun chunk(text: String) = TextChunker.chunk(text, Languages.FRENCH)

    @Test fun removesBareUrlsAndSourceOnlyLines() {
        assertEquals(listOf("Voir pour plus."), chunk(
            "Source: https://example.com/source\n\nVoir www.example.com pour plus.\n\nLien : https://example.com"))
    }

    @Test fun keepsMarkdownLinkDestinations() {
        val text = "Lire [le texte](https://example.com/texte)."
        assertEquals(listOf(text), chunk(text))
    }

    @Test fun dropsSeparatorsAndUnspeakableBlocks() {
        assertEquals(listOf("Texte"), chunk("---\n\n* * *\n\n═══\n\n🍀👏\n\nTexte"))
        assertEquals(emptyList(), chunk("![Description](https://example.com/image.jpg)"))
        assertEquals(emptyList(), chunk("1. 🍀\n## 👏"))
    }

    @Test fun normalizesSymbolBullets() {
        val bullets = listOf("•", "▪", "◦", "‣", "●", "■", "►", "▶", "✓", "✔", "➤", "→")
        assertEquals(bullets.map { "- Point" }, chunk(bullets.joinToString("\n") { "$it Point" }))
    }

    @Test fun frenchDocumentKeepsDeterministicBlocks() {
        assertEquals(listOf("# Le 14 juillet", "## 🍀 ACTIVITÉS 🍀", "- Écoutez le texte.",
            "- **Bravo** 🎉 *vraiment*", "## 🔷 QUESTIONS :", "Voir pour plus."), chunk("""
            # Le 14 juillet
            ## 🍀 ACTIVITÉS 🍀
            • Écoutez le texte.
            • **Bravo** 🎉 *vraiment*

            ## 🔷 QUESTIONS :
            Voir www.example.com pour plus.

            Source: https://example.com/source
            ---
        """.trimIndent()))
    }

    @Test fun isolatesEpubImages() {
        assertEquals(listOf("Avant.", "![Carte](epubimg:abc123/4_2.jpg)", "Après."),
            chunk("Avant.\n![Carte](epubimg:abc123/4_2.jpg)\nAprès."))
    }

    @Test fun boundsLongParagraphsWithoutPunctuation() {
        val chunks = chunk("mot ".repeat(1000).trim())
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 1200 })
    }

    @Test fun boundsPersianParagraphsAndKeepsTablesTogether() {
        val chunks = TextChunker.chunk("این جمله است؟ ".repeat(200), Languages.PERSIAN)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 1200 })
        val table = "| A | B |\n| --- | --- |\n| ${"word ".repeat(400).trimEnd()} | C |"
        assertEquals(listOf(table), chunk(table))
    }
}
