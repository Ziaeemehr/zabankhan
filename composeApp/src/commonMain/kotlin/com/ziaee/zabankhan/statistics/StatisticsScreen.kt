package com.ziaee.zabankhan.statistics

import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.FlowRow
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.srs.VocabStatus
import com.ziaee.zabankhan.srs.status
import com.ziaee.zabankhan.ui.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn

@Composable fun StatisticsScreen(vm: AppViewModel, settings: AppSettings, back: () -> Unit, openStudyLog: () -> Unit = {}) {
    val lang = requireNotNull(settings.learningLang)
    val rows by remember(lang) { vm.container.database.activity().observe(lang) }.collectAsState(emptyList())
    val words by remember(lang) { vm.container.vocab.observeAll(lang) }.collectAsState(emptyList())
    val attempts by remember(lang) { vm.container.database.shadowAttempts().observe(lang) }.collectAsState(emptyList())
    val studyVm = rememberStudyLogViewModel(lang)
    val studyState by studyVm.uiState.collectAsState(initial = StudyLogUiState(lang))
    var today by remember { mutableStateOf(localToday()) }
    var reviews by remember(lang) { mutableStateOf(emptyList<ReviewLogEntry>()) }
    LaunchedEffect(lang) { while (true) { today = localToday(); reviews = vm.container.vocab.reviewLogSince(lang, 0); delay(30_000) } }
    Page {
        EditorialTopAppBar(stringResource(Res.string.h2_progress), back)
        FilledTonalButton(onClick = openStudyLog, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.study_log_title) + " · " + stringResource(Res.string.study_log_add_time))
        }
        val streak = activityStreak(rows, today, reviews, attempts, manualSessions = studyState.allSessions, lang = lang)
        val knownWords = wordStatusCounts(words).known
        val weekMinutes = studyPeriodMinutes(rows, studyState.allSessions, today, 7)
        val priorWeekMinutes = studyPeriodMinutes(rows, studyState.allSessions, today.minus(7, DateTimeUnit.DAY), 7)
        val priorStreak = activityStreak(rows, today.minus(7, DateTimeUnit.DAY), reviews, attempts, manualSessions = studyState.allSessions, lang = lang)
        val recentKnown = countNewlyKnown(words, today.minus(6, DateTimeUnit.DAY), today)
        val previousKnown = countNewlyKnown(words, today.minus(13, DateTimeUnit.DAY), today.minus(7, DateTimeUnit.DAY))
        EditorialCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroMetric(stringResource(Res.string.stats_streak), streak, trendDelta(streak.toDouble(), priorStreak.toDouble()), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                    DailyGoalCard(settings, rows, today, studyState.allSessions, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroMetric(stringResource(Res.string.stats_minutes_week), weekMinutes.roundToInt(), trendDelta(weekMinutes, priorWeekMinutes), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                    HeroMetric(stringResource(Res.string.stats_words_learned), knownWords, trendDelta(recentKnown.toDouble(), previousKnown.toDouble()), Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        var chartPeriod by remember { mutableStateOf(ChartPeriod.WEEK) }
        val studyValues = remember(chartPeriod, today, rows, studyState.allSessions) { studyChartValues(rows, studyState.allSessions, today, chartPeriod) }
        val studyLabels = remember(chartPeriod, today, studyValues.size) { chartLabels(chartPeriod, today, studyValues.size) }
        EditorialCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.stats_study_chart), style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ChartPeriod.entries.forEachIndexed { i, period -> SegmentedButton(chartPeriod == period, { chartPeriod = period }, SegmentedButtonDefaults.itemShape(i, ChartPeriod.entries.size)) { Text(stringResource(period.label)) } }
                }
                SmoothStudyChart(studyValues, studyLabels)
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
                    Text(stringResource(Res.string.study_log_activity), style = MaterialTheme.typography.titleMedium)
                    Row {
                        IconButton(onClick = { if (heatmapYear > 2000) heatmapYear-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
                        Text(heatmapYear.toString(), Modifier.align(Alignment.CenterVertically))
                        IconButton(enabled = heatmapYear < today.year, onClick = { heatmapYear++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                    }
                }
                StudyYearHeatmap(combined, heatmapYear, today, firstDay, studyState.allSessions, studyState.sources)
            }
        }
        val skillMinutes = remember(studyState.allSessions, today) { minutesBySkill(studyState.allSessions, today.minus(6, DateTimeUnit.DAY), today) }
        EditorialCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.stats_time_by_skill), style = MaterialTheme.typography.titleMedium)
                if (skillMinutes.isEmpty()) Text(stringResource(Res.string.stats_time_by_skill_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val max = skillMinutes.maxOfOrNull { it.second } ?: 1
                skillMinutes.forEach { (skill, min) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(studySkillLabelResource(skill)), Modifier.weight(.8f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Box(Modifier.weight(1.4f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f))) {
                            Box(Modifier.fillMaxWidth(min / max.toFloat()).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .4f + .6f * min / max)))
                        }
                        Text(formatStudyMinutes(min), Modifier.weight(.7f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, maxLines = 1)
                    }
                }
            }
        }
        EditorialCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.study_log_weekly_reviews, dailyReviews(reviews, today, 7).sumOf { it.count }), style = MaterialTheme.typography.titleMedium)
                val weekDays = listOf(Res.string.ed_mon, Res.string.ed_tue, Res.string.ed_wed, Res.string.ed_thu, Res.string.ed_fri, Res.string.ed_sat, Res.string.ed_sun).map { stringResource(it) }
                val reviewDays = dailyReviews(reviews, today, 7)
                TapBarChart(reviewDays.map { it.count.toDouble() }, reviewDays.map { weekDays[it.date.dayOfWeek.ordinal] }, MaterialTheme.colorScheme.secondary)
            }
        }
        EditorialCard { WordDonut(wordStatusCounts(words)) }
    }
}
@Composable
private fun StudyYearHeatmap(days: List<StudyDayActivity>, year: Int, today: LocalDate, firstDay: DayOfWeek, sessions: List<StudySession>, sources: List<StudySource>) {
    val months = monthlyTotals(days, year)
    val total = months.sum().roundToInt()
    val bestMonth = months.maxOrNull()?.roundToInt() ?: 0
    var selectedMonth by remember(year) { mutableStateOf<Int?>(null) }
    var selectedDay by remember(year) { mutableStateOf<StudyDayActivity?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val bestMonthNumber = (months.indices.maxByOrNull { months[it] } ?: 0) + 1
    val monthLabel = "${stringResource(studyMonthShortLabelResource(bestMonthNumber))} ${formatStudyMinutes(bestMonth)}"
    val header = if (bestMonth == 0) stringResource(Res.string.stats_time_by_skill_empty) else stringResource(Res.string.study_log_year_summary, formatStudyMinutes(total), monthLabel)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(header, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.study_log_less), style = MaterialTheme.typography.labelSmall)
            listOf(.08f, .28f, .48f, .68f, .88f).forEach { level ->
                Box(Modifier.padding(start = 3.dp).size(9.dp).background(androidx.compose.ui.graphics.lerp(surfaceVariant, primary, level), MaterialTheme.shapes.extraSmall))
            }
            Text(stringResource(Res.string.study_log_more), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
        MonthlyStudyBars(months, year, primary, selectedMonth, { month ->
            selectedMonth = if (selectedMonth == month) null else month
            selectedDay = null
        })
        val month = selectedMonth
        if (month != null) {
            val selectedTotal = months[month - 1].roundToInt()
            StudyTooltip(stringResource(studyMonthLabelResource(month)), formatStudyMinutes(selectedTotal))
            val grid = studyMonthGrid(days, LocalDate(year, month, 1), firstDay, today)
            val weekdayLabels = listOf(Res.string.ed_mon, Res.string.ed_tue, Res.string.ed_wed, Res.string.ed_thu, Res.string.ed_fri, Res.string.ed_sat, Res.string.ed_sun).map { stringResource(it).take(2) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    weekdayLabels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                grid.forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { day ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                HeatmapCell(day, primary, surfaceVariant, Modifier.size(36.dp)) { selectedDay = it }
                            }
                        }
                    }
                }
            }
            selectedDay?.let { day ->
                val detailLines = sessions.filter { it.date == day.date.toEpochDays().toLong() }.map { session ->
                    val sourceName = sources.firstOrNull { it.id == session.sourceId }?.name
                    val skill = stringResource(studySkillLabelResource(session.skill))
                    listOfNotNull("${session.durationMin} ${stringResource(Res.string.study_log_minutes)}", skill, sourceName, session.note?.takeIf(String::isNotBlank)).joinToString(" · ")
                }
                StudyTooltip(day.date.toString(), "${formatStudyMinutes(day.totalMinutes.roundToInt())} · ${stringResource(Res.string.study_log_day_detail, day.automatic.total.roundToInt(), day.manualMinutes)}", detailLines)
            }
        }
    }
}

