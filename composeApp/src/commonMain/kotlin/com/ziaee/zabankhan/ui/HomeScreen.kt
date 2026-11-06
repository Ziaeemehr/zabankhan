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

@OptIn(ExperimentalLayoutApi::class)
@Composable fun HomeScreen(
    vm: AppViewModel, settings: AppSettings, open: (Long) -> Unit, add: () -> Unit,
    review: () -> Unit, watch: () -> Unit, progress: () -> Unit = {}, discover: () -> Unit = {},
    grammar: () -> Unit = {}, exams: () -> Unit = {}, openSettings: () -> Unit = {},
) {
    val lang = requireNotNull(settings.learningLang)
    val recentState by remember(lang) { vm.container.texts.observeRecent(lang) }.collectAsState(initial = null)
    val textsState by remember(lang) { vm.container.texts.observeAll(lang) }.collectAsState(initial = null)
    val wordsState by remember(lang) { vm.container.vocab.observeAll(lang) }.collectAsState(initial = null)
    val activityState by remember(lang) { vm.container.database.activity().observe(lang) }.collectAsState(initial = null)
    val attemptsState by remember(lang) { vm.container.database.shadowAttempts().observe(lang) }.collectAsState(initial = null)
    var now by remember { mutableStateOf(nowMs()) }
    var reviewState by remember(lang) { mutableStateOf<List<ReviewLogEntry>?>(null) }
    LaunchedEffect(lang) { while (true) {
        now = nowMs(); reviewState = vm.container.vocab.reviewLogSince(lang, 0); delay(30_000)
    } }
    val reviews = reviewState.orEmpty()
    val recent = recentState.orEmpty()
    val texts = textsState.orEmpty()
    val words = wordsState.orEmpty()
    val activity = activityState.orEmpty()
    val attempts = attemptsState.orEmpty()
    val statsReady = wordsState != null && activityState != null && attemptsState != null && reviewState != null
    val remaining = remainingNewCards(reviews, settings.dailyNewCards, VocabSrs.startOfDayMs(now))
    val due = reviewableCount(words.map { it.reviewCard() }, now, remaining)
    val today = localToday()
    var deleteText by remember { mutableStateOf<TextDocument?>(null) }
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val title = stringResource(Res.string.app_name)
            Text(title, Modifier.weight(1f), style = editorialStyle(title, EditorialRole.ScreenTitle))
            EditorialIconButton(openSettings, Icons.Default.Settings, stringResource(Res.string.nav_settings))
        }
        Choice(stringResource(Res.string.learning_languages), lang, settings.learningLangs.mapNotNull { Languages.byCode(it) }.map { it.code to languageName(it) }) { code ->
            vm.action {
                vm.container.settings.setLearningLang(code)
                if (settings.meaningLang == code) vm.container.settings.setMeaningLang(if (code == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code)
            }
        }
        if (statsReady) LearningSummaryStrip(activityStreak(activity, today, reviews, attempts), words.count { it.srsState().status() == VocabStatus.LEARNED }, words.size,
            activityMinutes(activity, today, 1).total.roundToInt(), settings.dailyGoalMinutes, progress)
        HomeNews(vm, lang, discover)
        if (statsReady) ReviewSummaryCard(due, words.isEmpty(), review, reviews.any { it.timestampMs >= VocabSrs.startOfDayMs(now) })
        recent.firstOrNull()?.let { text ->
            EditorialSectionHeader(stringResource(Res.string.continue_reading))
            EditorialCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MetadataBadge(documentTypeLabel(text))
                        val metadata = listOfNotNull(text.sourceName?.takeIf { it.isNotBlank() }, editorialDate(text.createdAtMs)).joinToString(" · ")
                        Text(metadata, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.merge(contentStyle(metadata)), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(text.title, Modifier.fillMaxWidth(), style = editorialStyle(text.title))
                    // Character offsets are meaningful for extracted text, not PDF page positions.
                    if (text.pdfPath == null && text.body.isNotEmpty() && text.lastAccessedAtMs > 0) {
                        val fraction = text.lastOffset.toFloat() / text.body.length
                        EditorialProgressIndicator(fraction, stringResource(Res.string.ed_reading_progress, (fraction * 100).roundToInt().coerceIn(0, 100)))
                    }
                    EditorialPrimaryButton(stringResource(Res.string.ed_resume), { open(text.id) })
                }
            }
        }
        EditorialSectionHeader(stringResource(Res.string.ed_resources))
        EditorialCard {
            EditorialListRow(stringResource(Res.string.h2_discover), Icons.Default.Explore, discover)
            if (com.ziaee.zabankhan.datapack.LanguageDataPacks.available(lang)) {
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                EditorialListRow(stringResource(Res.string.pack_grammar), Icons.Default.MenuBook, grammar)
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                EditorialListRow(stringResource(Res.string.pack_tcf), Icons.Default.School, exams)
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            EditorialListRow(stringResource(Res.string.watch_video), Icons.Default.PlayCircleOutline, watch)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ImportAddMenu(vm, settings, add, open, watch, discover, trigger = { onImport, busy ->
                EditorialListRow(stringResource(Res.string.add_text), Icons.Default.Add, onImport, enabled = !busy)
            })
        }
        EditorialSectionHeader(stringResource(Res.string.recent_texts))
        if (textsState != null && texts.isEmpty()) EditorialEmptyState(Icons.Default.MenuBook, stringResource(Res.string.empty_library),
            stringResource(Res.string.ed_library_empty_body), actionLabel = stringResource(Res.string.add_text), onAction = add)
        (recent.ifEmpty { texts }.take(5)).forEach { text ->
            TextCard(text, { open(text.id) }, trailing = {
                var menu by remember { mutableStateOf(false) }
                Box {
                    EditorialIconButton({ menu = true }, Icons.Default.MoreVert, stringResource(Res.string.more_actions))
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(if (text.pinned) Res.string.unpin else Res.string.pin)) },
                            onClick = { menu = false; vm.action { vm.container.texts.setPinned(text.id, !text.pinned) } })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.delete)) }, onClick = { menu = false; deleteText = text })
                    }
                }
            })
        }
    }
    deleteText?.let { text -> ConfirmDelete(onDismiss = { deleteText = null }) { vm.action { vm.container.texts.delete(text) } } }
}

