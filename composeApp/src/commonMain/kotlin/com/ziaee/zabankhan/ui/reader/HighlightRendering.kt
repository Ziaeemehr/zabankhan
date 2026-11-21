package com.ziaee.zabankhan.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import com.ziaee.zabankhan.data.HighlightEntry

internal val HIGHLIGHT_PALETTE = linkedMapOf(
    "yellow" to Color(0xFFFFE082),
    "green" to Color(0xFFA5D6A7),
    "blue" to Color(0xFF90CAF9),
    "pink" to Color(0xFFF48FB1),
    "orange" to Color(0xFFFFAB91),
    "amber" to Color(0xFFFFCC80),
    "lime" to Color(0xFFDCE775),
    "mint" to Color(0xFF80CBC4),
    "cyan" to Color(0xFF80DEEA),
    "lavender" to Color(0xFFB39DDB),
    "plum" to Color(0xFF6A3D79),
    "navy" to Color(0xFF26456E)
)

internal fun Color.toHighlightKey(): String =
    "#" + toArgb().toUInt().toString(16).padStart(8, '0').uppercase()

internal fun applyHighlights(
    text: String,
    highlights: List<HighlightEntry>,
    colors: Map<String, Color> = HIGHLIGHT_PALETTE,
    surface: Color = Color.White
): AnnotatedString = buildAnnotatedString {
    append(text)
    highlights.forEach { highlight ->
        val key = highlight.colorKey
        val highlightColor = colors[key] ?: key.takeIf { it.length == 9 && it.startsWith('#') }
            ?.substring(1)?.toUIntOrNull(16)?.let { Color(it.toInt()) } ?: return@forEach
        val background = highlightColor.compositeOver(surface)
        val luminance = background.luminance()
        // At this luminance, WCAG contrast is equal for black and white.
        val ink = if (luminance >= 0.179f) Color.Black else Color.White
        val start = highlight.startOffset.coerceIn(0, text.length)
        val end = highlight.endOffset.coerceIn(0, text.length)
        if (start < end) addStyle(SpanStyle(background = background, color = ink), start, end)
    }
}

internal fun overlappingHighlightIds(
    selection: TextRange,
    highlights: List<HighlightEntry>
): List<Long> = highlights
    .filter { highlight -> selection.min < highlight.endOffset && selection.max > highlight.startOffset }
    .map { it.id }
