package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val vm = viewModel(key = "review:${settings.learningLang}:${settings.dailyNewCards}") { ReviewViewModel(container, settings) }
    LaunchedEffect(vm) { if (vm.session?.cards?.isEmpty() == true) vm.load() }
    val session = vm.session
    Page {
        Heading(stringResource(Res.string.nav_review))
        if (vm.failed) {
            Text(stringResource(Res.string.error_save), color = MaterialTheme.colorScheme.error)
            if (session == null) TextButton(onClick = vm::load) { Text(stringResource(Res.string.retry)) }
        }
        if (session == null) { if (vm.busy) CircularProgressIndicator(); return@Page }
        val card = session.current
        if (card == null) {
            if (session.answers.isEmpty()) Text(stringResource(Res.string.nothing_due))
            else {
                Heading(stringResource(Res.string.review_summary))
                Text(stringResource(Res.string.review_counts, session.completedCount, session.accuracy))
                listOf(VocabAnswer.FORGOT to Res.string.forgot, VocabAnswer.HARD to Res.string.hard, VocabAnswer.KNEW to Res.string.knew).forEach { (answer, label) ->
                    Text("${stringResource(label)}: ${session.answers.count { it.answer == answer }}")
                }
                Button(enabled = !vm.busy, onClick = vm::load) { Text(stringResource(Res.string.start_review)) }
            }
        } else {
            Text("${session.answers.size + 1} / ${session.cards.size}")
            ElevatedCard(onClick = { vm.flipped = !vm.flipped }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp).heightIn(min = 220.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(card.word, style = MaterialTheme.typography.headlineLarge.merge(contentStyle(card.word)))
                    Text(stringResource(Res.string.box_label, card.leitnerBox))
                    val spec = Languages.byCode(card.lang)!!
                    val matches = VocabHighlighter(listOf(SavedVocab(card.word, card.srsState().status())), spec).findMatches(card.sentence)
                    Text(buildAnnotatedString { append(card.sentence); matches.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1) } }, style = contentStyle(card.sentence))
                    if (vm.flipped) Text(card.meaning.orEmpty(), style = MaterialTheme.typography.titleLarge.merge(contentStyle(card.meaning.orEmpty())))
                    else Text(stringResource(Res.string.flip_hint), style = MaterialTheme.typography.labelLarge)
                }
            }
            if (vm.flipped) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(VocabAnswer.FORGOT to Res.string.forgot, VocabAnswer.HARD to Res.string.hard, VocabAnswer.KNEW to Res.string.knew).forEach { (answer, label) ->
                    Button(enabled = !vm.busy, onClick = { vm.answer(answer) }) { Text(stringResource(label)) }
                }
            }
        }
        if (session.answers.isNotEmpty()) OutlinedButton(enabled = !vm.busy, onClick = vm::undo) { Text(stringResource(Res.string.undo)) }
    }
}
