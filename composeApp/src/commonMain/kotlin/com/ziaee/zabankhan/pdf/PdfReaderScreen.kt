package com.ziaee.zabankhan.pdf

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val list = rememberLazyListState(text.lastOffset.coerceIn(0, handle.pageCount - 1))
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
    var highlightColors by remember(path) { mutableStateOf(false) }
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) range = null }
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
                            onClick = { overflow = false; lastSentence?.let { selection = ReaderSelection(it, it, 0..0, true) } })
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
        LazyColumn(state = list, userScrollEnabled = !areaMode, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0 until handle.pageCount).toList(), key = { it }) { index ->
                PdfPage(handle, layout, index, areaMode, range?.takeIf { it.page == index }, highlights.filter { it.page == index },
                    penMode = penMode, penColor = penColor, eraser = eraser, fingerInk = fingerInk,
                    ink = pageInk(index),
                    onInk = { saveInk(index, it) },
                    onError = { failed = true },
                    onTap = { words, word ->
                        range = null
                        lastSentence = sentenceAround(words, words.indexOf(word))
                        selection = ReaderSelection(pdfLookupWord(word.text, text.lang), lastSentence.orEmpty(), 0..0, false)
                    },
                    onRange = { first, last, words, anchor -> selecting = true; range = PdfRange(index, first, last, words, anchor) },
                    onRangeEnd = { selecting = false },
                    onScroll = { amount -> scope.launch { list.scrollBy(amount) } },
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
    range?.takeUnless { selecting }?.let { selected ->
        Popup(popupPositionProvider = SelectionRectPositionProvider(selected.anchor, with(density) { 8.dp.toPx() }),
            onDismissRequest = { range = null; highlightColors = false }) {
            Box {
                SelectionToolbarContent(
                    onCopy = { clipboard.setText(AnnotatedString(selected.text)); range = null },
                    onSave = { selection = ReaderSelection(selected.text, selected.text, 0..0, true); range = null },
                    onListen = { tts.pronounce(selected.text, text.lang, settings); range = null },
                    onHighlight = { vm.action {
                        vm.container.database.pdfHighlights().insert(PdfHighlight(textId = text.id, page = selected.page,
                            startWord = minOf(selected.first, selected.last), endWord = maxOf(selected.first, selected.last), colorKey = "primary"))
                        range = null
                    } },
                    onChooseHighlightColor = { highlightColors = true }, onDeleteHighlight = null,
                    onMore = { highlightColors = true },
                    onTranslate = { selection = ReaderSelection(selected.text, selected.text, 0..0, true); range = null },
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
    selection?.let { DictionarySheet(vm, settings, text, it, vocab, emptyList(), dismiss = { selection = null }) }
    areaText?.let { recognized ->
        ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { areaText = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.pdf_recognized_text), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(recognized, { areaText = it }, modifier = Modifier.fillMaxWidth(), textStyle = contentStyle(recognized))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(recognized)) }) { Text(stringResource(Res.string.pdf_copy)) }
                    TextButton(enabled = recognized.isNotBlank(), onClick = { areaText = null; selection = ReaderSelection(recognized, recognized, 0..0, true) }) { Text(stringResource(Res.string.pdf_translate)) }
                    TextButton(enabled = recognized.isNotBlank(), onClick = { vm.action {
                        vm.container.texts.insert(TextDocument(lang = text.lang, title = text.title + " · " + (page + 1), body = recognized))
                        areaText = null
                    } }) { Text(stringResource(Res.string.pdf_save_text)) }
                }
            }
        }
    }
}

