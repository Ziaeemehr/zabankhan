package com.ziaee.zabankhan.data

import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.translate.llm.LlmPrefs
import com.ziaee.zabankhan.translate.llm.LlmProvider
import com.ziaee.zabankhan.translate.llm.LlmSecrets
import com.ziaee.zabankhan.translate.llm.TranslationEngine
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath

const val PREFS_FILE = "zabankhan.preferences_pb"

fun createPrefsStore(path: String, scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { path.toPath() })

data class AppSettings(
    /** Active learning language; null until onboarding picks one. */
    val learningLang: String? = null,
    /** Languages the user has added, comma-separated codes. */
    val learningLangs: List<String> = emptyList(),
    /** Language meanings are shown in ("fa" / "en"). */
    val meaningLang: String = Languages.PERSIAN.code,
    val dictionaryMeaningLang: String = "",
    /** UI language ("system", "en", "fa", "fr"). */
    val uiLang: String = "system",
    val themeMode: String = "SYSTEM",
    val dailyNewCards: Int = 10,
    val reviewIntervalDays: List<Long> = com.ziaee.zabankhan.srs.VocabSrs.DEFAULT_INTERVAL_DAYS,
    val dailyGoalMinutes: Int = 10,
    val weeklyPointsGoal: Int = com.ziaee.zabankhan.progress.DEFAULT_WEEKLY_GOAL,
    val readerFontSize: Int = 20,
    val readerFontScale: String = "MEDIUM",
    val dictionaryFontScale: String = "MEDIUM",
    val readingBackground: String = "SEPIA",
    val pageNavigation: String = "SCROLL",
    val readingHighlightColor: String = "YELLOW",
    val readerAutoFollow: Boolean = true,
    val lastHighlightColor: String = "yellow",
    val watchTranslationEnabled: Boolean = false,
    val cloudTranslationEnabled: Boolean = true,
    val watchSubtitleSizePercent: Int = 100,
    val watchCompactPlayer: Boolean = false,
    val speechRate: Float = 1f,
    // How many times reader playback speaks each sentence before moving on.
    val speechRepeat: Int = 1,
    val voiceEngine: String = "online",
    val reviewAudioAutoplay: AudioAutoplay = AudioAutoplay.OFF,
    val speechRecognitionEngine: String = "offline",
    val cardPronunciationPractice: Boolean = false,
    val ttsCacheMaxSizeMb: Int = 500,
    val ttsCacheMaxAgeDays: Int = 30,
    val ttsVoices: Map<String, String> = emptyMap(),
    val lastDictionaryProviders: Map<String, String> = emptyMap(),
    val newsCategories: Map<String, String> = emptyMap(),
    val newsKeywords: Map<String, String> = emptyMap(),
    val newsMatchingFirst: Boolean = true,
    val newsOnlyMatching: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val cloudModelPrefs: LlmPrefs? = null,
    val llmProvider: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "",
)

class SettingsRepository(private val store: DataStore<Preferences>, private val scope: CoroutineScope? = null, private val secrets: SecretStore = createSecretStore()) {
    suspend fun closeForRestore() {
        secrets.remove(LLM_API_KEY)
        scope?.coroutineContext?.get(Job)?.cancelAndJoin()
    }
    private val progressPrefs = com.ziaee.zabankhan.progress.ProgressPreferences(store)
    val backupPrefs = com.ziaee.zabankhan.backup.BackupPrefs(store)
    private val secretMutex = Mutex()
    private val secretRevision = MutableStateFlow(0)

