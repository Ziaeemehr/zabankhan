package com.ziaee.zabankhan.dictionary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.VocabEntry
import com.ziaee.zabankhan.data.VocabList
import com.ziaee.zabankhan.lang.LanguageSpec
import com.ziaee.zabankhan.resources.Res
import com.ziaee.zabankhan.resources.own_listen
import com.ziaee.zabankhan.resources.own_dictionary_word_family
import com.ziaee.zabankhan.ui.reader.partOfSpeechResource
import com.ziaee.zabankhan.resources.own_compact_all_meanings
import com.ziaee.zabankhan.resources.own_compact_examples
import com.ziaee.zabankhan.resources.own_compact_expressions
import com.ziaee.zabankhan.resources.own_compact_conjugation
import com.ziaee.zabankhan.resources.own_compact_forms
import com.ziaee.zabankhan.resources.own_frequency_common
import com.ziaee.zabankhan.resources.own_frequency_very_common
import com.ziaee.zabankhan.ui.contentStyle
import com.ziaee.zabankhan.ui.TranslationSourceIcon
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactEntryCard(
    entry: Entry, meanings: List<SenseMeaning>, lang: LanguageSpec, target: String,
    vm: AppViewModel, textId: Long, sentence: String, speak: (String) -> Unit,
    onLookup: (String) -> Unit, vocabEntries: List<VocabEntry>, lists: List<VocabList>,
    via: List<DictionaryRedirect> = emptyList(), initiallyExpanded: Boolean = true,
    wordFamily: @Composable () -> Unit,
) {
    var open by rememberSaveable(entry.id) { mutableStateOf(initiallyExpanded) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!initiallyExpanded) {
                Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val summary = listOf(entry.word, entry.pos).filter(String::isNotBlank).joinToString(" · ")
                    Text(summary, Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(summary)))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) { CompactEntryContents(entry, meanings, lang, target, vm, textId, sentence, speak, onLookup, vocabEntries, lists, via, wordFamily) }
            } else CompactEntryContents(entry, meanings, lang, target, vm, textId, sentence, speak, onLookup, vocabEntries, lists, via, wordFamily)
        }
    }
}