@Composable private fun PdfPage(handle: PdfDocumentHandle, layout: PdfPageWords, index: Int, areaMode: Boolean,
    selection: PdfRange?, highlights: List<PdfHighlight>,
    penMode: Boolean, penColor: String, eraser: Boolean, fingerInk: Boolean,
    ink: List<InkStroke>, onInk: (List<InkStroke>) -> Unit, onError: () -> Unit,
    onTap: (List<PdfWord>, PdfWord) -> Unit, onRange: (Int, Int, List<PdfWord>, Rect) -> Unit, onRangeEnd: () -> Unit, onScroll: (Float) -> Unit,
    onArea: (ImageBitmap, PdfRect) -> Unit) {
    val currentInk by rememberUpdatedState(ink)
    val saveInk by rememberUpdatedState(onInk)
    var preview by remember(handle, index) { mutableStateOf<List<InkStroke>?>(null) }
    var aspect by remember(handle, index) { mutableFloatStateOf(1.414f) }
    var image by remember(handle, index) { mutableStateOf<ImageBitmap?>(null) }
    var words by remember(handle, index) { mutableStateOf<List<PdfWord>>(emptyList()) }
    var width by remember { mutableIntStateOf(0) }
    var view by remember(handle, index) { mutableStateOf(PdfView(1f, Offset.Zero)) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var selectingWords by remember { mutableStateOf(false) }
    var box by remember(areaMode) { mutableStateOf<PdfRect?>(null) }

    var renderFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(handle, index, attempt) {
        try { aspect = handle.pageAspect(index) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { renderFailed = true; onError() }
    }
    LaunchedEffect(handle, index, width, attempt) {
        if (width > 0) {
            renderFailed = false
            try {
                image = handle.renderPage(index, width)
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
    }.graphicsLayer {
        scaleX = view.zoom; scaleY = view.zoom
        translationX = view.pan.x; translationY = view.pan.y
        transformOrigin = TransformOrigin(0f, 0f)
    }.onGloballyPositioned { coordinates = it }.background(MaterialTheme.colorScheme.surface)
        .pointerInput(penMode, penColor, eraser, fingerInk, viewport) {
            if (penMode) awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull {
                        it.pressed && !it.previousPressed && (it.type == PointerType.Stylus ||
                            it.type == PointerType.Eraser || (fingerInk && it.type == PointerType.Touch))
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
                            if (down.type == PointerType.Touch && event.changes.count { it.pressed } > 1) { pinch = true; break }
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
        .pointerInput(areaMode, words, image, viewport) {
            detectTapGestures(
                onDoubleTap = { point ->
                    view = if (view.zoom > 1f) PdfView(1f, Offset.Zero) else
                        PdfView(2.5f, clampPan(2.5f, point * -1.5f, viewport))
                },
                onTap = { point ->
                    if (!areaMode) {
                        val n = normalized(point)
                        hitTestWord(words, n.x, n.y, .008f)?.let { onTap(words, it) }
                    }
                })
        }
        .pointerInput(areaMode, words, viewport) {
            var anchor: Int? = null
            if (!areaMode) detectDragGesturesAfterLongPress(
                onDragEnd = { selectingWords = false; onRangeEnd() }, onDragCancel = { selectingWords = false; onRangeEnd() },
                onDragStart = { point ->
                    val n = normalized(point)
                    hitTestWord(words, n.x, n.y, .012f)?.let { word ->
                        val first = words.indexOf(word)
                        selectingWords = true
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
        }
        .pointerInput(areaMode, viewport, penMode, fingerInk) {
            if (!areaMode) awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (penMode && (down.type == PointerType.Stylus || down.type == PointerType.Eraser)) return@awaitEachGesture
                val inkFinger = penMode && fingerInk && down.type == PointerType.Touch
                var transforming = false
                var totalPan = Offset.Zero
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (penMode && event.changes.any { it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser) }) continue
                    val fingers = event.changes.count { it.pressed }
                    // With finger drawing, one finger draws; only two fingers zoom or pan.
                    if (inkFinger && fingers < 2 && !transforming) continue
                    totalPan += event.calculatePan()
                    if (fingers >= 2 || (!selectingWords && view.zoom > 1f && totalPan.getDistance() > viewConfiguration.touchSlop)) transforming = true
                    if (transforming) {
                        val zoom = event.calculateZoom()
                        if (zoom.isFinite()) {
                            val next = pinchView(view, zoom, event.calculateCentroid(useCurrent = false) * view.zoom + view.pan,
                                event.calculatePan() * view.zoom, viewport)
                            view = next.first
                            if (next.second != 0f) onScroll(-next.second)
                        }
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
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