    private suspend fun migrateSecret() = secretMutex.withLock {
        store.edit { prefs ->
            val legacy = prefs[K.llmKey]
            if (!legacy.isNullOrBlank() && secrets.get(LLM_API_KEY) == null) secrets.set(LLM_API_KEY, legacy)
            prefs.remove(K.llmKey)
        }
    }
    private object K {
        val lang = stringPreferencesKey("learning_lang")
        val langs = stringPreferencesKey("learning_langs")
        val meaning = stringPreferencesKey("meaning_lang")
        val dictionaryMeaning = stringPreferencesKey("dictionary_meaning_lang")
        val ui = stringPreferencesKey("ui_lang")
        val theme = stringPreferencesKey("theme_mode")
        val newCards = intPreferencesKey("daily_new_cards")
        val intervals = stringPreferencesKey("review_intervals")
        val dailyGoal = intPreferencesKey("daily_goal_minutes")
        val font = intPreferencesKey("reader_font_size")
        val fontScale = stringPreferencesKey("reader_font_scale")
        val dictionaryFontScale = stringPreferencesKey("dictionary_font_scale")
        val readingBackground = stringPreferencesKey("reading_background")
        val pageNavigation = stringPreferencesKey("page_navigation")
        val readingHighlight = stringPreferencesKey("reading_highlight_color")
        val autoFollow = androidx.datastore.preferences.core.booleanPreferencesKey("reader_auto_follow")
        val lastHighlight = stringPreferencesKey("last_highlight_color")
        val watchTranslation = androidx.datastore.preferences.core.booleanPreferencesKey("watch_translation_enabled")
        val cloudTranslation = androidx.datastore.preferences.core.booleanPreferencesKey("cloud_translation_enabled")
        val watchSize = intPreferencesKey("watch_subtitle_size_percent")
        val watchCompact = androidx.datastore.preferences.core.booleanPreferencesKey("watch_compact_player")
        val speechRate = androidx.datastore.preferences.core.floatPreferencesKey("speech_rate")
        val speechRepeat = intPreferencesKey("speech_repeat")
        val autoCards = androidx.datastore.preferences.core.booleanPreferencesKey("auto_pronounce_cards")
        val autoplay = stringPreferencesKey("review_audio_autoplay_sides")
        val recognitionEngine = stringPreferencesKey("speech_recognition_engine")
        val cardPractice = androidx.datastore.preferences.core.booleanPreferencesKey("card_practice")
        val cacheSize = intPreferencesKey("tts_cache_max_size_mb")
        val cacheAge = intPreferencesKey("tts_cache_max_age_days")
        val engine = stringPreferencesKey("voice_engine")
        val voices = stringPreferencesKey("tts_voices")
        val dictionaryProviders = stringPreferencesKey("last_dictionary_providers")
        val newsCategories = stringPreferencesKey("news_categories")
        val newsKeywords = stringPreferencesKey("news_keywords")
        val newsMatchingFirst = androidx.datastore.preferences.core.booleanPreferencesKey("news_matching_first")
        val newsOnlyMatching = androidx.datastore.preferences.core.booleanPreferencesKey("news_only_matching")
        val reminderEnabled = androidx.datastore.preferences.core.booleanPreferencesKey("review_reminder_enabled")
        val reminderHour = intPreferencesKey("review_reminder_hour")
        val reminderMinute = intPreferencesKey("review_reminder_minute")
        val llmProvider = stringPreferencesKey("llm_provider")
        val llmKey = stringPreferencesKey("llm_api_key")
        val llmModel = stringPreferencesKey("llm_model")
    }

