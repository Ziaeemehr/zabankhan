package com.ziaee.zabankhan.pdf

import com.ziaee.zabankhan.ui.TranslationSourceIcon
import com.ziaee.zabankhan.translate.ExampleTranslation

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import com.ziaee.zabankhan.lang.TextDir
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.dictionary.*
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import com.ziaee.zabankhan.speech.*
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.ziaee.zabankhan.ui.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.stringResource

private data class PdfRange(val page: Int, val first: Int, val last: Int, val words: List<PdfWord>, val anchor: Rect) {
    val selected get() = words.subList(minOf(first, last), maxOf(first, last) + 1)
    val text get() = selected.joinToString(" ") { it.text }
}
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun PdfReaderScreen(vm: AppViewModel, settings: AppSettings, text: TextDocument, back: () -> Unit) {
    val path = requireNotNull(text.pdfPath)
    var document by remember(path) { mutableStateOf<PdfDocumentHandle?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(path) {
        var opened: PdfDocumentHandle? = null
        try {
            withContext(Dispatchers.IO) { opened = openPdf(path) }
            document = opened
            awaitCancellation()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { withContext(NonCancellable + Dispatchers.IO) { opened?.close() } }
    }
    val handle = document
    if (handle == null) {
        Column {
            EditorialTopAppBar(text.title, back)
            if (failed) Text(stringResource(Res.string.pdf_error)) else CircularProgressIndicator()
        }
        return
    }
    if (handle.pageCount == 0) {
        Column { EditorialTopAppBar(text.title, back); Text(stringResource(Res.string.pdf_empty)) }
        return
    }
    com.ziaee.zabankhan.statistics.TrackActivity(vm.container, text.id, text.lang, com.ziaee.zabankhan.statistics.ActivityKind.READING)
    LaunchedEffect(text.id, handle.pageCount) {
        vm.container.cache.put("pdf:page-count:${text.id}", handle.pageCount.toString())
    }
    val list = rememberLazyListState(text.lastOffset.coerceIn(0, handle.pageCount - 1))
    var zoom by rememberSaveable(path) { mutableFloatStateOf(1f) }
    var panX by rememberSaveable(path) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(path) { mutableFloatStateOf(0f) }
    var renderZoom by rememberSaveable(path) { mutableFloatStateOf(zoom) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val layout = remember(handle, text.lang) { PdfPageWords(path, handle, text.lang) }
    val vocab by remember(text.lang) { vm.container.vocab.observeAll(text.lang) }.collectAsState(emptyList())
    val highlights by remember(text.id) { vm.container.database.pdfHighlights().observeForText(text.id) }.collectAsState(emptyList())
    val inkDao = vm.container.database.pdfInk()
    val savedInk by remember(text.id) { inkDao.observeForText(text.id) }.collectAsState(initial = null)
    val ink = remember(text.id) { mutableStateMapOf<Int, List<InkStroke>>() }
    fun pageInk(index: Int): List<InkStroke> = ink[index] ?:
        savedInk?.firstOrNull { it.page == index }?.let { decodeInk(it.strokes) }.orEmpty()
    // ViewModel writes survive leaving the reader; the mutex preserves edit order.
    val inkWriteLock = remember(text.id) { Mutex() }
    // Undo follows the page last drawn on, which is not always the centre page.
    var lastInkPage by remember(text.id) { mutableStateOf<Int?>(null) }
    fun saveInk(index: Int, strokes: List<InkStroke>) {
        ink[index] = strokes
        lastInkPage = index
        vm.action {
            inkWriteLock.withLock {
                if (strokes.isEmpty()) inkDao.delete(text.id, index)
                else inkDao.upsert(PdfInk(textId = text.id, page = index, strokes = encodeInk(strokes)))
            }
        }
    }
    var penMode by remember(path) { mutableStateOf(false) }
    var penColor by remember(path) { mutableStateOf("primary") }
    var eraser by remember(path) { mutableStateOf(false) }
    var fingerInk by remember(path) { mutableStateOf(false) }
    var selection by remember(path) { mutableStateOf<ReaderSelection?>(null) }
    var range by remember(path) { mutableStateOf<PdfRange?>(null) }
    var selecting by remember(path) { mutableStateOf(false) }
    var areaMode by remember(path) { mutableStateOf(false) }
    var areaText by remember(path) { mutableStateOf<String?>(null) }
    var recognizing by remember(path) { mutableStateOf(false) }
    var page by remember(path) { mutableIntStateOf(list.firstVisibleItemIndex) }
    LaunchedEffect(list, text.id) {
        snapshotFlow {
            val info = list.layoutInfo
            pageAtCentre(info.visibleItemsInfo.map { PdfVisiblePage(it.index, it.offset, it.size) }, info.viewportStartOffset, info.viewportEndOffset)
        }.distinctUntilChanged().collect { index ->
            if (index != null) { page = index; vm.container.texts.savePosition(text.id, index) }
        }
    }
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val tts = remember { createTts() }
    DisposableEffect(tts) { onDispose { tts.stop() } }
    val share = rememberPdfShare()
    var overflow by remember(path) { mutableStateOf(false) }
    var lastSentence by remember(path) { mutableStateOf<String?>(null) }
    var translationSource by remember(path) { mutableStateOf<ReaderSelection?>(null) }
    val translationTarget = settings.meaningLang
    fun translate(source: String, sentence: String) {
        range = null
        selection = null
        if (source.trim().none { it.isWhitespace() }) {
            selection = ReaderSelection(pdfLookupWord(source, text.lang), sentence, 0..0, false)
        } else translationSource = ReaderSelection(source, sentence, 0..0, true)
    }
    var highlightColors by remember(path) { mutableStateOf(false) }
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) range = null }
    fun changeView(factor: Float, centroid: Offset, move: Offset) {
        val (view, overflowY) = pinchView(PdfView(zoom, Offset(panX, panY)), factor, centroid, move,
            Size(viewportWidth.toFloat(), viewportHeight.toFloat()))
        zoom = view.zoom
        panX = view.pan.x
        panY = view.pan.y
        if (overflowY != 0f) list.dispatchRawDelta(-overflowY / zoom)
        range = null
    }
    val pinchByLatest by rememberUpdatedState<(Float, Offset, Offset) -> Unit> { factor, centroid, move ->
        changeView(factor, centroid, move)
    }
    val endPinch by rememberUpdatedState({ renderZoom = zoom })
    val zoomAtPoint by rememberUpdatedState<(Float, Offset) -> Unit> { target, point ->
        changeView(target / zoom, point, Offset.Zero)
        renderZoom = zoom
    }
    val latestZoom by rememberUpdatedState(zoom)
    val horizontalPan = rememberScrollableState { delta ->
        val before = panX
        panX = (panX + delta).coerceIn(-(zoom - 1f) * viewportWidth, 0f)
        panX - before
    }
    val verticalPan = remember(path) { object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y == 0f) return Offset.Zero
            val before = panY
            panY = (panY + available.y * zoom).coerceIn(-(zoom - 1f) * viewportHeight, 0f)
            return Offset(0f, (panY - before) / zoom)
        }
    } }
    Column(Modifier.fillMaxSize()) {
        // Slim bar: the file name added nothing; only the page counter and a few actions remain.
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(40.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back), Modifier.size(20.dp))
                }
                handle.let { pages ->
                    val indicator = stringResource(Res.string.pdf_page_indicator, (page + 1).coerceAtMost(pages.pageCount), pages.pageCount)
                    Text(stringResource(Res.string.pdf_page_short, (page + 1).coerceAtMost(pages.pageCount), pages.pageCount),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.semantics { contentDescription = indicator })
                }
                Spacer(Modifier.weight(1f))
                IconButton(enabled = !recognizing, modifier = Modifier.size(40.dp), onClick = {
                    areaMode = !areaMode
                    range = null; penMode = false
                }) {
                    Icon(Icons.Default.Gesture, stringResource(Res.string.pdf_select_area), Modifier.size(20.dp),
                        tint = if (areaMode) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
                Box {
                    IconButton(onClick = { overflow = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, stringResource(Res.string.selection_action_more), Modifier.size(20.dp)) }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.pdf_share)) }, onClick = {
                            overflow = false
                            try { share(path) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true }
                        })
                        DropdownMenuItem(enabled = lastSentence != null,
                            text = { Text(stringResource(Res.string.pdf_translate_sentence)) },
                            onClick = { overflow = false; lastSentence?.let { translate(it, it) } })
                        DropdownMenuItem(enabled = !recognizing && savedInk != null,
                            text = { Text(stringResource(Res.string.pdf_pen)) }, onClick = {
                                overflow = false; penMode = !penMode; areaMode = false; range = null
                            })
                    }
                }
            }
        }
        if (penMode) Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            FlowRow(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("primary" to Res.string.highlight_primary, "secondary" to Res.string.highlight_secondary, "tertiary" to Res.string.highlight_tertiary).forEach { (key, label) ->
                    val swatch = when (key) { "secondary" -> MaterialTheme.colorScheme.secondary; "tertiary" -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary }
                    val onSwatch = when (key) { "secondary" -> MaterialTheme.colorScheme.onSecondary; "tertiary" -> MaterialTheme.colorScheme.onTertiary; else -> MaterialTheme.colorScheme.onPrimary }
                    val description = stringResource(label)
                    IconToggleButton(checked = penColor == key && !eraser, onCheckedChange = { penColor = key; eraser = false }, modifier = Modifier.size(48.dp).semantics { contentDescription = description }) {
                        Box(Modifier.size(28.dp).background(swatch, CircleShape), contentAlignment = Alignment.Center) {
                            if (penColor == key && !eraser) Icon(Icons.Default.Check, null, tint = onSwatch, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                FilterChip(selected = eraser, onClick = { eraser = !eraser }, label = { Text(stringResource(Res.string.pdf_pen_eraser)) })
                FilterChip(selected = fingerInk, onClick = { fingerInk = !fingerInk }, label = { Text(stringResource(Res.string.pdf_pen_finger)) })
                val undoPage = lastInkPage ?: page
                TextButton(enabled = pageInk(undoPage).isNotEmpty(), onClick = { saveInk(undoPage, pageInk(undoPage).dropLast(1)) }) { Text(stringResource(Res.string.pdf_pen_undo)) }
                TextButton(enabled = pageInk(page).isNotEmpty(), onClick = { saveInk(page, emptyList()) }) { Text(stringResource(Res.string.pdf_pen_clear)) }
            }
        }
        if (recognizing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Row {
            Text(stringResource(Res.string.pdf_error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { failed = false }) { Text(stringResource(Res.string.pdf_done)) }
        }
        // Pinch coordinates belong to the untransformed viewport, as in French Reader.
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged {
            viewportWidth = it.width; viewportHeight = it.height
        }.pointerInput(areaMode, penMode) {
            if (areaMode) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var pinching = false
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val stylus = penMode && event.changes.any {
                            it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser)
                        }
                        if (!stylus && event.changes.count { it.pressed } >= 2) {
                            pinching = true
                            val factor = event.calculateZoom()
                            if (factor.isFinite()) pinchByLatest(factor,
                                event.calculateCentroid(useCurrent = false), event.calculatePan())
                        }
                        // Keep consuming until every finger lifts, including the remaining finger.
                        if (pinching) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                } finally {
                    if (pinching) endPinch()
                }
            }
        }) {
            Box(Modifier.fillMaxSize().clipToBounds()
                .scrollable(horizontalPan, Orientation.Horizontal,
                    enabled = zoom > 1f && !areaMode && !(penMode && fingerInk))
                .nestedScroll(verticalPan)) {
                LazyColumn(state = list, userScrollEnabled = !areaMode && !(penMode && fingerInk),
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom; scaleY = zoom
                        translationX = panX; translationY = panY
                        transformOrigin = TransformOrigin(0f, 0f)
                    }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items((0 until handle.pageCount).toList(), key = { it }) { index ->
                        PdfPage(handle, layout, index, renderZoom, areaMode, range?.takeIf { it.page == index }, highlights.filter { it.page == index },
                            penMode = penMode, penColor = penColor, eraser = eraser, fingerInk = fingerInk,
                            ink = pageInk(index),
                            onInk = { saveInk(index, it) },
                            onError = { failed = true },
                            onTap = { words, word ->
                                range = null
                                lastSentence = sentenceAround(words, words.indexOf(word))
                                selection = ReaderSelection(pdfLookupWord(word.text, text.lang), lastSentence.orEmpty(), 0..0, false)
                            },
                            onRange = { first, last, words, anchor ->
                                selecting = true
                                lastSentence = sentenceAround(words, minOf(first, last))
                                range = PdfRange(index, first, last, words, anchor)
                            },
                            onRangeEnd = { selecting = false },
                            onDoubleTap = { point ->
                                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                if (item != null) zoomAtPoint(if (latestZoom > 1f) 1f else 2.5f,
                                    Offset(point.x * latestZoom + panX, (point.y + item.offset) * latestZoom + panY))
                            },
                            onArea = { image, rect ->
                                scope.launch {
                                    recognizing = true
                                    try {
                                        val recognized = withContext(Dispatchers.Default) {
                                            val highResolution = handle.renderPage(index, OcrRegionFilter.renderWidth(rect, handle.pageAspect(index), image.width))
                                            createOcrEngine().recognizeWords(cropPdfImage(highResolution, rect), text.lang)
                                        }
                                        areaText = PdfTextCleaner.clean(recognized.joinToString(" ") { it.text })
                                    } catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { failed = true }
                                    finally { recognizing = false }
                                    areaMode = false
                                }
                            })
                    }
                }
            }
        }
    }
    range?.takeUnless { selecting }?.let { selected ->
        val overlapping = highlights.filter { it.page == selected.page &&
            it.startWord <= maxOf(selected.first, selected.last) && it.endWord >= minOf(selected.first, selected.last) }
        val sentence = sentenceAround(selected.words, minOf(selected.first, selected.last))
        Popup(popupPositionProvider = SelectionRectPositionProvider(selected.anchor, with(density) { 8.dp.toPx() }),
            onDismissRequest = { range = null; highlightColors = false }) {
            Box {
                SelectionToolbarContent(
                    onCopy = { clipboard.setText(AnnotatedString(selected.text)); range = null },
                    onSave = { selection = ReaderSelection(selected.text, sentence, 0..0, selected.first != selected.last); range = null },
                    onListen = { tts.pronounce(selected.text, text.lang, settings); range = null },
                    onHighlight = { vm.action {
                        vm.container.database.pdfHighlights().insert(PdfHighlight(textId = text.id, page = selected.page,
                            startWord = minOf(selected.first, selected.last), endWord = maxOf(selected.first, selected.last), colorKey = "primary"))
                        range = null
                    } },
                    onChooseHighlightColor = { highlightColors = true },
                    onDeleteHighlight = overlapping.takeIf { it.isNotEmpty() }?.let { found -> {
                        vm.action { found.forEach { vm.container.database.pdfHighlights().delete(it.id) } }
                        range = null; highlightColors = false
                    } },
                    onMore = { highlightColors = true },
                    onTranslate = { translate(selected.text, sentence) },
                    showReadingActions = true, showListen = true)
                DropdownMenu(highlightColors, { highlightColors = false }) {
                    listOf("primary" to Res.string.highlight_primary, "secondary" to Res.string.highlight_secondary,
                        "tertiary" to Res.string.highlight_tertiary).forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { vm.action {
                            vm.container.database.pdfHighlights().insert(PdfHighlight(textId = text.id, page = selected.page,
                                startWord = minOf(selected.first, selected.last), endWord = maxOf(selected.first, selected.last), colorKey = key))
                            range = null; highlightColors = false
                        } })
                    }
                    DropdownMenuItem(text = { Text(stringResource(Res.string.cancel)) }, onClick = {
                        range = null; highlightColors = false
                    })
                }
            }
        }
    }
    translationSource?.let { source ->
        val dictionaryLang = requireNotNull(Languages.byCode(text.lang))
        val dictionaryTranslator = vm.container.translations(settings)
        var matches by remember(source, translationTarget, dictionaryLang) { mutableStateOf(emptyList<PdfDictionaryMatch>()) }
        LaunchedEffect(source, translationTarget, dictionaryLang, dictionaryTranslator) {
            matches = vm.container.ownDictionary.lookupOrEmpty(source.word, dictionaryLang)
                .filter { dictionaryIdiomMatches(it.word, source.word) }.map { entry ->
                    val result = vm.container.ownDictionary.meaningOrEnglish(entry, dictionaryLang, translationTarget, dictionaryTranslator)
                    PdfDictionaryMatch(entry.word, result.meanings.take(4).joinToString("؛ "), result.machineTranslated, result.translations)
                }
        }
        PdfTranslationSheet(vm, settings, text, source, translationTarget,
            maxHeight = with(density) { viewportHeight.toDp() * .7f }, dictionaryMatches = matches,
            onListen = { tts.pronounce(source.word, text.lang, settings) },
            onDictionary = { translationSource = null; selection = source },
            dismiss = { translationSource = null })
    }
    selection?.let { DictionarySheet(vm, settings, text, it, vocab, emptyList(), dismiss = { selection = null }) }
    areaText?.let { recognized ->
        ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { areaText = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.pdf_recognized_text), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(recognized, { areaText = it }, modifier = Modifier.fillMaxWidth(), textStyle = contentStyle(recognized))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(recognized)) }) { Text(stringResource(Res.string.pdf_copy)) }
                    TextButton(enabled = recognized.isNotBlank(), onClick = { areaText = null; translate(recognized, recognized) }) { Text(stringResource(Res.string.pdf_translate)) }
                    TextButton(enabled = recognized.isNotBlank(), onClick = { vm.action {
                        vm.container.texts.insert(TextDocument(lang = text.lang, title = text.title + " · " + (page + 1), body = recognized))
                        areaText = null
                    } }) { Text(stringResource(Res.string.pdf_save_text)) }
                }
            }
        }
    }
}