@Composable
private fun MonthlyStudyBars(values: List<Double>, year: Int, color: Color, selectedMonth: Int?, onSelect: (Int) -> Unit) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val labels = (1..12).map { stringResource(studyMonthShortLabelResource(it)).take(1) }
    val peak = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().height(142.dp)) {
            Canvas(Modifier.fillMaxSize().pointerInput(values, rtl) {
                detectTapGestures { point ->
                    val visual = (point.x / (size.width / 12f)).toInt().coerceIn(0, 11)
                    val month = if (rtl) 11 - visual else visual
                    if (year < localToday().year || month + 1 <= localToday().monthNumber) onSelect(month + 1)
                }
            }) {
                val slot = size.width / 12f
                val barWidth = slot * .58f
                values.forEachIndexed { index, value ->
                    val visual = if (rtl) 11 - index else index
                    val height = (size.height - 24.dp.toPx()) * (value / peak).toFloat()
                    val x = visual * slot + (slot - barWidth) / 2
                    if (height > 0f) {
                        val r = androidx.compose.ui.geometry.CornerRadius(minOf(9.dp.toPx(), barWidth / 2, height))
                        val top = size.height - height
                        val bar = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(x, top, x + barWidth, size.height, r, r, androidx.compose.ui.geometry.CornerRadius.Zero, androidx.compose.ui.geometry.CornerRadius.Zero)) }
                        drawPath(bar, Brush.verticalGradient(listOf(color, color.copy(alpha = .35f)), top, size.height))
                    }
                }
            }
            val labelIndex = selectedMonth?.minus(1) ?: values.indices.maxByOrNull { values[it] }
            if (labelIndex != null && values[labelIndex] > 0.0) {
                val visual = if (rtl) 11 - labelIndex else labelIndex
                val text = values[labelIndex].roundToInt().toString()
                val slot = maxWidth / 12
                val barTop = maxHeight - (maxHeight - 24.dp) * (values[labelIndex] / peak).toFloat() - 16.dp
                Text(text, Modifier.absoluteOffset(x = slot * visual, y = barTop.coerceAtLeast(0.dp)).width(slot), style = MaterialTheme.typography.labelSmall, color = color, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, softWrap = false)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEachIndexed { index, label ->
                val month = index + 1
                Text(label, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = if (selectedMonth == month) color else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HeatmapCell(day: StudyDayActivity?, active: Color, empty: Color, modifier: Modifier = Modifier, onSelect: (StudyDayActivity) -> Unit) {
    val intensity = day?.let { studyActivityIntensity(it.totalMinutes) } ?: 0f
    val color = if (day == null) Color.Transparent else androidx.compose.ui.graphics.lerp(empty, active, .2f + intensity * .8f)
    Surface(onClick = { day?.let(onSelect) }, modifier = modifier, shape = RoundedCornerShape(8.dp), color = color) {}
}

@Composable
private fun StudyTooltip(title: String, detail: String, lines: List<String> = emptyList()) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun formatStudyMinutes(minutes: Int): String =
    if (minutes >= 60) stringResource(Res.string.stats_duration_h_min, minutes / 60, minutes % 60) else stringResource(Res.string.stats_duration_min, minutes)

@Composable fun DailyGoalCard(settings: AppSettings, rows: List<ActivityLogEntry>, today: LocalDate, manualSessions: List<StudySession> = emptyList(), modifier: Modifier = Modifier) {
    val minutes = activityMinutes(rows, today, 1).total + manualSessions.filter { it.date == today.toEpochDays().toLong() }.sumOf { it.durationMin }
    val label = stringResource(Res.string.h2_goal_progress, minutes.roundToInt(), settings.dailyGoalMinutes)
    val progress = (minutes / settings.dailyGoalMinutes.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .2f), strokeWidth = 4.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

private fun formatChartAxisValue(value: Double): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.roundToInt().toString() else rounded.toString()
}

private enum class ChartPeriod {
    WEEK, MONTH, YEAR;
    val label get() = when (this) { WEEK -> Res.string.stats_period_week; MONTH -> Res.string.stats_period_month; YEAR -> Res.string.stats_period_year }
}

private fun studyPeriodMinutes(rows: List<ActivityLogEntry>, sessions: List<StudySession>, end: LocalDate, days: Int): Double {
    val startEpoch = end.minus(days - 1, DateTimeUnit.DAY).toEpochDays().toLong()
    val endEpoch = end.toEpochDays().toLong()
    return rows.filter { it.date >= end.minus(days - 1, DateTimeUnit.DAY).toString() && it.date <= end.toString() }.sumOf { it.totalMs() } / 60_000.0 +
        sessions.filter { it.date in startEpoch..endEpoch }.sumOf { it.durationMin }.toDouble()
}

private fun countNewlyKnown(words: List<VocabEntry>, start: LocalDate, end: LocalDate): Int {
    val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
    val from = start.atStartOfDayIn(zone).toEpochMilliseconds()
    val endExclusive = end.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
    return words.count {
        it.srsState().status() == VocabStatus.LEARNED &&
            (it.lastReviewedAtMs ?: it.createdAtMs) in from until endExclusive
    }
}

private fun studyChartValues(rows: List<ActivityLogEntry>, sessions: List<StudySession>, today: LocalDate, period: ChartPeriod): List<Double> = when (period) {
    ChartPeriod.WEEK -> lastDays(today, 7).map { date ->
        rows.filter { it.date == date.toString() }.sumOf { it.totalMs() } / 60_000.0 + sessions.filter { it.date == date.toEpochDays().toLong() }.sumOf { it.durationMin }
    }
    ChartPeriod.MONTH -> lastDays(today, 30).map { date ->
        rows.filter { it.date == date.toString() }.sumOf { it.totalMs() } / 60_000.0 + sessions.filter { it.date == date.toEpochDays().toLong() }.sumOf { it.durationMin }
    }
    ChartPeriod.YEAR -> (11 downTo 0).map { offset ->
        val monthIndex = today.monthNumber - 1 - offset
        val year = today.year + kotlin.math.floor(monthIndex / 12.0).toInt()
        val month = ((monthIndex % 12) + 12) % 12 + 1
        val prefix = year.toString().padStart(4, '0') + "-" + month.toString().padStart(2, '0')
        rows.filter { it.date.startsWith(prefix) }.sumOf { it.totalMs() } / 60_000.0 + sessions.filter { LocalDate.fromEpochDays(it.date).let { d -> d.year == year && d.monthNumber == month } }.sumOf { it.durationMin }
    }
}

private fun chartLabels(period: ChartPeriod, today: LocalDate, count: Int): List<String> = when (period) {
    ChartPeriod.YEAR -> (11 downTo 0).map { offset ->
        val index = today.monthNumber - 1 - offset
        val month = ((index % 12) + 12) % 12 + 1
        month.toString()
    }
    ChartPeriod.WEEK -> lastDays(today, count).map { it.dayOfMonth.toString() }
    ChartPeriod.MONTH -> lastDays(today, count).mapIndexed { i, date -> if (i % 5 == 0 || i == count - 1) date.dayOfMonth.toString() else "" }
}

@Composable private fun HeroMetric(label: String, value: Int, trend: TrendDelta, modifier: Modifier = Modifier, accent: Color) {
    Column(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2)
        if (trend.percent != 0) {
            val trendLabel = if (trend.rising) Res.string.stats_trend_up else Res.string.stats_trend_down
            Text(stringResource(trendLabel, trend.percent).replace("%%", "%"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable private fun SmoothStudyChart(values: List<Double>, labels: List<String>) {
    var selected by remember(values) { mutableIntStateOf(-1) }
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val description = stringResource(Res.string.ed_chart_description, stringResource(Res.string.stats_study_chart), labels.zip(values).joinToString { (label, value) -> "$label: ${value.roundToInt()}" })
    Column(Modifier.semantics { contentDescription = description }) {
        if (selected in values.indices) Text(stringResource(Res.string.stats_selected_value, labels[selected], values[selected].roundToInt()), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(48.dp).height(142.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Text(formatChartAxisValue(values.maxOrNull() ?: 0.0), style = MaterialTheme.typography.labelSmall)
                Text(formatChartAxisValue((values.maxOrNull() ?: 0.0) / 2), style = MaterialTheme.typography.labelSmall)
            Text("0", Modifier.padding(end = 2.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
            }
            Canvas(Modifier.weight(1f).height(142.dp).pointerInput(values, rtl) { detectTapGestures { p -> if (values.isNotEmpty()) { val col = ((p.x / size.width) * values.size).toInt().coerceIn(0, values.lastIndex); selected = if (rtl) values.lastIndex - col else col } } }) {
                val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                val points = values.mapIndexed { i, v -> Offset(size.width * (i + .5f) / values.size.coerceAtLeast(1), size.height * (1f - (v / max).toFloat())) }
                withTransform({ if (rtl) scale(-1f, 1f, pivot = Offset(size.width / 2, size.height / 2)) }) {
                    for (i in 0..2) drawLine(grid.copy(alpha = .65f), Offset(0f, size.height * i / 2), Offset(size.width, size.height * i / 2), 1.dp.toPx())
                    val path = smoothPath(points)
                    val area = Path().apply { addPath(path); lineTo(points.last().x, size.height); lineTo(points.first().x, size.height); close() }
                    drawPath(area, Brush.verticalGradient(listOf(primary.copy(alpha = .35f), primary.copy(alpha = .02f))))
                    drawPath(path, primary, style = Stroke(3.dp.toPx()))
                    points.forEachIndexed { i, pt -> drawCircle(primary, if (i == selected) 6.dp.toPx() else 3.dp.toPx(), pt) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 48.dp)) { labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1) } }
    }
}

private fun smoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (i in 0 until points.lastIndex) {
        val a = points[i]; val b = points[i + 1]; val dx = (b.x - a.x) / 3f
        cubicTo(a.x + dx, a.y, b.x - dx, b.y, b.x, b.y)
    }
}

@Composable private fun TapBarChart(values: List<Double>, labels: List<String>, color: Color) {
    var selected by remember(values) { mutableIntStateOf(-1) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val description = stringResource(Res.string.ed_chart_description, stringResource(Res.string.study_log_weekly_reviews, values.sumOf { it.roundToInt() }), labels.zip(values).joinToString { (label, value) -> "$label: ${value.roundToInt()}" })
    Column(Modifier.semantics { contentDescription = description }) {
        if (selected in values.indices) Text(stringResource(Res.string.stats_selected_count, labels[selected], values[selected].roundToInt()), style = MaterialTheme.typography.labelMedium)
        Canvas(Modifier.fillMaxWidth().height(120.dp).pointerInput(values, rtl) { detectTapGestures { p -> if (values.isNotEmpty()) { val col = (p.x / size.width * values.size).toInt().coerceIn(0, values.lastIndex); selected = if (rtl) values.lastIndex - col else col } } }) {
            val max = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0); val cell = size.width / values.size.coerceAtLeast(1)
            for (i in 0..2) drawLine(grid, Offset(0f, size.height * i / 2), Offset(size.width, size.height * i / 2), 1.dp.toPx())
            values.forEachIndexed { i, v -> val col = if (rtl) values.lastIndex - i else i; val h = (v / max * size.height).toFloat(); if (h > 0f) drawRoundRect(color, Offset(col * cell + cell * .2f, size.height - h), Size(cell * .6f, h), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())) }
        }
        Row { labels.forEach { Text(it.take(2), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
    }
}

@Composable private fun WordDonut(counts: WordStatusCounts) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    val colors = listOf(MaterialTheme.colorScheme.outline, tertiary.copy(alpha = .5f), tertiary)
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val track = MaterialTheme.colorScheme.surfaceVariant
    val labels = listOf(stringResource(Res.string.stats_new), stringResource(Res.string.stats_learning), stringResource(Res.string.stats_known))
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(Res.string.stats_word_status), style = MaterialTheme.typography.titleMedium)
        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2f
                val arcTopLeft = Offset(inset, inset)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, -90f, 360f, false, arcTopLeft, arcSize, style = Stroke(stroke))
                var start = -90f
                val values = listOf(counts.new, counts.learning, counts.known)
                val total = counts.total
                val flip = if (rtl) -1f else 1f
                scale(flip, 1f, Offset(size.width / 2, size.height / 2)) {
                if (total > 0) values.forEachIndexed { i, v -> val sweep = v.toFloat() / total * 360f; drawArc(colors[i], start, sweep, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Butt)); start += sweep }
                }
            }
            Text(counts.total.toString(), style = MaterialTheme.typography.headlineMedium)
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label -> Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).background(colors[i], RoundedCornerShape(50))); Text(" $label ${listOf(counts.new, counts.learning, counts.known)[i]}", style = MaterialTheme.typography.labelMedium) } }
        }
    }
}

private fun studyMonthLabelResource(month: Int) = when (month) {
    1 -> Res.string.study_month_january
    2 -> Res.string.study_month_february
    3 -> Res.string.study_month_march
    4 -> Res.string.study_month_april
    5 -> Res.string.study_month_may
    6 -> Res.string.study_month_june
    7 -> Res.string.study_month_july
    8 -> Res.string.study_month_august
    9 -> Res.string.study_month_september
    10 -> Res.string.study_month_october
    11 -> Res.string.study_month_november
    else -> Res.string.study_month_december
}

private fun studyMonthShortLabelResource(month: Int) = when (month) {
    1 -> Res.string.study_month_short_january
    2 -> Res.string.study_month_short_february
    3 -> Res.string.study_month_short_march
    4 -> Res.string.study_month_short_april
    5 -> Res.string.study_month_short_may
    6 -> Res.string.study_month_short_june
    7 -> Res.string.study_month_short_july
    8 -> Res.string.study_month_short_august
    9 -> Res.string.study_month_short_september
    10 -> Res.string.study_month_short_october
    11 -> Res.string.study_month_short_november
    else -> Res.string.study_month_short_december
}
