package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
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
import com.ziaee.zabankhan.speech.*
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
    val pack by produceState<com.ziaee.zabankhan.datapack.LanguageDataPack?>(null, lang) {
        value = com.ziaee.zabankhan.datapack.LanguageDataPacks.load(lang.code)
    }
    val idioms = remember(pack, lang) { pack?.let { com.ziaee.zabankhan.watch.IdiomMatcher(it.idioms, lang, it.lemmas) } }
    val parts = remember(text.body) { paragraphs(text.body) }
    val words by remember(text.lang) { vm.container.vocab.observeAll(text.lang) }.collectAsState(emptyList())
    val highlights by remember(id) { vm.container.highlights.observeForText(id) }.collectAsState(emptyList())
    val highlighter = remember(words, lang) { VocabHighlighter(words.map { SavedVocab(it.word, it.srsState().status()) }, lang) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = paragraphIndexAt(parts, text.lastOffset))
    val tts = remember { createTts() }
    val playback = remember(id, parts, lang) { ReaderPlayback(tts, parts.map { SpokenParagraph(it.text, it.offset) }, lang) }
    val speaking by tts.isSpeaking.collectAsState()
    val spokenSentence by playback.sentence.collectAsState()
    com.ziaee.zabankhan.statistics.TrackActivity(vm.container, id, lang.code, com.ziaee.zabankhan.statistics.ActivityKind.READING, !speaking)
    var rate by remember { mutableFloatStateOf(settings.speechRate) }
    var shadowRange by remember(id) { mutableStateOf<IntRange?>(null) }
    LaunchedEffect(playback) { tts.speakingRange.collect { playback.rangeChanged(it) } }
    // Compose the destination paragraph before its TextLayoutResult can scroll to the sentence.
    LaunchedEffect(spokenSentence) {
        val range = spokenSentence ?: return@LaunchedEffect
        val index = paragraphIndexAt(parts, range.first)
        if (list.layoutInfo.visibleItemsInfo.none { it.index == index }) list.scrollToItem(index)
    }
    var selection by remember(id) { mutableStateOf<ReaderSelection?>(null) }
    LaunchedEffect(id) {
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
            parts.getOrNull(index)?.let { vm.container.texts.savePosition(id, it.offset) }
        }
    }
    DisposableEffect(id) {
        onDispose { playback.stop(); parts.getOrNull(list.firstVisibleItemIndex)?.let { part -> vm.action { vm.container.texts.savePosition(id, part.offset) } } }
    }
    val smallerLabel = stringResource(Res.string.font_smaller)
    val largerLabel = stringResource(Res.string.font_larger)
    Column(Modifier.fillMaxSize()) {
        // Icon bar: one line on phones (text buttons wrapped onto two lines).
        var rateMenu by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.action { vm.container.settings.setReaderFontSize(settings.readerFontSize - 1) } }, enabled = settings.readerFontSize > 12) { Icon(Icons.Filled.TextDecrease, smallerLabel) }
            IconButton(onClick = { vm.action { vm.container.settings.setReaderFontSize(settings.readerFontSize + 1) } }, enabled = settings.readerFontSize < 40) { Icon(Icons.Filled.TextIncrease, largerLabel) }
            Box {
                TextButton(onClick = { rateMenu = true }) { Text("${rate}×") }
                DropdownMenu(rateMenu, onDismissRequest = { rateMenu = false }) {
                    listOf(.75f, 1f, 1.25f).forEach { r ->
                        DropdownMenuItem(text = { Text(stringResource(Res.string.speech_rate) + ": ${r}×") }, onClick = {
                            rateMenu = false; rate = r
                            if (speaking) playback.start(list.firstVisibleItemIndex, rate, settings.ttsVoices[lang.code])
                        })
                    }
                }
            }
            FilledTonalIconButton(onClick = { if (speaking) playback.stop() else playback.start(list.firstVisibleItemIndex, rate, settings.ttsVoices[lang.code]) }) {
                Icon(if (speaking) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (speaking) Res.string.watch_pause else Res.string.watch_play))
            }
        }
        Text(text.title, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge.merge(contentStyle(text.title)))
        Text(stringResource(Res.string.reader_hint), modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
        LazyColumn(state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(parts, key = { _, part -> part.offset }) { _, part ->
                ReaderParagraph(part, lang, highlighter, words, highlights, settings.readerFontSize, spokenSentence, pack?.lemmas, idioms, { y ->
                    val index = parts.indexOf(part)
                    if (index >= 0) list.animateScrollToItem(index, y)
                }) { token, sentence, range, long ->
                    playback.stop()
                    selection = ReaderSelection(if (long) sentence else token, sentence, range, long)
                }
            }
        }
    }
    selection?.let { selected -> DictionarySheet(vm, settings, text, selected, words, highlights, { selection = null },
        listen = { playback.sentenceOnly(selected.sentence, selected.range.first, rate, settings.ttsVoices[lang.code]); selection = null },
        listenFromHere = { playback.start(paragraphIndexAt(parts, selected.range.first), rate, settings.ttsVoices[lang.code]); selection = null },
        shadow = { playback.stop(); shadowRange = selected.range; selection = null }) }
    shadowRange?.let { range ->
        val sentences = splitSentences(text.body, lang)
        val start = sentences.indexOfFirst { range.first in it }.coerceAtLeast(0)
        ShadowingPanel(vm.container, settings, lang, id, sentences.map { text.body.substring(it) }, start, { shadowRange = null })
    }
}