@Composable private fun PdfPage(handle: PdfDocumentHandle, layout: PdfPageWords, index: Int, renderZoom: Float, areaMode: Boolean,
    selection: PdfRange?, highlights: List<PdfHighlight>,
    penMode: Boolean, penColor: String, eraser: Boolean, fingerInk: Boolean,
    ink: List<InkStroke>, onInk: (List<InkStroke>) -> Unit, onError: () -> Unit,
    onTap: (List<PdfWord>, PdfWord) -> Unit, onRange: (Int, Int, List<PdfWord>, Rect) -> Unit, onRangeEnd: () -> Unit, onDoubleTap: (Offset) -> Unit,
    onArea: (ImageBitmap, PdfRect) -> Unit) {
    val currentInk by rememberUpdatedState(ink)
    val saveInk by rememberUpdatedState(onInk)
    var preview by remember(handle, index) { mutableStateOf<List<InkStroke>?>(null) }
    var aspect by remember(handle, index) { mutableFloatStateOf(1.414f) }
    var image by remember(handle, index) { mutableStateOf<ImageBitmap?>(null) }
    var words by remember(handle, index) { mutableStateOf<List<PdfWord>>(emptyList()) }
    var width by remember { mutableIntStateOf(0) }
    val doubleTap by rememberUpdatedState(onDoubleTap)
    var viewport by remember { mutableStateOf(Size.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var box by remember(areaMode) { mutableStateOf<PdfRect?>(null) }

    var renderFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(handle, index, attempt) {
        try { aspect = handle.pageAspect(index) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { renderFailed = true; onError() }
    }
    LaunchedEffect(handle, index, width, renderZoom, attempt) {
        if (width > 0) {
            renderFailed = false
            try {
                image = handle.renderPage(index, (width * renderZoom).toInt().coerceAtMost(4096))
                words = layout.get(index)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { renderFailed = true; onError() }
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    fun normalized(point: Offset) = Offset((point.x / viewport.width).coerceIn(0f, 1f), (point.y / viewport.height).coerceIn(0f, 1f))
    fun select(first: Int, last: Int) {
        val layout = coordinates?.takeIf { it.isAttached } ?: return
        val chosen = words.subList(minOf(first, last), maxOf(first, last) + 1)
        val topLeft = layout.localToWindow(Offset(chosen.minOf { it.rect.left } * viewport.width,
            chosen.minOf { it.rect.top } * viewport.height))
        val bottomRight = layout.localToWindow(Offset(chosen.maxOf { it.rect.right } * viewport.width,
            chosen.maxOf { it.rect.bottom } * viewport.height))
        onRange(first, last, words, Rect(topLeft, bottomRight))
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f / aspect).onSizeChanged {
        width = it.width; viewport = Size(it.width.toFloat(), it.height.toFloat())
    }.onGloballyPositioned { coordinates = it }.background(MaterialTheme.colorScheme.surface)
        .pointerInput(penMode, penColor, eraser, fingerInk, viewport) {
            if (penMode) awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull {
                        it.pressed && !it.previousPressed && (it.type == PointerType.Stylus ||
                            it.type == PointerType.Eraser || (fingerInk && it.type == PointerType.Touch && !it.isConsumed))
                    } ?: continue
                    down.consume()
                    val erase = eraser || down.type == PointerType.Eraser
                    val original = currentInk
                    var strokes = original
                    val points = mutableListOf<Float>()
                    var previous: Offset? = null
                    fun update(point: Offset) {
                        val n = normalized(point)
                        if (erase) {
                            val start = previous ?: n
                            val steps = kotlin.math.ceil((n - start).getDistance() / .005f).toInt().coerceAtLeast(1)
                            for (step in 0..steps) {
                                val point = start + (n - start) * (step.toFloat() / steps)
                                strokes = strokes.filterNot { inkHit(it, point.x, point.y) }
                            }
                            previous = n
                        } else { points += n.x; points += n.y }
                        preview = if (erase) strokes else strokes + InkStroke(penColor, points = simplifyInkPoints(points))
                    }
                    try {
                        update(down.position)
                        var pressed = true
                        var pinch = false
                        while (pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            // A second finger means pinch zoom: drop the provisional finger stroke.
                            if (down.type == PointerType.Touch && event.changes.count { it.pressed } > 1) {
                                pinch = true
                                preview = null
                                do {
                                    val remaining = awaitPointerEvent(PointerEventPass.Initial)
                                } while (remaining.changes.any { it.pressed })
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            update(change.position)
                            change.consume()
                            pressed = change.pressed
                        }
                        val result = preview ?: original
                        if (!pinch && result != original) saveInk(result)
                    } finally { preview = null }
                }
            }
        }
        .pointerInput(areaMode, penMode, words, image, viewport, highlights) {
            detectTapGestures(
                onDoubleTap = { doubleTap(it) },
                onTap = { point ->
                    if (!areaMode) {
                        val n = normalized(point)
                        hitTestWord(words, n.x, n.y, .008f)?.let { word ->
                            val wordIndex = words.indexOf(word)
                            if (highlights.any { wordIndex in it.startWord..it.endWord }) {
                                select(wordIndex, wordIndex)
                                onRangeEnd()
                            } else onTap(words, word)
                        }
                    }
                })
        }
        .pointerInput(areaMode, words, viewport) {
            var anchor: Int? = null
            if (!areaMode) detectDragGesturesAfterLongPress(
                onDragEnd = { onRangeEnd() }, onDragCancel = { onRangeEnd() },
                onDragStart = { point ->
                    val n = normalized(point)
                    hitTestWord(words, n.x, n.y, .012f)?.let { word ->
                        val first = words.indexOf(word)
                        anchor = first
                        select(first, first)
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val n = normalized(change.position)
                    hitTestWord(words, n.x, n.y, .02f)?.let { word ->
                        anchor?.let { select(it, words.indexOf(word)) }
                    }
                })
        }
        .pointerInput(areaMode, image, viewport) {
            if (areaMode) {
                var start = Offset.Zero
                detectDragGestures(onDragStart = { start = normalized(it) }, onDrag = { change, _ ->
                    change.consume()
                    val end = normalized(change.position)
                    box = PdfRect(minOf(start.x,end.x), minOf(start.y,end.y), maxOf(start.x,end.x), maxOf(start.y,end.y))
                }, onDragEnd = {
                    val rect = box; val bitmap = image
                    if (rect != null && bitmap != null && rect.right - rect.left > .01f && rect.bottom - rect.top > .01f) onArea(bitmap, rect)
                    box = null
                }, onDragCancel = { box = null })
            }
        }) {
        image?.let { Image(it, null, Modifier.fillMaxSize()) }
        if (image == null && !renderFailed) CircularProgressIndicator()
        if (renderFailed) TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) }
        Canvas(Modifier.fillMaxSize()) {
            fun rectangle(rect: PdfRect, color: androidx.compose.ui.graphics.Color) {
                drawRect(color, Offset(rect.left * size.width, rect.top * size.height),
                    Size((rect.right - rect.left) * size.width, (rect.bottom - rect.top) * size.height))
            }
            highlights.forEach { h ->
                words.filterIndexed { i, _ -> i in h.startWord..h.endWord }.forEach {
                    rectangle(it.rect, (when (h.colorKey) { "secondary" -> secondary; "tertiary" -> tertiary; else -> primary }).copy(alpha = .25f))
                }
            }
            (preview ?: ink).forEach { stroke ->
                val color = (when (stroke.colorKey) { "secondary" -> secondary; "tertiary" -> tertiary; else -> primary }).copy(alpha = .9f)
                val points = stroke.points
                val radius = stroke.width * size.width / 2f
                // One path per stroke: overlapping translucent segments would leave darker dots.
                if (points.size == 2) drawCircle(color, radius, Offset(points[0] * size.width, points[1] * size.height))
                else drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(points[0] * size.width, points[1] * size.height)
                    for (i in 2 until points.size - 1 step 2) lineTo(points[i] * size.width, points[i + 1] * size.height)
                }, color, style = androidx.compose.ui.graphics.drawscope.Stroke(radius * 2f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            selection?.selected?.forEach { rectangle(it.rect, primary.copy(alpha = .3f)) }
            box?.let { rectangle(it, primary.copy(alpha = .25f)) }
        }
    }
}

/** Expressions found in the selected text, ready to save with their dictionary meaning. */
internal data class PdfDictionaryMatch(val phrase: String, val meaning: String, val machineTranslated: Boolean = false, val translations: List<ExampleTranslation> = emptyList())

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PdfTranslationSheet(
    vm: AppViewModel, settings: AppSettings, text: TextDocument, source: ReaderSelection,
    target: String, maxHeight: androidx.compose.ui.unit.Dp,
    dictionaryMatches: List<PdfDictionaryMatch>, onListen: () -> Unit,
    onDictionary: () -> Unit, dismiss: () -> Unit,
) {
    val repository = vm.container.translations(settings)
    var provenance by remember(source, target) { mutableStateOf<ExampleTranslation?>(null) }
    var translation by remember(source, target) { mutableStateOf<String?>(null) }
    var loading by remember(source, target) { mutableStateOf(true) }
    var attempt by remember(source, target) { mutableIntStateOf(0) }
    var saving by remember(source, target) { mutableStateOf(false) }
    var saved by remember(source, target) { mutableStateOf(false) }
    var saveFailed by remember(source, target) { mutableStateOf(false) }
    var showSaveConfirmation by remember(source, target) { mutableStateOf(false) }
    LaunchedEffect(saved) {
        if (saved) {
            showSaveConfirmation = true
            delay(2500)
            showSaveConfirmation = false
        }
    }
    LaunchedEffect(source, target, repository, attempt) {
        loading = true
        translation = null
        try {
            provenance = repository.translateTextWithSource(source.word, text.lang, target, source.sentence).getOrThrow()
            translation = provenance?.text
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { translation = null }
        finally { loading = false }
    }
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
            .padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.word, Modifier.weight(1f), style = contentStyle(source.word))
                IconButton(onClick = onListen) { Icon(Icons.Default.VolumeUp, stringResource(Res.string.selection_action_listen)) }
            }
            when {
                loading -> { CircularProgressIndicator(); Text(stringResource(Res.string.translation_loading)) }
                translation == null -> {
                    Text(stringResource(Res.string.error_translation_unavailable), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) }
                }
                else -> {
                    val rtl = Languages.byCode(target)?.direction == TextDir.RTL
                    SelectionContainer {
                        Text(translation.orEmpty(), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr),
                            textAlign = if (rtl) TextAlign.Right else TextAlign.Left)
                    }
                    TranslationSourceIcon(provenance)
                }
            }
            dictionaryMatches.forEach { match ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(match.phrase, style = contentStyle(match.phrase))
                        Text(match.meaning, style = contentStyle(match.meaning))
                        if (match.translations.isEmpty()) TranslationSourceIcon(com.ziaee.zabankhan.translate.ExampleSource.DICTIONARY)
                        else match.translations.distinctBy { it.source to it.engine }.forEach { TranslationSourceIcon(it) }
                        var matchSaved by remember(source, target, match) { mutableStateOf(false) }
                        var matchSaving by remember(source, target, match) { mutableStateOf(false) }
                        TextButton(enabled = match.meaning.isNotBlank() && !matchSaved && !matchSaving, onClick = {
                            matchSaving = true
                            saveFailed = false
                            vm.action {
                                try {
                                    val lang = requireNotNull(Languages.byCode(text.lang))
                                    val existing = dictionarySavedState(vm.container.vocab.getAll(text.lang), lang,
                                        text.id, match.phrase, source.sentence).exactEntry
                                    val listId = existing?.listId ?: vm.container.cache.get("dictionary:last-list:${text.lang}")?.toLongOrNull()
                                    val entry = existing?.copy(meaning = match.meaning, meaningLang = target)
                                        ?: VocabEntry(lang = text.lang, textId = text.id, word = match.phrase,
                                            sentence = source.sentence, meaning = match.meaning, meaningLang = target, listId = listId)
                                    if (entry.id == 0L) vm.container.vocab.insert(entry) else vm.container.vocab.update(entry)
                                    matchSaved = true
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { saveFailed = true }
                                finally { matchSaving = false }
                            }
                        }) { Text(stringResource(if (matchSaved) Res.string.saved else Res.string.pdf_add_to_leitner)) }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !loading && !translation.isNullOrBlank() && !saving && !saved, onClick = {
                    val meaning = translation ?: return@Button
                    saving = true
                    saveFailed = false
                    vm.action {
                        try {
                            val lang = requireNotNull(Languages.byCode(text.lang))
                            val existing = dictionarySavedState(vm.container.vocab.getAll(text.lang), lang,
                                text.id, source.word, source.sentence).exactEntry
                            val listId = existing?.listId ?: vm.container.cache.get("dictionary:last-list:${text.lang}")?.toLongOrNull()
                            val entry = existing?.copy(meaning = meaning, meaningLang = target)
                                ?: VocabEntry(lang = text.lang, textId = text.id, word = source.word,
                                    sentence = source.sentence, meaning = meaning, meaningLang = target, listId = listId)
                            if (entry.id == 0L) vm.container.vocab.insert(entry) else vm.container.vocab.update(entry)
                            saved = true
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { saveFailed = true }
                        finally { saving = false }
                    }
                }) { Text(stringResource(Res.string.pdf_add_to_leitner)) }
                TextButton(onClick = onDictionary) { Text(stringResource(Res.string.pdf_full_dictionary)) }
            }
            if (showSaveConfirmation) Text(stringResource(Res.string.saved))
            if (saveFailed) Text(stringResource(Res.string.pdf_vocab_save_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}
