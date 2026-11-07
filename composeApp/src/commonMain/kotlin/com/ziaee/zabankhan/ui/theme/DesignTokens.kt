package com.ziaee.zabankhan.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.Font

@Immutable data class AppSpacing(
    val half: Dp = 4.dp, val xSmall: Dp = 8.dp, val small: Dp = 16.dp,
    val medium: Dp = 24.dp, val large: Dp = 32.dp, val xLarge: Dp = 48.dp,
    val gutter: Dp = 20.dp,
)
@Immutable data class AppSizes(
    val iconSmall: Dp = 18.dp, val icon: Dp = 24.dp,
    val touchTarget: Dp = 48.dp, val readerMeasure: Dp = 640.dp,
    val thumbnailSmall: Dp = 48.dp, val thumbnailMedium: Dp = 72.dp,
)
@Immutable data class AppElevations(
    val flat: Dp = 0.dp, val card: Dp = 1.dp, val raised: Dp = 3.dp, val overlay: Dp = 6.dp,
)
val EditorialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)
val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val LocalAppSizes = staticCompositionLocalOf { AppSizes() }
val LocalAppElevations = staticCompositionLocalOf { AppElevations() }
internal data class EditorialFonts(val interfaceFont: FontFamily, val latin: FontFamily)
internal val LocalEditorialFonts = staticCompositionLocalOf { EditorialFonts(FontFamily.SansSerif, FontFamily.Serif) }
object ZabankhanDesign {
    val spacing: AppSpacing @Composable @ReadOnlyComposable get() = LocalAppSpacing.current
    val sizes: AppSizes @Composable @ReadOnlyComposable get() = LocalAppSizes.current
    val elevations: AppElevations @Composable @ReadOnlyComposable get() = LocalAppElevations.current
}

/** Any Arabic-script letters require Vazirmatn, which also covers Latin in mixed headlines.
 * Digits, marks and punctuation do not select a script. No platform-specific Unicode regex. */
enum class EditorialScript { LATIN, ARABIC }
fun editorialScript(text: String): EditorialScript = if (text.any { c ->
    c.isLetter() && (c.code in 0x0600..0x06FF || c.code in 0x0750..0x077F ||
        c.code in 0x0870..0x08FF || c.code in 0xFB50..0xFDFF || c.code in 0xFE70..0xFEFF)
}) EditorialScript.ARABIC else EditorialScript.LATIN

enum class EditorialRole { ScreenTitle, SectionTitle, ArticleHeadline, ReviewWord }
@Composable internal fun literataFontFamily() = FontFamily(
    Font(Res.font.literata_regular, FontWeight.Normal),
    Font(Res.font.literata_semibold, FontWeight.SemiBold),
)
@Composable @ReadOnlyComposable
fun editorialStyle(text: String, role: EditorialRole = EditorialRole.ArticleHeadline): TextStyle {
    val fonts = LocalEditorialFonts.current
    val (size, height) = when (role) {
        EditorialRole.ScreenTitle -> 32 to 40
        EditorialRole.SectionTitle -> 24 to 30
        EditorialRole.ArticleHeadline -> 20 to 26
        EditorialRole.ReviewWord -> 36 to 46
    }
    return TextStyle(
        fontFamily = if (editorialScript(text) == EditorialScript.ARABIC) fonts.interfaceFont else fonts.latin,
        fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = height.sp,
        textDirection = TextDirection.Content, textAlign = TextAlign.Start,
    )
}
