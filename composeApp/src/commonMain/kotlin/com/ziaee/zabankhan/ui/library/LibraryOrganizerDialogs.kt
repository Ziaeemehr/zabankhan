package com.ziaee.zabankhan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.StringResource
import com.ziaee.zabankhan.data.LibraryFolder
import com.ziaee.zabankhan.data.LibraryTag
import com.ziaee.zabankhan.data.TextDocument

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryOrganizerDialog(
    folders: List<LibraryFolder>,
    documents: List<TextDocument>,
    tags: List<LibraryTag>,
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long, Boolean) -> Unit,
    onCreateTag: (String) -> Unit,
    onRenameTag: (Long, String) -> Unit,
    onDeleteTag: (Long) -> Unit,
    onCreateFolderIn: (String, Long?) -> Unit = { name, _ -> onCreateFolder(name) },
    onMoveFolder: (Long, Long?) -> Unit = { _, _ -> }
) {
    var folderName by remember { mutableStateOf("") }
    var tagName by remember { mutableStateOf("") }
    var addSubfolderTo by remember { mutableStateOf<LibraryFolder?>(null) }
    var renameFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var moveFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var renameTag by remember { mutableStateOf<LibraryTag?>(null) }
    var deleteFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var confirmDeleteFolderWithFiles by remember { mutableStateOf<LibraryFolder?>(null) }
    var deleteTag by remember { mutableStateOf<LibraryTag?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp).testTag("libraryOrganizerDialog"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.heightIn(max = 600.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.library_manage_organizer), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
                    }
                }
                HorizontalDivider()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                FolderOrganizerSection(
                    folders = folders,
                    input = folderName,
                    onInputChange = { folderName = it },
                    onAdd = { onCreateFolder(folderName); folderName = "" },
                    onAddSubfolder = { addSubfolderTo = it },
                    onRename = { renameFolder = it },
                    onMove = { moveFolder = it },
                    onDelete = { deleteFolder = it }
                )
                OrganizerSection(
                    title = stringResource(Res.string.library_tags),
                    items = tags.map { it.id to it.name },
                    input = tagName,
                    inputLabel = stringResource(Res.string.library_new_tag),
                    onInputChange = { tagName = it },
                    onAdd = { onCreateTag(tagName); tagName = "" },
                    onRename = { id -> renameTag = tags.first { it.id == id } },
                    onDelete = { id -> deleteTag = tags.first { it.id == id } },
                    testTag = "tag"
                )
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = onDismiss, modifier = Modifier.testTag("libraryOrganizerDone")
                    ) { Text(stringResource(Res.string.action_close)) }
                }
            }
        }
    }

    addSubfolderTo?.let { parent ->
        NameOrganizerDialog(
            title = Res.string.add_subfolder,
            inputLabel = Res.string.library_new_folder,
            testTag = "addSubfolderDialog",
            onSave = { onCreateFolderIn(it, parent.id); addSubfolderTo = null },
            onDismiss = { addSubfolderTo = null }
        )
    }
    renameFolder?.let { item ->
        RenameOrganizerDialog(item.name, Res.string.library_rename_folder, {
            onRenameFolder(item.id, it); renameFolder = null
        }) { renameFolder = null }
    }
    moveFolder?.let { item ->
        MoveFolderDialog(
            folder = item,
            folders = folders,
            onDismiss = { moveFolder = null },
            onMove = { onMoveFolder(item.id, it); moveFolder = null }
        )
    }
    renameTag?.let { item ->
        RenameOrganizerDialog(item.name, Res.string.library_rename_tag, {
            onRenameTag(item.id, it); renameTag = null
        }) { renameTag = null }
    }
    deleteFolder?.let { item ->
        var deleteFiles by remember(item.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { deleteFolder = null },
            title = { Text(stringResource(Res.string.library_delete_folder_confirm)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                    Text(stringResource(Res.string.library_delete_folder_files))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (deleteFiles) {
                        confirmDeleteFolderWithFiles = item
                    } else {
                        onDeleteFolder(item.id, false)
                    }
                    deleteFolder = null
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolder = null }) { Text(stringResource(Res.string.action_cancel)) }
            }
        )
    }
    confirmDeleteFolderWithFiles?.let { item ->
        val folderIds = remember(folders, item.id) { descendantIds(folders, item.id) }
        val textCount = documents.count { it.folderId in folderIds }
        AlertDialog(
            onDismissRequest = { confirmDeleteFolderWithFiles = null },
            title = { Text(stringResource(Res.string.library_delete_folder_with_files_title)) },
            text = { Text(stringResource(Res.string.library_delete_folder_with_files_confirm, textCount)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder(item.id, true)
                    confirmDeleteFolderWithFiles = null
                }) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFolderWithFiles = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
    deleteTag?.let { item ->
        DeleteOrganizerDialog(Res.string.library_delete_tag_confirm, {
            onDeleteTag(item.id); deleteTag = null
        }) { deleteTag = null }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FolderOrganizerSection(
    folders: List<LibraryFolder>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onAddSubfolder: (LibraryFolder) -> Unit,
    onRename: (LibraryFolder) -> Unit,
    onMove: (LibraryFolder) -> Unit,
    onDelete: (LibraryFolder) -> Unit
) {
    Text(stringResource(Res.string.library_folders), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    flattenTree(folders).forEach { (folder, depth) ->
        Row(Modifier.fillMaxWidth().padding(start = (depth * 16).dp, bottom = 8.dp).testTag("folderRow_${folder.id}")) {
            if (depth > 0) Box(Modifier.width(1.dp).heightIn(min = 48.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OrganizerIcon(Icons.Outlined.Folder, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(folder.name, modifier = Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.titleSmall)
                    }
                    FlowRow(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        OrganizerAction(Icons.Outlined.CreateNewFolder, stringResource(Res.string.add_subfolder), { onAddSubfolder(folder) }, "folderAddSubfolder_${folder.id}")
                        OrganizerAction(Icons.Outlined.Edit, stringResource(Res.string.action_edit), { onRename(folder) }, "folderRename_${folder.id}")
                        OrganizerAction(Icons.Outlined.DriveFileMove, stringResource(Res.string.move_folder), { onMove(folder) }, "folderMove_${folder.id}")
                        OrganizerAction(Icons.Outlined.Delete, stringResource(Res.string.action_delete), { onDelete(folder) }, "folderDelete_${folder.id}", destructive = true)
                    }
                }
            }
        }
    }
    OrganizerInput(
        input = input,
        inputLabel = stringResource(Res.string.library_new_folder),
        onInputChange = onInputChange,
        onAdd = onAdd,
        testTag = "folder"
    )
}

@Composable
private fun OrganizerSection(
    title: String,
    items: List<Pair<Long, String>>,
    input: String,
    inputLabel: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRename: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    testTag: String
) {
    Spacer(Modifier.height(16.dp)); HorizontalDivider(); Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    items.forEach { (id, name) ->
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OrganizerIcon(Icons.Outlined.Sell, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                Text(name, modifier = Modifier.weight(1f).padding(start = 10.dp))
                OrganizerAction(Icons.Outlined.Edit, stringResource(Res.string.action_edit), { onRename(id) }, "${testTag}Rename_$id")
                OrganizerAction(Icons.Outlined.Delete, stringResource(Res.string.action_delete), { onDelete(id) }, "${testTag}Delete_$id", destructive = true)
            }
        }
    }
    OrganizerInput(input, inputLabel, onInputChange, onAdd, testTag)
}

@Composable
private fun OrganizerInput(
    input: String,
    inputLabel: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    testTag: String,
    inputTestTag: String = "${testTag}NameInput"
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text(inputLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) onAdd() }),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).testTag(inputTestTag)
        )
        IconButton(onClick = onAdd, enabled = input.isNotBlank(), modifier = Modifier.testTag("${testTag}Add")) {
            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.action_add))
        }
    }
}