private data class HomeHeadline(val item: NewsItem, val source: String)
@Composable private fun HomeNews(vm: AppViewModel, lang: String, discover: () -> Unit) {
    var headlines by remember(lang) { mutableStateOf<List<HomeHeadline>?>(null) }
    LaunchedEffect(lang) {
        val spec = Languages.byCode(lang) ?: run { headlines = emptyList(); return@LaunchedEffect }
        val source = ContentSources.forLanguage(spec).filterIsInstance<NewsSource>().firstOrNull() ?: run { headlines = emptyList(); return@LaunchedEffect }
        try {
            headlines = mergeHeadlines(NewsFetcher(vm.container.http).fetchItems(source.feedUrl, 12), limit = 8).map { HomeHeadline(it, source.name) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { headlines = emptyList() }
    }
    if (headlines?.isEmpty() == true) return
    EditorialSectionHeader(stringResource(Res.string.ed_today_news))
    val state = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth * .86f).coerceAtMost(420.dp)
        LazyRow(state = state, flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (headlines == null) items(2) {
                EditorialCard(Modifier.width(cardWidth)) {
                    Spacer(Modifier.fillMaxWidth().height(232.dp))
                }
            }
            items(headlines.orEmpty(), key = { it.item.guid }) { headline ->
                EditorialCard(Modifier.width(cardWidth), onClick = discover) {
                    Column(Modifier.padding(20.dp).heightIn(min = 192.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetadataBadge(headline.source)
                        Text(headline.item.title, Modifier.fillMaxWidth(), style = editorialStyle(headline.item.title, EditorialRole.ArticleHeadline), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        headline.item.publishedAtMs?.let { Text(editorialDate(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TextCard(
    text: TextDocument, open: () -> Unit, trailing: @Composable (() -> Unit)? = null,
) {
    val container = com.ziaee.zabankhan.app.LocalAppContainer.current
    val words by remember(text.lang) { container.vocab.observeAll(text.lang) }.collectAsState(emptyList())
    val pack by produceState<com.ziaee.zabankhan.datapack.LanguageDataPack?>(null, text.lang) {
        value = com.ziaee.zabankhan.datapack.LanguageDataPacks.load(text.lang)
    }
    val comprehension by produceState<Int?>(null, text.body, words, pack) {
        val data = pack
        val spec = Languages.byCode(text.lang)
        value = if (data == null || spec == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            ComprehensionAnalyzer(spec, data.lemmas).analyze(text.body, words.filter { it.learned || it.leitnerBox >= 3 }.map { it.word }.toSet()).knownWordPercent.toInt()
        }
    }
    EditorialCard(onClick = open) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Icon(documentIcon(text), documentTypeLabel(text), Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text.title, Modifier.fillMaxWidth(), style = editorialStyle(text.title), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    text.sourceName?.let { Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelMedium.merge(contentStyle(it)), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                trailing?.invoke()
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MetadataBadge(documentTypeLabel(text), containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                MetadataBadge(editorialDate(text.createdAtMs), containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                comprehension?.let { MetadataBadge(stringResource(Res.string.pack_comprehension, it)) }
            }
        }
    }
}

// Imported EPUBs are stored as extracted text. Use source metadata only when retained.
private fun documentIcon(text: TextDocument): ImageVector = when {
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
