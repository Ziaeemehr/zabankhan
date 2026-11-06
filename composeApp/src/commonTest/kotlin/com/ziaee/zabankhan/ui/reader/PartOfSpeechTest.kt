package com.ziaee.zabankhan.ui.reader

import com.ziaee.zabankhan.resources.*
import kotlin.test.*

class PartOfSpeechTest {
    @Test fun knownPartsOfSpeechMapCaseInsensitively() {
        val resources = listOf(
            "noun" to Res.string.pos_noun, "verb" to Res.string.pos_verb,
            "adjective" to Res.string.pos_adjective, "adverb" to Res.string.pos_adverb,
            "pronoun" to Res.string.pos_pronoun, "preposition" to Res.string.pos_preposition,
            "conjunction" to Res.string.pos_conjunction, "interjection" to Res.string.pos_interjection,
            "article" to Res.string.pos_article, "abbreviation" to Res.string.pos_abbreviation,
            "phrase" to Res.string.pos_phrase, "prefix" to Res.string.pos_prefix,
            "suffix" to Res.string.pos_suffix, "particle" to Res.string.pos_particle,
            "numeral" to Res.string.pos_numeral,
        )
        resources.forEach { (raw, resource) ->
            assertEquals(resource, partOfSpeechResource(raw))
            assertEquals(resource, partOfSpeechResource(raw.uppercase()))
        }
    }

    @Test fun unknownPartsOfSpeechUseRawFallback() {
        assertNull(partOfSpeechResource("unknown category"))
        assertNull(partOfSpeechResource(""))
    }
}
