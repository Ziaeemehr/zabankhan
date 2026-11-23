package com.ziaee.zabankhan.ui

import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*

@Composable
fun ShadowingModelDialog(progress: Float?, androidAvailable: Boolean, onDismiss: () -> Unit,
    onDownload: () -> Unit, onUseAndroid: () -> Unit, modelLanguage: String, modelSizeMb: Int, systemLabel: String) {
    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        icon = { Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.shadowing_model_prompt_title, modelLanguage)) },
        text = {
            if (progress == null) Text(stringResource(Res.string.shadowing_model_prompt_body, modelSizeMb))
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.shadowing_model_downloading, (progress * 100).toInt()))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = progress == null, onClick = onDownload) { Text(stringResource(Res.string.shadowing_model_download)) }
        },
        dismissButton = {
            if (androidAvailable) TextButton(enabled = progress == null, onClick = onUseAndroid) {
                Text(systemLabel)
            }
        }
    )
}
