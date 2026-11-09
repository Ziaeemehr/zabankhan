package com.ziaee.zabankhan.ui.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DictionaryProvidersTest {
    @Test fun filtersByLearningAndMeaningLanguage() {
        val frenchEnglish = dictionaryProviders(Languages.FRENCH, "en")
        assertTrue(frenchEnglish.any { it.name == "WordReference" })
        assertFalse(frenchEnglish.any { it.name == "B-amooz" })
        val frenchPersian = dictionaryProviders(Languages.FRENCH, "fa")
        assertTrue(frenchPersian.any { it.name == "B-amooz" })
        assertTrue(frenchPersian.any { it.name == "WordReference" })
        val englishPersian = dictionaryProviders(Languages.ENGLISH, "fa")
        assertTrue(englishPersian.any { it.name == "Cambridge" })
        assertFalse(englishPersian.any { it.name == "Larousse" })
        assertEquals("https://glosbe.com/en/fa/ice%20cream", englishPersian.first { it.name == "Glosbe" }.urlFor("ice cream"))
        assertTrue(dictionaryProviders(Languages.ENGLISH.copy(dictionaryLinks = emptyList()), "fa").isEmpty())
    }

    @Test fun restoresLastProviderSeparatelyPerLearningLanguage() = runTest {
        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                transform(data.value).also { data.value = it }
        }
        val repository = SettingsRepository(store, secrets = FakeSecretStore())
        repository.setLastDictionaryProvider("fr", "Larousse")
        repository.setLastDictionaryProvider("en", "Cambridge")
        val restored = SettingsRepository(store, secrets = FakeSecretStore()).current()
        assertEquals("Larousse", restored.lastDictionaryProviders["fr"])
        assertEquals("Cambridge", restored.lastDictionaryProviders["en"])
        assertEquals("Larousse", selectedDictionaryProvider(dictionaryProviders(Languages.FRENCH, "fa"), restored.lastDictionaryProviders["fr"])?.name)
        assertEquals("WordReference", selectedDictionaryProvider(dictionaryProviders(Languages.FRENCH, "fa"), "WordReference")?.name)
        assertNull(selectedDictionaryProvider(emptyList(), "Larousse"))
    }

    @Test fun blocksAdDomainsAndSubdomainsWithoutBlockingLookalikes() {
        assertTrue(isDictionaryAdHost("doubleclick.net"))
        assertTrue(isDictionaryAdHost("ads.DOUBLECLICK.NET"))
        assertTrue(isDictionaryAdHost("ads.doubleclick.net."))
        assertFalse(isDictionaryAdHost("notdoubleclick.net"))
        assertFalse(isDictionaryAdHost("doubleclick.net.example.org"))
        assertFalse(isDictionaryAdHost("www.larousse.fr"))
        assertTrue(isAllowedDictionaryUrl("https://www.larousse.fr/dictionnaires/francais/mot"))
        listOf("http://example.org", "file:///etc/passwd", "javascript:alert(1)", "https://ads.doubleclick.net/a").forEach {
            assertFalse(isAllowedDictionaryUrl(it), it)
        }
    }
}
