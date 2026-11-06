package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.datapack.LanguageDataPacks
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.translate.MeaningOption
import com.ziaee.zabankhan.ui.contentStyle
import com.ziaee.zabankhan.ui.theme.ReaderReferenceTheme
import com.ziaee.zabankhan.ui.theme.readerContentFamily
import org.jetbrains.compose.resources.stringResource

internal fun manualDictionaryWord(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

/** Dictionary forms come from the active language pack; missing packs have no suggestions. */
internal fun lemmaSuggestions(word: String, lexicon: LemmaLexicon?, lang: LanguageSpec): List<String> {
    val form = normalizeForMatch(word.trim(), lang)
    if (form.isEmpty() || form.any(Char::isWhitespace)) return emptyList()
    return lexicon?.lemmas(form).orEmpty().filter { it != form }.distinct().take(3)
}

internal data class DictionarySavedState(val exactEntry: VocabEntry? = null, val otherContextCount: Int = 0)

/** Same spelling in a different sentence or document is a separate learning context. */
internal fun dictionarySavedState(entries: List<VocabEntry>, lang: LanguageSpec, textId: Long, word: String, sentence: String): DictionarySavedState {
    val matching = entries.filter { it.lang == lang.code && dictionaryIdentityKey(it.word) == dictionaryIdentityKey(word) }
    val exact = matching.firstOrNull { it.textId == textId && it.sentence == sentence }
    return DictionarySavedState(exact, matching.count { it.textId != textId || it.sentence != sentence })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReferenceDictionarySheet(
    vm: AppViewModel, settings: AppSettings, text: TextDocument, selection: ReaderSelection,
    words: List<VocabEntry>, highlights: List<HighlightEntry>, dismiss: () -> Unit,
    listen: (() -> Unit)? = null, listenFromHere: (() -> Unit)? = null, shadow: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) },
        // A slim handle: the default one pads 22dp above and below.
        dragHandle = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).size(width = 32.dp, height = 4.dp)
            ) {}
        }
    ) {
        ReferenceDictionaryPane(vm, settings, text, selection, words, highlights, dismiss, listen, listenFromHere, shadow)
    }
}

