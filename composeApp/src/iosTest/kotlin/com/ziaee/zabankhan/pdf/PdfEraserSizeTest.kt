package com.ziaee.zabankhan.pdf

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.click
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PdfEraserSizeTest {
    @Test
    fun eraserControlInVerticalToolbarChangesTheEraseReach() = runComposeUiTest {
        val size = mutableFloatStateOf(PDF_ERASER_DEFAULT_SIZE)
        val stroke = InkStroke(points = listOf(.1f, .2f, .9f, .2f))
        assertFalse(inkHit(stroke, .5f, .24f, size.floatValue))
        setContent {
            MaterialTheme {
                PdfInkWidthControl(size.floatValue, vertical = true, eraser = true) { size.floatValue = it }
            }
        }
        onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performTouchInput { click(Offset(width * .85f, centerY)) }
        runOnIdle {
            assertTrue(inkHit(stroke, .5f, .24f, size.floatValue), "Eraser size after touching the slider: ${size.floatValue}")
        }
    }
}
