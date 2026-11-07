package com.ziaee.zabankhan.ui

// Copied from FrenchReader ui/SettingsScreen.kt: ShadowingSettings and TtsCacheSettings.
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.*

@Composable
internal fun ShadowingSettings(container: AppContainer, settings: AppSettings, language: LanguageSpec, scope: CoroutineScope, snackbarHostState: SnackbarHostState) {
    val support = remember(language) { recognitionSupport(language) }
    val engine = settings.speechRecognitionEngine
    val manager = remember(language) { VoskModelManager(language) }
    var installed by remember(manager) { mutableStateOf(manager.isInstalled()) }
    var progress by remember(manager) { mutableStateOf<Float?>(null) }
    val androidAvailable = support.systemAvailable
    val failed = stringResource(Res.string.shadowing_model_failed)

    CompactSettingLabel(Res.string.shadowing_engine_title)
    val picker = com.ziaee.zabankhan.files.rememberFilePicker(listOf(com.ziaee.zabankhan.files.FileKind.ZIP)) { file ->
        if (file != null && progress == null) {
            progress = 0f
            scope.launch {
                try { manager.installFromZip(file.path); installed = manager.isInstalled() }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { snackbarHostState.showSnackbar(failed) }
                finally { progress = null }
            }
        }
    }
    val engines = buildList {
        if ((support.downloadableModels && language.offlineSpeechModel != null) || (!support.downloadableModels && support.onDeviceAvailable)) add("offline")
        if (androidAvailable) add("system")
    }
    CompactChoiceRow(
        engines,
        engine,
        {
            stringResource(
                if (it == "offline") {
                    if (support.downloadableModels) Res.string.shadowing_engine_vosk else Res.string.g_on_device
                } else {
                    if (support.downloadableModels) Res.string.shadowing_engine_android else Res.string.g_apple_recognizer
                }
            )
        }
    ) { kind ->
        scope.launch { container.settings.setSpeechRecognitionEngine(kind) }
    }

    if (!support.downloadableModels) {
        Text(stringResource(if (support.onDeviceAvailable) Res.string.g_on_device_available else Res.string.g_on_device_unavailable),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        return
    }
    if (language.offlineSpeechModel == null) return
    Text(stringResource(Res.string.shadowing_model_title, languageName(language)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
    val p = progress
    when {
        p != null -> {
            Text(
                stringResource(Res.string.shadowing_model_downloading, (p * 100).toInt()),
                style = MaterialTheme.typography.bodySmall
            )
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        }
        installed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.shadowing_model_installed, manager.sizeBytes() / (1024 * 1024)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                scope.launch {
                    manager.deleteAndUnload()
                    installed = false
                }
            }) {
                Text(stringResource(Res.string.shadowing_model_delete))
            }
        }
        else -> OutlinedButton(onClick = {
            progress = 0f
            scope.launch {
                val ok = try {
                    manager.download(container.http) { progress = it }
                    true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
                progress = null
                installed = manager.isInstalled()
                if (!ok) snackbarHostState.showSnackbar(failed)
            }
        }) {
            Text(stringResource(Res.string.shadowing_model_download))
        }
    }
    if (progress == null) TextButton(onClick = picker) { Text(stringResource(Res.string.g_model_install_zip)) }
}


@Composable
internal fun TtsCacheSettings(
    container: AppContainer, settings: AppSettings,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val repository = container.ttsCache
    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    val maxSizeMb = settings.ttsCacheMaxSizeMb
    val maxAgeDays = settings.ttsCacheMaxAgeDays
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(repository) {
        cacheSizeBytes = withContext(Dispatchers.IO) { repository.cacheSizeBytes() }
    }

    Text(
        stringResource(Res.string.tts_cache_current_size, cacheMegabytes(cacheSizeBytes)),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    StepperSetting(stringResource(Res.string.tts_cache_max_size), maxSizeMb, 50, 5000) {
        scope.launch { container.settings.setTtsCacheMaxSizeMb(it) }
    }
    StepperSetting(stringResource(Res.string.tts_cache_max_age), maxAgeDays, 1, 365) {
        scope.launch { container.settings.setTtsCacheMaxAgeDays(it) }
    }
    Text(
        stringResource(Res.string.tts_cache_pinned_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    OutlinedButton(onClick = { showClearConfirm = true }) {
        Text(stringResource(Res.string.tts_cache_clear))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(Res.string.tts_cache_clear_confirm_title)) },
            text = { Text(stringResource(Res.string.tts_cache_clear_confirm_message)) },
            confirmButton = {
                OutlinedButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        cacheSizeBytes = withContext(Dispatchers.IO) {
                            repository.clearCache()
                            repository.cacheSizeBytes()
                        }
                        snackbarHostState.showSnackbar(getString(Res.string.tts_cache_clear_done))
                    }
                }) { Text(stringResource(Res.string.tts_cache_clear)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(Res.string.accessibility_back))
                }
            }
        )
    }
}


@Composable private fun CompactSettingLabel(resource: StringResource) {
    Text(stringResource(resource), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
}

// Compose Resources supports %s/%d, so format the reference's %.1f argument in common Kotlin.
internal fun cacheMegabytes(bytes: Long): String {
    val tenths = kotlin.math.round(bytes / (1024.0 * 1024.0) * 10).toLong()
    return "${tenths / 10}.${tenths % 10}"
}
