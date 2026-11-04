package com.ziaee.zabankhan.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.srs.*
import com.ziaee.zabankhan.ui.*
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.ui.reader.*
import kotlinx.coroutines.*
import okio.FileSystem
import okio.Path.Companion.toPath
import com.ziaee.zabankhan.pdf.AppFiles
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable fun WatchScreen(vm: AppViewModel, settings: AppSettings, videoId: String, path: String, subs: String, back: () -> Unit) {
    val lang = Languages.learnable.firstOrNull { it.code == settings.learningLang } ?: return
    val identity = path.ifBlank { videoId }
    val scope = rememberCoroutineScope()
    var subtitlePath by remember(identity) { mutableStateOf(subs) }
    var controller by remember(identity) { mutableStateOf<VideoController?>(null) }
    var captions by remember(identity, lang) { mutableStateOf<VideoCaptions?>(null) }
    var document by remember(identity, lang) { mutableStateOf<TextDocument?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var shadowIndex by remember(identity) { mutableStateOf<Int?>(null) }
    var selection by remember(identity) { mutableStateOf<ReaderSelection?>(null) }
    var idiomMatcher by remember(lang) { mutableStateOf(IdiomMatcher(InMemoryIdiomLexicon(emptyList()), lang)) }
    val words by remember(lang) { vm.container.vocab.observeAll(lang.code) }.collectAsState(emptyList())
    val highlighter = remember(words, lang) { VocabHighlighter(words.map { SavedVocab(it.word, it.srsState().status()) }, lang) }
    val history = remember { WatchHistory(vm.container.cache) }
    val cueList = captions?.cues.orEmpty()
    val offsets = remember(cueList) { transcriptOffsets(cueList) }
    val position by (controller?.positionMs ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0L) }).collectAsState()
    val playing by (controller?.isPlaying ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    val playerError by ((controller as? VideoStatus)?.error ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsState()
    val active = findCueIndex(cueList, position)
    var rate by remember { mutableFloatStateOf(1f) }
    var loopIndex by remember(identity) { mutableStateOf<Int?>(null) }
    var follow by remember(identity) { mutableStateOf(true) }
    val list = rememberLazyListState()
    var showTranslation by remember { mutableStateOf(false) }
    var translation by remember { mutableStateOf<String?>(null) }
    var translating by remember { mutableStateOf(false) }
    var translationFailed by remember { mutableStateOf(false) }
    var translationAttempt by remember { mutableIntStateOf(0) }
    val meaningLang = Languages.byCode(settings.meaningLang)?.takeIf { it in listOf(Languages.PERSIAN, Languages.ENGLISH) } ?: Languages.PERSIAN
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
            document = saveTranscript(vm.container.texts, identity, lang, result.title, if (path.isBlank()) "https://www.youtube.com/watch?v=$videoId" else null, result.cues)
            if (result.cues.isEmpty() && subtitlePath.isNotBlank()) failed = true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(lang) {
        idiomMatcher = withContext(Dispatchers.IO) {
            val tsv = runCatching { FileSystem.SYSTEM.read("${AppFiles.filesDir}/language-packs/${lang.code}/idioms.tsv".toPath()) { readUtf8() } }.getOrDefault("")
            IdiomMatcher(loadIdiomLexicon(tsv, lang), lang)
        }
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
    LaunchedEffect(list) { list.interactionSource.interactions.collect { if (it is DragInteraction.Start) follow = false } }
    LaunchedEffect(active, follow) { if (follow && active >= 0) list.animateScrollToItem(active) }
    LaunchedEffect(position, playing, loopIndex) {
        val cue = loopIndex?.let { cueList.getOrNull(it) }
        if (playing && cue != null && position >= cue.endMs) controller?.seekTo(cue.startMs)
    }
    LaunchedEffect(active, showTranslation, meaningLang, translationAttempt) {
        translation = null; translationFailed = false
        if (showTranslation && active >= 0) {
            translating = true
            try {
                val result = vm.container.translations(settings).translateText(cueList[active].text, lang.code, meaningLang.code)
                translation = result.getOrNull(); translationFailed = result.isFailure
            } finally { translating = false }
        }
    }
    fun select(index: Int, offset: Int) {
        val cue = cueList[index]
        val idiom = idiomMatcher.match(cue.text).firstOrNull { offset in it.startChar until it.endChar }
        val word = wordAt(cue.text, offset, lang)?.text ?: return
        controller?.pause()
        selection = ReaderSelection(idiom?.expression ?: word, cue.text, offsets[index] until offsets[index] + cue.text.length, false)
    }
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = back) { Text(stringResource(Res.string.back)) }
            TextButton(onClick = subtitlePicker) { Text(stringResource(Res.string.watch_add_subtitles)) }
        }
        captions?.let { Text(it.title, Modifier.fillMaxWidth().padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium.merge(contentStyle(it.title))) }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Row { Text(stringResource(Res.string.watch_caption_error), Modifier.weight(1f)); TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) } }
        if (playerError != null) Text(stringResource(Res.string.watch_player_error, playerError.orEmpty()), color = MaterialTheme.colorScheme.error)
        FlowRow(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { controller?.seekTo(position - 5_000) }) { Text("−5s") }
            TextButton(onClick = { if (playing) controller?.pause() else controller?.play() }) { Text(stringResource(if (playing) Res.string.watch_pause else Res.string.watch_play)) }
            TextButton(onClick = { controller?.seekTo(position + 5_000) }) { Text("+5s") }
            Choice(stringResource(Res.string.watch_rate), rate, listOf(0.75f, 1f, 1.25f).map { it to "${it}×" }) { rate = it; controller?.setRate(it) }
            TextButton(enabled = active >= 0 || loopIndex != null, onClick = {
                loopIndex = if (loopIndex != null) null else active.takeIf { it >= 0 }
                loopIndex?.let { controller?.seekTo(cueList[it].startMs); controller?.play() }
            }) { Text(stringResource(if (loopIndex != null) Res.string.watch_stop_repeat else Res.string.watch_repeat)) }
            TextButton(enabled = active >= 0, onClick = {
                loopIndex = null; controller?.pause(); shadowIndex = active
            }) { Text(stringResource(Res.string.speech_shadow)) }
            TextButton(onClick = { showTranslation = !showTranslation }) { Text(stringResource(if (showTranslation) Res.string.watch_hide_translation else Res.string.watch_translation)) }
            if (!follow) TextButton(onClick = { follow = true }) { Text(stringResource(Res.string.watch_back_current)) }
        }
        if (showTranslation) {
            if (translating) LinearProgressIndicator(Modifier.fillMaxWidth())
            translation?.let { Text(it, Modifier.fillMaxWidth().padding(12.dp), style = contentStyle(it)) }
            if (translationFailed) TextButton(onClick = { translationAttempt++ }) { Text(stringResource(Res.string.retry)) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 700.dp || maxWidth > maxHeight * 1.4f
            val player: @Composable (Modifier) -> Unit = { m ->
                if (path.isNotBlank()) LocalVideoPlayer(path, { controller = it }, m) else YoutubePlayer(videoId, { controller = it }, m)
            }
            val transcript: @Composable (Modifier) -> Unit = { m ->
                if (cueList.isEmpty()) Box(m.padding(16.dp)) { Text(stringResource(Res.string.watch_no_captions)) }
                else LazyColumn(m, state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(cueList) { index, cue ->
                        Column(Modifier.fillMaxWidth().background(if (index == active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface).padding(10.dp)) {
                            TextButton(onClick = { controller?.seekTo(cue.startMs); follow = true }) { Text(videoTime(cue.startMs)) }
                            CueWords(cue, lang, highlighter, words, settings.readerFontSize) { select(index, it) }
                        }
                    }
                }
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { player(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
                transcript(Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                player(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                transcript(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
    shadowIndex?.let { start ->
        ShadowingPanel(vm.container, settings, lang, document?.id, cueList.map { it.text }, start,
            dismiss = { controller?.pause(); shadowIndex = null }, autoListen = true, replay = { index ->
                val cue = cueList[index]
                val player = checkNotNull(controller)
                replayShadowCue(player, cue.startMs, cue.endMs, rate)
            })
    }
    val doc = document
    if (doc != null) selection?.let { DictionarySheet(vm, settings, doc, it, words, emptyList(), dismiss = { selection = null }) }
}

@Composable private fun CueWords(cue: SubtitleCue, lang: LanguageSpec, highlighter: VocabHighlighter, words: List<VocabEntry>, fontSize: Int, select: (Int) -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val matches = remember(cue, highlighter) { highlighter.findMatches(cue.text) }
    val boxes = words.associate { normalizeForMatch(it.word, lang) to it.leitnerBox.coerceIn(1, 5) }
    val colors = (1..5).associateWith { boxColor(it) }
    val text = buildAnnotatedString {
        append(cue.text)
        matches.forEach { m -> addStyle(SpanStyle(color = colors.getValue(boxes[normalizeForMatch(cue.text.substring(m.range), lang)] ?: 1)), m.range.first, m.range.last + 1) }
    }
    val onSelect by rememberUpdatedState(select)
    Text(text, Modifier.fillMaxWidth().pointerInput(cue) { detectTapGestures { point -> layout?.getOffsetForPosition(point)?.let(onSelect) } },
        style = MaterialTheme.typography.bodyLarge.merge(contentStyle(cue.text)).copy(fontSize = fontSize.sp), onTextLayout = { layout = it })
}
