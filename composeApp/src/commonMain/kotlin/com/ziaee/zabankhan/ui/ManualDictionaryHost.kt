package com.ziaee.zabankhan.ui

// Copied from French Reader ui/DictionarySheet.kt; the host uses the shared dictionary sheet.
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
            onLookup = { lookupWord = it },
            suggest = vm.container.ownDictionary::completeHeadwords,
        )
    } else {
        DictionarySheet(vm, settings,
            TextDocument(id = 0L, lang = requireNotNull(settings.learningLang), title = word, body = ""),
            ReaderSelection(word, "", 0..0, false), emptyList(), emptyList(), onDismiss)

    }
}

@Composable
fun ManualDictionaryDialog(
    onDismiss: () -> Unit,
    language: LanguageSpec,
    onLookup: (String) -> Unit,
    suggest: suspend (String, LanguageSpec) -> List<String>,
) {
    var rawWord by rememberSaveable { mutableStateOf("") }
    val word = rawWord.trim().takeIf { it.isNotEmpty() }
    val suggestions = dictionarySuggestions(word, language, suggest)
    val submit: () -> Unit = {
        rawWord.trim().takeIf(String::isNotEmpty)?.let(onLookup)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.b_words_manual_dictionary_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.b_words_action_cancel))
                    }
                }
                WordAutocompleteField(
                    value = rawWord,
                    onValueChange = { rawWord = it },
                    suggestions = suggestions,
                    label = stringResource(Res.string.b_words_manual_dictionary_word_hint, languageName(language)),
                    modifier = Modifier.fillMaxWidth(),
                    onSearch = submit,
                )
                Button(onClick = submit, enabled = word != null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.b_words_manual_dictionary_lookup))
                }
            }
        }
    }
}