/** How many other contexts this word is already saved in; nothing when there are none. */
@Composable
private fun SavedElsewhereBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val savedElsewhere = stringResource(Res.string.dictionary_saved_elsewhere, count)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = savedElsewhere }
    ) {
        Icon(
            Icons.Default.BookmarkAdded,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Compact controls ported from French Reader; platform WebView dictionaries remain external. */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReferenceDictionaryPane(
    vm: AppViewModel, settings: AppSettings, text: TextDocument, selection: ReaderSelection,
    words: List<VocabEntry>, highlights: List<HighlightEntry>, dismiss: () -> Unit,
    listen: (() -> Unit)? = null, listenFromHere: (() -> Unit)? = null, shadow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lang = requireNotNull(Languages.byCode(text.lang))
    val meaningTarget = settings.meaningLang.takeIf { it != text.lang } ?: if (text.lang == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code
    val originalWord = selection.word
    var currentWord by remember(selection) { mutableStateOf(originalWord) }
    val word = currentWord
    val isOriginal = word == originalWord
    var lemmas by remember(selection) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(originalWord, lang) {
        lemmas = lemmaSuggestions(originalWord, LanguageDataPacks.load(lang.code)?.lemmas, lang)
    }
    val entries by remember(text.lang) { vm.container.vocab.observeAll(text.lang) }.collectAsState(initial = null)
    val lists by remember(text.lang) { vm.container.vocab.observeLists(text.lang) }.collectAsState(emptyList())
    var justSaved by remember(selection, word) { mutableStateOf<VocabEntry?>(null) }
    var deletedIds by remember(selection) { mutableStateOf(emptySet<Long>()) }
    val visibleEntries = (entries ?: words).filterNot { it.id in deletedIds }
    val observedState = dictionarySavedState(visibleEntries, lang, text.id, word, selection.sentence)
    val savedState = if (justSaved != null && justSaved?.id !in deletedIds) observedState.copy(exactEntry = justSaved) else observedState
    var meaningEdited by remember(word, selection.sentence, meaningTarget) { mutableStateOf(false) }
    var alternatives by remember(word, selection.sentence, meaningTarget) { mutableStateOf(emptyList<MeaningOption>()) }
    var meaning by remember(word, selection.sentence, meaningTarget) { mutableStateOf(savedState.exactEntry?.takeIf { it.meaningLang == meaningTarget }?.meaning.orEmpty()) }
    var selectedListId by remember(word, selection.sentence) { mutableStateOf(savedState.exactEntry?.listId) }
    var saved by remember(word, selection.sentence, meaningTarget) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var autoTranslating by remember(word, meaningTarget) { mutableStateOf(false) }
    var listMenuExpanded by remember { mutableStateOf(false) }
    var showNewListDialog by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val provider = lang.dictionaryLinks.firstOrNull { meaningTarget in it.meaningLangs }
        ?: lang.dictionaryLinks.firstOrNull { it.supportsMeaningLanguage(meaningTarget) }
    val repository = vm.container.translations(settings)
    LaunchedEffect(savedState.exactEntry?.id, meaningTarget) {
        val existing = savedState.exactEntry
        if (existing != null && !meaningEdited) {
            meaning = existing.takeIf { it.meaningLang == meaningTarget }?.meaning.orEmpty()
            selectedListId = existing.listId
            saved = existing.meaningLang == meaningTarget
        }
    }
    LaunchedEffect(entries != null, word, selection.sentence, meaningTarget, repository, savedState.exactEntry?.id) {
        if (entries == null || savedState.exactEntry != null || meaningEdited) return@LaunchedEffect
        selectedListId = vm.container.cache.get("dictionary:last-list:${lang.code}")?.toLongOrNull()
        autoTranslating = true
        try {
            val result = if (selection.sentenceActions) {
                repository.translateText(word, text.lang, meaningTarget).getOrNull()?.let { com.ziaee.zabankhan.translate.WordMeaning(it, emptyList(), "") }
            } else repository.lookupWord(word, selection.sentence, text.lang, meaningTarget).getOrNull()
            if (!meaningEdited && savedState.exactEntry == null) {
                result?.primary?.let { meaning = it }
                alternatives = result?.senses.orEmpty().flatMap { sense -> sense.meanings.map { MeaningOption(it, null, sense.pos) } }
                    .filter { it.text.isNotBlank() }.distinctBy { it.text }.take(8)
            }
        } finally { autoTranslating = false }
    }
    LaunchedEffect(lists) {
        if (selectedListId != null && lists.none { it.id == selectedListId }) selectedListId = null
    }
    Column(modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            val focusManager = LocalFocusManager.current
            var draft by remember(word) { mutableStateOf(TextFieldValue(word, TextRange(word.length))) }
            val draftWord = manualDictionaryWord(draft.text)
            val applyDraft = {
                draftWord?.let { currentWord = it }
                focusManager.clearFocus()
            }
            val wordInteraction = remember { MutableInteractionSource() }
            val wordFocused by wordInteraction.collectIsFocusedAsState()
            val shownLemmas = lemmas.filter { it != word }
            val wordField: @Composable (Modifier) -> Unit = { fieldModifier ->
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.merge(contentStyle(draft.text))
                        .copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = readerContentFamily(draft.text)),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { applyDraft() }),
                    interactionSource = wordInteraction,
                    modifier = fieldModifier,
                    decorationBox = { inner ->
                        Column {
                            Box(Modifier.padding(vertical = 6.dp)) { inner() }
                            HorizontalDivider(
                                thickness = if (wordFocused) 2.dp else 1.dp,
                                color = if (wordFocused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                )
            }
            val wordActions: @Composable () -> Unit = {
                if (draftWord != null && draftWord != word) {
                    IconButton(onClick = applyDraft) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(Res.string.dictionary_apply_word),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = { provider?.let { uri.openUri(it.urlFor(word)) } }, enabled = provider != null) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(Res.string.accessibility_open_in_browser))
                }
                IconButton(onClick = dismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.accessibility_close))
                }
            }
            val wordPills: @Composable (Boolean) -> Unit = { compact ->
                shownLemmas.forEach { lemma ->
                    WordPill("→ $lemma", compact = compact) { currentWord = lemma }
                }
                if (!isOriginal) {
                    val resetLabel = stringResource(Res.string.dictionary_reset_word)
                    WordPill("↺ $originalWord", Modifier.semantics { contentDescription = "$resetLabel: $originalWord" }, compact = compact) {
                        currentWord = originalWord
                    }
                }
            }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    wordField(Modifier.weight(1f))
                    wordActions()
                }
                if (shownLemmas.isNotEmpty() || !isOriginal) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) { wordPills(false) }
                }
            val saveWord: () -> Unit = {
                if (!saving) {
                    val snapshotWord = word
                    val snapshotMeaning = meaning.trim()
                    val snapshotList = selectedListId
                    val entry = savedState.exactEntry?.copy(meaning = snapshotMeaning, meaningLang = meaningTarget, listId = snapshotList)
                        ?: VocabEntry(lang = lang.code, textId = text.id, word = snapshotWord, sentence = selection.sentence,
                            meaning = snapshotMeaning, meaningLang = meaningTarget, listId = snapshotList)
                    saving = true
                    vm.action {
                        try {
                            // Recheck after dispatch: the first database emission may have arrived
                            // between opening the sheet and this tap.
                            val existing = dictionarySavedState(vm.container.vocab.getAll(lang.code), lang,
                                text.id, snapshotWord, selection.sentence).exactEntry
                            val toSave = existing?.copy(meaning = snapshotMeaning, meaningLang = meaningTarget, listId = snapshotList) ?: entry
                            val stored = if (toSave.id == 0L) toSave.copy(id = vm.container.vocab.insert(toSave))
                                else { vm.container.vocab.update(toSave); toSave }
                            vm.container.cache.put("dictionary:last-list:${lang.code}", snapshotList?.toString().orEmpty())
                            if (currentWord == snapshotWord) {
                                justSaved = stored
                                deletedIds = deletedIds - stored.id
                                saved = meaning.trim() == snapshotMeaning && selectedListId == snapshotList
                            }
                        } finally { saving = false }
                    }
                }
            }
            val meaningAlternatives: @Composable () -> Unit = {
                if (alternatives.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    MeaningOptionPills(alternatives.take(8), meaning, meaningTarget) { option ->
                        meaning = option.text
                        meaningEdited = true
                        saved = false
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = meaning,
                    onValueChange = { meaning = it; meaningEdited = true; saved = false },
                    placeholder = { Text(stringResource(if (meaningTarget == Languages.PERSIAN.code) Res.string.dictionary_meaning_persian else Res.string.dictionary_meaning_english)) },
                    trailingIcon = {
                        if (autoTranslating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.merge(contentStyle(meaning)).copy(fontFamily = readerContentFamily(meaning))
                )
                Spacer(Modifier.width(10.dp))
                FilledIconButton(
                    onClick = saveWord,
                    enabled = !saving,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        if (saved) Icons.Default.Check else Icons.Default.Save,
                        contentDescription = stringResource(
                            if (saved) Res.string.accessibility_vocabulary_saved
                            else Res.string.accessibility_save_vocabulary
                        )
                    )
                }
            }

            meaningAlternatives()

            // One quiet line of details: the list it's filed in, where else it was saved, delete.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box {
                    TextButton(
                        onClick = { listMenuExpanded = true },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            lists.find { it.id == selectedListId }?.name ?: stringResource(Res.string.vocab_list_uncategorized),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = listMenuExpanded, onDismissRequest = { listMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.vocab_list_uncategorized)) },
                            onClick = { selectedListId = null; saved = false; listMenuExpanded = false }
                        )
                        lists.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l.name) },
                                onClick = { selectedListId = l.id; saved = false; listMenuExpanded = false }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("+ " + stringResource(Res.string.vocab_list_new)) },
                            onClick = { listMenuExpanded = false; showNewListDialog = true }
                        )
                    }
                }
                SavedElsewhereBadge(savedState.otherContextCount, Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                if (saved && savedState.exactEntry != null) {
                    IconButton(
                        onClick = {
                            savedState.exactEntry?.let { entry ->
                                vm.action {
                                    vm.container.vocab.delete(entry)
                                    deletedIds = deletedIds + entry.id
                                    justSaved = null
                                    saved = false
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = stringResource(Res.string.accessibility_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

    }
    if (showNewListDialog) {
        var newListName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewListDialog = false },
            title = { Text(stringResource(Res.string.vocab_list_new)) },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    label = { Text(stringResource(Res.string.vocab_list_name_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = newListName.isNotBlank(), onClick = {
                    vm.action { selectedListId = vm.container.vocab.insertList(VocabList(lang = lang.code, name = newListName.trim())); saved = false }
                    showNewListDialog = false
                }) { Text(stringResource(Res.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewListDialog = false }) { Text(stringResource(Res.string.action_cancel)) }
            }
        )
    }
}
@Composable
private fun WordPill(text: String, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    ) {
        Text(
            text,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 6.dp)
        )
    }
}


@Composable
fun DictionarySheet(vm: AppViewModel, settings: AppSettings, text: TextDocument, selection: ReaderSelection,
    words: List<VocabEntry>, highlights: List<HighlightEntry>, dismiss: () -> Unit,
    listen: (() -> Unit)? = null, listenFromHere: (() -> Unit)? = null, shadow: (() -> Unit)? = null) {
    ReaderReferenceTheme(settings.uiLang, settings.themeMode) {
        ReferenceDictionarySheet(vm, settings, text, selection, words, highlights, dismiss, listen, listenFromHere, shadow)
    }
}

@Composable
fun DictionaryPane(vm: AppViewModel, settings: AppSettings, text: TextDocument, selection: ReaderSelection,
    words: List<VocabEntry>, highlights: List<HighlightEntry>, dismiss: () -> Unit,
    listen: (() -> Unit)? = null, listenFromHere: (() -> Unit)? = null, shadow: (() -> Unit)? = null,
    modifier: Modifier = Modifier) {
    ReaderReferenceTheme(settings.uiLang, settings.themeMode) {
        ReferenceDictionaryPane(vm, settings, text, selection, words, highlights, dismiss, listen, listenFromHere, shadow, modifier)
    }
}
