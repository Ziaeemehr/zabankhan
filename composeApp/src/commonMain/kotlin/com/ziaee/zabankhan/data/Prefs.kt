package com.ziaee.zabankhan.data

import com.ziaee.zabankhan.lang.Languages
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /** UI language ("system", "en", "fa", "fr"). */
    val uiLang: String = "system",
    val themeMode: String = "SYSTEM",
    val dailyNewCards: Int = 10,
    val dailyGoalMinutes: Int = 10,
    val readerFontSize: Int = 20,
    val speechRate: Float = 1f,
    val voiceEngine: String = "online",
    val autoPronounceCards: Boolean = false,
    val ttsVoices: Map<String, String> = emptyMap(),
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val llmProvider: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "",
)

class SettingsRepository(private val store: DataStore<Preferences>, private val scope: CoroutineScope? = null) {
    suspend fun closeForRestore() { scope?.coroutineContext?.get(Job)?.cancelAndJoin() }
    private object K {
        val lang = stringPreferencesKey("learning_lang")
        val langs = stringPreferencesKey("learning_langs")
        val meaning = stringPreferencesKey("meaning_lang")
        val ui = stringPreferencesKey("ui_lang")
        val theme = stringPreferencesKey("theme_mode")
        val newCards = intPreferencesKey("daily_new_cards")
        val dailyGoal = intPreferencesKey("daily_goal_minutes")
        val font = intPreferencesKey("reader_font_size")
        val speechRate = androidx.datastore.preferences.core.floatPreferencesKey("speech_rate")
        val autoCards = androidx.datastore.preferences.core.booleanPreferencesKey("auto_pronounce_cards")
        val engine = stringPreferencesKey("voice_engine")
        val voices = stringPreferencesKey("tts_voices")
        val reminderEnabled = androidx.datastore.preferences.core.booleanPreferencesKey("review_reminder_enabled")
        val reminderHour = intPreferencesKey("review_reminder_hour")
        val reminderMinute = intPreferencesKey("review_reminder_minute")
        val llmProvider = stringPreferencesKey("llm_provider")
        val llmKey = stringPreferencesKey("llm_api_key")
        val llmModel = stringPreferencesKey("llm_model")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        val allowed = Languages.learnable.map { it.code }.toSet()
        val langs = p[K.langs]?.split(',')?.filter { it in allowed }?.distinct().orEmpty()
        val active = p[K.lang]?.takeIf { it in allowed } ?: langs.firstOrNull()
        AppSettings(
            learningLang = active,
            learningLangs = (langs + listOfNotNull(active)).distinct(),
            meaningLang = p[K.meaning]?.takeIf {
                it == Languages.PERSIAN.code || (it == Languages.ENGLISH.code && it != active)
            } ?: Languages.PERSIAN.code,
            uiLang = p[K.ui] ?: "system",
            themeMode = p[K.theme] ?: "SYSTEM",
            dailyNewCards = p[K.newCards] ?: 10,
            dailyGoalMinutes = (p[K.dailyGoal] ?: 10).coerceIn(1, 120),
            readerFontSize = p[K.font] ?: 20,
            speechRate = p[K.speechRate] ?: 1f,
            voiceEngine = p[K.engine]?.takeIf { it == "device" } ?: "online",
            autoPronounceCards = p[K.autoCards] ?: false,
            ttsVoices = runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(p[K.voices] ?: "{}") }.getOrDefault(emptyMap()),
            reminderEnabled = p[K.reminderEnabled] ?: false,
            reminderHour = (p[K.reminderHour] ?: 19).coerceIn(0, 23),
            reminderMinute = (p[K.reminderMinute] ?: 0).coerceIn(0, 59),
            llmProvider = p[K.llmProvider].orEmpty(),
            llmApiKey = p[K.llmKey].orEmpty(),
            llmModel = p[K.llmModel].orEmpty(),
        )
    }

    /** Persist defaults before a file-based backup, even on a fresh installation. */
    suspend fun persistForBackup() = store.edit { if (it[K.theme] == null) it[K.theme] = "SYSTEM" }

    suspend fun current(): AppSettings = settings.first()

    suspend fun addLearningLang(code: String) = store.edit { p ->
        require(Languages.learnable.any { it.code == code }) { "Unsupported learning language" }
        val list = p[K.langs]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        if (code !in list) p[K.langs] = (list + code).joinToString(",")
        p[K.lang] = code
    }

    suspend fun removeLearningLang(code: String) = store.edit { p ->
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
        require(Languages.learnable.any { it.code == code }) { "Unsupported learning language" }
        it[K.lang] = code
    }
    suspend fun setMeaningLang(code: String) = store.edit { it[K.meaning] = code }
    suspend fun setUiLang(code: String) = store.edit { it[K.ui] = code }
    suspend fun setThemeMode(mode: String) = store.edit { it[K.theme] = mode }
    suspend fun setDailyGoalMinutes(minutes: Int) = store.edit { it[K.dailyGoal] = minutes.coerceIn(1, 120) }
    suspend fun setDailyNewCards(n: Int) = store.edit { it[K.newCards] = n.coerceIn(0, 100) }
    suspend fun setReaderFontSize(sp: Int) = store.edit { it[K.font] = sp.coerceIn(12, 40) }
    suspend fun setSpeechRate(rate: Float) = store.edit { it[K.speechRate] = rate.coerceIn(.75f, 1.25f) }
    suspend fun setAutoPronounceCards(enabled: Boolean) = store.edit { it[K.autoCards] = enabled }
    suspend fun setVoiceEngine(engine: String) = store.edit {
        require(engine == "online" || engine == "device")
        it[K.engine] = engine
    }
    suspend fun setTtsVoice(lang: String, id: String?) = store.edit {
        com.ziaee.zabankhan.speech.learningSpeechLanguage(lang)
        val voices = runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(it[K.voices] ?: "{}") }.getOrDefault(emptyMap())
        it[K.voices] = kotlinx.serialization.json.Json.encodeToString(if (id == null) voices - lang else voices + (lang to id))
    }
    suspend fun setLlm(provider: String, apiKey: String, model: String) = store.edit {
        it[K.llmProvider] = provider; it[K.llmKey] = apiKey; it[K.llmModel] = model
    }
}
