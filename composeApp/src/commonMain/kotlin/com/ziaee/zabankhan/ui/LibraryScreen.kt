package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** AppViewModel and Room host for LibraryContent. */
@Composable
fun LibraryScreen(vm: AppViewModel, settings: AppSettings, open: (Long) -> Unit, add: () -> Unit,
    watch: () -> Unit, discover: () -> Unit = {}, openAt: (Long, String) -> Unit = { id, _ -> open(id) }) {
    val lang = requireNotNull(settings.learningLang)
    val spec = requireNotNull(Languages.byCode(lang))
    val dao = vm.container.texts
    val organizer = vm.container.database.libraryOrganizer()
    val texts by remember(lang) { dao.observeAll(lang) }.collectAsState(emptyList())
    val folders by remember(lang) { organizer.observeFolders(lang) }.collectAsState(emptyList())
    val tags by remember(lang) { organizer.observeTags(lang) }.collectAsState(emptyList())
    val refs by remember(lang) { organizer.observeAllTagRefs(lang) }.collectAsState(emptyList())
    var query by rememberSaveable(lang) { mutableStateOf("") }
    var sort by rememberSaveable(lang) { mutableStateOf(LibrarySort.NEWEST) }
    var shelf by remember(lang) { mutableStateOf(LibraryShelf.READING) }
    var folder by remember(lang) { mutableStateOf<FolderFilter>(FolderFilter.All) }
    var level by remember(lang) { mutableStateOf<LevelFilter>(LevelFilter.All) }
    var tagIds by remember(lang) { mutableStateOf(emptySet<Long>()) }
    var selected by remember(lang) { mutableStateOf(emptySet<Long>()) }
    var snippets by remember(lang, query) { mutableStateOf(emptyMap<Long, String>()) }
    LaunchedEffect(lang, query, texts) {
        if (query.isBlank()) return@LaunchedEffect
        delay(250)
        val indexed = try {
            textSearchMatch(query)?.let { match ->
                dao.searchFullText(lang, match, Int.MAX_VALUE).associate { it.id to it.snippet }
            }.orEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyMap()
        }
        snippets = withContext(Dispatchers.Default) {
            texts.mapNotNull { doc ->
                (indexed[doc.id] ?: bodySnippet(doc.body, query, spec))?.let { doc.id to it }
            }.toMap()
        }
    }
    val tagIdsByText = remember(refs) { refs.groupBy { it.textId }.mapValues { (_, refs) -> refs.mapTo(mutableSetOf()) { it.tagId } } }
    val completionCache = remember(lang) { LibraryCompletionCache() }
    var completion by remember(lang) { mutableStateOf(emptyMap<Long, Boolean>()) }
    LaunchedEffect(lang, texts) {
        val pdfProgress = texts.filter { it.pdfPath != null }.associate { document ->
            document.id to com.ziaee.zabankhan.pdf.PdfReadingProgress(document.lastOffset,
                vm.container.cache.get("pdf:page-count:${document.id}")?.toIntOrNull() ?: 0)
        }
        completion = completionCache.completionFor(texts, { it.body }, { pdfProgress[it] })
    }
    val finishedIds = texts.filter { isTextFinished(it, completion[it.id] == true) }.mapTo(mutableSetOf()) { it.id }
    val filtered = projectLibraryTree(texts, query, sort, folder, tagIds, tagIdsByText, folders,
        snippets.keys, LibraryShelf.ALL, finishedIds, level, spec)
    val visible = projectLibraryTree(filtered, "", sort, shelf = shelf, finishedIds = finishedIds, language = spec)
    val visibleIds = visible.mapTo(mutableSetOf()) { it.id }
    val selection = prunedSelection(selected, visibleIds)
    val state = LibraryUiState(learningLanguageName = spec.nativeName, shelf = shelf, query = query, sort = sort, documents = visible,
        allDocuments = texts, folders = folders, tags = tags, tagIdsByText = tagIdsByText,
        completionByTextId = completion, folderFilter = folder, levelFilter = level,
        selectedTagIds = tagIds, selectedIds = selection, totalDocumentCount = texts.size,
        bodySnippets = snippets, shelfCounts = libraryShelfCounts(filtered, finishedIds))
    suspend fun createFolder(name: String, parent: Long? = null): Long? {
        val clean = name.trim().takeIf { it.isNotEmpty() } ?: return null
        return organizer.findFolderByName(lang, clean, parent)?.id ?: organizer.insertFolder(LibraryFolder(lang = lang, name = clean, parentId = parent))
    }
    suspend fun createTag(name: String): Long? {
        val clean = name.trim().takeIf { it.isNotEmpty() } ?: return null
        return organizer.findTagByName(lang, clean)?.id ?: organizer.insertTag(LibraryTag(lang = lang, name = clean)).takeIf { it >= 0 }
            ?: organizer.findTagByName(lang, clean)?.id
    }
    LibraryContent(state,
        onQueryChange = { query = it }, onSortSelect = { sort = it },
        onShelfSelect = { shelf = it; selected = emptySet() },
        onOpen = { if (query.isNotBlank() && it.id in snippets) openAt(it.id, query.trim()) else open(it.id) },
        onDelete = { doc -> vm.action { organizer.deleteRefsForText(doc.id); dao.delete(doc) } },
        onEdit = { doc, title, body -> vm.action { vm.updateText(doc, title, body) } },
        onLoadBody = { doc, loaded -> loaded(doc.body) },
        onAddText = add, onFilePickerClick = add, onOpenHome = {},
        onFolderFilterChange = { folder = it; selected = emptySet() },
        onLevelFilterChange = { level = it; selected = emptySet() },
        onSetLevel = { ids, value -> vm.action { dao.setLevels(ids, normalizeTextLevel(value)); selected = emptySet() } },
        onReadMark = { ids, mark -> vm.action {
            ids.groupBy { readMarkToStore(mark, completion[it] == true) }.forEach { (stored, ids) -> dao.setReadMarks(ids, stored) }
            selected = emptySet()
        } },
        onTagFilterToggle = { tagIds = toggleSelection(tagIds, it); selected = emptySet() },
        onCreateFolder = { name -> vm.action { createFolder(name) } },
        onCreateFolderIn = { name, parent -> vm.action { createFolder(name, parent) } },
        onRenameFolder = { id, name -> vm.action {
            val clean = name.trim()
            val parent = folders.firstOrNull { it.id == id }?.parentId
            if (clean.isNotBlank() && organizer.findFolderByName(lang, clean, parent)?.id.let { it == null || it == id }) organizer.renameFolder(id, clean)
        } },
        onMoveFolder = { id, parent -> vm.action { if (canMoveFolder(folders, id, parent)) organizer.moveFolder(id, parent) } },
        onDeleteFolder = { id, deleteFiles -> vm.action {
            val ids = descendantIds(folders, id)
            if (deleteFiles) {
                texts.filter { it.folderId in ids }.forEach { organizer.deleteRefsForText(it.id); dao.delete(it) }
                organizer.deleteFolderRows(ids.toList())
            } else organizer.deleteFolder(id)
            if ((folder as? FolderFilter.Folder)?.id in ids) folder = FolderFilter.All
        } },
        onCreateTag = { name -> vm.action { createTag(name) } },
        onRenameTag = { id, name -> vm.action {
            val clean = name.trim()
            if (clean.isNotBlank() && organizer.findTagByName(lang, clean)?.id.let { it == null || it == id }) organizer.renameTag(id, clean)
        } },
        onDeleteTag = { id -> vm.action { organizer.deleteTag(id); tagIds = tagIds - id } },
        onMoveToFolder = { doc, parent -> vm.action { dao.setFolder(doc.id, parent) } },
        onSetTags = { doc, ids -> vm.action { organizer.setTextTags(doc.id, ids.toList()) } },
        onCreateFolderAndMove = { doc, name -> vm.action { createFolder(name)?.let { dao.setFolder(doc.id, it) } } },
        onCreateTagAndAssign = { doc, name, ids -> vm.action { createTag(name)?.let { organizer.setTextTags(doc.id, (ids + it).toList()) } } },
        onToggleSelection = { selected = toggleSelection(selection, it) },
        onStartSelection = { selected = selection + it },
        onSelectAll = { selected = selectAllVisible(selection, it) },
        onClearSelection = { selected = emptySet() },
        onDeleteSelected = { vm.action { texts.filter { it.id in selection }.forEach { organizer.deleteRefsForText(it.id); dao.delete(it) }; selected = emptySet() } },
        onMoveSelectedToFolder = { parent -> vm.action { selection.forEach { dao.setFolder(it, parent) }; selected = emptySet() } },
        onAddTagsToSelected = { ids -> vm.action { organizer.addTagsToTexts(selection.toList(), ids.toList()); selected = emptySet() } },
        onCreateFolderAndMoveSelected = { name -> vm.action { createFolder(name)?.let { parent -> selection.forEach { dao.setFolder(it, parent) } }; selected = emptySet() } },
        onCreateTagAndAddToSelected = { name, ids -> vm.action { createTag(name)?.let { organizer.addTagsToTexts(selection.toList(), (ids + it).toList()) }; selected = emptySet() } },
        importAction = { ImportAddMenu(vm, settings, add, open, watch, discover, trigger = { onImport, busy ->
            FilledIconButton(onClick = onImport, enabled = !busy, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Add, stringResource(Res.string.add_text), modifier = Modifier.size(24.dp)) }
        }) }
    )
}
@Composable
fun AddTextScreen(vm: AppViewModel, settings: AppSettings, back: () -> Unit, open: (Long) -> Unit, initialText: String = "") {
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val language = requireNotNull(Languages.byCode(requireNotNull(settings.learningLang)))
    AddTextEditor(
        learningLanguageName = language.nativeName,
        initialTitle = initialText.lineSequence().firstOrNull { it.isNotBlank() }
            ?.trim()?.trimStart('#')?.trim()?.take(60).orEmpty(),
        initialBody = initialText,
        levelOptions = { TextLevelSelector(level) { level = it } },
        onDismiss = back,
        onSave = { title, body ->
            if (!saving) {
                saving = true
                vm.action {
                    try {
                        open(vm.container.texts.insert(TextDocument(lang = language.code,
                            title = title.trim(), body = body, level = level)))
                    } finally { saving = false }
                }
            }
        }
    )
}
