package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun DictionarySheet(vm: AppViewModel, settings: AppSettings, text: TextDocument, selection: ReaderSelection,
    words: List<VocabEntry>, highlights: List<HighlightEntry>, dismiss: () -> Unit) {
    val lang = Languages.byCode(text.lang)!!
    val target = settings.meaningLang.takeIf { it != text.lang } ?: if (text.lang == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code
    val saved = words.firstOrNull { normalizeForMatch(it.word, lang) == normalizeForMatch(selection.word, lang) }
    var lookup by remember(selection, target) { mutableStateOf<WordMeaning?>(null) }
    var error by remember(selection, target) { mutableStateOf<Throwable?>(null) }
    var loading by remember(selection, target) { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var translated by remember(selection, target) { mutableStateOf<String?>(null) }
    var sentenceLoading by remember { mutableStateOf(false) }
    var sentenceError by remember(selection, target) { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val repository = vm.container.translations(settings)
    LaunchedEffect(selection, target, attempt, repository) {
        loading = true; error = null
        if (selection.sentenceActions) {
            val result = repository.translateText(selection.word, text.lang, target)
            lookup = result.getOrNull()?.let { WordMeaning(it, emptyList(), "") }
            error = result.exceptionOrNull()
        } else {
            val result = repository.lookupWord(selection.word, selection.sentence, text.lang, target)
            lookup = result.getOrNull(); error = result.exceptionOrNull()
        }
        loading = false
    }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(selection.word, style = MaterialTheme.typography.headlineSmall.merge(contentStyle(selection.word)))
            if (loading) CircularProgressIndicator()
            error?.let { TranslationError(it); TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) } }
            if (!loading && error == null && lookup?.primary.isNullOrBlank() && lookup?.senses.isNullOrEmpty()) Text(stringResource(Res.string.no_meaning))
            lookup?.takeIf { it.primary.isNotBlank() || it.senses.isNotEmpty() }?.let { value ->
                Text(value.primary, style = MaterialTheme.typography.titleLarge.merge(contentStyle(value.primary)))
                value.senses.forEach { sense ->
                    // pos and meanings on separate lines: one mixed-direction line renders "x :noun".
                    if (sense.pos.isNotBlank()) Text(sense.pos, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(sense.meanings.joinToString("، "), style = contentStyle(sense.meanings.joinToString()))
                }
            }
            if (!selection.sentenceActions) {
                Text(selection.sentence, style = contentStyle(selection.sentence))
                TextButton(enabled = !sentenceLoading, onClick = { scope.launch {
                    sentenceLoading = true; sentenceError = null
                    try { val result = repository.translateText(selection.sentence, text.lang, target); translated = result.getOrNull(); sentenceError = result.exceptionOrNull() }
                    finally { sentenceLoading = false }
                } }) { Text(stringResource(Res.string.translate_sentence)) }
                if (sentenceLoading) CircularProgressIndicator()
                translated?.let { Text(it, style = contentStyle(it)) }
                sentenceError?.let { TranslationError(it) }
            }
            saved?.let { Text(stringResource(Res.string.box_label, it.leitnerBox), color = boxColor(it.leitnerBox)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = lookup != null && !saving, onClick = { saving = true; vm.action {
                    try {
                    val entry = saved?.copy(meaning = lookup!!.primary, meaningLang = target, sentence = selection.sentence, textId = text.id)
                        ?: VocabEntry(lang = text.lang, word = selection.word, sentence = selection.sentence, meaning = lookup!!.primary, meaningLang = target, textId = text.id)
                    if (saved == null) vm.container.vocab.insert(entry) else vm.container.vocab.update(entry)
                    dismiss()
                    } finally { saving = false }
                } }) { Text(stringResource(if (selection.sentenceActions) Res.string.save_phrase else Res.string.save)) }
                if (saved != null) OutlinedButton(enabled = !saving, onClick = { saving = true; vm.action { try { vm.container.vocab.delete(saved); dismiss() } finally { saving = false } } }) { Text(stringResource(Res.string.remove)) }
            }
            if (selection.sentenceActions && text.pdfPath == null) {
                Text(stringResource(Res.string.highlight))
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
            } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lang.dictionaryLinks.filter { it.supportsMeaningLanguage(target) }.forEach { link ->
                    OutlinedButton(onClick = { uri.openUri(link.urlFor(selection.word)) }) { Text(link.name) }
                }
            }
            Spacer(Modifier.height(16.dp))
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
