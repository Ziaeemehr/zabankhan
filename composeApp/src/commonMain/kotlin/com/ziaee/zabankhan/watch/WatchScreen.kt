package com.ziaee.zabankhan.watch

import com.ziaee.zabankhan.ui.TranslationSourceIcon
import com.ziaee.zabankhan.translate.ExampleTranslation

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.srs.*
import com.ziaee.zabankhan.ui.*
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.ui.reader.*
import com.ziaee.zabankhan.translate.TranslationOverrides
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.stringResource
import kotlin.time.TimeSource

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable fun WatchScreen(vm: AppViewModel, settings: AppSettings, videoId: String, path: String, subs: String, back: () -> Unit) {
    val lang = Languages.learnable.firstOrNull { it.code == settings.learningLang } ?: return
    val identity = path.ifBlank { videoId }
    val scope = rememberCoroutineScope()
    val clock = remember { TimeSource.Monotonic.markNow() }
    fun now() = clock.elapsedNow().inWholeMilliseconds
    var subtitlePath by remember(identity) { mutableStateOf(subs) }
    var controller by remember(identity) { mutableStateOf<VideoController?>(null) }
    var captions by remember(identity, lang) { mutableStateOf<VideoCaptions?>(null) }
    var document by remember(identity, lang) { mutableStateOf<TextDocument?>(null) }
    var loading by remember(identity) { mutableStateOf(true) }
    var failed by remember(identity) { mutableStateOf(false) }
    var attempt by remember(identity) { mutableIntStateOf(0) }
    var shadowIndex by remember(identity) { mutableStateOf<Int?>(null) }
    var selection by remember(identity) { mutableStateOf<ReaderSelection?>(null) }
    var idiomMatcher by remember(lang) { mutableStateOf(IdiomMatcher(InMemoryIdiomLexicon(emptyList()), lang)) }
    val words by remember(lang) { vm.container.vocab.observeAll(lang.code) }.collectAsState(emptyList())
    val highlighter = remember(words, lang) { VocabHighlighter(words.map { SavedVocab(it.word, it.srsState().status()) }, lang) }
    val history = remember { WatchHistory(vm.container.cache) }
    val cueList = captions?.cues.orEmpty()
    val merged = remember(cueList, lang) { mergeSentenceUnits(cueList, lang) }
    val sentences = merged.units
    val offsets = remember(sentences) { transcriptOffsets(sentences) }
    val position by (controller?.positionMs ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0L) }).collectAsState()
    val playing by (controller?.isPlaying ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    val latestPosition by rememberUpdatedState(position)
    val latestPlaying by rememberUpdatedState(playing)
    com.ziaee.zabankhan.statistics.TrackActivity(vm.container, identity, lang.code, com.ziaee.zabankhan.statistics.ActivityKind.WATCHING, playing)
    val playerError by ((controller as? VideoStatus)?.error ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsState()
    var rate by remember(identity) { mutableFloatStateOf(1f) }
    var freeze by remember(identity) { mutableStateOf(PlaybackFreeze()) }
    var gestureActive by remember(identity) { mutableStateOf(false) }
    var playingBeforeTouch by remember(identity) { mutableStateOf(false) }
    var resumeAfterDictionary by remember(identity) { mutableStateOf(false) }
    var following by remember(identity) { mutableStateOf(true) }
    var lastTouch by remember(identity) { mutableLongStateOf(-1L) }
    val shownTime = freeze.cueTimeMs ?: position
    val active = findCueIndex(cueList, shownTime).takeIf { it >= 0 } ?: cueList.indexOfLast { it.endMs <= shownTime }
    val sentenceIndex = merged.unitIndex(active)
    val activeRange = merged.cueTextRanges.getOrNull(active)
    val list = rememberLazyListState()
    val viewport by remember { derivedStateOf { list.layoutInfo.viewportSize.height } }
    val density = LocalDensity.current
    val padding = with(density) { transcriptEdgePadding(viewport, 8.dp.roundToPx()).toDp() }
    val heights = remember(sentences) { mutableStateMapOf<Int, Int>() }
    val sizes = remember { listOf(85, 100, 115, 130, 150, 175) }
    val percent = settings.watchSubtitleSizePercent
    val scale = percent / 100f
    val subtitleStyle = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale, textDirection = TextDirection.Content) }
    val translationStyle = MaterialTheme.typography.bodySmall.let { it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale) }
    val meaningLang = Languages.byCode(settings.meaningLang)?.takeIf { it in listOf(Languages.PERSIAN, Languages.ENGLISH) } ?: Languages.PERSIAN
    val repository = vm.container.translations(settings)
    val translations = remember(identity, lang.code, meaningLang.code, repository) {
        WatchTranslations(TranslationOverrides(vm.container.cache), lang.code, meaningLang.code, translateWithSource = { repository.translateTextWithSource(it, lang.code, meaningLang.code) }) { text -> repository.translateText(text, lang.code, meaningLang.code) }
    }
    var editingTranslation by remember(identity) { mutableStateOf<String?>(null) }
    var exportOpen by remember(identity) { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val addedMessage = stringResource(Res.string.watch_added)
    val subtitlePicker = rememberMediaPicker(MediaFileKind.SUBTITLES) { it?.let { subtitlePath = it } }
    LaunchedEffect(controller, rate) { controller?.setRate(rate) }
    LaunchedEffect(identity, lang, subtitlePath, attempt) {
        loading = true; failed = false
        try {
            val result = if (path.isNotBlank() || subtitlePath.isNotBlank()) withContext(Dispatchers.IO) {
                val title = runCatching { FileSystem.SYSTEM.read((path + ".title").toPath()) { readUtf8() } }.getOrDefault(path.substringAfterLast('/'))
                VideoCaptions(captions?.title ?: title.ifBlank { videoId }, if (subtitlePath.isBlank()) emptyList() else parseSubtitleFile(subtitlePath, FileSystem.SYSTEM.read(subtitlePath.toPath()) { readUtf8() }, lang))
            } else CaptionCache(vm.container.cache).load(videoId, lang, YoutubeCaptionFetcher(vm.container.http))
            if (path.isBlank() && subtitlePath.isNotBlank() && result.cues.isNotEmpty()) CaptionCache(vm.container.cache).write(videoId, lang, result, "file")
            captions = result
            document = saveTranscript(vm.container.texts, identity, lang, result.title, if (path.isBlank()) "https://www.youtube.com/watch?v=$videoId" else null, mergeSentenceUnits(result.cues, lang).units, legacyCues = result.cues)
            if (result.cues.isEmpty() && subtitlePath.isNotBlank()) failed = true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(lang) {
        val pack = com.ziaee.zabankhan.datapack.LanguageDataPacks.load(lang.code)
        idiomMatcher = if (pack == null) IdiomMatcher(InMemoryIdiomLexicon(emptyList()), lang)
            else IdiomMatcher(pack.idioms, lang, pack.lemmas)
    }
    LaunchedEffect(controller, identity, lang) {
        val player = controller ?: return@LaunchedEffect
        history.read(lang).firstOrNull { (it.path ?: it.videoId) == identity }?.let { player.seekTo(it.positionMs) }
        while (true) {
            captions?.let { c -> history.record(lang, RecentVideo(videoId, c.title, c.channel, player.positionMs.value, path.takeIf { it.isNotBlank() }, subtitlePath.takeIf { it.isNotBlank() })) }
            delay(5_000)
        }
    }
    val latestCaptions by rememberUpdatedState(captions)
    val latestSubtitles by rememberUpdatedState(subtitlePath)
    DisposableEffect(controller, identity, lang) {
        val player = controller
        onDispose {
            player?.pause()
            createTts().stop()
            val c = latestCaptions; val currentSubs = latestSubtitles
            if (player != null && c != null) vm.action { history.record(lang, RecentVideo(videoId, c.title, c.channel, player.positionMs.value, path.takeIf { it.isNotBlank() }, currentSubs.takeIf { it.isNotBlank() })) }
        }
    }
    var previousTime by remember(identity) { mutableLongStateOf(position) }
    LaunchedEffect(position) {
        freeze = freeze.onTime(previousTime, position, if (playing) 1 else 2, shadowIndex != null, now())
        previousTime = position
    }
    LaunchedEffect(playing) {
        if (playing) {
            if (freeze.guardsPlaying(gestureActive, now())) controller?.pause()
            else { freeze = freeze.resume(); following = true }
        }
    }
    LaunchedEffect(list) { list.interactionSource.interactions.collect { if (it is DragInteraction.Start) following = false } }
    LaunchedEffect(lastTouch, gestureActive) {
        if (lastTouch >= 0 && !gestureActive) { delay(4_000); following = true }
    }
    LaunchedEffect(sentences, sentenceIndex, settings.watchTranslationEnabled, translations) {
        // Screen scope: advancing a sentence must not cancel requests the next window still needs; load() dedups.
        if (settings.watchTranslationEnabled) translationPrefetchWindow(sentences, sentenceIndex).forEach { text -> scope.launch { translations.load(text) } }
    }
    LaunchedEffect(sentenceIndex, following, gestureActive, viewport, heights[sentenceIndex], percent,
        settings.watchTranslationEnabled, translations.states[sentences.getOrNull(sentenceIndex)?.text], selection) {
        if (following && !gestureActive && selection == null && sentenceIndex in sentences.indices && viewport > 0) {
            withFrameNanos { }
            if (list.layoutInfo.visibleItemsInfo.none { it.index == sentenceIndex }) list.scrollToItem(sentenceIndex)
            val info = snapshotFlow { list.layoutInfo }.first { layout -> layout.visibleItemsInfo.any { it.index == sentenceIndex } }
            val item = info.visibleItemsInfo.first { it.index == sentenceIndex }
            list.animateScrollToItem(sentenceIndex, transcriptCenterOffset(info.viewportSize.height, item.size, info.beforeContentPadding))
        }
    }
    fun playFromLine(index: Int) {
        val sentence = sentences.getOrNull(index) ?: return
        freeze = freeze.resume(); gestureActive = false; following = true; lastTouch = -1
        controller?.seekTo(sentence.startMs); controller?.play()
    }
    fun skip(delta: Long) {
        freeze = freeze.resume(); gestureActive = false; following = true; lastTouch = -1
        controller?.seekTo((position + delta).coerceAtLeast(0))
    }
    fun closeDictionary() {
        selection = null; following = true; lastTouch = -1
        if (resumeAfterDictionary && shadowIndex == null) { freeze = freeze.resume(); gestureActive = false; controller?.play() }
        resumeAfterDictionary = false
    }
    BackHandler(enabled = selection != null) { closeDictionary() }
    val clipboard = LocalClipboardManager.current
    val player = remember(identity) {
        movableContentOf<Modifier> { modifier ->
            if (path.isNotBlank()) LocalVideoPlayer(path, { controller = it }, modifier)
            else YoutubePlayer(videoId, { controller = it }, modifier)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val playerHeight = if (settings.watchCompactPlayer) 96.dp else maxOf(96.dp, minOf(maxWidth * 9f / 16f, maxHeight * .35f))
        val playerWidth = if (settings.watchCompactPlayer) minOf(playerHeight * 16f / 9f, maxWidth * .55f) else minOf(maxWidth, playerHeight * 16f / 9f)
        Column(Modifier.fillMaxSize()) {
            var optionsOpen by remember { mutableStateOf(false) }
            val title = watchScreenTitle(captions?.title ?: document?.title, path, videoId)
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (selection != null) closeDictionary() else back() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back), Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium.merge(contentStyle(title)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(if (loading) Res.string.watch_loading_captions else if (cueList.isEmpty()) Res.string.watch_no_captions else Res.string.watch_caption_ready),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(
                        onClick = { scope.launch { vm.container.settings.setWatchCompactPlayer(!settings.watchCompactPlayer) } },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            if (settings.watchCompactPlayer) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                            stringResource(if (settings.watchCompactPlayer) Res.string.watch_full_player else Res.string.watch_compact_player),
                            Modifier.size(24.dp),
                        )
                    }
                    Box {
                        IconButton(onClick = { optionsOpen = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.MoreVert, stringResource(Res.string.more_actions), Modifier.size(24.dp))
                        }
                        DropdownMenu(optionsOpen, onDismissRequest = { optionsOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(Res.string.watch_subtitle_size_percent, percent).replace("%%", "%")) }, enabled = false, onClick = {})
                            fun setSize(size: Int) { scope.launch { vm.container.settings.setWatchSubtitleSizePercent(size) }; following = true; optionsOpen = false }
                            DropdownMenuItem(text = { Text("A−   ${stringResource(Res.string.watch_subtitle_smaller)}") }, enabled = percent > sizes.first(), onClick = { setSize(sizes.lastOrNull { it < percent } ?: sizes.first()) })
                            DropdownMenuItem(text = { Text("A+   ${stringResource(Res.string.watch_subtitle_larger)}") }, enabled = percent < sizes.last(), onClick = { setSize(sizes.firstOrNull { it > percent } ?: sizes.last()) })
                            HorizontalDivider()
                            listOf(.75f, 1f, 1.25f).forEach { speed ->
                                DropdownMenuItem(text = { Text("${stringResource(Res.string.watch_rate)}: $speed×") }, onClick = { rate = speed; optionsOpen = false })
                            }
                        }
                    }
                }
            }
            if (settings.watchCompactPlayer) {
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    player(Modifier.size(playerWidth, playerHeight))
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    player(Modifier.size(playerWidth, playerHeight).clip(RoundedCornerShape(16.dp)))
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                    WatchActionButton(Icons.Default.Subtitles, stringResource(Res.string.watch_toolbar_subtitles), false, !loading, subtitlePicker)
                    WatchActionButton(Icons.Default.Translate, stringResource(Res.string.watch_toolbar_translate), settings.watchTranslationEnabled, true) {
                        scope.launch { vm.container.settings.setWatchTranslationEnabled(!settings.watchTranslationEnabled) }
                    }
                    WatchActionButton(Icons.Default.RecordVoiceOver, stringResource(Res.string.watch_toolbar_shadow), shadowIndex != null, sentenceIndex >= 0) {
                        if (shadowIndex == null) { controller?.pause(); shadowIndex = sentenceIndex } else shadowIndex = null
                    }
                    WatchActionButton(Icons.Default.IosShare, stringResource(Res.string.watch_toolbar_export), false, cueList.isNotEmpty()) { exportOpen = true }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.watch_caption_error), Modifier.weight(1f))
                TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) }
            }
            playerError?.let { Text(stringResource(Res.string.watch_player_error, it), Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.error) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val lineHeight = with(density) { subtitleStyle.lineHeight.toPx().toInt() }
                val itemHeight = with(density) { transcriptItemHeight(viewport, lineHeight, 32.dp.roundToPx(), 8.dp.roundToPx()).toDp() }
                LazyColumn(state = list, contentPadding = PaddingValues(vertical = padding), verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val edge = minOf(32.dp.toPx(), size.height / 2f)
                            if (size.height > 0) drawRect(Brush.verticalGradient(0f to Color.Transparent, edge / size.height to Color.Black,
                                1f - edge / size.height to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                        }.pointerInput(identity) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                playingBeforeTouch = latestPlaying && freeze.cueTimeMs == null && shadowIndex == null
                                gestureActive = true; lastTouch = now()
                                freeze = PlaybackFreeze(freeze.cueTimeMs ?: latestPosition, lastTouch)
                                controller?.pause()
                                try {
                                    do { val event = awaitPointerEvent(PointerEventPass.Initial); lastTouch = now() } while (event.changes.any { it.pressed })
                                } finally { lastTouch = now(); gestureActive = false }
                            }
                        }) {
                    if (sentences.isEmpty()) item { Text(stringResource(Res.string.watch_no_captions)) }
                    itemsIndexed(sentences) { index, sentence ->
                        val current = index == sentenceIndex
                        val scroll = rememberScrollState()
                        var layout by remember(sentence.text) { mutableStateOf<TextLayoutResult?>(null) }
                        var innerHeight by remember { mutableIntStateOf(0) }
                        LaunchedEffect(scroll) { scroll.interactionSource.interactions.collect { if (it is DragInteraction.Start) following = false } }
                        LaunchedEffect(current, activeRange, layout, innerHeight, scroll.maxValue, following, gestureActive, selection) {
                            val value = layout; val range = activeRange
                            if (current && following && !gestureActive && selection == null && value != null && range != null && sentence.text.isNotEmpty()) {
                                val first = value.getLineForOffset(range.first.coerceIn(0, sentence.text.lastIndex))
                                val last = value.getLineForOffset(range.last.coerceIn(0, sentence.text.lastIndex))
                                scroll.animateScrollTo(transcriptActiveOffset(value.getLineTop(first).toInt(), value.getLineBottom(last).toInt(), innerHeight, innerHeight + scroll.maxValue))
                            }
                        }
                        fun lookup(query: String) {
                            resumeAfterDictionary = playingBeforeTouch; playingBeforeTouch = false
                            controller?.pause()
                            selection = ReaderSelection(query, sentence.text, offsets[index] until offsets[index] + sentence.text.length, false)
                        }
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = itemHeight)
                                .then(if (current) Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                                .padding(horizontal = if (current) 12.dp else 4.dp, vertical = if (current) 10.dp else 2.dp)
                                .onSizeChanged { innerHeight = it.height; heights[index] = it.height }.verticalScroll(scroll)
                        ) {
                            if (!current) Text(videoTime(sentence.startMs), Modifier.clickable { playFromLine(index) }.padding(vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f))
                            TranscriptSentence(sentence.text, lang, highlighter, idiomMatcher, subtitleStyle, current,
                                if (current) activeRange else null, onLayout = { layout = it }, onLookup = ::lookup,
                                onCopy = if (!current) null else ({
                                    clipboard.setText(AnnotatedString(sentence.text))
                                    if (playingBeforeTouch && shadowIndex == null) { freeze = freeze.resume(); gestureActive = false; controller?.play() }
                                    playingBeforeTouch = false
                                }))
                            if (current && settings.watchTranslationEnabled) {
                                val state = translations.states[sentence.text]
                                val line = state?.text ?: stringResource(if (state?.failed == true) Res.string.watch_translation_failed else Res.string.watch_translation_loading)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    if (state?.corrected != true) TranslationSourceIcon(state?.provenance)
                                    Text(line, Modifier.weight(1f).pointerInput(sentence.text, state?.failed) {
                                        detectTapGestures(onLongPress = { controller?.pause(); editingTranslation = sentence.text }, onTap = {
                                            if (state?.failed == true) scope.launch { translations.load(sentence.text, retry = true) }
                                        })
                                    }, style = translationStyle.merge(contentStyle(line)), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                                }
                            }
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = selection != null, enter = slideInVertically { it / 6 } + fadeIn(), exit = slideOutVertically { it / 6 } + fadeOut(), modifier = Modifier.fillMaxSize()) {
                    Surface(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} }, color = MaterialTheme.colorScheme.surface) {
                        val lookup = selection
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(lookup?.sentence.orEmpty(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall.merge(contentStyle(lookup?.sentence.orEmpty())))
                                IconButton(onClick = ::closeDictionary, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, stringResource(Res.string.cancel)) }
                            }
                            val doc = document
                            if (lookup != null && doc != null) DictionaryPane(vm, settings, doc, lookup, words, emptyList(), dismiss = ::closeDictionary, modifier = Modifier.weight(1f).fillMaxWidth(), compactControls = true)
                        }
                    }
                }
            }
            if (selection == null) WatchPlaybackBar(playing, sentenceIndex > 0, sentenceIndex < sentences.lastIndex && sentences.isNotEmpty(),
                previous = { playFromLine(sentenceIndex - 1) }, backward = { skip(-5_000) },
                playPause = { if (playing) controller?.pause() else { freeze = freeze.resume(); gestureActive = false; following = true; controller?.play() } },
                forward = { skip(5_000) }, next = { playFromLine(sentenceIndex + 1) })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    shadowIndex?.let { start ->
        ShadowingPanel(vm.container, settings, lang, document?.id, sentences.map { it.text }, start,
            dismiss = { controller?.pause(); shadowIndex = null }, autoListen = true, replay = { index ->
                val cue = sentences[index]
                replayShadowCue(checkNotNull(controller), cue.startMs, cue.endMs, rate)
            }, compactControls = true)
    }
    editingTranslation?.let { text ->
        var draft by remember(text) { mutableStateOf(translations.states[text]?.text.orEmpty()) }
        ModalBottomSheet(onDismissRequest = { editingTranslation = null }) {
            Column(Modifier.fillMaxWidth().padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text, style = contentStyle(text), maxLines = 3, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.merge(contentStyle(draft)), minLines = 2, maxLines = 6)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { scope.launch { translations.save(text, null) }; editingTranslation = null }) { Text(stringResource(Res.string.watch_translation_automatic)) }
                    Button(onClick = { scope.launch { translations.save(text, draft.trim()) }; editingTranslation = null }, enabled = draft.isNotBlank()) { Text(stringResource(Res.string.save)) }
                }
            }
        }
    }
    if (exportOpen) captions?.let { value -> WatchExportSheet(vm, lang, identity, videoId, path, value,
        dismiss = { exportOpen = false }, added = { scope.launch { snackbar.showSnackbar(addedMessage) } }) }
}

