package com.ziaee.zabankhan.ui

// Copied from French Reader ui/DictionarySheet.kt; the host uses the shared dictionary sheet.
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.reader.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ManualDictionaryHost(vm: AppViewModel, settings: AppSettings, open: Boolean, onDismiss: () -> Unit) {
    var lookupWord by rememberSaveable(open) { mutableStateOf<String?>(null) }
    if (!open) return

    val word = lookupWord
    if (word == null) {
        ManualDictionaryDialog(
            onDismiss = onDismiss,
            language = requireNotNull(Languages.byCode(requireNotNull(settings.learningLang))),
            onLookup = { lookupWord = it }
        )
    } else {
        DictionarySheet(vm, settings,
            TextDocument(id = 0L, lang = requireNotNull(settings.learningLang), title = word, body = ""),
            ReaderSelection(word, "", 0..0, false), emptyList(), emptyList(), onDismiss)

    }
}

@Composable
fun ManualDictionaryDialog(onDismiss: () -> Unit, language: LanguageSpec, onLookup: (String) -> Unit) {
    var rawWord by rememberSaveable { mutableStateOf("") }
    val word = rawWord.trim().takeIf { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.b_words_manual_dictionary_title)) },
        text = {
            OutlinedTextField(
                value = rawWord,
                onValueChange = { rawWord = it },
                label = { Text(stringResource(Res.string.b_words_manual_dictionary_word_hint, languageName(language))) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(textDirection = TextDirection.Content)
            )
        },
        confirmButton = {
            TextButton(onClick = { word?.let(onLookup) }, enabled = word != null) {
                Text(stringResource(Res.string.b_words_manual_dictionary_lookup))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.b_words_action_cancel)) }
        }
    )
}
