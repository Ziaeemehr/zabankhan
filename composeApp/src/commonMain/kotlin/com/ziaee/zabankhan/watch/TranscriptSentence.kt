package com.ziaee.zabankhan.watch

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.srs.VocabStatus
import com.ziaee.zabankhan.ui.reader.boxColor
import org.jetbrains.compose.resources.stringResource

/** Stable gesture ownership keeps phrase selection alive while playback updates the active cue. */
@Composable internal fun TranscriptSentence(
    text: String, lang: LanguageSpec, highlighter: VocabHighlighter, idiomMatcher: IdiomMatcher,
    textStyle: TextStyle, current: Boolean, activeRange: IntRange?, onLayout: (TextLayoutResult) -> Unit,
    onLookup: (String) -> Unit, onCopy: (() -> Unit)?
) {
    val matches = remember(text, highlighter) { highlighter.findMatches(text) }
    val idioms = remember(text, idiomMatcher) { idiomMatcher.match(text) }
    val colors = mapOf(VocabStatus.NEW to boxColor(1), VocabStatus.LEARNING to boxColor(2), VocabStatus.REVIEWING to boxColor(4), VocabStatus.LEARNED to boxColor(5))
    val ink = MaterialTheme.colorScheme.onSurface
    val idiomColor = MaterialTheme.colorScheme.tertiary
    val annotated = remember(text, lang, matches, colors, current, activeRange, idioms, ink, idiomColor) {
        buildAnnotatedString {
            tokenizeSubtitle(text, lang).forEach { token ->
                if (token.isWord) pushStringAnnotation("word", token.text)
                append(token.text)
                if (token.isWord) pop()
            }
            matches.forEach { match -> addStyle(SpanStyle(background = colors.getValue(match.status).copy(alpha = if (match.status == VocabStatus.LEARNED) .12f else .7f),
                textDecoration = if (match.status == VocabStatus.LEARNED) null else TextDecoration.Underline), match.range.first, match.range.last + 1) }
            if (current) {
                addStyle(SpanStyle(color = ink.copy(alpha = .7f)), 0, length)
                activeRange?.let { addStyle(SpanStyle(color = ink), it.first.coerceIn(0, length), (it.last + 1).coerceIn(0, length)) }
            }
            idioms.forEach { addStyle(SpanStyle(color = idiomColor, fontWeight = FontWeight.Medium), it.startChar, it.endChar) }
        }
    }
    val latestClick by rememberUpdatedState<(Int) -> Unit>({ offset ->
        val idiom = idioms.firstOrNull { offset in it.startChar until it.endChar }
        if (idiom != null) onLookup(idiom.expression)
        else annotated.getStringAnnotations("word", offset, offset).firstOrNull()?.let { onLookup(it.item) }
    })
    val latestLookup by rememberUpdatedState(onLookup)
    val latestText by rememberUpdatedState(text)
    val words = remember(annotated) { annotated.getStringAnnotations("word", 0, text.length).map { it.start until it.end } }
    val latestWords by rememberUpdatedState(words)
    val latestCopy by rememberUpdatedState(onCopy)
    var selected by remember(text) { mutableStateOf<IntRange?>(null) }
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = .45f)
    val displayed = remember(annotated, selected, selectionColor, onCopy != null) {
        buildAnnotatedString {
            append(annotated)
            selected?.let { addStyle(SpanStyle(background = selectionColor), it.first, it.last + 1) }
            if (onCopy != null) { append("  "); appendInlineContent("copy", "\uFFFC") }
        }
    }
    val inline = mapOf("copy" to InlineTextContent(Placeholder(1.1.em, 1.1.em, PlaceholderVerticalAlign.TextCenter)) {
        Icon(Icons.Default.ContentCopy, stringResource(Res.string.watch_copy_sentence), Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
    })
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(displayed, inlineContent = inline, style = textStyle.copy(color = ink, textDirection = TextDirection.Content),
        modifier = Modifier.fillMaxWidth().alpha(if (current || selected != null) 1f else .45f).pointerInput(text, lang) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                fun offsetAt(position: androidx.compose.ui.geometry.Offset, inside: Boolean): Int? {
                    val value = layout ?: return null
                    val line = value.getLineForVerticalPosition(position.y)
                    if (inside && (position.y < 0 || position.y >= value.size.height || position.x < value.getLineLeft(line) || position.x >= value.getLineRight(line))) return null
                    return value.getOffsetForPosition(position)
                }
                var multiTouch = false
                val longPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (event.changes.size > 1) { multiTouch = true; break }
                        if (change == null || change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        if (!change.pressed) {
                            val copyRect = layout?.placeholderRects?.lastOrNull()?.inflate(12.dp.toPx())
                            if (latestCopy != null && copyRect?.contains(change.position) == true) latestCopy?.invoke()
                            else offsetAt(change.position, true)?.let { latestClick(it) }
                            break
                        }
                        if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) break
                    }
                    false
                } == null
                if (longPress && !multiTouch) {
                    val gestureText = latestText
                    val gestureWords = latestWords
                    val anchorOffset = offsetAt(down.position, true)
                    val anchor = gestureWords.indexOfFirst { anchorOffset != null && anchorOffset in it }
                    var target = anchor
                    if (anchor >= 0) selected = gestureWords[anchor]
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || latestText != gestureText || event.changes.count { it.pressed } > 1) break
                            if (anchor >= 0) {
                                val offset = offsetAt(change.position, false)
                                val word = gestureWords.indexOfFirst { offset != null && offset in it }
                                if (word >= 0) target = word
                                selected = transcriptSelectionRange(gestureWords, anchor, target)
                            }
                            event.changes.forEach { it.consume() }
                            if (!change.pressed) {
                                selected?.let { latestLookup(gestureText.substring(it).trim()) }
                                break
                            }
                        }
                    } finally { selected = null }
                }
            }
        }, onTextLayout = { layout = it; onLayout(it) })
}
