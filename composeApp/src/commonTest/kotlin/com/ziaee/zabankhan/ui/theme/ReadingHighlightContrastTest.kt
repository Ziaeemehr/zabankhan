package com.ziaee.zabankhan.ui.theme

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

class ReadingHighlightContrastTest {
    @Test
    fun highlightTextHasReadableContrastOnEveryReadingBackground() {
        for (background in ReadingBackground.entries) {
            for (highlight in HighlightColor.entries) {
                val palette = readingPaletteFor(background, highlight)
                val fill = palette.highlightBg.compositeOver(palette.background).luminance()
                val ink = palette.highlightInk.luminance()
                val contrast = (max(fill, ink) + 0.05f) / (min(fill, ink) + 0.05f)
                assertTrue(contrast >= 4.5f, "$background / $highlight contrast is $contrast")
            }
        }
    }
}
