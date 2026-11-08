package com.ziaee.zabankhan.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.zabankhan.data.StudySession
import com.ziaee.zabankhan.data.StudySkill
import com.ziaee.zabankhan.data.StudySource
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyLogEntryScreen(lang: String, entryId: Long?, onBack: () -> Unit) {
    val viewModel = rememberStudyLogViewModel(lang)
    val state by viewModel.uiState.collectAsState(initial = StudyLogUiState(lang))
    val scope = rememberCoroutineScope()
    val today = localToday()
    var originalId by rememberSaveable(entryId) { mutableStateOf<Long?>(null) }
    var dateEpochDay by rememberSaveable(entryId) { mutableLongStateOf(today.toEpochDays().toLong()) }
    var hours by rememberSaveable(entryId) { mutableStateOf("0") }
    var minutes by rememberSaveable(entryId) { mutableStateOf("30") }
    var skill by rememberSaveable(entryId) { mutableStateOf(StudySkill.CE) }
    var sourceId by rememberSaveable(entryId) { mutableStateOf<Long?>(null) }
    var note by rememberSaveable(entryId) { mutableStateOf("") }
    var loading by remember(entryId) { mutableStateOf(entryId != null) }
    var saving by remember { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showSourceEditor by rememberSaveable { mutableStateOf(false) }
    var newSourceName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<StudyLogValidation?>(null) }
    val date = LocalDate.fromEpochDays(dateEpochDay.toInt())
    val datePickerState = rememberEntryDatePickerState(dateEpochDay)

    LaunchedEffect(entryId) {
        val id = entryId ?: return@LaunchedEffect
        val session = viewModel.getSession(id)
        if (session != null) {
            originalId = session.id
            dateEpochDay = session.date
            hours = (session.durationMin / 60).toString()
            minutes = (session.durationMin % 60).toString()
            skill = session.skill
            sourceId = session.sourceId
            note = session.note.orEmpty()
        } else {
            error = StudyLogValidation.MISSING_SESSION
        }
        loading = false
    }

    LaunchedEffect(state.sources, newSourceName) {
        val name = newSourceName ?: return@LaunchedEffect
        val source = state.sources.firstOrNull { it.name.equals(name, ignoreCase = true) && !it.archived }
            ?: return@LaunchedEffect
        sourceId = source.id
        source.defaultSkill?.let { skill = it }
        newSourceName = null
    }

    fun save() {
        if (saving || loading) return
        val duration = parseStudyDuration(hours, minutes)
        if (duration == null) {
            error = StudyLogValidation.INVALID_DURATION
            return
        }
        saving = true
        scope.launch {
            try {
                val result = viewModel.saveSession(
                    StudySession(
                        id = originalId ?: 0,
                        date = dateEpochDay,
                        durationMin = duration,
                        skill = skill,
                        sourceId = sourceId,
                        note = note.trim().ifBlank { null },
                        lang = lang,
                    ),
                    today,
                )
                when (result) {
                    StudyLogSaveResult.Saved -> onBack()
                    StudyLogSaveResult.InFlight -> Unit
                    is StudyLogSaveResult.Invalid -> error = result.reason
                }
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (entryId == null) Res.string.study_log_add_time else Res.string.study_log_edit_session))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    TextButton(enabled = !saving && !loading, onClick = ::save) {
                        Text(stringResource(Res.string.study_log_save))
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                OutlinedButton(onClick = {
                    datePickerState.selectedDateMillis = dateEpochDay * MILLIS_PER_DAY
                    showDatePicker = true
                }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.study_log_date) + " · " + date.toString())
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.study_log_duration), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = hours,
                            onValueChange = { hours = it; error = null },
                            label = { Text(stringResource(Res.string.study_log_hours)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { minutes = it; error = null },
                            label = { Text(stringResource(Res.string.study_log_minutes)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.study_log_skill), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StudySkill.entries.forEach { option ->
                            FilterChip(
                                selected = skill == option,
                                onClick = { skill = option; error = null },
                                label = { Text(studySkillLabel(option)) },
                            )
                        }
                    }
                }

                SourcePicker(
                    sources = studySourcePickerOptions(state.sources, sourceId),
                    selectedSourceId = sourceId,
                    onSelect = { source ->
                        sourceId = source?.id
                        source?.defaultSkill?.let { skill = it }
                        error = null
                    },
                    onAddSource = { showSourceEditor = true },
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(Res.string.study_log_note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                error?.let {
                    Text(
                        stringResource(validationMessage(it)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(enabled = !saving, onClick = ::save, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.study_log_save))
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateEpochDay = it / MILLIS_PER_DAY }
                    showDatePicker = false
                }) { Text(stringResource(Res.string.study_log_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.study_log_cancel)) }
            },
        ) { DatePicker(state = datePickerState) }
    }

    if (showSourceEditor) {
        StudySourceEditorDialog(
            lang = lang,
            source = null,
            onDismiss = { showSourceEditor = false },
            onSave = viewModel::saveSource,
            onSaved = { newSourceName = it.name; showSourceEditor = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcePicker(
    sources: List<StudySource>,
    selectedSourceId: Long?,
    onSelect: (StudySource?) -> Unit,
    onAddSource: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = sources.firstOrNull { it.id == selectedSourceId }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.study_log_source_optional), style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.name ?: stringResource(Res.string.study_log_no_source), Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.study_log_no_source)) },
                    onClick = { onSelect(null); expanded = false },
                )
                sources.forEach { source ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(source.name)
                                Text(stringResource(studySourceKindLabelResource(source.kind)), style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        onClick = { onSelect(source); expanded = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.study_log_add_source)) },
                    onClick = { expanded = false; onAddSource() },
                )
            }
        }
    }
}

@Composable
private fun validationMessage(validation: StudyLogValidation) = when (validation) {
    StudyLogValidation.INVALID_DURATION -> Res.string.study_log_invalid_duration
    StudyLogValidation.FUTURE_DATE -> Res.string.study_log_future_date
    StudyLogValidation.MISSING_SOURCE, StudyLogValidation.ARCHIVED_SOURCE -> Res.string.study_log_missing_source
    StudyLogValidation.BLANK_SOURCE_NAME -> Res.string.study_log_source_name_required
    StudyLogValidation.DUPLICATE_SOURCE_NAME -> Res.string.study_log_source_duplicate
    else -> Res.string.study_log_save_error
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberEntryDatePickerState(dateEpochDay: Long): DatePickerState =
    rememberDatePickerState(initialSelectedDateMillis = dateEpochDay * MILLIS_PER_DAY)

private val MILLIS_PER_DAY = 86_400_000L
