package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.theme.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.srs.*
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.ui.contentStyle
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalClipboardManager

import com.ziaee.zabankhan.ui.srsState

private class ReadingViewport {
    var bounds by mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
    val offsets = mutableStateMapOf<Int, Int>()
}
private val LocalReadingViewport = compositionLocalOf<ReadingViewport?> { null }
private val LocalReaderLanguage = staticCompositionLocalOf { Languages.ENGLISH }
private val LocalReaderFindRevision = staticCompositionLocalOf { 0 }

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun ReadingScreen(vm: AppViewModel, settings: AppSettings, textId: Long, onBack: () -> Unit,
    onOpenVocab: () -> Unit = {}, initialQuery: String = "") {
    val document by remember(textId) { vm.container.texts.observeById(textId) }.collectAsState(null)
    var loaded by remember(textId) { mutableStateOf(false) }
    LaunchedEffect(textId) { loaded = vm.container.texts.getById(textId) == null }
    val text = document
    if (text == null) {
        Column { TopAppBar(title = { Text(stringResource(Res.string.reading_loading_title)) })
            if (loaded) Text(stringResource(Res.string.not_found)) else CircularProgressIndicator() }
        return
    }
    if (text.pdfPath != null) { com.ziaee.zabankhan.pdf.PdfReaderScreen(vm, settings, text, onBack); return }
    val lang = requireNotNull(Languages.byCode(text.lang))
    val parts = remember(text.body) { paragraphs(text.body) }
    val words by remember(text.lang) { vm.container.vocab.observeAll(text.lang) }.collectAsState(emptyList())
    val highlights by remember(textId) { vm.container.highlights.observeForText(textId) }.collectAsState(emptyList())
    val savedVocabStatuses = remember(words, lang) { words.associate { it.word to it.srsState().status() } }
    val dark = when (settings.themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val background = if (dark) ReadingBackground.DARK else
        ReadingBackground.entries.firstOrNull { it.name == settings.readingBackground } ?: ReadingBackground.SEPIA
    val palette = readingPaletteFor(background,
        HighlightColor.entries.firstOrNull { it.name == settings.readingHighlightColor } ?: HighlightColor.YELLOW)
    val fontScale = FontScale.entries.firstOrNull { it.name == settings.readerFontScale } ?: FontScale.MEDIUM
    val tts = remember { createTts() }
    val playback = remember(textId, parts, lang) { ReaderPlayback(tts, parts.map {
        SpokenParagraph(it.block.plainText, it.offset, it.sourceOffsets)
    }, lang, textId) }
    val speaking by playback.isSpeaking.collectAsState()
    val spokenSentence by playback.sentence.collectAsState()
    val currentParagraph by playback.currentParagraph.collectAsState()
    val audioStatus by playback.audioStatus.collectAsState()
    val paragraphAudioStatuses by playback.paragraphAudioStatuses.collectAsState()
    val synthesisProgress by playback.synthesisProgress.collectAsState()
    val activity = com.ziaee.zabankhan.statistics.TrackActivity(vm.container, textId, lang.code,
        com.ziaee.zabankhan.statistics.ActivityKind.READING, !speaking)
    val listState = key(textId, text.body) { rememberLazyListState(initialFirstVisibleItemIndex = paragraphIndexAt(parts, text.lastOffset)) }
    val readingViewport = remember { ReadingViewport() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val pageNavigation = PageNavigation.entries.firstOrNull { it.name == settings.pageNavigation } ?: PageNavigation.SCROLL
    var requestedPageAnchor by remember(textId, text.body) { mutableStateOf<ReadingAnchor?>(null) }
    var visiblePage by remember(textId, text.body) { mutableStateOf<ReadingPage?>(null) }
    var visiblePageAnchor by remember(textId, text.body) { mutableStateOf(ReadingAnchor(paragraphIndexAt(parts, text.lastOffset))) }
    var pageNavigationMenuOpen by remember { mutableStateOf(false) }
    var rate by remember { mutableFloatStateOf(settings.speechRate) }
    var dictionaryTarget by remember(textId) { mutableStateOf<ReaderSelection?>(null) }
    var selection by remember(textId) { mutableStateOf<ReaderSelection?>(null) }
    var clearSelectionTick by remember { mutableIntStateOf(0) }
    var textSelectionActive by remember { mutableStateOf(false) }
    var showHighlightPalette by remember { mutableStateOf(false) }
    var lastHighlightColorKey by remember(settings.lastHighlightColor) { mutableStateOf(settings.lastHighlightColor) }
    var processTextTarget by remember { mutableStateOf<String?>(null) }
    val selectionToolbarController = remember { SelectionToolbarController() }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var showSourceInfoSheet by remember { mutableStateOf(false) }
    var showContentsSheet by remember { mutableStateOf(false) }
    var showGestureGuide by remember { mutableStateOf(false) }
    var showTranslations by remember(textId) { mutableStateOf(false) }
    var findOpen by remember(textId, initialQuery) { mutableStateOf(initialQuery.isNotBlank()) }
    var findQuery by remember(textId, initialQuery) { mutableStateOf(initialQuery) }
    var findFocusRequest by remember { mutableStateOf(false) }
    var findIndex by remember(findQuery) { mutableIntStateOf(0) }
    var findRevision by remember { mutableIntStateOf(0) }
    var shadowRange by remember(textId) { mutableStateOf<IntRange?>(null) }
    val translations = remember(textId, text.body, settings.meaningLang) { mutableStateMapOf<Int, Pair<ChunkStatus, String?>>() }
    val shadowSentences = remember(parts, lang) { parts.flatMap { part ->
        splitSentences(part.block.plainText, lang).map { range ->
            part.block.plainText.substring(range) to
                (part.sourceOffsets.getOrElse(range.first) { part.offset }..part.sourceOffsets.getOrElse(range.last) { part.offset })
        }
    } }
    var voices by remember(tts, lang) { mutableStateOf(emptyList<TtsVoice>()) }
    LaunchedEffect(voiceMenuExpanded, tts, lang) {
        if (!voiceMenuExpanded) return@LaunchedEffect
        repeat(10) {
            voices = tts.voices(lang.code)
            if (voices.isNotEmpty()) return@LaunchedEffect
            delay(300)
        }
    }
    val targetLang = settings.meaningLang.takeUnless { it == text.lang } ?: Languages.PERSIAN.code
    val repository = vm.container.translations(settings)
    val chunks = parts.mapIndexed { index, part ->
        val status = when (if (index == currentParagraph && audioStatus != SpeechAudioStatus.IDLE) audioStatus else paragraphAudioStatuses[index] ?: SpeechAudioStatus.IDLE) {
            SpeechAudioStatus.PREPARING -> ChunkStatus.LOADING
            SpeechAudioStatus.ERROR -> ChunkStatus.ERROR
            SpeechAudioStatus.READY -> ChunkStatus.READY
            else -> ChunkStatus.PENDING
        }
        ChunkState(part.block, part.sourceOffsets, status,
            splitSentences(part.block.plainText, lang).map { range ->
                SentenceBoundary(part.block.plainText.substring(range), range.first.toDouble(), (range.last + 1 - range.first).toDouble())
            }, translations[index]?.second, translations[index]?.first ?: ChunkStatus.PENDING)
    }
    val findMatches = remember(parts, findQuery) { parts.flatMapIndexed { index, part ->
        findTextMatches(part.block.plainText, findQuery).map { index to it }
    } }
    val activeFind = findMatches.getOrNull(findIndex)
    val headings = chunks.mapIndexedNotNull { index, chunk ->
        if (chunk.block.type == BlockType.HEADER) index to chunk else null
    }
    var readingSection by remember(textId, text.body) { mutableIntStateOf(paragraphIndexAt(parts, text.lastOffset)) }
    LaunchedEffect(currentParagraph) { currentParagraph?.let { readingSection = it } }
    val spokenProgress = if (parts.isEmpty()) 0 to 0 else readingSection + 1 to parts.size
    LaunchedEffect(playback) { tts.speakingRange.collect { playback.rangeChanged(it) } }
    LaunchedEffect(playback) { playback.audioStatus.collect { playback.audioStatusChanged(it) } }
    LaunchedEffect(playback, rate, settings.ttsVoices[lang.code]) { playback.configureAudio(rate, settings.ttsVoices[lang.code]) }
    LaunchedEffect(textId, parts, pageNavigation) {
        if (pageNavigation != PageNavigation.SCROLL) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
            parts.getOrNull(index)?.let { vm.container.texts.savePosition(textId, it.offset) }
        }
    }
    LaunchedEffect(listState, activity) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { activity.interact() }
    }
    LaunchedEffect(text.pinned, playback) { playback.setAudioPinned(text.pinned) }
    DisposableEffect(playback) {
        // Both scroll and paged readers persist anchors as they change; an old playback
        // instance must not overwrite the reset anchor after a document body edit.
        onDispose { playback.close() }
    }
    LaunchedEffect(activeFind, findRevision) {
        activeFind?.let {
            if (pageNavigation == PageNavigation.SCROLL) listState.scrollToItem(it.first)
            else requestedPageAnchor = ReadingAnchor(it.first, it.second.first)
        }
    }
    LaunchedEffect(pageNavigation, parts) {
        if (pageNavigation == PageNavigation.SCROLL) {
            val anchor = requestedPageAnchor ?: visiblePageAnchor
            if (chunks.isNotEmpty()) listState.scrollToItem(anchor.chunkIndex.coerceIn(chunks.indices))
            requestedPageAnchor = null
        } else if (requestedPageAnchor == null) {
            requestedPageAnchor = ReadingAnchor(listState.firstVisibleItemIndex, 0)
        }
    }
    // The editor is the same editor used by import and library editing.
    var editTarget by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    editTarget?.let { (title, body, bodyEditable) ->
        com.ziaee.zabankhan.ui.AddTextEditor(
            learningLanguageName = lang.nativeName,
            initialTitle = title,
            initialBody = body,
            bodyEditable = bodyEditable,
            dialogTitle = stringResource(Res.string.edit_text_title),
            confirmLabel = stringResource(Res.string.action_save),
            onDismiss = { editTarget = null },
            onSave = { newTitle, newBody ->
                editTarget = null
                playback.stop()
                vm.action { vm.updateText(text, newTitle, newBody) }
            }
        )
    }
    var lastFollowedChunk by remember { mutableIntStateOf(-1) }
    LaunchedEffect(settings.readerAutoFollow, speaking, pageNavigation) { lastFollowedChunk = -1 }
    LaunchedEffect(currentParagraph, spokenSentence, speaking, settings.readerAutoFollow, pageNavigation) {
        val index = currentParagraph ?: return@LaunchedEffect
        val findHoldsScroll = findOpen && findQuery.isNotBlank() && !speaking
        if (!settings.readerAutoFollow || findHoldsScroll) return@LaunchedEffect
        if (pageNavigation != PageNavigation.SCROLL) {
            val part = parts.getOrNull(index)
            val local = spokenSentence?.first?.let { offset -> part?.sourceOffsets?.indexOfFirst { it >= offset }?.coerceAtLeast(0) } ?: 0
            requestedPageAnchor = ReadingAnchor(index, local)
            return@LaunchedEffect
        }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.find { it.index == index }
        if (index != lastFollowedChunk) {
            lastFollowedChunk = index
            val margin = (info.viewportEndOffset - info.viewportStartOffset) * .08f
            val fits = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset - margin
            if (!fits) listState.animateScrollToItem(index)
        }
        val current = listState.layoutInfo.visibleItemsInfo.find { it.index == index } ?: return@LaunchedEffect
        val part = parts.getOrNull(index) ?: return@LaunchedEffect
        val local = spokenSentence?.first?.let { offset -> part.sourceOffsets.indexOfFirst { it >= offset }.coerceAtLeast(0) } ?: 0
        followScrollDelta(current.offset.toFloat(), current.size.toFloat(), info.viewportStartOffset.toFloat(),
            info.viewportEndOffset.toFloat(), local.toFloat() / part.block.plainText.length.coerceAtLeast(1), false)?.let {
            listState.animateScrollBy(it)
        }
    }
    LaunchedEffect(showTranslations, repository, targetLang, pageNavigation, parts, translations) {
        if (!showTranslations) return@LaunchedEffect
        snapshotFlow { if (pageNavigation == PageNavigation.SCROLL) listState.layoutInfo.visibleItemsInfo.map { it.index } else visiblePage?.chunkIndices().orEmpty() }.distinctUntilChanged().debounce(300).collect { visible ->
            visible.forEach { index ->
                val part = parts.getOrNull(index) ?: return@forEach
                if (part.block.type != BlockType.IMAGE && translations[index] == null) {
                    translations[index] = ChunkStatus.LOADING to null
                    scope.launch {
                        val result = repository.translateText(part.block.spokenText, text.lang, targetLang)
                        translations[index] = (if (result.isSuccess) ChunkStatus.READY else ChunkStatus.ERROR) to result.getOrNull()
                    }
                }
            }
        }
    }
    fun clearSelection() { selection = null; showHighlightPalette = false; processTextTarget = null; clearSelectionTick++ }
    fun applyHighlight(key: String) {
        val selected = selection ?: return
        vm.action {
            overlappingHighlightIds(TextRange(selected.range.first, selected.range.last + 1), highlights).forEach { vm.container.highlights.delete(it) }
            vm.container.highlights.insert(HighlightEntry(textId = textId, startOffset = selected.range.first,
                endOffset = selected.range.last + 1, colorKey = key))
            vm.container.settings.setLastHighlightColor(key)
        }
        lastHighlightColorKey = key
        clearSelection()
    }
    fun select(word: String, sentence: String, range: TextRange, openDictionary: Boolean) {
        val around = sentenceAround(text.body, range.min, lang)
        val selected = ReaderSelection(word, sentence, range.min until range.max,
            tokenizeSurface(word, lang).size > 1, around)
        if (openDictionary) { playback.pause(); dictionaryTarget = selected; clearSelection() }
        else { selection = selected; showHighlightPalette = false; processTextTarget = null }
    }
    if (showGestureGuide) { GestureGuideScreen(lang) { showGestureGuide = false }; return }
    CompositionLocalProvider(LocalReaderLanguage provides lang, LocalReadingViewport provides readingViewport,
        LocalTextToolbar provides selectionToolbarController,
        LocalTextContextMenuToolbarProvider provides selectionToolbarController,
        LocalTextContextMenuDropdownProvider provides selectionToolbarController,
        LocalReaderFindRevision provides findRevision) {
        Scaffold(modifier = Modifier.onGloballyPositioned { selectionToolbarController.coordinates = it }, containerColor = palette.background, topBar = {
            Column {
                TopAppBar(title = {
                    Column(Modifier.fillMaxWidth()) {
                        // Titles taken from a Markdown first line may still carry heading markers.
                        Text(text.title.trimStart('#').trim(), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (spokenProgress.second > 0) Text(stringResource(Res.string.reading_position,
                            spokenProgress.first.coerceAtLeast(1), spokenProgress.second),
                            style = MaterialTheme.typography.labelSmall, color = palette.inkFaded)
                    }
                }, navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.accessibility_back)) }
                }, actions = {
                    IconButton(onClick = { showGestureGuide = true }) { Icon(Icons.Default.HelpOutline, stringResource(Res.string.gesture_guide_title)) }
                    if (headings.size >= 2) IconButton(onClick = { showContentsSheet = true }) {
                        Icon(Icons.Default.Toc, stringResource(Res.string.reading_contents))
                    }
                    IconButton(onClick = { showTranslations = !showTranslations }) {
                        Icon(Icons.Default.Translate, stringResource(Res.string.accessibility_toggle_translation),
                            tint = if (showTranslations) palette.accent else palette.inkFaded)
                    }
                    Box {
                        IconButton(onClick = { moreMenuExpanded = true }) { Icon(Icons.Default.MoreVert, stringResource(Res.string.reading_more_actions)) }
                        DropdownMenu(moreMenuExpanded, onDismissRequest = { moreMenuExpanded = false; pageNavigationMenuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(Res.string.reading_find)) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                                onClick = { moreMenuExpanded = false; findOpen = true; findFocusRequest = true })
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(Res.string.reading_font_scale_title), Modifier.weight(1f))
                                val scales = FontScale.entries
                                val index = scales.indexOf(fontScale)
                                IconButton(onClick = { vm.action { vm.container.settings.setReaderFontScale(scales[index - 1].name) } }, enabled = index > 0) {
                                    Text("A−", style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { vm.action { vm.container.settings.setReaderFontScale(scales[index + 1].name) } }, enabled = index < scales.lastIndex) {
                                    Text("A+", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            DropdownMenuItem(text = { Text(stringResource(Res.string.accessibility_vocabulary)) }, leadingIcon = { Icon(Icons.Default.MenuBook, null) },
                                onClick = { moreMenuExpanded = false; onOpenVocab() })
                            DropdownMenuItem(text = { Text(stringResource(Res.string.accessibility_select_voice)) }, leadingIcon = { Icon(Icons.Default.RecordVoiceOver, null) },
                                onClick = { moreMenuExpanded = false; voiceMenuExpanded = true })
                            DropdownMenuItem(text = { Text(stringResource(Res.string.accessibility_autoplay)) }, leadingIcon = {
                                Icon(if (settings.readerAutoFollow) Icons.Default.Check else Icons.Default.SwapVert, null,
                                    tint = if (settings.readerAutoFollow) palette.accent else LocalContentColor.current)
                            }, onClick = { moreMenuExpanded = false; vm.action { vm.container.settings.setReaderAutoFollow(!settings.readerAutoFollow) } })
                                    fun pageNavigationLabel(navigation: PageNavigation) = when (navigation) {
                                        PageNavigation.SCROLL -> Res.string.page_navigation_scroll
                                        PageNavigation.HORIZONTAL -> Res.string.page_navigation_horizontal
                                        PageNavigation.VERTICAL -> Res.string.page_navigation_vertical
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(stringResource(Res.string.page_navigation_title))
                                                Text(
                                                    stringResource(pageNavigationLabel(pageNavigation)),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = palette.inkFaded
                                                )
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) },
                                        trailingIcon = {
                                            Icon(
                                                if (pageNavigationMenuOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = { pageNavigationMenuOpen = !pageNavigationMenuOpen }
                                    )
                                    if (pageNavigationMenuOpen) PageNavigation.entries.forEach { navigation ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(pageNavigationLabel(navigation))) },
                                            modifier = Modifier.padding(start = 24.dp),
                                            leadingIcon = {
                                                if (pageNavigation == navigation) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            },
                                            onClick = {
                                                moreMenuExpanded = false
                                                pageNavigationMenuOpen = false
                                                requestedPageAnchor = if (pageNavigation == PageNavigation.SCROLL) {
                                                    ReadingAnchor(listState.firstVisibleItemIndex, 0)
                                                } else visiblePageAnchor
                                                vm.action { vm.container.settings.setPageNavigation(navigation.name) }
                                            }
                                        )
                                    }
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { moreMenuExpanded = false; editTarget = Triple(text.title, text.body, true) }
                            )
                            DropdownMenuItem(text = { Text(stringResource(if (text.pinned) Res.string.settings_unpin_audio else Res.string.settings_pin_audio)) }, leadingIcon = {
                                Box(Modifier.size(24.dp)) {
                                    Icon(Icons.Default.PushPin, null)
                                    if (text.pinned) Icon(Icons.Default.Check, null, tint = palette.accent, modifier = Modifier.size(12.dp).align(Alignment.BottomEnd))
                                }
                            }, onClick = { moreMenuExpanded = false; vm.action { vm.container.texts.setPinned(textId, !text.pinned) } })
                            // Full-document synthesis is available with the cached online engine.
                            if (settings.voiceEngine == "online") DropdownMenuItem(text = { Text(stringResource(Res.string.settings_synthesize_full_document)) },
                                leadingIcon = { Icon(Icons.Default.CloudDownload, null) }, enabled = synthesisProgress == null,
                                onClick = { moreMenuExpanded = false; scope.launch { playback.prepareAll(rate, settings.ttsVoices[lang.code]) } })
                            DropdownMenuItem(text = { Text(stringResource(Res.string.accessibility_source_info)) }, leadingIcon = { Icon(Icons.Default.Info, null) },
                                onClick = { moreMenuExpanded = false; showSourceInfoSheet = true })
                        }
                        DropdownMenu(voiceMenuExpanded, onDismissRequest = { voiceMenuExpanded = false }) {
                            voices.forEach { voice -> DropdownMenuItem(text = { Text(voice.name) },
                                leadingIcon = { if (settings.ttsVoices[lang.code] == voice.id) Icon(Icons.Default.Check, null) },
                                onClick = {
                                    voiceMenuExpanded = false
                                    vm.action { vm.container.settings.setTtsVoice(lang.code, voice.id) }
                                    if (speaking) playback.startAt(spokenSentence?.first ?: parts.getOrNull(listState.firstVisibleItemIndex)?.offset ?: 0,
                                        rate, voice.id, settings.speechRepeat)
                                }) }
                        }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background,
                    titleContentColor = palette.ink, navigationIconContentColor = palette.ink, actionIconContentColor = palette.ink))
                if (spokenProgress.second > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    LinearProgressIndicator(progress = { spokenProgress.first.toFloat() / spokenProgress.second },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = palette.accent, trackColor = palette.divider)
                }
                if (findOpen) ReadingFindBar(findQuery, { findQuery = it }, if (findMatches.isEmpty()) 0 else findIndex + 1,
                    findMatches.size, { if (findMatches.isNotEmpty()) { findIndex = (findIndex - 1 + findMatches.size) % findMatches.size; findRevision++ } },
                    { if (findMatches.isNotEmpty()) { findIndex = (findIndex + 1) % findMatches.size; findRevision++ } },
                    { findOpen = false; findQuery = "" }, palette, findFocusRequest, { findFocusRequest = false })
            }
        }, bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                shadowRange?.let { range ->
                    Surface(color = palette.background, tonalElevation = 3.dp) {
                        ReaderShadowingPanel(vm.container, settings, lang, textId, shadowSentences.map { it.first },
                            shadowSentences.indexOfFirst { it.second.last >= range.first }.coerceAtLeast(0), palette, { shadowRange = null })
                    }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    PlaybackControls(palette, speaking, audioStatus == SpeechAudioStatus.PREPARING || synthesisProgress != null, rate,
                        previous = { playback.previousSentence(if (pageNavigation == PageNavigation.SCROLL) listState.firstVisibleItemIndex else visiblePageAnchor.chunkIndex, rate, settings.ttsVoices[lang.code], settings.speechRepeat) },
                        next = { playback.nextSentence(if (pageNavigation == PageNavigation.SCROLL) listState.firstVisibleItemIndex else visiblePageAnchor.chunkIndex, rate, settings.ttsVoices[lang.code], settings.speechRepeat) },
                        seek = { playback.seekBy(it) }, canSeek = settings.voiceEngine == "online",
                        toggle = { if (speaking) playback.pause() else { lastFollowedChunk = -1; playback.resume(if (pageNavigation == PageNavigation.SCROLL) listState.firstVisibleItemIndex else visiblePageAnchor.chunkIndex, rate, settings.ttsVoices[lang.code], settings.speechRepeat) } },
                        changeRate = { rate = it; vm.action { vm.container.settings.setSpeechRate(it) }
                            if (speaking) playback.startAt(spokenSentence?.first ?: parts.getOrNull(listState.firstVisibleItemIndex)?.offset ?: 0,
                                rate, settings.ttsVoices[lang.code], settings.speechRepeat) },
                        shadowingEnabled = shadowRange != null, onToggleShadowing = {
                            val range = spokenSentence ?: parts.getOrNull(listState.firstVisibleItemIndex)?.let { sentenceAround(text.body, it.offset, lang) }
                            playback.pause(); shadowRange = if (shadowRange != null) null else range
                        })
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).onGloballyPositioned { readingViewport.bounds = it.boundsInWindow() }) {
                if (pageNavigation == PageNavigation.SCROLL) LazyColumn(state = listState,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.TopCenter)
                        .onGloballyPositioned { readingViewport.bounds = it.boundsInWindow() },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)) {
                    itemsIndexed(chunks, key = { index, _ -> parts[index].offset }) { index, chunk ->
                        val active = currentParagraph == index
                        val localPosition = if (active) spokenSentence?.first?.let { offset ->
                            chunk.sourceOffsets.indexOfFirst { it >= offset }.coerceAtLeast(0).toLong()
                        } ?: -1L else -1L
                        ChunkParagraph(chunk, active, localPosition, showTranslations, savedVocabStatuses, highlights,
                            parts[index].offset, palette, fontScale.multiplier,
                            findMatches.filter { it.first == index }.map { it.second }, activeFind?.takeIf { it.first == index }?.second,
                            onRetry = { playback.start(index, rate, settings.ttsVoices[lang.code], settings.speechRepeat) },
                            onWordLookup = { word, sentence, range -> select(word, sentence, range, true) },
                            onPhraseSelected = { phrase, sentence, range -> select(phrase, sentence, range, false) },
                            clearSelectionSignal = clearSelectionTick, onSelectionActiveChange = { textSelectionActive = it })
                        val next = chunks.getOrNull(index + 1)
                        Spacer(Modifier.height(when {
                            chunk.block.type == BlockType.IMAGE -> 16.dp
                            chunk.block.type == BlockType.LIST_ITEM && next?.block?.type == BlockType.LIST_ITEM -> 6.dp
                            chunk.block.type == BlockType.HEADER -> 32.dp
                            else -> 24.dp
                        }))
                    }
                }
                else BoxWithConstraints(Modifier.fillMaxSize()) {
                    val pageDensity = LocalDensity.current
                    val readerWidthPx = minOf(
                        constraints.maxWidth,
                        with(pageDensity) { ZabankhanDesign.sizes.readerMeasure.roundToPx() }
                    )
                    val reservedVerticalPx = with(pageDensity) { 56.dp.roundToPx() }
                    val pageHeightPx = (constraints.maxHeight - reservedVerticalPx).coerceAtLeast(1)
                    val pages by rememberReadingPages(
                        chunks = chunks,
                        widthPx = readerWidthPx,
                        pageHeightPx = pageHeightPx,
                        fontScale = fontScale.multiplier,
                        showTranslations = showTranslations,
                        density = pageDensity,
                        geometryThemeKey = settings.readingBackground
                    )
                    val bookDirection = if (dominantReadingDirectionIsRtl(chunks)) {
                        LayoutDirection.Rtl
                    } else LayoutDirection.Ltr
                    PagedReadingContent(
                        pages = pages,
                        navigation = pageNavigation,
                        bookDirection = bookDirection,
                        background = palette.background,
                        inkFaded = palette.inkFaded,
                        requestedAnchor = requestedPageAnchor,
                        onAnchorConsumed = { requestedPageAnchor = null },
                        userScrollEnabled = !textSelectionActive,
                        onVisiblePage = { page ->
                            visiblePage = page
                            page.fragments.firstOrNull()?.let {
                                visiblePageAnchor = ReadingAnchor(it.chunkIndex, it.startOffset)
                            }
                            activity.interact()
                            parts.getOrNull(visiblePageAnchor.chunkIndex)?.let { part ->
                                val offset = part.sourceOffsets.getOrElse(visiblePageAnchor.characterOffset) { part.offset }
                                vm.action { vm.container.texts.savePosition(textId, offset) }
                            }
                        }
                    ) { page ->
                        val scrolling = page.fragments.any { it.atomic || it.internallyScrollable }
                        Column(
                            Modifier
                                .fillMaxSize()
                                .then(if (scrolling) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                                .padding(top = 20.dp, bottom = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                Modifier
                                    .widthIn(max = ZabankhanDesign.sizes.readerMeasure)
                                    .fillMaxWidth()
                                    .padding(horizontal = ZabankhanDesign.spacing.medium)
                            ) {
                                page.fragments.forEach { fragment ->
                                    val chunk = chunks.getOrNull(fragment.chunkIndex) ?: return@forEach
                                    val start = fragment.startOffset
                                    val end = fragment.endOffset
                                    fun shifted(range: IntRange): IntRange? {
                                        val clippedStart = maxOf(range.first, start)
                                        val clippedEnd = minOf(range.last + 1, end)
                                        return if (clippedStart < clippedEnd) {
                                            (clippedStart - start)..(clippedEnd - start - 1)
                                        } else null
                                    }
                                    val pageFindRanges = if (findOpen) {
                                        findMatches.filter { it.first == fragment.chunkIndex }.map { it.second }.mapNotNull(::shifted)
                                    } else emptyList()
                                    val pageActiveFind = activeFind?.takeIf { findOpen && it.first == fragment.chunkIndex }?.second?.let(::shifted)
                                    ChunkParagraph(
                                        chunk = chunk,
                                        isCurrentChunk = fragment.chunkIndex == currentParagraph,
                                        currentPositionMs = if (fragment.chunkIndex == currentParagraph) spokenSentence?.first?.let { offset ->
                                            chunk.sourceOffsets.indexOfFirst { it >= offset }.coerceAtLeast(0).toLong()
                                        } ?: -1L else -1L,
                                        showTranslation = showTranslations && fragment.showTranslation,
                                        savedVocabStatuses = savedVocabStatuses,
                                        highlights = highlights,
                                        documentStartOffset = chunk.sourceOffsets.getOrElse(start) { parts[fragment.chunkIndex].offset + start },
                                        palette = palette,
                                        fontScale = fontScale.multiplier,
                                        findRanges = pageFindRanges,
                                        activeFindRange = pageActiveFind,
                                        onRetry = { playback.start(fragment.chunkIndex, rate, settings.ttsVoices[lang.code], settings.speechRepeat) },
                                        onWordLookup = { word, sentence, range -> select(word, sentence, range, true) },
                                        onPhraseSelected = { phrase, sentence, range -> select(phrase, sentence, range, false) },
                                        clearSelectionSignal = clearSelectionTick,
                                        onSelectionActiveChange = { textSelectionActive = it },
                                        textStartOffset = start,
                                        textEndOffset = end
                                    )
                                    if (!fragment.atomic && end == chunk.readingDisplayText().length) {
                                        val spacing = when {
                                            chunk.block.type == BlockType.LIST_ITEM &&
                                                chunks.getOrNull(fragment.chunkIndex + 1)?.block?.type == BlockType.LIST_ITEM -> 6.dp
                                            chunk.block.type == BlockType.HEADER -> ZabankhanDesign.spacing.large
                                            else -> ZabankhanDesign.spacing.medium
                                        }
                                        Spacer(Modifier.height(spacing))
                                    }
                                }
                            }
                        }
                    }
                }
                val visibleIndex = if (pageNavigation == PageNavigation.SCROLL) listState.firstVisibleItemIndex else visiblePageAnchor.chunkIndex
                val pageContainsActive = if (pageNavigation == PageNavigation.SCROLL) currentParagraph == visibleIndex else visiblePage?.fragments?.any { fragment ->
                    fragment.chunkIndex == currentParagraph && spokenSentence?.first?.let { offset ->
                        val local = parts[fragment.chunkIndex].sourceOffsets.indexOfFirst { it >= offset }
                        local >= fragment.startOffset && local < fragment.endOffset
                    } != false
                } == true
                if (parts.isNotEmpty() && !pageContainsActive) FilledTonalButton(
                    onClick = { playback.startAt(if (pageNavigation == PageNavigation.SCROLL) readingViewport.offsets.values.minOrNull() ?: parts.getOrNull(visibleIndex)?.offset ?: 0
                        else parts.getOrNull(visibleIndex)?.let { part -> part.sourceOffsets.getOrElse(visiblePageAnchor.characterOffset) { part.offset } } ?: 0,
                        rate, settings.ttsVoices[lang.code], settings.speechRepeat) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp, end = 16.dp).height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text(stringResource(if (pageNavigation == PageNavigation.SCROLL) Res.string.reading_play_from_here else Res.string.reading_play_from_page), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        val selected = selection
        val toolbarRect = selectionToolbarController.rect
        if (selected != null && toolbarRect != null && selectionToolbarController.status == TextToolbarStatus.Shown) {
            val ids = overlappingHighlightIds(TextRange(selected.range.first, selected.range.last + 1), highlights)
            Popup(popupPositionProvider = SelectionRectPositionProvider(toolbarRect, with(density) { 8.dp.toPx() }),
                properties = androidx.compose.ui.window.PopupProperties(dismissOnClickOutside = false), onDismissRequest = ::clearSelection) {
                val delete: (() -> Unit)? = ids.takeIf { it.isNotEmpty() }?.let { found -> {
                    vm.action { found.forEach { vm.container.highlights.delete(it) } }; clearSelection()
                } }
                when {
                    showHighlightPalette -> HighlightPalettePopupContent({ applyHighlight(it) }, { showHighlightPalette = false })
                    processTextTarget != null -> {
                        val apps = rememberReaderProcessTextActions(processTextTarget!!)
                        ProcessTextAppsPopupContent(apps, { it.invoke(); clearSelection() }, { processTextTarget = null })
                    }
                    !selected.sentenceActions -> DefineToolbarContent(
                        onDefine = { playback.pause(); dictionaryTarget = selected; clearSelection() },
                        onHighlight = { applyHighlight(lastHighlightColorKey) }, onChooseHighlightColor = { showHighlightPalette = true },
                        onDeleteHighlight = delete, onMore = { processTextTarget = selected.word })
                    else -> SelectionToolbarContent(
                        onCopy = { clipboard.setText(AnnotatedString(selected.word)); clearSelection() },
                        onSave = { playback.pause(); dictionaryTarget = selected; clearSelection() },
                        onListen = { playback.sentenceOnly(selected.word, selected.range.first, rate, settings.ttsVoices[lang.code]); clearSelection() },
                        onHighlight = { applyHighlight(lastHighlightColorKey) }, onChooseHighlightColor = { showHighlightPalette = true },
                        onDeleteHighlight = delete, onMore = { processTextTarget = selected.word })
                }
            }
        }
    }
    dictionaryTarget?.let { target -> DictionarySheet(vm, settings, text, target, words, highlights, { dictionaryTarget = null }) }
    if (showContentsSheet) ContentsSheet(headings, currentParagraph ?: listState.firstVisibleItemIndex, chunks.size, palette,
        { index -> if (pageNavigation == PageNavigation.SCROLL) scope.launch { listState.scrollToItem(index) } else requestedPageAnchor = ReadingAnchor(index); showContentsSheet = false }, { showContentsSheet = false })
    if (showSourceInfoSheet) SourceInfoSheet(text, chunks, currentParagraph ?: if (pageNavigation == PageNavigation.SCROLL) listState.firstVisibleItemIndex else visiblePageAnchor.chunkIndex, palette) { showSourceInfoSheet = false }

}

private val SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f)
@Composable
private fun ChunkParagraph(
    chunk: ChunkState,
    isCurrentChunk: Boolean,
    currentPositionMs: Long,
    showTranslation: Boolean,
    savedVocabStatuses: Map<String, VocabStatus>,
    highlights: List<HighlightEntry>,
    documentStartOffset: Int,
    palette: ReadingPalette,
    fontScale: Float,
    findRanges: List<IntRange>,
    activeFindRange: IntRange?,
    onRetry: () -> Unit,
    onWordLookup: (word: String, sentence: String, range: TextRange) -> Unit,
    onPhraseSelected: (phrase: String, sentence: String, range: TextRange) -> Unit,
    clearSelectionSignal: Int,
    onSelectionActiveChange: (Boolean) -> Unit = {},
    textStartOffset: Int = 0,
    textEndOffset: Int? = null
) {
    // Not-yet-synthesized chunks have no sentence timings, but must still render with
    // their Markdown structure (headings, lists, emphasis) -- so show the whole block as
    // one untimed sentence through the same path as READY chunks.
    // A block whose audio failed still shows its text the same way.
    val unsynthesized = chunk.status == ChunkStatus.PENDING || chunk.status == ChunkStatus.LOADING
    val failed = chunk.status == ChunkStatus.ERROR
    val chunk = if ((unsynthesized || failed) && chunk.sentences.isEmpty()) {
        chunk.copy(sentences = listOf(SentenceBoundary(text = chunk.text, offsetMs = 0.0, durationMs = 0.0)))
    } else chunk

    if (chunk.block.type == BlockType.IMAGE) {
        EpubImage(block = chunk.block, palette = palette, fontScale = fontScale)
        return
    }

    // Each block takes the direction of its own text, not the surrounding UI's:
    // French stays left-to-right under a Persian UI, and Persian blocks in a
    // document lay out right-to-left (alignment and list bullet included).
    val blockDirection = if (isRtlText(chunk.text)) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
        Column(Modifier.fillMaxWidth()) {
            when (chunk.block.type) {
                BlockType.HEADER -> Column {
                    if (textStartOffset == 0) {
                        Spacer(Modifier.height(if (chunk.block.headerLevel == 1) 12.dp else 4.dp))
                    }
                    SentenceFlowText(
                        chunk = chunk,
                        isCurrentChunk = isCurrentChunk,
                        currentPositionMs = currentPositionMs,
                        fontSize = headerFontSize(chunk.block.headerLevel, fontScale),
                        lineHeight = headerLineHeight(chunk.block.headerLevel, fontScale),
                        fontWeight = FontWeight.SemiBold,
                        color = palette.ink,
                        palette = palette,
                        savedVocabStatuses = savedVocabStatuses,
                        highlights = highlights,
                        documentStartOffset = documentStartOffset,
                        findRanges = findRanges,
                        activeFindRange = activeFindRange,
                        onWordLookup = onWordLookup,
                        onPhraseSelected = onPhraseSelected,
                        clearSelectionSignal = clearSelectionSignal,
                        onSelectionActiveChange = onSelectionActiveChange,
                        justify = false,
                        textStartOffset = textStartOffset,
                        textEndOffset = textEndOffset
                    )
                    if (chunk.block.headerLevel == 1 &&
                        (textEndOffset == null || textEndOffset >= chunk.readingDisplayText().length)
                    ) {
                        Box(
                            Modifier.padding(top = 12.dp).width(48.dp).height(2.dp)
                                .clip(RoundedCornerShape(1.dp)).background(palette.accent)
                        )
                    }
                }
                BlockType.LIST_ITEM -> Row {
                    Text(
                        if (textStartOffset == 0) "•  " else "   ",
                        fontSize = (19f * fontScale).sp,
                        lineHeight = (29f * fontScale).sp,
                        color = palette.accent
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        SentenceFlowText(
                            chunk = chunk,
                            isCurrentChunk = isCurrentChunk,
                            currentPositionMs = currentPositionMs,
                            fontSize = (19f * fontScale).sp,
                            lineHeight = (29f * fontScale).sp,
                            fontWeight = null,
                            color = palette.ink,
                            palette = palette,
                            savedVocabStatuses = savedVocabStatuses,
                            highlights = highlights,
                            documentStartOffset = documentStartOffset,
                            findRanges = findRanges,
                            activeFindRange = activeFindRange,
                            onWordLookup = onWordLookup,
                            onPhraseSelected = onPhraseSelected,
                            clearSelectionSignal = clearSelectionSignal,
                            onSelectionActiveChange = onSelectionActiveChange,
                            justify = false,
                            textStartOffset = textStartOffset,
                            textEndOffset = textEndOffset
                        )
                    }
                }
                BlockType.PARAGRAPH -> SentenceFlowText(
                    chunk = chunk,
                    isCurrentChunk = isCurrentChunk,
                    currentPositionMs = currentPositionMs,
                    fontSize = (19f * fontScale).sp,
                    lineHeight = (31f * fontScale).sp,
                    fontWeight = null,
                    color = palette.ink,
                    palette = palette,
                    savedVocabStatuses = savedVocabStatuses,
                    highlights = highlights,
                    documentStartOffset = documentStartOffset,
                    findRanges = findRanges,
                    activeFindRange = activeFindRange,
                    onWordLookup = onWordLookup,
                    onPhraseSelected = onPhraseSelected,
                    clearSelectionSignal = clearSelectionSignal,
                    onSelectionActiveChange = onSelectionActiveChange,
                    justify = true,
                    textStartOffset = textStartOffset,
                    textEndOffset = textEndOffset
                )
                BlockType.TABLE -> TableBlock(
                    chunk = chunk,
                    fontScale = fontScale,
                    palette = palette,
                    savedVocabStatuses = savedVocabStatuses,
                    highlights = highlights,
                    documentStartOffset = documentStartOffset,
                    findRanges = findRanges,
                    activeFindRange = activeFindRange,
                    onWordLookup = onWordLookup,
                    onPhraseSelected = onPhraseSelected,
                    clearSelectionSignal = clearSelectionSignal,
                    onSelectionActiveChange = onSelectionActiveChange
                )
                BlockType.IMAGE -> Unit
            }

            if (chunk.status == ChunkStatus.LOADING) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = palette.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.audio_preparing), fontSize = (12f * fontScale).sp, color = palette.inkFaded)
                }
            }
            if (failed) {
                // Just a small muted mark instead of an error message; tap to retry.
                Icon(
                    Icons.Default.VolumeOff,
                    contentDescription = stringResource(Res.string.action_retry),
                    tint = palette.inkFaded.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp).size(16.dp).clickable(onClick = onRetry)
                )
            }

            if (showTranslation) {
                Spacer(Modifier.height(6.dp))
                when (chunk.translationStatus) {
                    ChunkStatus.READY -> chunk.translation?.let {
                        // The block is forced LTR for the French text; let the translation
                        // take its own direction so Persian lays out right-to-left.
                        Text(
                            it,
                            fontSize = (14f * fontScale).sp,
                            lineHeight = (21f * fontScale).sp,
                            fontStyle = FontStyle.Italic,
                            color = palette.inkFaded,
                            style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ChunkStatus.LOADING -> Text(
                        stringResource(Res.string.translation_loading),
                        fontSize = (13f * fontScale).sp,
                        fontStyle = FontStyle.Italic,
                        color = palette.inkFaded
                    )
                    ChunkStatus.ERROR -> Text(
                        stringResource(Res.string.error_translation_unavailable),
                        fontSize = (13f * fontScale).sp,
                        fontStyle = FontStyle.Italic,
                        color = palette.inkFaded
                    )
                    ChunkStatus.PENDING -> {}
                }
            }
        }
    }
}

