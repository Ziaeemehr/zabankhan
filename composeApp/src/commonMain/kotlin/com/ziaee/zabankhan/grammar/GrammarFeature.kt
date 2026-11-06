@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.ziaee.zabankhan.grammar

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.material3.*
import com.ziaee.zabankhan.app.LocalAppContainer
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.datapack.*
import com.ziaee.zabankhan.lang.*
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

/** The existing grammar destination owns its nested back stack, including related-note links. */
@Composable
fun GrammarScreen(settings: AppSettings, back: () -> Unit) {
    val language = Languages.byCode(settings.learningLang.orEmpty()) ?: return
    val container = LocalAppContainer.current
    val preferences = remember(container, language.code) { container.grammarPreferences(language.code) }
    var pack by remember(language.code) { mutableStateOf<LanguageDataPack?>(null) }
    var loaded by remember(language.code) { mutableStateOf(false) }
    var failed by remember(language.code) { mutableStateOf(false) }
    LaunchedEffect(language.code, preferences) {
        try {
            preferences.load()
            pack = LanguageDataPacks.load(language.code)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loaded = true }
    }
    var pages by rememberSaveable(language.code) { mutableStateOf(listOf<String>()) }
    fun pop() { if (pages.isNotEmpty()) pages = pages.dropLast(1) else back() }
    BackHandler(enabled = pages.isNotEmpty()) { pop() }
    val showPersian = settings.meaningLang == Languages.PERSIAN.code ||
        settings.uiLang == Languages.PERSIAN.code ||
        (settings.uiLang == "system" && androidx.compose.ui.text.intl.Locale.current.language == Languages.PERSIAN.code)
    val savedPages = rememberSaveableStateHolder()
    GrammarFeatureTheme(settings.uiLang) {
      CompositionLocalProvider(LocalGrammarContentLabels provides pack?.grammarLabels.orEmpty()) {
        if (failed) {
            // Preserve the visible error and the reference back navigation on loading failure.
            Column(Modifier.fillMaxSize()) {
                Text(stringResource(Res.string.error_save))
                Box(Modifier.weight(1f)) {
                    GrammarListScreen(emptyList(), language, showPersian, preferences, ::pop, {}, {})
                }
            }
        } else savedPages.SaveableStateProvider(pages.joinToString("/").ifEmpty { ":list" }) {
          when (val page = pages.lastOrNull()) {
            null -> GrammarListScreen(if (loaded) pack?.grammar.orEmpty() else null, language, showPersian,
                preferences, ::pop, { pages = pages + it }, { pages = pages + POSTERS_PAGE })
            POSTERS_PAGE -> GrammarPostersScreen(pack?.posters.orEmpty(), showPersian, ::pop)
            else -> GrammarNoteScreen(page, pack?.grammar, showPersian, preferences, ::pop, { pages = pages + it })
          }
        }
      }
    }
}

internal val LocalGrammarContentLabels = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

private const val POSTERS_PAGE = ":posters"
