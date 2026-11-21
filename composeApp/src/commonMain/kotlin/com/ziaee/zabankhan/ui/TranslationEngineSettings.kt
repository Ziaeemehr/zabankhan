package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.translate.TranslationService
import com.ziaee.zabankhan.translate.llm.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Cloud portion of French Reader's TranslationEngineSettings, using common Ktor and platform secrets. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TranslationEngineSettings(container: AppContainer, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf(settings.cloudModelPrefs ?: LlmPrefs(
        engine = if (settings.llmProvider.isBlank()) TranslationEngine.GOOGLE else TranslationEngine.ONLINE,
        onlineProvider = LlmProvider.entries.firstOrNull { it.name == settings.llmProvider } ?: if (settings.llmProvider.isBlank()) LlmProvider.OPENROUTER else LlmProvider.CUSTOM,
        customBaseUrl = settings.llmProvider.takeIf { it.startsWith("https://") }.orEmpty(),
        onlineModel = settings.llmModel.ifBlank { LlmProvider.OPENROUTER.defaultModel }
    )) }
    var draft by remember(prefs.onlineProvider, prefs.customBaseUrl) { mutableStateOf("") }
    var saved by remember { mutableStateOf(LlmSecrets()) }
    var replacing by remember(prefs.onlineProvider, prefs.customBaseUrl) { mutableStateOf(false) }
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var loadedProvider by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val provider = prefs.onlineProvider
    val providerId = if (provider == LlmProvider.CUSTOM) prefs.customBaseUrl.trim().trimEnd('/') else provider.name
    val ready = loadedProvider == providerId
    val valid = ready && prefs.onlineModel.isNotBlank() && (provider != LlmProvider.CUSTOM || runCatching {
        val url = io.ktor.http.Url(providerId)
        url.protocol.name == "https" && url.host.isNotBlank() && url.user == null && url.password == null && url.parameters.isEmpty() && url.fragment.isEmpty()
    }.getOrDefault(false))
    LaunchedEffect(providerId) {
        loadedProvider = null
        saved = LlmSecrets()
        result = null
        try { saved = container.settings.cloudSecrets(providerId); loadedProvider = providerId }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { result = getString(Res.string.cloud_save_failed) }
    }
    suspend fun persist(remove: Boolean = false) {
        val token = if (remove) "" else draft.trim().ifBlank { saved.get(providerId).orEmpty() }
        container.settings.saveCloud(providerId, token, prefs)
        saved = LlmSecrets(if (token.isBlank()) emptyMap() else mapOf(providerId to token))
        draft = ""
        replacing = false
    }
    fun save(remove: Boolean = false) {
        if (!ready || busy) return
        busy = true
        scope.launch {
            try { persist(remove); result = getString(Res.string.cloud_saved) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { result = getString(Res.string.cloud_save_failed) }
            finally { busy = false }
        }
    }
    Text(stringResource(Res.string.cloud_sources), style = MaterialTheme.typography.titleMedium)
    CompactChoiceColumn(listOf(TranslationEngine.GOOGLE, TranslationEngine.ONLINE), prefs.engine, {
        stringResource(if (it == TranslationEngine.GOOGLE) Res.string.cloud_google else Res.string.llm_settings)
    }) { selected ->
        if (!busy && ready) {
            prefs = prefs.copy(engine = selected)
            if (selected == TranslationEngine.GOOGLE) save()
        }
    }
    if (prefs.engine == TranslationEngine.ONLINE) {
        Text(stringResource(Res.string.cloud_notice), style = MaterialTheme.typography.bodySmall)
        ExposedDropdownMenuBox(providerMenu, { if (!busy) providerMenu = it }) {
            OutlinedTextField(providerLabel(provider), {}, readOnly = true,
                label = { Text(stringResource(Res.string.provider)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenu) },
                modifier = Modifier.menuAnchor().fillMaxWidth())
            ExposedDropdownMenu(providerMenu, { providerMenu = false }) {
                LlmProvider.entries.forEach { item ->
                    DropdownMenuItem(text = { Text(providerLabel(item)) }, onClick = {
                        prefs = prefs.copy(onlineProvider = item, onlineModel = item.defaultModel)
                        providerMenu = false
                    })
                }
            }
        }
        if (provider == LlmProvider.CUSTOM) {
            OutlinedTextField(prefs.customBaseUrl, { prefs = prefs.copy(customBaseUrl = it) },
                label = { Text(stringResource(Res.string.base_url)) }, singleLine = true, enabled = !busy,
                textStyle = TextStyle(textDirection = TextDirection.Ltr), modifier = Modifier.fillMaxWidth())
            Text(stringResource(Res.string.cloud_https), style = MaterialTheme.typography.bodySmall)
        } else Text(provider.baseUrl, style = MaterialTheme.typography.bodySmall)
        ExposedDropdownMenuBox(modelMenu, { if (!busy) modelMenu = it }) {
            OutlinedTextField(prefs.onlineModel, { prefs = prefs.copy(onlineModel = it) },
                label = { Text(stringResource(Res.string.model)) }, singleLine = true, enabled = !busy,
                textStyle = TextStyle(textDirection = TextDirection.Ltr),
                trailingIcon = { if (provider.suggestedModels.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(modelMenu) },
                modifier = Modifier.menuAnchor().fillMaxWidth())
            if (provider.suggestedModels.isNotEmpty()) ExposedDropdownMenu(modelMenu, { modelMenu = false }) {
                provider.suggestedModels.forEach { model ->
                    DropdownMenuItem(text = { Text(model) }, onClick = { prefs = prefs.copy(onlineModel = model); modelMenu = false })
                }
            }
        }
        val last4 = saved.last4(providerId)
        if (last4 != null) {
            Text(stringResource(Res.string.cloud_saved_key, last4), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy && ready, onClick = { replacing = true }) { Text(stringResource(Res.string.cloud_replace)) }
                TextButton(enabled = !busy && ready, onClick = { save(remove = true) }) { Text(stringResource(Res.string.api_key_remove)) }
            }
        }
        if (last4 == null || replacing) OutlinedTextField(draft, { draft = it },
            label = { Text(stringResource(Res.string.api_key)) }, singleLine = true, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(), textStyle = TextStyle(textDirection = TextDirection.Ltr),
            // iOS shows no Paste menu on password fields, so offer an explicit paste button.
            trailingIcon = {
                @Suppress("DEPRECATION") val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                androidx.compose.material3.IconButton(enabled = !busy, onClick = {
                    clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { draft = it }
                }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.ContentPaste, stringResource(Res.string.add_text_paste)) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid && !busy) save() }), modifier = Modifier.fillMaxWidth())
        Text(stringResource(Res.string.api_key_storage), style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(onClick = { save() }, enabled = valid && !busy) { Text(stringResource(Res.string.save)) }
    }
    Button(enabled = ready && !busy && (prefs.engine == TranslationEngine.GOOGLE || valid), modifier = Modifier.fillMaxWidth(), onClick = {
        busy = true
        scope.launch {
            try {
                persist()
                val language = Languages.byCode(settings.learningLang.orEmpty()) ?: Languages.learnable.first()
                val sample = language.voicePreview
                val translation = if (prefs.engine == TranslationEngine.GOOGLE) {
                    TranslationService(container.http).translate(sample, settings.meaningLang, language.code)
                } else {
                    val token = saved.get(providerId)?.takeIf { it.isNotBlank() } ?: throw LlmError(LlmError.Kind.MISSING_KEY)
                    val url = if (provider == LlmProvider.CUSTOM) providerId else provider.baseUrl
                    LlmTranslator(LlmClient(container.http, url, prefs.onlineModel.trim(), token, false, provider), "$url:${prefs.onlineModel}")
                        .translateLines(listOf(sample), null, null, settings.meaningLang, language.code).single()
                }
                result = getString(Res.string.cloud_connected, translation)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                // Never show raw exceptions or server bodies that might echo credentials.
                result = getString(when ((error as? LlmError)?.kind) {
                    LlmError.Kind.MISSING_KEY -> Res.string.error_missing_key
                    LlmError.Kind.UNAUTHORIZED -> Res.string.error_key
                    LlmError.Kind.PAYMENT -> Res.string.error_payment
                    LlmError.Kind.FORBIDDEN -> Res.string.error_forbidden
                    LlmError.Kind.NOT_FOUND -> Res.string.error_model
                    LlmError.Kind.RATE_LIMIT -> Res.string.error_rate
                    LlmError.Kind.UNREACHABLE -> Res.string.error_network
                    else -> Res.string.error_response
                })
            } finally { busy = false }
        }
    }) { Text(stringResource(if (busy) Res.string.cloud_testing else Res.string.cloud_test)) }
    result?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), modifier = Modifier.padding(vertical = 8.dp)) }
    OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
        busy = true
        scope.launch {
            try { container.database.cache().clearTranslations(); result = getString(Res.string.cloud_cache_cleared) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { result = getString(Res.string.error_save) }
            finally { busy = false }
        }
    }) { Text(stringResource(Res.string.cloud_clear_cache)) }
}

@Composable
private fun providerLabel(provider: LlmProvider): String = when (provider) {
    LlmProvider.OPENROUTER -> "OpenRouter"
    LlmProvider.DEEPSEEK -> "DeepSeek"
    LlmProvider.QWEN -> "Qwen"
    LlmProvider.KIMI -> "Kimi"
    LlmProvider.CUSTOM -> stringResource(Res.string.custom)
}