@Composable
private fun OrganizerIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, background: Color, tint: Color) {
    Surface(shape = CircleShape, color = background, modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun OrganizerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tag: String, destructive: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp).testTag(tag)) {
        Surface(shape = CircleShape, color = if (destructive) scheme.errorContainer else scheme.secondaryContainer, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = if (destructive) scheme.onErrorContainer else scheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun NameOrganizerDialog(
    title: StringResource,
    inputLabel: StringResource,
    testTag: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.testTag(testTag),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(inputLabel)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSave(name) }),
                modifier = Modifier.testTag("${testTag}NameInput")
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(Res.string.action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

@Composable
private fun RenameOrganizerDialog(
    initialName: String,
    title: StringResource,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

@Composable
private fun DeleteOrganizerDialog(title: StringResource, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

@Composable
private fun MoveFolderDialog(
    folder: LibraryFolder,
    folders: List<LibraryFolder>,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit
) {
    var selectedId by remember(folder.id, folder.parentId) { mutableStateOf(folder.parentId) }
    val validTargets = remember(folders, folder.id) {
        flattenTree(folders).filter { (candidate, _) -> canMoveFolder(folders, folder.id, candidate.id) }
    }
    AlertDialog(
        modifier = Modifier.testTag("moveFolderDialog_${folder.id}"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.move_folder)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                FolderChoice(
                    id = null,
                    name = stringResource(Res.string.top_level),
                    selected = selectedId == null,
                    depth = 0,
                    testTagPrefix = "folderMoveTarget"
                ) { selectedId = null }
                validTargets.forEach { (candidate, depth) ->
                    FolderChoice(
                        id = candidate.id,
                        name = candidate.name,
                        selected = selectedId == candidate.id,
                        depth = depth,
                        testTagPrefix = "folderMoveTarget"
                    ) { selectedId = candidate.id }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMove(selectedId) }, modifier = Modifier.testTag("moveFolderConfirm")) {
                Text(stringResource(Res.string.library_move))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

@Composable
internal fun MoveToFolderDialog(
    doc: TextDocument? = null,
    folders: List<LibraryFolder>,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit,
    onCreateAndMove: (String) -> Unit,
    initialFolderId: Long? = doc?.folderId
) {
    var selectedId by remember(doc?.id, initialFolderId) { mutableStateOf(initialFolderId) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.testTag("moveToFolderDialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.library_move_to_folder)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                FolderChoice(
                    id = null,
                    name = stringResource(Res.string.library_no_folder),
                    selected = selectedId == null
                ) { selectedId = null }
                flattenTree(folders).forEach { (folder, depth) ->
                    FolderChoice(folder.id, folder.name, selectedId == folder.id, depth) { selectedId = folder.id }
                }
                OrganizerInput(
                    input = newName,
                    inputLabel = stringResource(Res.string.library_new_folder),
                    onInputChange = { newName = it },
                    onAdd = { onCreateAndMove(newName) },
                    testTag = "moveNewFolder",
                    inputTestTag = "moveNewFolderInput"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newName.isNotBlank()) onCreateAndMove(newName) else onMove(selectedId)
            }) { Text(stringResource(Res.string.library_move)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

@Composable
private fun FolderChoice(
    id: Long?,
    name: String,
    selected: Boolean,
    depth: Int = 0,
    testTagPrefix: String = "folderChoice",
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp).clickable(onClick = onSelect)
            .testTag("${testTagPrefix}_${id ?: "none"}")
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(name)
    }
}

@Composable
internal fun EditTagsDialog(
    doc: TextDocument? = null,
    tags: List<LibraryTag>,
    initialTagIds: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
    onCreateAndAssign: (String, Set<Long>) -> Unit,
    addMode: Boolean = false
) {
    var selectedIds by remember(doc?.id, initialTagIds) { mutableStateOf(initialTagIds) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.testTag("editTagsDialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (addMode) Res.string.library_add_tags else Res.string.library_edit_tags)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                tags.forEach { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedIds = selectedIds.toggle(tag.id)
                        }.testTag("tagChoice_${tag.id}")
                    ) {
                        Checkbox(
                            checked = tag.id in selectedIds,
                            onCheckedChange = { selectedIds = selectedIds.toggle(tag.id) }
                        )
                        Text(tag.name)
                    }
                }
                OrganizerInput(
                    input = newName,
                    inputLabel = stringResource(Res.string.library_new_tag),
                    onInputChange = { newName = it },
                    onAdd = { onCreateAndAssign(newName, selectedIds) },
                    testTag = "editTagsNew",
                    inputTestTag = "editTagsNewInput"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newName.isNotBlank()) onCreateAndAssign(newName, selectedIds) else onSave(selectedIds)
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}

private fun Set<Long>.toggle(id: Long): Set<Long> = toMutableSet().apply {
    if (!add(id)) remove(id)
}
