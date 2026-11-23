package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*

@Composable
fun HelpGuidesScreen(
    onOpenGestures: () -> Unit,
    onBack: () -> Unit
) {
    GuideScaffold(Res.string.help_guides_title, onBack) {
        EditorialCard(onClick = onOpenGestures) {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.gesture_guide_title)) },
                supportingContent = { Text(stringResource(Res.string.help_gestures_description)) },
                leadingContent = { Icon(Icons.Default.HelpOutline, contentDescription = null) }
            )
        }
        EditorialCard {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.help_optional_drive_title)) },
                supportingContent = { Text(stringResource(Res.string.help_optional_drive_body)) },
                leadingContent = { Icon(Icons.Default.HelpOutline, contentDescription = null) }
            )
        }
        EditorialCard {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.help_french_only_title)) },
                supportingContent = { Text(stringResource(Res.string.help_french_only_body)) },
                leadingContent = { Icon(Icons.Default.HelpOutline, contentDescription = null) }
            )
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideScaffold(title: StringResource, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        EditorialTopAppBar(
            title = stringResource(title),
            back = onBack,
            windowInsets = TopAppBarDefaults.windowInsets,
        )
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}
