package com.ziaee.zabankhan.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.resources.Res
import com.ziaee.zabankhan.resources.vazirmatn_regular
import com.ziaee.zabankhan.resources.vazirmatn_medium
import org.jetbrains.compose.resources.Font

/** Android CustomFallbackBuilder has no Compose/iOS equivalent; Persian content selects its bundled face. */
@Composable
internal actual fun readerPlatformFontFamily(language: String, serif: Boolean): FontFamily =
    if (language == Languages.PERSIAN.code) FontFamily(
        Font(Res.font.vazirmatn_regular, FontWeight.Normal),
        Font(Res.font.vazirmatn_medium, FontWeight.Medium)
    ) else if (serif) FontFamily.Serif else FontFamily.SansSerif
