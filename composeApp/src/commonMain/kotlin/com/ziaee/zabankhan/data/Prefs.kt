package com.ziaee.zabankhan.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

const val PREFS_FILE = "zabankhan.preferences_pb"

fun createPrefsStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

data class AppSettings(
    /** Active learning language; null until onboarding picks one. */
    val learningLang: String? = null,
    /** Languages the user has added, comma-separated codes. */
    val learningLangs: List<String> = emptyList(),
    /** Language meanings are shown in ("fa" / "en"). */
    val meaningLang: String = "fa",
    /** UI language ("system", "en", "fa", "fr"). */
    val uiLang: String = "system",
    val themeMode: String = "SYSTEM",
    val dailyNewCards: Int = 10,
    val readerFontSize: Int = 20,
    val llmProvider: String = "",
    val llmApiKey: String = "",
    val llmModel: String = "",
)

class SettingsRepository(private val store: DataStore<Preferences>) {
    private object K {
        val lang = stringPreferencesKey("learning_lang")
        val langs = stringPreferencesKey("learning_langs")
        val meaning = stringPreferencesKey("meaning_lang")
        val ui = stringPreferencesKey("ui_lang")
        val theme = stringPreferencesKey("theme_mode")
        val newCards = intPreferencesKey("daily_new_cards")
        val font = intPreferencesKey("reader_font_size")
        val llmProvider = stringPreferencesKey("llm_provider")
        val llmKey = stringPreferencesKey("llm_api_key")
        val llmModel = stringPreferencesKey("llm_model")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            learningLang = p[K.lang],
            learningLangs = p[K.langs]?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            meaningLang = p[K.meaning] ?: "fa",
            uiLang = p[K.ui] ?: "system",
            themeMode = p[K.theme] ?: "SYSTEM",
            dailyNewCards = p[K.newCards] ?: 10,
            readerFontSize = p[K.font] ?: 20,
            llmProvider = p[K.llmProvider].orEmpty(),
            llmApiKey = p[K.llmKey].orEmpty(),
            llmModel = p[K.llmModel].orEmpty(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun addLearningLang(code: String) = store.edit { p ->
        val list = p[K.langs]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        if (code !in list) p[K.langs] = (list + code).joinToString(",")
        p[K.lang] = code
    }

    suspend fun removeLearningLang(code: String) = store.edit { p ->
        val list = p[K.langs]?.split(',')?.filter { it.isNotBlank() && it != code }.orEmpty()
        p[K.langs] = list.joinToString(",")
        if (p[K.lang] == code) { if (list.isEmpty()) p.remove(K.lang) else p[K.lang] = list.first() }
    }

    suspend fun setLearningLang(code: String) = store.edit { it[K.lang] = code }
    suspend fun setMeaningLang(code: String) = store.edit { it[K.meaning] = code }
    suspend fun setUiLang(code: String) = store.edit { it[K.ui] = code }
    suspend fun setThemeMode(mode: String) = store.edit { it[K.theme] = mode }
    suspend fun setDailyNewCards(n: Int) = store.edit { it[K.newCards] = n.coerceIn(0, 100) }
    suspend fun setReaderFontSize(sp: Int) = store.edit { it[K.font] = sp.coerceIn(12, 40) }
    suspend fun setLlm(provider: String, apiKey: String, model: String) = store.edit {
        it[K.llmProvider] = provider; it[K.llmKey] = apiKey; it[K.llmModel] = model
    }
}
