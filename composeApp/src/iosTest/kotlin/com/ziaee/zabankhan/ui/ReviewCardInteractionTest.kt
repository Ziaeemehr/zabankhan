package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.data.VocabEntry
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReviewCardInteractionTest {
    private val entry = VocabEntry(id = 1, lang = "fr", word = "bonjour", meaning = "سلام",
        sentence = "le petit chat dort")

    @Composable
    private fun Card(onLookup: (String) -> Unit) {
        MaterialTheme {
            Box(Modifier.size(640.dp, 360.dp)) {
                EntryFlashcard(entry, revealed = true, interactive = true, onFlip = {}, onPlayWord = {},
                    onPlaySentence = {}, sentenceTranslation = null, sentenceTranslationLoading = false,
                    sentenceTranslationError = false, onEdit = {}, modifier = Modifier,
                    onLookup = onLookup)
            }
        }
    }

    @Test fun shortTapLooksUpOnlyTheTouchedWord() = runComposeUiTest {
        var lookedUp: String? = null
        setContent { Card { lookedUp = it } }

        onNodeWithText("bonjour").performTouchInput { click() }

        runOnIdle { assertEquals("bonjour", lookedUp) }
    }

    @Test fun longPressDoesNotLookupAndPhraseSelectionCopies() = runComposeUiTest {
        var lookedUp: String? = null
        var copied: String? = null
        val clipboard = object : ClipboardManager {
            override fun getText(): AnnotatedString? = copied?.let(::AnnotatedString)
            override fun setText(annotatedString: AnnotatedString) { copied = annotatedString.text }
        }
        setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard) { Card { lookedUp = it } }
        }

        onNodeWithText("le petit chat dort").performTouchInput { longClick() }
        runOnIdle { assertEquals(null, lookedUp) }
        onNodeWithText("le petit chat dort").performSemanticsAction(SemanticsActions.SetSelection) {
            it(3, 13, false)
        }
        val selection = onNodeWithText("le petit chat dort").fetchSemanticsNode()
            .config[SemanticsProperties.TextSelectionRange]

        runOnIdle {
            assertEquals(TextRange(3, 13), selection)
            assertEquals(null, lookedUp)
            assertEquals("petit chat", copied)
        }
    }
}
