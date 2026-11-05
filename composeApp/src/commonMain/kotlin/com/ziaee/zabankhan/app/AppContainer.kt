package com.ziaee.zabankhan.app

import androidx.compose.runtime.staticCompositionLocalOf
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.translate.*
import com.ziaee.zabankhan.translate.llm.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.speech.edge.*
import com.ziaee.zabankhan.pdf.AppFiles
import kotlinx.coroutines.*

class AppContainer(val database: AppDatabase, val settings: SettingsRepository, http: HttpClient) {
    private val sourceHttp = http
    val http = http.config { install(WebSockets) }
    private val speechScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tts = SelectedTts(EdgeTts(EdgeTtsClient(this.http), TtsCache("${AppFiles.cacheDir}/tts"), createAudioPlayer(), speechScope), createDeviceTts(), speechScope)
    init {
        SpeechEngine.current?.stop()
        SpeechEngine.current = tts
    }
    /** Release container-owned background work before replacing it after restore. */
    fun close() {
        tts.stop()
        if (SpeechEngine.current === tts) SpeechEngine.current = null
        speechScope.cancel()
        http.close()
        sourceHttp.close()
    }
    val texts = database.texts()
    val vocab = database.vocab()
    val highlights = database.highlights()
    val cache: KeyValueCache = object : KeyValueCache {
        override suspend fun get(key: String) = database.cache().get(key)
        override suspend fun put(key: String, value: String) = database.cache().put(CacheEntry(key, value))
    }
    private var translationConfig: List<String>? = null
    private var repository: TranslationRepository? = null
    fun translations(settings: AppSettings): TranslationRepository {
        val config = listOf(settings.llmProvider, settings.llmApiKey, settings.llmModel)
        if (config != translationConfig || repository == null) {
            val provider = LlmProvider.entries.firstOrNull { it.name == settings.llmProvider }
            val url = provider?.baseUrl ?: settings.llmProvider.takeIf { it.startsWith("https://") }.orEmpty()
            val model = settings.llmModel.ifBlank { provider?.defaultModel.orEmpty() }
            val llm = if (url.isNotBlank() && settings.llmApiKey.isNotBlank() && model.isNotBlank()) {
                LlmTranslator(LlmClient(http, url, model, settings.llmApiKey, false, provider), "$url:$model")
            } else null
            repository = TranslationRepository(http, cache, llm)
            translationConfig = config
        }
        return requireNotNull(repository)
    }
}
val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
