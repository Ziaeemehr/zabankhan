package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.*
import com.ziaee.zabankhan.srs.*
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

data class ReaderSelection(val word: String, val sentence: String, val range: IntRange, val sentenceActions: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReaderScreen(vm: AppViewModel, settings: AppSettings, id: Long, back: () -> Unit) {
    val document by remember(id) { vm.container.texts.observeById(id) }.collectAsState(null)
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { loaded = vm.container.texts.getById(id) == null }
    val text = document
    if (text == null) { Page { TextButton(onClick = back) { Text(stringResource(Res.string.back)) }; if (loaded) Text(stringResource(Res.string.not_found)) else CircularProgressIndicator() }; return }
    if (text.pdfPath != null) { com.ziaee.zabankhan.pdf.PdfReaderScreen(vm, settings, text, back); return }
    val lang = requireNotNull(Languages.byCode(text.lang))
    val parts = remember(text.body) { paragraphs(text.body) }
    val words by remember(text.lang) { vm.container.vocab.observeAll(text.lang) }.collectAsState(emptyList())
    val highlights by remember(id) { vm.container.highlights.observeForText(id) }.collectAsState(emptyList())
    val highlighter = remember(words, lang) { VocabHighlighter(words.map { SavedVocab(it.word, it.srsState().status()) }, lang) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = paragraphIndexAt(parts, text.lastOffset))
    var selection by remember(id) { mutableStateOf<ReaderSelection?>(null) }
    LaunchedEffect(id) {
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
            parts.getOrNull(index)?.let { vm.container.texts.savePosition(id, it.offset) }
        }
    }
    DisposableEffect(id) {
        onDispose { parts.getOrNull(list.firstVisibleItemIndex)?.let { part -> vm.action { vm.container.texts.savePosition(id, part.offset) } } }
    }
    val smallerLabel = stringResource(Res.string.font_smaller)
    val largerLabel = stringResource(Res.string.font_larger)
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = back) { Text(stringResource(Res.string.back)) }
            TextButton(onClick = { vm.action { vm.container.settings.setReaderFontSize(settings.readerFontSize - 1) } }, modifier = Modifier.semantics { contentDescription = smallerLabel }, enabled = settings.readerFontSize > 12) { Text("A−", modifier = Modifier, style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = { vm.action { vm.container.settings.setReaderFontSize(settings.readerFontSize + 1) } }, modifier = Modifier.semantics { contentDescription = largerLabel }, enabled = settings.readerFontSize < 40) { Text("A+", style = MaterialTheme.typography.titleMedium) }
        }
        Text(text.title, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge.merge(contentStyle(text.title)))
        Text(stringResource(Res.string.reader_hint), modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
        LazyColumn(state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(parts, key = { _, part -> part.offset }) { _, part ->
                ReaderParagraph(part, lang, highlighter, words, highlights, settings.readerFontSize) { token, sentence, range, long ->
                    selection = ReaderSelection(if (long) sentence else token, sentence, range, long)
                }
            }
        }
    }
    selection?.let { selected -> DictionarySheet(vm, settings, text, selected, words, highlights, { selection = null }) }
}

@Composable private fun ReaderParagraph(part: Paragraph, lang: LanguageSpec, highlighter: VocabHighlighter,
    words: List<VocabEntry>, highlights: List<HighlightEntry>, fontSize: Int, select: (String, String, IntRange, Boolean) -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val matches = remember(part, highlighter) { highlighter.findMatches(part.text) }
    val colors = (1..5).associateWith { boxColor(it) }
    val savedBoxes = words.associate { normalizeForMatch(it.word, lang) to it.leitnerBox.coerceIn(1, 5) }
    val scheme = MaterialTheme.colorScheme
    val annotated = buildAnnotatedString {
        append(part.text)
        highlights.forEach { h ->
            val start = (h.startOffset - part.offset).coerceAtLeast(0)
            val end = (h.endOffset - part.offset).coerceAtMost(part.text.length)
            if (start < end) addStyle(SpanStyle(background = when (h.colorKey) {
                "secondary" -> scheme.secondaryContainer
                "tertiary" -> scheme.tertiaryContainer
                else -> scheme.primaryContainer
            }), start, end)
        }
        matches.forEach { match ->
            val box = savedBoxes[normalizeForMatch(part.text.substring(match.range), lang)] ?: 1
            addStyle(SpanStyle(color = colors.getValue(box)), match.range.first, match.range.last + 1)
        }
    }
    fun onPosition(position: androidx.compose.ui.geometry.Offset, long: Boolean) {
        val offset = layout?.getOffsetForPosition(position) ?: return
        val word = wordAt(part.text, offset, lang) ?: return
        val range = sentenceAround(part.text, offset, lang)
        if (range.isEmpty()) return
        select(word.text, part.text.substring(range), (range.first + part.offset)..(range.last + part.offset), long)
    }
    Text(annotated, modifier = Modifier.fillMaxWidth().pointerInput(part, lang) {
        detectTapGestures(onTap = { onPosition(it, false) }, onLongPress = { onPosition(it, true) })
    }, style = MaterialTheme.typography.bodyLarge.merge(contentStyle(part.text)).copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.7).sp), onTextLayout = { layout = it })
}