@Composable
private fun EpubImage(
    block: ParsedBlock,
    palette: ReadingPalette,
    fontScale: Float
) {
    val ref = block.imageRef ?: return
    val bitmap = rememberReaderImage(ref) ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Image(bitmap, block.imageAlt.takeIf { it.isNotBlank() },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                .border(1.dp, palette.divider, MaterialTheme.shapes.medium))
        block.imageAlt.takeIf { it.isNotBlank() }?.let { alt ->
            Text(alt, Modifier.padding(top = 8.dp), fontSize = (13f * fontScale).sp,
                lineHeight = (19f * fontScale).sp, color = palette.inkFaded, textAlign = TextAlign.Center)
        }
    }
}

private fun headerFontSize(level: Int, fontScale: Float): TextUnit = (when (level) {
    1 -> 30f
    2 -> 25f
    3 -> 22f
    4 -> 20f
    5 -> 19f
    else -> 18f
} * fontScale).sp

private fun headerLineHeight(level: Int, fontScale: Float): TextUnit = (when (level) {
    1 -> 39f
    2 -> 33f
    3 -> 29f
    4 -> 27f
    5 -> 26f
    else -> 25f
} * fontScale).sp

/**
 * The shared "flowing, sentence-highlighted, tappable, long-pressable" text
 * renderer used for every block type (paragraph, heading, list item). The
 * displayed string is the exact Markdown-stripped document text. Sentence
 * boundaries are mapped onto that source for playback highlighting, while
 * paged callers can render a line-aligned [textStartOffset, textEndOffset)
 * slice without changing selection or document offsets.
 *
 * A single tap seeks/continues playback from the tapped sentence (Phase 1
 * behaviour, unchanged). A long-press on a word instead opens the
 * dictionary for that word -- the two never fire for the same gesture, so
 * they cannot conflict per the design doc's acceptance criterion.
 */
