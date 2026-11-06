package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.foundation.background
import com.ziaee.zabankhan.ui.theme.*
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

data class ReaderSelection(val word: String, val sentence: String, val range: IntRange, val sentenceActions: Boolean, val sentenceRange: IntRange = range)

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReaderScreen(vm: AppViewModel, settings: AppSettings, id: Long, back: () -> Unit) {
    val document by remember(id) { vm.container.texts.observeById(id) }.collectAsState(null)
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { loaded = vm.container.texts.getById(id) == null }
    val text = document
    if (text == null) { Page { EditorialTopAppBar(stringResource(Res.string.nav_library), back); if (loaded) Text(stringResource(Res.string.not_found)) else CircularProgressIndicator() }; return }
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
    val activity = com.ziaee.zabankhan.statistics.TrackActivity(vm.container, id, lang.code, com.ziaee.zabankhan.statistics.ActivityKind.READING, !speaking)
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
    LaunchedEffect(activity, list) { activity.interact(); snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { activity.interact() } }
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
    var finding by remember(id) { mutableStateOf(false) }
    var query by remember(id) { mutableStateOf("") }
    val findMatches = remember(text.body, query) { findTextMatches(text.body, query) }
    var findIndex by remember(query) { mutableIntStateOf(0) }
    var findRevision by remember(id) { mutableIntStateOf(0) }
    val activeFind = if (finding) findMatches.getOrNull(findIndex) else null
    LaunchedEffect(activeFind, findRevision) {
        activeFind?.let { list.scrollToItem(paragraphIndexAt(parts, it.first)) }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        var fontMenu by remember { mutableStateOf(false) }
        ReaderTopBar(text.title, back, actions = {
            EditorialIconButton({ finding = !finding; if (finding) playback.pause() }, Icons.Default.Search, stringResource(Res.string.reader_find))
            Box {
                EditorialIconButton({ fontMenu = true }, Icons.Default.TextFields, stringResource(Res.string.font_size))
                DropdownMenu(fontMenu, onDismissRequest = { fontMenu = false }) {
                    DropdownMenuItem(text = { Text(smallerLabel) }, enabled = settings.readerFontSize > 12,
                        leadingIcon = { Icon(Icons.Filled.TextDecrease, smallerLabel) },
                        onClick = { vm.action { vm.container.settings.setReaderFontSize(settings.readerFontSize - 1) } })
                    DropdownMenuItem(text = { Text(largerLabel) }, enabled = settings.readerFontSize < 40,
                        leadingIcon = { Icon(Icons.Filled.TextIncrease, largerLabel) },
                        onClick = { vm.action { vm.container.settings.setReaderFontSize(settings.readerFontSize + 1) } })
                }
            }

        })
        if (finding) ReaderFindBar(query, { query = it }, findIndex, findMatches.size,
            previous = { if (findMatches.isNotEmpty()) { findIndex = (findIndex - 1 + findMatches.size) % findMatches.size; findRevision++ } },
            next = { if (findMatches.isNotEmpty()) { findIndex = (findIndex + 1) % findMatches.size; findRevision++ } },
            close = { finding = false; query = "" })
        val progress by remember(list, parts) { derivedStateOf {
            if (parts.isEmpty()) 0f else if (list.layoutInfo.totalItemsCount > 0 && !list.canScrollForward) 1f
            else parts.getOrNull(list.firstVisibleItemIndex)?.offset?.toFloat()?.div(text.body.length.coerceAtLeast(1)) ?: 0f
        } }
        val progressLabel = stringResource(Res.string.ed_reading_progress, (progress * 100).toInt())
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp).semantics { contentDescription = progressLabel },
            color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        Column(Modifier.widthIn(max = ZabankhanDesign.sizes.readerMeasure).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.reader_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f).widthIn(max = ZabankhanDesign.sizes.readerMeasure).fillMaxWidth().align(Alignment.CenterHorizontally), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            itemsIndexed(parts, key = { _, part -> part.offset }) { _, part ->
                ReaderParagraph(part, lang, highlighter, words, highlights, settings.readerFontSize, if (finding) null else spokenSentence, if (finding) findMatches else emptyList(), activeFind, findRevision, pack?.lemmas, idioms, { y ->
                    val index = parts.indexOf(part)
                    if (index >= 0) list.animateScrollToItem(index, y)
                }) { selected ->
                    playback.stop(); activity.interact(); selection = selected
                }
            }
        }
        ReaderPlayer(speaking, rate,
            previous = { playback.previousSentence(list.firstVisibleItemIndex, rate, settings.ttsVoices[lang.code], settings.speechRepeat) },
            next = { playback.nextSentence(list.firstVisibleItemIndex, rate, settings.ttsVoices[lang.code], settings.speechRepeat) },
            toggle = { if (speaking) playback.pause() else playback.resume(list.firstVisibleItemIndex, rate, settings.ttsVoices[lang.code], settings.speechRepeat) },
            changeRate = { value ->
                rate = value
                if (speaking) playback.startAt(spokenSentence?.first ?: parts.getOrNull(list.firstVisibleItemIndex)?.offset ?: 0, rate, settings.ttsVoices[lang.code], settings.speechRepeat)
            },
            shadowing = shadowRange != null,
            shadow = {
                val start = spokenSentence ?: parts.getOrNull(list.firstVisibleItemIndex)?.let { sentenceAround(text.body, it.offset, lang) }
                playback.stop(); shadowRange = if (shadowRange != null) null else start
            })
    }
    selection?.let { selected -> DictionarySheet(vm, settings, text, selected, words, highlights, { selection = null },
        listen = { playback.sentenceOnly(selected.sentence, selected.sentenceRange.first, rate, settings.ttsVoices[lang.code]); selection = null },
        listenFromHere = { playback.startAt(selected.sentenceRange.first, rate, settings.ttsVoices[lang.code], settings.speechRepeat); selection = null },
        shadow = { playback.stop(); shadowRange = selected.sentenceRange; selection = null }) }
    shadowRange?.let { range ->
        val sentences = splitSentences(text.body, lang)
        val start = sentences.indexOfFirst { range.first in it }.coerceAtLeast(0)
        ShadowingPanel(vm.container, settings, lang, id, sentences.map { text.body.substring(it) }, start, { shadowRange = null })
    }
}

