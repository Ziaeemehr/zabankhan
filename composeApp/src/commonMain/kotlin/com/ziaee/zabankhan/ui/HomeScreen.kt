package com.ziaee.zabankhan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.discover.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.srs.*
import com.ziaee.zabankhan.statistics.*
import com.ziaee.zabankhan.ui.theme.*
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.unit.sp
import com.ziaee.zabankhan.watch.RecentVideo
import com.ziaee.zabankhan.watch.WatchHistory

/** Home dashboard ported from French Reader's ui/home/HomeScreen.kt: progress, resume cards, review, then resource cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(
    vm: AppViewModel, settings: AppSettings, open: (Long) -> Unit, add: () -> Unit,
    review: () -> Unit, watch: () -> Unit, progress: () -> Unit = {}, discover: () -> Unit = {},
    grammar: () -> Unit = {}, exams: () -> Unit = {}, openSettings: () -> Unit = {},
    words: () -> Unit = {}, news: () -> Unit = {}, watchVideo: (String, String, String) -> Unit = { _, _, _ -> },
    datasets: () -> Unit = {},
) {
    val lang = requireNotNull(settings.learningLang)
    val spec = requireNotNull(Languages.byCode(lang))
    val recentState by remember(lang) { vm.container.texts.observeRecent(lang) }.collectAsState(initial = null)
    val wordsState by remember(lang) { vm.container.vocab.observeAll(lang) }.collectAsState(initial = null)
    val activityState by remember(lang) { vm.container.database.activity().observe(lang) }.collectAsState(initial = null)
    val attemptsState by remember(lang) { vm.container.database.shadowAttempts().observe(lang) }.collectAsState(initial = null)
    var now by remember { mutableStateOf(nowMs()) }
    var reviewState by remember(lang) { mutableStateOf<List<ReviewLogEntry>?>(null) }
    LaunchedEffect(lang) { while (true) {
        now = nowMs(); reviewState = vm.container.vocab.reviewLogSince(lang, 0); delay(30_000)
    } }
    val continueWatching by produceState<RecentVideo?>(null, lang) { value = WatchHistory(vm.container.cache).read(spec).firstOrNull() }
    val headlineCount by produceState(0, lang) {
        val sources = ContentSources.forLanguage(spec).filterIsInstance<NewsSource>()
        value = loadNews(sources) { NewsFetcher(vm.container.http).fetchItems(it.feedUrl, 25) }.headlines.size
    }
    val reviews = reviewState.orEmpty()
    val allWords = wordsState.orEmpty()
    val activity = activityState.orEmpty()
    val today = localToday()
    val statsReady = wordsState != null && activityState != null && attemptsState != null && reviewState != null
    val remaining = remainingNewCards(reviews, settings.dailyNewCards, VocabSrs.startOfDayMs(now))
    val due = reviewableCount(allWords.map { it.reviewCard() }, now, remaining)
    val languageLabel = languageName(spec)
    val itemModifier = Modifier.fillMaxWidth().padding(horizontal = ZabankhanDesign.spacing.small, vertical = ZabankhanDesign.spacing.half)
    Column(Modifier.fillMaxSize()) {
        val title = stringResource(Res.string.app_name)
        EditorialTopAppBar(title, titleStyle = editorialStyle(title, EditorialRole.SectionTitle).copy(fontSize = 20.sp, lineHeight = 24.sp), actions = {
            // zabankhan keeps several learning languages; a compact dropdown switches between them.
            var languageMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { languageMenu = true }) {
                    Text(languageLabel, maxLines = 1)
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(Res.string.learning_languages))
                }
                DropdownMenu(languageMenu, { languageMenu = false }) {
                    settings.learningLangs.mapNotNull { Languages.byCode(it) }.forEach { option ->
                        DropdownMenuItem(text = { Text(languageName(option)) }, onClick = {
                            languageMenu = false
                            vm.action {
                                vm.container.settings.setLearningLang(option.code)
                                if (settings.meaningLang == option.code) vm.container.settings.setMeaningLang(if (option.code == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code)
                            }
                        })
                    }
                }
            }
            IconButton(onClick = discover) { Icon(Icons.Default.Search, stringResource(Res.string.h2_find_content)) }
            // French Reader adds texts from its bottom bar; zabankhan's bottom bar has no add slot.
            ImportAddMenu(vm, settings, add, open, watch, discover, openDatasets = datasets, trigger = { onImport, busy ->
                IconButton(onClick = onImport, enabled = !busy) { Icon(Icons.Default.Add, stringResource(Res.string.add_text)) }
            })
            IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, stringResource(Res.string.nav_settings)) }
        })
        LazyColumn(Modifier.fillMaxSize()) {
            if (statsReady) item {
                ProgressCard(
                    HomeProgress(activityStreak(activity, today, reviews, attemptsState.orEmpty()), allWords.count { it.createdAtMs >= com.ziaee.zabankhan.data.nowMs() - 7 * 86_400_000L },
                        allWords.count { it.srsState().status() == VocabStatus.LEARNED }, activityMinutes(activity, today, 1).total.roundToInt(), settings.dailyGoalMinutes),
                    onOpen = progress, onSavedWords = words,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            recentState?.firstOrNull()?.let { doc ->
                item { ContinueReadingCard(doc, onClick = { open(doc.id) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) }
            }
            continueWatching?.let { video ->
                item {
                    ContinueWatchingCard(video, onClick = { watchVideo(video.videoId, video.path.orEmpty(), video.subs.orEmpty()) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            if (statsReady) item { DailyReviewCard(due, review, itemModifier) }
            item { WatchVideosCard(languageLabel, watch, itemModifier) }
            item { TodayNewsCard(headlineCount, languageLabel, news, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            item { GrammarCard(languageLabel, grammar, itemModifier) }
            item { DatasetEntryCard(lang, datasets, itemModifier) }
            if (com.ziaee.zabankhan.datapack.LanguageDataPacks.available(lang)) item { ExamsCard(exams, itemModifier) }
        }
    }
}

@Composable fun ReviewSummaryCard(due: Int, noWords: Boolean, review: () -> Unit, reviewedToday: Boolean = false) {
    EditorialCard(containerColor = if (due > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val title = when {
                due > 0 -> stringResource(Res.string.due_today, due)
                noWords -> stringResource(Res.string.ed_review_first_words)
                reviewedToday -> stringResource(Res.string.ed_review_done)
                else -> stringResource(Res.string.ed_review_unscheduled)
            }
            Text(title, style = editorialStyle(title, EditorialRole.SectionTitle))
            Text(stringResource(if (due > 0) Res.string.ed_review_hint else if (noWords) Res.string.ed_words_empty_body else Res.string.ed_review_done_hint), style = MaterialTheme.typography.bodyMedium)
            if (due > 0) EditorialPrimaryButton(stringResource(Res.string.start_review), review)
        }
    }
}

// Imported EPUBs are stored as extracted text. Use source metadata only when retained.
internal fun documentIcon(text: TextDocument): ImageVector = when {
    text.pdfPath != null -> Icons.Default.PictureAsPdf
    text.sourceUrl?.substringBefore('?')?.endsWith(".epub", ignoreCase = true) == true -> Icons.Default.MenuBook
    text.externalKey?.startsWith("video:") == true -> Icons.Default.PlayCircleOutline
    else -> Icons.AutoMirrored.Filled.Article
}
@Composable internal fun documentTypeLabel(text: TextDocument): String = stringResource(when {
    text.pdfPath != null -> Res.string.ed_type_pdf
    text.sourceUrl?.substringBefore('?')?.endsWith(".epub", ignoreCase = true) == true -> Res.string.ed_type_epub
    text.externalKey?.startsWith("video:") == true -> Res.string.ed_type_video
    else -> Res.string.ed_type_text
})
internal fun editorialDate(ms: Long): String = kotlin.time.Instant.fromEpochMilliseconds(ms)
    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