@Composable
private fun CompactEntryContents(
    entry: Entry, meanings: List<SenseMeaning>, lang: LanguageSpec, target: String,
    vm: AppViewModel, textId: Long, sentence: String, speak: (String) -> Unit,
    onLookup: (String) -> Unit, vocabEntries: List<VocabEntry>, lists: List<VocabList>, via: List<DictionaryRedirect>,
    wordFamily: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(entry.word, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.merge(contentStyle(entry.word)), maxLines = 1)
        if (entry.ipa.isNotBlank()) Text(entry.ipa, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(entry.ipa)), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        IconButton(onClick = { speak(entry.word) }) { Icon(Icons.Default.VolumeUp, stringResource(Res.string.own_listen)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val posGender = listOf(entry.pos, entry.gender).filter(String::isNotBlank).joinToString(" · ")
        if (posGender.isNotBlank()) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) { Text(posGender, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall.merge(contentStyle(posGender))) }
        if (entry.freq > 0) Text(stringResource(if (entry.freq >= 4) Res.string.own_frequency_very_common else Res.string.own_frequency_common), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    via.firstOrNull()?.let { redirect ->
        val resolution = "${redirect.form} → ${redirect.lemma} · ${redirect.labels.joinToString("; ")}"
        Text(resolution, style = MaterialTheme.typography.bodySmall.merge(contentStyle(resolution)))
    }
    val allExamples = entry.senses.flatMap { it.examples } + entry.moreExamples.map { Example(it, "", "") }
    val previewExample = entry.senses.firstNotNullOfOrNull { it.examples.firstOrNull() }
        ?: entry.moreExamples.firstOrNull()?.let { Example(it, "", "") }
    val additionalExamples = allExamples.toMutableList().also { if (previewExample != null) it.remove(previewExample) }
    entry.senses.take(3).forEachIndexed { index, sense ->
        val meaning = meanings.getOrNull(index)?.text ?: sense.en
        Row(verticalAlignment = Alignment.Top) {
            Text("${index + 1}. ", style = MaterialTheme.typography.bodyMedium)
            Text(meaning, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.merge(contentStyle(meaning)))
            meanings.getOrNull(index)?.translation?.let { TranslationSourceIcon(it) }
            OwnDictionarySenseSaveControl(entry, sense, index, meaning, lang, target, vm, textId, sentence, vocabEntries, lists)
        }
    }
    wordFamily()
    bestExampleContent(entry, target, speak)
    Column(Modifier.fillMaxWidth()) {
      Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
    ExpandableSection(stringResource(Res.string.own_compact_all_meanings, entry.senses.size), entry.senses.size, entry.id, "meanings", icon = Icons.AutoMirrored.Outlined.List) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entry.senses.forEachIndexed { index, sense ->
                val meaning = meanings.getOrNull(index)?.text ?: sense.en
                Row(verticalAlignment = Alignment.Top) {
                    Text("${index + 1}. ", style = MaterialTheme.typography.bodyMedium)
                    Text(meaning, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.merge(contentStyle(meaning)))
                    meanings.getOrNull(index)?.translation?.let { TranslationSourceIcon(it) }
                    OwnDictionarySenseSaveControl(entry, sense, index, meaning, lang, target, vm, textId, sentence, vocabEntries, lists)
                }
            }
        }
    }
    if (additionalExamples.isNotEmpty()) ExpandableSection(stringResource(Res.string.own_compact_examples, additionalExamples.size), additionalExamples.size, entry.id, "examples", icon = Icons.Outlined.FormatQuote) {
        Column { additionalExamples.forEach { example ->
            Text(example.text, Modifier.clickable { speak(example.text) }, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(example.text)).copy(fontStyle = FontStyle.Italic))
            val translation = example.fa.ifBlank { example.en }
            if (translation.isNotBlank()) Text(translation, style = MaterialTheme.typography.bodySmall.merge(contentStyle(translation)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
    if (entry.expressions.isNotEmpty()) ExpandableSection(stringResource(Res.string.own_compact_expressions, entry.expressions.size), entry.expressions.size, entry.id, "expressions", icon = Icons.Outlined.Lightbulb) {
        Column { entry.expressions.forEach { TextButton(onClick = { onLookup(it) }) { Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium.merge(contentStyle(it))) } } }
    }
    if (entry.forms.isNotEmpty()) ExpandableSection(stringResource(if (entry.pos.contains("verb", true)) Res.string.own_compact_conjugation else Res.string.own_compact_forms, entry.forms.values.sumOf { it.size }), entry.forms.values.sumOf { it.size }, entry.id, "forms", icon = Icons.Outlined.TableChart) {
        OwnDictionaryEntryCard(entry, meanings, target, speak, onLookup)
    }
        }
      }
    }
}

@Composable
internal fun WordFamilySection(
    entryId: Long, relatives: List<Entry>, lang: LanguageSpec, target: String,
    onLookup: (DictionaryHeadword) -> Unit, expanded: Boolean, onExpandChange: (Boolean) -> Unit,
) {
    val shownPos = listOf("noun", "adjective", "verb", "adverb")
    val shown = relatives.filter { it.pos in shownPos }
    if (shown.isEmpty()) return
    ExpandableSection(stringResource(Res.string.own_dictionary_word_family), shown.size, entryId, "word-family",
        expanded = expanded, onExpandChange = onExpandChange) {
        val byPos = shown.groupBy { it.pos }
        for (pos in shownPos) {
            val group = byPos[pos] ?: continue
            val label = partOfSpeechResource(pos)?.let { stringResource(it) } ?: pos
            Text(label, style = MaterialTheme.typography.labelMedium.merge(contentStyle(label)))
            group.forEach { relative ->
                val article = lang.dictionaryNounArticles[relative.gender].takeIf { pos == "noun" }
                val word = listOfNotNull(article, relative.word).joinToString(" ")
                val meaning = relative.wordFamilyMeaning(target, lang)
                TextButton(onClick = { onLookup(DictionaryHeadword(relative.word, relative.pos)) },
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(word, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(word)))
                        if (meaning.isNotBlank()) Text(meaning, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall.merge(contentStyle(meaning)))
                    }
                }
            }
        }
    }
}

@Composable
private fun bestExampleContent(entry: Entry, target: String, speak: (String) -> Unit) {
    val example = entry.senses.firstNotNullOfOrNull { it.examples.firstOrNull() } ?: entry.moreExamples.firstOrNull()?.let { Example(it, "", "") } ?: return
    Column(Modifier.clickable { speak(example.text) }) {
        Text(example.text, style = MaterialTheme.typography.bodyMedium.merge(contentStyle(example.text)).copy(fontStyle = FontStyle.Italic))
        val translation = when (target) { "fa" -> example.fa.ifBlank { example.en }; "en" -> example.en; else -> example.fa.ifBlank { example.en } }
        if (translation.isNotBlank()) Text(translation, style = MaterialTheme.typography.bodySmall.merge(contentStyle(translation)), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExpandableSection(title: String, count: Int, entryId: Long, section: String, forceExpanded: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.Lightbulb, expanded: Boolean? = null,
    onExpandChange: ((Boolean) -> Unit)? = null, content: @Composable () -> Unit,
) {
    var localExpanded by rememberSaveable(entryId, section) { mutableStateOf(section != "forms") }
    val isExpanded = expanded ?: localExpanded
    Column {
        if (section != "meanings") HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        val targetExpanded = isExpanded || forceExpanded
        val rotation by animateFloatAsState(if (targetExpanded) 180f else 0f)
        val stateLabel = if (targetExpanded) "Expanded" else "Collapsed"
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) {
            if (onExpandChange == null) localExpanded = !isExpanded else onExpandChange(!isExpanded)
        }.semantics { role = Role.Button; stateDescription = stateLabel }.heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall.merge(contentStyle(title)))
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) { Text(count.toString(), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall) }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(targetExpanded, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) { content() }
        }
    }
}
