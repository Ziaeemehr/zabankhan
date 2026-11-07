package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.grammar.rememberGrammarExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuideFileActions(assetName: String) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val error = stringResource(Res.string.guide_file_error)
    val chooser = stringResource(Res.string.guide_share)
    val export = rememberGrammarExport()
    val url = "https://github.com/Ziaeemehr/zabankhan/blob/main/composeApp/src/commonMain/composeResources/files/guides/$assetName"
    var reading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun perform(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar(error)
            } finally {
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.guide_ai_explanation), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { reading = true }) { Text(stringResource(Res.string.guide_read)) }
            OutlinedButton(enabled = !busy, onClick = { perform {
                uriHandler.openUri(url)
            } }) { Text(stringResource(Res.string.guide_github)) }
            OutlinedButton(enabled = !busy, onClick = { perform { export(readGuide(assetName), assetName, true) } }) {
                Text(stringResource(Res.string.guide_save))
            }
            OutlinedButton(enabled = !busy, onClick = { perform {
                export(readGuide(assetName), assetName, false)
            } }) { Text(chooser) }
        }
        SnackbarHost(snackbar)
    }
    if (reading) GuideAssetDialog(assetName) { reading = false }
}

@Composable
private fun GuideAssetDialog(assetName: String, onDismiss: () -> Unit) {
    var content by remember(assetName) { mutableStateOf<String?>(null) }
    var failed by remember(assetName) { mutableStateOf(false) }
    LaunchedEffect(assetName) {
        try {
            content = readGuide(assetName)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
                Text(stringResource(Res.string.guide_read), style = MaterialTheme.typography.titleLarge)
                SelectionContainer(Modifier.weight(1f)) {
                    // Let each paragraph follow its content, including the Persian guide section.
                    Text(content ?: if (failed) stringResource(Res.string.guide_file_error) else "",
                        style = LocalTextStyle.current.copy(
                            textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start),
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.accessibility_back)) }
            }
        }
    }
}

private suspend fun readGuide(assetName: String): String {
    require(assetName == "gestures.md")
    return Res.readBytes("files/guides/$assetName").decodeToString()
}
