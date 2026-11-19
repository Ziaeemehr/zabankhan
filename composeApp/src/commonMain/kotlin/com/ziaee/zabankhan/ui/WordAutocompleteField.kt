package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.lang.LanguageSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun dictionarySuggestions(
    query: String?,
    language: LanguageSpec,
    suggest: suspend (String, LanguageSpec) -> List<String>,
): List<String> = key(query, language) {
    val results by produceState(emptyList<String>()) {
        if (query == null || !language.ownDictionaryAvailable || query.length < 2) return@produceState
        delay(180)
        value = try {
            suggest(query, language)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }
    results
}

@Composable
internal fun WordAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    val currentSearch by rememberUpdatedState(onSearch)
    val searchAction = remember { KeyboardActions(onSearch = { currentSearch?.invoke() }) }
    WordSuggestionMenu(suggestions, onValueChange, modifier) { anchor, showSuggestions ->
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                showSuggestions()
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().then(anchor),
            textStyle = contentStyle(value),
            singleLine = true,
            keyboardOptions = if (onSearch == null) KeyboardOptions.Default else KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = if (onSearch == null) KeyboardActions.Default else searchAction,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WordSuggestionMenu(
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
    field: @Composable (Modifier, () -> Unit) -> Unit,
) {
    var showSuggestions by remember { mutableStateOf(false) }
    val expanded = showSuggestions && suggestions.isNotEmpty()
    val focusManager = LocalFocusManager.current
    ExposedDropdownMenuBox(expanded, onExpandedChange = { showSuggestions = it }, modifier = modifier) {
        val anchor = Modifier.onFocusChanged { if (!it.hasFocus) showSuggestions = false }
            .menuAnchor(MenuAnchorType.PrimaryEditable)
        field(anchor) { showSuggestions = true }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { showSuggestions = false },
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            for (word in suggestions) {
                DropdownMenuItem(
                    text = { Text(word, style = contentStyle(word), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSuggestion(word)
                        showSuggestions = false
                        focusManager.clearFocus()
                    },
                )
            }
        }
    }
}
