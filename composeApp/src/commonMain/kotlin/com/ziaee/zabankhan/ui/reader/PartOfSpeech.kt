package com.ziaee.zabankhan.ui.reader

import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.StringResource

internal fun partOfSpeechResource(value: String): StringResource? = when (value.lowercase()) {
    "noun" -> Res.string.pos_noun
    "verb" -> Res.string.pos_verb
    "adjective" -> Res.string.pos_adjective
    "adverb" -> Res.string.pos_adverb
    "pronoun" -> Res.string.pos_pronoun
    "preposition" -> Res.string.pos_preposition
    "conjunction" -> Res.string.pos_conjunction
    "interjection" -> Res.string.pos_interjection
    "article" -> Res.string.pos_article
    "abbreviation" -> Res.string.pos_abbreviation
    "phrase" -> Res.string.pos_phrase
    "prefix" -> Res.string.pos_prefix
    "suffix" -> Res.string.pos_suffix
    "particle" -> Res.string.pos_particle
    "numeral" -> Res.string.pos_numeral
    else -> null
}
