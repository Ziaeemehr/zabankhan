package com.ziaee.zabankhan.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.speech.*
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.style.TextDirection
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.translate.MeaningOption
import com.ziaee.zabankhan.ui.reader.MeaningOptionPills
import com.ziaee.zabankhan.srs.looksLikeScript

internal fun flashcardWordAudioText(entry: VocabEntry): String? =
    entry.word.takeIf { word -> Languages.byCode(entry.lang)?.let { looksLikeScript(word, it.script) } == true }

internal fun flashcardSentenceAudioText(entry: VocabEntry): String? = entry.sentence.takeIf { it.isNotBlank() }

@Composable
internal fun FlipCard(
    revealed: Boolean,
    onFlip: () -> Unit,
    interactive: Boolean = true,
    flipBackEnabled: Boolean = false,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(if (revealed) 180f else 0f, tween(350), label = "flip")
    val density = LocalDensity.current.density
    Card(
        Modifier.fillMaxWidth().graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }
            .clickable(
                enabled = interactive && (!revealed || flipBackEnabled),
                role = Role.Button,
                onClick = onFlip
            )
    ) {
        if (rotation <= 90f) front()
        else Box(Modifier.graphicsLayer { rotationY = 180f }) { back() }
    }
}

@Composable
private fun WordAudioButton(
    entry: VocabEntry,
    loading: Boolean,
    error: Boolean,
    interactive: Boolean,
    onPlay: () -> Unit
) {
    if (flashcardWordAudioText(entry) == null) return
    IconButton(onClick = onPlay, enabled = interactive) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.VolumeUp, stringResource(Res.string.accessibility_play_word))
        }
    }
    if (error) {
        Text(
            stringResource(Res.string.error_audio_generation),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryFlashcard(
    entry: VocabEntry,
    revealed: Boolean,
    interactive: Boolean,
    onFlip: () -> Unit,
    onPlayWord: () -> Unit,
    onPlaySentence: () -> Unit,
    wordAudioLoading: Boolean,
    wordAudioError: Boolean,
    sentenceAudioLoading: Boolean,
    sentenceAudioError: Boolean,
    sentenceTranslation: String?,
    sentenceTranslationLoading: Boolean,
    sentenceTranslationError: Boolean,
    onWordTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRetryModel: () -> Unit = {},
    onDictionary: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    flipBackEnabled: Boolean = false,
    meaningOptions: List<MeaningOption> = emptyList(),
    meaningTarget: String = Languages.PERSIAN.code,
    onMeaningSelected: (String) -> Unit = {},
) {
    var appliedMeaning by remember(entry.id, entry.word, entry.sentence, meaningTarget) { mutableStateOf<String?>(null) }
    var previousMeaning by remember(entry.id, entry.word, entry.sentence, meaningTarget) { mutableStateOf<String?>(null) }
    val language = requireNotNull(Languages.byCode(entry.lang))
    val wordTap: (String) -> Unit = { if (interactive) onWordTap(it) }
    Box(modifier) {
        FlipCard(
            revealed = revealed,
            onFlip = onFlip,
            interactive = interactive,
            flipBackEnabled = flipBackEnabled,
            front = {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(entry.word, style = MaterialTheme.typography.headlineMedium.copy(textDirection = TextDirection.Content), textAlign = TextAlign.Center)
                    WordAudioButton(entry, wordAudioLoading, wordAudioError, interactive, onPlayWord)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(Res.string.review_tap_to_reveal), style = MaterialTheme.typography.labelSmall)
                }
            },
            back = {
                Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TappableLearningText(entry.word, language, wordTap, selectable = true, style = MaterialTheme.typography.headlineMedium.merge(contentStyle(entry.word)), textAlign = TextAlign.Center)
                    WordAudioButton(entry, wordAudioLoading, wordAudioError, interactive, onPlayWord)
                    Spacer(Modifier.height(14.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp))
                    if (onDictionary != null || onEdit != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            onDictionary?.let { callback ->
                                TextButton(onClick = callback, enabled = interactive) {
                                    Icon(Icons.Default.Search, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(Res.string.vocab_open_dictionary))
                                }
                            }
                            onEdit?.let { callback ->
                                IconButton(onClick = callback, enabled = interactive) {
                                    Icon(Icons.Default.Edit, stringResource(Res.string.action_edit))
                                }
                            }
                        }
                    }
                    entry.meaning?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium.merge(contentStyle(it)))
                        Spacer(Modifier.height(8.dp))
                    }
                    if (revealed && meaningOptions.isNotEmpty()) {
                        val options = reviewMeaningOptions(meaningOptions, entry.meaning, appliedMeaning, previousMeaning)
                        MeaningOptionPills(options, appliedMeaning, meaningTarget, enabled = interactive,
                            quietMeaning = previousMeaning) { option ->
                            val replacement = replaceMeaningLine(entry.meaning, option.text, Languages.byCode(entry.lang)?.vocabularyLessonTag)
                            if (replacement.meaning != entry.meaning) {
                                previousMeaning = replacement.previousLine
                                appliedMeaning = option.text
                                onMeaningSelected(replacement.meaning)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (entry.sentence.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TappableLearningText(
                                entry.sentence,
                                language,
                                wordTap,
                                selectable = true,
                                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onPlaySentence, enabled = interactive) {
                                if (sentenceAudioLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.VolumeUp, stringResource(Res.string.accessibility_play_sentence))
                            }
                        }
                        when {
                            sentenceTranslationLoading && sentenceTranslation == null -> Text(stringResource(Res.string.translation_loading), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                            sentenceTranslationError -> Text(stringResource(Res.string.error_translation_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            sentenceTranslation != null -> Text(sentenceTranslation, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content), textAlign = TextAlign.Center)
                        }
                        if (sentenceAudioError) Text(stringResource(Res.string.error_audio_generation), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        )
    }
}
/** French Reader's flip-card front/back, wired to the shared speech/translation services. */
@Composable internal fun VocabPreviewFlashcard(entry: VocabEntry, vm: AppViewModel, settings: AppSettings, onWordTap: (String) -> Unit) {
    var revealed by remember(entry.id) { mutableStateOf(false) }
    var sentenceTranslation by remember(entry.id, entry.sentence) { mutableStateOf<String?>(null) }
    var translationLoading by remember(entry.id, entry.sentence) { mutableStateOf(false) }
    var translationError by remember(entry.id, entry.sentence) { mutableStateOf(false) }
    val tts = remember { createTts() }
    DisposableEffect(entry.id) { onDispose { tts.stop() } }
    LaunchedEffect(revealed, entry.id, settings.reviewAudioAutoplay) {
        tts.stop()
        val autoplay = settings.reviewAudioAutoplay
        when {
            !revealed && autoplay.front -> flashcardWordAudioText(entry)?.let { tts.pronounce(it, entry.lang, settings) }
            revealed && autoplay.back -> flashcardSentenceAudioText(entry)?.let { tts.pronounce(it, entry.lang, settings) }
        }
    }
    LaunchedEffect(revealed, entry.id, entry.sentence, settings.meaningLang) {
        if (revealed && entry.sentence.isNotBlank()) {
            translationLoading = true
            translationError = false
            vm.container.translations(settings).translateText(entry.sentence, entry.lang, meaningTarget(entry.lang, settings)).fold(
                onSuccess = { sentenceTranslation = it }, onFailure = { translationError = true })
            translationLoading = false
        }
    }
    EntryFlashcard(entry = entry, revealed = revealed, interactive = true,
        onFlip = { revealed = !revealed },
        onPlayWord = { tts.pronounce(entry.word, entry.lang, settings) },
        onPlaySentence = { tts.pronounce(entry.sentence, entry.lang, settings) },
        wordAudioLoading = false, wordAudioError = false,
        sentenceAudioLoading = false, sentenceAudioError = false,
        sentenceTranslation = sentenceTranslation, sentenceTranslationLoading = translationLoading,
        sentenceTranslationError = translationError, onWordTap = onWordTap, flipBackEnabled = true)
}