/**
 * A Markdown pipe table, Notion-style: each column takes its text's natural width up to a
 * cap (cells wrap past it), and a table wider than the screen scrolls sideways instead of
 * squeezing its columns. Each cell is its own [SentenceFlowText] over its slice of the
 * block text, so word lookup, highlights and find keep their document offsets.
 */
@Composable
private fun TableBlock(
    chunk: ChunkState,
    fontScale: Float,
    palette: ReadingPalette,
    savedVocabStatuses: Map<String, VocabStatus>,
    highlights: List<HighlightEntry>,
    documentStartOffset: Int,
    findRanges: List<IntRange>,
    activeFindRange: IntRange?,
    onWordLookup: (word: String, sentence: String, range: TextRange) -> Unit,
    onPhraseSelected: (phrase: String, sentence: String, range: TextRange) -> Unit,
    clearSelectionSignal: Int,
    onSelectionActiveChange: (Boolean) -> Unit
) {
    val block = chunk.block
    val rows = block.tableRows
    if (rows.isEmpty()) return
    val columns = rows.first().size
    val fontSize = (16f * fontScale).sp
    val lineHeight = (24f * fontScale).sp
    val cellPadding = 10.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val headerFontFamily = readerHeadlineFamily(chunk.text)
    // Clicking a cell plays the table from its start: the TTS sentences don't map to cells.
    val tableSentence = chunk.sentences.firstOrNull()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth
        val columnWidths = remember(block, fontScale, available) {
            val natural = (0 until columns).map { col ->
                val widest = rows.withIndex().maxOf { (rowIndex, row) ->
                    val cell = row[col]
                    measurer.measure(
                        block.plainText.substring(cell.start, cell.end),
                        TextStyle(
                            fontSize = fontSize,
                            letterSpacing = 0.1.sp,
                            // Same font SentenceFlowText draws the bold header row with.
                            fontWeight = if (rowIndex == 0) FontWeight.SemiBold else null,
                            fontFamily = if (rowIndex == 0) headerFontFamily else null
                        )
                    ).size.width
                }
                with(density) { widest.toDp() } + cellPadding * 2 + 2.dp
            }
            val capped = natural.map { it.coerceIn(72.dp, 260.dp) }
            // Room left on screen goes to the columns the cap cut short, so a narrow table
            // with one long column wraps less instead of leaving empty space.
            var spare = available - capped.fold(0.dp) { acc, w -> acc + w }
            capped.mapIndexed { i, w ->
                val grow = minOf(natural[i] - w, spare).coerceAtLeast(0.dp)
                spare -= grow
                w + grow
            }
        }
        val lineColor = palette.divider
        Column(
            Modifier
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(vertical = 6.dp)
                .border(1.dp, lineColor, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
        ) {
            rows.forEachIndexed { rowIndex, row ->
                val header = rowIndex == 0
                Row(
                    Modifier
                        .height(IntrinsicSize.Min)
                        .then(if (header) Modifier.background(palette.accent.copy(alpha = 0.08f)) else Modifier)
                ) {
                    row.forEachIndexed { col, cell ->
                        if (col > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(lineColor))
                        val cellText = block.plainText.substring(cell.start, cell.end)
                        val cellBlock = ParsedBlock(
                            type = BlockType.PARAGRAPH,
                            plainText = cellText,
                            spokenText = cellText,
                            emphasisSpans = block.emphasisSpans
                                .filter { it.start >= cell.start && it.end <= cell.end }
                                .map { it.copy(start = it.start - cell.start, end = it.end - cell.start) }
                        )
                        val cellChunk = chunk.copy(
                            block = cellBlock,
                            sourceOffsets = chunk.sourceOffsets.drop(cell.start).take(cell.end - cell.start),
                            sentences = listOf(SentenceBoundary(text = cellText, offsetMs = 0.0, durationMs = 0.0))
                        )
                        Box(Modifier.width(columnWidths[col] - if (col > 0) 1.dp else 0.dp).padding(cellPadding)) {
                            SentenceFlowText(
                                chunk = cellChunk,
                                isCurrentChunk = false,
                                currentPositionMs = 0L,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                fontWeight = if (header) FontWeight.SemiBold else null,
                                color = palette.ink,
                                palette = palette,
                                savedVocabStatuses = savedVocabStatuses,
                                highlights = highlights,
                                documentStartOffset = documentStartOffset + cell.start,
                                findRanges = findRanges
                                    .filter { it.first >= cell.start && it.last < cell.end }
                                    .map { (it.first - cell.start)..(it.last - cell.start) },
                                activeFindRange = activeFindRange
                                    ?.takeIf { it.first >= cell.start && it.last < cell.end }
                                    ?.let { (it.first - cell.start)..(it.last - cell.start) },
                                onWordLookup = onWordLookup,
                                onPhraseSelected = onPhraseSelected,
                                clearSelectionSignal = clearSelectionSignal,
                                onSelectionActiveChange = onSelectionActiveChange,
                                justify = false
                            )
                        }
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    Box(Modifier.width(columnWidths.fold(0.dp) { acc, w -> acc + w }).height(1.dp).background(lineColor))
                }
            }
        }
    }
}

@Composable
private fun SentenceFlowText(
    chunk: ChunkState,
    isCurrentChunk: Boolean,
    currentPositionMs: Long,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight?,
    color: Color,
    palette: ReadingPalette,
    savedVocabStatuses: Map<String, VocabStatus>,
    highlights: List<HighlightEntry>,
    documentStartOffset: Int,
    findRanges: List<IntRange>,
    activeFindRange: IntRange?,
    onWordLookup: (word: String, sentence: String, range: TextRange) -> Unit,
    onPhraseSelected: (phrase: String, sentence: String, range: TextRange) -> Unit,
    clearSelectionSignal: Int,
    onSelectionActiveChange: (Boolean) -> Unit = {},
    justify: Boolean,
    textStartOffset: Int = 0,
    textEndOffset: Int? = null
) {
    val readerLanguage = LocalReaderLanguage.current
    val vocabHighlighter = remember(savedVocabStatuses, readerLanguage) {
        VocabHighlighter(savedVocabStatuses.map { SavedVocab(it.key, it.value) }, readerLanguage)
    }
    // Pick colors for the reading page's own background, not the system
    // theme: a light sepia page can sit under a dark system theme.
    val darkPage = palette.ink.luminance() > 0.5f
    val vocabStyles = VocabStatus.entries.associateWith { status ->
        SpanStyle(
            // Keep the page's high-contrast ink over the status tint. The
            // status hue remains visible in the background and underline.
            color = palette.ink,
            background = status.containerColor(darkPage).copy(
                alpha = if (status == VocabStatus.LEARNED) 0.12f else 0.7f
            ),
            textDecoration = if (status == VocabStatus.LEARNED) null else TextDecoration.Underline
        )
    }
    val sourceText = remember(chunk.sentences, chunk.block.plainText) {
        chunk.block.plainText
    }
    val sliceStart = textStartOffset.coerceIn(0, sourceText.length)
    val sliceEnd = (textEndOffset ?: sourceText.length).coerceIn(sliceStart, sourceText.length)
    val sentenceSourceRanges = remember(chunk.sentences, sourceText) {
        var cursor = 0
        chunk.sentences.map { sentence ->
            val start = sourceText.indexOf(sentence.text, cursor).takeIf { it >= 0 } ?: cursor
            val end = (start + sentence.text.length).coerceAtMost(sourceText.length)
            cursor = end
            start until end
        }
    }
    val ranges = remember(chunk.sentences, sliceStart, sliceEnd) { mutableListOf<IntRange>() }
    val visibleSentences = remember(chunk.sentences, sentenceSourceRanges, sliceStart, sliceEnd) {
        chunk.sentences.zip(sentenceSourceRanges).mapNotNull { (sentence, range) ->
            val start = maxOf(range.first, sliceStart)
            val end = minOf(range.last + 1, sliceEnd)
            if (start < end) sentence to ((start - sliceStart) until (end - sliceStart)) else null
        }
    }
    val activeRange = remember(chunk.sentences, sentenceSourceRanges, sliceStart, sliceEnd, isCurrentChunk, currentPositionMs) {
        if (!isCurrentChunk) null else {
            val activeIndex = chunk.sentences.indexOfFirst { sentence ->
                currentPositionMs >= sentence.offsetMs &&
                    currentPositionMs < sentence.offsetMs + sentence.durationMs
            }
            if (activeIndex < 0) null else {
                val sourceRange = sentenceSourceRanges[activeIndex]
                val start = maxOf(sourceRange.first, sliceStart)
                val end = minOf(sourceRange.last + 1, sliceEnd)
                if (start < end) (start - sliceStart) until (end - sliceStart) else null
            }
        }
    }
    val annotated = remember(
        chunk.sentences,
        chunk.block,
        sourceText,
        sliceStart,
        sliceEnd,
        visibleSentences,
        isCurrentChunk,
        currentPositionMs,
        palette,
        savedVocabStatuses,
        vocabStyles,
        highlights,
        documentStartOffset,
        findRanges,
        activeFindRange
    ) {
        buildAnnotatedString {
            ranges.clear()
            val full = sourceText.substring(sliceStart, sliceEnd)
            append(full)
            ranges.addAll(visibleSentences.map { it.second })
            if (savedVocabStatuses.isNotEmpty()) {
                for (match in vocabHighlighter.findMatches(full)) {
                    val style = vocabStyles.getValue(match.status)
                    addStyle(style, match.range.first, match.range.last + 1)
                }
            }
            // Manual highlights are a separate, offset-based layer. Apply
            // their background after vocabulary styles so a saved-word tint
            // cannot hide the color the reader explicitly chose; the vocab
            // underline and foreground color remain intact.
            val localHighlights = highlights.mapNotNull { highlight ->
                val positions = (sliceStart until sliceEnd).filter { chunk.sourceOffsets.getOrElse(it) { documentStartOffset + it } in highlight.startOffset until highlight.endOffset }
                val start = positions.firstOrNull()?.minus(sliceStart) ?: return@mapNotNull null
                val end = (positions.last() + 1 - sliceStart)
                if (start < end) highlight.copy(startOffset = start, endOffset = end) else null
            }
            applyHighlights(full, localHighlights).spanStyles.forEach { span ->
                addStyle(span.item, span.start, span.end)
            }
            // Find-in-text matches sit on top of manual highlights so they stay visible; the
            // current match is stronger than the rest.
            for (range in findRanges) {
                if (range.first < 0 || range.last >= full.length) continue
                val active = range == activeFindRange
                addStyle(
                    SpanStyle(
                        background = palette.accent.copy(alpha = if (active) 0.55f else 0.22f),
                        fontWeight = if (active) FontWeight.SemiBold else null
                    ),
                    range.first,
                    range.last + 1
                )
            }
            visibleSentences.forEachIndexed { index, (sentence, _) ->
                val isActive = isCurrentChunk &&
                    currentPositionMs >= sentence.offsetMs &&
                    currentPositionMs < sentence.offsetMs + sentence.durationMs
                if (isActive) {
                    val range = ranges[index]
                    addStyle(
                        SpanStyle(color = palette.highlightInk),
                        range.first,
                        range.last + 1
                    )
                }
            }
            for (span in chunk.block.emphasisSpans) {
                val start = maxOf(span.start, sliceStart)
                val end = minOf(span.end, sliceEnd)
                if (start < end) {
                    addStyle(
                        SpanStyle(
                            fontWeight = if (span.bold) FontWeight.Bold else null,
                            fontStyle = if (span.italic) FontStyle.Italic else null
                        ),
                        start - sliceStart, end - sliceStart
                    )
                }
            }
        }
    }

    var selection by remember(chunk.sentences, sliceStart, sliceEnd, clearSelectionSignal) { mutableStateOf(TextRange.Zero) }
    LaunchedEffect(clearSelectionSignal) { onSelectionActiveChange(false) }
    val viewport = LocalReadingViewport.current
    DisposableEffect(viewport, documentStartOffset) {
        onDispose { viewport?.offsets?.remove(documentStartOffset) }
    }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val findRevision = LocalReaderFindRevision.current
    val findRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(activeFindRange, findRevision) {
        val range = activeFindRange ?: return@LaunchedEffect
        val layout = snapshotFlow { textLayout }.filterNotNull().first()
        withFrameNanos { }
        if (annotated.isNotEmpty()) findRequester.bringIntoView(layout.getBoundingBox(range.first.coerceIn(0, annotated.lastIndex)))
    }
    var layoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val headingFontFamily = readerHeadlineFamily(chunk.text)

    fun sentenceTextFor(offset: Int): String {
        val idx = ranges.indexOfFirst { offset in it }
        return if (idx >= 0) visibleSentences[idx].first.text else annotated.text
    }

    // Start-aligned base style. Justification is done by widening the spaces (see
    // justifyBySpaceWidening): Compose 1.7's TextAlign.Justify draws stretched lines but
    // reports unstretched glyph positions, so selection, handles and hit-testing drifted
    // off the visible characters.
    val baseStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = BASE_LETTER_SPACING,
        color = color,
        fontWeight = fontWeight,
        fontFamily = if (fontWeight != null) headingFontFamily else null,
        textAlign = TextAlign.Start,
        // Android's inserted hyphen glyphs have no source-text offset. Keeping
        // hyphenation off makes selection offsets identical to document offsets.
        hyphens = Hyphens.None,
        textDirection = TextDirection.Content
    )
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var layoutMaxWidth by remember { mutableIntStateOf(0) }
    // A paged slice that stops mid-paragraph justifies its last line too, like a book.
    val justifyLastLine = sliceEnd < sourceText.length
    val displayed = remember(annotated, baseStyle, layoutMaxWidth, justify, justifyLastLine, density) {
        if (!justify || layoutMaxWidth <= 0 || annotated.isEmpty()) annotated
        else justifyBySpaceWidening(
            annotated,
            textMeasurer.measure(
                annotated,
                baseStyle,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = layoutMaxWidth),
                density = density
            ),
            layoutMaxWidth,
            density,
            justifyLastLine
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
            androidx.compose.foundation.text.selection.TextSelectionColors(
                // The stock teardrop handles stay (invisibly) for dragging; slim
                // bar handles are drawn over them in drawWithContent below.
                handleColor = Color.Transparent,
                backgroundColor = palette.selectionBg
            )
    ) {
        BasicTextField(
            value = TextFieldValue(annotatedString = displayed, selection = selection),
            onValueChange = { newValue ->
                onSelectionActiveChange(!newValue.selection.collapsed)
                if (newValue.selection.collapsed) {
                    selection = TextRange.Zero
                } else {
                    val kind = classifySelection(annotated.text, newValue.selection, readerLanguage)
                    // Show the word-snapped range, not the raw handle offset, so a handle
                    // dropped mid-word still selects the whole word (keep drag direction).
                    val snapped = when (kind) {
                        is SelectionKind.Word -> kind.range
                        is SelectionKind.Phrase -> kind.range
                        null -> null
                    }
                    selection = when {
                        snapped == null -> newValue.selection
                        newValue.selection.reversed -> TextRange(snapped.max, snapped.min)
                        else -> snapped
                    }
                    when (kind) {
                        is SelectionKind.Word ->
                            onPhraseSelected(
                                kind.word,
                                sentenceTextFor(newValue.selection.start),
                                TextRange(
                                    chunk.sourceOffset(sliceStart + kind.range.min),
                                    chunk.sourceEnd(sliceStart + kind.range.max)
                                )
                            )
                        is SelectionKind.Phrase ->
                            onPhraseSelected(
                                kind.text,
                                sentenceTextFor(newValue.selection.start),
                                TextRange(
                                    chunk.sourceOffset(sliceStart + kind.range.min),
                                    chunk.sourceEnd(sliceStart + kind.range.max)
                                )
                            )
                        null -> {
                            selection = TextRange.Zero
                            onSelectionActiveChange(false)
                        }
                    }
                }
            },
            readOnly = true,
            modifier = Modifier.bringIntoViewRequester(findRequester).pointerInput(displayed, documentStartOffset) {
                awaitEachGesture {
                    val down = awaitFirstDown(false, PointerEventPass.Initial)
                    var moved = false
                    var up = down
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        up = event.changes.firstOrNull { it.id == down.id } ?: break
                        if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    } while (up.pressed)
                    if (!moved && !up.isConsumed &&
                        up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                    ) {
                        val layout = textLayout ?: return@awaitEachGesture
                        val cursor = layout.getOffsetForPosition(up.position)
                        val offset = listOf(cursor, cursor - 1).firstOrNull {
                            it in displayed.indices && layout.getBoundingBox(it).contains(up.position)
                        } ?: return@awaitEachGesture
                        val bounds = wordAt(displayed.text, offset, readerLanguage)?.let { it.start until it.end } ?: return@awaitEachGesture
                        up.consume()
                        onWordLookup(
                            displayed.text.substring(bounds.first, bounds.last + 1),
                            sentenceTextFor(offset),
                            TextRange(chunk.sourceOffset(sliceStart + bounds.first), chunk.sourceEnd(sliceStart + bounds.last + 1))
                        )
                    }
                }
            }.onGloballyPositioned { coordinates ->
                layoutCoordinates = coordinates
                val layout = textLayout
                if (viewport != null && layout != null) {
                    val bounds = coordinates.boundsInWindow()
                    val visible = viewport.bounds
                    if (bounds.bottom > visible.top && bounds.top < visible.bottom) {
                        // boundsInWindow is clipped to the viewport; the line math needs the
                        // unclipped top, or a paragraph scrolled half off reports its line 0.
                        val textTop = coordinates.positionInWindow().y
                        val line = layout.getLineForVerticalPosition((visible.top - textTop).coerceAtLeast(0f))
                        val offset = layout.getLineStart(line)
                        viewport.offsets[documentStartOffset] = chunk.sourceOffset(sliceStart + offset)
                    } else viewport.offsets.remove(documentStartOffset)
                }
            }.drawBehind {
                val layout = textLayout ?: return@drawBehind
                val range = activeRange ?: return@drawBehind
                if (range.isEmpty()) return@drawBehind
                val firstLine = layout.getLineForOffset(range.first)
                val lastLine = layout.getLineForOffset(range.last)
                for (line in firstLine..lastLine) {
                    val lineStart = maxOf(range.first, layout.getLineStart(line))
                    val visibleLineEnd = layout.getLineEnd(line, visibleEnd = true)
                    val lineEnd = minOf(range.last + 1, visibleLineEnd)
                    if (lineStart >= lineEnd) continue
                    // Justified/hyphenated lines: a line-end offset resolves to the next line's
                    // start, so use per-character boxes and the line's own edges instead.
                    val left = if (lineStart == layout.getLineStart(line)) layout.getLineLeft(line)
                        else layout.getBoundingBox(lineStart).left
                    val right = if (lineEnd == visibleLineEnd) layout.getLineRight(line)
                        else layout.getBoundingBox(lineEnd - 1).right
                    drawRoundRect(
                        color = palette.highlightBg,
                        topLeft = Offset(minOf(left, right) - 3.dp.toPx(), layout.getLineTop(line) + 1.dp.toPx()),
                        size = Size(kotlin.math.abs(right - left) + 6.dp.toPx(), layout.getLineBottom(line) - layout.getLineTop(line) - 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
                    )
                }
            }.drawWithContent {
                drawContent()
                val layout = textLayout ?: return@drawWithContent
                if (selection.collapsed || annotated.isEmpty()) return@drawWithContent
                val start = selection.min.coerceIn(0, annotated.lastIndex)
                val endChar = (selection.max - 1).coerceIn(0, annotated.lastIndex)
                drawSlimSelectionHandle(layout, start, isStart = true, color = palette.selectionHandle)
                drawSlimSelectionHandle(layout, endChar, isStart = false, color = palette.selectionHandle)
            },
            onTextLayout = {
                textLayout = it
                layoutMaxWidth = it.layoutInput.constraints.maxWidth
            },
            textStyle = baseStyle,
            cursorBrush = SolidColor(Color.Transparent)
        )
    }
}


