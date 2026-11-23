package com.ziaee.zabankhan.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.zabankhan.app.LocalAppContainer
import com.ziaee.zabankhan.data.StudySession
import com.ziaee.zabankhan.data.StudySkill
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import com.ziaee.zabankhan.ui.EditorialCard
import com.ziaee.zabankhan.ui.EditorialTopAppBar
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun rememberStudyLogViewModel(lang: String): StudyLogViewModel {
    val container = LocalAppContainer.current
    return viewModel(key = "study-log-$lang") { StudyLogViewModel(container.database, lang) }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudyLogScreen(
    lang: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSources: () -> Unit,
) {
    val viewModel = rememberStudyLogViewModel(lang)
    val state by viewModel.uiState.collectAsState(initial = StudyLogUiState(lang))
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(Res.string.study_log_undo)
    val deletedLabel = stringResource(Res.string.study_log_deleted)
    var deleting by remember { mutableStateOf<StudySession?>(null) }

    Scaffold(
        topBar = {
            EditorialTopAppBar(
                title = stringResource(Res.string.study_log_title),
                back = onBack,
                windowInsets = TopAppBarDefaults.windowInsets,
                actions = {
                    IconButton(onClick = onSources) {
                        Icon(Icons.Default.LibraryBooks, contentDescription = stringResource(Res.string.study_log_manage_sources))
                    }
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.study_log_add_time))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onAdd) { Text(stringResource(Res.string.study_log_add_time)) }
                OutlinedButton(onClick = onSources) { Text(stringResource(Res.string.study_log_manage_sources)) }
            }
            if (state.sessions.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(Res.string.study_log_empty), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onAdd) { Text(stringResource(Res.string.study_log_add_time)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.sessions, key = { it.id }) { session ->
                        StudySessionRow(
                            session = session,
                            source = state.sources.firstOrNull { it.id == session.sourceId },
                            onEdit = { onEdit(session.id) },
                            onDelete = { deleting = session },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { session ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.study_log_delete_session_title)) },
            text = { Text(stringResource(Res.string.study_log_delete_session_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        if (viewModel.deleteSession(session)) {
                            val result = snackbar.showSnackbar(
                                message = deletedLabel,
                                actionLabel = undoLabel,
                                withDismissAction = true,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.restoreSession(session)
                        }
                    }
                }) { Text(stringResource(Res.string.study_log_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.study_log_cancel)) }
            },
        )
    }
}

@Composable
private fun StudySessionRow(
    session: StudySession,
    source: com.ziaee.zabankhan.data.StudySource?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val date = LocalDate.fromEpochDays(session.date.toInt()).toString()
    val sourceName = source?.name ?: stringResource(Res.string.study_log_no_source)
    val details = stringResource(studySkillLabelResource(session.skill)) + " · " + sourceName
    EditorialCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(studyDurationLabel(session.durationMin), style = MaterialTheme.typography.titleMedium)
                Text(details, style = MaterialTheme.typography.bodySmall)
                session.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.study_log_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.study_log_delete))
            }
        }
    }
}

@Composable
internal fun studyDurationLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(Res.string.study_duration_minutes_format, minutes)
    minutes % 60 == 0 -> stringResource(Res.string.study_duration_hours_format, minutes / 60)
    else -> stringResource(Res.string.study_duration_hours_minutes_format, minutes / 60, minutes % 60)
}

@Composable
internal fun studySkillLabel(skill: StudySkill): String = stringResource(studySkillLabelResource(skill))
