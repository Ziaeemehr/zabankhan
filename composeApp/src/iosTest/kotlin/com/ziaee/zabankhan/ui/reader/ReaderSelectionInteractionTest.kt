package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziaee.zabankhan.ui.theme.ReadingBackground
import com.ziaee.zabankhan.ui.theme.readingPaletteFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReaderSelectionInteractionTest {
    @Test
    fun longPressSelectsTextAtItsDocumentOffsetAndOpensActions() = runComposeUiTest {
        val text = "hello kitten today"
        val start = 200
        val chunk = ChunkState(
            block = ParsedBlock(BlockType.PARAGRAPH, plainText = text),
            sourceOffsets = List(text.length) { start + it },
            sentences = listOf(SentenceBoundary(text, 0.0, 0.0)),
        )
        val toolbar = SelectionToolbarController()
        val handleColor = Color.Magenta
        var selection: TextRange? = null
        var lookup: String? = null
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalTextToolbar provides toolbar) {
                    Box(Modifier.width(640.dp)) {
                        val palette = readingPaletteFor(ReadingBackground.PAPER).copy(selectionHandle = handleColor)
                        SentenceFlowText(
                            chunk = chunk,
                            isCurrentChunk = false,
                            currentPositionMs = 0,
                            fontSize = 22.sp,
                            lineHeight = 30.sp,
                            fontWeight = null,
                            color = palette.ink,
                            palette = palette,
                            savedVocabStatuses = emptyMap(),
                            highlights = emptyList(),
                            documentStartOffset = start,
                            findRanges = emptyList(),
                            activeFindRange = null,
                            onWordLookup = { word, _, _ -> lookup = word },
                            onPhraseSelected = { _, _, range -> selection = range },
                            clearSelectionSignal = 0,
                            paragraphIndex = 1,
                            justify = false,
                        )
                    }
                }
            }
        }

        val paragraph = onNodeWithText(text)
        paragraph.performTouchInput { down(center) }
        try {
            waitUntil(timeoutMillis = 3_000) { selection != null }
        } finally {
            paragraph.performTouchInput { advanceEventTime(650); up() }
        }
        runOnIdle {
            assertEquals(TextRange(start + 6, start + 12), selection)
            assertNotNull(toolbar.touchSelectionRect)
            assertNull(lookup)
        }
        val pixels = paragraph.captureToImage().toPixelMap()
        assertTrue((0 until pixels.width).any { x ->
            (0 until pixels.height).any { y -> pixels[x, y] == handleColor }
        }, "The selection handles should be visible in a later paragraph")
        paragraph.performTouchInput { click() }
        runOnIdle { assertEquals("kitten", lookup) }
    }
}
