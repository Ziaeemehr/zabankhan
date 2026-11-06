package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import com.ziaee.zabankhan.ui.theme.*
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
    var apiKey by remember(settings.llmApiKey) { mutableStateOf("") }
    var model by remember(settings.llmModel) { mutableStateOf(settings.llmModel) }
    var customUrl by remember(settings.llmProvider) { mutableStateOf(settings.llmProvider.takeIf { it.startsWith("https://") }.orEmpty()) }
    Page {
        EditorialTopAppBar(stringResource(Res.string.nav_settings))
        SettingsGroup(stringResource(Res.string.ed_settings_general)) {
            Text(stringResource(Res.string.learning_languages), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Languages.learnable.forEach { lang ->
                    val selected = lang.code in settings.learningLangs
                    FilterChip(selected, onClick = { if (!selected || settings.learningLangs.size > 1) vm.action {
                        if (selected && settings.learningLangs.size > 1) repository.removeLearningLang(lang.code)
                        else if (!selected) repository.addLearningLang(lang.code)
                        val active = repository.current().learningLang
                        if (active == settings.meaningLang) repository.setMeaningLang(if (active == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code)
                    } }, label = { Text(languageName(lang)) },
                        leadingIcon = if (selected) ({ Icon(Icons.Default.Check, stringResource(Res.string.learning_languages), Modifier.size(18.dp)) }) else null,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer))
                }
            }
            if (com.ziaee.zabankhan.datapack.LanguageDataPacks.available(settings.learningLang.orEmpty())) {
                EditorialListRow(stringResource(Res.string.pack_grammar), Icons.Default.MenuBook, grammar)
                EditorialListRow(stringResource(Res.string.pack_tcf), Icons.Default.School, exams)
            }
        }
        SettingsGroup(stringResource(Res.string.ed_settings_appearance)) {
            Choice(stringResource(Res.string.theme), settings.themeMode,
                listOf("SYSTEM" to stringResource(Res.string.system), "LIGHT" to stringResource(Res.string.light), "DARK" to stringResource(Res.string.dark))) {
                vm.action { repository.setThemeMode(it) }
            }
        }
        SettingsGroup(stringResource(Res.string.ed_settings_reading)) {
            Text("${stringResource(Res.string.font_size)}: ${settings.readerFontSize}")
            Slider(settings.readerFontSize.toFloat(), { vm.action { repository.setReaderFontSize(it.toInt()) } }, valueRange = 12f..40f, steps = 27, colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
            val preview = Languages.byCode(settings.learningLang.orEmpty())?.voicePreview.orEmpty()
            if (preview.isNotBlank()) Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                Text(preview, Modifier.fillMaxWidth().padding(16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge.merge(contentStyle(preview)).copy(fontSize = settings.readerFontSize.sp, lineHeight = (settings.readerFontSize * 1.7).sp))
            }
        }
        SettingsGroup(stringResource(Res.string.ed_settings_speech)) {
            Choice(stringResource(Res.string.edge_engine), settings.voiceEngine,
                listOf("online" to stringResource(Res.string.edge_online), "device" to stringResource(Res.string.edge_device))) {
                vm.action { repository.setVoiceEngine(it) }
            }
            Choice(stringResource(Res.string.speech_rate), settings.speechRate, listOf(.75f, 1f, 1.25f).map { it to "${it}×" }) { vm.action { repository.setSpeechRate(it) } }
            Choice(stringResource(Res.string.speech_repeat), settings.speechRepeat, (1..3).map { it to "${it}×" }) { vm.action { repository.setSpeechRepeat(it) } }
            Languages.learnable.filter { it.code in settings.learningLangs }.forEach { lang ->
                Choice("${languageName(lang)} — ${stringResource(Res.string.speech_voice)}", settings.ttsVoices[lang.code].orEmpty(),
                    listOf("" to stringResource(Res.string.speech_default)) + voices[lang.code].orEmpty().map { it.id to it.name }) { id -> vm.action { repository.setTtsVoice(lang.code, id.ifBlank { null }) } }
                TextButton(onClick = { tts.pronounce(lang.voicePreview, lang.code, settings) }) { Text(stringResource(Res.string.edge_preview)) }
            }
        }
        SettingsGroup(stringResource(Res.string.ed_settings_dictionary)) {
            Choice(stringResource(Res.string.meaning_language), settings.meaningLang,
                listOf(Languages.PERSIAN, Languages.ENGLISH).filter { it.code != settings.learningLang }.map { it.code to languageName(it) }) {
                vm.action { repository.setMeaningLang(it) }
            }
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
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text(stringResource(Res.string.api_key)) }, placeholder = { if (settings.llmApiKey.isNotBlank()) Text(stringResource(Res.string.api_key_saved)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(model, { model = it }, label = { Text(stringResource(Res.string.model)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            EditorialPrimaryButton(text = stringResource(Res.string.save), enabled = (provider != LlmProvider.CUSTOM.name && !provider.startsWith("https://")) || customUrl.startsWith("https://"), onClick = { vm.action {
                repository.setLlm(if (provider == LlmProvider.CUSTOM.name || provider.startsWith("https://")) customUrl.trim().trimEnd('/') else provider, apiKey.trim().ifBlank { settings.llmApiKey }, model.trim())
                apiKey = ""
            } })
            if (settings.llmApiKey.isNotBlank()) TextButton(onClick = { vm.action {
                repository.setLlm(settings.llmProvider, "", settings.llmModel)
                apiKey = ""
            } }) { Text(stringResource(Res.string.api_key_remove)) }
            Text(stringResource(Res.string.api_key_storage), style = MaterialTheme.typography.bodySmall)
        }
        SettingsGroup(stringResource(Res.string.ed_settings_review)) {
            TextButton(onClick = progress) { Text(stringResource(Res.string.h2_progress)) }
            Text("${stringResource(Res.string.h2_daily_goal)}: ${settings.dailyGoalMinutes}")
            Slider(settings.dailyGoalMinutes.toFloat(), { vm.action { repository.setDailyGoalMinutes(it.toInt()) } }, valueRange = 1f..120f, steps = 118, colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
            Text("${stringResource(Res.string.daily_new)}: ${settings.dailyNewCards}")
            Slider(settings.dailyNewCards.toFloat(), { vm.action { repository.setDailyNewCards(it.toInt()) } }, valueRange = 0f..100f, steps = 99, colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
            val autoPronounceLabel = stringResource(Res.string.speech_auto_cards)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(autoPronounceLabel, Modifier.weight(1f))
                Switch(settings.autoPronounceCards, { vm.action { repository.setAutoPronounceCards(it) } }, modifier = Modifier.semantics { contentDescription = autoPronounceLabel })
            }
            com.ziaee.zabankhan.reminder.ReviewReminderSettings(vm, settings)
        }
        SettingsGroup(stringResource(Res.string.ed_settings_backup)) {
            com.ziaee.zabankhan.backup.BackupSettings(vm.container)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            com.ziaee.zabankhan.backup.FrenchReaderImportSettings(vm.container)
        }
        SettingsGroup(stringResource(Res.string.about)) {
            Text(stringResource(Res.string.version))
            Text(stringResource(Res.string.edge_privacy))
            com.ziaee.zabankhan.datapack.DataPackCredits()
            TextButton(onClick = { uri.openUri("https://github.com/Ziaeemehr/zabankhan") }) { Text(stringResource(Res.string.github)) }
        }
    }
}

@Composable private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    EditorialSectionHeader(title)
    EditorialCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
