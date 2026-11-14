package com.ziaee.zabankhan.speech.local

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.ziaee.zabankhan.net.*
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.speech.SpeechAudioStatus
import com.ziaee.zabankhan.speech.createTts
import com.ziaee.zabankhan.resources.Res
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LocalTtsSettings(language: LanguageSpec) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { LocalTtsPrefs }
    val serverPrefs = remember { LocalServerPrefs(context) }
    val engine = remember { LocalTts(context, scope) }
    var url by remember { mutableStateOf(prefs.serverUrl(context)) }
    var token by remember { mutableStateOf(prefs.token(context)) }
    var usePaired by remember { mutableStateOf(prefs.usePaired(context)) }
    var paired by remember { mutableStateOf(serverPrefs.paired) }
    var speakers by remember { mutableStateOf(emptyList<String>()) }
    var speaker by remember { mutableStateOf(prefs.speaker(context)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var previewSpeaker by remember { mutableStateOf<String?>(null) }
    val previewStatus by engine.audioStatus.collectAsState()
    val discoverLabel = stringResource(Res.string.local_tts_find)
    val searchingLabel = stringResource(Res.string.local_tts_searching)
    val noComputerLabel = stringResource(Res.string.local_tts_not_found)
    val pairLabel = stringResource(Res.string.local_tts_pair)
    val forgetLabel = stringResource(Res.string.local_tts_forget)
    val refreshLabel = stringResource(Res.string.local_tts_refresh_speakers)
    val failedLabel = stringResource(Res.string.local_tts_failed)
    fun stopPreview() {
        engine.stop()
        previewSpeaker = null
    }
    fun togglePreview(name: String) {
        val stopping = previewSpeaker == name
        stopPreview()
        if (stopping) return
        createTts().stop()
        status = ""
        previewSpeaker = name
        engine.setVoice(name)
        engine.speak(language.voicePreview, language.code, 1f,
            onError = { previewSpeaker = null; status = failedLabel },
            onDone = { previewSpeaker = null })
    }
    DisposableEffect(url, token, usePaired, paired, language.code) {
        onDispose { stopPreview() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (paired != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.local_tts_paired, paired!!.name), modifier = Modifier.weight(1f))
                TextButton(onClick = { serverPrefs.forget(); paired = null; usePaired = false; prefs.setUsePaired(context, false) }) { Text(forgetLabel) }
            }
        } else {
            var found by remember { mutableStateOf(emptyList<FoundComputer>()) }
            Button(enabled = !busy, onClick = {
                busy = true; status = searchingLabel
                scope.launch {
                    try { found = withContext(Dispatchers.IO) { LocalServerFinder.discover(context) }; if (found.isEmpty()) status = noComputerLabel else status = "" }
                    catch (_: Exception) { status = failedLabel }
                    finally { busy = false }
                }
            }) { Text(if (busy) searchingLabel else discoverLabel) }
            found.forEach { computer ->
                TextButton(enabled = !busy, onClick = {
                    serverPrefs.pair(computer); paired = serverPrefs.paired; usePaired = true; prefs.setUsePaired(context, true); status = pairLabel
                }) { Text("$pairLabel: ${computer.name}") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.local_tts_use_paired), modifier = Modifier.weight(1f))
            Switch(usePaired, { usePaired = it; prefs.setUsePaired(context, it) }, enabled = paired != null)
        }
        OutlinedTextField(url, { url = it; prefs.setServerUrl(context, it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.local_tts_server_url)) }, singleLine = true)
        OutlinedTextField(token, { token = it; prefs.setToken(context, it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.local_tts_token)) }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.local_tts_speaker), modifier = Modifier.weight(1f))
            var expanded by remember { mutableStateOf(false) }
            TextButton(onClick = { expanded = true }) { Text(speaker.ifBlank { stringResource(Res.string.local_tts_choose_speaker) }) }
            DropdownMenu(expanded, { expanded = false; stopPreview() }) {
                speakers.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { stopPreview(); speaker = name; prefs.setSpeaker(context, name); expanded = false },
                        trailingIcon = {
                            val active = previewSpeaker == name
                            val previewLabel = stringResource(if (active) Res.string.local_tts_preview_stop else Res.string.local_tts_preview_play, name)
                            IconButton(onClick = { togglePreview(name) }, enabled = language.voicePreview.isNotBlank(),
                                modifier = Modifier.semantics { contentDescription = previewLabel }) {
                                if (active && previewStatus == SpeechAudioStatus.PREPARING) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(if (active) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                                }
                            }
                        },
                    )
                }
            }
        }
        Button(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { speakers = withContext(Dispatchers.IO) { engine.speakers() }; engine.updateSpeakers(speakers); status = "${speakers.size}" }
                catch (_: Exception) { status = failedLabel }
                finally { busy = false }
            }
        }) { Text(refreshLabel) }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}