    val settings: Flow<AppSettings> = store.data.onStart { migrateSecret() }.combine(secretRevision) { p, _ ->
        val allowed = Languages.enabledLearning.map { it.code }.toSet()
        val storedLangs = p[K.langs]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val storedActive = p[K.lang]
        // Keep the original values intact for a future language release, but restore active
        // English installations to French while English learning is temporarily unavailable.
        val active = when {
            storedActive != null && storedActive in allowed -> storedActive
            storedActive == Languages.ENGLISH.code || Languages.ENGLISH.code in storedLangs -> Languages.FRENCH.code
            else -> storedLangs.firstOrNull { it in allowed }
        }
        val langs = (storedLangs.filter { it in allowed } + listOfNotNull(active)).distinct()
        AppSettings(
            learningLang = active,
            learningLangs = (langs + listOfNotNull(active)).distinct(),
            meaningLang = p[K.meaning]?.takeIf {
                it == Languages.PERSIAN.code || (it == Languages.ENGLISH.code && it != active)
            } ?: Languages.PERSIAN.code,
            dictionaryMeaningLang = p[K.dictionaryMeaning]?.takeIf { it.isBlank() || Languages.byCode(it) != null }.orEmpty(),
            uiLang = p[K.ui] ?: "system",
            themeMode = p[K.theme] ?: "SYSTEM",
            dailyNewCards = p[K.newCards] ?: 10,
            reviewIntervalDays = restoreReviewIntervals(p[K.intervals]),
            dailyGoalMinutes = (p[K.dailyGoal] ?: 10).coerceIn(1, 120),
            weeklyPointsGoal = com.ziaee.zabankhan.progress.ProgressPreferences.readGoal(p),
            readerFontSize = p[K.font] ?: 20,
            readerFontScale = p[K.fontScale]?.takeIf { value -> com.ziaee.zabankhan.ui.theme.FontScale.entries.any { it.name == value } } ?: "MEDIUM",
            dictionaryFontScale = p[K.dictionaryFontScale]?.takeIf { value -> com.ziaee.zabankhan.ui.theme.FontScale.entries.any { it.name == value } } ?: "MEDIUM",
            readingBackground = p[K.readingBackground]?.takeIf { value -> com.ziaee.zabankhan.ui.theme.ReadingBackground.entries.any { it.name == value } } ?: "SEPIA",
            pageNavigation = p[K.pageNavigation]?.takeIf { value -> com.ziaee.zabankhan.ui.theme.PageNavigation.entries.any { it.name == value } } ?: "SCROLL",
            readingHighlightColor = p[K.readingHighlight]?.takeIf { value -> com.ziaee.zabankhan.ui.theme.HighlightColor.entries.any { it.name == value } } ?: "YELLOW",
            readerAutoFollow = p[K.autoFollow] ?: true,
            lastHighlightColor = p[K.lastHighlight] ?: "yellow",
            watchTranslationEnabled = p[K.watchTranslation] ?: false,
            cloudTranslationEnabled = p[K.cloudTranslation] ?: true,
            watchSubtitleSizePercent = (p[K.watchSize] ?: 100).takeIf { it in listOf(85, 100, 115, 130, 150, 175) } ?: 100,
            watchCompactPlayer = p[K.watchCompact] ?: false,
            speechRate = p[K.speechRate] ?: 1f,
            speechRepeat = (p[K.speechRepeat] ?: 1).coerceIn(1, 3),
            voiceEngine = p[K.engine]?.takeIf { it in setOf("device", "local") } ?: "online",
            reviewAudioAutoplay = AudioAutoplay.restored(p[K.autoplay], p[K.autoCards] ?: false),
            speechRecognitionEngine = p[K.recognitionEngine]?.takeIf { it in listOf("offline", "system") } ?: "offline",
            cardPronunciationPractice = p[K.cardPractice] ?: false,
            ttsCacheMaxSizeMb = (p[K.cacheSize] ?: 500).coerceIn(50, 5000),
            ttsCacheMaxAgeDays = (p[K.cacheAge] ?: 30).coerceIn(1, 365),
            ttsVoices = runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(p[K.voices] ?: "{}") }.getOrDefault(emptyMap()),
            lastDictionaryProviders = runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(p[K.dictionaryProviders] ?: "{}") }.getOrDefault(emptyMap()),
            newsCategories = runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(p[K.newsCategories] ?: "{}") }.getOrDefault(emptyMap()),
            newsKeywords = runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(p[K.newsKeywords] ?: "{}") }.getOrDefault(emptyMap()),
            newsMatchingFirst = p[K.newsMatchingFirst] ?: true,
            newsOnlyMatching = p[K.newsOnlyMatching] ?: false,
            reminderEnabled = p[K.reminderEnabled] ?: false,
            reminderHour = (p[K.reminderHour] ?: 19).coerceIn(0, 23),
            reminderMinute = (p[K.reminderMinute] ?: 0).coerceIn(0, 59),
            cloudModelPrefs = p[stringPreferencesKey("cloud_provider")]?.let { name ->
                val provider = LlmProvider.entries.firstOrNull { it.name == name }
                    ?: LlmProvider.OPENROUTER
                LlmPrefs(
                    engine = if (p[K.llmProvider].isNullOrBlank()) TranslationEngine.GOOGLE else TranslationEngine.ONLINE,
                    onlineProvider = provider,
                    customBaseUrl = p[stringPreferencesKey("cloud_url")].orEmpty(),
                    onlineModel = p[stringPreferencesKey("cloud_model")] ?: provider.defaultModel
                )
            },
            llmProvider = p[K.llmProvider].orEmpty(),
            // A Keychain/Keystore failure must not take the whole settings flow (and app launch) down.
            llmApiKey = runCatching { secrets.get(LLM_API_KEY) }.getOrNull().orEmpty(),
            llmModel = p[K.llmModel].orEmpty(),
        )
    }

    /** Persist defaults before a file-based backup, even on a fresh installation. */
    suspend fun persistForBackup() {
        migrateSecret()
        store.edit { if (it[K.theme] == null) it[K.theme] = "SYSTEM" }
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun addLearningLang(code: String) = store.edit { p ->
        require(Languages.enabledLearning.any { it.code == code }) { "Unsupported learning language" }
        val list = p[K.langs]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        if (code !in list) p[K.langs] = (list + code).joinToString(",")
        p[K.lang] = code
    }

    suspend fun removeLearningLang(code: String) = store.edit { p ->
        require(Languages.enabledLearning.any { it.code == code }) { "Unsupported learning language" }
        val list = p[K.langs]?.split(',')?.filter { it.isNotBlank() && it != code }.orEmpty()
        p[K.langs] = list.joinToString(",")
        if (p[K.lang] == code) { if (list.isEmpty()) p.remove(K.lang) else p[K.lang] = list.first() }
    }

    suspend fun setReminderEnabled(enabled: Boolean) = store.edit { it[K.reminderEnabled] = enabled }
    suspend fun setReminderTime(hour: Int, minute: Int) = store.edit {
        require(hour in 0..23 && minute in 0..59)
        it[K.reminderHour] = hour; it[K.reminderMinute] = minute
    }
    suspend fun setLearningLang(code: String) = store.edit {
        require(Languages.enabledLearning.any { it.code == code }) { "Unsupported learning language" }
        it[K.lang] = code
    }
    suspend fun setLastDictionaryProvider(lang: String, provider: String) = store.edit {
        val providers = runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(it[K.dictionaryProviders] ?: "{}") }.getOrDefault(emptyMap())
        it[K.dictionaryProviders] = kotlinx.serialization.json.Json.encodeToString(providers + (lang to provider))
    }
    suspend fun setNewsCategory(lang: String, category: String) = store.edit {
        val values = runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(it[K.newsCategories] ?: "{}") }.getOrDefault(emptyMap())
        it[K.newsCategories] = kotlinx.serialization.json.Json.encodeToString(values + (lang to category))
    }
    suspend fun setNewsKeywords(lang: String, keywords: String) = store.edit {
        val values = runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(it[K.newsKeywords] ?: "{}") }.getOrDefault(emptyMap())
        it[K.newsKeywords] = kotlinx.serialization.json.Json.encodeToString(values + (lang to keywords))
    }
    suspend fun setNewsMatchingFirst(enabled: Boolean) = store.edit { it[K.newsMatchingFirst] = enabled }
    suspend fun setNewsOnlyMatching(enabled: Boolean) = store.edit { it[K.newsOnlyMatching] = enabled }
    suspend fun setMeaningLang(code: String) = store.edit { it[K.meaning] = code }
    suspend fun setDictionaryMeaningLang(code: String) = store.edit { it[K.dictionaryMeaning] = code.takeIf { it.isBlank() || Languages.byCode(it) != null }.orEmpty() }
    suspend fun setUiLang(code: String) = store.edit { it[K.ui] = code }
    suspend fun setThemeMode(mode: String) = store.edit { it[K.theme] = mode }
    suspend fun setWeeklyPointsGoal(points: Int) = progressPrefs.setGoal(points)
    suspend fun claimProgress(summary: com.ziaee.zabankhan.progress.ProgressSummary) = progressPrefs.claim(summary)

    suspend fun setDailyGoalMinutes(minutes: Int) = store.edit { it[K.dailyGoal] = minutes.coerceIn(1, 120) }
    suspend fun setReviewIntervals(days: List<Long>) = store.edit {
        require(days.size == 5 && days.all { day -> day in 1..365 })
        it[K.intervals] = days.joinToString(",")
    }
    suspend fun resetReviewIntervals() = store.edit { it.remove(K.intervals) }
    suspend fun setDailyNewCards(n: Int) = store.edit { it[K.newCards] = n.coerceIn(0, 100) }
    suspend fun setReaderFontSize(sp: Int) = store.edit { it[K.font] = sp.coerceIn(12, 40) }
    suspend fun setReaderFontScale(value: String) = store.edit {
        require(com.ziaee.zabankhan.ui.theme.FontScale.entries.any { scale -> scale.name == value })
        it[K.fontScale] = value
    }
    suspend fun setDictionaryFontScale(value: String) = store.edit {
        require(com.ziaee.zabankhan.ui.theme.FontScale.entries.any { scale -> scale.name == value })
        it[K.dictionaryFontScale] = value
    }
    suspend fun setPageNavigation(value: String) = store.edit {
        require(com.ziaee.zabankhan.ui.theme.PageNavigation.entries.any { it.name == value })
        it[K.pageNavigation] = value
    }
    suspend fun setReadingBackground(value: String) = store.edit {
        require(com.ziaee.zabankhan.ui.theme.ReadingBackground.entries.any { background -> background.name == value })
        it[K.readingBackground] = value
    }
    suspend fun setReadingHighlightColor(value: String) = store.edit {
        require(com.ziaee.zabankhan.ui.theme.HighlightColor.entries.any { color -> color.name == value })
        it[K.readingHighlight] = value
    }
    suspend fun setReaderAutoFollow(value: Boolean) = store.edit { it[K.autoFollow] = value }
    suspend fun setLastHighlightColor(value: String) = store.edit {
        require(value in setOf("yellow", "green", "blue", "pink", "orange"))
        it[K.lastHighlight] = value
    }
    suspend fun setWatchTranslationEnabled(enabled: Boolean) = store.edit { it[K.watchTranslation] = enabled }
    suspend fun setCloudTranslationEnabled(enabled: Boolean) = store.edit { it[K.cloudTranslation] = enabled }
    suspend fun setWatchCompactPlayer(compact: Boolean) = store.edit { it[K.watchCompact] = compact }
    suspend fun setWatchSubtitleSizePercent(percent: Int) = store.edit {
        require(percent in listOf(85, 100, 115, 130, 150, 175))
        it[K.watchSize] = percent
    }
    suspend fun setSpeechRate(rate: Float) = store.edit { it[K.speechRate] = rate.coerceIn(.75f, 1.5f) }
    suspend fun setSpeechRepeat(times: Int) = store.edit { it[K.speechRepeat] = times.coerceIn(1, 3) }
    suspend fun setReviewAudioAutoplay(value: AudioAutoplay) = store.edit { it[K.autoplay] = value.name }
    suspend fun setSpeechRecognitionEngine(value: String) = store.edit {
        require(value == "offline" || value == "system")
        it[K.recognitionEngine] = value
    }
    suspend fun setCardPronunciationPractice(enabled: Boolean) = store.edit { it[K.cardPractice] = enabled }
    suspend fun setTtsCacheMaxSizeMb(value: Int) = store.edit { it[K.cacheSize] = value.coerceIn(50, 5000) }
    suspend fun setTtsCacheMaxAgeDays(value: Int) = store.edit { it[K.cacheAge] = value.coerceIn(1, 365) }
    suspend fun setVoiceEngine(engine: String) = store.edit {
        require(engine == "online" || engine == "device" || engine == "local")
        it[K.engine] = engine
    }
    suspend fun setTtsVoice(lang: String, id: String?) = store.edit {
        com.ziaee.zabankhan.speech.learningSpeechLanguage(lang)
        val voices = runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(it[K.voices] ?: "{}") }.getOrDefault(emptyMap())
        it[K.voices] = kotlinx.serialization.json.Json.encodeToString(if (id == null) voices - lang else voices + (lang to id))
    }
    /** Provider credentials remain in platform secret storage, including when another source is selected. */
    suspend fun cloudSecrets(provider: String): LlmSecrets = secretMutex.withLock {
        val saved = secrets.get("cloud_$provider")
            ?: if (store.data.first()[K.llmProvider] == provider) secrets.get(LLM_API_KEY) else null
        LlmSecrets(saved?.let { mapOf(provider to it) }.orEmpty())
    }

    suspend fun saveCloud(provider: String, key: String, prefs: LlmPrefs) = secretMutex.withLock {
        if (key.isBlank()) secrets.remove("cloud_$provider") else secrets.set("cloud_$provider", key)
        if (key.isBlank()) secrets.remove(LLM_API_KEY) else secrets.set(LLM_API_KEY, key)
        store.edit {
            it.remove(K.llmKey)
            it[K.llmProvider] = if (prefs.engine == TranslationEngine.ONLINE) provider else ""
            it[K.llmModel] = prefs.onlineModel.trim()
            it[stringPreferencesKey("cloud_provider")] = prefs.onlineProvider.name
            it[stringPreferencesKey("cloud_url")] = prefs.customBaseUrl.trim()
            it[stringPreferencesKey("cloud_model")] = prefs.onlineModel.trim()
        }
        secretRevision.value += 1
    }

    suspend fun setLlm(provider: String, apiKey: String, model: String) = secretMutex.withLock {
        if (apiKey.isBlank()) secrets.remove(LLM_API_KEY) else secrets.set(LLM_API_KEY, apiKey)
        store.edit {
            it.remove(K.llmKey)
            it[K.llmProvider] = provider
            it[K.llmModel] = model
        }
        secretRevision.value += 1
    }
}

/** French Reader VocabPrefs.getIntervals, with the bounds used by its settings stepper. */
internal fun restoreReviewIntervals(raw: String?): List<Long> = raw?.split(',')
    ?.mapNotNull(String::toLongOrNull)
    ?.takeIf { it.size == 5 && it.all { day -> day in 1..365 } }
    ?: com.ziaee.zabankhan.srs.VocabSrs.DEFAULT_INTERVAL_DAYS
