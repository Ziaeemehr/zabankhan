package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.translate.llm.LlmProvider
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable fun Onboarding(vm: AppViewModel) {
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var meaning by rememberSaveable { mutableStateOf(Languages.PERSIAN.code) }
    var busy by remember { mutableStateOf(false) }
    Page {
        Heading(stringResource(Res.string.app_name))
        Text(stringResource(Res.string.welcome), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(Res.string.learning_languages))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Languages.learnable.forEach { lang -> FilterChip(selected = lang.code in selected, onClick = {
                selected = if (lang.code in selected) selected - lang.code else selected + lang.code
                if (selected.firstOrNull() == meaning) meaning = if (meaning == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code
            }, label = { Column(Modifier.padding(8.dp)) { Text(lang.nativeName); languageName(lang).takeIf { it != lang.nativeName }?.let { Text(it) } } }) }
        }
        val meanings = listOf(Languages.PERSIAN, Languages.ENGLISH).filter { it.code != selected.firstOrNull() }
        Choice(stringResource(Res.string.meaning_language), meaning, meanings.map { it.code to languageName(it) }) { meaning = it }
        Button(enabled = selected.isNotEmpty() && !busy, onClick = {
            busy = true
            vm.action {
                try {
                    vm.container.settings.setMeaningLang(meaning)
                    selected.reversed().forEach { vm.container.settings.addLearningLang(it) }
                } finally { busy = false }
            }
        }) { Text(stringResource(Res.string.continue_action)) }
        if (vm.failed) Text(stringResource(Res.string.error_save), color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(vm: AppViewModel, settings: AppSettings, progress: () -> Unit = {}, grammar: () -> Unit = {}, exams: () -> Unit = {}) {
    val repository = vm.container.settings
    val tts = remember { createTts() }
    val speaking by tts.isSpeaking.collectAsState()
    var voices by remember { mutableStateOf(emptyMap<String, List<TtsVoice>>()) }
    LaunchedEffect(tts, speaking, settings.voiceEngine) {
        repeat(10) {
            voices = Languages.learnable.associate { it.code to tts.voices(it.code) }
            if (voices.values.any { it.isNotEmpty() }) return@LaunchedEffect
            kotlinx.coroutines.delay(300)
        }
    }
    val uri = LocalUriHandler.current
    var provider by remember(settings.llmProvider) { mutableStateOf(settings.llmProvider) }
    var apiKey by remember(settings.llmApiKey) { mutableStateOf(settings.llmApiKey) }
    var model by remember(settings.llmModel) { mutableStateOf(settings.llmModel) }
    var customUrl by remember(settings.llmProvider) { mutableStateOf(settings.llmProvider.takeIf { it.startsWith("https://") }.orEmpty()) }
    Page {
        Heading(stringResource(Res.string.nav_settings))
        com.ziaee.zabankhan.backup.BackupSettings(vm.container)
        com.ziaee.zabankhan.backup.FrenchReaderImportSettings(vm.container)
        TextButton(onClick = progress) { Text(stringResource(Res.string.h2_progress)) }
        Text("${stringResource(Res.string.h2_daily_goal)}: ${settings.dailyGoalMinutes}")
        Slider(settings.dailyGoalMinutes.toFloat(), { vm.action { repository.setDailyGoalMinutes(it.toInt()) } }, valueRange = 1f..120f, steps = 118)
        if (com.ziaee.zabankhan.datapack.LanguageDataPacks.available(settings.learningLang.orEmpty())) {
            TextButton(onClick = grammar) { Text(stringResource(Res.string.pack_grammar)) }
            TextButton(onClick = exams) { Text(stringResource(Res.string.pack_tcf)) }
        }
        com.ziaee.zabankhan.reminder.ReviewReminderSettings(vm, settings)
        Text(stringResource(Res.string.learning_languages), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Languages.learnable.forEach { lang ->
                val selected = lang.code in settings.learningLangs
                FilterChip(selected, onClick = { vm.action {
                    if (selected && settings.learningLangs.size > 1) repository.removeLearningLang(lang.code)
                    else if (!selected) repository.addLearningLang(lang.code)
                    val active = repository.current().learningLang
                    if (active == settings.meaningLang) repository.setMeaningLang(if (active == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code)
                } }, enabled = !selected || settings.learningLangs.size > 1, label = { Text(languageName(lang)) })
            }
        }
        Choice(stringResource(Res.string.meaning_language), settings.meaningLang,
            listOf(Languages.PERSIAN, Languages.ENGLISH).filter { it.code != settings.learningLang }.map { it.code to languageName(it) }) {
            vm.action { repository.setMeaningLang(it) }
        }
        Choice(stringResource(Res.string.theme), settings.themeMode,
            listOf("SYSTEM" to stringResource(Res.string.system), "LIGHT" to stringResource(Res.string.light), "DARK" to stringResource(Res.string.dark))) {
            vm.action { repository.setThemeMode(it) }
        }
        Choice(stringResource(Res.string.edge_engine), settings.voiceEngine,
            listOf("online" to stringResource(Res.string.edge_online), "device" to stringResource(Res.string.edge_device))) {
            vm.action { repository.setVoiceEngine(it) }
        }
        Choice(stringResource(Res.string.speech_rate), settings.speechRate, listOf(.75f, 1f, 1.25f).map { it to "${it}×" }) { vm.action { repository.setSpeechRate(it) } }
        Row { Checkbox(settings.autoPronounceCards, { vm.action { repository.setAutoPronounceCards(it) } }); Text(stringResource(Res.string.speech_auto_cards)) }
        Languages.learnable.filter { it.code in settings.learningLangs }.forEach { lang ->
            Choice("${languageName(lang)} — ${stringResource(Res.string.speech_voice)}", settings.ttsVoices[lang.code].orEmpty(),
                listOf("" to stringResource(Res.string.speech_default)) + voices[lang.code].orEmpty().map { it.id to it.name }) { id -> vm.action { repository.setTtsVoice(lang.code, id.ifBlank { null }) } }
            TextButton(onClick = { tts.pronounce(lang.voicePreview, lang.code, settings) }) { Text(stringResource(Res.string.edge_preview)) }
        }
        Text("${stringResource(Res.string.font_size)}: ${settings.readerFontSize}")
        Slider(settings.readerFontSize.toFloat(), { vm.action { repository.setReaderFontSize(it.toInt()) } }, valueRange = 12f..40f, steps = 27)
        Text("${stringResource(Res.string.daily_new)}: ${settings.dailyNewCards}")
        Slider(settings.dailyNewCards.toFloat(), { vm.action { repository.setDailyNewCards(it.toInt()) } }, valueRange = 0f..100f, steps = 99)
        HorizontalDivider()
        Text(stringResource(Res.string.llm_settings), style = MaterialTheme.typography.titleMedium)
        Choice(stringResource(Res.string.provider), provider,
            listOf("" to stringResource(Res.string.disabled)) + LlmProvider.entries.map {
                it.name to if (it == LlmProvider.CUSTOM) stringResource(Res.string.custom) else it.name
            } + if (provider.startsWith("https://")) listOf(provider to stringResource(Res.string.custom)) else emptyList()) {
            provider = it
            model = LlmProvider.entries.firstOrNull { p -> p.name == it }?.defaultModel.orEmpty()
        }
        val preset = LlmProvider.entries.firstOrNull { it.name == provider }
        if (provider.isNotEmpty()) {
            if (provider == LlmProvider.CUSTOM.name || provider.startsWith("https://")) {
                OutlinedTextField(customUrl, { customUrl = it }, label = { Text(stringResource(Res.string.base_url)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            } else Text(preset?.baseUrl.orEmpty(), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(apiKey, { apiKey = it }, label = { Text(stringResource(Res.string.api_key)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(model, { model = it }, label = { Text(stringResource(Res.string.model)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        Button(enabled = (provider != LlmProvider.CUSTOM.name && !provider.startsWith("https://")) || customUrl.startsWith("https://"), onClick = { vm.action {
            // Shortcut: DataStore stores the key for Phase 1. Move to Keychain / EncryptedPrefs later.
            repository.setLlm(if (provider == LlmProvider.CUSTOM.name || provider.startsWith("https://")) customUrl.trim().trimEnd('/') else provider, apiKey.trim(), model.trim())
        } }) { Text(stringResource(Res.string.save)) }
        HorizontalDivider()
        Text(stringResource(Res.string.about), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.version))
        Text(stringResource(Res.string.edge_privacy))
        com.ziaee.zabankhan.datapack.DataPackCredits()
        TextButton(onClick = { uri.openUri("https://github.com/Ziaeemehr/zabankhan") }) { Text(stringResource(Res.string.github)) }
    }
}
