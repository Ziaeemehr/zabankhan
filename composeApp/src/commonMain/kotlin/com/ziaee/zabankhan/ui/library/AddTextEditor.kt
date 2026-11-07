package com.ziaee.zabankhan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTextEditor(
    learningLanguageName: String,
    levelOptions: (@Composable () -> Unit)? = null,
    initialTitle: String = "",
    initialBody: String = "",
    bodyEditable: Boolean = true,
    dialogTitle: String? = null,
    confirmLabel: String? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var body by remember(initialBody) { mutableStateOf(TextFieldValue(initialBody)) }
    val clipboard = LocalClipboardManager.current
    val wordCount = remember(body.text) {
        body.text.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.size ?: 0
    }
    val history = remember(initialBody) { mutableStateListOf(initialBody) }
    var historyIndex by remember(initialBody) { mutableStateOf(0) }
    var findVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var currentMatch by remember { mutableStateOf(0) }
    var toolsExpanded by remember { mutableStateOf(false) }
    var replacementNotice by remember { mutableStateOf<Int?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val findFocusRequester = remember { FocusRequester() }
    val bodyFocusRequester = remember { FocusRequester() }
    val matches = remember(body.text, findQuery, matchCase, wholeWord) {
        findMatches(body.text, findQuery, matchCase, wholeWord)
    }
    val hasChanges = title != initialTitle || body.text != initialBody

    fun pushSnapshot(text: String) {
        if (history.getOrNull(historyIndex) == text) return
        while (history.lastIndex > historyIndex) history.removeAt(history.lastIndex)
        history += text
        if (history.size > 100) history.removeAt(0) else historyIndex++
        historyIndex = history.lastIndex
    }

    fun applyBodyText(text: String, selection: TextRange = TextRange(text.length)) {
        pushSnapshot(body.text)
        body = TextFieldValue(
            text,
            TextRange(
                selection.start.coerceIn(0, text.length),
                selection.end.coerceIn(0, text.length)
            )
        )
        pushSnapshot(text)
    }

    fun requestDismiss() {
        if (hasChanges) confirmDiscard = true else onDismiss()
    }

    fun selectMatch(index: Int) {
        if (matches.isEmpty()) return
        currentMatch = (index % matches.size + matches.size) % matches.size
        val range = matches[currentMatch]
        body = body.copy(selection = TextRange(range.first, range.last + 1))
        bodyFocusRequester.requestFocus()
    }

    LaunchedEffect(body.text) {
        delay(800)
        pushSnapshot(body.text)
    }
    LaunchedEffect(findVisible) {
        if (findVisible) findFocusRequester.requestFocus()
    }
    LaunchedEffect(matches) {
        currentMatch = currentMatch.coerceIn(0, (matches.size - 1).coerceAtLeast(0))
    }
    LaunchedEffect(replacementNotice) {
        if (replacementNotice != null) {
            delay(2_000)
            replacementNotice = null
        }
    }

    Dialog(
        onDismissRequest = ::requestDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = {
                LibraryTopAppBar(
                    title = dialogTitle
                        ?: stringResource(if (initialBody.isBlank()) Res.string.add_text_title_new else Res.string.add_text_title_review),
                    navigationIcon = {
                        IconButton(onClick = ::requestDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_cancel))
                        }
                    },
                    actions = {
                        if (bodyEditable) {
                            IconButton(
                                onClick = {
                                    pushSnapshot(body.text)
                                    if (historyIndex > 0) {
                                        historyIndex--
                                        body = historyValue(body.text, history[historyIndex])
                                    }
                                },
                                enabled = historyIndex > 0 || body.text != history[historyIndex]
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(Res.string.editor_undo))
                            }
                            IconButton(
                                onClick = {
                                    if (historyIndex < history.lastIndex) {
                                        historyIndex++
                                        body = historyValue(body.text, history[historyIndex])
                                    }
                                },
                                enabled = historyIndex < history.lastIndex
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Redo, stringResource(Res.string.editor_redo))
                            }
                            IconButton(onClick = { findVisible = !findVisible }) {
                                Icon(Icons.Default.Search, stringResource(Res.string.editor_find))
                            }
                            Box {
                                IconButton(onClick = { toolsExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, stringResource(Res.string.editor_tools))
                                }
                                DropdownMenu(expanded = toolsExpanded, onDismissRequest = { toolsExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.editor_join_lines)) },
                                        onClick = {
                                            toolsExpanded = false
                                            applyBodyText(joinWrappedLines(body.text))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.editor_tidy_spaces)) },
                                        onClick = {
                                            toolsExpanded = false
                                            applyBodyText(tidyWhitespace(body.text))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.editor_copy_all)) },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                        onClick = {
                                            toolsExpanded = false
                                            clipboard.setText(AnnotatedString(body.text))
                                        }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = { onSave(title, body.text) },
                            enabled = !bodyEditable || body.text.isNotBlank(),
                            modifier = Modifier.padding(end = ZabankhanDesign.spacing.xSmall)
                        ) { Text(confirmLabel ?: stringResource(Res.string.add_text_save_open)) }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding)
                    .padding(horizontal = ZabankhanDesign.spacing.medium)
            ) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(Res.string.add_text_field_title)) },
                    textStyle = MaterialTheme.typography.headlineMedium.merge(contentStyle(title)),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                levelOptions?.invoke()
                if (bodyEditable && findVisible) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = findQuery,
                                onValueChange = { findQuery = it; currentMatch = 0 },
                                placeholder = { Text(stringResource(Res.string.editor_find_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).focusRequester(findFocusRequester)
                            )
                            Text(
                                stringResource(
                                    Res.string.editor_match_count,
                                    if (matches.isEmpty()) 0 else currentMatch + 1,
                                    matches.size
                                ),
                                style = MaterialTheme.typography.labelMedium
                            )
                            IconButton(onClick = { selectMatch(currentMatch - 1) }, enabled = matches.isNotEmpty()) {
                                Icon(Icons.Default.KeyboardArrowUp, stringResource(Res.string.editor_previous_match))
                            }
                            IconButton(onClick = { selectMatch(currentMatch + 1) }, enabled = matches.isNotEmpty()) {
                                Icon(Icons.Default.KeyboardArrowDown, stringResource(Res.string.editor_next_match))
                            }
                            IconButton(onClick = { findVisible = false }) {
                                Icon(Icons.Default.Close, stringResource(Res.string.editor_close_find))
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = replacement,
                                onValueChange = { replacement = it },
                                placeholder = { Text(stringResource(Res.string.editor_replace_hint)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = matchCase,
                                onClick = { matchCase = !matchCase; currentMatch = 0 },
                                label = { Text(stringResource(Res.string.editor_match_case_short)) }
                            )
                            Spacer(Modifier.size(4.dp))
                            FilterChip(
                                selected = wholeWord,
                                onClick = { wholeWord = !wholeWord; currentMatch = 0 },
                                label = { Text(stringResource(Res.string.editor_whole_word)) }
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                enabled = matches.isNotEmpty(),
                                onClick = {
                                    val match = matches[currentMatch]
                                    // Only replace a match the user can see: the first tap selects it.
                                    if (body.selection != TextRange(match.first, match.last + 1)) {
                                        selectMatch(currentMatch)
                                        return@TextButton
                                    }
                                    val replaced = body.text.replaceRange(match, replacement)
                                    val remaining = findMatches(replaced, findQuery, matchCase, wholeWord)
                                    val nextIndex = remaining.indexOfFirst {
                                        it.first >= match.first + replacement.length
                                    }.takeIf { it >= 0 } ?: 0
                                    val nextSelection = remaining.getOrNull(nextIndex)?.let {
                                        TextRange(it.first, it.last + 1)
                                    } ?: TextRange(match.first + replacement.length)
                                    applyBodyText(
                                        replaced,
                                        nextSelection
                                    )
                                    currentMatch = nextIndex
                                    replacementNotice = null
                                }
                            ) { Text(stringResource(Res.string.editor_replace)) }
                            TextButton(
                                enabled = matches.isNotEmpty(),
                                onClick = {
                                    val (replaced, count) = replaceAll(
                                        body.text, findQuery, replacement, matchCase, wholeWord
                                    )
                                    applyBodyText(replaced)
                                    replacementNotice = count
                                }
                            ) { Text(stringResource(Res.string.editor_replace_all)) }
                            replacementNotice?.let {
                                Text(stringResource(Res.string.editor_replaced_count, it), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        if (bodyEditable) {
                            TextButton(onClick = {
                                clipboard.getText()?.text?.takeIf { it.isNotEmpty() }?.let { pasted ->
                                    applyBodyText(if (body.text.isBlank()) pasted else "${body.text}\n$pasted")
                                }
                            }) { Text(stringResource(Res.string.add_text_paste)) }
                            TextButton(onClick = { applyBodyText("") }, enabled = body.text.isNotEmpty()) {
                                Text(stringResource(Res.string.add_text_clear))
                            }
                        }
                    }
                    Text(
                        stringResource(Res.string.add_text_counts, wordCount, body.text.length),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (bodyEditable) {
                    TextField(
                        value = body,
                        onValueChange = {
                            if (it.text != body.text && historyIndex < history.lastIndex) {
                                while (history.lastIndex > historyIndex) history.removeAt(history.lastIndex)
                            }
                            body = it
                        },
                        placeholder = { Text(stringResource(Res.string.add_text_body_hint, learningLanguageName)) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                            fontFamily = editorialStyle(body.text).fontFamily
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .focusRequester(bodyFocusRequester)
                            .padding(bottom = ZabankhanDesign.spacing.small)
                    )
                } else {
                    Text(
                        stringResource(Res.string.add_text_full_text_note, wordCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = ZabankhanDesign.spacing.xSmall)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .padding(bottom = ZabankhanDesign.spacing.small)
                    ) {
                        Text(
                            text = body.text.take(2_000),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                            fontFamily = editorialStyle(body.text).fontFamily
                            ),
                            modifier = Modifier.padding(ZabankhanDesign.spacing.small)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(Res.string.editor_discard_title)) },
            text = { Text(stringResource(Res.string.editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.editor_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(Res.string.editor_keep_editing))
                }
            }
        )
    }
}

/** Restores an undo/redo snapshot with the cursor at the first changed character, so long texts don't jump to the top. */
private fun historyValue(current: String, restored: String): TextFieldValue {
    val changedAt = current.commonPrefixWith(restored).length
    return TextFieldValue(restored, TextRange(changedAt))
}

