package com.ziaee.zabankhan.ui

// Copied from French Reader ui/DictionarySheet.kt; the host uses the shared dictionary sheet.
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    var suppress by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.b_words_manual_dictionary_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
                    }
                }
                Column(
                    modifier = Modifier.padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = rawWord,
                        onValueChange = { rawWord = it; suppress = false },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(stringResource(Res.string.b_words_manual_dictionary_word_hint, languageName(language)))
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (rawWord.isNotEmpty()) IconButton(onClick = { rawWord = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_clear))
                            }
                        },
                        textStyle = contentStyle(rawWord),
                        singleLine = true,
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                    )
                    AnimatedVisibility(visible = !suppress && suggestions.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                                suggestions.forEachIndexed { index, s ->
                                    if (index > 0) HorizontalDivider(
                                        Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    )
                                    Text(
                                        s,
                                        style = contentStyle(s),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth().clickable { rawWord = s; suppress = true }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                    )
                                }
                            }
                        }
                    }
                    Button(onClick = submit, enabled = word != null, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(Res.string.b_words_manual_dictionary_lookup))
                    }
                }
            }
        }
    }
}
