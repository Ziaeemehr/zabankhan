package com.ziaee.zabankhan.pdf

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSProcessInfo
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosPdfWordsTest {
    private val fixture = NSProcessInfo.processInfo.environment["PDF_FIXTURE"] as String

    @Test fun extractsWordsWithBoxesFromTextLayer() = runTest {
        val pdf = openPdf(fixture)
        val words = pdf.words(0)
        assertTrue(words.isNotEmpty(), "no words extracted")
        val word = assertNotNull(words.firstOrNull { it.text.startsWith("appartement") || it.text == "L'appartement" }, "words: ${words.map { it.text }}")
        assertTrue(word.rect.top in 0.05f..0.12f && word.rect.right > word.rect.left, "bad rect ${word.rect}")
        val hit = hitTestWord(words, (word.rect.left + word.rect.right) / 2, (word.rect.top + word.rect.bottom) / 2, .008f)
        assertNotNull(hit, "hit test missed ${word.rect}")
        pdf.close()
    }

    @Test fun pageWordsCacheReturnsTextLayer() = runTest {
        val pdf = openPdf(fixture)
        assertTrue(PdfPageWords(fixture, pdf, "fr").get(0).size >= 6)
        pdf.close()
    }
}
