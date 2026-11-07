package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.dictionary.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.speech.createTts
import com.ziaee.zabankhan.speech.pronounce
import com.ziaee.zabankhan.speech.togglePronounce
import com.ziaee.zabankhan.ui.contentStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySearchDialog(vm: AppViewModel, settings: AppSettings, onDismiss: () -> Unit) {
    val lang = Languages.byCode(settings.learningLang ?: "fr") ?: Languages.FRENCH
    val tts = remember { createTts() }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Entry>()) }
    var selected by remember { mutableStateOf<Entry?>(null) }
    var lookup by remember { mutableStateOf<DictionaryLookup?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    val target = settings.meaningLang
    LaunchedEffect(query, lang) {
        if (query.isBlank()) { results = emptyList(); searchFailed = false; searching = false; return@LaunchedEffect }
        delay(300)
        searching = true
        searchFailed = false
        try { results = vm.container.ownDictionary.search(query, lang) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { results = emptyList(); searchFailed = true }
        finally { searching = false }
    }
    LaunchedEffect(selected, lang, target) {
        val entry = selected ?: return@LaunchedEffect
        lookup = DictionaryLookup(entry.word, listOf(entry), emptyList(), emptyList())
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(Res.string.own_search)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
                OutlinedTextField(query, { query = it; selected = null }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(Res.string.own_search_hint)) }, keyboardOptions = KeyboardOptions.Default.copy(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}), textStyle = contentStyle(query))
                val current = lookup
                if (current != null) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        OwnDictionaryBlock(current, lang, target, vm, 0L, "", { tts.togglePronounce(it, lang.code, settings) }) { word -> query = word; selected = null; lookup = null }
                    }
                } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when {
                        searching -> Text(stringResource(Res.string.own_searching), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        searchFailed -> Text(stringResource(Res.string.own_search_error), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                        query.isNotBlank() && results.isEmpty() -> Text(stringResource(Res.string.own_no_entry), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> results.forEach { entry -> TextButton(onClick = { selected = entry }) { Text(entry.word, Modifier.weight(1f), style = contentStyle(entry.word)); if (entry.ipa.isNotBlank()) Text(entry.ipa) } }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        dismissButton = { if (selected != null) TextButton(onClick = { selected = null; lookup = null }) { Text(stringResource(Res.string.own_search_results)) } })
}
