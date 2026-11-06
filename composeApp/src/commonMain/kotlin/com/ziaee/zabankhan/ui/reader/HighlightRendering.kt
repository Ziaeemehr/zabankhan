package com.ziaee.zabankhan.ui.reader

import androidx.compose.ui.graphics.Color
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
    "orange" to Color(0xFFFFAB91)
)

internal fun applyHighlights(
    text: String,
    highlights: List<HighlightEntry>,
    colors: Map<String, Color> = HIGHLIGHT_PALETTE
): AnnotatedString = buildAnnotatedString {
    append(text)
    highlights.forEach { highlight ->
        val highlightColor = colors[highlight.colorKey] ?: return@forEach
        val start = highlight.startOffset.coerceIn(0, text.length)
        val end = highlight.endOffset.coerceIn(0, text.length)
        if (start < end) addStyle(SpanStyle(background = highlightColor), start, end)
    }
}

internal fun overlappingHighlightIds(
    selection: TextRange,
    highlights: List<HighlightEntry>
): List<Long> = highlights
    .filter { highlight -> selection.min < highlight.endOffset && selection.max > highlight.startOffset }
    .map { it.id }
