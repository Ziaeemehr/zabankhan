package com.ziaee.zabankhan.grammar

import com.ziaee.zabankhan.lang.LanguageSpec
import kotlinx.serialization.json.*

/** Poster titles are listed until a PDF viewer is available, matching French Reader. */
data class GrammarPoster(val id: String, val titleLearning: String, val titleFa: String, val file: String, val notes: List<String>, val titleEnglish: String = titleLearning)

fun parseGrammarPosters(json: String, language: LanguageSpec): List<GrammarPoster> = runCatching {
    val root = Json.parseToJsonElement(json).jsonObject
    require(root.getValue("version").jsonPrimitive.int == 1)
    root.getValue("posters").jsonArray.mapNotNull { element ->
        runCatching {
            val poster = element.jsonObject
            fun string(key: String) = poster.getValue(key).jsonPrimitive.content
            GrammarPoster(
                id = string("id"), titleLearning = string("title${language.grammarFieldSuffix}"),
                titleFa = string("titleFa"), file = string("file"),
                notes = poster["notes"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
                titleEnglish = poster["titleEn"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                    ?: string("title${language.grammarFieldSuffix}")
            )
        }.getOrNull()
    }
}.getOrDefault(emptyList())
