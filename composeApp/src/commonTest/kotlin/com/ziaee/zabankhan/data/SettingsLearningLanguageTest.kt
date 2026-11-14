package com.ziaee.zabankhan.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ziaee.zabankhan.lang.Languages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SettingsLearningLanguageTest {
    @Test fun restoredMeaningLanguageMustBeSupportedAndDifferentFromLearningLanguage() = runTest {
        for ((learning, meaning, expected) in listOf(
            Triple(Languages.FRENCH.code, "unsupported", Languages.PERSIAN.code),
            Triple(Languages.ENGLISH.code, Languages.ENGLISH.code, Languages.ENGLISH.code),
            Triple(Languages.FRENCH.code, Languages.ENGLISH.code, Languages.ENGLISH.code),
        )) {
            val repository = SettingsRepository(MemoryStore(preferencesOf(
                stringPreferencesKey("learning_lang") to learning,
                stringPreferencesKey("meaning_lang") to meaning,
            )), secrets = FakeSecretStore())
            assertEquals(expected, repository.current().meaningLang)
        }
    }

    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }

    @Test fun restoredEnglishLearningLanguageFallsBackToFrenchWithoutDeletingEnglishPreferences() = runTest {
        val store = MemoryStore(preferencesOf(
            stringPreferencesKey("learning_lang") to Languages.ENGLISH.code,
            stringPreferencesKey("learning_langs") to "${Languages.FRENCH.code},${Languages.ENGLISH.code}",
        ))
        val repository = SettingsRepository(store,
            secrets = FakeSecretStore())
        assertEquals(Languages.FRENCH.code, repository.current().learningLang)
        assertEquals(listOf(Languages.FRENCH.code), repository.current().learningLangs)
        assertEquals(Languages.ENGLISH.code, store.data.value[stringPreferencesKey("learning_lang")])
        assertEquals("fr,en", store.data.value[stringPreferencesKey("learning_langs")])
    }

    @Test fun englishLearningCannotBeAddedOrSelectedWhileTemporarilyDisabled() = runTest {
        val repository = SettingsRepository(MemoryStore(), secrets = FakeSecretStore())
        assertFailsWith<IllegalArgumentException> { repository.addLearningLang(Languages.ENGLISH.code) }
        assertFailsWith<IllegalArgumentException> { repository.setLearningLang(Languages.ENGLISH.code) }
    }

    @Test fun englishRemainsAvailableAsMeaningLanguageForFrench() = runTest {
        val repository = SettingsRepository(MemoryStore(preferencesOf(
            stringPreferencesKey("learning_lang") to Languages.FRENCH.code,
            stringPreferencesKey("meaning_lang") to Languages.ENGLISH.code,
        )), secrets = FakeSecretStore())
        assertEquals(Languages.ENGLISH.code, repository.current().meaningLang)
    }

    @Test fun restoredPersianWithEnglishPreferencesReturnsToFrench() = runTest {
        val repository = SettingsRepository(MemoryStore(preferencesOf(
            stringPreferencesKey("learning_lang") to Languages.PERSIAN.code,
            stringPreferencesKey("learning_langs") to "${Languages.PERSIAN.code},${Languages.ENGLISH.code}",
        )), secrets = FakeSecretStore())
        assertEquals(Languages.FRENCH.code, repository.current().learningLang)
        assertEquals(listOf(Languages.FRENCH.code), repository.current().learningLangs)
    }

    @Test fun restoredPersianOnlyPreferencesReturnToOnboarding() = runTest {
        val repository = SettingsRepository(MemoryStore(preferencesOf(
            stringPreferencesKey("learning_lang") to Languages.PERSIAN.code,
            stringPreferencesKey("learning_langs") to Languages.PERSIAN.code,
        )), secrets = FakeSecretStore())
        assertNull(repository.current().learningLang)
        assertTrue(repository.current().learningLangs.isEmpty())
    }

    @Test fun frenchDictionaryMeaningLanguageSurvivesSettingsReload() = runTest {
        val store = MemoryStore()
        SettingsRepository(store, secrets = FakeSecretStore()).setDictionaryMeaningLang(Languages.FRENCH.code)
        assertEquals(Languages.FRENCH.code, SettingsRepository(store, secrets = FakeSecretStore()).current().dictionaryMeaningLang)
    }

    @Test fun persianCannotBeAddedOrActivatedAsLearningLanguage() = runTest {
        val repository = SettingsRepository(MemoryStore(), secrets = FakeSecretStore())
        assertFailsWith<IllegalArgumentException> { repository.addLearningLang(Languages.PERSIAN.code) }
        assertFailsWith<IllegalArgumentException> { repository.setLearningLang(Languages.PERSIAN.code) }
        repository.addLearningLang(Languages.FRENCH.code)
        assertEquals(Languages.FRENCH.code, repository.current().learningLang)
    }

    @Test fun freshInstallStillRequiresOnboarding() = runTest {
        val repository = SettingsRepository(MemoryStore(), secrets = FakeSecretStore())
        assertNull(repository.current().learningLang)
        assertTrue(repository.current().learningLangs.isEmpty())
    }

    @Test fun englishContentRemainsSupportedWhileLearningChoiceIsDisabled() {
        assertTrue(Languages.ENGLISH in Languages.learnable)
        assertFalse(Languages.ENGLISH in Languages.enabledLearning)
        assertEquals(listOf(Languages.FRENCH), Languages.enabledLearning)
    }

}
