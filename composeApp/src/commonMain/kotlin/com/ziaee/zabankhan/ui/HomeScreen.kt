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

@Composable fun HomeScreen(vm: AppViewModel, settings: AppSettings, open: (Long) -> Unit, add: () -> Unit, review: () -> Unit) {
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
        Text(stringResource(Res.string.recent_texts), style = MaterialTheme.typography.titleMedium)
        if (texts.isEmpty()) Text(stringResource(Res.string.empty_library))
        (recent.ifEmpty { texts }.take(5)).forEach { text -> TextCard(text, { open(text.id) }) }
    }
}
@Composable fun TextCard(text: TextDocument, open: () -> Unit, actions: @Composable ColumnScope.() -> Unit = {}) {
    ElevatedCard(onClick = open, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (text.pdfPath != null) Icon(Icons.Default.PictureAsPdf, "PDF")
            Text(text.title, style = MaterialTheme.typography.titleMedium.merge(contentStyle(text.title)))
            Text(text.body.take(180), maxLines = 3, style = contentStyle(text.body))
            Text(kotlin.time.Instant.fromEpochMilliseconds(text.createdAtMs).toString().take(10), style = MaterialTheme.typography.labelSmall)
            actions()
        }
    }
}
