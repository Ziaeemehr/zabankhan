package com.ziaee.zabankhan.translate.prepare

import com.ziaee.zabankhan.translate.paragraphTranslationContext

data class PreparationParagraph(val text: String, val previous: String? = null, val next: String? = null)

fun libraryPreparationSource(id: String, title: String, paragraphs: List<PreparationParagraph>, language: String) =
    PreparationSource(id, title, paragraphs.mapIndexed { index, p ->
        PreparationItem(p.text, language, before = paragraphTranslationContext(p.text, p.previous), after = p.next, group = "$id:$index", position = index)
    }.filter { it.text.isNotBlank() })

fun subtitlePreparationSource(id: String, title: String, sentences: List<String>, language: String) =
    PreparationSource(id, title, sentences.mapIndexed { index, sentence ->
        PreparationItem(sentence, language, before = sentences.getOrNull(index - 1), after = sentences.getOrNull(index + 1), group = id, position = index)
    }.filter { it.text.isNotBlank() })

data class PreparationCard(val word: String, val sentence: String, val deckId: Long?)
fun flashcardPreparationSources(cards: List<PreparationCard>, language: String, meaningsTitle: String, examplesTitle: String, decks: List<PreparationDeck> = emptyList(), unfiledTitle: String = ""): List<PreparationSource> {
    val allDecks = decks + PreparationDeck(null, unfiledTitle, cards.count { it.deckId == null })
    return listOf(
        PreparationSource("meanings", meaningsTitle, cards.map { PreparationItem(it.word, language, PreparationKind.MEANING, sentence = it.sentence, deckId = it.deckId) }, allDecks),
        PreparationSource("examples", examplesTitle, cards.filter { it.sentence.isNotBlank() }.mapIndexed { index, it -> PreparationItem(it.sentence, language, group = "example:$index", deckId = it.deckId) }, allDecks)
    )
}
