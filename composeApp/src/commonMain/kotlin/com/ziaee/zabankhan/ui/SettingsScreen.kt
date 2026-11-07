package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.luminance
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, settings: AppSettings, progress: () -> Unit = {}, grammar: () -> Unit = {}, exams: () -> Unit = {}) {
    val repository = vm.container.settings
    val speechSettingsScope = rememberCoroutineScope()
    val speechSettingsSnackbar = remember { SnackbarHostState() }
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
    val tabTitles = listOf(
        Res.string.ed_settings_general,
        Res.string.ed_settings_appearance,
        Res.string.ed_settings_reading,
        Res.string.ed_settings_speech,
        Res.string.ed_settings_dictionary,
        Res.string.ed_settings_review,
        Res.string.ed_settings_backup,
        Res.string.about
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(-1) }
    BackHandler(enabled = selectedTab >= 0) { selectedTab = -1 }
    Scaffold(
        snackbarHost = { SnackbarHost(speechSettingsSnackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (selectedTab < 0) Res.string.nav_settings else tabTitles[selectedTab])) },
                navigationIcon = {
                    if (selectedTab >= 0) IconButton(onClick = { selectedTab = -1 }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.accessibility_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            if (selectedTab < 0) {
                SettingsTabRow(tabTitles = tabTitles, selectedTab = selectedTab, onSelect = { selectedTab = it })
            } else {
                key(selectedTab) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {

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
                                OutlinedButton(onClick = grammar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.pack_grammar)) }
                                if (com.ziaee.zabankhan.datapack.LanguageDataPacks.available(settings.learningLang.orEmpty())) {
                                    OutlinedButton(onClick = exams, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.pack_tcf)) }
                                }

                            }
                            1 -> {

                                SettingsChoice(stringResource(Res.string.theme), settings.themeMode,
                                    listOf("SYSTEM" to stringResource(Res.string.system), "LIGHT" to stringResource(Res.string.light), "DARK" to stringResource(Res.string.dark))) {
                                    vm.action { repository.setThemeMode(it) }
                                }

                            }
                            2 -> {

                                Text(stringResource(Res.string.reading_background_title), style = MaterialTheme.typography.labelMedium)
                                ReadingColorGrid(ReadingBackground.entries, settings.readingBackground, ::backgroundSwatch,
                                    { stringResource(it.labelResource()) }) { vm.action { repository.setReadingBackground(it.name) } }
                                Text(stringResource(Res.string.sync_highlight_color), style = MaterialTheme.typography.labelMedium)
                                ReadingColorGrid(HighlightColor.entries, settings.readingHighlightColor,
                                    { highlightSwatch(it, settings.readingBackground in listOf("DARK", "BLACK")) },
                                    { stringResource(it.labelResource()) }) { vm.action { repository.setReadingHighlightColor(it.name) } }
                                SettingsChoice(stringResource(Res.string.reading_font_scale_title), settings.readerFontScale,
                                    FontScale.entries.map { it.name to stringResource(it.labelResource()) }) { vm.action { repository.setReaderFontScale(it) } }

                            }
                            3 -> {

                                SettingsChoice(stringResource(Res.string.edge_engine), settings.voiceEngine,
                                    listOf("online" to stringResource(Res.string.edge_online), "device" to stringResource(Res.string.edge_device))) {
                                    vm.action { repository.setVoiceEngine(it) }
                                }
                                SettingsChoice(stringResource(Res.string.speech_rate), settings.speechRate, listOf(.75f, 1f, 1.25f).map { it to "${it}×" }) { vm.action { repository.setSpeechRate(it) } }
                                SettingsChoice(stringResource(Res.string.speech_repeat), settings.speechRepeat, (1..3).map { it to "${it}×" }) { vm.action { repository.setSpeechRepeat(it) } }
                                Languages.learnable.filter { it.code in settings.learningLangs }.forEach { lang ->
                                    Choice("${languageName(lang)} — ${stringResource(Res.string.speech_voice)}", settings.ttsVoices[lang.code].orEmpty(),
                                        listOf("" to stringResource(Res.string.speech_default)) + voices[lang.code].orEmpty().map { it.id to it.name }) { id -> vm.action { repository.setTtsVoice(lang.code, id.ifBlank { null }) } }
                                    TextButton(onClick = { tts.pronounce(lang.voicePreview, lang.code, settings) }) { Text(stringResource(Res.string.edge_preview)) }
                                }
                                Languages.learnable.filter { it.code in settings.learningLangs }.forEach { lang ->
                                    key(lang.code) {
                                        Text(languageName(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                                        ShadowingSettings(vm.container, settings, lang, speechSettingsScope, speechSettingsSnackbar)
                                    }
                                }
                                Text(stringResource(Res.string.settings_section_tts_cache), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                                TtsCacheSettings(vm.container, settings, speechSettingsScope, speechSettingsSnackbar)

                            }
                            4 -> {

                                SettingsChoice(stringResource(Res.string.meaning_language), settings.meaningLang,
                                    listOf(Languages.PERSIAN, Languages.ENGLISH).filter { it.code != settings.learningLang }.map { it.code to languageName(it) }) {
                                    vm.action { repository.setMeaningLang(it) }
                                }
                                Text(stringResource(Res.string.llm_settings), style = MaterialTheme.typography.titleMedium)
                                SettingsChoice(stringResource(Res.string.provider), provider,
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
                                Button( enabled = (provider != LlmProvider.CUSTOM.name && !provider.startsWith("https://")) || customUrl.startsWith("https://"), onClick = { vm.action {
                                    repository.setLlm(if (provider == LlmProvider.CUSTOM.name || provider.startsWith("https://")) customUrl.trim().trimEnd('/') else provider, apiKey.trim().ifBlank { settings.llmApiKey }, model.trim())
                                    apiKey = ""
                                } }) { Text(stringResource(Res.string.save)) }
                                if (settings.llmApiKey.isNotBlank()) TextButton(onClick = { vm.action {
                                    repository.setLlm(settings.llmProvider, "", settings.llmModel)
                                    apiKey = ""
                                } }) { Text(stringResource(Res.string.api_key_remove)) }
                                Text(stringResource(Res.string.api_key_storage), style = MaterialTheme.typography.bodySmall)

                            }
                            5 -> {

                                TextButton(onClick = progress) { Text(stringResource(Res.string.h2_progress)) }
                                StepperSetting(stringResource(Res.string.h2_daily_goal), settings.dailyGoalMinutes, 1, 120) {
                                    vm.action { repository.setDailyGoalMinutes(it) }
                                }
                                StepperSetting(stringResource(Res.string.daily_new), settings.dailyNewCards, 0, 100) {
                                    vm.action { repository.setDailyNewCards(it) }
                                }
                                Text(stringResource(Res.string.review_audio_autoplay), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
                                CompactChoiceRow(com.ziaee.zabankhan.data.AudioAutoplay.entries, settings.reviewAudioAutoplay, {
                                    stringResource(when (it) {
                                        com.ziaee.zabankhan.data.AudioAutoplay.OFF -> Res.string.review_autoplay_off
                                        com.ziaee.zabankhan.data.AudioAutoplay.FRONT -> Res.string.review_autoplay_front
                                        com.ziaee.zabankhan.data.AudioAutoplay.BACK -> Res.string.review_autoplay_back
                                        com.ziaee.zabankhan.data.AudioAutoplay.BOTH -> Res.string.review_autoplay_both
                                    })
                                }) { vm.action { repository.setReviewAudioAutoplay(it) } }
                                com.ziaee.zabankhan.reminder.ReviewReminderSettings(vm, settings)

                            }
                            6 -> {

                                com.ziaee.zabankhan.backup.BackupSettings(vm.container)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                com.ziaee.zabankhan.backup.FrenchReaderImportSettings(vm.container)

                            }
                            7 -> {

                                Text(stringResource(Res.string.version))
                                Text(stringResource(Res.string.edge_privacy))
                                com.ziaee.zabankhan.datapack.DataPackCredits()
                                TextButton(onClick = { uri.openUri("https://github.com/Ziaeemehr/zabankhan") }) { Text(stringResource(Res.string.github)) }

                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabRow(tabTitles: List<StringResource>, selectedTab: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
        tabTitles.chunked(3).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { columnIndex, resource ->
                    val index = rowIndex * 3 + columnIndex
                    val selected = selectedTab == index
                    val background by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                        label = "settingsTabBackground"
                    )
                    val contentColor by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "settingsTabContent"
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(background)
                            .border(
                                width = if (selected) 0.dp else 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onSelect(index) }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(resource),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun StepperSetting(label: String, value: Int, minimum: Int, maximum: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(settingsStep(value, -1, minimum, maximum)) }, enabled = value > minimum) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(value.toString(), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { onChange(settingsStep(value, 1, minimum, maximum)) }, enabled = value < maximum) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> CompactChoiceRow(
    entries: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit
) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { entry ->
            FilterChip(
                selected = entry == selected,
                onClick = { onSelect(entry) },
                label = { Text(label(entry), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun <T> SettingsChoice(label: String, value: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
    CompactChoiceRow(options.map { it.first }, value, { key -> options.first { it.first == key }.second }, onSelect)
}

private fun FontScale.labelResource(): org.jetbrains.compose.resources.StringResource = when (this) {
    FontScale.SMALL -> Res.string.font_scale_small
    FontScale.MEDIUM -> Res.string.font_scale_medium
    FontScale.LARGE -> Res.string.font_scale_large
    FontScale.XLARGE -> Res.string.font_scale_xlarge
}
private fun ReadingBackground.labelResource(): org.jetbrains.compose.resources.StringResource = when (this) {
    ReadingBackground.WHITE -> Res.string.reading_background_white
    ReadingBackground.SEPIA -> Res.string.reading_background_sepia
    ReadingBackground.PAPER -> Res.string.reading_background_paper
    ReadingBackground.SAND -> Res.string.reading_background_sand
    ReadingBackground.GRAY -> Res.string.reading_background_gray
    ReadingBackground.MINT -> Res.string.reading_background_mint
    ReadingBackground.SAGE -> Res.string.reading_background_sage
    ReadingBackground.BLUE_TINT -> Res.string.reading_background_blue
    ReadingBackground.ROSE -> Res.string.reading_background_rose
    ReadingBackground.DARK -> Res.string.reading_background_dark
    ReadingBackground.BLACK -> Res.string.reading_background_black
}
private fun HighlightColor.labelResource(): org.jetbrains.compose.resources.StringResource = when (this) {
    HighlightColor.YELLOW -> Res.string.highlight_yellow
    HighlightColor.GREEN -> Res.string.highlight_green
    HighlightColor.BLUE -> Res.string.highlight_blue
    HighlightColor.PINK -> Res.string.highlight_pink
    HighlightColor.ORANGE -> Res.string.highlight_orange
    HighlightColor.PURPLE -> Res.string.highlight_purple
    HighlightColor.TEAL -> Res.string.highlight_teal
    HighlightColor.RED -> Res.string.highlight_red
}

@Composable
private fun <T : Enum<T>> ReadingColorGrid(
    entries: List<T>, selected: String, color: (T) -> Color,
    label: @Composable (T) -> String, onSelect: (T) -> Unit
) {
    entries.chunked(6).forEach { rowEntries ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            rowEntries.forEach { entry ->
                val description = label(entry)
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(color(entry))
                        .then(if (entry.name == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                        .clickable { onSelect(entry) }
                        .semantics { contentDescription = description; role = Role.RadioButton }
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
