package com.ziaee.zabankhan.ui

// Port of FrenchReader ui/VocabReviewScreen.kt + ui/VocabFlashcard.kt (overview, box ladder, flip card, answers, summary).
// Scheduling stays zabankhan's (srs package, VocabDao); French assumptions go through LanguageSpec / the entry's lang.
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.srs.*
import com.ziaee.zabankhan.statistics.activityStreak
import com.ziaee.zabankhan.statistics.localToday
import com.ziaee.zabankhan.ui.reader.boxColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.ziaee.zabankhan.translate.MeaningOption
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

enum class ReviewStage { OVERVIEW, REVIEW, SUMMARY }

/** FrenchReader's VocabPrefs default daily goal; zabankhan has no per-card goal setting. */
private const val REVIEW_DAILY_GOAL = 20

/** Identity holder per card presentation (FrenchReader CardSlot), so AnimatedContent never merges equal entries. */
class CardSlot(entry: VocabEntry) { var entry by mutableStateOf(entry) }

class ReviewViewModel(private val container: AppContainer, private val settings: AppSettings, private val listScope: Long = -1L) : ViewModel() {
    private val lang = requireNotNull(settings.learningLang)
    var meaningTarget by mutableStateOf(meaningTarget(lang, settings)); private set
    var meaningOptions by mutableStateOf<List<MeaningOption>>(emptyList()); private set
    private var meaningOptionsJob: Job? = null
    private val meaningOptionsCache = mutableMapOf<Pair<Long, String>, List<MeaningOption>>()
    fun selectMeaningTarget(target: String) {
        if (meaningTarget == target) return
        meaningTarget = target
        sentenceTranslation = null
        loadMeaningOptions()
        translateSentence()
    }
    /** French Reader's slot-guarded alternatives lookup; language is supplied by each card. */
    fun loadMeaningOptions() {
        val card = slot ?: return
        val entry = card.entry
        val target = meaningTarget
        val key = entry.id to target
        meaningOptionsJob?.cancel()
        meaningOptionsCache[key]?.let { meaningOptions = it; return }
        meaningOptions = emptyList()
        meaningOptionsJob = viewModelScope.launch {
            val options = try {
                container.translations(settings).lookupWord(entry.word, entry.sentence, entry.lang, target).getOrNull()
                    ?.senses.orEmpty().flatMap { sense -> sense.meanings.map { MeaningOption(it, null, sense.pos) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
            if (slot !== card || meaningTarget != target || card.entry.word != entry.word || card.entry.sentence != entry.sentence) return@launch
            meaningOptionsCache[key] = options
            meaningOptions = options
        }
    }
    val intervals get() = settings.reviewIntervalDays
    var loading by mutableStateOf(true); private set
    var failed by mutableStateOf(false); private set
    var stage by mutableStateOf(ReviewStage.OVERVIEW); private set
    var overview by mutableStateOf<ReviewOverview?>(null); private set
    var hasCards by mutableStateOf(false); private set
    var streak by mutableIntStateOf(0); private set
    var session by mutableStateOf<ReviewSession?>(null); private set
    var slot by mutableStateOf<CardSlot?>(null); private set
    var isLearnedReview by mutableStateOf(false); private set
    var learnedReviewed by mutableIntStateOf(0); private set
    var busy by mutableStateOf(false); private set
    /** (boxBefore, boxAfter) of the last answer, shown once as a snackbar. */
    var move by mutableStateOf<Pair<Int, Int>?>(null); private set
    var dayTotals by mutableStateOf(ReviewDayTotals(0, 0, 0)); private set
    var studyTimeMs by mutableLongStateOf(0L); private set
    var nextScheduledAtMs by mutableStateOf<Long?>(null); private set
    var listNames by mutableStateOf<Map<Long, String>>(emptyMap()); private set
    var sentenceTranslation by mutableStateOf<String?>(null); private set
    var sentenceTranslationLoading by mutableStateOf(false); private set
    var sentenceTranslationError by mutableStateOf(false); private set
    var pronunciationEnabled by mutableStateOf(settings.cardPronunciationPractice); private set
    private var pronunciationStats by mutableStateOf(CardPronunciationStats())
    val pronunciationAccuracy get() = pronunciationStats.accuracy
    private val shadowingController = ShadowingController(viewModelScope, createSpeechRecognizer(), saveAttempt = {
        container.database.shadowAttempts().insert(it)
        pronunciationStats = pronunciationStats.after(it.matched, it.total)
    })
    val shadowing = shadowingController.phase
    val hasRecording get() = shadowingController.hasRecording
    fun setPronunciation(enabled: Boolean) {
        pronunciationEnabled = enabled
        shadowingController.cancel()
        viewModelScope.launch { container.settings.setCardPronunciationPractice(enabled) }
        current?.let(::setShadowTarget)
    }
    private fun setShadowTarget(entry: VocabEntry) {
        shadowingController.target(learningSpeechLanguage(entry.lang), entry.textId, flashcardShadowText(entry).orEmpty())
    }
    fun startCardRecording(): Boolean {
        if (current?.let(::flashcardShadowText) == null) return false
        createTts().stop()
        return shadowingController.listen()
    }
    fun endCardRecording() = shadowingController.finishRecording()
    fun cancelCardRecording() = shadowingController.cancel()
    fun replayCardRecording() { createTts().stop(); shadowingController.replayMine() }
    private val timer = ReviewStudyTimer()
    val current get() = session?.current
    val canUndo get() = stage == ReviewStage.REVIEW && session?.answers?.isNotEmpty() == true && !busy

    private suspend fun scopedEntries() = container.vocab.getAll(lang).filter {
        listScope == VOCAB_SCOPE_ALL || (if (it.listId == null) VOCAB_SCOPE_UNFILED else it.listId) == listScope
    }
    init { load() }

    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; failed = false
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true } finally { busy = false; loading = false }
        }
    }

