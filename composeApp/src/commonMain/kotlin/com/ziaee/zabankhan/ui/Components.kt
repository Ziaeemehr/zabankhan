package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable fun languageName(lang: LanguageSpec): String = stringResource(when (lang.code) {
    Languages.PERSIAN.code -> Res.string.language_persian
    Languages.ENGLISH.code -> Res.string.language_english
    else -> Res.string.language_french
})
fun contentStyle(text: String) = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start)

@Composable fun <T> Choice(label: String, value: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${options.firstOrNull { it.first == value }?.second.orEmpty()}") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (key, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onSelect(key) }) }
        }
    }
}
@Composable fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable fun Heading(text: String) { Text(text, style = MaterialTheme.typography.headlineSmall) }
@Composable fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.name)) }, singleLine = true)
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()); onDismiss() }) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } })
}
@Composable fun ConfirmDelete(note: String = "", onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(Res.string.delete_confirm)) }, text = { Text(note) },
        confirmButton = { TextButton(onClick = { onDelete(); onDismiss() }) { Text(stringResource(Res.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } })
}
