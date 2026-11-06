package com.ziaee.zabankhan.ui

import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MenuBook
import com.ziaee.zabankhan.ui.theme.*
import androidx.compose.material.icons.Icons
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
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable fun LibraryScreen(vm: AppViewModel, settings: AppSettings, open: (Long) -> Unit, add: () -> Unit, watch: () -> Unit, discover: () -> Unit = {}) {
    val lang = requireNotNull(settings.learningLang)
    val spec = requireNotNull(Languages.byCode(lang))
    val texts by remember(lang) { vm.container.texts.observeAll(lang) }.collectAsState(emptyList())
    val folders by remember(lang) { vm.container.texts.observeFolders(lang) }.collectAsState(emptyList())
    var search by rememberSaveable(lang) { mutableStateOf("") }
    var folder by rememberSaveable(lang) { mutableStateOf(-1L) }
    var folderDialog by remember { mutableStateOf<LibraryFolder?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var deleteText by remember { mutableStateOf<TextDocument?>(null) }
    val options = listOf(-1L to stringResource(Res.string.all), 0L to stringResource(Res.string.unfiled)) + folders.map { it.id to it.name }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 20.dp)) {
        item {
            val title = stringResource(Res.string.nav_library)
            Text(title, style = editorialStyle(title, EditorialRole.ScreenTitle))
        }
        item { EditorialSearchEntry(search, { search = it }) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(stringResource(Res.string.folder), folder, options) { folder = it }
            OutlinedButton(onClick = { newFolder = true }) { Text(stringResource(Res.string.new_folder)) }
            ImportAddMenu(vm, settings, add, open, watch, discover)
            folders.firstOrNull { it.id == folder }?.let { selected ->
                var menu by remember(selected.id) { mutableStateOf(false) }
                Box {
                    EditorialIconButton({ menu = true }, Icons.Filled.MoreVert, stringResource(Res.string.more_actions))
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.rename)) }, onClick = { menu = false; folderDialog = selected })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; deleteFolder = selected })
                    }
                }
            }
        } }
        val filtered = texts.filter { (folder == -1L || (it.folderId ?: 0L) == folder) &&
            normalizeForMatch(it.title + " " + it.body, spec).contains(normalizeForMatch(search, spec)) }
        if (filtered.isEmpty()) item { EditorialEmptyState(Icons.Default.MenuBook,
            stringResource(Res.string.empty_library), stringResource(Res.string.ed_library_empty_body),
            Modifier.fillParentMaxHeight(.5f), stringResource(Res.string.add_text), add) }
        items(filtered, key = { it.id }) { text -> TextCard(text, { open(text.id) }, trailing = {
            // One overflow menu instead of a row of buttons on every card.
            var menu by remember { mutableStateOf(false) }
            Box(contentAlignment = Alignment.CenterEnd) {
                EditorialIconButton({ menu = true }, Icons.Filled.MoreVert, stringResource(Res.string.more_actions))
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(if (text.pinned) Res.string.unpin else Res.string.pin)) },
                        onClick = { menu = false; vm.action { vm.container.texts.setPinned(text.id, !text.pinned) } })
                    options.filter { it.first != -1L && it.first != (text.folderId ?: 0L) }.forEach { (id, name) ->
                        DropdownMenuItem(text = { Text(stringResource(Res.string.move) + ": " + name) },
                            onClick = { menu = false; vm.action { vm.container.texts.setFolder(text.id, id.takeIf { it != 0L }) } })
                    }
                    DropdownMenuItem(text = { Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; deleteText = text })
                }
            }
        }) }
    }
    if (newFolder) NameDialog(stringResource(Res.string.new_folder), onDismiss = { newFolder = false }) { name -> vm.action { vm.container.texts.insertFolder(LibraryFolder(lang = lang, name = name)) } }
    folderDialog?.let { selected -> NameDialog(stringResource(Res.string.rename), selected.name, { folderDialog = null }) { name -> vm.action { vm.container.texts.renameFolder(selected.id, name) } } }
    deleteFolder?.let { selected -> ConfirmDelete(stringResource(Res.string.folder_delete_note), { deleteFolder = null }) { vm.action {
        vm.container.texts.clearFolder(selected.id); vm.container.texts.deleteFolderRow(selected.id); folder = -1L
    } } }
    deleteText?.let { selected -> ConfirmDelete(onDismiss = { deleteText = null }) { vm.action { vm.container.texts.delete(selected) } } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun AddTextScreen(vm: AppViewModel, settings: AppSettings, back: () -> Unit, open: (Long) -> Unit, initialText: String = "") {
    // Shared text arrives without a title; start from its first line so Save is one tap away.
    var title by rememberSaveable(initialText) { mutableStateOf(initialText.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.trimStart('#')?.trim()?.take(60).orEmpty()) }
    var body by rememberSaveable(initialText) { mutableStateOf(initialText) }
    var busy by remember { mutableStateOf(false) }
    Page {
        EditorialTopAppBar(stringResource(Res.string.add_text), back)
        OutlinedTextField(title, { title = it }, label = { Text(stringResource(Res.string.title)) }, modifier = Modifier.fillMaxWidth(), textStyle = contentStyle(title))
        // Content direction resolves at each paragraph, independently of the UI locale.
        OutlinedTextField(body, { body = it }, label = { Text(stringResource(Res.string.body)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp), textStyle = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
        EditorialPrimaryButton(stringResource(Res.string.save), enabled = title.isNotBlank() && body.isNotBlank(), loading = busy, onClick = {
            busy = true
            vm.action { try { open(vm.container.texts.insert(TextDocument(lang = requireNotNull(settings.learningLang), title = title.trim(), body = body))) } finally { busy = false } }
        })
    }
}
