package com.ziaee.zabankhan.dictionary

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.resources.Res
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.contentStyle
import com.ziaee.zabankhan.ui.reader.partOfSpeechResource
import com.ziaee.zabankhan.ui.TranslationSourceIcon
import com.ziaee.zabankhan.ui.theme.ZabankhanDesign
import com.ziaee.zabankhan.translate.ExampleSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.platform.LocalUriHandler

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OwnDictionaryBlock(lookup: DictionaryLookup, lang: LanguageSpec, target: String, vm: AppViewModel,
    textId: Long, sentence: String, speak: (String) -> Unit, onLookup: (String) -> Unit,
    loading: Boolean = false, failed: Boolean = false) {
    val entries = lookup.entries + lookup.idioms
    val settings by vm.settings.collectAsState()
    val effectiveTarget = settings?.dictionaryMeaningLang?.ifBlank { target } ?: target
    if (entries.isEmpty()) {
        DictionaryMachineFallback(lookup.query, lang.code, effectiveTarget, sentence, textId, vm,
            settings?.let(vm.container::translations), loading, failed)
        return
    }
    val vocabEntries by remember(lang.code) { vm.container.vocab.observeAll(lang.code) }.collectAsState(emptyList())
    val lists by remember(lang.code) { vm.container.vocab.observeLists(lang.code) }.collectAsState(emptyList())
    val translations = remember(effectiveTarget, settings) { settings?.let(vm.container::translations) }
    val meaningsState by produceState(false to emptyMap<Long, List<SenseMeaning>>(), lookup, effectiveTarget, translations) {
        val repository = translations ?: return@produceState
        value = true to entries.associate { it.id to vm.container.ownDictionary.senseMeanings(it, effectiveTarget, repository) }
    }
    val meanings = meaningsState.second
    if (meaningsState.first && entries.none { entry -> meanings[entry.id].orEmpty().any { it.text.isNotBlank() } }) {
        DictionaryMachineFallback(lookup.query, lang.code, effectiveTarget, sentence, textId, vm,
            translations, loading = false, failed = false)
        return
    }
    var monolingualMode by remember(lookup.query) { mutableStateOf(false) }
    val hasMonolingualDefinitions = lang.monolingualPackId != null && entries.any { it.monolingualDefinitions.isNotEmpty() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.own_dictionary_title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        listOf("fa" to "فا", "en" to "EN").forEach { (code, label) ->
            TextButton(onClick = { monolingualMode = false; vm.action { vm.container.settings.setDictionaryMeaningLang(code) } }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(label, color = if (!monolingualMode && effectiveTarget == code) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (hasMonolingualDefinitions) TextButton(onClick = { monolingualMode = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(stringResource(Res.string.own_dictionary_french_definitions), color = if (monolingualMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (lookup.via.isNotEmpty()) {
        val names = formCodeNames()
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) { Text(lookup.via.joinToString(" · ") { redirect ->
            "${redirect.form} → ${redirect.lemma} · ${formLabelText(redirect.labels.joinToString(";")) { names[it] ?: it }}"
        }, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium.merge(contentStyle(lookup.query))) }
    }
    if (monolingualMode) {
        val uri = LocalUriHandler.current
        Text(stringResource(Res.string.own_dictionary_french_definitions), style = MaterialTheme.typography.titleSmall)
        entries.distinctBy { it.id }.filter { it.monolingualDefinitions.isNotEmpty() }.take(8).forEach { entry ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.word, style = MaterialTheme.typography.titleMedium.merge(contentStyle(entry.word)))
                    val definitions = entry.monolingualDefinitions
                    val pos = partOfSpeechResource(entry.pos)?.let { stringResource(it) } ?: entry.pos
                    val gender = when (entry.gender) {
                        "m" -> stringResource(Res.string.own_form_m)
                        "f" -> stringResource(Res.string.own_form_f)
                        else -> entry.gender
                    }
                    Text(listOf(pos, gender.takeIf(String::isNotBlank)).filterNotNull().joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var expanded by remember(entry.id) { mutableStateOf(false) }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        definitions.take(if (expanded || definitions.size <= 2) definitions.size else 2).forEachIndexed { index, sense ->
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("${index + 1}. ${sense.definition}", style = MaterialTheme.typography.bodyMedium)
                                    if (sense.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        sense.tags.forEach { tag -> Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                            Text(tag, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                        } }
                                    }
                                    if (sense.example.isNotBlank()) Text(sense.example, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (definitions.size > 2) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (expanded) stringResource(Res.string.own_dictionary_show_less) else stringResource(Res.string.own_dictionary_show_more), style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { lang.monolingualSourceUrlTemplate?.let { uri.openUri(it.replace("{word}", entry.word.replace(" ", "_"))) } }, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(Res.string.own_dictionary_french_source), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text(stringResource(Res.string.own_dictionary_french_attribution), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else entries.distinctBy { it.id }.take(8).forEachIndexed { index, entry ->
        CompactEntryCard(entry, meanings[entry.id].orEmpty(), lang, effectiveTarget, vm, textId, sentence, speak, onLookup, vocabEntries, lists,
            via = if (index == 0) lookup.via.filter { it.entryId == entry.id } else emptyList(), initiallyExpanded = index == 0)
    }
    HorizontalDivider()
}

@Composable
private fun DictionaryMachineFallback(
    query: String, source: String, target: String, sentence: String, textId: Long, vm: AppViewModel,
    repository: com.ziaee.zabankhan.translate.TranslationRepository?, loading: Boolean, failed: Boolean,
) {
    var translated by remember(query, source, target, sentence) { mutableStateOf<com.ziaee.zabankhan.translate.ExampleTranslation?>(null) }
    var translating by remember(query, source, target, sentence) { mutableStateOf(false) }
    var saving by remember(query, source, target, sentence) { mutableStateOf(false) }
    val vocabEntries by remember(source) { vm.container.vocab.observeAll(source) }.collectAsState(emptyList())
    LaunchedEffect(query, source, target, sentence, loading, failed, repository) {
        translated = null
        if (loading || failed || query.isBlank() || repository == null) return@LaunchedEffect
        translating = true
        try {
            translated = repository.googleDictionaryFallback(query, source, target)
                .getOrNull()?.takeIf { it.text.isNotBlank() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { translated = null }
        finally { translating = false }
    }
    when {
        loading -> Text(stringResource(Res.string.own_searching), Modifier.fillMaxWidth().padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        failed -> Text(stringResource(Res.string.own_search_error), Modifier.fillMaxWidth().padding(vertical = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        translating -> Text(stringResource(Res.string.translation_loading), Modifier.fillMaxWidth().padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        translated != null -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
            val value = requireNotNull(translated)
            val savedEntry = vocabEntries.firstOrNull { it.lang == source && it.word.equals(query, true) &&
                it.meaningLang == target && it.meaning == value.text && it.sentence == sentence }
            SelectionContainer(Modifier.weight(1f)) { Text(value.text, style = MaterialTheme.typography.bodyLarge.merge(contentStyle(value.text))) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TranslationSourceIcon(value)
                Text(stringResource(Res.string.own_dictionary_machine_translation), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(enabled = !saving, onClick = {
                    saving = true
                    vm.action {
                        try {
                            if (savedEntry != null) vm.container.vocab.delete(savedEntry)
                            else vm.container.vocab.insert(com.ziaee.zabankhan.data.VocabEntry(lang = source,
                                textId = textId.takeIf { it > 0L }, word = query, sentence = sentence,
                                meaning = value.text, meaningLang = target))
                        } finally { saving = false }
                    }
                }) {
                    Icon(if (savedEntry != null) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                        contentDescription = stringResource(if (savedEntry != null) Res.string.own_remove_from_leitner else Res.string.own_add_to_leitner))
                }
            }
        }
        else -> Text(stringResource(Res.string.own_no_entry), Modifier.fillMaxWidth().padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun formCodeNames(): Map<String, String> = mapOf(
    "ind" to stringResource(Res.string.own_form_ind), "subj" to stringResource(Res.string.own_form_subj),
    "cond" to stringResource(Res.string.own_form_cond), "imp" to stringResource(Res.string.own_form_imp),
    "pres" to stringResource(Res.string.own_form_pres), "impf" to stringResource(Res.string.own_form_impf),
    "fut" to stringResource(Res.string.own_form_fut), "past" to stringResource(Res.string.own_form_past),
    "pp" to stringResource(Res.string.own_form_pp), "ppr" to stringResource(Res.string.own_form_ppr),
    "inf" to stringResource(Res.string.own_form_inf), "1s" to stringResource(Res.string.own_form_1s),
    "2s" to stringResource(Res.string.own_form_2s), "3s" to stringResource(Res.string.own_form_3s),
    "1p" to stringResource(Res.string.own_form_1p), "2p" to stringResource(Res.string.own_form_2p),
    "3p" to stringResource(Res.string.own_form_3p), "m" to stringResource(Res.string.own_form_m),
    "f" to stringResource(Res.string.own_form_f), "sg" to stringResource(Res.string.own_form_sg),
    "pl" to stringResource(Res.string.own_form_pl), "refl" to stringResource(Res.string.own_form_refl),
    "form" to stringResource(Res.string.own_form_form)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OwnDictionarySenseSaveControl(
    entry: Entry, sense: Sense, index: Int, meaning: String, lang: LanguageSpec, target: String,
    vm: AppViewModel, textId: Long, sentence: String,
    vocabEntries: List<com.ziaee.zabankhan.data.VocabEntry>, lists: List<com.ziaee.zabankhan.data.VocabList>,
) {
    val scope = rememberCoroutineScope()
    var busy by remember(entry.id, index) { mutableStateOf(false) }
    var listMenu by remember(entry.id, index) { mutableStateOf(false) }
    var newList by remember(entry.id, index) { mutableStateOf(false) }
    var newListName by remember(entry.id, index) { mutableStateOf("") }
    var pendingListId by remember(entry.id, index) { mutableStateOf<Long?>(null) }
    val example = sense.examples.firstOrNull()?.text ?: sentence
    val savedEntry = vocabEntries.firstOrNull {
        it.lang == lang.code && it.word.equals(entry.word, true) && it.meaningLang == target &&
            it.meaning == meaning && it.sentence == example
    }
    LaunchedEffect(lang.code, lists) {
        val last = vm.container.cache.get("dictionary:last-list:${lang.code}")?.toLongOrNull()
        if (pendingListId == null) pendingListId = last?.takeIf { id -> lists.any { it.id == id } }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    vm.action {
                        if (savedEntry != null) vm.container.vocab.delete(savedEntry)
                        else {
                            val listId = pendingListId?.takeIf { it >= 0 }
                            val existing = vm.container.vocab.getAll(lang.code).firstOrNull {
                                it.lang == lang.code && it.word.equals(entry.word, true) && it.meaningLang == target &&
                                    it.meaning == meaning && it.sentence == example
                            }
                            val item = existing?.copy(textId = textId, meaning = meaning, meaningLang = target, listId = listId)
                                ?: com.ziaee.zabankhan.data.VocabEntry(lang = lang.code, textId = textId, word = entry.word,
                                    sentence = example, meaning = meaning, meaningLang = target, listId = listId)
                            if (item.id == 0L) vm.container.vocab.insert(item) else vm.container.vocab.update(item)
                            vm.container.cache.put("dictionary:last-list:${lang.code}", listId?.toString().orEmpty())
                        }
                    }
                } finally { busy = false }
            }
        }) {
            Icon(if (savedEntry != null) Icons.Filled.Bookmark else Icons.Outlined.BookmarkAdd,
                contentDescription = stringResource(if (savedEntry != null) Res.string.own_remove_from_leitner else Res.string.own_add_to_leitner))
        }
        Box {
            IconButton(onClick = { listMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ArrowDropDown, stringResource(Res.string.vocab_list_select), Modifier.size(18.dp))
            }
            DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(Res.string.vocab_list_uncategorized)) }, onClick = { pendingListId = -1L; listMenu = false })
                lists.forEach { list -> DropdownMenuItem(text = { Text(list.name) }, onClick = { pendingListId = list.id; listMenu = false }) }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("+ " + stringResource(Res.string.vocab_list_new)) }, onClick = { newList = true; listMenu = false })
            }
        }
    }
    if (newList) AlertDialog(
        onDismissRequest = { newList = false }, title = { Text(stringResource(Res.string.vocab_list_new)) },
        text = { OutlinedTextField(value = newListName, onValueChange = { newListName = it }, label = { Text(stringResource(Res.string.vocab_list_name_field)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = newListName.isNotBlank(), onClick = {
            val name = newListName.trim()
            vm.action { pendingListId = vm.container.vocab.insertList(com.ziaee.zabankhan.data.VocabList(lang = lang.code, name = name)) }
            newListName = ""; newList = false
        }) { Text(stringResource(Res.string.action_create)) } },
        dismissButton = { TextButton(onClick = { newList = false; newListName = "" }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun OwnDictionaryEntryCard(entry: Entry, meanings: List<SenseMeaning>, target: String,
    speak: (String) -> Unit, onLookup: (String) -> Unit) {
    var expressionExpanded by remember(entry.id) { mutableStateOf(false) }
    var sensesExpanded by remember(entry.id) { mutableStateOf(false) }
    var tense by remember(entry.id) { mutableStateOf("pres") }
    var tenseMenu by remember(entry.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = ZabankhanDesign.spacing.xSmall), shape = MaterialTheme.shapes.large, elevation = CardDefaults.cardElevation(defaultElevation = ZabankhanDesign.elevations.card)) {
        Column(Modifier.fillMaxWidth().padding(ZabankhanDesign.spacing.small), verticalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.xSmall)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectionContainer { Text(entry.word, style = MaterialTheme.typography.headlineMedium.merge(contentStyle(entry.word))) }
                    if (entry.ipa.isNotBlank()) Text(entry.ipa, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(entry.ipa)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { speak(entry.word) }) { Icon(Icons.Default.VolumeUp, stringResource(Res.string.own_listen)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                listOf(entry.pos, entry.gender).filter(String::isNotBlank).forEach { DictionaryLabel(it) }
                if (entry.freq > 0) {
                    Text(if (entry.freq >= 4) stringResource(Res.string.own_frequency_very_common) else stringResource(Res.string.own_frequency_common), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val shownSenses = entry.senses.take(if (sensesExpanded) entry.senses.size else 3)
            shownSenses.forEachIndexed { index, sense ->
                val meaning = meanings.getOrNull(index)?.text ?: sense.en
                // Show up to 2 entry-level examples under the first senses that have none.
                val fallback = if (sense.examples.isEmpty() && index < 2) entry.moreExamples.drop(index * 2).take(2).map { Example(it, "", "") } else emptyList()
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("${index + 1}. ", style = MaterialTheme.typography.bodyLarge)
                        SelectionContainer(Modifier.weight(1f)) { Text(meaning, style = MaterialTheme.typography.bodyLarge.merge(contentStyle(meaning))) }
                        if (meanings.getOrNull(index)?.translation != null) TranslationSourceIcon(meanings[index].translation)
                        else if (target == Languages.PERSIAN.code && sense.fa.isBlank()) TranslationSourceIcon(ExampleSource.DICTIONARY)
                    }
                    if (sense.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { sense.tags.forEach { DictionaryLabel(it) } }
                    (sense.examples + fallback).forEach { example ->
                        val accent = MaterialTheme.colorScheme.primary
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.width(2.dp).fillMaxHeight().background(accent))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Top) {
                                    SelectionContainer(Modifier.weight(1f)) { Text(example.text, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(example.text)).copy(fontStyle = FontStyle.Italic)) }
                                    IconButton(onClick = { speak(example.text) }, Modifier.size(32.dp)) { Icon(Icons.Default.VolumeUp, stringResource(Res.string.own_listen), Modifier.size(16.dp)) }
                                }
                                // Persian missing in data for many entries: fall back to English.
                                val translated = when (target) { Languages.PERSIAN.code -> example.fa.ifBlank { example.en }; Languages.ENGLISH.code -> example.en; else -> "" }
                                if (translated.isNotBlank()) Text(translated, style = MaterialTheme.typography.bodySmall.merge(contentStyle(translated)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (sense.synonyms.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        sense.synonyms.forEach { Text(it, Modifier.clickable { onLookup(it) }.padding(vertical = 4.dp), style = MaterialTheme.typography.bodySmall.merge(contentStyle(it)), color = MaterialTheme.colorScheme.primary) }
                    }
                }
                if (index < shownSenses.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            }
            if (entry.senses.size > 3) TextButton(onClick = { sensesExpanded = !sensesExpanded }, contentPadding = PaddingValues(0.dp)) { Text(if (sensesExpanded) stringResource(Res.string.own_show_less) else stringResource(Res.string.own_show_all_meanings, entry.senses.size)) }
            if (entry.moreExamples.size > 4 || (entry.moreExamples.isNotEmpty() && entry.senses.none { it.examples.isEmpty() })) {
                HorizontalDivider()
                Text(stringResource(Res.string.own_more_examples), style = MaterialTheme.typography.titleSmall)
                entry.moreExamples.drop(if (entry.senses.any { it.examples.isEmpty() }) 4 else 0).take(5).forEach { TextButton(onClick = { speak(it) }, contentPadding = PaddingValues(0.dp)) { Text(it, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(it)).copy(fontStyle = FontStyle.Italic)) } }
            }
            if (entry.expressions.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(Res.string.own_expressions), style = MaterialTheme.typography.titleSmall)
                Column { entry.expressions.take(if (expressionExpanded) entry.expressions.size else 6).forEach { expression ->
                    TextButton(onClick = { onLookup(expression) }, contentPadding = PaddingValues(vertical = 4.dp)) { Text(expression, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium.merge(contentStyle(expression))) }
                }
                if (entry.expressions.size > 6) TextButton(onClick = { expressionExpanded = !expressionExpanded }) { Text(stringResource(if (expressionExpanded) Res.string.own_show_less else Res.string.own_show_more)) } }
            }
            if (entry.forms.isNotEmpty()) {
                Text(stringResource(Res.string.own_dictionary_forms), style = MaterialTheme.typography.titleSmall)
                if (entry.pos.contains("verb", true) || entry.forms.keys.any { it in setOf("ind", "subj", "cond", "imp") }) {
                    Box {
                        TextButton(onClick = { tenseMenu = true }) {
                            Text(stringResource(when (tense) {
                                "impf" -> Res.string.own_form_impf
                                "fut" -> Res.string.own_form_fut
                                "cond" -> Res.string.own_form_cond
                                "subj" -> Res.string.own_form_subj
                                else -> Res.string.own_form_pres
                            }))
                        }
                        DropdownMenu(
                            expanded = tenseMenu,
                            onDismissRequest = { tenseMenu = false }
                        ) {
                            listOf("pres", "impf", "fut", "cond", "subj").forEach { code ->
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(when (code) {
                                            "impf" -> Res.string.own_form_impf
                                            "fut" -> Res.string.own_form_fut
                                            "cond" -> Res.string.own_form_cond
                                            "subj" -> Res.string.own_form_subj
                                            else -> Res.string.own_form_pres
                                        }))
                                    },
                                    onClick = { tense = code; tenseMenu = false }
                                )
                            }
                        }
                    }
                    val forms = entry.forms[tense].orEmpty()
                    val pronouns = listOf("je", "tu", "il", "nous", "vous", "ils")
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f), shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            forms.take(6).forEachIndexed { i, form -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(pronouns[i], color = MaterialTheme.colorScheme.onSurfaceVariant); Text(form, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(form))) } }
                        }
                    }
                    listOf("pp", "ppr", "inf", "aux").forEach { key -> entry.forms[key]?.firstOrNull()?.let { Text("${formCodeNames()[key] ?: key}: $it", style = MaterialTheme.typography.bodySmall.merge(contentStyle(it))) } }
                } else { val codeNames = formCodeNames(); Text(entry.forms.entries.joinToString(" · ") { (key, values) -> "${codeNames[key] ?: key}: ${values.joinToString(", ")}" }, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(entry.word))) }
            }
        }
    }
}

@Composable
private fun DictionaryLabel(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .65f)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall.merge(contentStyle(text)), color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OwnDictionarySettings(vm: AppViewModel, settings: AppSettings, openSearch: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val dictionary = vm.container.ownDictionary
    TextButton(onClick = openSearch) { Text(stringResource(Res.string.own_search)) }
    var meaningMenu by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = meaningMenu, onExpandedChange = { meaningMenu = it }) {
        OutlinedButton(onClick = { meaningMenu = true }, modifier = Modifier.menuAnchor()) {
            Text(stringResource(Res.string.own_dictionary_meaning_language, when (settings.dictionaryMeaningLang) { "fa" -> stringResource(Res.string.own_dictionary_persian); "en" -> stringResource(Res.string.own_dictionary_english); else -> stringResource(Res.string.own_dictionary_follow_app) }))
        }
        ExposedDropdownMenu(expanded = meaningMenu, onDismissRequest = { meaningMenu = false }) {
            listOf("" to Res.string.own_dictionary_follow_app, "fa" to Res.string.own_dictionary_persian, "en" to Res.string.own_dictionary_english).forEach { (code, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { meaningMenu = false; vm.action { vm.container.settings.setDictionaryMeaningLang(code) } })
            }
        }
    }
    Languages.learnable.filter { it.ownDictionaryAvailable && it.code in settings.learningLangs }.forEach { lang ->
        key(lang.code) {
            var state by remember { mutableStateOf(DictionaryDownload(0, 0, 0)) }
            var busy by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            LaunchedEffect(dictionary, lang) { state = dictionary.status(lang) }
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(Res.string.own_dictionary_settings, lang.nativeName), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (state.complete) Res.string.own_dictionary_offline else Res.string.own_dictionary_online))
                val size = if (state.bytes >= 1024L * 1024L) "${state.bytes / (1024L * 1024L)} MB" else "${state.bytes / 1024} KB"
                Text(stringResource(Res.string.own_dictionary_size, size), style = MaterialTheme.typography.bodySmall)
                if (busy) {
                    if (state.total > 0) LinearProgressIndicator(progress = { state.completed.toFloat() / state.total }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${state.completed} / ${state.total}", style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    TextButton(enabled = !busy, onClick = { busy = true; failed = false; scope.launch {
                        try { dictionary.downloadAll(lang) { state = it } } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true } finally { busy = false; state = dictionary.status(lang) }
                    } }) { Text(stringResource(Res.string.own_dictionary_download)) }
                    TextButton(enabled = !busy && state.completed > 0, onClick = { busy = true; scope.launch {
                        try { dictionary.delete(lang); state = dictionary.status(lang) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true } finally { busy = false }
                    } }) { Text(stringResource(Res.string.own_dictionary_delete)) }
                }
                if (failed) Text(stringResource(Res.string.own_dictionary_download_failed), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
