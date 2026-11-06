package com.ziaee.zabankhan.datapack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.grammar.*
import com.ziaee.zabankhan.exams.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.*
import com.ziaee.zabankhan.ui.theme.*
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

/** The notes' inline Markdown supports emphasis and code, without displaying delimiters. */
@Composable private fun Markdown(text: String) {
    val annotated = remember(text) { buildAnnotatedString {
        val pattern = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
        var cursor = 0
        for (match in pattern.findAll(text)) {
            append(text.substring(cursor, match.range.first))
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(match.groupValues[1].ifEmpty { match.groupValues[2] }); pop()
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    } }
    Text(annotated, modifier = Modifier.fillMaxWidth(), style = contentStyle(text))
}
/** Self-rating is local to this practice session; no AI evaluation. */
@Composable fun TcfPracticeScreen(lang: String, back: () -> Unit) {
    var pack by remember(lang) { mutableStateOf<LanguageDataPack?>(null) }
    var failed by remember(lang) { mutableStateOf(false) }
    LaunchedEffect(lang) { try { pack = LanguageDataPacks.load(lang) } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true } }
    var taskId by remember { mutableStateOf("") }
    var index by remember(taskId) { mutableStateOf(0) }
    val answers = remember { mutableStateMapOf<String, String>() }
    val scores = remember { mutableStateMapOf<String, Int>() }
    val tasks = pack?.questions?.tasks.orEmpty()
    val task = tasks.firstOrNull { it.id == taskId } ?: tasks.firstOrNull()
    val question = task?.items?.getOrNull(index)
    Page {
        EditorialTopAppBar(stringResource(Res.string.pack_tcf), back)
        if (failed) Text(stringResource(Res.string.error_save))
        else if (pack == null && LanguageDataPacks.available(lang)) CircularProgressIndicator()
        Text(stringResource(Res.string.pack_self_score_hint))
        if (task != null) Choice(stringResource(Res.string.pack_task), task.id, tasks.map { it.id to "${it.skill} ${it.task} — ${it.titleFr}" }) { taskId = it }
        if (question != null) {
            EditorialProgressIndicator(index.toFloat() / task!!.items.size.coerceAtLeast(1), stringResource(Res.string.ed_review_progress, index + 1, task.items.size))
            EditorialSectionHeader(task.titleFr)
            EditorialCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    question.texts.forEach { Markdown(it.fr); if (it.fa.isNotBlank()) Markdown(it.fa) }
                }
            }
            OutlinedTextField(answers[question.id].orEmpty(), { answers[question.id] = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp), label = { Text(stringResource(Res.string.pack_response)) })
            Text(stringResource(Res.string.pack_self_score, scores[question.id] ?: 0))
            Slider((scores[question.id] ?: 0).toFloat(), { scores[question.id] = it.toInt() }, valueRange = 0f..5f, steps = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled = index > 0, onClick = { index-- }) { Text(stringResource(Res.string.pack_previous)) }
                Button(enabled = index < task.items.lastIndex, onClick = { index++ }) { Text(stringResource(Res.string.pack_next)) }
            }
            Text(stringResource(Res.string.pack_session_score, scores.values.sum(), scores.size * 5))
            Text(pack!!.questions.source.title + " — " + pack!!.questions.source.url, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable fun DataPackCredits() {
    Text(stringResource(Res.string.pack_credits), style = MaterialTheme.typography.bodySmall)
}
