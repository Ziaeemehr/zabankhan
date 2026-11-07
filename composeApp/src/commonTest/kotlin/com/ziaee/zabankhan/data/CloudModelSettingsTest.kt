package com.ziaee.zabankhan.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.ziaee.zabankhan.translate.llm.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CloudModelSettingsTest {
    private class Store : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test fun providerKeysStaySeparateAndNeverEnterPreferences() = runTest {
        val store = Store()
        val secrets = FakeSecretStore()
        val repository = SettingsRepository(store, secrets = secrets)
        val router = LlmPrefs(engine = TranslationEngine.ONLINE)
        repository.saveCloud("OPENROUTER", "router-secret", router)
        val deepseek = router.copy(onlineProvider = LlmProvider.DEEPSEEK, onlineModel = "deepseek-chat")
        repository.saveCloud("DEEPSEEK", "deepseek-secret", deepseek)
        assertEquals("router-secret", repository.cloudSecrets("OPENROUTER").get("OPENROUTER"))
        assertEquals("deepseek-secret", repository.current().llmApiKey)
        assertFalse(store.data.value.asMap().values.any { it == "router-secret" || it == "deepseek-secret" })
        repository.saveCloud("DEEPSEEK", "", deepseek)
        assertNull(repository.cloudSecrets("DEEPSEEK").get("DEEPSEEK"))
        assertEquals("router-secret", repository.cloudSecrets("OPENROUTER").get("OPENROUTER"))
    }

    @Test fun disablingCloudPreservesConfigurationAndSecureKeyAcrossRecreation() = runTest {
        val store = Store()
        val secrets = FakeSecretStore()
        val prefs = LlmPrefs(engine = TranslationEngine.GOOGLE, onlineProvider = LlmProvider.CUSTOM,
            customBaseUrl = "https://example.org/v1", onlineModel = "my-model")
        SettingsRepository(store, secrets = secrets).saveCloud(prefs.customBaseUrl, "custom-secret", prefs)
        val restored = SettingsRepository(store, secrets = secrets)
        assertEquals("", restored.current().llmProvider)
        assertEquals(prefs, restored.current().cloudModelPrefs)
        assertEquals("custom-secret", restored.cloudSecrets(prefs.customBaseUrl).get(prefs.customBaseUrl))
    }

    @Test fun existingActiveCredentialCanBeLoadedAndRetainedAfterDisabling() = runTest {
        val store = Store()
        val repository = SettingsRepository(store, secrets = FakeSecretStore())
        repository.setLlm("OPENROUTER", "legacy-secure-key", "model")
        val token = repository.cloudSecrets("OPENROUTER").get("OPENROUTER")
        assertEquals("legacy-secure-key", token)
        repository.saveCloud("OPENROUTER", token.orEmpty(), LlmPrefs())
        assertEquals(token, repository.cloudSecrets("OPENROUTER").get("OPENROUTER"))
    }
}
