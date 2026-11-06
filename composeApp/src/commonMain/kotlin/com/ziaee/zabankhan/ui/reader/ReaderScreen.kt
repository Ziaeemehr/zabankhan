package com.ziaee.zabankhan.ui.reader

import androidx.compose.runtime.Composable
import com.ziaee.zabankhan.ui.theme.ReaderReferenceTheme
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings

/** Selection ranges always refer to the original imported document, including Markdown syntax. */
data class ReaderSelection(val word: String, val sentence: String, val range: IntRange,
    val sentenceActions: Boolean, val sentenceRange: IntRange = range)

@Composable
fun ReaderScreen(vm: AppViewModel, settings: AppSettings, id: Long,
    onOpenVocab: () -> Unit = {}, back: () -> Unit) = ReaderReferenceTheme(settings.uiLang, settings.themeMode) {
        ReadingScreen(vm, settings, id, back, onOpenVocab)
    }
