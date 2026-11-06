package com.ziaee.zabankhan.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.test.runTest
import kotlin.test.*

internal class FakeSecretStore : SecretStore {
    val values = mutableMapOf<String, String>()
    var failWrites = false
    override fun get(name: String) = values[name]
    override fun set(name: String, value: String) {
        check(!failWrites) { "Storage unavailable" }
        values[name] = value
    }
    override fun remove(name: String) { values.remove(name) }
}

class SecretMigrationTest {
    private val key = stringPreferencesKey("llm_api_key")
    private class MemoryStore(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test fun migratesLegacyKeyAndRemovesPlaintext() = runTest {
        val prefs = MemoryStore(preferencesOf(key to "legacy"))
        val secrets = FakeSecretStore()
        val repo = SettingsRepository(prefs, secrets = secrets)
        assertEquals("legacy", repo.current().llmApiKey)
        assertEquals("legacy", secrets.get(LLM_API_KEY))
        assertNull(prefs.data.value[key])
        assertEquals("legacy", repo.current().llmApiKey)
    }

    @Test fun existingSecureKeyWinsOverLegacyValue() = runTest {
        val prefs = MemoryStore(preferencesOf(key to "old"))
        val secrets = FakeSecretStore().apply { set(LLM_API_KEY, "new") }
        assertEquals("new", SettingsRepository(prefs, secrets = secrets).current().llmApiKey)
        assertNull(prefs.data.value[key])
    }

    @Test fun failedMigrationPreservesLegacyKeyForRetry() = runTest {
        val prefs = MemoryStore(preferencesOf(key to "legacy"))
        val secrets = FakeSecretStore().apply { failWrites = true }
        val repo = SettingsRepository(prefs, secrets = secrets)
        assertFailsWith<IllegalStateException> { repo.current() }
        assertEquals("legacy", prefs.data.value[key])
        secrets.failWrites = false
        assertEquals("legacy", repo.current().llmApiKey)
        assertNull(prefs.data.value[key])
    }

    @Test fun savesReplacesRemovesAndExportsWithoutPlaintext() = runTest {
        val prefs = MemoryStore(preferencesOf(key to "legacy"))
        val secrets = FakeSecretStore()
        val repo = SettingsRepository(prefs, secrets = secrets)
        repo.persistForBackup()
        assertNull(prefs.data.value[key])
        repo.setLlm("provider", "replacement", "model")
        assertEquals("replacement", repo.current().llmApiKey)
        assertNull(prefs.data.value[key])
        repo.setLlm("provider", "", "model")
        assertEquals("", repo.current().llmApiKey)
        assertNull(secrets.get(LLM_API_KEY))
    }

    @Test fun emptyLegacyValueIsRemoved() = runTest {
        val prefs = MemoryStore(preferencesOf(key to ""))
        val secrets = FakeSecretStore()
        assertEquals("", SettingsRepository(prefs, secrets = secrets).current().llmApiKey)
        assertNull(prefs.data.value[key])
        assertTrue(secrets.values.isEmpty())
    }

    @Test fun activeCollectorsSeeKeyOnlyChanges() = runTest {
        val prefs = MemoryStore(preferencesOf(
            stringPreferencesKey("llm_provider") to "provider",
            stringPreferencesKey("llm_model") to "model",
        ))
        val secrets = FakeSecretStore().apply { set(LLM_API_KEY, "first") }
        val repo = SettingsRepository(prefs, secrets = secrets)
        val observed = mutableListOf<AppSettings>()
        val collection = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            repo.settings.take(2).toList(observed)
        }
        testScheduler.runCurrent()
        assertEquals("first", observed.single().llmApiKey)
        repo.setLlm("provider", "second", "model")
        testScheduler.runCurrent()
        assertEquals(listOf("first", "second"), collection.await().map { it.llmApiKey })
    }

    @Test fun restoreClearsDeviceCredential() = runTest {
        val secrets = FakeSecretStore().apply { set(LLM_API_KEY, "saved") }
        SettingsRepository(MemoryStore(emptyPreferences()), secrets = secrets).closeForRestore()
        assertNull(secrets.get(LLM_API_KEY))
    }
}
