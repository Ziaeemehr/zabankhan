package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.srs.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable fun HomeScreen(vm: AppViewModel, settings: AppSettings, open: (Long) -> Unit, add: () -> Unit, review: () -> Unit, watch: () -> Unit, progress: () -> Unit = {}, discover: () -> Unit = {}, grammar: () -> Unit = {}, exams: () -> Unit = {}) {
    val lang = requireNotNull(settings.learningLang)
    val recent by remember(lang) { vm.container.texts.observeRecent(lang) }.collectAsState(emptyList())
    val texts by remember(lang) { vm.container.texts.observeAll(lang) }.collectAsState(emptyList())
    val words by remember(lang) { vm.container.vocab.observeAll(lang) }.collectAsState(emptyList())
    var now by remember { mutableStateOf(nowMs()) }
    LaunchedEffect(lang) { while (true) { now = nowMs(); delay(30_000) } }
    var remaining by remember(lang, settings.dailyNewCards) { mutableStateOf(settings.dailyNewCards) }
    LaunchedEffect(words, now, settings.dailyNewCards) {
        remaining = remainingNewCards(vm.container.vocab.reviewLogSince(lang, 0), settings.dailyNewCards)
    }
    val due = reviewableCount(words.map { it.reviewCard() }, now, remaining)
    Page {
        Heading(stringResource(Res.string.app_name))
        Choice(stringResource(Res.string.learning_languages), lang, settings.learningLangs.mapNotNull { Languages.byCode(it) }.map { it.code to languageName(it) }) { code ->
            vm.action {
                vm.container.settings.setLearningLang(code)
                if (settings.meaningLang == code) vm.container.settings.setMeaningLang(if (code == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code)
            }
        }
        Text(stringResource(Res.string.greeting), style = MaterialTheme.typography.headlineSmall)
        ElevatedCard(onClick = progress, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.h2_progress), style = MaterialTheme.typography.titleMedium)
                com.ziaee.zabankhan.statistics.DailyGoalCard(vm, settings)
            }
        }
        ElevatedCard(onClick = discover, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.h2_discover), Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
        }
        if (com.ziaee.zabankhan.datapack.LanguageDataPacks.available(lang)) {
            TextButton(onClick = grammar) { Text(stringResource(Res.string.pack_grammar)) }
            TextButton(onClick = exams) { Text(stringResource(Res.string.pack_tcf)) }
        }
        recent.firstOrNull()?.let { text ->
            Text(stringResource(Res.string.continue_reading), style = MaterialTheme.typography.titleMedium)
            TextCard(text, { open(text.id) })
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.due_today, due), style = MaterialTheme.typography.titleMedium)
                Button(onClick = review) { Text(stringResource(Res.string.start_review)) }
            }
        }
        com.ziaee.zabankhan.pdf.PdfAddMenu(vm, settings, add, open)
        ElevatedCard(onClick = watch, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(Res.string.watch_video), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.watch_home_hint))
            }
        }
        Text(stringResource(Res.string.recent_texts), style = MaterialTheme.typography.titleMedium)
        if (texts.isEmpty()) Text(stringResource(Res.string.empty_library))
        (recent.ifEmpty { texts }.take(5)).forEach { text -> TextCard(text, { open(text.id) }) }
    }
}
@Composable fun TextCard(text: TextDocument, open: () -> Unit, actions: @Composable ColumnScope.() -> Unit = {}) {
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
    ElevatedCard(onClick = open, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (text.pdfPath != null) Icon(Icons.Default.PictureAsPdf, "PDF")
            Text(text.title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium.merge(contentStyle(text.title)))
            Text(text.body.take(180), modifier = Modifier.fillMaxWidth(), maxLines = 3, style = contentStyle(text.body))
            comprehension?.let { Text(stringResource(Res.string.pack_comprehension, it), style = MaterialTheme.typography.labelMedium) }
            Text(kotlin.time.Instant.fromEpochMilliseconds(text.createdAtMs).toString().take(10), style = MaterialTheme.typography.labelSmall)
            actions()
        }
    }
}
