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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.contentStyle
import com.ziaee.zabankhan.ui.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

private data class PdfRange(val page: Int, val first: Int, val last: Int, val words: List<PdfWord>) {
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
            TextButton(onClick = back) { Text(stringResource(Res.string.back)) }
            if (failed) Text(stringResource(Res.string.pdf_error)) else CircularProgressIndicator()
        }
        return
    }
    if (handle.pageCount == 0) {
        Column { TextButton(onClick = back) { Text(stringResource(Res.string.back)) }; Text(stringResource(Res.string.pdf_empty)) }
        return
    }
    com.ziaee.zabankhan.statistics.TrackActivity(vm.container, text.id, text.lang, com.ziaee.zabankhan.statistics.ActivityKind.READING)
    val list = rememberLazyListState(text.lastOffset.coerceIn(0, handle.pageCount - 1))
    val layout = remember(handle, text.lang) { PdfPageWords(path, handle, text.lang) }
    val vocab by remember(text.lang) { vm.container.vocab.observeAll(text.lang) }.collectAsState(emptyList())
    val highlights by remember(text.id) { vm.container.database.pdfHighlights().observeForText(text.id) }.collectAsState(emptyList())
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
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = back) { Text(stringResource(Res.string.back)) }
            Text("\u2066${page + 1} / ${handle.pageCount}\u2069", Modifier.padding(vertical = 12.dp), style = contentStyle("1 / 2"))
            FilterChip(selected = areaMode, enabled = !recognizing, onClick = { areaMode = !areaMode; range = null },
                label = { Text(stringResource(Res.string.pdf_select_area)) })
        }
        if (recognizing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Row {
            Text(stringResource(Res.string.pdf_error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { failed = false }) { Text(stringResource(Res.string.pdf_done)) }
        }
        range?.takeUnless { selecting }?.let { r ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(r.text)); range = null }) { Text(stringResource(Res.string.pdf_copy)) }
                TextButton(onClick = { selection = ReaderSelection(r.text, r.text, 0..0, true); range = null }) { Text(stringResource(Res.string.pdf_translate)) }
                TextButton(onClick = { selection = ReaderSelection(r.text, r.text, 0..0, true); range = null }) { Text(stringResource(Res.string.save_phrase)) }
                listOf("primary" to Res.string.highlight_primary, "secondary" to Res.string.highlight_secondary, "tertiary" to Res.string.highlight_tertiary).forEach { (key, label) ->
                    TextButton(onClick = { vm.action {
                        vm.container.database.pdfHighlights().insert(PdfHighlight(textId = text.id, page = r.page,
                            startWord = minOf(r.first, r.last), endWord = maxOf(r.first, r.last), colorKey = key))
                        range = null
                    } }) { Text(stringResource(label)) }
                }
                TextButton(onClick = { range = null }) { Text(stringResource(Res.string.cancel)) }
            }
        }
        LazyColumn(state = list, userScrollEnabled = !areaMode, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0 until handle.pageCount).toList(), key = { it }) { index ->
                PdfPage(handle, layout, index, areaMode, range?.takeIf { it.page == index }, highlights.filter { it.page == index },
                    onError = { failed = true },
                    onTap = { words, word ->
                        range = null
                        selection = ReaderSelection(pdfLookupWord(word.text, text.lang), sentenceAround(words, words.indexOf(word)), 0..0, false)
                    },
                    onRange = { first, last, words -> selecting = true; range = PdfRange(index, first, last, words) },
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
    selection?.let { DictionarySheet(vm, settings, text, it, vocab, emptyList(), dismiss = { selection = null }) }
    areaText?.let { recognized ->
        ModalBottomSheet(onDismissRequest = { areaText = null }) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.pdf_area_text), style = MaterialTheme.typography.titleLarge)
                Text(recognized.ifBlank { stringResource(Res.string.pdf_empty) }, style = contentStyle(recognized))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    selection: PdfRange?, highlights: List<PdfHighlight>, onError: () -> Unit,
    onTap: (List<PdfWord>, PdfWord) -> Unit, onRange: (Int, Int, List<PdfWord>) -> Unit, onRangeEnd: () -> Unit, onScroll: (Float) -> Unit,
    onArea: (ImageBitmap, PdfRect) -> Unit) {
    var aspect by remember(handle, index) { mutableFloatStateOf(1.414f) }
    var image by remember(handle, index) { mutableStateOf<ImageBitmap?>(null) }
    var words by remember(handle, index) { mutableStateOf<List<PdfWord>>(emptyList()) }
    var width by remember { mutableIntStateOf(0) }
    var view by remember(handle, index) { mutableStateOf(PdfView(1f, Offset.Zero)) }
    var viewport by remember { mutableStateOf(Size.Zero) }
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
    Box(Modifier.fillMaxWidth().aspectRatio(1f / aspect).onSizeChanged {
        width = it.width; viewport = Size(it.width.toFloat(), it.height.toFloat())
    }.graphicsLayer {
        scaleX = view.zoom; scaleY = view.zoom
        translationX = view.pan.x; translationY = view.pan.y
        transformOrigin = TransformOrigin(0f, 0f)
    }.background(MaterialTheme.colorScheme.surface)
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
                        onRange(first, first, words)
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val n = normalized(change.position)
                    hitTestWord(words, n.x, n.y, .02f)?.let { word ->
                        anchor?.let { onRange(it, words.indexOf(word), words) }
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
        .pointerInput(areaMode, viewport) {
            if (!areaMode) awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var transforming = false
                var totalPan = Offset.Zero
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val fingers = event.changes.count { it.pressed }
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
            selection?.selected?.forEach { rectangle(it.rect, primary.copy(alpha = .3f)) }
            box?.let { rectangle(it, primary.copy(alpha = .25f)) }
        }
    }
}
