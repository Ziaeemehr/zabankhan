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
