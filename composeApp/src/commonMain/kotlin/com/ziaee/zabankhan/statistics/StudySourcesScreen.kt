package com.ziaee.zabankhan.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.data.SourceKind
import com.ziaee.zabankhan.data.StudySkill
import com.ziaee.zabankhan.data.StudySource
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.launch
import com.ziaee.zabankhan.ui.EditorialCard
import com.ziaee.zabankhan.ui.EditorialTopAppBar
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudySourcesScreen(lang: String, onBack: () -> Unit) {
    val vm = rememberStudyLogViewModel(lang)
    val state by vm.uiState.collectAsState(initial = StudyLogUiState(lang))
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<StudySource?>(null) }
    var deleting by remember { mutableStateOf<StudySource?>(null) }
    Scaffold(topBar = {
        EditorialTopAppBar(
            title = stringResource(Res.string.study_log_manage_sources),
            back = onBack,
            windowInsets = TopAppBarDefaults.windowInsets,
            actions = { IconButton(onClick = { editing = StudySource(lang = lang, name = "", kind = SourceKind.BOOK) }) { Icon(Icons.Default.Add, null) } },
        )
    }) { padding ->
        if (state.sources.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text(stringResource(Res.string.study_log_empty_sources)) }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.sources, key = { it.id }) { source ->
                EditorialCard {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(source.name, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(studySourceKindLabelResource(source.kind)) + source.defaultSkill?.let { " · ${studySkillLabel(it)}" }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            if (source.archived) Text(stringResource(Res.string.study_log_source_archived), style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { editing = source }) { Icon(Icons.Default.Edit, stringResource(Res.string.study_log_edit)) }
                        IconButton(onClick = { scope.launch { vm.archiveSource(source.id, !source.archived) } }) {
                            Icon(if (source.archived) Icons.Default.Unarchive else Icons.Default.Archive, stringResource(Res.string.study_log_source_archive))
                        }
                        IconButton(onClick = { deleting = source }) { Icon(Icons.Default.Delete, stringResource(Res.string.study_log_delete)) }
                    }
                }
            }
        }
    }
    editing?.let { source -> StudySourceEditorDialog(lang, source.takeIf { it.id != 0L }, onDismiss = { editing = null }, onSave = vm::saveSource, onSaved = { editing = null }) }
    deleting?.let { source ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.study_log_delete_source_title)) },
            text = { Text(stringResource(Res.string.study_log_delete_source_message)) },
            confirmButton = { TextButton(onClick = { deleting = null; scope.launch { vm.deleteUnusedSource(source.id) } }) { Text(stringResource(Res.string.study_log_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.study_log_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudySourceEditorDialog(
    lang: String,
    source: StudySource?,
    onDismiss: () -> Unit,
    onSave: suspend (StudySource) -> StudyLogValidation,
    onSaved: (StudySource) -> Unit,
) {
    var name by remember(source?.id) { mutableStateOf(source?.name.orEmpty()) }
    var kind by remember(source?.id) { mutableStateOf(source?.kind ?: SourceKind.BOOK) }
    var skill by remember(source?.id) { mutableStateOf(source?.defaultSkill) }
    var kindExpanded by remember { mutableStateOf(false) }
    var skillExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StudyLogValidation?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (source == null) Res.string.study_log_add_source else Res.string.study_log_edit_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text(stringResource(Res.string.study_log_source_name)) }, singleLine = true)
                ExposedDropdownMenuBox(expanded = kindExpanded, onExpandedChange = { kindExpanded = it }) {
                    OutlinedTextField(value = stringResource(studySourceKindLabelResource(kind)), onValueChange = {}, readOnly = true, label = { Text(stringResource(Res.string.study_log_source_type)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = kindExpanded, onDismissRequest = { kindExpanded = false }) { SourceKind.entries.forEach { option -> DropdownMenuItem(text = { Text(stringResource(studySourceKindLabelResource(option))) }, onClick = { kind = option; kindExpanded = false }) } }
                }
                ExposedDropdownMenuBox(expanded = skillExpanded, onExpandedChange = { skillExpanded = it }) {
                    OutlinedTextField(value = skill?.let { stringResource(studySkillLabelResource(it)) } ?: stringResource(Res.string.study_log_no_default_skill), onValueChange = {}, readOnly = true, label = { Text(stringResource(Res.string.study_log_default_skill)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(skillExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = skillExpanded, onDismissRequest = { skillExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.study_log_no_default_skill)) }, onClick = { skill = null; skillExpanded = false })
                        StudySkill.entries.forEach { option -> DropdownMenuItem(text = { Text(studySkillLabel(option)) }, onClick = { skill = option; skillExpanded = false }) }
                    }
                }
                error?.let { Text(stringResource(if (it == StudyLogValidation.DUPLICATE_SOURCE_NAME) Res.string.study_log_source_duplicate else Res.string.study_log_source_name_required), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    val value = source?.copy(name = name, kind = kind, defaultSkill = skill) ?: StudySource(lang = lang, name = name, kind = kind, defaultSkill = skill)
                    val result = onSave(value)
                    saving = false
                    if (result == StudyLogValidation.VALID) onSaved(value) else error = result
                }
            }) { Text(stringResource(Res.string.study_log_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.study_log_cancel)) } },
    )
}
