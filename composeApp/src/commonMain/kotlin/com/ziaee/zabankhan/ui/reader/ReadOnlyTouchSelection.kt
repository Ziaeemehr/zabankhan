package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.wordAt

/** Compose's iOS long press places a caret even in a read-only field. Select a word there. */
@Composable
internal fun Modifier.readOnlyTouchSelection(
    text: String,
    language: LanguageSpec,
    enabled: Boolean = true,
    layout: () -> TextLayoutResult?,
    onSelection: (TextRange, released: Boolean) -> Unit,
): Modifier {
    if (!needsReadOnlyTouchSelection || !enabled) return this
    val currentLayout by rememberUpdatedState(layout)
    val select by rememberUpdatedState(onSelection)
    return pointerInput(text, language) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.isConsumed || down.type == PointerType.Mouse) return@awaitEachGesture
            awaitPointerEvent(PointerEventPass.Main)
            val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            if (currentEvent.changes.count { it.pressed } != 1) return@awaitEachGesture
            if ((held.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
            val result = currentLayout() ?: return@awaitEachGesture
            val cursor = result.getOffsetForPosition(held.position)
            val offset = listOf(cursor, cursor - 1).firstOrNull {
                it in text.indices && result.getBoundingBox(it).contains(held.position)
            } ?: return@awaitEachGesture
            val word = wordAt(text, offset, language) ?: return@awaitEachGesture
            var range = TextRange(word.start, word.end)
            select(range, false)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                    change.consume()
                    if (!change.pressed) break
                    val current = currentLayout() ?: break
                    val end = current.getOffsetForPosition(change.position)
                    val next = if (end < word.start) TextRange(word.end, end) else TextRange(word.start, maxOf(word.end, end))
                    if (range != next) {
                        range = next
                        select(range, false)
                    }
                }
            } finally {
                select(range, true)
            }
        }
    }
}
