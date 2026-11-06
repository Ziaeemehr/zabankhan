package com.ziaee.zabankhan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDirection
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.lang.isRtlText
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val ReaderReferenceLightColorScheme = lightColorScheme(
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

val ReaderReferenceDarkColorScheme = darkColorScheme(
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


private val LocalReaderUiLanguage = staticCompositionLocalOf { Languages.ENGLISH.code }

/** Keeps reference interface colours/type local to reader and dictionary, including Watch's pane. */
@Composable
fun ReaderReferenceTheme(uiLanguage: String, themeMode: String, content: @Composable () -> Unit) {
    val language = if (uiLanguage == "system") Locale.current.language else uiLanguage
    val dark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalReaderUiLanguage provides language) {
        MaterialTheme(
            colorScheme = if (dark) ReaderReferenceDarkColorScheme else ReaderReferenceLightColorScheme,
            typography = readerInterfaceTypography(language),
            shapes = EditorialShapes,
            content = content
        )
    }
}

/** System serif headings use the reference's Persian fallback on Android. */
@Composable
fun readerHeadlineFamily(text: String): FontFamily = readerPlatformFontFamily(
    if (isRtlText(text)) Languages.PERSIAN.code else LocalReaderUiLanguage.current, serif = true)

/** iOS needs an explicit Persian face for Persian content inside a Latin interface. */
@Composable
internal fun readerContentFamily(text: String): FontFamily = readerPlatformFontFamily(
    if (isRtlText(text)) Languages.PERSIAN.code else LocalReaderUiLanguage.current, serif = false)

@Composable
internal expect fun readerPlatformFontFamily(language: String, serif: Boolean): FontFamily

@Composable
private fun readerInterfaceTypography(language: String): Typography {
    val fontFamily = readerPlatformFontFamily(language, serif = false)
    return Typography().let { base ->
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            displayMedium = base.displayMedium.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            displaySmall = base.displaySmall.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            titleLarge = base.titleLarge.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            titleMedium = base.titleMedium.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            titleSmall = base.titleSmall.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            bodySmall = base.bodySmall.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            labelLarge = base.labelLarge.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            labelMedium = base.labelMedium.copy(fontFamily = fontFamily, textDirection = TextDirection.Content),
            labelSmall = base.labelSmall.copy(fontFamily = fontFamily, textDirection = TextDirection.Content)
        )
    }
}
