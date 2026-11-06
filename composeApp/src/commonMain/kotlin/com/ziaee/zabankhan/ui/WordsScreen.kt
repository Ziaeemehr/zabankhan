package com.ziaee.zabankhan.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.reader.boxColor
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable fun WordsScreen(vm: AppViewModel, settings: AppSettings) {
    val lang = settings.learningLang!!
    val spec = Languages.byCode(lang)!!
    val words by remember(lang) { vm.container.vocab.observeAll(lang) }.collectAsState(emptyList())
    val decks by remember(lang) { vm.container.vocab.observeLists(lang) }.collectAsState(emptyList())
    var search by rememberSaveable(lang) { mutableStateOf("") }
    var deck by rememberSaveable(lang) { mutableStateOf(-1L) }
    var selected by remember(lang) { mutableStateOf(emptySet<Long>()) }
    var edit by remember { mutableStateOf<VocabEntry?>(null) }
    var createDeck by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val options = listOf(-1L to stringResource(Res.string.all), 0L to stringResource(Res.string.unfiled)) + decks.map { it.id to it.name }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading(stringResource(Res.string.nav_words)) }
        item { OutlinedTextField(search, { search = it }, label = { Text(stringResource(Res.string.search)) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Choice(stringResource(Res.string.deck), deck, options) { deck = it } }
        item { OutlinedButton(onClick = { createDeck = true }) { Text(stringResource(Res.string.new_deck)) } }
        if (selected.isNotEmpty()) item {
            Text(stringResource(Res.string.selected_count, selected.size))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(stringResource(Res.string.move), 0L, options.filter { it.first != -1L }) { target -> vm.action { vm.container.vocab.moveToList(selected.toList(), target.takeIf { it != 0L }); selected = emptySet() } }
                TextButton(onClick = { delete = true }) { Text(stringResource(Res.string.delete)) }
                TextButton(onClick = { selected = emptySet() }) { Text(stringResource(Res.string.cancel)) }
            }
        }
        val filtered = words.filter { (deck == -1L || (it.listId ?: 0L) == deck) && normalizeForMatch(it.word + " " + it.meaning.orEmpty(), spec).contains(normalizeForMatch(search, spec)) }
        if (filtered.isEmpty()) item { Text(stringResource(Res.string.empty_words)) }
        items(filtered, key = { it.id }) { word ->
            Card(Modifier.fillMaxWidth().combinedClickable(onClick = {
                if (selected.isEmpty()) edit = word else selected = if (word.id in selected) selected - word.id else selected + word.id
            }, onLongClick = { selected = selected + word.id }), colors = CardDefaults.cardColors(containerColor = if (word.id in selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(word.word, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge.merge(contentStyle(word.word)))
                    Text(word.meaning.orEmpty(), modifier = Modifier.fillMaxWidth(), style = contentStyle(word.meaning.orEmpty()))
                    Text(stringResource(Res.string.box_label, word.leitnerBox), color = boxColor(word.leitnerBox))
                }
            }
        }
    }
    if (createDeck) NameDialog(stringResource(Res.string.new_deck), onDismiss = { createDeck = false }) { name -> vm.action { vm.container.vocab.insertList(VocabList(lang = lang, name = name)) } }
    if (delete) ConfirmDelete(onDismiss = { delete = false }) { vm.action { vm.container.vocab.deleteIds(selected.toList()); selected = emptySet() } }
    edit?.let { entry -> EditWordDialog(entry, options.filter { it.first != -1L }, { edit = null }) { updated -> vm.action { vm.container.vocab.update(updated) } } }
}
@Composable private fun EditWordDialog(entry: VocabEntry, decks: List<Pair<Long, String>>, dismiss: () -> Unit, save: (VocabEntry) -> Unit) {
    var word by remember { mutableStateOf(entry.word) }
    var meaning by remember { mutableStateOf(entry.meaning.orEmpty()) }
    var sentence by remember { mutableStateOf(entry.sentence) }
    var deck by remember { mutableStateOf(entry.listId ?: 0L) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(Res.string.edit)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(word, { word = it }, label = { Text(stringResource(Res.string.word)) }, textStyle = contentStyle(word))
            OutlinedTextField(meaning, { meaning = it }, label = { Text(stringResource(Res.string.meaning)) }, textStyle = contentStyle(meaning))
            OutlinedTextField(sentence, { sentence = it }, label = { Text(stringResource(Res.string.sentence)) }, textStyle = contentStyle(sentence))
            Choice(stringResource(Res.string.deck), deck, decks) { deck = it }
        }
    }, confirmButton = { TextButton(enabled = word.isNotBlank(), onClick = { save(entry.copy(word = word.trim(), meaning = meaning.trim(), sentence = sentence, listId = deck.takeIf { it != 0L })); dismiss() }) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.cancel)) } })
}
