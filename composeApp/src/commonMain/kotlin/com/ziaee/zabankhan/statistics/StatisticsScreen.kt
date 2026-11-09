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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

@Composable fun StatisticsScreen(vm: AppViewModel, settings: AppSettings, back: () -> Unit, openStudyLog: () -> Unit = {}) {
    val lang = requireNotNull(settings.learningLang)
    val rows by remember(lang) { vm.container.database.activity().observe(lang) }.collectAsState(emptyList())
    val words by remember(lang) { vm.container.vocab.observeAll(lang) }.collectAsState(emptyList())
    val texts by remember(lang) { vm.container.texts.observeAll(lang) }.collectAsState(emptyList())
    val attempts by remember(lang) { vm.container.database.shadowAttempts().observe(lang) }.collectAsState(emptyList())
    val studyVm = rememberStudyLogViewModel(lang)
    val studyState by studyVm.uiState.collectAsState(initial = StudyLogUiState(lang))
    var today by remember { mutableStateOf(localToday()) }
    var reviews by remember(lang) { mutableStateOf(emptyList<ReviewLogEntry>()) }
    LaunchedEffect(lang) { while (true) { today = localToday(); reviews = vm.container.vocab.reviewLogSince(lang, 0); delay(30_000) } }
    Page {
        EditorialTopAppBar(stringResource(Res.string.h2_progress), back)
        TextButton(onClick = openStudyLog, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.study_log_title) + " · " + stringResource(Res.string.study_log_add_time)) }
        EditorialCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EditorialSectionHeader(stringResource(Res.string.h2_streak, activityStreak(rows, today, reviews, attempts, manualSessions = studyState.allSessions, lang = lang)))
                DailyGoalCard(vm, settings, studyState.allSessions)
            }
        }
        val firstDay = DayOfWeek.MONDAY
        var heatmapYear by remember { mutableIntStateOf(today.year) }
        val yearStart = LocalDate(heatmapYear, 1, 1)
        val yearEnd = if (heatmapYear == today.year) today else LocalDate(heatmapYear, 12, 31)
        val combined = remember(rows, studyState.allSessions, lang, yearStart, yearEnd) {
            combinedStudyDays(lang, rows, studyState.allSessions, yearStart, yearEnd)
        }
        EditorialCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    EditorialSectionHeader(stringResource(Res.string.study_log_activity))
                    Row {
                        TextButton(onClick = { if (heatmapYear > 2000) heatmapYear-- }) { Text("‹") }
                        Text(heatmapYear.toString(), Modifier.align(Alignment.CenterVertically))
                        TextButton(enabled = heatmapYear < today.year, onClick = { heatmapYear++ }) { Text("›") }
                    }
                }
                StudyYearHeatmap(combined, heatmapYear, today, firstDay, studyState.allSessions, studyState.sources)
                Text(stringResource(Res.string.study_log_combined_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        listOf(1 to Res.string.h2_today, 7 to Res.string.h2_seven_days, 30 to Res.string.h2_thirty_days).forEach { (days, label) ->
            val minutes = activityMinutes(rows, today, days)
            val manual = studyState.allSessions.filter { it.date in today.minus(days - 1, DateTimeUnit.DAY).toEpochDays().toLong()..today.toEpochDays().toLong() }.sumOf { it.durationMin }
            EditorialCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorialSectionHeader(stringResource(label))
                    Text(stringResource(Res.string.study_log_combined_total, (minutes.total + manual).roundToInt()), style = MaterialTheme.typography.titleSmall)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyYearHeatmap(days: List<StudyDayActivity>, year: Int, today: LocalDate, firstDay: DayOfWeek, sessions: List<StudySession>, sources: List<StudySource>) {
    var monthView by remember(year) { mutableStateOf(false) }
    val lastAvailableMonth = if (year < today.year) 12 else today.monthNumber
    var selectedMonth by remember(year) { mutableIntStateOf(if (year == today.year) today.monthNumber else 12) }
    val grid = if (monthView) studyMonthGrid(days, LocalDate(year, selectedMonth, 1), firstDay, today)
    else studyYearGrid(days, year, firstDay, today)
    var selected by remember(year, selectedMonth, monthView) { mutableStateOf<StudyDayActivity?>(null) }
    val active = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.surface
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { monthView = false }) { Text(stringResource(Res.string.study_log_year_view)) }
            TextButton(onClick = { monthView = true }) { Text(stringResource(Res.string.study_log_month_view)) }
            if (monthView) {
                TextButton(enabled = selectedMonth > 1, onClick = { if (selectedMonth > 1) selectedMonth-- }) { Text("‹") }
                Text(LocalDate(year, selectedMonth, 1).month.name, Modifier.align(Alignment.CenterVertically))
                TextButton(enabled = selectedMonth < lastAvailableMonth, onClick = { if (selectedMonth < lastAvailableMonth) selectedMonth++ }) { Text("›") }
            }
        }
        if (monthView) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                grid.forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        week.forEach { day -> HeatmapCell(day, active, empty, muted, Modifier.weight(1f)) { selected = it } }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                grid.forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        week.forEach { day ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                day?.let { Text(it.date.month.name.take(1), style = MaterialTheme.typography.labelSmall) } ?: Spacer(Modifier.height(14.dp))
                                HeatmapCell(day, active, empty, muted) { selected = it }
                            }
                        }
                    }
                }
            }
        }
        selected?.let { day ->
            Text("${day.date} · ${day.totalMinutes.roundToInt()} ${stringResource(Res.string.study_log_minutes)}", style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(Res.string.study_log_day_detail, day.automatic.total.roundToInt(), day.manualMinutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            sessions.filter { it.date == day.date.toEpochDays().toLong() }.forEach { session ->
                val sourceName = sources.firstOrNull { it.id == session.sourceId }?.name
                val skill = stringResource(studySkillLabelResource(session.skill))
                Text(listOfNotNull("${session.durationMin} ${stringResource(Res.string.study_log_minutes)}", skill, sourceName, session.note?.takeIf(String::isNotBlank)).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        } ?: Text(stringResource(Res.string.study_log_heatmap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeatmapCell(day: StudyDayActivity?, active: androidx.compose.ui.graphics.Color, empty: androidx.compose.ui.graphics.Color, muted: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, onSelect: (StudyDayActivity) -> Unit) {
    val intensity = day?.let { (it.totalMinutes / 90.0).coerceIn(0.0, 1.0).toFloat() } ?: 0f
    val description = day?.let { "${it.date}: ${it.totalMinutes.roundToInt()} ${stringResource(Res.string.study_log_minutes)}" }
    Surface(
        onClick = { day?.let(onSelect) },
        modifier = modifier.size(11.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = when { day == null -> muted; intensity <= 0f -> empty; else -> androidx.compose.ui.graphics.lerp(empty, active, .2f + intensity * .8f) },
    ) { androidx.compose.foundation.layout.Box(Modifier.semantics { description?.let { contentDescription = it } }) }
}

@Composable fun DailyGoalCard(vm: AppViewModel, settings: AppSettings, manualSessions: List<StudySession> = emptyList()) {
    val lang = requireNotNull(settings.learningLang)
    val rows by remember(lang) { vm.container.database.activity().observe(lang) }.collectAsState(emptyList())
    var today by remember { mutableStateOf(localToday()) }
    LaunchedEffect(lang) { while (true) { today = localToday(); delay(30_000) } }
    val minutes = activityMinutes(rows, today, 1).total + manualSessions.filter { it.date == today.toEpochDays().toLong() }.sumOf { it.durationMin }
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
