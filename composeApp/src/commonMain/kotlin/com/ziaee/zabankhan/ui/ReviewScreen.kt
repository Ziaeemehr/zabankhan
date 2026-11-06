package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import com.ziaee.zabankhan.ui.theme.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.srs.*
import com.ziaee.zabankhan.speech.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

class ReviewViewModel(private val container: AppContainer, private val settings: AppSettings) : ViewModel() {
    var session by mutableStateOf<ReviewSession?>(null); private set
    var busy by mutableStateOf(false); private set
    var failed by mutableStateOf(false); private set
    var flipped by mutableStateOf(false)
    init { load() }
    fun load() = run {
        val lang = requireNotNull(settings.learningLang)
        val now = nowMs()
        val entries = container.vocab.getAll(lang)
        val remaining = remainingNewCards(container.vocab.reviewLogSince(lang, 0), settings.dailyNewCards, VocabSrs.startOfDayMs(now))
        val ids = buildReviewQueue(entries.map { it.reviewCard() }, now, VocabSrs.startOfDayMs(now), remaining).map { it.id }
        session = ReviewSession(ids.mapNotNull { id -> entries.firstOrNull { it.id == id } })
    }
    private fun run(block: suspend () -> Unit) { if (busy) return; busy = true; failed = false
        viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true } finally { busy = false } }
    }
    fun answer(answer: VocabAnswer) = run {
        val state = session ?: return@run
        val before = state.current ?: return@run
        val now = nowMs()
        // FrenchReader repeats forgotten cards within the session and keeps them due on exit.
        val schedule = VocabSrs.apply(before.srsState(), answer, now)
        val after = before.withState(if (answer == VocabAnswer.FORGOT) schedule.copy(nextReviewAtMs = now) else schedule)
        val log = container.vocab.recordReview(after, ReviewLogEntry(entryId = before.id, lang = before.lang, timestampMs = now, knew = answer != VocabAnswer.FORGOT, boxBefore = before.leitnerBox, boxAfter = after.leitnerBox))
        session = state.answered(SessionAnswer(before, answer, after, log)); flipped = false
    }
    fun undo() = run {
        val state = session ?: return@run
        val last = state.answers.lastOrNull() ?: return@run
        container.vocab.undoReview(last.before, last.logId)
        session = state.undo(); flipped = false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReviewScreen(container: AppContainer, settings: AppSettings) {
    val vm = viewModel(key = "review:${container.hashCode()}:${settings.learningLang}:${settings.dailyNewCards}") { ReviewViewModel(container, settings) }
    LaunchedEffect(vm) { if (vm.session?.cards?.isEmpty() == true) vm.load() }
    val tts = remember { createTts() }
    var shadow by remember { mutableStateOf<VocabEntry?>(null) }
    DisposableEffect(Unit) { onDispose { tts.stop() } }
    LaunchedEffect(vm.session?.current?.id, settings.autoPronounceCards) {
        tts.stop()
        val card = vm.session?.current
        if (settings.autoPronounceCards && card != null) tts.pronounce(card.word, card.lang, settings)
    }
    shadow?.let { card -> ShadowingPanel(container, settings, learningSpeechLanguage(card.lang), card.textId,
        listOf(card.sentence.ifBlank { card.word }), dismiss = { shadow = null }) }
    val session = vm.session
    Page {
        val title = stringResource(Res.string.nav_review)
        Text(title, style = editorialStyle(title, EditorialRole.ScreenTitle))
        if (vm.failed) {
            Text(stringResource(Res.string.error_save), color = MaterialTheme.colorScheme.error)
            if (session == null) TextButton(onClick = vm::load) { Text(stringResource(Res.string.retry)) }
        }
        if (session == null) { if (vm.busy) CircularProgressIndicator(); return@Page }
        val card = session.current
        if (card == null) {
            if (session.answers.isEmpty()) EditorialEmptyState(Icons.Default.CheckCircleOutline,
                stringResource(Res.string.nothing_due), stringResource(Res.string.ed_review_done_hint), Modifier.heightIn(min = 300.dp))
            else {
                EditorialEmptyState(Icons.Default.CheckCircleOutline, stringResource(Res.string.review_summary),
                    stringResource(Res.string.review_counts, session.completedCount, session.accuracy))
                EditorialCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(VocabAnswer.FORGOT to Res.string.forgot, VocabAnswer.HARD to Res.string.hard, VocabAnswer.KNEW to Res.string.knew).forEach { (answer, label) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(stringResource(label), Modifier.weight(1f))
                                Text(session.answers.count { it.answer == answer }.toString())
                            }
                        }
                        EditorialPrimaryButton(stringResource(Res.string.start_review), vm::load, enabled = !vm.busy)
                    }
                }
            }
        } else {
            EditorialProgressIndicator(session.answers.size.toFloat() / session.cards.size.coerceAtLeast(1),
                stringResource(Res.string.ed_review_progress, session.answers.size + 1, session.cards.size))
            EditorialCard(onClick = { vm.flipped = !vm.flipped }, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(24.dp).heightIn(min = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    MetadataBadge(stringResource(Res.string.box_label, card.leitnerBox), containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
                    Text(card.word, Modifier.fillMaxWidth(), style = editorialStyle(card.word, EditorialRole.ReviewWord).copy(textAlign = TextAlign.Center))
                    val spec = Languages.byCode(card.lang)!!
                    val matches = VocabHighlighter(listOf(SavedVocab(card.word, card.srsState().status())), spec).findMatches(card.sentence)
                    Text(buildAnnotatedString { append(card.sentence); matches.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1) } }, Modifier.fillMaxWidth(), style = contentStyle(card.sentence))
                    if (vm.flipped) Text(card.meaning.orEmpty(), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge.merge(contentStyle(card.meaning.orEmpty())))
                    else Text(stringResource(Res.string.flip_hint), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeakerButton(card.word, card.lang, settings)
                TextButton(onClick = { tts.stop(); shadow = card }) { Text(stringResource(Res.string.speech_shadow)) }
            }
            if (!vm.flipped) EditorialPrimaryButton(stringResource(Res.string.ed_reveal_meaning), { vm.flipped = true }, Modifier.fillMaxWidth())
            if (vm.flipped) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(VocabAnswer.FORGOT to Res.string.forgot, VocabAnswer.HARD to Res.string.hard, VocabAnswer.KNEW to Res.string.knew).forEach { (answer, label) ->
                    Column(Modifier.widthIn(min = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedButton(enabled = !vm.busy, onClick = { vm.answer(answer) }) { Text(stringResource(label)) }
                        val days = VocabSrs.previewIntervalDays(card.srsState(), answer).toInt()
                        val interval = if (answer == VocabAnswer.FORGOT) stringResource(Res.string.ed_interval_session)
                            else stringResource(if (days == 1) Res.string.ed_interval_day else Res.string.ed_interval_days, days)
                        Text(interval, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (session.answers.isNotEmpty()) OutlinedButton(enabled = !vm.busy, onClick = vm::undo) { Text(stringResource(Res.string.undo)) }
    }
}
