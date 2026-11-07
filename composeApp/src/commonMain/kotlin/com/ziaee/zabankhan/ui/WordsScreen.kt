package com.ziaee.zabankhan.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.srs.*
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.ui.theme.*
import com.ziaee.zabankhan.ui.reader.color
import com.ziaee.zabankhan.ui.reader.pieColor
import com.ziaee.zabankhan.ui.reader.containerColor
import com.ziaee.zabankhan.ui.reader.leitnerBoxColor
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable fun WordsScreen(vm: AppViewModel, settings: AppSettings, review: () -> Unit = {}) {
    val lang = settings.learningLang!!
    val spec = Languages.byCode(lang)!!
    val words by remember(lang) { vm.container.vocab.observeAll(lang) }.collectAsState(emptyList())
    val decks by remember(lang) { vm.container.vocab.observeLists(lang) }.collectAsState(emptyList())
    var search by rememberSaveable(lang) { mutableStateOf("") }
    var deck by rememberSaveable(lang) { mutableStateOf(-1L) }
    var selected by remember(lang) { mutableStateOf(emptySet<Long>()) }
    var edit by remember { mutableStateOf<VocabEntry?>(null) }
    var createDeck by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<VocabEntry?>(null) }
    var now by remember { mutableStateOf(nowMs()) }
    var logs by remember(lang) { mutableStateOf(emptyList<ReviewLogEntry>()) }
    LaunchedEffect(lang) { while (true) {
        now = nowMs(); logs = vm.container.vocab.reviewLogSince(lang, 0); delay(30_000)
    } }
    val scoped = words.filter { deck == -1L || (it.listId ?: 0L) == deck }
    val remaining = remainingNewCards(logs, settings.dailyNewCards, VocabSrs.startOfDayMs(now))
    // The host review callback starts the active language's queue, across all decks.
    val due = reviewableCount(words.map { it.reviewCard() }, now, remaining)
    val options = listOf(-1L to stringResource(Res.string.all), 0L to stringResource(Res.string.unfiled)) + decks.map { it.id to it.name }

    var previewEntryId by rememberSaveable(lang) { mutableStateOf<Long?>(null) }
    var meaningsVisible by rememberSaveable(lang) { mutableStateOf(true) }
    var selectedStatus by rememberSaveable(lang) { mutableStateOf<VocabStatus?>(null) }
    var showMove by remember { mutableStateOf(false) }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }
    val filtered = filterVocabulary(words, deck, search, selectedStatus, spec)
    LaunchedEffect(filtered) { selected = selected.intersect(filtered.map { it.id }.toSet()) }
    Scaffold(topBar = {
        if (selected.isNotEmpty()) {
            TopAppBar(title = { Text(stringResource(Res.string.selected_count, selected.size)) },
                navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, stringResource(Res.string.vocab_selection_close)) } },
                actions = {
                    IconButton(onClick = { selected = filtered.map { it.id }.toSet() }) { Icon(Icons.Default.SelectAll, stringResource(Res.string.vocab_select_all)) }
                    IconButton(onClick = { showMove = true }) { Icon(Icons.Default.Folder, stringResource(Res.string.vocab_bulk_move)) }
                    IconButton(onClick = { delete = true }) { Icon(Icons.Default.Delete, stringResource(Res.string.vocab_bulk_delete)) }
                })
        } else {
            TopAppBar(title = { Text(stringResource(Res.string.vocab_screen_title)) }, actions = {
                IconButton(onClick = { meaningsVisible = !meaningsVisible }) {
                    Icon(if (meaningsVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, stringResource(Res.string.accessibility_hide_meaning))
                }
            })
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                var scopeMenuExpanded by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { scopeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(options.firstOrNull { it.first == deck }?.second.orEmpty(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(scopeMenuExpanded, { scopeMenuExpanded = false }) {
                    options.forEach { (id, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { deck = id; scopeMenuExpanded = false }) }
                    DropdownMenuItem(text = { Text(stringResource(Res.string.vocab_list_new)) }, onClick = { createDeck = true; scopeMenuExpanded = false }, leadingIcon = { Icon(Icons.Default.Add, null) })
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (due > 0) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Style, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(Res.string.vocab_due_now, due), style = MaterialTheme.typography.titleSmall)
                                if (due == 0) {
                                    Text(stringResource(Res.string.vocab_nothing_due), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Button(
                                onClick = { review() },
                                enabled = due > 0
                            ) {
                                Text(stringResource(Res.string.vocab_review_start))
                            }
                        }
                    }
                }


                item {
                    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val counts = VocabStatus.entries.associateWith { status -> scoped.count { it.srsState().status() == status } }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        if (scoped.isNotEmpty()) {
                            Canvas(Modifier.size(96.dp)) {
                                var startAngle = -90f
                                VocabStatus.entries.forEach { status ->
                                    val sweepAngle = 360f * counts.getValue(status) / scoped.size
                                    if (sweepAngle > 0f) drawArc(status.pieColor(dark), startAngle, sweepAngle, useCenter = true)
                                    startAngle += sweepAngle
                                }
                            }
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VocabStatus.entries.forEach { status ->
                            StatusChip(status, scoped.count { it.srsState().status() == status }, selectedStatus) { selectedStatus = if (selectedStatus == status) null else status }
                        }
                    }
                }
                item { OutlinedTextField(search, { search = it }, label = { Text(stringResource(Res.string.vocab_search_hint)) }, modifier = Modifier.fillMaxWidth().padding(12.dp), singleLine = true) }
                if (filtered.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (words.isEmpty()) Res.string.vocab_empty_hint else Res.string.vocab_search_no_results))
                    }
                }
                items(filtered, key = { it.id }) { entry ->
                    VocabRow(entry, meaningsVisible, entry.id in selected, selected.isNotEmpty(),
                        onClick = { if (selected.isEmpty()) previewEntryId = entry.id else selected = if (entry.id in selected) selected - entry.id else selected + entry.id },
                        onLongClick = { selected = selected + entry.id }, onEdit = { edit = entry },
                        onToggleLearned = { vm.action {
                            vm.container.vocab.update(if (entry.srsState().status() == VocabStatus.LEARNED) entry.copy(learned = false, leitnerBox = 4, nextReviewAtMs = nowMs()) else entry.copy(learned = true))
                        } }, onDelete = { deleteEntry = entry })
                }
            }
        }
    }
    if (showMove) AlertDialog(onDismissRequest = { showMove = false }, title = { Text(stringResource(Res.string.vocab_move_to_list_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) { options.filter { it.first != -1L }.forEach { (id, label) ->
            TextButton(onClick = { val ids = selected.toList(); vm.action { vm.container.vocab.moveToList(ids, id.takeIf { it != 0L }); selected = emptySet() }; showMove = false }) { Text(label) }
        } }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = { showMove = false }) { Text(stringResource(Res.string.cancel)) } })
    val previewEntry = words.firstOrNull { it.id == previewEntryId }
    previewEntry?.let { entry ->
        ModalBottomSheet(onDismissRequest = { previewEntryId = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                VocabPreviewFlashcard(entry, vm, settings)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { edit = entry; previewEntryId = null }) { Text(stringResource(Res.string.edit)) }
                    TextButton(onClick = { previewEntryId = null }) { Text(stringResource(Res.string.close)) }
                }
            }
        }
    }
    deleteEntry?.let { entry -> ConfirmDelete(onDismiss = { deleteEntry = null }) { vm.action { vm.container.vocab.deleteIds(listOf(entry.id)) } } }
    if (createDeck) NameDialog(stringResource(Res.string.new_deck), onDismiss = { createDeck = false }) { name -> vm.action { vm.container.vocab.insertList(VocabList(lang = lang, name = name)) } }
    if (delete) ConfirmDelete(onDismiss = { delete = false }) { vm.action { vm.container.vocab.deleteIds(selected.toList()); selected = emptySet() } }
    edit?.let { entry -> EditWordDialog(entry, options.filter { it.first != -1L }, { edit = null }) { updated -> vm.action { vm.container.vocab.update(updated) } } }
}
@Composable internal fun EditWordDialog(entry: VocabEntry, decks: List<Pair<Long, String>>, dismiss: () -> Unit, save: (VocabEntry) -> Unit) {
    var word by remember { mutableStateOf(entry.word) }
    var meaning by remember { mutableStateOf(entry.meaning.orEmpty()) }
    var sentence by remember { mutableStateOf(entry.sentence) }
    var deck by remember { mutableStateOf(entry.listId ?: 0L) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(Res.string.edit)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(word, { word = it }, label = { Text(stringResource(Res.string.word)) }, textStyle = contentStyle(word))
            OutlinedTextField(meaning, { meaning = it }, label = { Text(stringResource(Res.string.meaning)) }, textStyle = contentStyle(meaning))
            OutlinedTextField(sentence, { sentence = it }, label = { Text(stringResource(Res.string.sentence)) }, textStyle = contentStyle(sentence))
            Choice(stringResource(Res.string.deck), deck, decks) { deck = it }
        }
    }, confirmButton = { TextButton(enabled = word.isNotBlank(), onClick = { save(entry.copy(word = word.trim(), meaning = meaning.trim(), sentence = sentence, listId = deck.takeIf { it != 0L })); dismiss() }) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.cancel)) } })
}
@Composable
private fun StatusChip(status: VocabStatus, count: Int, selectedStatus: VocabStatus?, onClick: () -> Unit) {
    val statusColor = status.color()
    val containerColor = status.containerColor()
    FilterChip(
        selected = selectedStatus == status,
        onClick = onClick,
        leadingIcon = { Box(Modifier.size(8.dp).background(statusColor, RoundedCornerShape(50))) },
        label = { Text("${stringResource(status.labelRes())} $count", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = statusColor,
            selectedContainerColor = containerColor,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = statusColor
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VocabRow(
    entry: VocabEntry,
    showMeaning: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleLearned: () -> Unit,
    onDelete: () -> Unit
) {
    val status = entry.srsState().status()
    val statusColor = status.color()
    val surface = MaterialTheme.colorScheme.surface
    // Very light status tint over the theme surface, so text always keeps the
    // theme's own on-surface contrast (whatever the app/system theme is).
    val cardColor = if (selected) MaterialTheme.colorScheme.primaryContainer
    else statusColor.copy(alpha = 0.08f).compositeOver(surface)
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Box(
                Modifier.padding(vertical = 12.dp, horizontal = 10.dp).width(4.dp).fillMaxHeight()
                    .background(statusColor, RoundedCornerShape(2.dp))
            )
            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.word,
                        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(6.dp).background(statusColor, RoundedCornerShape(50)))
                        Text(
                            stringResource(status.labelRes()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.sentence,
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val displayMeaning = entry.meaning?.takeIf { it.isNotBlank() }
                if (showMeaning && displayMeaning != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(displayMeaning, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(displayMeaning)))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.width(92.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(5) { index ->
                            Surface(
                                modifier = Modifier.weight(1f).height(5.dp),
                                shape = RoundedCornerShape(50),
                                color = if (index < entry.leitnerBox) leitnerBoxColor(index + 1)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ) {}
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        nextReviewText(entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!selectionMode) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onToggleLearned) {
                    Icon(
                        if (entry.learned) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = stringResource(
                            if (entry.learned) Res.string.vocab_mark_learning else Res.string.vocab_mark_known
                        ),
                        tint = if (entry.learned) statusColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.action_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.accessibility_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun nextReviewText(entry: VocabEntry): String = when (
    val bucket = wordsDueBucket(entry.nextReviewAtMs, nowMs(), entry.srsState().status() == VocabStatus.LEARNED)
) {
    WordsDueBucket.Learned -> stringResource(Res.string.vocab_next_review_learned)
    WordsDueBucket.Today -> stringResource(Res.string.vocab_next_review_today)
    is WordsDueBucket.InDays -> if (bucket.days == 1L) {
        stringResource(Res.string.vocab_next_review_tomorrow)
    } else {
        stringResource(Res.string.vocab_next_review_in_days, bucket.days)
    }
}


sealed interface WordsDueBucket {
    data object Learned : WordsDueBucket
    data object Today : WordsDueBucket
    data class InDays(val days: Long) : WordsDueBucket
}

fun wordsDueBucket(nextReviewAtMs: Long, nowMs: Long, learned: Boolean = false): WordsDueBucket {
    if (learned) return WordsDueBucket.Learned
    if (nextReviewAtMs <= nowMs) return WordsDueBucket.Today
    val days = kotlin.math.ceil((nextReviewAtMs - nowMs).toDouble() / 86_400_000.0).toLong()
    return WordsDueBucket.InDays(days.coerceAtLeast(1))
}

private fun VocabStatus.labelRes() = when (this) {
    VocabStatus.NEW -> Res.string.ed_status_new
    VocabStatus.LEARNING -> Res.string.ed_status_learning
    VocabStatus.REVIEWING -> Res.string.ed_status_reviewing
    VocabStatus.LEARNED -> Res.string.ed_status_learned
}
