package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.data.VocabEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A very long meaning must scroll inside the card; the answer buttons stay on screen (landscape phone, 2x font). */
@OptIn(ExperimentalTestApi::class)
class ReviewCardLayoutTest {
    @Test fun longMeaningScrollsInsideCardAndAnswersStayVisible() = runComposeUiTest {
        val alternatives = (1..60).joinToString("\n") { "معنی جایگزین شماره $it برای این واژه" }
        val entry = VocabEntry(id = 1, lang = "en", word = "serendipity",
            meaning = "اتفاق خوش‌یمن\n$alternatives\nALT-END",
            sentence = List(20) { "A long example sentence that keeps going." }.joinToString(" "))
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                MaterialTheme {
                    Box(Modifier.size(width = 640.dp, height = 360.dp)) {
                        ReviewCardLayout(header = { Text("1 / 3") }, card = { modifier ->
                            EntryFlashcard(entry, revealed = true, interactive = true, onFlip = {}, onPlayWord = {}, onPlaySentence = {},
                                sentenceTranslation = null, sentenceTranslationLoading = false, sentenceTranslationError = false,
                                onEdit = {}, modifier = modifier)
                        }) {
                            Row(Modifier.fillMaxWidth()) {
                                ReviewAnswerButton("Again", "now", Modifier.weight(1f)) {}
                                ReviewAnswerButton("Hard", "1", Modifier.weight(1f)) {}
                                ReviewAnswerButton("Good", "2", Modifier.weight(1f)) {}
                            }
                        }
                    }
                }
            }
        }
        listOf("Again", "Hard", "Good").forEach {
            val bottom = onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot().bottom
            assertTrue(bottom <= 360.dp, "$it button ends at $bottom, below the 360dp screen")
        }
        // Bounds inside the flip card's 3D graphicsLayer are not projected reliably, so read the card's scroll semantics.
        val scroller = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        fun range() = scroller.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range().maxValue() > 0f, "long meaning should overflow the card and scroll inside it")
        assertEquals(0f, range().value())
        scroller.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        assertEquals(range().maxValue(), range().value(), "scrolling inside the card should reach the last line")
        listOf("Again", "Good").forEach { onNodeWithText(it).assertIsDisplayed() }
    }
}
