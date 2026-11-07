package com.ziaee.zabankhan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import com.ziaee.zabankhan.resources.Res
import com.ziaee.zabankhan.resources.vazirmatn_medium
import com.ziaee.zabankhan.resources.vazirmatn_regular
import com.ziaee.zabankhan.resources.vazirmatn_semibold
import com.ziaee.zabankhan.resources.vazirmatn_bold
import org.jetbrains.compose.resources.Font

// Editorial paper palette; the primary roles carry the logo’s lapis accent.
val LightColors = lightColorScheme(
    primary = Color(0xFF24418F), onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE5F6), onPrimaryContainer = Color(0xFF0F2560),
    secondary = Color(0xFF2F6F6A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7ECE8), onSecondaryContainer = Color(0xFF163F3C),
    tertiary = Color(0xFFA66A16), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9E3BB), onTertiaryContainer = Color(0xFF5D3908),
    background = Color(0xFFF7F1E7), onBackground = Color(0xFF211C19),
    surface = Color(0xFFFFFBF4), onSurface = Color(0xFF211C19),
    surfaceVariant = Color(0xFFEFE6D9), onSurfaceVariant = Color(0xFF655B55),
    outline = Color(0xFF8C8178), outlineVariant = Color(0xFFD8CEC3),
    error = Color(0xFFBA1A1A), onError = Color.White,
    surfaceContainerLowest = Color(0xFFFFFBF4), surfaceContainerLow = Color(0xFFFFFBF4),
    surfaceContainer = Color(0xFFF7F1E7), surfaceContainerHigh = Color(0xFFF2EADF),
    surfaceContainerHighest = Color(0xFFEFE6D9),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF), onPrimary = Color(0xFF102A6B),
    primaryContainer = Color(0xFF22397A), onPrimaryContainer = Color(0xFFDFE5F6),
    secondary = Color(0xFF86BDB6), onSecondary = Color(0xFF0D3733),
    secondaryContainer = Color(0xFF234F4B), onSecondaryContainer = Color(0xFFA1D8D1),
    tertiary = Color(0xFFE3B66F), onTertiary = Color(0xFF432C04),
    tertiaryContainer = Color(0xFF5D410D), onTertiaryContainer = Color(0xFFFFDEA3),
    background = Color(0xFF181513), onBackground = Color(0xFFF2EAE0),
    surface = Color(0xFF211D1A), onSurface = Color(0xFFF2EAE0),
    surfaceVariant = Color(0xFF2D2825), onSurfaceVariant = Color(0xFFD2C6BC),
    outline = Color(0xFF9B9087), outlineVariant = Color(0xFF49413C),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    surfaceContainerLowest = Color(0xFF14110F), surfaceContainerLow = Color(0xFF211D1A),
    surfaceContainer = Color(0xFF26211E), surfaceContainerHigh = Color(0xFF2D2825),
    surfaceContainerHighest = Color(0xFF38312C),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun appFontFamily(): FontFamily = FontFamily(
    Font(Res.font.vazirmatn_regular, FontWeight.Normal),
    Font(Res.font.vazirmatn_medium, FontWeight.Medium),
    Font(Res.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(Res.font.vazirmatn_bold, FontWeight.Bold),
)

@Composable
fun ZabankhanTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val family = appFontFamily()
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family, textDirection = TextDirection.Content),
        displayMedium = base.displayMedium.copy(fontFamily = family, textDirection = TextDirection.Content),
        displaySmall = base.displaySmall.copy(fontFamily = family, textDirection = TextDirection.Content),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, textDirection = TextDirection.Content),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, textDirection = TextDirection.Content),
        headlineSmall = base.headlineSmall.copy(fontFamily = family, textDirection = TextDirection.Content),
        titleLarge = base.titleLarge.copy(fontFamily = family, textDirection = TextDirection.Content),
        titleMedium = base.titleMedium.copy(fontFamily = family, textDirection = TextDirection.Content),
        titleSmall = base.titleSmall.copy(fontFamily = family, textDirection = TextDirection.Content),
        bodyLarge = base.bodyLarge.copy(fontFamily = family, fontSize = 16.sp, lineHeight = 24.sp, textDirection = TextDirection.Content),
        bodyMedium = base.bodyMedium.copy(fontFamily = family, textDirection = TextDirection.Content),
        bodySmall = base.bodySmall.copy(fontFamily = family, fontSize = 13.sp, lineHeight = 18.sp, textDirection = TextDirection.Content),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, textDirection = TextDirection.Content),
        labelMedium = base.labelMedium.copy(fontFamily = family, textDirection = TextDirection.Content),
        labelSmall = base.labelSmall.copy(fontFamily = family, textDirection = TextDirection.Content),
    )
    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing(),
        LocalGrammarColors provides if (dark) GrammarDarkColors else GrammarLightColors,
        LocalAppSizes provides AppSizes(),
        LocalAppElevations provides AppElevations(),
        LocalEditorialFonts provides EditorialFonts(family, literataFontFamily()),
    ) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = typography, shapes = EditorialShapes) {
            // Keep a root surface so content and the iOS window inherit the theme colours.
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

/** Existing names are retained so stored preferences from older versions remain valid. */
enum class ReadingBackground {
    WHITE, SEPIA, PAPER, SAND, GRAY, MINT, SAGE, BLUE_TINT, ROSE, DARK, BLACK
}

enum class PageNavigation { SCROLL, HORIZONTAL, VERTICAL }

enum class HighlightColor { YELLOW, GREEN, BLUE, PINK, ORANGE, PURPLE, TEAL, RED }

data class ReadingPalette(
    val background: Color,
    val ink: Color,
    val inkFaded: Color,
    val highlightBg: Color,
    val highlightInk: Color,
    val divider: Color,
    val accent: Color,
    val selectionBg: Color,
    val selectionHandle: Color
)

fun backgroundSwatch(background: ReadingBackground): Color = when (background) {
    ReadingBackground.WHITE -> Color(0xFFFFFFFF)
    ReadingBackground.SEPIA -> Color(0xFFFBF6EC)
    ReadingBackground.PAPER -> Color(0xFFFFFDF5)
    ReadingBackground.SAND -> Color(0xFFF3E5C8)
    ReadingBackground.GRAY -> Color(0xFFE8E9EB)
    ReadingBackground.MINT -> Color(0xFFE5F3EA)
    ReadingBackground.SAGE -> Color(0xFFDCE7DA)
    ReadingBackground.BLUE_TINT -> Color(0xFFE5EFF8)
    ReadingBackground.ROSE -> Color(0xFFF7E9EA)
    ReadingBackground.DARK -> Color(0xFF1A1A1A)
    ReadingBackground.BLACK -> Color(0xFF0D0D0E)
}

fun highlightSwatch(color: HighlightColor, dark: Boolean = false): Color = when (color) {
    HighlightColor.YELLOW -> if (dark) Color(0xFF65562D) else Color(0xFFF2D995)
    HighlightColor.GREEN -> if (dark) Color(0xFF315A3C) else Color(0xFFAEDDB5)
    HighlightColor.BLUE -> if (dark) Color(0xFF31516B) else Color(0xFFAED5F2)
    HighlightColor.PINK -> if (dark) Color(0xFF704052) else Color(0xFFF2BCD0)
    HighlightColor.ORANGE -> if (dark) Color(0xFF74472A) else Color(0xFFF3C18E)
    HighlightColor.PURPLE -> if (dark) Color(0xFF564371) else Color(0xFFD1B9ED)
    HighlightColor.TEAL -> if (dark) Color(0xFF275D5B) else Color(0xFFA6DEDA)
    HighlightColor.RED -> if (dark) Color(0xFF713838) else Color(0xFFF1AEAA)
}

fun readingPaletteFor(
    background: ReadingBackground,
    highlightColor: HighlightColor = HighlightColor.YELLOW
): ReadingPalette {
    val bg = backgroundSwatch(background)
    val dark = bg.luminance() < 0.35f
    val ink = when (background) {
        ReadingBackground.SEPIA -> Color(0xFF2E2A22)
        ReadingBackground.WHITE -> Color(0xFF1A1A1A)
        ReadingBackground.DARK -> Color(0xFFE8E4DA)
        else -> if (dark) Color(0xFFF0ECE4) else Color(0xFF272522)
    }
    val faded = when (background) {
        ReadingBackground.SEPIA -> Color(0xFF6B6252)
        ReadingBackground.WHITE -> Color(0xFF5C5C5C)
        ReadingBackground.DARK -> Color(0xFFA8A296)
        else -> if (dark) Color(0xFFB4AEA4) else Color(0xFF68635C)
    }
    val accent = when (background) {
        ReadingBackground.SEPIA -> Color(0xFF8A6D3B)
        ReadingBackground.WHITE -> Color(0xFF3B6EA8)
        ReadingBackground.DARK -> Color(0xFFD8B978)
        else -> if (dark) Color(0xFFD8B978) else Color(0xFF7D6336)
    }
    val highlight = when {
        highlightColor == HighlightColor.YELLOW && background == ReadingBackground.WHITE -> Color(0xFFFFE7A3)
        else -> highlightSwatch(highlightColor, dark)
    }
    return ReadingPalette(
        background = bg,
        ink = ink,
        inkFaded = faded,
        highlightBg = highlight.copy(alpha = 0.72f),
        highlightInk = if (highlightColor == HighlightColor.YELLOW && background == ReadingBackground.DARK) Color(0xFFFFE4A3)
            else if (highlight.luminance() > 0.48f) ink else Color(0xFFFFF8EE),
        divider = when (background) {
            ReadingBackground.SEPIA -> Color(0xFFE6DDC8)
            ReadingBackground.WHITE -> Color(0xFFE0E0E0)
            ReadingBackground.DARK -> Color(0xFF3A3A3A)
            else -> blend(bg, ink, if (dark) 0.18f else 0.12f)
        },
        accent = accent,
        selectionBg = accent.copy(alpha = 0.28f),
        selectionHandle = accent
    )
}

private fun blend(base: Color, overlay: Color, amount: Float) = Color(
    red = base.red * (1f - amount) + overlay.red * amount,
    green = base.green * (1f - amount) + overlay.green * amount,
    blue = base.blue * (1f - amount) + overlay.blue * amount,
    alpha = 1f
)

enum class FontScale(val multiplier: Float) {
    SMALL(0.85f), MEDIUM(1f), LARGE(1.15f), XLARGE(1.3f)
}
