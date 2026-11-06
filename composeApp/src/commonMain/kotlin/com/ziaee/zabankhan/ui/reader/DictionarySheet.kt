package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.Alignment
import com.ziaee.zabankhan.ui.theme.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.translate.*
import com.ziaee.zabankhan.translate.llm.LlmError
import com.ziaee.zabankhan.ui.*
import com.ziaee.zabankhan.speech.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun DictionarySheet(vm: AppViewModel, settings: AppSettings, text: TextDocument, selection: ReaderSelection,
    words: List<VocabEntry>, highlights: List<HighlightEntry>, dismiss: () -> Unit, listen: (() -> Unit)? = null, listenFromHere: (() -> Unit)? = null, shadow: (() -> Unit)? = null) {
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)), containerColor = MaterialTheme.colorScheme.surface) {
        DictionaryPane(vm, settings, text, selection, words, highlights, dismiss, listen, listenFromHere, shadow)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DictionaryPane(vm: AppViewModel, settings: AppSettings, text: TextDocument, selection: ReaderSelection,
    words: List<VocabEntry>, highlights: List<HighlightEntry>, dismiss: () -> Unit, listen: (() -> Unit)? = null, listenFromHere: (() -> Unit)? = null, shadow: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val lang = Languages.byCode(text.lang)!!
    val target = settings.meaningLang.takeIf { it != text.lang } ?: if (text.lang == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code
    var currentWord by remember(selection) { mutableStateOf(selection.word) }
    var draft by remember(selection) { mutableStateOf(selection.word) }
    val existing = words.firstOrNull { normalizeForMatch(it.word, lang) == normalizeForMatch(currentWord, lang) }
    var justSaved by remember(selection, currentWord) { mutableStateOf<VocabEntry?>(null) }
    val saved = justSaved ?: existing
    var meaning by remember(selection, currentWord, target) { mutableStateOf(existing?.takeIf { it.meaningLang == target }?.meaning.orEmpty()) }
    var meaningEdited by remember(selection, currentWord, target) { mutableStateOf(false) }
    var savedMeaning by remember(selection, currentWord, target) { mutableStateOf(existing?.takeIf { it.meaningLang == target }?.meaning) }
    val isSaved = saved != null && saved.meaningLang == target && savedMeaning == meaning
    var attempt by remember(selection) { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val applyDraft: () -> Unit = {
        val word = draft.trim()
        if (word.isNotEmpty()) { currentWord = word; draft = word; attempt++; focus.clearFocus() }
    }
    var lookup by remember(selection, currentWord, target) { mutableStateOf<WordMeaning?>(null) }
    var error by remember(selection, currentWord, target) { mutableStateOf<Throwable?>(null) }
    var loading by remember(selection, currentWord, target) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var translated by remember(selection, currentWord, target) { mutableStateOf<String?>(null) }
    var sentenceLoading by remember { mutableStateOf(false) }
    var sentenceError by remember(selection, currentWord, target) { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val repository = vm.container.translations(settings)
    LaunchedEffect(selection, currentWord, target, attempt, repository) {
        loading = true; error = null; lookup = null
        if (selection.sentenceActions) {
            val result = repository.translateText(currentWord, text.lang, target)
            lookup = result.getOrNull()?.let { WordMeaning(it, emptyList(), "") }
            error = result.exceptionOrNull()
        } else {
            val result = repository.lookupWord(currentWord, selection.sentence, text.lang, target)
            lookup = result.getOrNull(); error = result.exceptionOrNull()
        }
        if (!meaningEdited && (saved == null || saved.meaningLang != target)) meaning = lookup?.primary.orEmpty()
        loading = false
    }
        Column(modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            val lookupLabel = stringResource(Res.string.dictionary_lookup)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(draft, onValueChange = { draft = it }, enabled = !saving,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp).semantics { contentDescription = lookupLabel },
                    singleLine = true, textStyle = MaterialTheme.typography.titleLarge.merge(contentStyle(draft)).copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { applyDraft() }),
                    decorationBox = { inner -> Column { inner(); HorizontalDivider(Modifier.padding(top = 6.dp)) } })
                if (draft.trim().isNotEmpty() && draft.trim() != currentWord) IconButton(onClick = applyDraft) {
                    Icon(Icons.Default.Check, stringResource(Res.string.dictionary_lookup))
                }
                SpeakerButton(currentWord, text.lang, settings)
                EditorialPrimaryButton(text = stringResource(if (isSaved) Res.string.ed_saved else Res.string.save),
                    enabled = meaning.isNotBlank() && draft.trim() == currentWord && !isSaved, loading = saving, onClick = {
                        val wordToSave = currentWord
                        val meaningToSave = meaning.trim()
                        val entry = saved?.copy(meaning = meaningToSave, meaningLang = target, sentence = selection.sentence, textId = text.id)
                            ?: VocabEntry(lang = text.lang, word = wordToSave, sentence = selection.sentence, meaning = meaningToSave, meaningLang = target, textId = text.id)
                        saving = true
                        vm.action {
                            try {
                                val stored = if (saved == null) entry.copy(id = vm.container.vocab.insert(entry))
                                    else { vm.container.vocab.update(entry); entry }
                                if (currentWord == wordToSave) { justSaved = stored; savedMeaning = meaningToSave; meaning = meaningToSave }
                            } finally { saving = false }
                        }
                    })
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (loading) CircularProgressIndicator()
                error?.let { TranslationError(it); TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) } }
                if (!loading && error == null && lookup?.primary.isNullOrBlank() && lookup?.senses.isNullOrEmpty()) Text(stringResource(Res.string.no_meaning))
                OutlinedTextField(meaning, onValueChange = { meaning = it; meaningEdited = true },
                    modifier = Modifier.fillMaxWidth(), enabled = !saving, label = { Text(stringResource(Res.string.meaning)) },
                    textStyle = MaterialTheme.typography.bodyLarge.merge(contentStyle(meaning)), minLines = 2,
                    shape = MaterialTheme.shapes.medium)
                val alternatives = lookup?.senses.orEmpty().flatMap { sense -> sense.meanings.map { sense.pos to it } }
                    .filter { it.second.isNotBlank() }.distinctBy { it.second }
                alternatives.forEach { (pos, alternative) ->
                    Surface(shape = MaterialTheme.shapes.medium,
                        color = if (meaning == alternative) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().selectable(selected = meaning == alternative, enabled = !saving, role = Role.RadioButton, onClick = { meaning = alternative; meaningEdited = true })) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = meaning == alternative, onClick = null)
                            Column(Modifier.weight(1f)) {
                                if (pos.isNotBlank()) Text(partOfSpeechResource(pos)?.let { stringResource(it) } ?: pos, style = MaterialTheme.typography.labelSmall)
                                Text(alternative, style = contentStyle(alternative))
                            }
                        }
                    }
                }
                if (!selection.sentenceActions) {
                    EditorialCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(selection.sentence, modifier = Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyLarge.merge(contentStyle(selection.sentence)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!selection.sentenceActions) {
                    TextButton(enabled = !sentenceLoading, onClick = { scope.launch {
                        sentenceLoading = true; sentenceError = null
                        try { val result = repository.translateText(selection.sentence, text.lang, target); translated = result.getOrNull(); sentenceError = result.exceptionOrNull() }
                        finally { sentenceLoading = false }
                    } }) { Text(stringResource(Res.string.translate_sentence)) }
                    }
                    TextButton(onClick = { if (listen != null) listen() else createTts().pronounce(selection.sentence, text.lang, settings) }) { Text(stringResource(Res.string.speech_sentence)) }
                    if (listenFromHere != null) TextButton(onClick = listenFromHere) { Text(stringResource(Res.string.speech_from_here)) }
                    if (shadow != null) TextButton(onClick = shadow) { Text(stringResource(Res.string.speech_shadow)) }
                }
                if (!selection.sentenceActions) {
                    if (sentenceLoading) CircularProgressIndicator()
                    translated?.let { Text(it, modifier = Modifier.fillMaxWidth(), style = contentStyle(it)) }
                    sentenceError?.let { TranslationError(it) }
                }
                saved?.takeIf { isSaved }?.let {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetadataBadge(stringResource(Res.string.ed_saved))
                        MetadataBadge(stringResource(Res.string.box_label, it.leitnerBox))
                    }
                }
                if (selection.sentenceActions && text.pdfPath == null) {
                    Text(stringResource(Res.string.reader_highlight_selection))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("primary" to Res.string.highlight_primary, "secondary" to Res.string.highlight_secondary, "tertiary" to Res.string.highlight_tertiary).forEach { (key, label) ->
                            OutlinedButton(onClick = { vm.action {
                                highlights.filter { it.startOffset == selection.range.first && it.endOffset == selection.range.last + 1 }.forEach { vm.container.highlights.delete(it.id) }
                                vm.container.highlights.insert(HighlightEntry(textId = text.id, startOffset = selection.range.first, endOffset = selection.range.last + 1, colorKey = key)); dismiss()
                            } }) { Text(stringResource(label)) }
                        }
                        if (highlights.any { it.startOffset < selection.range.last + 1 && it.endOffset > selection.range.first }) TextButton(onClick = { vm.action {
                            highlights.filter { it.startOffset < selection.range.last + 1 && it.endOffset > selection.range.first }.forEach { vm.container.highlights.delete(it.id) }; dismiss()
                        } }) { Text(stringResource(Res.string.remove_highlight)) }
                    }
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    EditorialSectionHeader(stringResource(Res.string.ed_dictionary_links))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lang.dictionaryLinks.filter { it.supportsMeaningLanguage(target) }.forEach { link ->
                            OutlinedButton(onClick = { uri.openUri(link.urlFor(currentWord)) }) { Text(link.name) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (saved != null) Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = !saving, onClick = { saving = true; vm.action { try { vm.container.vocab.delete(saved); dismiss() } finally { saving = false } } }) { Text(stringResource(Res.string.remove)) }
            }
        }
}
@Composable private fun TranslationError(error: Throwable) {
    val reason = if (error is LlmError) translationErrorReason(error.kind) else TranslationErrorReason.UNREACHABLE
    val resource = when (reason) {
        TranslationErrorReason.MISSING_KEY -> Res.string.error_missing_key
        TranslationErrorReason.KEY -> Res.string.error_key
        TranslationErrorReason.PAYMENT -> Res.string.error_payment
        TranslationErrorReason.FORBIDDEN -> Res.string.error_forbidden
        TranslationErrorReason.MODEL -> Res.string.error_model
        TranslationErrorReason.RATE -> Res.string.error_rate
        TranslationErrorReason.UNREACHABLE -> Res.string.error_network
        TranslationErrorReason.RESPONSE -> Res.string.error_response
    }
    Text(stringResource(resource), color = MaterialTheme.colorScheme.error)
}
