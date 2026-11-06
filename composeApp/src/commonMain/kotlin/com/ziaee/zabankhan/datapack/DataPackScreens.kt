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
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

fun searchGrammarNotes(notes: List<GrammarNote>, query: String, level: String = "", group: String = ""): List<GrammarNote> {
    fun fold(text: String) = com.ziaee.zabankhan.lang.normalizeForMatch(text, com.ziaee.zabankhan.lang.Languages.FRENCH)
        .replace('ي', 'ی').replace('ك', 'ک').replace("\u200C", "")
    val terms = fold(query).split(Regex("\\s+")).filter(String::isNotBlank)
    return notes.filter { note ->
        (level.isEmpty() || note.level == level) && (group.isEmpty() || note.group == group) &&
            terms.all { it in fold(listOf(note.titleFr, note.titleFa, note.id).plus(note.aliasesFr).plus(note.aliasesFa).joinToString(" ")) }
    }
}

@Composable fun GrammarScreen(lang: String, back: () -> Unit) {
    var pack by remember(lang) { mutableStateOf<LanguageDataPack?>(null) }
    var failed by remember(lang) { mutableStateOf(false) }
    LaunchedEffect(lang) { try { pack = LanguageDataPacks.load(lang) } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true } }
    var selected by remember(lang) { mutableStateOf<GrammarNote?>(null) }
    var query by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    val notes = pack?.grammar.orEmpty()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { if (selected != null) selected = null else back() }) { Text(stringResource(Res.string.pack_back)) }
        Heading(stringResource(Res.string.pack_grammar))
        if (failed) Text(stringResource(Res.string.error_save))
        else if (pack == null && LanguageDataPacks.available(lang)) CircularProgressIndicator()
        val note = selected
        if (note == null) {
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(Res.string.pack_search)) }, modifier = Modifier.fillMaxWidth())
            val all = stringResource(Res.string.pack_all)
            Choice(stringResource(Res.string.pack_level), level, listOf("" to all) + notes.map { it.level }.distinct().sorted().map { it to it }) { level = it }
            Choice(stringResource(Res.string.pack_group), group, listOf("" to all) + notes.map { it.group }.distinct().map { it to grammarGroupLabel(it) }) { group = it }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(searchGrammarNotes(notes, query, level, group), key = { it.id }) { entry ->
                    ElevatedCard(onClick = { selected = entry }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(entry.titleFr, modifier = Modifier.fillMaxWidth(), style = contentStyle(entry.titleFr)); Text(entry.titleFa, modifier = Modifier.fillMaxWidth(), style = contentStyle(entry.titleFa))
                            Text(entry.level, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Heading(note.titleFr); Text(note.titleFa, modifier = Modifier.fillMaxWidth(), style = contentStyle(note.titleFa)); Text(note.level) }
            items(note.blocks) { block -> GrammarBlockView(block) }
        }
    }
}

@Composable private fun grammarGroupLabel(group: String): String = stringResource(when (group) {
    "basics" -> Res.string.pack_basics; "tenses" -> Res.string.pack_tenses
    "pronouns" -> Res.string.pack_pronouns; "structures" -> Res.string.pack_structures
    else -> Res.string.pack_comparisons
})

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
@Composable private fun Cell(cell: GrammarCell) {
    val value = remember(cell) { buildAnnotatedString {
        append(cell.text)
        cell.highlights.forEach { addStyle(SpanStyle(fontWeight = FontWeight.Bold), it.start, it.end) }
    } }
    Text(value, modifier = Modifier.fillMaxWidth(), style = contentStyle(cell.text))
}
@Composable private fun GrammarBlockView(block: GrammarBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (block) {
            is GrammarBlock.Prose -> block.markdown.forEach { Markdown((if (it.kind == "li") "• " else "") + it.text) }
            is GrammarBlock.Structure -> { block.patterns.forEach { Markdown("`$it`") }; block.text.forEach { Markdown(it.text) } }
            is GrammarBlock.Examples -> block.items.forEach { Cell(it.fr); Markdown(it.fa) }
            is GrammarBlock.Mistake -> { Markdown("✗ ${block.wrong}"); Markdown("✓ ${block.right}"); Markdown(block.why) }
            is GrammarBlock.Infographic -> {
                block.header.forEach { Cell(it) }
                block.rows.forEach { row -> HorizontalDivider(); row.forEach { Cell(it) } }
            }
        }
    }
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
        TextButton(onClick = back) { Text(stringResource(Res.string.pack_back)) }
        Heading(stringResource(Res.string.pack_tcf))
        if (failed) Text(stringResource(Res.string.error_save))
        else if (pack == null && LanguageDataPacks.available(lang)) CircularProgressIndicator()
        Text(stringResource(Res.string.pack_self_score_hint))
        if (task != null) Choice(stringResource(Res.string.pack_task), task.id, tasks.map { it.id to "${it.skill} ${it.task} — ${it.titleFr}" }) { taskId = it }
        if (question != null) {
            Text("${index + 1} / ${task!!.items.size}")
            question.texts.forEach { Markdown(it.fr); if (it.fa.isNotBlank()) Markdown(it.fa) }
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
