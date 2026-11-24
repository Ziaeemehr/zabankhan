package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import com.ziaee.zabankhan.ui.theme.*
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.stringResource
import com.ziaee.zabankhan.backup.rememberDriveAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable fun Onboarding(vm: AppViewModel) {
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var meaning by rememberSaveable { mutableStateOf(Languages.PERSIAN.code) }
    var busy by remember { mutableStateOf(false) }
    var showOfflineConfirmation by rememberSaveable { mutableStateOf(false) }
    var offlineAcknowledged by rememberSaveable { mutableStateOf(false) }
    var signInFailed by remember { mutableStateOf(false) }
    val drive = rememberDriveAuth()
    var connected by remember { mutableStateOf(drive.connected) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val colors = MaterialTheme.colorScheme
    val enabled = selected.isNotEmpty() && !busy
    val buttonScale by androidx.compose.animation.core.animateFloatAsState(if (enabled) 1f else .97f, animationSpec = androidx.compose.animation.core.tween(280), label = "onboardingStartScale")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(colors.primaryContainer, colors.tertiaryContainer.copy(alpha = .72f), colors.surface)))) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawCircle(colors.primary.copy(alpha = .08f), radius = size.minDimension * .34f, center = androidx.compose.ui.geometry.Offset(size.width * .94f, size.height * .09f))
                drawCircle(colors.tertiary.copy(alpha = .09f), radius = size.minDimension * .22f, center = androidx.compose.ui.geometry.Offset(size.width * .03f, size.height * .58f))
                drawCircle(colors.secondary.copy(alpha = .06f), radius = size.minDimension * .15f, center = androidx.compose.ui.geometry.Offset(size.width * .92f, size.height * .83f))
            }
            Column(Modifier.fillMaxSize().verticalScroll(scroll).safeDrawingPadding().padding(horizontal = 20.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    androidx.compose.animation.AnimatedVisibility(true, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260)) { it / 7 }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Zabankhan · زبان‌خوان", style = MaterialTheme.typography.headlineLarge, color = colors.onPrimaryContainer)
                            Text(stringResource(Res.string.onboarding_tagline), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(true, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 90)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260, delayMillis = 90)) { it / 8 }) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OnboardingValue(Icons.Default.MenuBook, stringResource(Res.string.onboarding_value_read))
                            OnboardingValue(Icons.Default.Bookmark, stringResource(Res.string.onboarding_value_review))
                            OnboardingValue(Icons.Default.CloudOff, stringResource(Res.string.onboarding_value_dictionary))
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(true, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 170)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260, delayMillis = 170)) { it / 9 }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(Res.string.learning_languages), style = MaterialTheme.typography.titleLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = if (wide) 2 else 1) {
                                Languages.learnable.forEach { lang ->
                                    val isSelected = lang.code in selected
                                    Surface(enabled = !busy && lang in Languages.enabledLearning, onClick = {
                                        if (lang !in Languages.enabledLearning) return@Surface
                                        selected = if (isSelected) selected - lang.code else selected + lang.code
                                        if (selected.firstOrNull() == meaning) meaning = if (meaning == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code
                                    }, modifier = Modifier.fillMaxWidth(if (wide) .485f else 1f), shape = RoundedCornerShape(20.dp), color = if (isSelected) colors.primaryContainer else colors.surface.copy(alpha = .88f), border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.primary else colors.outlineVariant), tonalElevation = if (isSelected) 2.dp else 0.dp) {
                                        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(lang.nativeName, style = MaterialTheme.typography.titleMedium)
                                                Text(languageName(lang), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                                if (lang !in Languages.enabledLearning) Text(stringResource(Res.string.language_temporarily_unavailable), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                            }
                                            Surface(shape = RoundedCornerShape(50), color = colors.secondaryContainer) { Text(lang.code.uppercase(), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = colors.onSecondaryContainer) }
                                            if (isSelected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.primary)
                                        }
                                    }
                                }
                            }
                            val meanings = listOf(Languages.PERSIAN, Languages.ENGLISH).filter { it.code != selected.firstOrNull() }
                            if (meanings.isNotEmpty()) {
                                Text(stringResource(Res.string.meaning_language), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                    meanings.forEachIndexed { index, lang ->
                                        SegmentedButton(selected = meaning == lang.code, onClick = { meaning = lang.code }, shape = SegmentedButtonDefaults.itemShape(index, meanings.size)) { Text(languageName(lang), style = MaterialTheme.typography.labelLarge) }
                                    }
                                }
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(true, enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 240)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260, delayMillis = 240)) { it / 10 }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (drive.configured) {
                                Text(stringResource(Res.string.onboarding_drive_optional), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(Res.string.onboarding_drive_description), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                OutlinedButton(enabled = !busy && !connected, onClick = {
                                    busy = true
                                    signInFailed = false
                                    scope.launch {
                                        try { connected = drive.accessToken() != null } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { signInFailed = true }
                                        finally { busy = false }
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (connected) Res.string.onboarding_drive_connected else Res.string.onboarding_drive_connect)) }
                                if (signInFailed) Text(stringResource(Res.string.onboarding_drive_error), color = colors.error, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text(stringResource(Res.string.onboarding_drive_unavailable), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            Button(enabled = enabled, onClick = {
                                if (drive.configured && !connected && !offlineAcknowledged) showOfflineConfirmation = true else startOnboarding(vm, selected, meaning) { busy = it }
                            }, modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = buttonScale; scaleY = buttonScale }, contentPadding = PaddingValues(vertical = 16.dp), shape = RoundedCornerShape(18.dp)) { Text(stringResource(Res.string.onboarding_start), style = MaterialTheme.typography.titleMedium) }
                            if (vm.failed) Text(stringResource(Res.string.error_save), color = colors.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
    if (showOfflineConfirmation) AlertDialog(
        onDismissRequest = { showOfflineConfirmation = false },
        title = { Text(stringResource(Res.string.onboarding_no_backup_title)) },
        text = { Text(stringResource(Res.string.onboarding_no_backup_message)) },
        confirmButton = { TextButton(onClick = { showOfflineConfirmation = false; offlineAcknowledged = true; startOnboarding(vm, selected, meaning) { busy = it } }) { Text(stringResource(Res.string.onboarding_continue_without_account)) } },
        dismissButton = { TextButton(onClick = { showOfflineConfirmation = false }) { Text(stringResource(Res.string.cancel)) } },
    )
}

private fun startOnboarding(vm: AppViewModel, selected: List<String>, meaning: String, setBusy: (Boolean) -> Unit) {
    setBusy(true)
    vm.action {
        try {
            vm.container.settings.setMeaningLang(meaning)
            selected.reversed().forEach { vm.container.settings.addLearningLang(it) }
        } finally { setBusy(false) }
    }
}

@Composable
private fun OnboardingValue(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = colors.secondaryContainer.copy(alpha = .9f), modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(21.dp)) }
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
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
    var guideScreen by remember { mutableStateOf<String?>(null) }
    val learningLanguage = Languages.byCode(settings.learningLang.orEmpty()) ?: Languages.learnable.first()
    val tabTitles = listOf(
        Res.string.ed_settings_general,
        Res.string.ed_settings_reading,
        Res.string.ed_settings_speech,
        Res.string.ed_settings_dictionary,
        Res.string.ed_settings_review,
        Res.string.ed_settings_backup,
        Res.string.about,
        Res.string.help_guides_title
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(-1) }
    BackHandler(enabled = selectedTab >= 0 || guideScreen != null) {
        when (guideScreen) {
            "gestures" -> guideScreen = "help"
            "help" -> guideScreen = null
            else -> selectedTab = -1
        }
    }
    when (guideScreen) {
        "help" -> { HelpGuidesScreen(onOpenGestures = { guideScreen = "gestures" }, onBack = { guideScreen = null }); return }
        "gestures" -> { com.ziaee.zabankhan.ui.reader.GestureGuideScreen(learningLanguage) { guideScreen = "help" }; return }
    }
    if (selectedTab == 6) {
        AboutScreen(learningLanguage, onBack = { selectedTab = -1 }, onHelp = { guideScreen = "help" })
        return
    }
    Scaffold(
        snackbarHost = { SnackbarHost(speechSettingsSnackbar) },
        topBar = {
            EditorialTopAppBar(
                title = stringResource(if (selectedTab < 0) Res.string.nav_settings else tabTitles[selectedTab]),
                back = if (selectedTab >= 0) ({ selectedTab = -1 }) else null,
                backLabel = stringResource(Res.string.accessibility_back),
                windowInsets = TopAppBarDefaults.windowInsets,
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            if (selectedTab < 0) {
                SettingsMenu(tabTitles = tabTitles, onSelect = { if (it == 7) guideScreen = "help" else selectedTab = it })
            } else {
                key(selectedTab) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (selectedTab) {
                            0 -> {

                                SettingsGroup {
                                    Text(stringResource(Res.string.learning_languages), style = MaterialTheme.typography.titleMedium)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Languages.learnable.forEach { lang ->
                                            val selected = lang.code in settings.learningLangs
                                            FilterChip(selected, enabled = lang in Languages.enabledLearning, onClick = { if (!selected || settings.learningLangs.size > 1) vm.action {
                                                if (selected && settings.learningLangs.size > 1) repository.removeLearningLang(lang.code)
                                                else if (!selected) repository.addLearningLang(lang.code)
                                                val active = repository.current().learningLang
                                                if (active == settings.meaningLang) repository.setMeaningLang(if (active == Languages.PERSIAN.code) Languages.ENGLISH.code else Languages.PERSIAN.code)
                                            } }, label = { Text(if (lang in Languages.enabledLearning) languageName(lang) else "${languageName(lang)} · ${stringResource(Res.string.language_temporarily_unavailable)}") },
                                                leadingIcon = if (selected) ({ Icon(Icons.Default.Check, stringResource(Res.string.learning_languages), Modifier.size(18.dp)) }) else null,
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer))
                                        }
                                    }
                                    SettingsChoice(stringResource(Res.string.meaning_language), settings.meaningLang,
                                        listOf(Languages.PERSIAN, Languages.ENGLISH).filter { it.code != settings.learningLang }.map { it.code to languageName(it) }) {
                                        vm.action { repository.setMeaningLang(it) }
                                    }
                                }
                                SettingsGroup {
                                    StepperSetting(stringResource(Res.string.h2_daily_goal), settings.dailyGoalMinutes, 1, 120) {
                                        vm.action { repository.setDailyGoalMinutes(it) }
                                    }
                                    StepperSetting(stringResource(Res.string.progress_weekly_goal), settings.weeklyPointsGoal,
                                        com.ziaee.zabankhan.progress.MIN_WEEKLY_GOAL, com.ziaee.zabankhan.progress.MAX_WEEKLY_GOAL) {
                                        vm.action { repository.setWeeklyPointsGoal(it) }
                                    }
                                }
                                com.ziaee.zabankhan.reminder.ReviewReminderSettings(vm, settings)
                                com.ziaee.zabankhan.update.UpdateSettings(vm.container.http)
                            }
                            1 -> {

                                SettingsGroup {
                                    SettingsChoice(stringResource(Res.string.theme), settings.themeMode,
                                        listOf("SYSTEM" to stringResource(Res.string.system), "LIGHT" to stringResource(Res.string.light), "DARK" to stringResource(Res.string.dark))) {
                                        vm.action { repository.setThemeMode(it) }
                                    }
                                }
                                OutlinedButton(onClick = grammar, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.pack_grammar)) }
                                if (com.ziaee.zabankhan.datapack.LanguageDataPacks.available(settings.learningLang.orEmpty())) {
                                    OutlinedButton(onClick = exams, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.pack_tcf)) }
                                }

                                SettingsGroup {
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
                                SettingsGroup {
                                    Text(stringResource(Res.string.page_navigation_title), style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
                                    CompactChoiceColumn(PageNavigation.entries,
                                        PageNavigation.entries.firstOrNull { it.name == settings.pageNavigation } ?: PageNavigation.SCROLL,
                                        { stringResource(it.labelResource()) }) { navigation ->
                                        vm.action { repository.setPageNavigation(navigation.name) }
                                    }
                                }

                            }
                            2 -> {

                                SettingsChoice(stringResource(Res.string.edge_engine), settings.voiceEngine,
                                    voiceEngineOptions()) {
                                    vm.action { repository.setVoiceEngine(it) }
                                }
                                if (settings.voiceEngine == "local") LocalTtsSettings(learningLanguage)
                                SettingsGroup {
                                    SettingsChoice(stringResource(Res.string.speech_rate), settings.speechRate, listOf(.75f, 1f, 1.25f).map { it to "${it}×" }) { vm.action { repository.setSpeechRate(it) } }
                                    SettingsChoice(stringResource(Res.string.speech_repeat), settings.speechRepeat, (1..3).map { it to "${it}×" }) { vm.action { repository.setSpeechRepeat(it) } }
                                }
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
                            3 -> {
                                var searchDictionary by remember { mutableStateOf(false) }
                                com.ziaee.zabankhan.dictionary.OwnDictionarySettings(vm, settings) { searchDictionary = true }
                                SettingsChoice(stringResource(Res.string.dictionary_font_scale_title), settings.dictionaryFontScale,
                                    FontScale.entries.map { it.name to stringResource(it.labelResource()) }) { vm.action { repository.setDictionaryFontScale(it) } }
                                if (searchDictionary) DictionarySearchDialog(vm, settings) { searchDictionary = false }

                                TranslationEngineSettings(vm.container, settings)

                            }
                            4 -> {

                                TextButton(onClick = progress) { Text(stringResource(Res.string.h2_progress)) }
                                SettingsGroup {
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
                                }
                                SettingsGroup {
                                    // Copied from French Reader SettingsScreen review settings.
                                    Text(stringResource(Res.string.review_intervals), style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 3.dp))
                                    settings.reviewIntervalDays.forEachIndexed { index, days ->
                                        StepperSetting(stringResource(Res.string.review_box_interval, index + 1), days.toInt(), 1, 365) { value ->
                                            val intervals = settings.reviewIntervalDays.toMutableList().also { it[index] = value.toLong() }
                                            vm.action { repository.setReviewIntervals(intervals) }
                                        }
                                    }
                                    TextButton(onClick = { vm.action { repository.resetReviewIntervals() } }) {
                                        Text(stringResource(Res.string.review_intervals_reset))
                                    }
                                }

                            }
                            5 -> {

                                com.ziaee.zabankhan.backup.BackupSettings(vm.container)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                com.ziaee.zabankhan.backup.FrenchReaderImportSettings(vm.container)

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
private fun SettingsMenu(tabTitles: List<StringResource>, onSelect: (Int) -> Unit) {
    val icons = listOf(
        Icons.Default.Settings, Icons.Default.MenuBook, Icons.Default.RecordVoiceOver, Icons.Default.Translate,
        Icons.Default.Style, Icons.Default.Backup, Icons.Default.Info, Icons.Default.HelpOutline,
    )
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        EditorialCard {
            tabTitles.forEachIndexed { index, title ->
                if (index > 0) HomeEntryDivider()
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onSelect(index) }
                        .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icons[index], null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text(stringResource(title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    EditorialCard {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal fun StepperSetting(label: String, value: Int, minimum: Int, maximum: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(settingsStep(value, -1, minimum, maximum)) }, enabled = value > minimum) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        // Typing the number avoids dozens of taps for big jumps (10 -> 100).
        var draft by remember(value) { mutableStateOf(value.toString()) }
        androidx.compose.foundation.text.BasicTextField(
            value = draft,
            onValueChange = { text ->
                draft = text.filter { it.isDigit() }.take(6)
                draft.toIntOrNull()?.takeIf { it in minimum..maximum && it != value }?.let(onChange)
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.width(56.dp).onFocusChanged { if (!it.isFocused) draft = value.toString() },
            decorationBox = { inner ->
                Box(Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)).padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center) { inner() }
            },
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T : Enum<T>> ReadingColorGrid(
    entries: List<T>, selected: String, color: (T) -> Color,
    label: @Composable (T) -> String, onSelect: (T) -> Unit
) {
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { entry ->
            val description = label(entry)
            val swatch = color(entry)
            val isSelected = entry.name == selected
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(entry) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(34.dp).background(swatch, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp),
                            tint = if (swatch.luminance() > 0.179f) Color.Black else Color.White)
                    }
                }
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

@Composable
internal fun <T> CompactChoiceColumn(
    entries: List<T>, selected: T, label: @Composable (T) -> String,
    trailing: @Composable (T) -> Unit = {}, onSelect: (T) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        entries.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(entry) }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = entry == selected, onClick = { onSelect(entry) })
                Text(label(entry), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                trailing(entry)
            }
        }
    }
}

private fun PageNavigation.labelResource(): StringResource = when (this) {
    PageNavigation.SCROLL -> Res.string.page_navigation_scroll
    PageNavigation.HORIZONTAL -> Res.string.page_navigation_horizontal
    PageNavigation.VERTICAL -> Res.string.page_navigation_vertical
}