    fun load() = run {
        val now = nowMs()
        val entries = scopedEntries()
        hasCards = entries.isNotEmpty()
        val logs = container.vocab.reviewLogSince(lang, 0)
        overview = reviewOverview(entries, logs, settings.dailyNewCards, now, intervals = intervals)
        listNames = container.vocab.observeLists(lang).first().associate { it.id to it.name }
        streak = activityStreak(container.database.activity().observe(lang).first(), localToday(), logs,
            container.database.shadowAttempts().observe(lang).first())
    }

    private fun begin(cards: List<VocabEntry>, learned: Boolean) {
        pronunciationStats = CardPronunciationStats()
        isLearnedReview = learned
        session = ReviewSession(cards)
        show()
        if (current == null) finish() else { stage = ReviewStage.REVIEW; timer.resume(nowMs()) }
    }

    fun startReview(box: Int? = null) = run {
        val now = nowMs()
        val dayStart = VocabSrs.startOfDayMs(now)
        val entries = scopedEntries()
        val remaining = remainingNewCards(container.vocab.reviewLogSince(lang, 0), settings.dailyNewCards, dayStart)
        val ids = buildReviewQueue(entries.map { it.reviewCard() }, now, dayStart, remaining, box).map { it.id }
        begin(ids.mapNotNull { id -> entries.firstOrNull { it.id == id } }, learned = false)
    }

    fun startLearnedReview() = run {
        learnedReviewed = 0
        begin(scopedEntries().filter { it.learned }.sortedBy { it.id }, learned = true)
    }

    private fun show() {
        meaningOptionsJob?.cancel()
        meaningOptions = emptyList()
        sentenceTranslation = null; sentenceTranslationError = false; sentenceTranslationLoading = false
        shadowingController.cancel()
        slot = current?.let(::CardSlot)
        current?.let(::setShadowTarget)
    }