@Composable private fun WatchPlaybackBar(playing: Boolean, hasPrevious: Boolean, hasNext: Boolean, previous: () -> Unit, backward: () -> Unit,
    playPause: () -> Unit, forward: () -> Unit, next: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = previous, enabled = hasPrevious, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.SkipPrevious, stringResource(Res.string.watch_previous_sentence), Modifier.size(28.dp)) }
                IconButton(onClick = backward, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Replay5, stringResource(Res.string.ed_seek_backward), Modifier.size(28.dp)) }
                FilledIconButton(onClick = playPause, modifier = Modifier.size(64.dp)) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (playing) Res.string.watch_pause else Res.string.watch_play), Modifier.size(36.dp)) }
                IconButton(onClick = forward, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Forward5, stringResource(Res.string.ed_seek_forward), Modifier.size(28.dp)) }
                IconButton(onClick = next, enabled = hasNext, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.SkipNext, stringResource(Res.string.watch_next_sentence), Modifier.size(28.dp)) }
            }
        }
    }
}

internal fun watchScreenTitle(title: String?, path: String, videoId: String): String {
    title?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val filename = path.substringAfterLast('/').substringBeforeLast('.')
    return filename.takeIf { it.isNotBlank() } ?: videoId
}

@Composable
private fun WatchActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).semantics { contentDescription = label },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.secondaryContainer else Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = .38f), modifier = Modifier.size(20.dp))
        }
    }
}
