package com.ziaee.zabankhan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.data.TextDocument
import com.ziaee.zabankhan.ui.theme.*
import androidx.compose.ui.backhandler.BackHandler

/** Stateless Library layout, hoisted out so a UI test can drive it with a
 * static [LibraryUiState] instead of a real database-backed ViewModel. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onSortSelect: (LibrarySort) -> Unit,
    onOpen: (TextDocument) -> Unit,
    onDelete: (TextDocument) -> Unit,
    onAddText: () -> Unit,
    onFilePickerClick: () -> Unit,
    onFolderPickerClick: () -> Unit = {},
    onOpenHome: () -> Unit,
    onOpenResources: () -> Unit = {},
    onWords: () -> Unit = {},
    onEdit: (TextDocument, String, String) -> Unit = { _, _, _ -> },
    onLoadBody: (TextDocument, (String) -> Unit) -> Unit = { _, loaded -> loaded("") },
    onFolderFilterChange: (FolderFilter) -> Unit = {},
    onTagFilterToggle: (Long) -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onRenameFolder: (Long, String) -> Unit = { _, _ -> },
    onDeleteFolder: (Long, Boolean) -> Unit = { _, _ -> },
    onCreateTag: (String) -> Unit = {},
    onRenameTag: (Long, String) -> Unit = { _, _ -> },
    onDeleteTag: (Long) -> Unit = {},
    onMoveToFolder: (TextDocument, Long?) -> Unit = { _, _ -> },
    onSetTags: (TextDocument, Set<Long>) -> Unit = { _, _ -> },
    onCreateFolderAndMove: (TextDocument, String) -> Unit = { _, _ -> },
    onCreateTagAndAssign: (TextDocument, String, Set<Long>) -> Unit = { _, _, _ -> },
    onCreateFolderIn: (String, Long?) -> Unit = { name, _ -> onCreateFolder(name) },
    onMoveFolder: (Long, Long?) -> Unit = { _, _ -> },
    onToggleSelection: (Long) -> Unit = {},
    onStartSelection: (Long) -> Unit = {},
    onSelectAll: (Set<Long>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onMoveSelectedToFolder: (Long?) -> Unit = {},
    onAddTagsToSelected: (Set<Long>) -> Unit = {},
    onCreateFolderAndMoveSelected: (String) -> Unit = {},
    onCreateTagAndAddToSelected: (String, Set<Long>) -> Unit = { _, _ -> },
    onShelfSelect: (LibraryShelf) -> Unit = {},
    onLevelFilterChange: (LevelFilter) -> Unit = {},
    onSetLevel: (List<Long>, String?) -> Unit = { _, _ -> },
    onReadMark: (List<Long>, Int) -> Unit = { _, _ -> },
    importAction: @Composable () -> Unit = {}
) {
    var levelMenuExpanded by remember { mutableStateOf(false) }
    var levelTargets by remember { mutableStateOf<List<TextDocument>?>(null) }
    var bulkMarkMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    var expandedFolderIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var selectedFolderIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var renameFolderTarget by remember { mutableStateOf<com.ziaee.zabankhan.data.LibraryFolder?>(null) }
    var deleteFolderTarget by remember { mutableStateOf<Long?>(null) }
    var confirmFolderFilesDelete by remember { mutableStateOf<Long?>(null) }
    var confirmSelectedFoldersFilesDelete by remember { mutableStateOf<List<Long>?>(null) }
    var deleteSelectedFolders by remember { mutableStateOf(false) }
    var selectedFolderDeleteFiles by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TextDocument?>(null) }
    var pendingEdit by remember { mutableStateOf<TextDocument?>(null) }
    var pendingEditBody by remember { mutableStateOf<String?>(null) }
    var showOrganizer by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<TextDocument?>(null) }
    var pendingTags by remember { mutableStateOf<TextDocument?>(null) }
    var showBulkDelete by remember { mutableStateOf(false) }
    var showBulkMove by remember { mutableStateOf(false) }
    var showBulkTags by remember { mutableStateOf(false) }
    val selectedFolderId = (state.folderFilter as? FolderFilter.Folder)?.id
    val breadcrumb = selectedFolderId?.let { pathTo(state.folders, it) }.orEmpty()
    val folderRows = visibleFolderTree(state.folders, expandedFolderIds.toSet())
    var unfiledExpanded by rememberSaveable { mutableStateOf(true) }
    val treeFolders = remember(state.folders, state.folderFilter) {
        when (val filter = state.folderFilter) {
            FolderFilter.All -> state.folders
            FolderFilter.Unfiled -> emptyList()
            is FolderFilter.Folder -> {
                val ids = descendantIds(state.folders, filter.id)
                state.folders.filter { it.id in ids }
            }
        }
    }
    val docsByFolder = remember(state.documents) { state.documents.groupBy { it.folderId } }
    val treeRows = remember(state.isSearching, treeFolders, expandedFolderIds) {
        if (state.isSearching) emptyList() else visibleFolderTree(treeFolders, expandedFolderIds.toSet())
    }
    val folderCounts = remember(treeFolders, docsByFolder) {
        libraryFolderCounts(treeFolders, docsByFolder.mapValues { (_, docs) -> docs.size })
    }
    val folderIds = remember(state.folders) { state.folders.mapTo(mutableSetOf()) { it.id } }
    val unfiled = state.documents.filter { it.folderId !in folderIds }
    val openFolderIds = treeRows.filter { it.folder.id in expandedFolderIds }.mapTo(mutableSetOf()) { it.folder.id }
    val visibleIds = state.documents.filter { doc ->
        state.isSearching || doc.folderId in openFolderIds || (unfiledExpanded && doc.folderId !in folderIds)
    }.mapTo(mutableSetOf()) { it.id }
    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all(state.selectedIds::contains)

    val folderSelectionMode = selectedFolderIds.isNotEmpty()
    LaunchedEffect(state.folders) {
        val present = state.folders.mapTo(mutableSetOf()) { it.id }
        if (!present.containsAll(selectedFolderIds)) selectedFolderIds = selectedFolderIds.filter { it in present }
    }
    fun clearFolderSelection() {
        selectedFolderIds = emptyList()
        deleteSelectedFolders = false
        selectedFolderDeleteFiles = false
    }
    BackHandler(enabled = folderSelectionMode || state.isSelecting, onBack = {
        if (folderSelectionMode) clearFolderSelection() else onClearSelection()
    })

    Scaffold(
        topBar = {
            LibraryTopAppBar(
                title = if (folderSelectionMode) stringResource(Res.string.library_folder_selection_count, selectedFolderIds.size) else if (state.isSelecting) pluralStringResource(
                    Res.plurals.library_selection_count, state.selectedIds.size, state.selectedIds.size
                ) else stringResource(Res.string.nav_library),
                modifier = if (folderSelectionMode) Modifier.testTag("folderSelectionBar") else if (state.isSelecting) Modifier.testTag("selectionBar") else Modifier,
                titleModifier = if (state.isSelecting || folderSelectionMode) Modifier.testTag("selectionCount") else Modifier,
                navigationIcon = {
                    if (folderSelectionMode) IconButton(onClick = ::clearFolderSelection) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.library_selection_close))
                    } else if (state.isSelecting) IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.library_selection_close))
                    }
                },
                actions = {
                    if (folderSelectionMode) {
                        IconButton(onClick = { selectedFolderDeleteFiles = false; deleteSelectedFolders = true }, modifier = Modifier.testTag("folderBulkDelete")) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.action_delete))
                        }
                    } else if (state.isSelecting) {
                        IconButton(onClick = { onSelectAll(visibleIds) }, modifier = Modifier.testTag("selectAll")) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(
                                if (allVisibleSelected) Res.string.library_deselect_all else Res.string.library_select_all
                            ))
                        }
                        IconButton(onClick = { showBulkDelete = true }, modifier = Modifier.testTag("bulkDelete")) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.library_bulk_delete))
                        }
                        IconButton(onClick = { showBulkMove = true }, modifier = Modifier.testTag("bulkMove")) {
                            Icon(Icons.Default.Folder, contentDescription = stringResource(Res.string.library_bulk_move))
                        }
                        IconButton(onClick = { showBulkTags = true }, modifier = Modifier.testTag("bulkTags")) {
                            Icon(Icons.Default.Label, contentDescription = stringResource(Res.string.library_bulk_tags))
                        }
                        Box {
                            IconButton(onClick = { bulkMarkMenuExpanded = true }, modifier = Modifier.testTag("bulkReadMark")) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.library_read_mark_actions))
                            }
                            DropdownMenu(expanded = bulkMarkMenuExpanded, onDismissRequest = { bulkMarkMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.text_level_set)) },
                                    onClick = {
                                        bulkMarkMenuExpanded = false
                                        levelTargets = state.allDocuments.filter { it.id in state.selectedIds }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.library_mark_finished)) },
                                    onClick = { bulkMarkMenuExpanded = false; onReadMark(state.selectedIds.toList(), READ_MARK_FINISHED) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.library_move_back_reading)) },
                                    onClick = { bulkMarkMenuExpanded = false; onReadMark(state.selectedIds.toList(), READ_MARK_NOT_FINISHED) }
                                )
                            }
                        }
                    } else {
                        importAction()
                        IconButton(
                            onClick = { showOrganizer = true },
                            modifier = Modifier.testTag("manageLibraryOrganizer")
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = stringResource(Res.string.library_manage_organizer))
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Default.Sort, contentDescription = stringResource(Res.string.accessibility_sort))
                            }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.library_sort_newest)) },
                                    onClick = { onSortSelect(LibrarySort.NEWEST); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.library_sort_title)) },
                                    onClick = { onSortSelect(LibrarySort.TITLE); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.library_sort_last_read)) },
                                    onClick = { onSortSelect(LibrarySort.LAST_READ); sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
            )
        },

    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            EditorialSearchEntry(
                value = state.query,
                onValueChange = onQueryChange,
                hint = stringResource(Res.string.library_search_hint),
                showFloatingLabel = false,
                modifier = Modifier.padding(horizontal = ZabankhanDesign.spacing.small, vertical = ZabankhanDesign.spacing.xSmall),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZabankhanDesign.spacing.small)
            ) {
                val folderSelectionLabel = when (state.folderFilter) {
                    FolderFilter.All -> stringResource(Res.string.library_folder_selection_all)
                    FolderFilter.Unfiled -> stringResource(Res.string.library_filter_unfiled)
                    is FolderFilter.Folder -> breadcrumb.joinToString(" › ") { it.name }
                        .ifBlank { stringResource(Res.string.library_folder_selection_all) }
                }
                OutlinedButton(
                    onClick = {
                        // Open with the selected folder's ancestors expanded so the selection is visible.
                        expandedFolderIds = (expandedFolderIds + breadcrumb.dropLast(1).map { it.id }).distinct()
                        folderMenuExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("folderFilters")
                ) {
                    Text(folderSelectionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = folderMenuExpanded,
                    onDismissRequest = { folderMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.library_filter_all)) },
                        onClick = {
                            onFolderFilterChange(FolderFilter.All)
                            folderMenuExpanded = false
                        },
                        modifier = Modifier.testTag("folderFilterAll")
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.library_filter_unfiled)) },
                        onClick = {
                            onFolderFilterChange(FolderFilter.Unfiled)
                            folderMenuExpanded = false
                        },
                        modifier = Modifier.testTag("folderFilterUnfiled")
                    )
                    folderRows.forEach { row ->
                        val folder = row.folder
                        val isSelected = folder.id == selectedFolderId
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.width((row.depth * 16).dp))
                                    if (row.hasChildren) {
                                        IconButton(
                                            onClick = {
                                                expandedFolderIds = if (row.expanded) {
                                                    expandedFolderIds - folder.id
                                                } else {
                                                    expandedFolderIds + folder.id
                                                }
                                            },
                                            modifier = Modifier.size(32.dp).testTag("folderToggle_${folder.id}")
                                        ) {
                                            Icon(
                                                if (row.expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = stringResource(
                                                    if (row.expanded) Res.string.library_folder_collapse
                                                    else Res.string.library_folder_expand,
                                                    folder.name
                                                )
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.width(32.dp))
                                    }
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else LocalContentColor.current
                                    )
                                    Spacer(Modifier.width(ZabankhanDesign.spacing.xSmall))
                                    Text(
                                        folder.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.Bold else null,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                                    )
                                }
                            },
                            onClick = {
                                if (folder.id !in expandedFolderIds) expandedFolderIds = expandedFolderIds + folder.id
                                onFolderFilterChange(FolderFilter.Folder(folder.id))
                                folderMenuExpanded = false
                            },
                            modifier = Modifier.testTag("folderFilter_${folder.id}")
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().padding(horizontal = ZabankhanDesign.spacing.small)) {
                val selectedLevel = (state.levelFilter as? LevelFilter.Level)?.level
                OutlinedButton(
                    onClick = { levelMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth().testTag("levelFilters")
                ) {
                    Text(
                        if (state.levelFilter == LevelFilter.All) stringResource(Res.string.text_level_all)
                        else selectedLevel ?: stringResource(Res.string.text_level_unspecified),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(expanded = levelMenuExpanded, onDismissRequest = { levelMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.text_level_all)) },
                        onClick = { onLevelFilterChange(LevelFilter.All); levelMenuExpanded = false }
                    )
                    (TEXT_LEVELS + listOf(null)).filter { level ->
                        state.allDocuments.any { it.level == level } || state.levelFilter == LevelFilter.Level(level)
                    }.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level ?: stringResource(Res.string.text_level_unspecified)) },
                            onClick = { onLevelFilterChange(LevelFilter.Level(level)); levelMenuExpanded = false }
                        )
                    }
                }
            }

            if (state.tags.isNotEmpty()) {
                FlowRow(modifier = Modifier.fillMaxWidth().testTag("tagFilters")) {
                    state.tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in state.selectedTagIds,
                            onClick = { onTagFilterToggle(tag.id) },
                            label = { Text(tag.name) },
                            leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                            modifier = Modifier.padding(horizontal = 4.dp).testTag("tagFilter_${tag.id}")
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZabankhanDesign.spacing.small),
                horizontalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.xSmall)
            ) {
                LibraryShelf.entries.forEach { shelf ->
                    val label = when (shelf) {
                        LibraryShelf.READING -> Res.string.library_shelf_reading
                        LibraryShelf.FINISHED -> Res.string.library_shelf_finished
                        LibraryShelf.ALL -> Res.string.library_shelf_all
                    }
                    val count = when (shelf) {
                        LibraryShelf.READING -> state.shelfCounts.reading
                        LibraryShelf.FINISHED -> state.shelfCounts.finished
                        LibraryShelf.ALL -> state.shelfCounts.all
                    }
                    val tag = when (shelf) {
                        LibraryShelf.READING -> "shelfReading"
                        LibraryShelf.FINISHED -> "shelfFinished"
                        LibraryShelf.ALL -> "shelfAll"
                    }
                    FilterChip(
                        selected = state.shelf == shelf,
                        onClick = { onShelfSelect(shelf) },
                        label = { Text(stringResource(label, count), maxLines = 1) },
                        modifier = Modifier.testTag(tag)
                    )
                }
            }

            val documentItem: @Composable (TextDocument, Int) -> Unit = { doc, depth ->
                LibraryRow(
                    doc,
                    onClick = { if (folderSelectionMode) clearFolderSelection() else if (state.isSelecting) onToggleSelection(doc.id) else onOpen(doc) },
                    onLongClick = { clearFolderSelection(); onStartSelection(doc.id) },
                    selected = doc.id in state.selectedIds,
                    selectionMode = state.isSelecting,
                    onEditRequest = {
                        pendingEdit = doc
                        onLoadBody(doc) { body -> pendingEditBody = body }
                    },
                    onMoveRequest = { pendingMove = doc },
                    onLevelRequest = { levelTargets = listOf(doc) },
                    onTagsRequest = { pendingTags = doc },
                    folderName = if (state.isSearching) doc.folderId?.let { id ->
                        pathTo(state.folders, id).takeIf { it.isNotEmpty() }?.joinToString(" / ") { it.name }
                    } else null,
                    tagNames = state.tags.filter { it.id in state.tagIdsByText[doc.id].orEmpty() }.map { it.name },
                    completed = state.completionByTextId[doc.id] == true,
                    bodySnippet = state.bodySnippets[doc.id]?.takeIf {
                        !doc.title.contains(state.query.trim(), ignoreCase = true)
                    },
                    onReadMarkRequest = {
                        val finished = isTextFinished(doc, state.completionByTextId[doc.id] == true)
                        onReadMark(listOf(doc.id), if (finished) READ_MARK_NOT_FINISHED else READ_MARK_FINISHED)
                    },
                    onDeleteRequest = { pendingDelete = doc },
                    modifier = Modifier.padding(start = (depth * 20).coerceAtMost(100).dp)
                )
                HorizontalDivider()
            }
            when {
                state.documents.isEmpty() && treeRows.isEmpty() && state.totalDocumentCount == 0 && !state.isSearching -> EditorialEmptyState(
                    icon = Icons.Default.Article,
                    title = stringResource(Res.string.library_empty_title),
                    body = stringResource(Res.string.library_empty_body),
                    actionLabel = stringResource(Res.string.action_add_text),
                    onAction = onAddText,
                    modifier = Modifier.fillMaxSize()
                )
                state.documents.isEmpty() && (treeRows.isEmpty() || state.totalDocumentCount > 0) -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            state.isSearching || state.isFiltering && state.shelfCounts.all == 0 ->
                                if (state.isFiltering) stringResource(Res.string.library_no_matches_filters)
                                else stringResource(Res.string.library_no_results, state.query)
                            state.shelf == LibraryShelf.FINISHED -> stringResource(Res.string.library_finished_empty)
                            state.shelf == LibraryShelf.READING && state.shelfCounts.finished > 0 ->
                                stringResource(Res.string.library_reading_empty)
                            else -> stringResource(Res.string.library_no_matches_filters)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = ZabankhanDesign.spacing.medium)
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (state.isSearching) {
                        items(state.documents, key = { it.id }) { doc -> documentItem(doc, 0) }
                    } else {
                        treeRows.forEach { row ->
                            val id = row.folder.id
                            val folderDocs = docsByFolder[id].orEmpty()
                            val expandable = row.hasChildren || folderDocs.isNotEmpty()
                            val expanded = id in expandedFolderIds
                            item(key = "folder-$id") {
                                LibraryFolderHeader(
                                    row.folder.name, folderCounts.getValue(id), row.depth, expanded,
                                    onToggle = if (expandable) ({
                                        expandedFolderIds = if (expanded) expandedFolderIds - id else expandedFolderIds + id
                                    }) else null,
                                    modifier = Modifier.testTag("libraryFolder_$id"),
                                    selectionMode = folderSelectionMode,
                                    canSelect = !state.isSelecting,
                                    selected = id in selectedFolderIds,
                                    onSelect = { onClearSelection(); selectedFolderIds = if (id in selectedFolderIds) selectedFolderIds - id else selectedFolderIds + id },
                                    onRename = { renameFolderTarget = row.folder },
                                    onDelete = { deleteFolderTarget = id; selectedFolderDeleteFiles = false }
                                )
                            }
                            if (expanded) items(folderDocs, key = { it.id }) { doc -> documentItem(doc, row.depth + 1) }
                        }
                        if (unfiled.isNotEmpty()) {
                            item(key = "unfiled-header") {
                                LibraryFolderHeader(
                                    stringResource(Res.string.library_filter_unfiled),
                                    unfiled.size, 0, unfiledExpanded,
                                    onToggle = { unfiledExpanded = !unfiledExpanded },
                                    selectionMode = false,
                                    contextActions = false
                                )
                            }
                            if (unfiledExpanded) items(unfiled, key = { it.id }) { doc -> documentItem(doc, 1) }
                        }
                    }
                }
            }
        }
    }

    levelTargets?.let { documents ->
        SetTextLevelDialog(
            level = documents.map { it.level }.distinct().singleOrNull(),
            onDismiss = { levelTargets = null },
            onSelect = { level -> onSetLevel(documents.map { it.id }, level); levelTargets = null }
        )
    }

    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(Res.string.library_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.library_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { onDelete(doc); pendingDelete = null }) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(Res.string.action_cancel)) }
            }
        )
    }

    if (showBulkDelete) {
        AlertDialog(
            onDismissRequest = { showBulkDelete = false },
            title = { Text(pluralStringResource(Res.plurals.library_bulk_delete_title, state.selectedIds.size, state.selectedIds.size)) },
            text = { Text(stringResource(Res.string.library_bulk_delete_message)) },
            confirmButton = { TextButton(onClick = { showBulkDelete = false; onDeleteSelected() }) { Text(stringResource(Res.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { showBulkDelete = false }) { Text(stringResource(Res.string.action_cancel)) } }
        )
    }

    if (showBulkMove) {
        MoveToFolderDialog(
            folders = state.folders,
            onDismiss = { showBulkMove = false },
            onMove = { showBulkMove = false; onMoveSelectedToFolder(it) },
            onCreateAndMove = { showBulkMove = false; onCreateFolderAndMoveSelected(it) }
        )
    }

    if (showBulkTags) {
        EditTagsDialog(
            tags = state.tags,
            initialTagIds = emptySet(),
            addMode = true,
            onDismiss = { showBulkTags = false },
            onSave = { showBulkTags = false; onAddTagsToSelected(it) },
            onCreateAndAssign = { name, tags -> showBulkTags = false; onCreateTagAndAddToSelected(name, tags) }
        )
    }

    pendingEdit?.let { doc -> pendingEditBody?.let { body ->
        AddTextEditor(
            learningLanguageName = state.learningLanguageName,
            initialTitle = doc.title,
            initialBody = body,
            bodyEditable = doc.bodyPath == null,
            dialogTitle = stringResource(Res.string.edit_text_title),
            confirmLabel = stringResource(Res.string.action_save),
            onDismiss = { pendingEdit = null; pendingEditBody = null },
            onSave = { title, body ->
                onEdit(doc, title, body)
                pendingEdit = null
                pendingEditBody = null
            }
        )
    } }


    if (showOrganizer) {
        LibraryOrganizerDialog(
            folders = state.folders,
            documents = state.allDocuments,
            tags = state.tags,
            onDismiss = { showOrganizer = false },
            onCreateFolder = onCreateFolder,
            onRenameFolder = onRenameFolder,
            onDeleteFolder = onDeleteFolder,
            onCreateTag = onCreateTag,
            onRenameTag = onRenameTag,
            onDeleteTag = onDeleteTag,
            onCreateFolderIn = onCreateFolderIn,
            onMoveFolder = onMoveFolder
        )
    }

    renameFolderTarget?.let { folder ->
        FolderRenameDialog(folder.name, onDismiss = { renameFolderTarget = null }, onSave = {
            onRenameFolder(folder.id, it); renameFolderTarget = null
        })
    }
    deleteFolderTarget?.let { id ->
        FolderDeleteConfirmDialog(
            deleteFiles = selectedFolderDeleteFiles,
            onDeleteFilesChange = { selectedFolderDeleteFiles = it },
            onDismiss = { deleteFolderTarget = null; selectedFolderDeleteFiles = false },
            onConfirm = {
                if (selectedFolderDeleteFiles) confirmFolderFilesDelete = id
                else { onDeleteFolder(id, false); deleteFolderTarget = null; selectedFolderDeleteFiles = false }
            }
        )
    }
    if (deleteSelectedFolders) {
        FolderDeleteConfirmDialog(
            deleteFiles = selectedFolderDeleteFiles,
            onDeleteFilesChange = { selectedFolderDeleteFiles = it },
            onDismiss = { deleteSelectedFolders = false; selectedFolderDeleteFiles = false },
            onConfirm = {
                val roots = selectedFolderRoots(state.folders, selectedFolderIds)
                if (selectedFolderDeleteFiles) {
                    confirmSelectedFoldersFilesDelete = roots
                } else {
                    roots.forEach { onDeleteFolder(it, false) }
                    clearFolderSelection()
                }
            }
        )
    }
    confirmFolderFilesDelete?.let { id ->
        val descendants = remember(state.folders, id) { descendantIds(state.folders, id) }
        val count = state.allDocuments.count { it.folderId in descendants }
        AlertDialog(
            onDismissRequest = { confirmFolderFilesDelete = null; deleteFolderTarget = null; selectedFolderDeleteFiles = false },
            title = { Text(stringResource(Res.string.library_delete_folder_with_files_title)) },
            text = { Text(stringResource(Res.string.library_delete_folder_with_files_confirm, count)) },
            confirmButton = { TextButton(onClick = {
                onDeleteFolder(id, true); confirmFolderFilesDelete = null; deleteFolderTarget = null; selectedFolderDeleteFiles = false
            }) { Text(stringResource(Res.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmFolderFilesDelete = null; deleteFolderTarget = null; selectedFolderDeleteFiles = false }) { Text(stringResource(Res.string.action_cancel)) } }
        )
    }
    confirmSelectedFoldersFilesDelete?.let { ids ->
        val removedFolders = remember(state.folders, ids) { ids.flatMapTo(mutableSetOf()) { descendantIds(state.folders, it) } }
        val count = state.allDocuments.count { it.folderId in removedFolders }
        AlertDialog(
            onDismissRequest = { confirmSelectedFoldersFilesDelete = null; deleteSelectedFolders = false; selectedFolderDeleteFiles = false },
            title = { Text(stringResource(Res.string.library_delete_folder_with_files_title)) },
            text = { Text(stringResource(Res.string.library_delete_folder_with_files_confirm, count)) },
            confirmButton = { TextButton(onClick = {
                ids.forEach { onDeleteFolder(it, true) }
                confirmSelectedFoldersFilesDelete = null
                clearFolderSelection()
            }) { Text(stringResource(Res.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmSelectedFoldersFilesDelete = null; deleteSelectedFolders = false; selectedFolderDeleteFiles = false }) { Text(stringResource(Res.string.action_cancel)) } }
        )
    }

    pendingMove?.let { doc ->
        MoveToFolderDialog(
            doc = doc,
            folders = state.folders,
            onDismiss = { pendingMove = null },
            onMove = { onMoveToFolder(doc, it); pendingMove = null },
            onCreateAndMove = { onCreateFolderAndMove(doc, it); pendingMove = null }
        )
    }

    pendingTags?.let { doc ->
        EditTagsDialog(
            doc = doc,
            tags = state.tags,
            initialTagIds = state.tagIdsByText[doc.id].orEmpty(),
            onDismiss = { pendingTags = null },
            onSave = { onSetTags(doc, it); pendingTags = null },
            onCreateAndAssign = { name, selected ->
                onCreateTagAndAssign(doc, name, selected); pendingTags = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryFolderHeader(
    title: String,
    count: Int,
    depth: Int,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    contextActions: Boolean = true,
    canSelect: Boolean = true
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
    Row(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                onClick = { if (selectionMode) onSelect() else onToggle?.invoke() },
                onLongClick = { if (contextActions) menuExpanded = true }
            )
            .heightIn(min = 48.dp)
            .padding(start = (16 + (depth * 20).coerceAtMost(100)).dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onSelect() }, modifier = Modifier.size(32.dp).testTag("folderCheckbox"))
        } else if (onToggle != null) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        } else {
            Spacer(Modifier.width(24.dp))
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall.merge(contentStyle(title)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    DropdownMenu(expanded = contextActions && menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(text = { Text(stringResource(Res.string.action_edit)) }, onClick = { menuExpanded = false; onRename() }, modifier = Modifier.testTag("folderRename"))
        DropdownMenuItem(text = { Text(stringResource(Res.string.action_delete)) }, onClick = { menuExpanded = false; onDelete() }, modifier = Modifier.testTag("folderDelete"))
        if (canSelect) DropdownMenuItem(text = { Text(stringResource(Res.string.library_select_folder)) }, onClick = { menuExpanded = false; onSelect() }, modifier = Modifier.testTag("folderSelect"))
    }
    }
    HorizontalDivider()
}

@Composable
private fun FolderRenameDialog(initialName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.library_rename_folder)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.testTag("folderRenameInput")) },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

@Composable
private fun FolderDeleteConfirmDialog(
    deleteFiles: Boolean,
    onDeleteFilesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.library_delete_folder_confirm)) },
        text = { Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = deleteFiles, onCheckedChange = onDeleteFilesChange)
            Text(stringResource(Res.string.library_delete_folder_files))
        } },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun LibraryRow(
    doc: TextDocument,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    onEditRequest: () -> Unit,
    onMoveRequest: () -> Unit,
    onLevelRequest: () -> Unit,
    onTagsRequest: () -> Unit,
    folderName: String?,
    tagNames: List<String>,
    completed: Boolean,
    onDeleteRequest: () -> Unit,
    onReadMarkRequest: () -> Unit,
    bodySnippet: String? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("libraryRow_${doc.id}")
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.size(ZabankhanDesign.sizes.thumbnailSmall))
        } else {
            DocumentThumbnail(doc, modifier = Modifier.size(ZabankhanDesign.sizes.thumbnailSmall))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(doc.title, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall.merge(contentStyle(doc.title)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (doc.pdfPath != null) {
                    Spacer(Modifier.width(4.dp))
                    MetadataBadge(text = stringResource(Res.string.ed_type_pdf), modifier = Modifier.testTag("pdfBadge_${doc.id}"))
                }
            }
            bodySnippet?.takeIf { it.isNotBlank() }?.let { snippet ->
                Text(
                    snippet.replace(Regex("\\s+"), " ").trim(),
                    style = MaterialTheme.typography.bodySmall.merge(contentStyle(snippet)),
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("librarySnippet_${doc.id}")
                )
            }
            Spacer(Modifier.height(4.dp))
            Row {
                val organizerMetadata = buildList {
                    if (folderName != null) add(folderName)
                    addAll(tagNames)
                    if (doc.sourceName != null) add(doc.sourceName)
                }.joinToString(" · ")
                if (organizerMetadata.isNotEmpty()) {
                    Text(
                        organizerMetadata,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = ZabankhanDesign.spacing.half)
                    )
                }
                val readLabel = when (readingStatus(doc, completed)) {
                    ReadingStatus.NOT_STARTED -> stringResource(Res.string.library_status_new)
                    ReadingStatus.IN_PROGRESS -> stringResource(Res.string.library_status_in_progress)
                    ReadingStatus.READ -> stringResource(Res.string.library_status_read)
                }
                MetadataBadge(text = readLabel)
                Spacer(Modifier.width(ZabankhanDesign.spacing.half))
                TextLevelBadge(doc.level)
            }
        }
        if (!selectionMode) Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.library_row_more_actions, doc.title)
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.text_level_set)) },
                    modifier = Modifier.testTag("setLevel_${doc.id}"),
                    leadingIcon = { Icon(Icons.Default.Stairs, contentDescription = null) },
                    onClick = { menuExpanded = false; onLevelRequest() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (isTextFinished(doc, completed))
                        Res.string.library_move_back_reading else Res.string.library_mark_finished)) },
                    modifier = Modifier.testTag("readMark_${doc.id}"),
                    leadingIcon = {
                        Icon(
                            if (isTextFinished(doc, completed)) Icons.AutoMirrored.Filled.Undo else Icons.Default.DoneAll,
                            contentDescription = null
                        )
                    },
                    onClick = { menuExpanded = false; onReadMarkRequest() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_edit)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onEditRequest() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.library_move_to_folder)) },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    modifier = Modifier.testTag("moveToFolder_${doc.id}"),
                    onClick = { menuExpanded = false; onMoveRequest() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.library_edit_tags)) },
                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                    modifier = Modifier.testTag("editTags_${doc.id}"),
                    onClick = { menuExpanded = false; onTagsRequest() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_delete)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; onDeleteRequest() }
                )
            }
        }
    }
}

/** Selected folders whose ancestors are not also selected; deleting a root already removes its subtree. */
internal fun selectedFolderRoots(folders: List<com.ziaee.zabankhan.data.LibraryFolder>, selected: List<Long>): List<Long> =
    selected.filter { id -> pathTo(folders, id).dropLast(1).none { it.id in selected } }
