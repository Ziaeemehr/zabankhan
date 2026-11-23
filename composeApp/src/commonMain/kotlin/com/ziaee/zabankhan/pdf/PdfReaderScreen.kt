package com.ziaee.zabankhan.pdf

import com.ziaee.zabankhan.ui.TranslationSourceIcon
import com.ziaee.zabankhan.translate.ExampleTranslation

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.ziaee.zabankhan.ui.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.stringResource

private data class PdfRange(val page: Int, val first: Int, val last: Int, val words: List<PdfWord>, val anchor: Rect) {
    val selected get() = words.subList(minOf(first, last), maxOf(first, last) + 1)
    val text get() = selected.joinToString(" ") { it.text }
}
private data class PdfTextNoteEditor(
    val page: Int,
    val startWord: Int,
    val endWord: Int,
    val selectedText: String,
    val body: String,
)
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable fun PdfReaderScreen(vm: AppViewModel, settings: AppSettings, text: TextDocument, back: () -> Unit) {
    val path = rebasePdfPath(requireNotNull(text.pdfPath), AppFiles.filesDir)
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
    // Real heights up front: on iOS aspect queued behind page renders, and a late height change above the viewport broke backward flings.
    LaunchedEffect(layout) {
        for (i in 0 until handle.pageCount) if (i !in layout.aspects) runCatching { layout.aspects[i] = handle.pageAspect(i) }
    }
    val vocab by remember(text.lang) { vm.container.vocab.observeAll(text.lang) }.collectAsState(emptyList())
    val highlights by remember(text.id) { vm.container.database.pdfHighlights().observeForText(text.id) }.collectAsState(emptyList())
    val inkDao = vm.container.database.pdfInk()
    val pdfTextNoteDao = vm.container.database.pdfTextNotes()
    val savedNotes by remember(text.id) { pdfTextNoteDao.observeForText(text.id) }.collectAsState(emptyList())
    val notesByPage = remember(savedNotes) { savedNotes.groupBy { it.page } }
    val notesRtl = remember(text.lang) { Languages.byCode(text.lang)?.direction == TextDir.RTL }
    val savedInk by remember(text.id) { inkDao.observeForText(text.id) }.collectAsState(initial = null)
    val ink = remember(text.id) { mutableStateMapOf<Int, List<InkStroke>>() }
    fun pageInk(index: Int): List<InkStroke> = ink[index] ?:
        savedInk?.firstOrNull { it.page == index }?.let { decodeInk(it.strokes) }.orEmpty()
    // ViewModel writes survive leaving the reader; the mutex preserves edit order.
    val inkWriteLock = remember(text.id) { Mutex() }
    val noteWriteLock = remember(text.id) { Mutex() }
    val noteVersions = remember(text.id) { mutableMapOf<Triple<Int, Int, Int>, Int>() }
    var noteEditor by remember(path) { mutableStateOf<PdfTextNoteEditor?>(null) }
    var noteSaveJob by remember(path) { mutableStateOf<Job?>(null) }
    var confirmNoteDelete by remember(path) { mutableStateOf(false) }
    fun openPdfNote(note: PdfTextNote) {
        noteEditor = PdfTextNoteEditor(note.page, note.startWord, note.endWord, note.selectedText, note.body)
    }
    fun savePdfNote(note: PdfTextNoteEditor) {
        val key = Triple(note.page, note.startWord, note.endWord)
        val version = noteVersions[key] ?: 0
        vm.action {
            noteWriteLock.withLock {
                if ((noteVersions[key] ?: 0) != version) return@withLock
                if (note.body.isBlank()) {
                    pdfTextNoteDao.delete(text.id, note.page, note.startWord, note.endWord)
                } else {
                    pdfTextNoteDao.upsert(PdfTextNote(
                        textId = text.id,
                        page = note.page,
                        startWord = note.startWord,
                        endWord = note.endWord,
                        selectedText = note.selectedText,
                        body = note.body,
                    ))
                }
            }
        }
    }
    fun closePdfNote() {
        noteSaveJob?.cancel()
        noteEditor?.let(::savePdfNote)
        noteEditor = null
    }
    fun changePdfNoteBody(body: String) {
        val updated = noteEditor?.copy(body = body) ?: return
        val key = Triple(updated.page, updated.startWord, updated.endWord)
        noteVersions[key] = (noteVersions[key] ?: 0) + 1
        noteEditor = updated
        noteSaveJob?.cancel()
        noteSaveJob = scope.launch {
            delay(300)
            savePdfNote(updated)
        }
    }
    fun deletePdfNote() {
        val note = noteEditor ?: return
        val key = Triple(note.page, note.startWord, note.endWord)
        noteVersions[key] = (noteVersions[key] ?: 0) + 1
        noteSaveJob?.cancel()
        noteEditor = null
        vm.action { noteWriteLock.withLock {
            pdfTextNoteDao.delete(text.id, note.page, note.startWord, note.endWord)
        } }
    }
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
    var penColor by rememberSaveable(path) { mutableStateOf("primary") }
    var penWidth by rememberSaveable(path) { mutableFloatStateOf(.004f) }
    var highlighter by rememberSaveable(path) { mutableStateOf(false) }
    var highlighterWidth by rememberSaveable(path) { mutableFloatStateOf(.004f) }
    var eraserSize by rememberSaveable(path) { mutableFloatStateOf(PDF_ERASER_DEFAULT_SIZE) }
    var eraser by rememberSaveable(path) { mutableStateOf(false) }
    var partialEraser by rememberSaveable(path) { mutableStateOf(false) }
    var lastInkToolHighlighter by rememberSaveable(path) { mutableStateOf(false) }
    var smoothPen by rememberSaveable(path) { mutableStateOf(false) }
    var fingerInk by rememberSaveable(path) { mutableStateOf(false) }
    var inkToolbarExpanded by rememberSaveable(path) { mutableStateOf(false) }
    var inkToolbarMinimized by rememberSaveable(path) { mutableStateOf(false) }
    var inkToolbarVertical by rememberSaveable(path) { mutableStateOf(false) }
    var inkToolbarBounds by remember { mutableStateOf<Rect?>(null) }
    var toolbarViewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var toolbarFractionX by rememberSaveable(path) { mutableFloatStateOf(.5f) }
    var toolbarFractionY by rememberSaveable(path) { mutableFloatStateOf(.9f) }
    var recentInkColors by rememberSaveable(path) { mutableStateOf(listOf("primary", "secondary", "tertiary", "#FF222222", "#FFB42318", "#FF1570EF")) }
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
                IconToggleButton(checked = penMode, enabled = !recognizing, onCheckedChange = {
                    penMode = it; areaMode = false; range = null
                }, modifier = Modifier.size(40.dp).semantics { contentDescription = "Pen" }) {
                    Icon(Icons.Default.Edit, stringResource(Res.string.pdf_pen), Modifier.size(20.dp),
                        tint = if (penMode) MaterialTheme.colorScheme.primary else LocalContentColor.current)
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
                    }
                }
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
        }.onGloballyPositioned { toolbarViewportCoordinates = it }.pointerInput(areaMode, penMode, inkToolbarExpanded, inkToolbarBounds) {
            if (areaMode) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (penMode && inkToolbarExpanded) {
                    val rootPosition = toolbarViewportCoordinates?.localToRoot(down.position)
                    if (rootPosition != null && inkToolbarBounds?.contains(rootPosition) != true) inkToolbarExpanded = false
                }
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
            var toolbarAreaSize by remember { mutableStateOf(IntSize.Zero) }
            var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
            val maxToolbarX = (toolbarAreaSize.width - toolbarSize.width).coerceAtLeast(0)
            val maxToolbarY = (toolbarAreaSize.height - toolbarSize.height).coerceAtLeast(0)
            val targetX = (toolbarFractionX * maxToolbarX).toInt().coerceIn(0, maxToolbarX)
            val targetY = (toolbarFractionY * maxToolbarY).toInt().coerceIn(0, maxToolbarY)
            val dragModifier = Modifier.offset { IntOffset(targetX, targetY) }
            // The drag handlers are installed once (pointerInput(Unit)), so read live state here, not captured vals.
            fun moveToolbar(delta: Offset) {
                val maxX = (toolbarAreaSize.width - toolbarSize.width).coerceAtLeast(0)
                val maxY = (toolbarAreaSize.height - toolbarSize.height).coerceAtLeast(0)
                if (maxX > 0) toolbarFractionX = ((toolbarFractionX * maxX + delta.x) / maxX).coerceIn(0f, 1f)
                if (maxY > 0) toolbarFractionY = ((toolbarFractionY * maxY + delta.y) / maxY).coerceIn(0f, 1f)
            }
            fun settleToolbar() {
                val maxX = (toolbarAreaSize.width - toolbarSize.width).coerceAtLeast(0)
                if (maxX == 0) return
                val edge = with(density) { 24.dp.roundToPx() }
                val x = toolbarFractionX * maxX
                if (x <= edge) toolbarFractionX = 0f else if (maxX - x <= edge) toolbarFractionX = 1f
            }
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()
                .scrollable(horizontalPan, Orientation.Horizontal,
                    enabled = zoom > 1f && !areaMode && !(penMode && fingerInk))
                .nestedScroll(verticalPan)) {
                LaunchedEffect(maxWidth, maxHeight) {
                    toolbarAreaSize = IntSize(with(density) { maxWidth.roundToPx() }, with(density) { maxHeight.roundToPx() })
                }
                LazyColumn(state = list, userScrollEnabled = !areaMode && !(penMode && fingerInk),
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom; scaleY = zoom
                        translationX = panX; translationY = panY
                        transformOrigin = TransformOrigin(0f, 0f)
                    }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items((0 until handle.pageCount).toList(), key = { it }) { index ->
                        PdfPage(handle, layout, index, renderZoom, zoom, areaMode, range?.takeIf { it.page == index }, highlights.filter { it.page == index },
                            notes = notesByPage[index].orEmpty(), notesRtl = notesRtl, onOpenNote = ::openPdfNote,
                            listState = list,
                            penMode = penMode, penColor = penColor, penWidth = if (highlighter) highlighterWidth else penWidth, highlighter = highlighter,
                            eraser = eraser, eraserSize = eraserSize, partialEraser = partialEraser, fingerInk = fingerInk, smoothPen = smoothPen,
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
                                        // Area mode disables new scrolling; also wait out any existing fling.
                                        if (handle.deferWorkWhileScrolling) snapshotFlow { list.isScrollInProgress }.first { !it }
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
            if (penMode) {
                val undoPage = lastInkPage ?: page
                PencilDoubleTapEffect(enabled = penMode, onToggle = {
                    if (eraser) { eraser = false; highlighter = lastInkToolHighlighter }
                    else { eraser = true; highlighter = false }
                })
                PdfInkToolbar(
                    modifier = dragModifier.onSizeChanged { toolbarSize = it }.onGloballyPositioned { inkToolbarBounds = it.boundsInRoot() },
                    onMove = { delta -> moveToolbar(delta) }, onDragEnd = { settleToolbar() },
                    minimized = inkToolbarMinimized, expanded = inkToolbarExpanded,
                    vertical = inkToolbarVertical, onToggleOrientation = { inkToolbarVertical = !inkToolbarVertical },
                    penColor = penColor, recentColors = recentInkColors, toolSize = if (eraser) eraserSize else if (highlighter) highlighterWidth else penWidth,
                    eraser = eraser, highlighter = highlighter, fingerInk = fingerInk, smoothPen = smoothPen,
                    partialEraser = partialEraser, onPartialEraser = { partialEraser = it },
                    onMinimize = { inkToolbarMinimized = true }, onRestore = { inkToolbarMinimized = false },
                    onExpand = { inkToolbarExpanded = !inkToolbarExpanded },
                    onColor = { color ->
                        penColor = color
                        if (eraser) { highlighter = false; lastInkToolHighlighter = false }
                        eraser = false
                        recentInkColors = (listOf(color) + recentInkColors.filterNot { it == color }).take(6)
                    },
                    onWidth = { if (eraser) eraserSize = it else if (highlighter) highlighterWidth = it else penWidth = it },
                    onEraser = {
                        eraser = it
                        if (it) {
                            highlighter = false
                            inkToolbarExpanded = true
                        } else lastInkToolHighlighter = false
                    },
                    onHighlighter = { highlighter = it; if (it) { eraser = false; lastInkToolHighlighter = true } else lastInkToolHighlighter = false }, onFinger = { fingerInk = it },
                    onSmoothPen = { smoothPen = it },
                    onToggleEraser = { if (eraser) { eraser = false; highlighter = lastInkToolHighlighter } else { if (!highlighter) lastInkToolHighlighter = false; eraser = true; highlighter = false } },
                    onUndo = { if (pageInk(undoPage).isNotEmpty()) saveInk(undoPage, pageInk(undoPage).dropLast(1)) },
                    canUndo = pageInk(undoPage).isNotEmpty()
                )
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
                    onNote = {
                        val first = minOf(selected.first, selected.last)
                        val last = maxOf(selected.first, selected.last)
                        range = null
                        scope.launch {
                            val saved = pdfTextNoteDao.find(text.id, selected.page, first, last)
                            noteEditor = PdfTextNoteEditor(
                                page = selected.page,
                                startWord = first,
                                endWord = last,
                                selectedText = selected.text,
                                body = saved?.body.orEmpty(),
                            )
                        }
                    },
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
    noteEditor?.let { editor ->
        EditorialBottomSheet(onDismissRequest = ::closePdfNote,
            sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.98f).imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.pdf_note_title, editor.page + 1),
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { confirmNoteDelete = true }) {
                        Icon(Icons.Default.DeleteOutline, stringResource(Res.string.delete),
                            tint = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = ::closePdfNote) { Text(stringResource(Res.string.pdf_done)) }
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(editor.selectedText, Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodyMedium.merge(contentStyle(editor.selectedText)), maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = editor.body,
                        onValueChange = ::changePdfNoteBody,
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.merge(contentStyle(editor.body))
                            .copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxSize()) {
                                if (editor.body.isEmpty()) Text(stringResource(Res.string.pdf_note_hint),
                                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                innerTextField()
                            }
                        },
                    )
                }
            }
        }
    }
    if (confirmNoteDelete) ConfirmDelete(note = noteEditor?.selectedText.orEmpty(),
        onDismiss = { confirmNoteDelete = false }, onDelete = ::deletePdfNote)
    translationSource?.let { source ->
        val dictionaryLang = requireNotNull(Languages.byCode(text.lang))
        val dictionaryTranslator = vm.container.translations(settings)
        var matches by remember(source, translationTarget, dictionaryLang) { mutableStateOf(emptyList<PdfDictionaryMatch>()) }
        LaunchedEffect(source, translationTarget, dictionaryLang, dictionaryTranslator) {
            val lookup = vm.container.ownDictionary.lookupOrEmpty(source.word, dictionaryLang, source.sentence)
            matches = lookup.idioms.map { entry ->
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
        EditorialBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { areaText = null }) {
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

@Composable private fun PdfPage(handle: PdfDocumentHandle, layout: PdfPageWords, index: Int, renderZoom: Float, displayZoom: Float, areaMode: Boolean,
    selection: PdfRange?, highlights: List<PdfHighlight>, notes: List<PdfTextNote>, notesRtl: Boolean,
    onOpenNote: (PdfTextNote) -> Unit,
    listState: LazyListState,
    penMode: Boolean, penColor: String, penWidth: Float, highlighter: Boolean, eraser: Boolean, eraserSize: Float, partialEraser: Boolean, fingerInk: Boolean, smoothPen: Boolean,
    ink: List<InkStroke>, onInk: (List<InkStroke>) -> Unit, onError: () -> Unit,
    onTap: (List<PdfWord>, PdfWord) -> Unit, onRange: (Int, Int, List<PdfWord>, Rect) -> Unit, onRangeEnd: () -> Unit, onDoubleTap: (Offset) -> Unit,
    onArea: (ImageBitmap, PdfRect) -> Unit) {
    val currentInk by rememberUpdatedState(ink)
    val saveInk by rememberUpdatedState(onInk)
    var preview by remember(handle, index) { mutableStateOf<List<InkStroke>?>(null) }
    var eraserCursor by remember(handle, index) { mutableStateOf<Offset?>(null) }
    var hoverCursor by remember(handle, index) { mutableStateOf<Offset?>(null) }
    val livePoints = remember(handle, index) { mutableStateListOf<Float>() }
    val aspect = layout.aspects[index] ?: 1.414f
    var image by remember(handle, index) { mutableStateOf<ImageBitmap?>(layout.cachedImage(index)) }
    var words by remember(handle, index) { mutableStateOf<List<PdfWord>>(emptyList()) }
    var width by remember { mutableIntStateOf(0) }
    val doubleTap by rememberUpdatedState(onDoubleTap)
    var viewport by remember { mutableStateOf(Size.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var box by remember(areaMode) { mutableStateOf<PdfRect?>(null) }

    var renderFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(handle, index, attempt) {
        try { if (index !in layout.aspects) layout.aspects[index] = handle.pageAspect(index) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { renderFailed = true; onError() }
    }
    LaunchedEffect(handle, index, width, renderZoom, attempt) {
        if (handle.deferWorkWhileScrolling) {
            // collectLatest cancels extraction (including a pending mutex wait) as soon as a fling starts.
            snapshotFlow { listState.isScrollInProgress }.distinctUntilChanged().collectLatest { scrolling ->
                if (width <= 0) return@collectLatest
                renderFailed = false
                try {
                    val target = (width * renderZoom).toInt().coerceIn(1, 4096)
                    image = layout.image(index, if (scrolling) (target / 2).coerceAtLeast(1) else target,
                        reuseLarger = true)
                    if (!scrolling) {
                        delay(300)
                        if (!listState.isScrollInProgress) words = layout.get(index)
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { renderFailed = true; onError() }
            }
            return@LaunchedEffect
        }
        if (width > 0) {
            renderFailed = false
            try {
                image = layout.image(index, (width * renderZoom).toInt().coerceAtMost(4096))
                // Word boxes (and OCR on scans) are heavy; skip them for pages that only flash past in a fling.
                delay(300)
                words = layout.get(index)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { renderFailed = true; onError() }
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val markerSize = 40.dp / displayZoom.coerceAtLeast(1f)
    val markerGap = 4.dp / displayZoom.coerceAtLeast(1f)
    val density = LocalDensity.current
    val markerPositions = remember(notes, words, viewport, markerSize, markerGap, notesRtl) {
        placePdfNoteMarkers(notes, words, viewport.width.toInt(), viewport.height.toInt(),
            with(density) { markerSize.roundToPx() }, with(density) { markerGap.roundToPx() }, notesRtl)
    }
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
    }.background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates = it }
        // Pencil hover (iPad): show where the tip will land, before it touches the page.
        .pointerInput(penMode) {
            if (penMode) awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.type != PointerType.Touch }
                    hoverCursor = if (change != null && !change.pressed && event.type != PointerEventType.Exit && size.width > 0)
                        Offset(change.position.x / size.width, change.position.y / size.height) else null
                }
            } else hoverCursor = null
        }
        .pointerInput(penMode, penColor, penWidth, highlighter, eraser, eraserSize, partialEraser, fingerInk, smoothPen, viewport, displayZoom) {
            if (penMode && viewport.width > 0f && viewport.height > 0f) awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull {
                        it.pressed && !it.previousPressed && (it.type == PointerType.Stylus ||
                            it.type == PointerType.Eraser || (fingerInk && it.type == PointerType.Touch && !it.isConsumed))
                    } ?: continue
                    down.consume()
                    val erase = eraser || down.type == PointerType.Eraser
                    val stabilize = smoothPen && !erase && !highlighter
                    val pixelsPerDp = density.density / displayZoom
                    val yScale = viewport.height / viewport.width
                    val original = currentInk
                    var strokes = original
                    val points = mutableListOf<Float>()
                    var previous: Offset? = null
                    var filtered: Offset? = null
                    fun update(point: Offset) {
                        val raw = normalized(point)
                        val n = filtered?.let { previousPoint ->
                            if (stabilize) smoothInkPoint(previousPoint, raw, viewport, pixelsPerDp)
                            else previousPoint + (raw - previousPoint) * .5f
                        } ?: raw
                        filtered = n
                        if (erase) {
                            val start = previous ?: n
                            val delta = n - start
                            val distance = Offset(delta.x, delta.y * yScale).getDistance()
                            val steps = kotlin.math.ceil(distance / .005f).toInt().coerceAtLeast(1)
                            for (step in 0..steps) {
                                val point = start + delta * (step.toFloat() / steps)
                                strokes = if (partialEraser) strokes.flatMap { inkErasePart(it, point.x, point.y, eraserSize, yScale) }
                                    else strokes.filterNot { inkHit(it, point.x, point.y, eraserSize, yScale) }
                            }
                            previous = n
                            eraserCursor = n
                        } else { points += n.x; points += n.y; livePoints.add(n.x); livePoints.add(n.y) }
                        if (erase) preview = strokes
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
                                eraserCursor = null
                                do {
                                    val remaining = awaitPointerEvent(PointerEventPass.Initial)
                                } while (remaining.changes.any { it.pressed })
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.historical.forEach { update(it.position) }
                            update(change.position)
                            change.consume()
                            pressed = change.pressed
                        }
                        val result = if (erase) preview ?: original else original + InkStroke(penColor,
                            width = penWidth * if (highlighter) 3f else 1f,
                            points = simplifyInkPoints(points), highlighter = highlighter)
                        if (!pinch && result != original) saveInk(result)
                    } finally {
                        preview = null
                        eraserCursor = null
                        livePoints.clear()
                    }
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
            notes.forEach { note ->
                if (note.startWord >= 0 && note.endWord in words.indices && note.startWord <= note.endWord) {
                    for (wordIndex in note.startWord..note.endWord) {
                        rectangle(words[wordIndex].rect, tertiary.copy(alpha = .08f))
                    }
                }
            }
            (preview ?: ink).forEach { stroke ->
                val color = inkColor(stroke.colorKey, primary, secondary, tertiary)
                    .copy(alpha = if (stroke.highlighter) .35f else .9f)
                val points = stroke.points
                val radius = stroke.width * size.width / 2f
                // One path per stroke: overlapping translucent segments would leave darker dots.
                if (points.size == 2) drawCircle(color, radius, Offset(points[0] * size.width, points[1] * size.height))
                else drawPath(Path().apply {
                    moveTo(points[0] * size.width, points[1] * size.height)
                    if (points.size == 4) lineTo(points[2] * size.width, points[3] * size.height)
                    else inkCurveSegments(points).forEach { segment ->
                        quadraticBezierTo(segment.controlX * size.width, segment.controlY * size.height,
                            segment.endX * size.width, segment.endY * size.height)
                    }
                    if (points.size > 4) lineTo(points[points.size - 2] * size.width, points.last() * size.height)
                }, color, style = Stroke(radius * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            selection?.selected?.forEach { rectangle(it.rect, primary.copy(alpha = .3f)) }
            box?.let { rectangle(it, primary.copy(alpha = .25f)) }
        }
        Canvas(Modifier.fillMaxSize()) {
            hoverCursor?.takeIf { eraserCursor == null }?.let { cursor ->
                val center = Offset(cursor.x * size.width, cursor.y * size.height)
                val radius = if (eraser) eraserSize * size.width
                    else ((if (highlighter) penWidth * 3f else penWidth) * size.width / 2f).coerceAtLeast(2.dp.toPx())
                drawCircle(if (eraser) Color.Gray.copy(alpha = .5f) else inkColor(penColor, primary, secondary, tertiary).copy(alpha = .5f), radius, center)
            }
            eraserCursor?.let { cursor ->
                val center = Offset(cursor.x * size.width, cursor.y * size.height)
                val radius = eraserSize * size.width
                val outline = 1.dp.toPx() / displayZoom.coerceAtLeast(1f)
                drawCircle(Color.White, radius, center, style = Stroke(outline * 3f))
                drawCircle(Color.Black, radius, center, style = Stroke(outline))
            }
            val points = livePoints.toList()
            if (points.size >= 2) {
                val color = inkColor(penColor, primary, secondary, tertiary).copy(alpha = if (highlighter) .35f else .9f)
                val radius = penWidth * if (highlighter) 3f else 1f
                if (points.size == 2) drawCircle(color, radius * size.width / 2f, Offset(points[0] * size.width, points[1] * size.height))
                else drawPath(Path().apply {
                    moveTo(points[0] * size.width, points[1] * size.height)
                    if (points.size == 4) lineTo(points[2] * size.width, points[3] * size.height)
                    else inkCurveSegments(points).forEach { segment ->
                        quadraticBezierTo(segment.controlX * size.width, segment.controlY * size.height,
                            segment.endX * size.width, segment.endY * size.height)
                    }
                    if (points.size > 4) lineTo(points[points.size - 2] * size.width, points.last() * size.height)
                }, color, style = Stroke(radius * size.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        }
        notes.zip(markerPositions).forEach { (note, position) ->
            Surface(
                onClick = { onOpenNote(note) },
                modifier = Modifier.offset { IntOffset(position.x, position.y) }.size(markerSize),
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ChatBubble, stringResource(Res.string.pdf_note_open, note.selectedText.take(40)),
                        Modifier.size(22.dp / displayZoom.coerceAtLeast(1f)),
                        tint = MaterialTheme.colorScheme.tertiary.copy(alpha = .78f))
                }
            }
        }
    }
}

private val curatedInkColors = listOf(
    "#FF171717", "#FF667085", "#FF98A2B3", "#FFD0D5DD",
    "#FFB42318", "#FFDC6803", "#FFCA8A04", "#FF3F6212",
    "#FF15803D", "#FF0F766E", "#FF026AA2", "#FF175CD3",
    "#FF5925DC", "#FF7A5AF8", "#FFC11574", "#FFD92D20",
)

private fun inkColor(key: String, primary: androidx.compose.ui.graphics.Color,
    secondary: androidx.compose.ui.graphics.Color, tertiary: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color {
    if (key == "secondary") return secondary
    if (key == "tertiary") return tertiary
    if (key == "primary") return primary
    val value = key.drop(1).toLongOrNull(16) ?: return primary
    return androidx.compose.ui.graphics.Color(
        red = ((value shr 16) and 0xff).toFloat() / 255f,
        green = ((value shr 8) and 0xff).toFloat() / 255f,
        blue = (value and 0xff).toFloat() / 255f,
        alpha = ((value shr 24) and 0xff).toFloat() / 255f,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfInkToolbar(
    modifier: Modifier, minimized: Boolean, expanded: Boolean, penColor: String, recentColors: List<String>,
    toolSize: Float, eraser: Boolean, highlighter: Boolean, fingerInk: Boolean, smoothPen: Boolean,
    partialEraser: Boolean, onPartialEraser: (Boolean) -> Unit, vertical: Boolean, onToggleOrientation: () -> Unit,
    onMinimize: () -> Unit, onRestore: () -> Unit, onExpand: () -> Unit,
    onMove: (Offset) -> Unit, onDragEnd: () -> Unit,
    onColor: (String) -> Unit, onWidth: (Float) -> Unit, onEraser: (Boolean) -> Unit,
    onHighlighter: (Boolean) -> Unit, onFinger: (Boolean) -> Unit, onSmoothPen: (Boolean) -> Unit,
    onToggleEraser: () -> Unit,
    onUndo: () -> Unit, canUndo: Boolean,
) {
    val palette = remember { mutableStateOf(false) }
    val currentToolIcon = when { eraser -> Icons.Default.Delete; highlighter -> Icons.Default.Highlight; else -> Icons.Default.Edit }
    if (minimized) {
        Surface(modifier = modifier.size(52.dp).combinedClickable(onClick = onRestore, onLongClick = onToggleEraser)
            .pointerInput(Unit) { detectDragGestures(onDragEnd = onDragEnd) { change, delta -> change.consume(); onMove(delta) } },
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(12.dp).align(Alignment.BottomEnd).background(inkColor(penColor,
                        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary), CircleShape))
                    Icon(currentToolIcon, "Restore pen toolbar", Modifier.size(21.dp))
                }
            }
        }
        return
    }
    BoxWithConstraints(modifier) {
        Column(Modifier.widthIn(max = this@BoxWithConstraints.maxWidth).heightIn(max = this@BoxWithConstraints.maxHeight), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AnimatedVisibility(visible = expanded, enter = expandVertically(tween(150)) + fadeIn(tween(150)),
                exit = shrinkVertically(tween(150)) + fadeOut(tween(150))) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 5.dp) {
                    val expandedItems: @Composable () -> Unit = {
                        recentColors.take(6).forEach { key ->
                            val swatchScale by animateFloatAsState(if (key == penColor) 1.08f else 1f,
                                animationSpec = tween(150), label = "ink-swatch-selection")
                            IconButton(onClick = { onColor(key) }, modifier = Modifier.size(44.dp).semantics { contentDescription = "Ink color" }) {
                    Box(Modifier.size(28.dp).graphicsLayer { scaleX = swatchScale; scaleY = swatchScale }
                        .background(inkColor(key, MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary), CircleShape)
                                    .then(if (key == penColor) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier))
                            }
                        }
                        Box {
                            IconButton(onClick = { palette.value = true }, modifier = Modifier.size(44.dp).semantics { contentDescription = "More colors" }) {
                                Icon(Icons.Default.Add, "More colors", Modifier.size(20.dp))
                            }
                            DropdownMenu(expanded = palette.value, onDismissRequest = { palette.value = false }) {
                                FlowRow(Modifier.width(192.dp).padding(4.dp), horizontalArrangement = Arrangement.Center) {
                                    curatedInkColors.forEach { color ->
                                        IconButton(onClick = { onColor(color); palette.value = false }, modifier = Modifier.size(44.dp).semantics { contentDescription = "Ink color" }) {
                                            Box(Modifier.size(28.dp).background(inkColor(color, MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary), CircleShape))
                                        }
                                    }
                                }
                            }
                        }
                        PdfInkWidthControl(toolSize, vertical, eraser, onWidth)
                        IconToggleButton(checked = !eraser && !highlighter, onCheckedChange = { onEraser(false); onHighlighter(false) },
                            modifier = Modifier.size(44.dp).semantics { contentDescription = "Pen" }) { Icon(Icons.Default.Edit, "Pen", Modifier.size(20.dp)) }
                        FilterChip(selected = smoothPen, enabled = !eraser && !highlighter,
                            onClick = { onSmoothPen(!smoothPen) },
                            label = { Text(stringResource(Res.string.pdf_smooth_pen)) })
                        IconToggleButton(checked = highlighter, onCheckedChange = onHighlighter,
                            modifier = Modifier.size(44.dp).semantics { contentDescription = "Highlighter" }) { Icon(Icons.Default.Highlight, "Highlighter", Modifier.size(20.dp)) }
                        IconToggleButton(checked = eraser, onCheckedChange = onEraser,
                            modifier = Modifier.size(44.dp).semantics { contentDescription = "Eraser" }) { Icon(Icons.Default.Delete, "Eraser", Modifier.size(20.dp)) }
                        if (eraser) FilterChip(selected = partialEraser, onClick = { onPartialEraser(!partialEraser) },
                            label = { Text(stringResource(Res.string.pdf_partial_eraser)) })
                        IconToggleButton(checked = fingerInk, onCheckedChange = onFinger,
                            modifier = Modifier.size(44.dp).semantics { contentDescription = "Finger ink" }) { Icon(Icons.Default.TouchApp, "Finger ink", Modifier.size(20.dp)) }
                        IconButton(enabled = canUndo, onClick = onUndo, modifier = Modifier.size(44.dp).semantics { contentDescription = "Undo ink" }) { Icon(Icons.Default.Undo, "Undo", Modifier.size(20.dp)) }
                    }
                    if (vertical) FlowColumn(Modifier.widthIn(max = this@BoxWithConstraints.maxWidth).heightIn(max = this@BoxWithConstraints.maxHeight * .72f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 5.dp),
                        maxItemsInEachColumn = 8, horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) { expandedItems() }
                    else FlowRow(Modifier.widthIn(max = this@BoxWithConstraints.maxWidth).heightIn(max = this@BoxWithConstraints.maxHeight * .72f).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) { expandedItems() }
                }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
                val toolbarItems: @Composable () -> Unit = {
                    Box(Modifier.size(28.dp).pointerInput(Unit) {
                        detectDragGestures(onDragEnd = onDragEnd) { change, delta -> change.consume(); onMove(delta) }
                    }.semantics { contentDescription = "Move toolbar" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DragIndicator, "Move toolbar", Modifier.size(18.dp))
                    }
                    IconToggleButton(checked = !eraser && !highlighter, onCheckedChange = { onEraser(false); onHighlighter(false) },
                        modifier = Modifier.size(40.dp).semantics { contentDescription = "Pen" }) { Icon(Icons.Default.Edit, "Pen", Modifier.size(19.dp)) }
                    IconToggleButton(checked = highlighter, onCheckedChange = onHighlighter,
                        modifier = Modifier.size(40.dp).semantics { contentDescription = "Highlighter" }) { Icon(Icons.Default.Highlight, "Highlighter", Modifier.size(19.dp)) }
                    IconToggleButton(checked = eraser, onCheckedChange = onEraser,
                        modifier = Modifier.size(40.dp).semantics { contentDescription = "Eraser" }) { Icon(Icons.Default.Delete, "Eraser", Modifier.size(19.dp)) }
                    IconButton(onClick = onExpand, modifier = Modifier.size(40.dp).semantics { contentDescription = "Current ink color" }) {
                        Box(Modifier.size(20.dp).background(inkColor(penColor, MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary), CircleShape))
                    }
                    IconButton(onClick = onExpand, modifier = Modifier.size(44.dp).semantics { contentDescription = if (expanded) "Hide pen tools" else "More pen tools" }) {
                        Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            if (expanded) "Hide pen tools" else "More pen tools", Modifier.size(20.dp))
                    }
                    IconButton(onClick = onToggleOrientation, modifier = Modifier.size(44.dp).semantics { contentDescription = if (vertical) "Horizontal toolbar" else "Vertical toolbar" }) {
                        Icon(if (vertical) Icons.Default.SwapHoriz else Icons.Default.SwapVert,
                            if (vertical) "Horizontal toolbar" else "Vertical toolbar", Modifier.size(20.dp))
                    }
                    IconButton(onClick = onMinimize, modifier = Modifier.size(44.dp).semantics { contentDescription = "Minimize pen toolbar" }) {
                        Icon(Icons.Default.Close, "Minimize toolbar", Modifier.size(18.dp))
                    }
                }
                if (vertical) FlowColumn(Modifier.heightIn(max = this@BoxWithConstraints.maxHeight).verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
                    maxItemsInEachColumn = 10, horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) { toolbarItems() }
                else FlowRow(Modifier.widthIn(max = this@BoxWithConstraints.maxWidth).padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.Center) { toolbarItems() }
            }
        }
    }
}

@Composable
internal fun PdfInkWidthControl(value: Float, vertical: Boolean, eraser: Boolean, onWidth: (Float) -> Unit) {
    val range = if (eraser) PDF_ERASER_MIN_SIZE..PDF_ERASER_MAX_SIZE else PDF_INK_MIN_WIDTH..PDF_INK_MAX_WIDTH
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val previewHeight = (2f + fraction * 8f).dp
    val valueLabel = "${(value * 1000f).toInt() / 10f}%"
    val description = stringResource(if (eraser) Res.string.pdf_eraser_size else Res.string.pdf_pen_thickness)

    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.width(148.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(description, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = value, onValueChange = onWidth, valueRange = range,
                modifier = Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = description })
            PdfInkWidthPreview(previewHeight, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PdfInkWidthPreview(thickness: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.height(18.dp)) {
        drawLine(
            color = color,
            start = Offset(4.dp.toPx(), size.height / 2),
            end = Offset(size.width - 4.dp.toPx(), size.height / 2),
            strokeWidth = thickness.toPx(),
            cap = StrokeCap.Round,
        )
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
    EditorialBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = dismiss) {
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
