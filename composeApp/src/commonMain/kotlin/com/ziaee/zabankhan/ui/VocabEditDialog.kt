package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.data.VocabEntry

/** Edits only the text fields of a card; scheduling (box, due dates, learned) is never touched. */
@Composable
internal fun VocabEditDialog(entry: VocabEntry, onDismiss: () -> Unit, onSave: (word: String, meaning: String?, sentence: String) -> Unit) {
    var word by remember(entry.id) { mutableStateOf(entry.word) }
    var meaning by remember(entry.id) { mutableStateOf(entry.meaning.orEmpty()) }
    var sentence by remember(entry.id) { mutableStateOf(entry.sentence) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.b_words_vocab_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(word, { word = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.b_words_vocab_edit_word)) }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
                OutlinedTextField(meaning, { meaning = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(stringResource(Res.string.b_words_vocab_edit_meaning)) }, textStyle = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
                OutlinedTextField(sentence, { sentence = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(stringResource(Res.string.b_words_vocab_edit_sentence)) }, textStyle = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(word.trim(), meaning.trim().ifBlank { null }, sentence.trim()) },
                enabled = word.isNotBlank()
            ) { Text(stringResource(Res.string.b_words_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.b_words_action_cancel)) } }
    )
}
