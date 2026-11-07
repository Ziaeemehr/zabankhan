package com.ziaee.zabankhan.ui

import androidx.compose.runtime.Composable
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*

@Composable
fun WordsScreen(vm: AppViewModel, settings: AppSettings, review: () -> Unit = {},
    onOpenDataset: () -> Unit = {}, reviewScope: ((Long) -> Unit)? = null) =
    androidx.compose.runtime.key(settings.learningLang) {
        VocabListScreen(vm, settings, onBack = {}, onOpenReview = { reviewScope?.invoke(it) ?: review() },
            onOpenDataset = onOpenDataset, asTab = true)
    }

@Composable
internal fun EditWordDialog(entry: VocabEntry, decks: List<Pair<Long, String>>, dismiss: () -> Unit, save: (VocabEntry) -> Unit) {
    VocabEditDialog(entry, dismiss) { word, meaning, sentence ->
        save(entry.copy(word = word, meaning = meaning, sentence = sentence)); dismiss()
    }
}

sealed interface WordsDueBucket {
    data object Learned : WordsDueBucket
    data object Today : WordsDueBucket
    data class InDays(val days: Long) : WordsDueBucket
}

fun wordsDueBucket(nextReviewAtMs: Long, nowMs: Long, learned: Boolean = false): WordsDueBucket {
    if (learned) return WordsDueBucket.Learned
    if (nextReviewAtMs <= nowMs) return WordsDueBucket.Today
    val days = kotlin.math.ceil((nextReviewAtMs - nowMs).toDouble() / 86_400_000.0).toLong()
    return WordsDueBucket.InDays(days.coerceAtLeast(1))
}