    fun answer(answer: VocabAnswer) = run {
        val state = session ?: return@run
        val before = state.current ?: return@run
        val now = nowMs()
        val learnedKeep = isLearnedReview && answer != VocabAnswer.FORGOT
        val boxBefore = if (isLearnedReview) 5 else before.leitnerBox
        val after = if (learnedKeep) before.copy(lastReviewedAtMs = now) else {
            val schedule = VocabSrs.apply(before.srsState(), answer, now, intervals)
            // "Again" keeps the card due today so leaving early does not push it to tomorrow.
            before.withState(if (answer == VocabAnswer.FORGOT) schedule.copy(nextReviewAtMs = now) else schedule)
        }
        val boxAfter = if (learnedKeep) 5 else after.leitnerBox
        val log = container.vocab.recordReview(after, ReviewLogEntry(entryId = before.id, lang = before.lang, timestampMs = now,
            knew = answer != VocabAnswer.FORGOT, boxBefore = boxBefore, boxAfter = boxAfter))
        session = state.answered(SessionAnswer(before, answer, after, log, requeued = !isLearnedReview && answer == VocabAnswer.FORGOT))
        if (isLearnedReview) learnedReviewed++
        move = boxBefore to boxAfter
        show()
        if (current == null) finish()
    }

    fun undo() = run {
        val state = session ?: return@run
        val last = state.answers.lastOrNull() ?: return@run
        container.vocab.undoReview(last.before, last.logId)
        if (isLearnedReview) learnedReviewed = (learnedReviewed - 1).coerceAtLeast(0)
        session = state.undo(); move = null
        show()
    }

    /** Text-only edit of the card on screen; its schedule and the session order stay as they are. */
    fun edit(updated: VocabEntry) = run {
        fun VocabEntry.patched() = if (id == updated.id) copy(word = updated.word, meaning = updated.meaning, sentence = updated.sentence, listId = updated.listId) else this
        val now = current?.patched() ?: return@run
        container.vocab.update(now)
        session = session?.let { s -> s.copy(cards = s.cards.map { it.patched() },
            answers = s.answers.map { it.copy(before = it.before.patched(), after = it.after.patched()) }) }
        val changedText = slot?.entry?.let { it.word != now.word || it.sentence != now.sentence } == true
        if (changedText) {
            meaningOptionsCache.keys.removeAll { it.first == now.id }
            show()
        } else slot?.entry = now
    }

    fun consumeMove() { move = null }