@Composable private fun ReaderParagraph(part: Paragraph, lang: LanguageSpec, highlighter: VocabHighlighter,
    words: List<VocabEntry>, highlights: List<HighlightEntry>, fontSize: Int, spokenSentence: IntRange?, lexicon: LemmaLexicon?, idioms: com.ziaee.zabankhan.watch.IdiomMatcher?, scrollTo: suspend (Int) -> Unit, select: (String, String, IntRange, Boolean) -> Unit) {
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
        // Colour inflected forms by their saved lemma's Leitner box as well.
        if (lexicon != null) {
            val lemmaBoxes = words.flatMap { entry -> lexicon.lemmas(normalizeForMatch(entry.word, lang)).map { it to entry.leitnerBox.coerceIn(1, 5) } }.toMap()
            tokenizeSurface(part.text, lang).forEach { token ->
                val box = lexicon.lemmas(normalizeForMatch(token.text, lang)).mapNotNull { lemmaBoxes[it] }.minOrNull()
                if (box != null) addStyle(SpanStyle(color = colors.getValue(box)), token.start, token.end)
            }
        }
        matches.forEach { match ->
            val box = savedBoxes[normalizeForMatch(part.text.substring(match.range), lang)] ?: 1
            addStyle(SpanStyle(color = colors.getValue(box)), match.range.first, match.range.last + 1)
        }
    }
    val localSpoken = spokenSentence?.takeIf { it.first < part.offset + part.text.length && it.last >= part.offset }
    LaunchedEffect(localSpoken, layout) {
        val start = localSpoken?.let { (it.first - part.offset).coerceAtLeast(0) }
        if (start != null) layout?.let { scrollTo(it.getLineTop(it.getLineForOffset(start)).toInt()) }
    }
    fun onPosition(position: androidx.compose.ui.geometry.Offset, long: Boolean) {
        val offset = layout?.getOffsetForPosition(position) ?: return
        val word = wordAt(part.text, offset, lang) ?: return
        val range = sentenceAround(part.text, offset, lang)
        if (range.isEmpty()) return
        val idiom = if (long) null else idioms?.match(part.text)?.firstOrNull { offset in it.startChar until it.endChar }
        select(idiom?.expression ?: word.text, part.text.substring(range), (range.first + part.offset)..(range.last + part.offset), long)
    }
    // Spoken sentence drawn as one path behind the text: span backgrounds leave seams between styled words.
    val spokenColor = scheme.secondaryContainer
    val spokenPath = remember(localSpoken, layout) {
        val l = layout; val r = localSpoken
        if (l == null || r == null) null else {
            val start = (r.first - part.offset).coerceIn(0, part.text.length)
            val end = (r.last + 1 - part.offset).coerceIn(0, part.text.length)
            if (start < end) l.getPathForRange(start, end) else null
        }
    }
    Text(annotated, modifier = Modifier.fillMaxWidth().drawBehind { spokenPath?.let { drawPath(it, spokenColor) } }.pointerInput(part, lang, idioms) {
        detectTapGestures(onTap = { onPosition(it, false) }, onLongPress = { onPosition(it, true) })
    }, style = MaterialTheme.typography.bodyLarge.merge(contentStyle(part.text)).copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.7).sp), onTextLayout = { layout = it })
}
