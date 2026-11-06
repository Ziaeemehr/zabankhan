package com.ziaee.zabankhan.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class EditorialScriptTest {
    @Test fun latinHeadlinesUseLiterataRegardlessOfInterfaceLanguage() {
        listOf("L’été à Paris", "Hello world", "2026 — Économie", "œuvre").forEach {
            assertEquals(EditorialScript.LATIN, editorialScript(it))
        }
    }
    @Test fun arabicLettersUseVazirmatnIncludingMixedHeadlines() {
        listOf("زبان‌خوان", "العربية", "Bonjour — سلام", "۱۲۳: مطالعه", "ﻻ").forEach {
            assertEquals(EditorialScript.ARABIC, editorialScript(it))
        }
    }
    @Test fun punctuationDigitsAndCombiningMarksDoNotSelectArabicFont() {
        listOf("", "2026 / ۱۲۳", "— … !", "e\u0301", "\u064E").forEach {
            assertEquals(EditorialScript.LATIN, editorialScript(it))
        }
    }
}
