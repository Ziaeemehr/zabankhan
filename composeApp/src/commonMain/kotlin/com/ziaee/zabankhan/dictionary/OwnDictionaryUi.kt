package com.ziaee.zabankhan.dictionary

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.contentStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnDictionaryBlock(matches: List<DictionaryMeaning>) {
    if (matches.isEmpty()) return
    Text(stringResource(Res.string.own_dictionary_title), style = MaterialTheme.typography.labelMedium)
    matches.take(4).forEach { match ->
        val entry = match.entry
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.word, style = MaterialTheme.typography.titleMedium.merge(contentStyle(entry.word)))
            entry.ipa?.takeIf(String::isNotBlank)?.let { Text(it, style = contentStyle(it)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOfNotNull(entry.gender, entry.pos.takeIf(String::isNotBlank)).forEach { label ->
                    SuggestionChip(onClick = {}, label = { Text(label) })
                }

            }
            match.meanings.take(4).forEachIndexed { index, meaning ->
                Text(meaning, style = contentStyle(meaning))
                val translation = match.translations.getOrNull(index)
                if (translation != null) com.ziaee.zabankhan.ui.TranslationSourceIcon(translation)
                else com.ziaee.zabankhan.ui.TranslationSourceIcon(com.ziaee.zabankhan.translate.ExampleSource.DICTIONARY)
            }
            entry.example?.split('\t')?.filter(String::isNotBlank)?.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall.merge(contentStyle(it)))
            }
            if (entry.formLabels.isNotEmpty()) {
                var expanded by remember(entry) { mutableStateOf(false) }
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(Res.string.own_dictionary_forms))
                }
                if (expanded) Text(entry.formLabels.joinToString(" · "), style = contentStyle(entry.word))
            }
        }
    }
    HorizontalDivider()
}

/** The only SettingsScreen integration is one call in its dictionary tab. */
@Composable
fun OwnDictionarySettings(vm: AppViewModel, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    val dictionary = vm.container.ownDictionary
    Languages.learnable.filter { it.ownDictionaryAvailable && it.code in settings.learningLangs }.forEach { lang ->
        key(lang.code) {
            var state by remember { mutableStateOf(DictionaryDownload(0, 0, 0)) }
            var busy by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            LaunchedEffect(dictionary, lang) { state = dictionary.status(lang) }
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(Res.string.own_dictionary_settings, lang.nativeName), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (state.complete) Res.string.own_dictionary_offline else Res.string.own_dictionary_online))
                Text(stringResource(Res.string.own_dictionary_size, (state.bytes / 1024).toString()), style = MaterialTheme.typography.bodySmall)
                if (busy) {
                    if (state.total > 0) LinearProgressIndicator(progress = { state.completed.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${state.completed} / ${state.total}", style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        failed = false
                        scope.launch {
                            try { dictionary.downloadAll(lang) { state = it } }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { failed = true }
                            finally { busy = false; state = dictionary.status(lang) }
                        }
                    }) { Text(stringResource(Res.string.own_dictionary_download)) }
                    TextButton(enabled = !busy && state.completed > 0, onClick = {
                        busy = true
                        scope.launch {
                            try { dictionary.delete(lang); state = dictionary.status(lang) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { failed = true }
                            finally { busy = false }
                        }
                    }) { Text(stringResource(Res.string.own_dictionary_delete)) }
                }
                if (failed) Text(stringResource(Res.string.own_dictionary_download_failed), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