@Composable private fun ReaderParagraph(part: Paragraph, lang: LanguageSpec, highlighter: VocabHighlighter,
    words: List<VocabEntry>, highlights: List<HighlightEntry>, fontSize: Int, spokenSentence: IntRange?, findMatches: List<IntRange>, activeFind: IntRange?, findRevision: Int, lexicon: LemmaLexicon?, idioms: com.ziaee.zabankhan.watch.IdiomMatcher?, scrollTo: suspend (Int) -> Unit, select: (ReaderSelection) -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var selectedRange by remember(part) { mutableStateOf<IntRange?>(null) }
    val gestureWords = remember(part, lang) { tokenizeSurface(part.text, lang).map { it.start until it.end } }
    val latestSelect by rememberUpdatedState(select)
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
    fun selectRange(range: IntRange, phrase: Boolean) {
        val sentence = sentenceAround(part.text, range.first, lang)
        if (sentence.isEmpty()) return
        val idiom = if (phrase) null else idioms?.match(part.text)?.firstOrNull { range.first in it.startChar until it.endChar }
        latestSelect(ReaderSelection(idiom?.expression ?: part.text.substring(range), part.text.substring(sentence),
            (range.first + part.offset)..(range.last + part.offset), phrase,
            (sentence.first + part.offset)..(sentence.last + part.offset)))
    }
    val latestSelectRange by rememberUpdatedState<(IntRange, Boolean) -> Unit>({ range, phrase -> selectRange(range, phrase) })
    val localFind = activeFind?.takeIf { it.first < part.offset + part.text.length && it.last >= part.offset }
    LaunchedEffect(localFind, layout, findRevision) {
        val start = localFind?.let { (it.first - part.offset).coerceIn(0, part.text.length) }
        if (start != null) layout?.let { scrollTo(it.getLineTop(it.getLineForOffset(start)).toInt()) }
    }
    val displayed = buildAnnotatedString {
        append(annotated)
        findMatches.forEach { range ->
            val start = (range.first - part.offset).coerceAtLeast(0)
            val end = (range.last + 1 - part.offset).coerceAtMost(part.text.length)
            if (start < end) addStyle(SpanStyle(background = if (range == activeFind) scheme.tertiary else scheme.tertiaryContainer,
                color = if (range == activeFind) scheme.onTertiary else scheme.onTertiaryContainer), start, end)
        }
        selectedRange?.let { addStyle(SpanStyle(background = scheme.primary.copy(alpha = .35f)), it.first, it.last + 1) }
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
    Text(displayed, modifier = Modifier.fillMaxWidth().drawBehind { spokenPath?.let { drawPath(it, spokenColor) } }.pointerInput(part, lang) {
        // Follow the watch transcript's ownership rules: scrolling wins until a long press.
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun offsetAt(position: androidx.compose.ui.geometry.Offset, inside: Boolean): Int? {
                val value = layout ?: return null
                val line = value.getLineForVerticalPosition(position.y)
                if (inside && (position.y < 0 || position.y >= value.size.height || position.x < value.getLineLeft(line) || position.x >= value.getLineRight(line))) return null
                return value.getOffsetForPosition(position)
            }
            var multiTouch = false
            val longPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (event.changes.size > 1) { multiTouch = true; break }
                    if (change == null || change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    if (!change.pressed) {
                        offsetAt(change.position, true)?.let { offset ->
                            wordAt(part.text, offset, lang)?.let { latestSelectRange(it.start until it.end, false) }
                        }
                        break
                    }
                    if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) break
                }
                false
            } == null
            if (longPress && !multiTouch) {
                val anchorOffset = offsetAt(down.position, true)
                val anchor = gestureWords.indexOfFirst { anchorOffset != null && anchorOffset in it }
                var target = anchor
                if (anchor >= 0) selectedRange = gestureWords[anchor]
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed || event.changes.count { it.pressed } > 1) break
                        if (anchor >= 0) {
                            val offset = offsetAt(change.position, false)
                            if (offset != null) target = gestureWords.indices.minByOrNull { i ->
                                val word = gestureWords[i]
                                when { offset < word.first -> word.first - offset; offset > word.last -> offset - word.last; else -> 0 }
                            } ?: target
                            selectedRange = phraseSelectionRange(gestureWords, anchor, target)
                        }
                        event.changes.forEach { it.consume() }
                        if (!change.pressed) {
                            selectedRange?.let { latestSelectRange(it, true) }
                            break
                        }
                    }
                } finally { selectedRange = null }
            }
        }
    }, style = MaterialTheme.typography.bodyLarge.merge(contentStyle(part.text)).copy(
        fontSize = (19f * fontSize / 20f).sp, lineHeight = (31f * fontSize / 20f).sp, hyphens = Hyphens.None), onTextLayout = { layout = it })
}

