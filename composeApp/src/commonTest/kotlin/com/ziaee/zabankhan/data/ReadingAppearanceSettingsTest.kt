package com.ziaee.zabankhan.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.compose.ui.graphics.Color
import com.ziaee.zabankhan.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ReadingAppearanceSettingsTest {
    private class Store(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
    @Test fun invalidRestoredAppearanceFallsBackToReferenceDefaults() = runTest {
        val store = Store(preferencesOf(
            stringPreferencesKey("reader_font_scale") to "unknown",
            stringPreferencesKey("reading_background") to "unknown",
            stringPreferencesKey("reading_highlight_color") to "unknown"))
        val settings = SettingsRepository(store, secrets = FakeSecretStore()).current()
        assertEquals("MEDIUM", settings.readerFontScale)
        assertEquals("SEPIA", settings.readingBackground)
        assertEquals("YELLOW", settings.readingHighlightColor)
    }
    @Test fun appearanceAndFollowSurviveRepositoryRecreation() = runTest {
        val store = Store()
        val repository = SettingsRepository(store, secrets = FakeSecretStore())
        repository.setReaderFontScale("XLARGE")
        repository.setReadingBackground("MINT")
        repository.setReadingHighlightColor("BLUE")
        repository.setReaderAutoFollow(false)
        repository.setLastHighlightColor("pink")
        repository.setSpeechRate(1.5f)
        val restored = SettingsRepository(store, secrets = FakeSecretStore()).current()
        assertEquals("XLARGE", restored.readerFontScale)
        assertEquals("MINT", restored.readingBackground)
        assertEquals("BLUE", restored.readingHighlightColor)
        assertFalse(restored.readerAutoFollow)
        assertEquals("pink", restored.lastHighlightColor)
        assertEquals(1.5f, restored.speechRate)
    }
    @Test fun sepiaPaletteAndDarkPaletteMatchReference() {
        val sepia = readingPaletteFor(ReadingBackground.SEPIA)
        assertEquals(Color(0xFFFBF6EC), sepia.background)
        assertEquals(Color(0xFF2E2A22), sepia.ink)
        assertEquals(Color(0xFF6B6252), sepia.inkFaded)
        assertEquals(Color(0xFF8A6D3B), sepia.accent)
        assertEquals(Color(0xFFE6DDC8), sepia.divider)
        val dark = readingPaletteFor(ReadingBackground.DARK)
        assertEquals(Color(0xFF1A1A1A), dark.background)
        assertEquals(Color(0xFFE8E4DA), dark.ink)
        assertEquals(Color(0xFFD8B978), dark.accent)
    }
}