@Composable
private fun PlaybackControls(palette: ReadingPalette, speaking: Boolean, synthesizing: Boolean,
    rate: Float, previous: () -> Unit, next: () -> Unit, seek: (Long) -> Unit, canSeek: Boolean,
    toggle: () -> Unit, changeRate: (Float) -> Unit, shadowingEnabled: Boolean, onToggleShadowing: () -> Unit) {
    var speedMenuExpanded by remember { mutableStateOf(false) }
    val isSynthesizing = synthesizing

    Surface(
        color = palette.background,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(32.dp).height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(palette.divider)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { previous() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(Res.string.accessibility_previous_sentence), modifier = Modifier.size(22.dp), tint = palette.ink.copy(alpha = 0.75f))
                }
                IconButton(onClick = { seek(-10_000) }, enabled = canSeek, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Replay10, contentDescription = stringResource(Res.string.accessibility_skip_back), modifier = Modifier.size(22.dp), tint = palette.ink.copy(alpha = 0.75f))
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp + 4.dp)) {
                    FilledIconButton(
                        onClick = { toggle() },
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = palette.accent,
                            disabledContainerColor = palette.accent.copy(alpha = 0.45f)
                        )
                    ) {
                        androidx.compose.animation.Crossfade(targetState = speaking, label = "playPause") { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(Res.string.accessibility_play_pause),
                                modifier = Modifier.size(26.dp),
                                tint = Color.White
                            )
                        }
                    }
                    if (isSynthesizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = palette.highlightInk,
                            trackColor = palette.accent.copy(alpha = 0.28f),
                            strokeWidth = 2.dp
                        )
                    }
                }
                IconButton(onClick = { seek(10_000) }, enabled = canSeek, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Forward10, contentDescription = stringResource(Res.string.accessibility_skip_forward), modifier = Modifier.size(22.dp), tint = palette.ink.copy(alpha = 0.75f))
                }
                IconButton(onClick = { next() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(Res.string.accessibility_next_sentence), modifier = Modifier.size(22.dp), tint = palette.ink.copy(alpha = 0.75f))
                }
                IconButton(onClick = onToggleShadowing, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = stringResource(Res.string.shadowing_toggle),
                        modifier = Modifier.size(22.dp),
                        tint = if (shadowingEnabled) palette.accent else palette.ink.copy(alpha = 0.75f)
                    )
                }
                Box {
                    Surface(
                        modifier = Modifier.clickable { speedMenuExpanded = true },
                        shape = RoundedCornerShape(50),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, palette.divider)
                    ) {
                        Text(
                            "${rate}x",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accent
                        )
                    }
                    DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }) {
                        SPEED_OPTIONS.forEach { s ->
                            DropdownMenuItem(text = { Text("${s}x") }, onClick = {
                                changeRate(s)
                                speedMenuExpanded = false
                            })
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ReadingFindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    palette: ReadingPalette,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusRequested()
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("readingFindField"),
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.reading_find_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() })
        )
        if (query.isNotBlank()) {
            Text(
                if (total == 0) stringResource(Res.string.reading_find_none)
                else stringResource(Res.string.reading_find_count, current, total),
                style = MaterialTheme.typography.labelMedium,
                color = palette.inkFaded,
                modifier = Modifier.padding(horizontal = 8.dp).testTag("readingFindCount")
            )
        }
        IconButton(onClick = onPrevious, enabled = total > 0) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(Res.string.reading_find_previous))
        }
        IconButton(onClick = onNext, enabled = total > 0) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(Res.string.reading_find_next))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.reading_find_close))
        }
    }
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSlimSelectionHandle(
    layout: TextLayoutResult,
    charOffset: Int,
    isStart: Boolean,
    color: Color
) {
    val box = layout.getBoundingBox(charOffset)
    val rtl = layout.getBidiRunDirection(charOffset) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl
    // Visual edge of the selection and the direction pointing away from it.
    val leftEdge = isStart != rtl
    val x = if (leftEdge) box.left else box.right
    val outward = if (leftEdge) -1f else 1f
    val line = layout.getLineForOffset(charOffset)
    val top = layout.getLineTop(line)
    val bottom = layout.getLineBottom(line)
    val barWidth = 2.dp.toPx()
    val flag = 9.dp.toPx()
    drawRect(color, topLeft = Offset(x - barWidth / 2, top), size = Size(barWidth, bottom - top))
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(x - outward * barWidth / 2, bottom - 1.dp.toPx())
        lineTo(x - outward * barWidth / 2, bottom + flag)
        lineTo(x + outward * flag, bottom + flag)
        close()
    }
    drawPath(path, color)
}

