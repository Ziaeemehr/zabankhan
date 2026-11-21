package com.ziaee.zabankhan.ui

// Copied file-for-file from French Reader ui/VocabListScreen.kt; platform services use the shared container.
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.content.*
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.datapack.LanguageDataPacks
import com.ziaee.zabankhan.files.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.srs.*
import com.ziaee.zabankhan.ui.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.getString

/** Scope sentinels for [VocabListScreen]'s filter chips -- real list ids are
 * always >= 1 (Room autoGenerate), so these never collide with one. */
const val VOCAB_SCOPE_ALL = -1L
const val VOCAB_SCOPE_UNFILED = -2L

class VocabListViewModel(
    private val app: AppViewModel, private val settings: AppSettings, private val scope: CoroutineScope,
) {
    private val db = app.container.database
    private val lang = requireNotNull(settings.learningLang)
    private val repository = ImportRepository(db, requireNotNull(Languages.byCode(lang)), settings.meaningLang)
    val entries = db.vocab().observeAll(lang).stateIn(scope, SharingStarted.Eagerly, emptyList())
    val lists = db.vocab().observeLists(lang).stateIn(scope, SharingStarted.Eagerly, emptyList())
    val logs = flow { while (true) { emit(db.vocab().reviewLogSince(lang, 0)); delay(30_000) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    fun setLearned(entry: VocabEntry, learned: Boolean) = app.action {
        db.vocab().update(if (!learned && entry.leitnerBox >= 5)
            entry.copy(learned = false, leitnerBox = 4, nextReviewAtMs = nowMs())
            else entry.copy(learned = learned))
    }
    fun edit(entry: VocabEntry, word: String, meaning: String?, sentence: String) = app.action {
        db.vocab().update(entry.copy(word = word, meaning = meaning, sentence = sentence))
    }
    fun delete(entry: VocabEntry) = app.action { db.vocab().delete(entry) }
    fun delete(ids: List<Long>) = app.action { ids.chunked(900).forEach { db.vocab().deleteIds(it) } }
    fun moveToList(ids: List<Long>, listId: Long?) = app.action { ids.chunked(900).forEach { db.vocab().moveToList(it, listId) } }
    fun createList(name: String, onCreated: (Long) -> Unit) = app.action {
        if (name.isNotBlank()) onCreated(db.vocab().insertList(VocabList(lang = lang, name = name.trim())))
    }
    fun renameList(list: VocabList, name: String) = app.action {
        if (name.isNotBlank()) db.vocab().renameList(list.id, name.trim())
    }
    fun deleteList(list: VocabList, deleteCards: Boolean) = app.action { db.vocab().deleteListAndCards(list.id, deleteCards) }
    fun importAnki(raw: String, keepProgress: Boolean, onDone: (WordImportResult?) -> Unit) = scope.launch {
        onDone(try { repository.anki(raw, keepProgress) } catch (e: CancellationException) { throw e } catch (_: Exception) { null })
    }
    fun importCsv(file: CsvWordFile, target: CsvImportTarget, onDone: (WordImportResult?) -> Unit) = scope.launch {
        onDone(try { repository.csv(file, target) } catch (e: CancellationException) { throw e } catch (_: Exception) { null })
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun VocabListScreen(
    appVm: AppViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
    onOpenReview: (Long, Set<Long>) -> Unit,
    onOpenDataset: () -> Unit,
    asTab: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val vm = remember(appVm, settings.learningLang, settings.meaningLang) { VocabListViewModel(appVm, settings, scope) }
    val entries by vm.entries.collectAsState()
    val lists by vm.lists.collectAsState()
    var query by remember { mutableStateOf("") }
    var meaningsVisible by remember { mutableStateOf(true) }
    var previewEntryId by remember { mutableStateOf<Long?>(null) }
    var dictionaryEntry by remember { mutableStateOf<VocabEntry?>(null) }
    var dictionaryTappedWord by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf<VocabEntry?>(null) }
    var selectedScope by remember { mutableStateOf(VOCAB_SCOPE_ALL) }
    var selectedStatus by remember { mutableStateOf<VocabStatus?>(null) }
    var showNewListDialog by remember { mutableStateOf(false) }
    var scopeMenuExpanded by remember { mutableStateOf(false) }
    var pendingDeleteList by remember { mutableStateOf<VocabList?>(null) }
    var renamingList by remember { mutableStateOf<VocabList?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showMoveToListDialog by remember { mutableStateOf(false) }
    var pendingBulkDelete by remember { mutableStateOf(false) }
    var createListForSelection by remember { mutableStateOf(false) }
    var showManualDictionary by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingAnkiUri by remember { mutableStateOf<String?>(null) }
    var keepAnkiProgress by remember { mutableStateOf(true) }
    var showImportHelp by remember { mutableStateOf(false) }
    val sampleSaver = rememberWordsSampleSaver()
    val sample = wordsCsvSample(requireNotNull(settings.learningLang))
    var pendingCsv by remember { mutableStateOf<Pair<String, CsvWordFile>?>(null) }
    val ankiPicker = rememberFilePicker(listOf(FileKind.JSON, FileKind.CSV, FileKind.TEXT)) { picked ->
        if (picked != null) scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) { FileSystem.SYSTEM.read(picked.path.toPath()) { readUtf8() } }
                if (picked.displayName.endsWith(".json", ignoreCase = true)) {
                    keepAnkiProgress = true
                    pendingAnkiUri = raw
                } else {
                    val file = parseCsvWords(raw)
                    if (file.words.isEmpty() && file.malformedRows == 0)
                        snackbarHostState.showSnackbar(getString(Res.string.b_words_csv_import_empty))
                    else pendingCsv = picked.displayName.substringBeforeLast('.') to file
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { snackbarHostState.showSnackbar(getString(Res.string.b_words_csv_import_failed)) }
        }
    }

    val scoped = remember(entries, selectedScope) {
        when (selectedScope) {
            VOCAB_SCOPE_ALL -> entries
            VOCAB_SCOPE_UNFILED -> entries.filter { it.listId == null }
            else -> entries.filter { it.listId == selectedScope }
        }
    }
    val spec = requireNotNull(Languages.byCode(requireNotNull(settings.learningLang)))
    val filtered = remember(scoped, query, selectedStatus, spec) {
        filterVocabulary(scoped, -1L, query, selectedStatus, spec)
    }
    val lemmaWords by produceState<Set<String>>(emptySet(), settings.learningLang) {
        value = settings.learningLang?.let { LanguageDataPacks.load(it) }?.lemmas?.wordForms.orEmpty()
    }
    val completions by produceState<List<String>>(emptyList(), entries, query, spec, lemmaWords) {
        value = if (query.trim().length < 2) emptyList()
        else withContext(Dispatchers.Default) { vocabularyCompletions(entries, query, spec, lemmaWords) }
    }
    val logs by vm.logs.collectAsState()
    val dueCount = remember(filtered, logs, settings.dailyNewCards) {
        val now = nowMs()
        val remainingNew = remainingNewCards(logs, settings.dailyNewCards, VocabSrs.startOfDayMs(now))
        reviewableCount(filtered.map { it.reviewCard() }, now, remainingNew)
    }
    val visibleIds = remember(filtered) { filtered.mapTo(mutableSetOf()) { it.id } }
    val isSelecting = selectedIds.isNotEmpty()

    LaunchedEffect(visibleIds) {
        selectedIds = selectedIds.intersect(visibleIds)
    }
    LaunchedEffect(selectedIds) {
        if (selectedIds.isEmpty()) {
            showMoveToListDialog = false
            pendingBulkDelete = false
            if (createListForSelection) showNewListDialog = false
            createListForSelection = false
        }
    }
    BackHandler(enabled = isSelecting) { selectedIds = emptySet() }

    if (showImportHelp) {
        AlertDialog(
            onDismissRequest = { showImportHelp = false },
            title = { Text(stringResource(Res.string.b_words_csv_import_help_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(Res.string.b_words_csv_import_help_body))
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        // Left-to-right even in the Persian UI: it's a file's contents.
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            // One row per line (scroll sideways) so the columns stay readable.
                            Text(
                                sample.trim(),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                softWrap = false,
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.b_words_csv_import_help_excel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { sampleSaver(sample, "zabankhan_words.csv") }) {
                        Text(stringResource(Res.string.b_words_csv_import_save_sample))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportHelp = false
                    ankiPicker()
                }) { Text(stringResource(Res.string.b_words_csv_import_choose_file)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportHelp = false }) { Text(stringResource(Res.string.b_words_action_cancel)) }
            }
        )
    }

    pendingCsv?.let { (fileName, file) ->
        CsvImportDialog(
            fileName = fileName,
            file = file,
            lists = lists,
            onDismiss = { pendingCsv = null },
            onImport = { target ->
                pendingCsv = null
                vm.importCsv(file, target) { r ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (r == null) getString(Res.string.b_words_csv_import_failed)
                            else getString(Res.string.b_words_anki_import_done, r.imported, r.skipped, r.decks)
                        )
                    }
                }
            }
        )
    }

    pendingAnkiUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingAnkiUri = null },
            title = { Text(stringResource(Res.string.b_words_anki_import_title)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { keepAnkiProgress = !keepAnkiProgress },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = keepAnkiProgress, onCheckedChange = { keepAnkiProgress = it })
                    Text(stringResource(Res.string.b_words_anki_import_keep_progress))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val keep = keepAnkiProgress
                    pendingAnkiUri = null
                    scope.launch {
                        val json: String? = uri
                        if (json == null) {
                            snackbarHostState.showSnackbar(getString(Res.string.b_words_anki_import_failed))
                        } else {
                            vm.importAnki(json, keep) { r ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (r == null) getString(Res.string.b_words_anki_import_failed)
                                        else getString(Res.string.b_words_anki_import_done, r.imported, r.skipped, r.decks)
                                    )
                                }
                            }
                        }
                    }
                }) { Text(stringResource(Res.string.b_words_anki_import_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAnkiUri = null }) { Text(stringResource(Res.string.b_words_action_cancel)) }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelecting) {
                TopAppBar(
                    title = {
                        Text(pluralStringResource(Res.plurals.b_words_vocab_selection_count, selectedIds.size, selectedIds.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.b_words_vocab_selection_close))
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectedIds = visibleIds }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(Res.string.b_words_vocab_select_all))
                        }
                        IconButton(onClick = { showMoveToListDialog = true }) {
                            Icon(Icons.Default.Folder, contentDescription = stringResource(Res.string.b_words_vocab_bulk_move))
                        }
                        IconButton(onClick = { pendingBulkDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.b_words_vocab_bulk_delete))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(Res.string.b_words_vocab_screen_title)) },
                    navigationIcon = {
                        if (!asTab) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.b_words_accessibility_back))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showManualDictionary = true }) {
                            Icon(Icons.Default.ManageSearch, contentDescription = stringResource(Res.string.b_words_manual_dictionary_action))
                        }
                        IconButton(onClick = { showImportHelp = true }) {
                            Icon(Icons.Default.Upload, contentDescription = stringResource(Res.string.b_words_anki_import_action))
                        }
                        IconButton(onClick = onOpenDataset) {
                            Icon(Icons.Default.MenuBook, contentDescription = stringResource(Res.string.b_words_dataset_title))
                        }
                        IconButton(onClick = { meaningsVisible = !meaningsVisible }) {
                            Icon(
                                if (meaningsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = stringResource(Res.string.b_words_accessibility_hide_meaning)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { scopeMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when (selectedScope) {
                            VOCAB_SCOPE_ALL -> stringResource(Res.string.b_words_vocab_list_all)
                            VOCAB_SCOPE_UNFILED -> stringResource(Res.string.b_words_vocab_list_uncategorized)
                            else -> lists.firstOrNull { it.id == selectedScope }?.name
                                ?: stringResource(Res.string.b_words_vocab_list_all)
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = scopeMenuExpanded,
                    onDismissRequest = { scopeMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.b_words_vocab_list_all)) },
                        onClick = { selectedScope = VOCAB_SCOPE_ALL; scopeMenuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.b_words_vocab_list_uncategorized)) },
                        onClick = { selectedScope = VOCAB_SCOPE_UNFILED; scopeMenuExpanded = false }
                    )
                    lists.forEach { list ->
                        DropdownMenuItem(
                            text = { Text(list.name) },
                            onClick = { selectedScope = list.id; scopeMenuExpanded = false },
                            trailingIcon = {
                                Row {
                                IconButton(onClick = { scopeMenuExpanded = false; renamingList = list }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.b_words_vocab_rename_list))
                                }
                                IconButton(onClick = {
                                    scopeMenuExpanded = false
                                    pendingDeleteList = list
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.b_words_accessibility_delete_list, list.name))
                                }
                                }
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.b_words_vocab_list_new)) },
                        onClick = { showNewListDialog = true; scopeMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (dueCount > 0) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Style, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(Res.string.b_words_vocab_due_now, dueCount), style = MaterialTheme.typography.titleSmall)
                                if (dueCount == 0) {
                                    Text(stringResource(Res.string.b_words_vocab_nothing_due), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Button(
                                onClick = { onOpenReview(selectedScope, visibleIds) },
                                enabled = dueCount > 0
                            ) {
                                Text(stringResource(Res.string.b_words_vocab_review_start))
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statuses = VocabStatus.values()
                        val counts = statuses.associateWith { status -> scoped.count { it.srsState().status() == status } }
                        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            statuses.take(2).forEach { StatusChip(it, counts.getValue(it), selectedStatus) { selectedStatus = if (selectedStatus == it) null else it } }
                        }
                        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            if (scoped.isNotEmpty()) {
                                Canvas(Modifier.size(96.dp)) {
                                    var startAngle = -90f
                                    statuses.forEach { status ->
                                        val sweepAngle = 360f * counts.getValue(status) / scoped.size
                                        if (sweepAngle > 0f) drawArc(status.pieColor(dark), startAngle, sweepAngle, useCenter = true)
                                        startAngle += sweepAngle
                                    }
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            statuses.takeLast(2).forEach { StatusChip(it, counts.getValue(it), selectedStatus) { selectedStatus = if (selectedStatus == it) null else it } }
                        }
                    }
                }

                item {
                    WordAutocompleteField(
                        value = query,
                        onValueChange = { query = it },
                        suggestions = completions,
                        label = stringResource(Res.string.b_words_vocab_search_hint),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }

                if (filtered.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (entries.isEmpty())
                                    stringResource(Res.string.b_words_vocab_empty_hint)
                                else stringResource(Res.string.b_words_vocab_search_no_results)
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { entry ->
                        VocabRow(
                            entry = entry,
                            showMeaning = meaningsVisible,
                            selected = entry.id in selectedIds,
                            selectionMode = isSelecting,
                            onClick = {
                                if (isSelecting) selectedIds = selectedIds.toggle(entry.id)
                                else {
                                    previewEntryId = entry.id
                                }
                            },
                            onLongClick = { selectedIds = selectedIds + entry.id },
                            onEdit = { editingText = entry },
                            onToggleLearned = { vm.setLearned(entry, !entry.learned) },
                            onDelete = { vm.delete(entry) }
                        )
                    }
                }
            }
        }
    }

    editingText?.let { entry ->
        VocabEditDialog(entry, onDismiss = { editingText = null }) { w, m, s ->
            vm.edit(entry, w, m, s); editingText = null
        }
    }

    val previewEntry = previewEntryId?.let { id -> entries.firstOrNull { it.id == id } }
    previewEntry?.let { entry ->
        Dialog(onDismissRequest = { previewEntryId = null }) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    VocabPreviewFlashcard(entry, appVm, settings) { word ->
                        dictionaryTappedWord = word
                        dictionaryEntry = entry
                        previewEntryId = null
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { dictionaryEntry = entry; previewEntryId = null }) {
                            Text(stringResource(Res.string.b_words_vocab_open_dictionary))
                        }
                        TextButton(onClick = { editingText = entry; previewEntryId = null }) {
                            Text(stringResource(Res.string.b_words_action_edit))
                        }
                        TextButton(onClick = { previewEntryId = null }) { Text(stringResource(Res.string.b_words_action_close)) }
                    }
                }
            }
        }
    }
    dictionaryEntry?.let { entry ->
        DictionarySheet(appVm, settings,
            TextDocument(id = entry.textId ?: 0L, lang = entry.lang, title = entry.word, body = entry.sentence),
            ReaderSelection(dictionaryTappedWord ?: entry.word, entry.sentence, 0..0, false),
            entries, emptyList(), { dictionaryEntry = null; dictionaryTappedWord = null })
    }
    ManualDictionaryHost(appVm, settings, showManualDictionary, { showManualDictionary = false })
    renamingList?.let { list ->
        var name by remember(list.id) { mutableStateOf(list.name) }
        AlertDialog(onDismissRequest = { renamingList = null },
            title = { Text(stringResource(Res.string.b_words_vocab_rename_list)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.b_words_vocab_list_name_field)) }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.renameList(list, name); renamingList = null }) { Text(stringResource(Res.string.b_words_action_save)) } },
            dismissButton = { TextButton(onClick = { renamingList = null }) { Text(stringResource(Res.string.b_words_action_cancel)) } })
    }

    if (showNewListDialog) {
        var newListName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                showNewListDialog = false
                createListForSelection = false
            },
            title = { Text(stringResource(Res.string.b_words_vocab_list_new)) },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text(stringResource(Res.string.b_words_vocab_list_name_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds.toList()
                    vm.createList(newListName) { newId ->
                        if (createListForSelection) {
                            vm.moveToList(ids, newId)
                            selectedIds = emptySet()
                        } else {
                            selectedScope = newId
                        }
                        createListForSelection = false
                    }
                    showNewListDialog = false
                }, enabled = newListName.isNotBlank()) { Text(stringResource(Res.string.b_words_action_create)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewListDialog = false
                    createListForSelection = false
                }) { Text(stringResource(Res.string.b_words_action_cancel)) }
            }
        )
    }

    pendingDeleteList?.let { list ->
        var deleteCards by remember(list.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pendingDeleteList = null },
            title = { Text(stringResource(Res.string.b_words_vocab_delete_list_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            if (deleteCards) Res.string.b_words_vocab_delete_list_with_cards_message else Res.string.b_words_vocab_delete_list_message,
                            list.name
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = deleteCards, onCheckedChange = { deleteCards = it })
                        Text(stringResource(Res.string.b_words_vocab_delete_list_cards))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedScope == list.id) selectedScope = VOCAB_SCOPE_ALL
                    vm.deleteList(list, deleteCards)
                    pendingDeleteList = null
                }) { Text(stringResource(Res.string.b_words_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteList = null }) { Text(stringResource(Res.string.b_words_action_cancel)) }
            }
        )
    }

    if (showMoveToListDialog) {
        AlertDialog(
            onDismissRequest = { showMoveToListDialog = false },
            title = { Text(stringResource(Res.string.b_words_vocab_move_to_list_title)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    item {
                        TextButton(
                            onClick = {
                                vm.moveToList(selectedIds.toList(), null)
                                selectedIds = emptySet()
                                showMoveToListDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(Res.string.b_words_vocab_list_uncategorized)) }
                    }
                    items(lists, key = { it.id }) { list ->
                        TextButton(
                            onClick = {
                                vm.moveToList(selectedIds.toList(), list.id)
                                selectedIds = emptySet()
                                showMoveToListDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(list.name) }
                    }
                    item {
                        TextButton(
                            onClick = {
                                createListForSelection = true
                                showMoveToListDialog = false
                                showNewListDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(Res.string.b_words_vocab_list_new_ellipsis)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveToListDialog = false }) {
                    Text(stringResource(Res.string.b_words_action_cancel))
                }
            }
        )
    }

    if (pendingBulkDelete) {
        AlertDialog(
            onDismissRequest = { pendingBulkDelete = false },
            title = {
                Text(pluralStringResource(Res.plurals.b_words_vocab_bulk_delete_title, selectedIds.size, selectedIds.size))
            },
            text = { Text(stringResource(Res.string.b_words_vocab_bulk_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(selectedIds.toList())
                    selectedIds = emptySet()
                    pendingBulkDelete = false
                }) { Text(stringResource(Res.string.b_words_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkDelete = false }) {
                    Text(stringResource(Res.string.b_words_action_cancel))
                }
            }
        )
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@Composable
private fun StatusChip(status: VocabStatus, count: Int, selectedStatus: VocabStatus?, onClick: () -> Unit) {
    val statusColor = status.color()
    val containerColor = status.containerColor()
    FilterChip(
        modifier = Modifier.fillMaxWidth(),
        selected = selectedStatus == status,
        onClick = onClick,
        leadingIcon = { Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(50))) },
        label = { Text("${stringResource(status.labelRes())} $count", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = statusColor,
            selectedContainerColor = containerColor,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = statusColor
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VocabRow(
    entry: VocabEntry,
    showMeaning: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleLearned: () -> Unit,
    onDelete: () -> Unit
) {
    val status = entry.srsState().status()
    val statusColor = status.color()
    val surface = MaterialTheme.colorScheme.surface
    // Very light status tint over the theme surface, so text always keeps the
    // theme's own on-surface contrast (whatever the app/system theme is).
    val cardColor = if (selected) MaterialTheme.colorScheme.primaryContainer
    else statusColor.copy(alpha = 0.08f).compositeOver(surface)
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Box(
                Modifier.padding(vertical = 12.dp, horizontal = 10.dp).width(4.dp).fillMaxHeight()
                    .background(statusColor, RoundedCornerShape(2.dp))
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.word,
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(6.dp).background(statusColor, RoundedCornerShape(50)))
                        Text(
                            stringResource(status.labelRes()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.sentence,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val displayMeaning = entry.meaning
                if (showMeaning && displayMeaning != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(displayMeaning, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.width(92.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(5) { index ->
                            Surface(
                                modifier = Modifier.weight(1f).height(5.dp),
                                shape = RoundedCornerShape(50),
                                color = if (index < entry.leitnerBox) leitnerBoxColor(index + 1)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ) {}
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        nextReviewText(entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!selectionMode) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onToggleLearned) {
                    Icon(
                        if (entry.learned) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = stringResource(
                            if (entry.learned) Res.string.b_words_vocab_mark_learning else Res.string.b_words_vocab_mark_known
                        ),
                        tint = if (entry.learned) statusColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.b_words_action_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.b_words_accessibility_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun nextReviewText(entry: VocabEntry): String = when (
    val bucket = wordsDueBucket(entry.nextReviewAtMs, nowMs(), entry.srsState().status() == VocabStatus.LEARNED)
) {
    WordsDueBucket.Learned -> stringResource(Res.string.b_words_vocab_next_review_learned)
    WordsDueBucket.Today -> stringResource(Res.string.b_words_vocab_next_review_today)
    is WordsDueBucket.InDays -> if (bucket.days == 1L) {
        stringResource(Res.string.b_words_vocab_next_review_tomorrow)
    } else {
        stringResource(Res.string.b_words_vocab_next_review_in_days, bucket.days)
    }
}

private enum class CsvTargetChoice { COLUMN, EXISTING, NEW }

/** Choose where spreadsheet words go: the file's deck column, an existing deck, or a new one. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun CsvImportDialog(
    fileName: String,
    file: CsvWordFile,
    lists: List<VocabList>,
    onDismiss: () -> Unit,
    onImport: (CsvImportTarget) -> Unit
) {
    var choice by remember {
        mutableStateOf(
            when {
                file.hasDeckColumn -> CsvTargetChoice.COLUMN
                lists.isNotEmpty() -> CsvTargetChoice.EXISTING
                else -> CsvTargetChoice.NEW
            }
        )
    }
    var existing by remember { mutableStateOf(lists.firstOrNull()) }
    var newName by remember { mutableStateOf(fileName) }
    var menuOpen by remember { mutableStateOf(false) }
    val target: CsvImportTarget? = when (choice) {
        CsvTargetChoice.COLUMN -> CsvImportTarget.DeckColumn(fallbackDeck = fileName)
        CsvTargetChoice.EXISTING -> existing?.let { CsvImportTarget.Deck(it.name) }
        CsvTargetChoice.NEW -> newName.trim().takeIf { it.isNotEmpty() }?.let(CsvImportTarget::Deck)
    }

    @Composable
    fun ChoiceRow(value: CsvTargetChoice, label: String) {
        Row(
            Modifier.fillMaxWidth().clickable { choice = value },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = choice == value, onClick = { choice = value })
            Text(label)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.b_words_csv_import_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(pluralStringResource(Res.plurals.b_words_csv_import_found, file.words.size, file.words.size))
                if (file.malformedRows > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pluralStringResource(Res.plurals.b_words_csv_import_malformed, file.malformedRows, file.malformedRows),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("csvMalformedWarning")
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (file.hasDeckColumn) ChoiceRow(CsvTargetChoice.COLUMN, stringResource(Res.string.b_words_csv_import_target_column))
                if (lists.isNotEmpty()) {
                    ChoiceRow(CsvTargetChoice.EXISTING, stringResource(Res.string.b_words_csv_import_target_existing))
                    if (choice == CsvTargetChoice.EXISTING) {
                        ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                            OutlinedTextField(
                                value = existing?.name.orEmpty(),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                lists.forEach { list ->
                                    DropdownMenuItem(
                                        text = { Text(list.name) },
                                        onClick = { existing = list; menuOpen = false }
                                    )
                                }
                            }
                        }
                    }
                }
                ChoiceRow(CsvTargetChoice.NEW, stringResource(Res.string.b_words_csv_import_target_new))
                if (choice == CsvTargetChoice.NEW) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.b_words_csv_import_new_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.b_words_csv_import_duplicates_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { target?.let(onImport) }, enabled = target != null) {
                Text(stringResource(Res.string.b_words_anki_import_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.b_words_action_cancel)) } }
    )
}

private fun VocabStatus.labelRes() = when (this) {
    VocabStatus.NEW -> Res.string.b_words_vocab_status_new
    VocabStatus.LEARNING -> Res.string.b_words_vocab_status_learning
    VocabStatus.REVIEWING -> Res.string.b_words_vocab_status_reviewing
    VocabStatus.LEARNED -> Res.string.b_words_vocab_status_learned
}
