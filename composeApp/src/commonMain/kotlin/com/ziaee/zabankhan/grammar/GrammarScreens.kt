@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ziaee.zabankhan.grammar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import com.ziaee.zabankhan.ui.contentStyle
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.FlowRow
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.datapack.*
import com.ziaee.zabankhan.app.LocalAppContainer
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.*
import com.ziaee.zabankhan.ui.theme.ZabankhanDesign
import com.ziaee.zabankhan.ui.theme.LocalGrammarColors
import com.ziaee.zabankhan.ui.theme.appFontFamily
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Section labels follow the note explanation language, as in the reference. */
@Composable
private fun contentString(id: StringResource): String = LocalGrammarContentLabels.current[id.key] ?: stringResource(id)

@Composable
private fun GrammarSectionHeader(id: StringResource) {
    val text = contentString(id)
    GrammarText(GrammarCell(text), rtl = isRtlText(text),
        style = MaterialTheme.typography.titleMedium, color = LocalGrammarColors.current.accent)
}

private const val VIEW_ALL = "all"
private const val VIEW_FAVOURITES = "favourites"
private const val VIEW_RECENT = "recent"

@Composable
fun GrammarListScreen(
    notes: List<GrammarNote>?, language: LanguageSpec, showPersian: Boolean, preferences: GrammarPrefs,
    onBack: () -> Unit, onOpenNote: (String) -> Unit, onOpenPosters: () -> Unit = {}
) {
    val prefs by preferences.state.collectAsState()
    val title = stringResource(Res.string.grammar_title)
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val index = remember(notes, language) { GrammarSearchIndex(notes.orEmpty(), language) }
    // Empty means every level; the levels are few, so a plain list is enough to save and restore.
    var levels by rememberSaveable { mutableStateOf(listOf<String>()) }
    val allLevels = remember(notes) { notes.orEmpty().map { it.level }.distinct().sorted() }
    // Favourites and recents are views over the same list, switched by a chip, not extra sections.
    var view by rememberSaveable { mutableStateOf(VIEW_ALL) }
    fun shown(note: GrammarNote) = (levels.isEmpty() || note.level in levels) && when (view) {
        VIEW_FAVOURITES -> note.id in prefs.favourites
        VIEW_RECENT -> prefs.recents.any { it.id == note.id }
        else -> true
    }
    // Selected chips use the grammar violet rather than the app-wide teal.
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = LocalGrammarColors.current.accentContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        selectedLeadingIconColor = LocalGrammarColors.current.accent
    )
    val results by remember(index) { derivedStateOf { index.search(query).filter { shown(it.note) } } }
    GrammarPage(topBar = {
        GrammarBackRow(onBack, title) {
            TextButton(
                onClick = onOpenPosters,
                colors = ButtonDefaults.textButtonColors(contentColor = LocalGrammarColors.current.accent)
            ) {
                Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.grammar_posters))
            }
        }
    }) { padding ->
        if (notes == null) {
            CircularProgressIndicator(Modifier.padding(padding).padding(ZabankhanDesign.spacing.small))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(ZabankhanDesign.spacing.small),
                verticalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.xSmall)
            ) {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                        placeholder = { Text(stringResource(Res.string.grammar_search_placeholder, com.ziaee.zabankhan.ui.languageName(language))) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, stringResource(Res.string.grammar_search_clear))
                                }
                            }
                        } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
                    )
                }
                item(key = "views") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = view == VIEW_FAVOURITES,
                            onClick = { view = if (view == VIEW_FAVOURITES) VIEW_ALL else VIEW_FAVOURITES },
                            label = { Text(stringResource(Res.string.grammar_favourites)) },
                            colors = chipColors,
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        FilterChip(
                            selected = view == VIEW_RECENT,
                            onClick = { view = if (view == VIEW_RECENT) VIEW_ALL else VIEW_RECENT },
                            label = { Text(stringResource(Res.string.grammar_recently_opened)) },
                            colors = chipColors,
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
                // Levels keep a row of their own so they never scroll out of sight.
                if (allLevels.size > 1) item(key = "levels") {
                    // Level names are Latin (A1, B1), so the row reads left to right in every UI language.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            allLevels.forEach { level ->
                                FilterChip(
                                    selected = level in levels,
                                    onClick = { levels = if (level in levels) levels - level else levels + level },
                                    label = { Text(level) },
                                    colors = chipColors
                                )
                            }
                        }
                    }
                }
                if (query.isBlank()) {
                    val visible = notes!!.filter(::shown)
                    if (visible.isEmpty()) item(key = "empty") {
                        Text(
                            stringResource(
                                when {
                                    notes!!.isEmpty() -> Res.string.grammar_empty
                                    view == VIEW_FAVOURITES -> Res.string.grammar_favourites_empty
                                    view == VIEW_RECENT -> Res.string.grammar_recent_empty
                                    else -> Res.string.grammar_search_no_results
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (view == VIEW_RECENT) {
                        // Most recently opened first, without group headers.
                        val byId = visible.associateBy { it.id }
                        items(prefs.recents.mapNotNull { byId[it.id] }, key = { "recent:${it.id}" }) { note ->
                            GrammarNoteCard(note, showPersian, onOpenNote, favourite = note.id in prefs.favourites)
                        }
                    } else {
                        visible.groupBy { it.group }.forEach { (group, entries) ->
                            item(key = "group-header:$group") { GrammarListHeader(stringResource(groupTitle(group))) }
                            items(entries, key = { "group:${it.id}" }) { note ->
                                GrammarNoteCard(note, showPersian, onOpenNote, favourite = note.id in prefs.favourites)
                            }
                        }
                    }
                } else {
                    item(key = "result-count") {
                        Text(
                            pluralStringResource(Res.plurals.grammar_search_results, results.size, results.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (results.isEmpty()) item(key = "no-results") {
                        Column {
                            Text(stringResource(Res.string.grammar_search_no_results))
                            TextButton(onClick = { query = "" }) { Text(stringResource(Res.string.grammar_search_clear)) }
                            if (levels.isNotEmpty()) {
                                TextButton(onClick = { levels = emptyList() }) { Text(stringResource(Res.string.grammar_filter_clear)) }
                            }
                        }
                    }
                    items(results, key = { "search:${it.note.id}" }) { result ->
                        GrammarNoteCard(result.note, showPersian, onOpenNote, result.snippet, result.note.id in prefs.favourites)
                    }
                }
            }
        }
    }
}

/** A quiet section label for the list: small accent text with a hairline, instead of a display heading. */
@Composable
private fun GrammarListHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ZabankhanDesign.spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.xSmall)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = LocalGrammarColors.current.accent
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun GrammarNoteCard(
    note: GrammarNote, showPersian: Boolean, onOpenNote: (String) -> Unit, snippet: GrammarCell? = null, favourite: Boolean = false
) {
    Card(onClick = { onOpenNote(note.id) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ZabankhanDesign.spacing.small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GrammarText(GrammarCell(note.titleLearning), rtl = isRtlText(note.titleLearning), modifier = Modifier.weight(1f))
                if (favourite) Icon(
                    Icons.Default.Star, stringResource(Res.string.grammar_favourites),
                    modifier = Modifier.size(18.dp), tint = LocalGrammarColors.current.accent
                )
            }
            val meaningTitle = if (showPersian) note.titleFa else note.titleEnglish.takeIf { it != note.titleLearning }
            if (!meaningTitle.isNullOrBlank()) GrammarText(GrammarCell(meaningTitle), rtl = grammarIsRtlText(meaningTitle), secondary = true)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { GrammarLevelBadge(note.level) }
            if (snippet != null) GrammarText(snippet, rtl = isRtlText(snippet.text), secondary = true)
        }
    }
}

private fun groupTitle(group: String): StringResource = when (group) {
    "basics" -> Res.string.grammar_group_basics
    "tenses" -> Res.string.grammar_group_tenses
    "pronouns" -> Res.string.grammar_group_pronouns
    "structures" -> Res.string.grammar_group_structures
    else -> Res.string.grammar_group_comparisons
}

@Composable
fun GrammarNoteScreen(
    id: String, notes: List<GrammarNote>?, showPersian: Boolean, preferences: GrammarPrefs,
    onBack: () -> Unit, onOpenNote: (String) -> Unit
) {
    val note = notes?.firstOrNull { it.id == id }
    val prefs by preferences.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val labels = GrammarExportLabels(
        contentString(Res.string.grammar_usage), contentString(Res.string.grammar_structure),
        contentString(Res.string.grammar_examples), contentString(Res.string.grammar_mistake),
        contentString(Res.string.grammar_more), contentString(Res.string.grammar_comparison),
        contentString(Res.string.grammar_timeline), contentString(Res.string.grammar_decision),
        contentString(Res.string.grammar_steps), contentString(Res.string.grammar_correct),
        contentString(Res.string.grammar_wrong), stringResource(Res.string.app_name)
    )
    val markdown = remember(note, labels) { note?.let { grammarNoteToMarkdown(it, labels) } }
    val export = rememberGrammarExport()
    val exportPdf = rememberGrammarPdfExport()
    LaunchedEffect(note?.id) { if (note != null) preferences.opened(note.id) }
    GrammarPage(snackbar, topBar = {
        GrammarBackRow(onBack) {
            if (note != null && markdown != null) {
                IconButton(onClick = { scope.launch { preferences.toggleFavourite(note.id) } }) {
                    val favourite = note.id in prefs.favourites
                    Icon(
                        if (favourite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        stringResource(if (favourite) Res.string.grammar_remove_favourite else Res.string.grammar_add_favourite),
                        tint = if (favourite) LocalGrammarColors.current.accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.Share, stringResource(Res.string.grammar_export))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(Res.string.grammar_share_text)) }, onClick = {
                            expanded = false; export(markdown, null, false)
                        })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.grammar_share_markdown)) }, onClick = {
                            expanded = false; export(markdown, "${note.id}.md", false)
                        })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.grammar_share_pdf)) }, onClick = {
                            expanded = false; exportPdf(grammarNoteToPdfBlocks(note, labels), "${note.id}.pdf", labels.appName)
                        })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.grammar_save_markdown)) }, onClick = {
                            expanded = false; export(markdown, "${note.id}.md", true)
                        })
                    }
                }
            }
        }
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(ZabankhanDesign.spacing.small),
            verticalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.small)
        ) {
            if (notes == null) item { CircularProgressIndicator() }
            else if (note == null) item { Text(stringResource(Res.string.grammar_not_found)) }
            else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GrammarText(GrammarCell(note.titleLearning), rtl = isRtlText(note.titleLearning), style = MaterialTheme.typography.titleLarge)
                        val meaningTitle = if (showPersian) note.titleFa else note.titleEnglish.takeIf { it != note.titleLearning }
            if (!meaningTitle.isNullOrBlank()) GrammarText(GrammarCell(meaningTitle), rtl = grammarIsRtlText(meaningTitle), secondary = true)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { GrammarLevelBadge(note.level) }
                    }
                }
                items(note.blocks) { GrammarBlockContent(it) }
                val related = note.related.mapNotNull { relatedId -> notes.firstOrNull { it.id == relatedId } }
                if (related.isNotEmpty()) item {
                    Column {
                        GrammarSectionHeader(Res.string.grammar_related)
                        related.forEach { relatedNote ->
                            val title = if (showPersian) relatedNote.titleFa else relatedNote.titleLearning
                            val direction = if (grammarIsRtlText(title)) LayoutDirection.Rtl else LayoutDirection.Ltr
                            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { onOpenNote(relatedNote.id) }
                                        .padding(horizontal = ZabankhanDesign.spacing.xSmall, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.xSmall),
                                ) {
                                    Text(
                                        title,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge.merge(contentStyle(title)).copy(
                                            fontFamily = appFontFamily(),
                                            color = MaterialTheme.colorScheme.primary,
                                            textDecoration = null,
                                        ),
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            if (relatedNote != related.last()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrammarPage(
    snackbar: SnackbarHostState? = null,
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(topBar = topBar, snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) }, content = content)
}

/** A compact fixed header matching the app's other screen top bars. */
@Composable
private fun GrammarBackRow(onBack: () -> Unit, title: String? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
        }
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun GrammarBlockContent(block: GrammarBlock) {
    val colors = LocalGrammarColors.current
    Column(verticalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.xSmall)) {
        when (block) {
            is GrammarBlock.Prose -> {
                if (block.type == "summary") {
                    Surface(color = colors.note, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(ZabankhanDesign.spacing.small)) { GrammarProse(block.markdown) }
                    }
                } else {
                    GrammarSectionHeader(if (block.type == "usage") Res.string.grammar_usage else Res.string.grammar_more)
                    GrammarProse(block.markdown)
                }
            }
            is GrammarBlock.Structure -> {
                GrammarSectionHeader(Res.string.grammar_structure)
                block.patterns.forEach { pattern ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        GrammarText(GrammarCell(pattern), rtl = isRtlText(pattern), formula = true, modifier = Modifier.padding(ZabankhanDesign.spacing.xSmall))
                    }
                }
                GrammarProse(block.text)
            }
            is GrammarBlock.Examples -> {
                GrammarSectionHeader(Res.string.grammar_examples)
                block.items.forEach { example ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(ZabankhanDesign.spacing.small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            GrammarText(example.learning, rtl = isRtlText(example.learning.text))
                            GrammarText(GrammarCell(example.fa), rtl = grammarIsRtlText(example.fa), secondary = true, markdown = true)
                        }
                    }
                }
            }
            is GrammarBlock.Mistake -> {
                GrammarSectionHeader(Res.string.grammar_mistake)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(ZabankhanDesign.spacing.small), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MistakeLine(block.wrong, false)
                        MistakeLine(block.right, true)
                        GrammarText(GrammarCell(block.why), rtl = grammarIsRtlText(block.why), markdown = true)
                    }
                }
            }
            is GrammarBlock.Infographic -> {
                GrammarSectionHeader(when (block.kind) {
                    "timeline" -> Res.string.grammar_timeline
                    "decision" -> Res.string.grammar_decision
                    "steps" -> Res.string.grammar_steps
                    else -> Res.string.grammar_comparison
                })
                GrammarTable(block)
            }
        }
    }
}