private val BASE_LETTER_SPACING = 0.1.sp

/**
 * Justifies [text] by giving each space of a full line extra letter spacing so the line
 * reaches [maxWidth]. Unlike TextAlign.Justify, the widened spaces are real metrics, so
 * TextLayoutResult positions (selection, handles, hit-testing, highlights) match the
 * drawn glyphs. [layout] is the start-aligned layout of [text]; line breaks don't move
 * because every widened line stays just under [maxWidth].
 */
internal fun justifyBySpaceWidening(
    text: AnnotatedString,
    layout: TextLayoutResult,
    maxWidth: Int,
    density: androidx.compose.ui.unit.Density,
    justifyLastLine: Boolean
): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    var changed = false
    for (line in 0 until layout.lineCount) {
        val lineEnd = layout.getLineEnd(line)
        val isParagraphEnd = line == layout.lineCount - 1 || text.getOrNull(lineEnd - 1) == '\n'
        if (isParagraphEnd && !(justifyLastLine && line == layout.lineCount - 1)) continue
        val start = layout.getLineStart(line)
        val visibleEnd = layout.getLineEnd(line, visibleEnd = true)
        val spaces = (start until visibleEnd).filter { text[it] == ' ' }
        if (spaces.isEmpty()) continue
        // Keep a small margin so float rounding never pushes a word onto the next line.
        val slack = maxWidth - (layout.getLineRight(line) - layout.getLineLeft(line)) - 2f
        if (slack <= 0f) continue
        val extraSp = with(density) { (slack / spaces.size).toSp() }
        val spacing = (BASE_LETTER_SPACING.value + extraSp.value).sp
        for (i in spaces) builder.addStyle(SpanStyle(letterSpacing = spacing), i, i + 1)
        changed = true
    }
    return if (changed) builder.toAnnotatedString() else text
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentsSheet(
    headings: List<Pair<Int, ChunkState>>,
    currentChunkIndex: Int,
    totalChunks: Int,
    palette: ReadingPalette,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentHeadingIndex = headings.indexOfLast { it.first <= currentChunkIndex }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.background, contentColor = palette.ink) {
        Text(
            stringResource(Res.string.reading_contents),
            style = TextStyle(fontFamily = readerHeadlineFamily(stringResource(Res.string.reading_contents)), fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(headings, key = { _, entry -> entry.first }) { headingIndex, (index, chunk) ->
                val isCurrent = headingIndex == currentHeadingIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) palette.accent.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(
                            start = if (chunk.block.headerLevel == 2) 24.dp else 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(chunk.text, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium)
                        Text(
                            stringResource(Res.string.reading_position, index + 1, totalChunks),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkFaded
                        )
                    }
                }
                if (headingIndex != headings.lastIndex) HorizontalDivider(color = palette.divider)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceInfoSheet(
    doc: TextDocument,
    chunks: List<ChunkState>,
    currentChunkIndex: Int,
    palette: ReadingPalette,
    onDismiss: () -> Unit
) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    
    
    val words = remember(chunks) {
        chunks.sumOf { chunk -> chunk.text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) } }
    }
    val paragraphs = chunks.count { it.block.type != BlockType.HEADER && it.block.type != BlockType.IMAGE }
    // Learner pace: ~150 words per minute.
    val minutes = maxOf(1, (words + 149) / 150)
    val progress = if (chunks.isEmpty()) 0 else ((currentChunkIndex + 1) * 100 / chunks.size).coerceIn(0, 100)

    @Composable
    fun InfoRow(label: String, value: String) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.inkFaded, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.background, contentColor = palette.ink) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ZabankhanDesign.spacing.medium).padding(bottom = ZabankhanDesign.spacing.large)) {
            Text(doc.title, style = TextStyle(fontFamily = readerHeadlineFamily(doc.title), fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold))
            HorizontalDivider(Modifier.padding(vertical = ZabankhanDesign.spacing.small), color = palette.divider)
            InfoRow(stringResource(Res.string.text_info_words), formatReaderNumber(words))
            InfoRow(stringResource(Res.string.text_info_reading_time), stringResource(Res.string.text_info_minutes, formatReaderNumber(minutes)))
            InfoRow(stringResource(Res.string.text_info_paragraphs), formatReaderNumber(paragraphs))
            InfoRow(stringResource(Res.string.text_info_progress), formatReaderNumber(progress) + "%")
            InfoRow(stringResource(Res.string.text_info_added), formatReaderDate(doc.createdAtMs))
            if (doc.sourceUrl != null || doc.sourceName != null || doc.author != null || doc.license != null || doc.publishedAt != null) {
                HorizontalDivider(Modifier.padding(vertical = ZabankhanDesign.spacing.small), color = palette.divider)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        doc.sourceName ?: stringResource(Res.string.source_default_label),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    doc.sourceUrl?.let { url ->
                        IconButton(onClick = { uri.openUri(url) }) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = stringResource(Res.string.accessibility_open_in_browser))
                        }
                    }
                }
                doc.author?.let { Text(stringResource(Res.string.source_author_label, it), style = MaterialTheme.typography.bodyLarge) }
                doc.license?.let { Text(stringResource(Res.string.source_license_label, it), style = MaterialTheme.typography.bodyLarge) }
                doc.publishedAt?.let {
                    Text(stringResource(Res.string.source_published_label, formatReaderDate(it)), style = MaterialTheme.typography.bodyLarge)
                }
                doc.sourceUrl?.let {
                    Text(
                        io.ktor.http.Url(it).host,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkFaded,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

