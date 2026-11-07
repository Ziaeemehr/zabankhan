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
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/** Saved texts, searchable and sortable; ported from French Reader's ui/library/LibraryScreen.kt without tags, shelves or nested folders. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable fun LibraryScreen(vm: AppViewModel, settings: AppSettings, open: (Long) -> Unit, add: () -> Unit, watch: () -> Unit, discover: () -> Unit = {}) {
    val lang = requireNotNull(settings.learningLang)
    val spec = requireNotNull(Languages.byCode(lang))
    val texts by remember(lang) { vm.container.texts.observeAll(lang) }.collectAsState(emptyList())
    val folders by remember(lang) { vm.container.texts.observeFolders(lang) }.collectAsState(emptyList())
    var query by rememberSaveable(lang) { mutableStateOf("") }
    var folder by rememberSaveable(lang) { mutableStateOf(-1L) }
    var sort by rememberSaveable(lang) { mutableStateOf(LibrarySort.NEWEST) }
    var levelFilter by remember(lang) { mutableStateOf<LevelFilter>(LevelFilter.All) }
    var selectedIds by rememberSaveable(lang) { mutableStateOf(emptyList<Long>()) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    var organizerExpanded by remember { mutableStateOf(false) }
    var levelMenuExpanded by remember { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf<LibraryFolder?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var pendingDelete by remember { mutableStateOf<TextDocument?>(null) }
    var showBulkDelete by remember { mutableStateOf(false) }
    var showBulkMove by remember { mutableStateOf(false) }
    val selection = selectedIds.toSet() intersect texts.mapTo(mutableSetOf()) { it.id }
    val isSelecting = selection.isNotEmpty()
    val documents = remember(texts, query, sort, folder, levelFilter, spec) { projectLibrary(texts, query, sort, folder, levelFilter, spec) }
    val visibleIds = documents.mapTo(mutableSetOf()) { it.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all(selection::contains)
    val clearSelection = { selectedIds = emptyList() }
    val moveOptions = listOf(0L to stringResource(Res.string.library_no_folder)) + folders.map { it.id to it.name }

    BackHandler(enabled = isSelecting, onBack = clearSelection)

    Column(Modifier.fillMaxSize()) {
        EditorialTopAppBar(
            title = if (isSelecting) pluralStringResource(Res.plurals.library_selection_count, selection.size, selection.size)
                else stringResource(Res.string.nav_library),
            back = if (isSelecting) clearSelection else null,
            backIcon = Icons.Default.Close, backLabel = stringResource(Res.string.library_selection_close),
            actions = {
                if (isSelecting) {
                    IconButton(onClick = { selectedIds = selectAllVisible(selection, visibleIds).toList() }) {
                        Icon(Icons.Default.SelectAll, stringResource(if (allVisibleSelected) Res.string.library_deselect_all else Res.string.library_select_all))
                    }
                    IconButton(onClick = { showBulkDelete = true }) { Icon(Icons.Default.Delete, stringResource(Res.string.library_bulk_delete)) }
                    IconButton(onClick = { showBulkMove = true }) { Icon(Icons.Default.Folder, stringResource(Res.string.library_bulk_move)) }
                } else {
                    Box {
                        IconButton(onClick = { organizerExpanded = true }) { Icon(Icons.Default.Folder, stringResource(Res.string.library_manage_organizer)) }
                        DropdownMenu(organizerExpanded, { organizerExpanded = false }) {
                            DropdownMenuItem(text = { Text(stringResource(Res.string.new_folder)) }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                onClick = { organizerExpanded = false; newFolder = true })
                            folders.firstOrNull { it.id == folder }?.let { selected ->
                                DropdownMenuItem(text = { Text(stringResource(Res.string.rename) + ": " + selected.name) }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { organizerExpanded = false; folderDialog = selected })
                                DropdownMenuItem(text = { Text(stringResource(Res.string.delete) + ": " + selected.name, color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { organizerExpanded = false; deleteFolder = selected })
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(Res.string.accessibility_sort)) }
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            listOf(LibrarySort.NEWEST to Res.string.library_sort_newest, LibrarySort.TITLE to Res.string.library_sort_title,
                                LibrarySort.LAST_READ to Res.string.library_sort_last_read).forEach { (option, label) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { sort = option; sortMenuExpanded = false },
                                    trailingIcon = if (sort == option) ({ Icon(Icons.Default.Check, null) }) else null)
                            }
                        }
                    }
                    // French Reader adds texts from its bottom bar; zabankhan's bottom bar has no add slot.
                    ImportAddMenu(vm, settings, add, open, watch, discover, trigger = { onImport, busy ->
                        IconButton(onClick = onImport, enabled = !busy) { Icon(Icons.Default.Add, stringResource(Res.string.add_text)) }
                    })
                }
            },
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text(stringResource(Res.string.library_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.search)) },
            singleLine = true, shape = MaterialTheme.shapes.medium, textStyle = contentStyle(query),
            modifier = Modifier.fillMaxWidth().padding(horizontal = ZabankhanDesign.spacing.small, vertical = ZabankhanDesign.spacing.xSmall),
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = ZabankhanDesign.spacing.small)) {
            OutlinedButton(onClick = { folderMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(when (folder) {
                    -1L -> stringResource(Res.string.library_folder_selection_all)
                    0L -> stringResource(Res.string.unfiled)
                    else -> folders.firstOrNull { it.id == folder }?.name ?: stringResource(Res.string.library_folder_selection_all)
                }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(folderMenuExpanded, { folderMenuExpanded = false }, Modifier.heightIn(max = 360.dp)) {
                (listOf(-1L to stringResource(Res.string.library_filter_all), 0L to stringResource(Res.string.unfiled)) + folders.map { it.id to it.name }).forEach { (id, name) ->
                    val isSelected = id == folder
                    DropdownMenuItem(
                        text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isSelected) FontWeight.Bold else null,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                        leadingIcon = if (id > 0L) ({ Icon(Icons.Default.Folder, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current) }) else null,
                        onClick = { folder = id; folderMenuExpanded = false },
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = ZabankhanDesign.spacing.small)) {
            val selectedLevel = (levelFilter as? LevelFilter.Level)?.level
            OutlinedButton(onClick = { levelMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (levelFilter == LevelFilter.All) stringResource(Res.string.text_level_all)
                    else selectedLevel ?: stringResource(Res.string.text_level_unspecified), modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded = levelMenuExpanded, onDismissRequest = { levelMenuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.text_level_all)) }, onClick = { levelFilter = LevelFilter.All; levelMenuExpanded = false })
                (texts.mapNotNull { it.level }.distinct().sorted() + listOf<String?>(null)).filter { level ->
                    texts.any { it.level == level } || levelFilter == LevelFilter.Level(level)
                }.forEach { level ->
                    DropdownMenuItem(text = { Text(level ?: stringResource(Res.string.text_level_unspecified)) },
                        onClick = { levelFilter = LevelFilter.Level(level); levelMenuExpanded = false })
                }
            }
        }
        when {
            texts.isEmpty() && query.isBlank() -> EditorialEmptyState(Icons.AutoMirrored.Filled.Article,
                stringResource(Res.string.library_empty_title), stringResource(Res.string.library_empty_body),
                Modifier.fillMaxSize(), stringResource(Res.string.add_text), add)
            documents.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (query.isNotBlank() && folder == -1L && levelFilter == LevelFilter.All) stringResource(Res.string.library_no_results, query)
                    else stringResource(Res.string.library_no_matches_filters),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = ZabankhanDesign.spacing.medium))
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(documents, key = { it.id }) { doc ->
                    val inTitle = normalizeForMatch(doc.title, spec).contains(normalizeForMatch(query.trim(), spec))
                    LibraryRow(
                        doc,
                        onClick = { if (isSelecting) selectedIds = toggleSelection(selection, doc.id).toList() else open(doc.id) },
                        onLongClick = { selectedIds = (selection + doc.id).toList() },
                        selected = doc.id in selection, selectionMode = isSelecting,
                        folderName = doc.folderId?.let { id -> folders.firstOrNull { it.id == id }?.name },
                        bodySnippet = if (query.isBlank() || inTitle) null else remember(doc.body, query) { bodySnippet(doc.body, query, spec) },
                        menu = { dismiss ->
                            DropdownMenuItem(text = { Text(stringResource(if (doc.pinned) Res.string.unpin else Res.string.pin)) },
                                leadingIcon = { Icon(Icons.Default.PushPin, null) },
                                onClick = { dismiss(); vm.action { vm.container.texts.setPinned(doc.id, !doc.pinned) } })
                            moveOptions.filter { it.first != (doc.folderId ?: 0L) }.forEach { (id, name) ->
                                DropdownMenuItem(text = { Text(stringResource(Res.string.move) + ": " + name) },
                                    leadingIcon = { Icon(Icons.Default.Folder, null) },
                                    onClick = { dismiss(); vm.action { vm.container.texts.setFolder(doc.id, id.takeIf { it != 0L }) } })
                            }
                            DropdownMenuItem(text = { Text(stringResource(Res.string.delete)) }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { dismiss(); pendingDelete = doc })
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.library_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.library_delete_confirm_message)) },
            confirmButton = { TextButton(onClick = { pendingDelete = null; vm.action { vm.container.texts.delete(doc) } }) { Text(stringResource(Res.string.delete)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
    if (showBulkDelete) {
        AlertDialog(
            onDismissRequest = { showBulkDelete = false },
            title = { Text(pluralStringResource(Res.plurals.library_bulk_delete_title, selection.size, selection.size)) },
            text = { Text(stringResource(Res.string.library_bulk_delete_message)) },
            confirmButton = { TextButton(onClick = {
                showBulkDelete = false
                val doomed = texts.filter { it.id in selection }
                clearSelection()
                vm.action { doomed.forEach { vm.container.texts.delete(it) } }
            }) { Text(stringResource(Res.string.delete)) } },
            dismissButton = { TextButton(onClick = { showBulkDelete = false }) { Text(stringResource(Res.string.cancel)) } },
        )
    }
    if (showBulkMove) {
        MoveToFolderDialog(moveOptions, onDismiss = { showBulkMove = false }) { id ->
            showBulkMove = false
            val ids = selection.toList()
            clearSelection()
            vm.action { ids.forEach { vm.container.texts.setFolder(it, id.takeIf { it != 0L }) } }
        }
    }
    if (newFolder) NameDialog(stringResource(Res.string.new_folder), onDismiss = { newFolder = false }) { name -> vm.action { vm.container.texts.insertFolder(LibraryFolder(lang = lang, name = name)) } }
    folderDialog?.let { selected -> NameDialog(stringResource(Res.string.rename), selected.name, { folderDialog = null }) { name -> vm.action { vm.container.texts.renameFolder(selected.id, name) } } }
    deleteFolder?.let { selected -> ConfirmDelete(stringResource(Res.string.folder_delete_note), { deleteFolder = null }) { vm.action {
        vm.container.texts.clearFolder(selected.id); vm.container.texts.deleteFolderRow(selected.id); folder = -1L
    } } }
}

@Composable
private fun MoveToFolderDialog(options: List<Pair<Long, String>>, onDismiss: () -> Unit, onMove: (Long) -> Unit) {
    var selectedId by remember { mutableStateOf(0L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.library_move_to_folder)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (id, name) ->
                    Row(Modifier.fillMaxWidth().clickable { selectedId = id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedId == id, onClick = { selectedId = id })
                        Text(name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMove(selectedId) }) { Text(stringResource(Res.string.move)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(
    doc: TextDocument, onClick: () -> Unit, onLongClick: () -> Unit, selected: Boolean, selectionMode: Boolean,
    folderName: String?, bodySnippet: String?, menu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(ZabankhanDesign.sizes.thumbnailSmall))
        else DocumentThumbnail(doc, Modifier.size(ZabankhanDesign.sizes.thumbnailSmall))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall.merge(contentStyle(doc.title)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            bodySnippet?.takeIf { it.isNotBlank() }?.let { snippet ->
                Text(snippet, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall.merge(contentStyle(snippet)), fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val metadata = listOfNotNull(folderName, doc.sourceName?.takeIf { it.isNotBlank() }, editorialDate(doc.createdAtMs)).joinToString(" · ")
                Text(metadata, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = ZabankhanDesign.spacing.half))
                MetadataBadge(stringResource(when (readingStatus(doc)) {
                    ReadingStatus.NOT_STARTED -> Res.string.library_status_new
                    ReadingStatus.IN_PROGRESS -> Res.string.library_status_in_progress
                    ReadingStatus.READ -> Res.string.library_status_read
                }))
                Spacer(Modifier.width(ZabankhanDesign.spacing.half))
                TextLevelBadge(doc.level)
            }
        }
        if (!selectionMode) Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.library_row_more_actions, doc.title))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) { menu { menuExpanded = false } }
        }
    }
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
