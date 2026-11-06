package com.ziaee.zabankhan.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
        EditorialTopAppBar(stringResource(Res.string.h2_progress), back)
        EditorialCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EditorialSectionHeader(stringResource(Res.string.h2_streak, activityStreak(rows, today, reviews, attempts)))
                DailyGoalCard(vm, settings)
            }
        }
        listOf(1 to Res.string.h2_today, 7 to Res.string.h2_seven_days, 30 to Res.string.h2_thirty_days).forEach { (days, label) ->
            val minutes = activityMinutes(rows, today, days)
            EditorialCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorialSectionHeader(stringResource(label))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PeriodMetric(minutes.reading.roundToInt(), stringResource(Res.string.ed_reading_minutes), Icons.Default.MenuBook, Modifier.weight(1f))
                        PeriodMetric(minutes.listening.roundToInt(), stringResource(Res.string.ed_listening_minutes), Icons.Default.Headphones, Modifier.weight(1f))
                        PeriodMetric(minutes.watching.roundToInt(), stringResource(Res.string.ed_watching_minutes), Icons.Default.PlayCircleOutline, Modifier.weight(1f))
                    }
                }
            }
        }
        EditorialSectionHeader(stringResource(Res.string.h2_activity_chart))
        val dates = lastDays(today, 7)
        val reading = stringResource(Res.string.h2_reading)
        val listening = stringResource(Res.string.h2_listening)
        val watching = stringResource(Res.string.h2_watching)
        val dayNames = listOf(Res.string.ed_mon, Res.string.ed_tue, Res.string.ed_wed, Res.string.ed_thu, Res.string.ed_fri, Res.string.ed_sat, Res.string.ed_sun).map { stringResource(it) }
        val labels = dates.map { dayNames[it.dayOfWeek.ordinal] }
        listOf(reading to { r: ActivityLogEntry -> r.readingMs }, listening to { r: ActivityLogEntry -> r.listeningMs }, watching to { r: ActivityLogEntry -> r.watchingMs }).forEach { (label, value) ->
            val values = dates.map { day -> rows.filter { it.date == day.toString() }.sumOf(value) / 60_000.0 }
            EditorialCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    BarChart(values, labels, label)
                }
            }
        }
        EditorialSectionHeader(stringResource(Res.string.h2_reviews))
        val daily = dailyReviews(reviews, today, 7)
        EditorialCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BarChart(daily.map { it.count.toDouble() }, labels, stringResource(Res.string.h2_reviews))
                daily.forEach { Text(stringResource(Res.string.h2_review_day, it.date.toString(), it.count, it.accuracy), style = MaterialTheme.typography.bodySmall) }
            }
        }
        EditorialCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.h2_shadow_score, shadowAverage(attempts), attempts.size))
                Text(stringResource(Res.string.h2_library_counts, texts.size, words.size))
                (1..5).forEach { box -> Text(stringResource(Res.string.h2_box_count, box, words.count { it.leitnerBox == box })) }
            }
        }
    }
}

@Composable fun DailyGoalCard(vm: AppViewModel, settings: AppSettings) {
    val lang = requireNotNull(settings.learningLang)
    val rows by remember(lang) { vm.container.database.activity().observe(lang) }.collectAsState(emptyList())
    var today by remember { mutableStateOf(localToday()) }
    LaunchedEffect(lang) { while (true) { today = localToday(); delay(30_000) } }
    val minutes = activityMinutes(rows, today, 1).total
    val label = stringResource(Res.string.h2_goal_progress, minutes.roundToInt(), settings.dailyGoalMinutes)
    EditorialProgressIndicator((minutes / settings.dailyGoalMinutes.coerceAtLeast(1)).toFloat(), label, Modifier.fillMaxWidth())
}

@Composable private fun BarChart(values: List<Double>, labels: List<String>, title: String) {
    val color = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val description = stringResource(Res.string.ed_chart_description, title,
        labels.zip(values).joinToString { (label, value) -> "$label: ${value.roundToInt()}" })
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val max = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            val cell = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { i, value ->
                val height = (size.height * value / max).toFloat()
                val column = if (rtl) values.lastIndex - i else i
                val left = column * cell + cell * .23f
                val width = cell * .54f
                drawRoundRect(track, Offset(left, 0f), Size(width, size.height), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                if (height > 0f) drawRoundRect(color, Offset(left, size.height - height), Size(width, height), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            labels.forEach { label -> Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable private fun PeriodMetric(minutes: Int, label: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, label, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.secondary)
        Text(minutes.toString(), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
