package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
        Card(onClick = onOpenGestures, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(Res.string.gesture_guide_title)) },
                supportingContent = { Text(stringResource(Res.string.help_gestures_description)) },
                leadingContent = { Icon(Icons.Default.HelpOutline, contentDescription = null) }
            )
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideScaffold(title: StringResource, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.accessibility_back))
            }
        })
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}
