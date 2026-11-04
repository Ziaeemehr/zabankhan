package com.ziaee.zabankhan.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable fun StatisticsScreen(vm: AppViewModel, settings: AppSettings, back: () -> Unit) {
    val lang = requireNotNull(settings.learningLang)
    val rows by remember(lang) { vm.container.database.activity().observe(lang) }.collectAsState(emptyList())
    val words by remember(lang) { vm.container.vocab.observeAll(lang) }.collectAsState(emptyList())
    val texts by remember(lang) { vm.container.texts.observeAll(lang) }.collectAsState(emptyList())
    val attempts by remember(lang) { vm.container.database.shadowAttempts().observe(lang) }.collectAsState(emptyList())
    var today by remember { mutableStateOf(localToday()) }
    var reviews by remember(lang) { mutableStateOf(emptyList<ReviewLogEntry>()) }
    LaunchedEffect(lang) { while (true) { today = localToday(); reviews = vm.container.vocab.reviewLogSince(lang, 0); delay(30_000) } }
    Page {
        TextButton(onClick = back) { Text(stringResource(Res.string.back)) }
        Heading(stringResource(Res.string.h2_progress))
        Text(stringResource(Res.string.h2_streak, activityStreak(rows, today, reviews, attempts)))
        DailyGoalCard(vm, settings)
        listOf(1 to Res.string.h2_today, 7 to Res.string.h2_seven_days, 30 to Res.string.h2_thirty_days).forEach { (days, label) ->
            val minutes = activityMinutes(rows, today, days)
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(Res.string.h2_activity_minutes, minutes.reading.roundToInt(), minutes.listening.roundToInt(), minutes.watching.roundToInt()))
        }
        Text(stringResource(Res.string.h2_activity_chart), style = MaterialTheme.typography.titleMedium)
        val dates = lastDays(today, 7)
        val reading = stringResource(Res.string.h2_reading)
        val listening = stringResource(Res.string.h2_listening)
        val watching = stringResource(Res.string.h2_watching)
        listOf(reading to { r: ActivityLogEntry -> r.readingMs }, listening to { r: ActivityLogEntry -> r.listeningMs }, watching to { r: ActivityLogEntry -> r.watchingMs }).forEach { (label, value) ->
            val values = dates.map { day -> rows.filter { it.date == day.toString() }.sumOf(value) / 60_000.0 }
            Text(label)
            BarChart(values, dates.map { it.toString() }, label)
        }
        Text(stringResource(Res.string.h2_reviews), style = MaterialTheme.typography.titleMedium)
        val daily = dailyReviews(reviews, today, 7)
        BarChart(daily.map { it.count.toDouble() }, daily.map { it.date.toString() }, stringResource(Res.string.h2_reviews))
        daily.forEach { Text(stringResource(Res.string.h2_review_day, it.date.toString(), it.count, it.accuracy)) }
        Text(stringResource(Res.string.h2_shadow_score, shadowAverage(attempts), attempts.size))
        Text(stringResource(Res.string.h2_library_counts, texts.size, words.size))
        (1..5).forEach { box -> Text(stringResource(Res.string.h2_box_count, box, words.count { it.leitnerBox == box })) }
    }
}

@Composable fun DailyGoalCard(vm: AppViewModel, settings: AppSettings) {
    val lang = requireNotNull(settings.learningLang)
    val rows by remember(lang) { vm.container.database.activity().observe(lang) }.collectAsState(emptyList())
    var today by remember { mutableStateOf(localToday()) }
    LaunchedEffect(lang) { while (true) { today = localToday(); delay(30_000) } }
    val minutes = activityMinutes(rows, today, 1).total
    val label = stringResource(Res.string.h2_goal_progress, minutes.roundToInt(), settings.dailyGoalMinutes)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(progress = { (minutes / settings.dailyGoalMinutes).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.size(48.dp).semantics { contentDescription = label })
        Text(label)
    }
}

@Composable private fun BarChart(values: List<Double>, labels: List<String>, title: String) {
    val color = MaterialTheme.colorScheme.primary
    val description = title + ": " + labels.zip(values).joinToString { (label, value) -> "$label: ${value.roundToInt()}" }
    Canvas(Modifier.fillMaxWidth().height(90.dp).semantics { contentDescription = description }) {
        val max = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val cell = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { i, value ->
            val height = (size.height * value / max).toFloat()
            drawRect(color, Offset(i * cell + cell * .15f, size.height - height), Size(cell * .7f, height))
        }
    }
    if (labels.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(labels.first(), style = MaterialTheme.typography.labelSmall)
        Text(labels.last(), style = MaterialTheme.typography.labelSmall)
    }
}
