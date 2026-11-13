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
            Triple(Languages.ENGLISH.code, Languages.ENGLISH.code, Languages.PERSIAN.code),
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

    @Test fun restoredPersianLearningLanguageFallsBackToSupportedLanguage() = runTest {
        val repository = SettingsRepository(MemoryStore(preferencesOf(
            stringPreferencesKey("learning_lang") to Languages.PERSIAN.code,
            stringPreferencesKey("learning_langs") to "${Languages.PERSIAN.code},${Languages.ENGLISH.code}",
        )), secrets = FakeSecretStore())
        assertEquals(Languages.ENGLISH.code, repository.current().learningLang)
        assertEquals(listOf(Languages.ENGLISH.code), repository.current().learningLangs)
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
        repository.addLearningLang(Languages.ENGLISH.code)
        assertEquals(Languages.ENGLISH.code, repository.current().learningLang)
    }
}