/** Only transport controls use physical LTR; prose retains its own direction. */
@Composable private fun ReaderPlayer(speaking: Boolean, rate: Float, previous: () -> Unit, next: () -> Unit,
    toggle: () -> Unit, changeRate: (Float) -> Unit, shadowing: Boolean, shadow: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val rateLabel = stringResource(Res.string.speech_rate)
    Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), tonalElevation = 3.dp, shadowElevation = 6.dp) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "$rateLabel: ${rate}×" }) { Text("${rate}×") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                            DropdownMenuItem(text = { Text("${value}×") }, onClick = { menu = false; changeRate(value) })
                        }
                    }
                }
                IconButton(onClick = previous) { Icon(Icons.Default.SkipPrevious, stringResource(Res.string.watch_previous_sentence)) }
                FilledIconButton(onClick = toggle, modifier = Modifier.size(56.dp)) {
                    Icon(if (speaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (speaking) Res.string.watch_pause else Res.string.watch_play), Modifier.size(28.dp))
                }
                IconButton(onClick = next) { Icon(Icons.Default.SkipNext, stringResource(Res.string.watch_next_sentence)) }
                IconToggleButton(checked = shadowing, onCheckedChange = { shadow() }) { Icon(Icons.Default.Mic, stringResource(Res.string.speech_shadow)) }
            }
        }
    }
}

@Composable private fun ReaderFindBar(query: String, change: (String) -> Unit, index: Int, count: Int,
    previous: () -> Unit, next: () -> Unit, close: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(query, change, modifier = Modifier.weight(1f).focusRequester(focus), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { next() }),
            placeholder = { Text(stringResource(Res.string.reader_find)) }, textStyle = contentStyle(query))
        Text(stringResource(Res.string.reader_find_count, if (count == 0) 0 else index + 1, count),
            Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = previous, enabled = count > 0) { Icon(Icons.Default.KeyboardArrowUp, stringResource(Res.string.reader_find_previous)) }
        IconButton(onClick = next, enabled = count > 0) { Icon(Icons.Default.KeyboardArrowDown, stringResource(Res.string.reader_find_next)) }
        IconButton(onClick = close) { Icon(Icons.Default.Close, stringResource(Res.string.reader_find_close)) }
    }
}

@Composable private fun ReaderTopBar(title: String, back: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        EditorialIconButton(back, Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
        Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.merge(contentStyle(title)))
        actions()
    }
}