    private fun finish() {
        val now = nowMs()
        studyTimeMs = timer.pause(now).values.sum()
        viewModelScope.launch {
            try {
                val entries = scopedEntries()
                nextScheduledAtMs = entries.filter { !it.learned && it.nextReviewAtMs > now }.minOfOrNull { it.nextReviewAtMs }
                dayTotals = reviewDayTotals(container.vocab.reviewLogSince(lang, VocabSrs.startOfDayMs(now)), VocabSrs.startOfDayMs(now))
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
        }
        stage = ReviewStage.SUMMARY
    }

    fun backToOverview() { shadowingController.cancel(); stage = ReviewStage.OVERVIEW; session = null; slot = null; load() }

    fun translateSentence() {
        val entry = current ?: return
        if (entry.sentence.isBlank()) return
        val requested = slot
        val target = meaningTarget
        sentenceTranslationLoading = true; sentenceTranslationError = false
        viewModelScope.launch {
            val result = try {
                container.translations(settings).translateText(entry.sentence, entry.lang, target)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
            if (slot !== requested || meaningTarget != target) return@launch
            result.fold({ sentenceTranslation = it }, { sentenceTranslationError = true })
            sentenceTranslationLoading = false
        }
    }

    override fun onCleared() { meaningOptionsJob?.cancel(); shadowingController.cancel(); timer.pause(nowMs()) }
}

/** Same rule as the dictionary sheet: meanings in the chosen language unless it is the card's own language. */
internal fun meaningTarget(cardLang: String, settings: AppSettings): String =
    settings.meaningLang.takeIf { it != cardLang } ?: if (cardLang == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(container: AppContainer, settings: AppSettings, onBack: () -> Unit = {}, onOpenSettings: () -> Unit = {}, listScope: Long = -1L, onOpenDatasets: () -> Unit = {}) {
    val vm = viewModel(key = "review:${container.hashCode()}:${settings.learningLang}:${settings.dailyNewCards}:${settings.reviewIntervalDays}:$listScope") { ReviewViewModel(container, settings, listScope) }
    val snackbar = remember { SnackbarHostState() }
    val tts = remember { createTts() }
    DisposableEffect(Unit) { onDispose { tts.stop() } }
    var revealedSlot by remember { mutableStateOf<CardSlot?>(null) }
    val permission = rememberMicPermission()
    var pendingPractice by remember { mutableStateOf(false) }
    var showModel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val language = learningSpeechLanguage(requireNotNull(settings.learningLang))
    val support = remember(language) { recognitionSupport(language) }
    val manager = remember(language) { VoskModelManager(language) }
    var downloadProgress by remember(language) { mutableStateOf<Float?>(null) }
    val modelFailed = stringResource(Res.string.shadowing_model_failed)
    val onDeviceUnavailable = stringResource(Res.string.g_on_device_unavailable)
    fun enablePractice() {
        if (!permission.granted) { pendingPractice = true; permission.request(); return }
        if (settings.speechRecognitionEngine == "offline" && support.downloadableModels && language.offlineSpeechModel != null && !manager.isInstalled()) {
            showModel = true; vm.setPronunciation(false)
        } else if (settings.speechRecognitionEngine != "offline" || support.onDeviceAvailable || (support.downloadableModels && language.offlineSpeechModel == null)) vm.setPronunciation(true)
        else scope.launch { snackbar.showSnackbar(onDeviceUnavailable) }
    }
    LaunchedEffect(permission.granted) { if (pendingPractice && permission.granted) { pendingPractice = false; enablePractice() } }
    LaunchedEffect(vm.stage) { if (vm.stage == ReviewStage.REVIEW && vm.pronunciationEnabled) enablePractice() }
    DisposableEffect(vm) { onDispose { vm.cancelCardRecording() } }
    if (showModel) ShadowingModelDialog(downloadProgress, support.systemAvailable, { showModel = false }, onDownload = {
        downloadProgress = 0f
        scope.launch {
            try { manager.download(container.http) { downloadProgress = it }; showModel = false; vm.setPronunciation(true) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { snackbar.showSnackbar(modelFailed) }
            finally { downloadProgress = null }
        }
    }, onUseAndroid = {
        scope.launch { container.settings.setSpeechRecognitionEngine("system"); RecognitionSettings.engine = "system"; showModel = false; vm.setPronunciation(true) }
    }, modelLanguage = languageName(language), modelSizeMb = language.offlineSpeechModel?.downloadMb ?: 0, systemLabel = stringResource(Res.string.shadowing_use_android_engine))
    var editing by remember { mutableStateOf<VocabEntry?>(null) }
    val currentRevealed = vm.slot != null && vm.slot === revealedSlot
    LaunchedEffect(settings.meaningLang) { vm.selectMeaningTarget(meaningTarget(requireNotNull(settings.learningLang), settings)) }
    LaunchedEffect(currentRevealed, vm.slot, vm.current?.word, vm.current?.sentence, vm.meaningTarget) {
        if (currentRevealed) vm.loadMeaningOptions()
    }
    LaunchedEffect(vm.stage) { if (vm.stage == ReviewStage.OVERVIEW) vm.load() }
    LaunchedEffect(vm.move) {
        val (before, after) = vm.move ?: return@LaunchedEffect
        snackbar.showSnackbar(when {
            after > before -> getString(Res.string.review_moved_forward, after)
            after < before -> getString(Res.string.review_returned_box_one)
            else -> getString(Res.string.review_stayed_box, after)
        })
        vm.consumeMove()
    }
    LaunchedEffect(vm.slot, currentRevealed) {
        tts.stop()
        val card = vm.current ?: return@LaunchedEffect
        if (currentRevealed) vm.translateSentence()
        val autoplay = settings.reviewAudioAutoplay
        when {
            currentRevealed && autoplay.back -> flashcardSentenceAudioText(card)?.let { tts.pronounce(it, card.lang, settings) }
            !currentRevealed && autoplay.front -> flashcardWordAudioText(card)?.let { tts.pronounce(it, card.lang, settings) }
        }
    }
    // Held so the card container does not collapse while the slot is briefly null between answers.
    var lastSlot by remember { mutableStateOf<CardSlot?>(null) }
    LaunchedEffect(vm.slot) { vm.slot?.let { lastSlot = it } }
    LaunchedEffect(vm.stage) { if (vm.stage != ReviewStage.REVIEW) lastSlot = null }
    editing?.let { e ->
        val decks = listOf(0L to stringResource(Res.string.unfiled)) + vm.listNames.toList()
        EditWordDialog(e, decks, { editing = null }) { vm.edit(it) }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.vocab_review_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (vm.stage == ReviewStage.OVERVIEW) onBack() else vm.backToOverview() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.accessibility_back))
                    }
                },
                actions = {
                    if (vm.stage == ReviewStage.REVIEW) IconToggleButton(checked = vm.pronunciationEnabled, onCheckedChange = { if (it) enablePractice() else vm.setPronunciation(false) }) {
                        Icon(Icons.Default.Mic, stringResource(Res.string.review_pronunciation_practice))
                    }
                    if (vm.stage == ReviewStage.REVIEW) IconButton(onClick = vm::undo, enabled = vm.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(Res.string.review_undo))
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, stringResource(Res.string.review_open_settings)) }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            when {
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.stage == ReviewStage.OVERVIEW -> vm.overview?.let { ReviewOverviewPane(vm, it, onOpenDatasets) }
                    ?: if (vm.failed) TextButton(onClick = vm::load, modifier = Modifier.align(Alignment.Center)) { Text(stringResource(Res.string.retry)) } else Unit
                vm.stage == ReviewStage.SUMMARY -> ReviewSummary(vm) { vm.backToOverview() }
                else -> {
                    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
                    AnimatedContent(
                        targetState = vm.slot ?: lastSlot,
                        transitionSpec = { (slideInHorizontally { dir * it / 4 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally { -dir * it / 4 } + fadeOut(tween(160))) using SizeTransform(clip = false) },
                        modifier = Modifier.fillMaxSize(),
                        label = "card"
                    ) { slot ->
                        slot?.let { sl ->
                            ReviewCard(vm, sl.entry, sl === revealedSlot, sl === vm.slot && !vm.busy,
                                onReveal = { revealedSlot = sl },
                                onPlay = { text -> vm.cancelCardRecording(); tts.stop(); tts.pronounce(text, sl.entry.lang, settings) },
                                onEdit = { editing = sl.entry })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewOverviewPane(vm: ReviewViewModel, o: ReviewOverview, onOpenDatasets: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(Res.string.review_today_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GoalRing(o.reviewedToday.coerceAtMost(REVIEW_DAILY_GOAL), REVIEW_DAILY_GOAL, Modifier.size(72.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(Res.string.review_goal_progress, o.reviewedToday.coerceAtMost(REVIEW_DAILY_GOAL), REVIEW_DAILY_GOAL))
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalFireDepartment, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.review_streak, vm.streak), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Metric(o.dueCount.toString(), stringResource(Res.string.review_due_count))
            Metric(o.newCount.toString(), stringResource(Res.string.review_new_count))
            Metric(stringResource(Res.string.review_minutes_short, o.estimatedMinutes), stringResource(Res.string.review_estimated_time))
        }
        if (o.dueCount + o.newCount == 0) {
            Text(
                stringResource(if (o.newCapReached) Res.string.review_new_cap_reached else Res.string.review_no_cards_due),
                style = MaterialTheme.typography.bodyMedium
            )
            if (!vm.hasCards) {
                EditorialPrimaryButton(stringResource(Res.string.dataset_home_title), onOpenDatasets)
            }
        }
        Button(onClick = { vm.startReview() }, enabled = o.dueCount + o.newCount > 0 && !vm.busy, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 52.dp)) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.review_start))
        }
        Text(stringResource(Res.string.review_box_tap_hint), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        BoxLadder(o.boxes, onBoxClick = { vm.startReview(it) })
        LearnedWordsCard(vm, o.learnedCount)
    }
}

@Composable
private fun LearnedWordsCard(vm: ReviewViewModel, learnedCount: Int) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, null, tint = boxColor(5))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.review_learned_words), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(Res.string.review_learned_progress, vm.learnedReviewed, learnedCount), style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = { vm.startLearnedReview() }, enabled = learnedCount > 0 && !vm.busy) {
                    Text(stringResource(Res.string.review_learned_start))
                }
            }
        }
    }
}