@Composable
private fun GrammarProse(paragraphs: List<GrammarParagraph>) {
    Column(verticalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.xSmall)) {
            paragraphs.forEach { paragraph ->
                CompositionLocalProvider(LocalLayoutDirection provides if (grammarIsRtlText(paragraph.text)) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Row {
                    if (paragraph.kind == "li") Text("•", modifier = Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        splitLongCode(paragraph.text).forEach { segment ->
                            if (segment.longCode) {
                                GrammarText(GrammarCell(segment.text), rtl = isRtlText(segment.text), color = LocalGrammarColors.current.target)
                            } else {
                                GrammarText(GrammarCell(segment.text), rtl = grammarIsRtlText(segment.text), markdown = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MistakeLine(text: String, correct: Boolean) {
    val color = if (correct) LocalGrammarColors.current.correct else LocalGrammarColors.current.wrong
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (correct) Icons.Default.CheckCircle else Icons.Default.Cancel, contentDescription = null, tint = color)
            Text(contentString(if (correct) Res.string.grammar_correct else Res.string.grammar_wrong), color = color, fontFamily = appFontFamily())
        }
        GrammarText(GrammarCell(text), rtl = isRtlText(text))
    }
}

@Composable
internal fun GrammarText(
    cell: GrammarCell, rtl: Boolean, modifier: Modifier = Modifier,
    secondary: Boolean = false, formula: Boolean = false, markdown: Boolean = false,
    style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = Color.Unspecified
) {
    val colors = LocalGrammarColors.current
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val text = remember(cell, rtl, markdown, colors, codeBackground) {
        grammarAnnotatedText(cell, rtl, colors.target, codeBackground, markdown)
    }
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Text(
            text = text,
            modifier = modifier.fillMaxWidth(),
            style = style.copy(
                textDirection = if (rtl) TextDirection.Rtl else TextDirection.Ltr,
                textAlign = if (rtl) TextAlign.Right else TextAlign.Left,
                fontFamily = when {
                    formula -> FontFamily.Monospace
                    rtl -> appFontFamily()
                    else -> FontFamily.SansSerif
                }
            ),
            color = when {
                color != Color.Unspecified -> color
                secondary -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun GrammarTable(table: GrammarBlock.Infographic) {
    // Persian row labels or headers put the first column on the right.
    val rtl = (table.header + table.rows.mapNotNull { it.firstOrNull() }).any { isRtlText(it.text) }
    val rows = listOf(table.header) + table.rows
    val columns = table.header.size
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth
        // A column is as wide as its longest cell, so short phrases stay on one line; only cells
        // longer than the cap wrap. Tables wider than the screen scroll sideways.
        val widestCell = 200.dp * fontScale.coerceAtLeast(1f)
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                // Measure each row first, then give every cell that row's maximum height.
                Layout(content = {
                    rows.forEachIndexed { row, cells ->
                        cells.forEach { cell ->
                            Box(Modifier.background(
                                when {
                                    row == 0 -> LocalGrammarColors.current.accentContainer
                                    row % 2 == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            ).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(8.dp)) {
                                GrammarText(cell, rtl = isRtlText(cell.text))
                            }
                        }
                    }
                }) { measurables, _ ->
                    val natural = List(columns) { column ->
                        measurables.filterIndexed { index, _ -> index % columns == column }
                            .maxOf { it.maxIntrinsicWidth(Constraints.Infinity) + 2 }
                            .coerceIn(64.dp.roundToPx(), widestCell.roundToPx())
                    }
                    val fill = available.roundToPx()
                    val columnWidths = if (natural.sum() < fill) natural.map { it * fill / natural.sum() } else natural
                    val heights = measurables.chunked(columns).map { row ->
                        row.withIndex().maxOf { (column, cell) -> cell.maxIntrinsicHeight(columnWidths[column]) }
                    }
                    val placeables = measurables.mapIndexed { index, measurable ->
                        measurable.measure(Constraints.fixed(columnWidths[index % columns], heights[index / columns]))
                    }
                    layout(columnWidths.sum(), heights.sum()) {
                        var y = 0
                        heights.forEachIndexed { row, height ->
                            var x = 0
                            repeat(columns) { column ->
                                placeables[row * columns + column].placeRelative(x, y)
                                x += columnWidths[column]
                            }
                            y += height
                        }
                    }
                }
            }
        }
    }
}

/** Exact reference metadata pill; no editorial substitution. */
@Composable
private fun GrammarLevelBadge(text: String) {
    Surface(
        modifier = Modifier.defaultMinSize(minHeight = 24.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(Modifier.padding(horizontal = ZabankhanDesign.spacing.xSmall, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}
