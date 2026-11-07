package com.ziaee.zabankhan.translate.prepare

import com.ziaee.zabankhan.translate.llm.meaningCacheKey
import com.ziaee.zabankhan.translate.llm.translationCacheKey

enum class PreparationKind { LINE, MEANING }

data class PreparationItem(
    val text: String,
    val language: String,
    val kind: PreparationKind = PreparationKind.LINE,
    val before: String? = null,
    val after: String? = null,
    val group: String = "",
    val position: Int = 0,
    val sentence: String? = null,
    val deckId: Long? = null
) {
    val identity get() = if (kind == PreparationKind.MEANING)
        meaningCacheKey(model = "", source = "", target = language, word = text, sentence = sentence)
        else translationCacheKey(model = "", source = "", target = language, text = text)
}

data class PreparationDeck(val id: Long?, val title: String, val cardCount: Int)
data class PreparationSource(val id: String, val title: String, val items: List<PreparationItem>, val decks: List<PreparationDeck> = emptyList())

fun selectedItems(sources: List<PreparationSource>, selected: Set<String>, selectedDecks: Set<Long?>? = null): List<PreparationItem> =
    sources.filter { it.id in selected }.flatMap { source ->
        if (source.id in setOf("meanings", "examples") && selectedDecks != null) source.items.filter { it.deckId in selectedDecks } else source.items
    }.filter { it.text.isNotBlank() }.groupBy { it.identity }.values.flatMap { duplicates ->
        val example = duplicates.firstOrNull { it.kind == PreparationKind.LINE && it.group.startsWith("example:") }
        if (example == null) listOf(duplicates.first()) else listOfNotNull(example, duplicates.firstOrNull { !it.group.startsWith("example:") })
    }

fun pendingItems(items: List<PreparationItem>, cached: (PreparationItem) -> Boolean) = items.filterNot(cached)

fun preparationBatches(items: List<PreparationItem>, maxLines: Int = 3): List<List<PreparationItem>> {
    require(maxLines > 0)
    val batches = mutableListOf<MutableList<PreparationItem>>()
    for (item in items) {
        val batch = batches.lastOrNull(); val last = batch?.lastOrNull()
        if (batch != null && last != null && batch.size < maxLines && item.kind == PreparationKind.LINE && last.kind == PreparationKind.LINE && item.group == last.group && item.language == last.language && item.position == last.position + 1 && batch.sumOf { it.text.length } + item.text.length <= 6000) batch.add(item)
        else batches.add(mutableListOf(item))
    }
    return batches
}

fun splitPreparationBatch(items: List<PreparationItem>): List<List<PreparationItem>> = when {
    items.isEmpty() -> emptyList(); items.size == 1 -> listOf(items); else -> listOf(items.take(items.size / 2), items.drop(items.size / 2))
}

data class PreparationEstimate(val seconds: Long, val bytes: Long)
fun estimatePreparation(items: List<PreparationItem>) = PreparationEstimate(
    preparationBatches(items).sumOf { batch -> when { batch.first().kind == PreparationKind.MEANING -> 1L; batch.size > 1 -> batch.size * 2L; else -> 3L } },
    items.sumOf { it.text.encodeToByteArray().size * 2L + 128L }
)