@Composable
private fun GoalRing(done: Int, goal: Int, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            drawArc(fill, -90f, (done.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f) * 360f, false, style = stroke)
        }
        Text("$done/$goal", style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable private fun Metric(value: String, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun BoxLadder(boxes: List<ReviewBoxSummary>, onBoxClick: (Int) -> Unit) {
    val icons = listOf(Icons.Default.School, Icons.Default.AutoStories, Icons.Default.Psychology, Icons.AutoMirrored.Filled.TrendingUp, Icons.Default.EmojiEvents)
    val fractions = boxBarFractions(boxes.map { it.count })
    boxes.forEachIndexed { i, box ->
        val enabled = box.dueCount > 0
        val color = boxColor(box.box)
        Card(onClick = { onBoxClick(box.box) }, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icons[(box.box - 1).coerceIn(0, icons.lastIndex)], null, tint = color); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.review_box_label, box.box), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(Res.string.review_box_details, box.count, box.intervalDays.toInt(), box.dueCount), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(box.nextReviewAtMs?.let { dateLabel(it) } ?: "—", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(fractions[i]).fillMaxHeight().background(color))
                    }
                    if (enabled) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                            Text(box.dueCount.toString(), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxDots(box: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..5) {
            val target = if (i <= box) boxColor(i) else MaterialTheme.colorScheme.outlineVariant
            val color by animateColorAsState(target, label = "dot")
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun AccuracyRing(percent: Int) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            drawArc(fill, -90f, percent.coerceIn(0, 100) / 100f * 360f, false, style = stroke)
        }
        Text("$percent%", style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** Centered, direction-from-content text: Persian meanings RTL, learning-language text LTR, without flipping the screen. */
private fun centeredContent(base: TextStyle) = base.copy(textDirection = TextDirection.Content, textAlign = TextAlign.Center)

@Composable
private fun ReviewCard(
    vm: ReviewViewModel,
    entry: VocabEntry,
    revealed: Boolean,
    interactive: Boolean,
    onReveal: () -> Unit,
    onPlay: (String) -> Unit,
    onEdit: () -> Unit,
) {
    val session = vm.session
    ReviewCardLayout(
        header = {
            entry.listId?.let { vm.listNames[it] }?.let { cornerLabel ->
                Text(cornerLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            BoxDots(if (vm.isLearnedReview) 5 else entry.leitnerBox)
            Spacer(Modifier.height(8.dp))
            val position = session?.progressPosition ?: 0
            val total = session?.totalCards ?: 0
            Text(stringResource(Res.string.review_progress, position, total), style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { if (total == 0) 0f else position.toFloat() / total }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        },
        card = { cardModifier ->
            EntryFlashcard(
                entry = entry,
                revealed = revealed,
                interactive = interactive,
                onFlip = onReveal,
                onPlayWord = { onPlay(entry.word) },
                onPlaySentence = { onPlay(entry.sentence) },
                sentenceTranslation = if (entry.id == vm.current?.id) vm.sentenceTranslation else null,
                sentenceTranslationLoading = vm.sentenceTranslationLoading,
                sentenceTranslationError = vm.sentenceTranslationError,
                onEdit = onEdit,
                meaningOptions = if (entry.id == vm.current?.id) vm.meaningOptions else emptyList(),
                meaningTarget = vm.meaningTarget,
                onMeaningSelected = { vm.edit(entry.copy(meaning = it)) },
                modifier = cardModifier
            )
        },
        practice = {
        if (revealed && interactive && vm.pronunciationEnabled && flashcardShadowText(entry) != null) {
            val phase by vm.shadowing.collectAsState()
            CardShadowingRow(ShadowingState(phase, vm.hasRecording), vm::startCardRecording, vm::endCardRecording,
                vm::cancelCardRecording, vm::replayCardRecording,
                if (learningSpeechLanguage(entry.lang).direction == TextDir.RTL) LayoutDirection.Rtl else LayoutDirection.Ltr)
        }
        }
    ) {
        if (!revealed) Button(onClick = onReveal, enabled = interactive, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.vocab_reveal_meaning)) }
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val thisSession = stringResource(Res.string.interval_this_session)
            val state = entry.srsState()
            val learningStep = !vm.isLearnedReview && VocabSrs.isLearningStep(state, nowMs())
            ReviewAnswerButton(stringResource(Res.string.vocab_answer_again), if (vm.isLearnedReview) intervalLabel(vm.intervals[0]) else thisSession, Modifier.weight(1f), MaterialTheme.colorScheme.error, interactive) { vm.answer(VocabAnswer.FORGOT) }
            // A card still being learned has no "hard": plain Leitner, right or wrong.
            if (!learningStep) ReviewAnswerButton(stringResource(Res.string.vocab_answer_hard), if (vm.isLearnedReview) null else intervalLabel(VocabSrs.previewIntervalDays(state, VocabAnswer.HARD, vm.intervals)), Modifier.weight(1f), enabled = interactive) { vm.answer(VocabAnswer.HARD) }
            ReviewAnswerButton(stringResource(Res.string.vocab_answer_good), if (vm.isLearnedReview) null else intervalLabel(VocabSrs.previewIntervalDays(state, VocabAnswer.KNEW, vm.intervals)), Modifier.weight(1f), MaterialTheme.colorScheme.primary, interactive) { vm.answer(VocabAnswer.KNEW) }
        }
    }
}

/** Required fix over FrenchReader: the card takes the remaining height and scrolls inside; [answers] stay pinned on screen. */
@Composable
internal fun ReviewCardLayout(header: @Composable ColumnScope.() -> Unit, card: @Composable (Modifier) -> Unit, practice: @Composable () -> Unit = {}, answers: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        header()
        card(Modifier.weight(1f).fillMaxWidth())
        practice()
        Spacer(Modifier.height(14.dp))
        answers()
        Spacer(Modifier.height(12.dp))
    }
}

/** FrenchReader FlipCard, filling the height it is given; each face scrolls on its own. */
@Composable
private fun FlipCard(revealed: Boolean, onFlip: () -> Unit, interactive: Boolean, modifier: Modifier,
    front: @Composable ColumnScope.() -> Unit, back: @Composable ColumnScope.() -> Unit) {
    val rotation by animateFloatAsState(if (revealed) 180f else 0f, tween(350), label = "flip")
    val density = LocalDensity.current.density
    Card(
        modifier.graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }
            .clickable(enabled = interactive && !revealed, role = Role.Button, onClick = onFlip)
    ) {
        if (rotation <= 90f) CardFace(Modifier, front)
        else CardFace(Modifier.graphicsLayer { rotationY = 180f }, back)
    }
}

@Composable
private fun CardFace(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) = BoxWithConstraints(modifier.fillMaxSize()) {
    // heightIn(min = viewport) keeps short cards centred; long ones scroll inside the card.
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(26.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, content = content)
}

@Composable
internal fun EntryFlashcard(
    entry: VocabEntry,
    revealed: Boolean,
    interactive: Boolean,
    onFlip: () -> Unit,
    onPlayWord: () -> Unit,
    onPlaySentence: () -> Unit,
    sentenceTranslation: String?,
    sentenceTranslationLoading: Boolean,
    sentenceTranslationError: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier,
    meaningOptions: List<MeaningOption> = emptyList(),
    meaningTarget: String = Languages.PERSIAN.code,
    onMeaningSelected: (String) -> Unit = {},
) {
    var appliedMeaning by remember(entry.id, entry.word, entry.sentence, meaningTarget) { mutableStateOf<String?>(null) }
    var previousMeaning by remember(entry.id, entry.word, entry.sentence, meaningTarget) { mutableStateOf<String?>(null) }
    val wordStyle = centeredContent(MaterialTheme.typography.headlineMedium)
    FlipCard(
        revealed = revealed,
        onFlip = onFlip,
        interactive = interactive,
        modifier = modifier,
        front = {
            Text(entry.word, style = wordStyle)
            WordAudioButton(interactive, onPlayWord)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.review_tap_to_reveal), style = MaterialTheme.typography.labelSmall)
        },
        back = {
            Text(entry.word, style = wordStyle)
            WordAudioButton(interactive, onPlayWord)
            Spacer(Modifier.height(14.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
            IconButton(onClick = onEdit, enabled = interactive) {
                Icon(Icons.Default.Edit, stringResource(Res.string.action_edit))
            }
            val (meaning, alternatives) = splitMeaning(entry.meaning)
            meaning?.let {
                Text(it, style = centeredContent(MaterialTheme.typography.titleMedium))
                Spacer(Modifier.height(8.dp))
            }
            alternatives.forEach {
                Text(it, style = centeredContent(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (alternatives.isNotEmpty()) Spacer(Modifier.height(8.dp))
            if (revealed && meaningOptions.isNotEmpty()) {
                val options = reviewMeaningOptions(meaningOptions, entry.meaning, appliedMeaning, previousMeaning)
                com.ziaee.zabankhan.ui.reader.MeaningOptionPills(options, appliedMeaning, meaningTarget, enabled = interactive,
                    quietMeaning = previousMeaning) { option ->
                    val replacement = replaceMeaningLine(entry.meaning, option.text, Languages.byCode(entry.lang)?.vocabularyLessonTag)
                    if (replacement.meaning != entry.meaning) {
                        previousMeaning = replacement.previousLine
                        appliedMeaning = option.text
                        onMeaningSelected(replacement.meaning)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (entry.sentence.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.sentence, style = centeredContent(LocalTextStyle.current), fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f))
                    IconButton(onClick = onPlaySentence, enabled = interactive) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, stringResource(Res.string.accessibility_play_sentence))
                    }
                }
                when {
                    sentenceTranslationLoading && sentenceTranslation == null -> Text(stringResource(Res.string.translation_loading), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    sentenceTranslationError -> Text(stringResource(Res.string.error_translation_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    sentenceTranslation != null -> Text(sentenceTranslation, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic, style = centeredContent(MaterialTheme.typography.bodySmall))
                }
            }
        }
    )
}

@Composable
private fun WordAudioButton(interactive: Boolean, onPlay: () -> Unit) {
    IconButton(onClick = onPlay, enabled = interactive) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, stringResource(Res.string.accessibility_play_word))
    }
}

@Composable private fun intervalLabel(days: Long): String =
    stringResource(if (days == 1L) Res.string.interval_day else Res.string.interval_days, days.toInt())

@Composable internal fun ReviewAnswerButton(label: String, hint: String?, modifier: Modifier, color: Color = MaterialTheme.colorScheme.onSurface, enabled: Boolean = true, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, color), colors = ButtonDefaults.outlinedButtonColors(contentColor = color)) { Text(label, maxLines = 1) }
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable private fun ReviewSummary(vm: ReviewViewModel, onClose: () -> Unit) {
    val session = vm.session
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(Res.string.review_summary_title), style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(12.dp))
        AccuracyRing(session?.accuracy ?: 0); Spacer(Modifier.height(12.dp))
        SummaryLine(stringResource(Res.string.review_summary_cards), vm.dayTotals.cards.toString())
        SummaryLine(stringResource(Res.string.review_summary_forward), vm.dayTotals.movedForward.toString())
        SummaryLine(stringResource(Res.string.review_summary_returned), vm.dayTotals.returnedToBoxOne.toString())
        vm.pronunciationAccuracy?.let { SummaryLine(stringResource(Res.string.review_summary_pronunciation), "$it%") }
        SummaryLine(stringResource(Res.string.review_summary_time), formatDuration(vm.studyTimeMs))
        SummaryLine(stringResource(Res.string.review_summary_next), vm.nextScheduledAtMs?.let { dateLabel(it) } ?: "—")
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text(stringResource(Res.string.action_close)) }
    }
}
@Composable private fun SummaryLine(label: String, value: String) = Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(label, Modifier.weight(1f)); Text(value, style = MaterialTheme.typography.titleSmall) }

/** shortcut: FrenchReader prints "d MMM"; KMP has no localized month names, so show days from today instead. */
@Composable private fun dateLabel(ms: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val days = localToday(zone).daysUntil(Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date).coerceAtLeast(0)
    return intervalLabel(days.toLong())
}
internal fun formatDuration(ms: Long): String { val seconds = ms / 1000; return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s" }
