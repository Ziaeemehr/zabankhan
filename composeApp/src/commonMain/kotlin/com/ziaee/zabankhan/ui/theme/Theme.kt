package com.ziaee.zabankhan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ziaee.zabankhan.resources.Res
import com.ziaee.zabankhan.resources.vazirmatn_medium
import com.ziaee.zabankhan.resources.vazirmatn_regular
import org.jetbrains.compose.resources.Font

// Palette taken from the app logo: deep navy, royal blue, white ribbon.
val LightColors = lightColorScheme(
    primary = Color(0xFF1F4FB5), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF), onPrimaryContainer = Color(0xFF0B1F5C),
    secondary = Color(0xFF2E6F8E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EAF5), onSecondaryContainer = Color(0xFF0E3446),
    tertiary = Color(0xFFA66A16), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9E3BB), onTertiaryContainer = Color(0xFF5D3908),
    background = Color(0xFFF6F7FB), onBackground = Color(0xFF151A26),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF151A26),
    surfaceVariant = Color(0xFFE6E9F2), onSurfaceVariant = Color(0xFF545B6E),
    outline = Color(0xFF7D8499), outlineVariant = Color(0xFFCCD1DE),
    error = Color(0xFFBA1A1A), onError = Color.White,
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF9FAFD),
    surfaceContainer = Color(0xFFEFF2F9), surfaceContainerHigh = Color(0xFFE9ECF5),
    surfaceContainerHighest = Color(0xFFE3E7F1),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC4FF), onPrimary = Color(0xFF0B225F),
    primaryContainer = Color(0xFF1C3C8F), onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF93CDE6), onSecondary = Color(0xFF0B3446),
    secondaryContainer = Color(0xFF1F4D63), onSecondaryContainer = Color(0xFFD3EAF5),
    tertiary = Color(0xFFE3B66F), onTertiary = Color(0xFF432C04),
    tertiaryContainer = Color(0xFF5D410D), onTertiaryContainer = Color(0xFFFFDEA3),
    background = Color(0xFF0A1024), onBackground = Color(0xFFE4E8F4),
    surface = Color(0xFF111A33), onSurface = Color(0xFFE4E8F4),
    surfaceVariant = Color(0xFF1D2744), onSurfaceVariant = Color(0xFFBEC5D9),
    outline = Color(0xFF8A92A8), outlineVariant = Color(0xFF353F5C),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    surfaceContainerLowest = Color(0xFF070C1C), surfaceContainerLow = Color(0xFF0E162D),
    surfaceContainer = Color(0xFF141E3A), surfaceContainerHigh = Color(0xFF1A2544),
    surfaceContainerHighest = Color(0xFF212D4F),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun appFontFamily(): FontFamily = FontFamily(
    Font(Res.font.vazirmatn_regular, FontWeight.Normal),
    Font(Res.font.vazirmatn_medium, FontWeight.Medium),
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
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
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
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = typography, content = content)
}
