package com.ziaee.zabankhan.grammar

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val GrammarReferenceLightScheme = lightColorScheme(
    primary = Color(0xFF7A2432), onPrimary = Color(0xFFFFF8F6),
    primaryContainer = Color(0xFFF4DCE0), onPrimaryContainer = Color(0xFF54131F),
    secondary = Color(0xFF2F6F6A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7ECE8), onSecondaryContainer = Color(0xFF163F3C),
    tertiary = Color(0xFFA66A16), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9E3BB), onTertiaryContainer = Color(0xFF5D3908),
    background = Color(0xFFF7F1E7), onBackground = Color(0xFF211C19),
    surface = Color(0xFFFFFBF4), onSurface = Color(0xFF211C19),
    surfaceVariant = Color(0xFFEFE6D9), onSurfaceVariant = Color(0xFF655B55),
    outline = Color(0xFF8C8178), outlineVariant = Color(0xFFD8CEC3),
    error = Color(0xFFBA1A1A), onError = Color.White
)

val GrammarReferenceDarkScheme = darkColorScheme(
    primary = Color(0xFFE0A2AA), onPrimary = Color(0xFF4B101B),
    primaryContainer = Color(0xFF64202B), onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = Color(0xFF86BDB6), onSecondary = Color(0xFF0D3733),
    secondaryContainer = Color(0xFF234F4B), onSecondaryContainer = Color(0xFFA1D8D1),
    tertiary = Color(0xFFE3B66F), onTertiary = Color(0xFF432C04),
    tertiaryContainer = Color(0xFF5D410D), onTertiaryContainer = Color(0xFFFFDEA3),
    background = Color(0xFF181513), onBackground = Color(0xFFF2EAE0),
    surface = Color(0xFF211D1A), onSurface = Color(0xFFF2EAE0),
    surfaceVariant = Color(0xFF2D2825), onSurfaceVariant = Color(0xFFD2C6BC),
    outline = Color(0xFF9B9087), outlineVariant = Color(0xFF49413C),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)

@androidx.compose.runtime.Composable
internal fun GrammarFeatureTheme(uiLanguage: String, content: @androidx.compose.runtime.Composable () -> Unit) {
    val dark = com.ziaee.zabankhan.ui.theme.LocalGrammarColors.current == com.ziaee.zabankhan.ui.theme.GrammarDarkColors
    val language = if (uiLanguage == "system") androidx.compose.ui.text.intl.Locale.current.language else uiLanguage
    val family = if (language == com.ziaee.zabankhan.lang.Languages.PERSIAN.code)
        com.ziaee.zabankhan.ui.theme.appFontFamily() else androidx.compose.ui.text.font.FontFamily.SansSerif
    val base = androidx.compose.material3.Typography()
    val typography = base.copy(
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
    androidx.compose.material3.MaterialTheme(
        colorScheme = if (dark) GrammarReferenceDarkScheme else GrammarReferenceLightScheme,
        typography = typography,
        shapes = com.ziaee.zabankhan.ui.theme.EditorialShapes,
        content = content
    )
}
