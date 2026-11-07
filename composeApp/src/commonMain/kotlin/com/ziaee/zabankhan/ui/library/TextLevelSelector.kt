package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.StringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextLevelSelector(level: String?, onSelect: (String?) -> Unit) {
    Column {
        Text(stringResource(Res.string.text_level_label), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (TEXT_LEVELS + listOf(null)).forEach { option ->
                FilterChip(
                    selected = option == level,
                    onClick = { onSelect(option) },
                    label = { Text(option ?: stringResource(Res.string.text_level_unspecified)) }
                )
            }
        }
    }
}

@Composable
fun SetTextLevelDialog(level: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.text_level_set)) },
        text = { TextLevelSelector(level, onSelect) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } }
    )
}
