package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.ui.languageName

private enum class GestureKind(val icon: ImageVector, val label: StringResource) {
    TAP(Icons.Outlined.TouchApp, Res.string.gesture_kind_tap),
    LONG_PRESS(Icons.Outlined.PanTool, Res.string.gesture_kind_long_press),
    DRAG(Icons.Outlined.Swipe, Res.string.gesture_kind_drag),
    VERTICAL_SWIPE(Icons.Outlined.SwipeVertical, Res.string.gesture_kind_vertical_swipe),
    HORIZONTAL_SWIPE(Icons.Outlined.SwipeRight, Res.string.gesture_kind_horizontal_swipe)
}

private data class GestureRow(val kind: GestureKind, val text: StringResource)

private data class GestureSection(val key: String, val title: StringResource, val rows: List<GestureRow>)

@Composable
private fun GestureGuideRow(kind: GestureKind, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(kind.icon, stringResource(kind.label), modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GestureGuideScreen(lang: LanguageSpec, section: String = "reading", onBack: () -> Unit) {
    val sections = listOf(
        GestureSection("reading", Res.string.gesture_reading_title, listOf(
            GestureRow(GestureKind.TAP, Res.string.gesture_reading_row_1),
            GestureRow(GestureKind.LONG_PRESS, Res.string.gesture_reading_row_2),
            GestureRow(GestureKind.TAP, Res.string.gesture_reading_row_4)
        ))
    )
    val sectionIndex = sections.indexOfFirst { it.key == section }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = if (sectionIndex >= 0) sectionIndex + 1 else 0)
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(Res.string.gesture_guide_title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.accessibility_back))
            }
        })
    }) { padding ->
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(key = "legend") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GestureKind.entries.forEach { kind ->
                        GestureGuideRow(kind, stringResource(kind.label))
                    }
                }
            }
            sections.forEach { (key, title, rows) ->
                item(key = key) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                        rows.forEach { row ->
                            GestureGuideRow(row.kind, if (row.text == Res.string.gesture_reading_row_1) stringResource(Res.string.reader_word_dictionary, languageName(lang)) else stringResource(row.text))
                        }
                    }
                }
            }
            item { com.ziaee.zabankhan.ui.GuideFileActions("gestures.md") }
        }
    }
}
