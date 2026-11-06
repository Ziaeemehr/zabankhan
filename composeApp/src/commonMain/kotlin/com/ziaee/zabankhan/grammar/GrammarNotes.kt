package com.ziaee.zabankhan.grammar

import kotlinx.serialization.json.*
import com.ziaee.zabankhan.lang.LanguageSpec

data class HighlightRange(val start: Int, val end: Int)
data class GrammarCell(val text: String, val highlights: List<HighlightRange> = emptyList())
data class GrammarParagraph(val kind: String, val text: String)
data class GrammarExample(val id: String, val learning: GrammarCell, val fa: String)
sealed interface GrammarBlock {
    data class Prose(val type: String, val markdown: List<GrammarParagraph>) : GrammarBlock
    data class Structure(val patterns: List<String>, val text: List<GrammarParagraph>) : GrammarBlock
    data class Examples(val items: List<GrammarExample>) : GrammarBlock
    data class Mistake(val wrong: String, val right: String, val why: String) : GrammarBlock
    data class Infographic(val kind: String, val header: List<GrammarCell>, val rows: List<List<GrammarCell>>) : GrammarBlock
}
data class GrammarNote(
    val id: String, val group: String, val level: String,
    val titleLearning: String, val titleFa: String,
    val aliasesLearning: List<String>, val aliasesFa: List<String>, val related: List<String>,
    val blocks: List<GrammarBlock>,
    val titleEnglish: String = titleLearning
)

fun parseGrammarNotes(json: String, language: LanguageSpec): List<GrammarNote> {
    val root = Json.parseToJsonElement(json).jsonObject
    require(root.int("version") == 1)
    return root.array("notes").indexedMap { index ->
        runCatching { parseNote(root.array("notes").obj(index), language) }.getOrNull()
    }.filterNotNull()
}

private fun parseNote(note: JsonObject, language: LanguageSpec): GrammarNote {
    val group = note.string("group")
    require(group in setOf("basics", "tenses", "pronouns", "structures", "comparisons"))
    val blocks = note.array("blocks").indexedMap { index ->
        val block = note.array("blocks").obj(index)
        when (val type = block.string("type")) {
            "summary", "usage", "more" -> GrammarBlock.Prose(type, block.array("markdown").paragraphs())
            "structure" -> GrammarBlock.Structure(block.array("patterns").strings(), block.array("text").paragraphs())
            "examples" -> GrammarBlock.Examples(block.array("items").indexedMap { i ->
                val example = block.array("items").obj(i)
                GrammarExample(example.string("id"), example.cell(language.code), example.string("fa"))
            })
            "mistake" -> GrammarBlock.Mistake(block.string("wrong"), block.string("right"), block.string("why"))
            "infographic" -> {
                val header = block.array("header").cells()
                val rows = block.array("rows").indexedMap { i -> block.array("rows").array(i).cells() }
                require(header.isNotEmpty() && rows.all { it.size == header.size })
                GrammarBlock.Infographic(block.string("kind"), header, rows)
            }
            else -> error("Unknown block: $type")
        }
    }
    require(listOf("summary", "usage").all { type -> blocks.any { it is GrammarBlock.Prose && it.type == type } })
    require(blocks.any { it is GrammarBlock.Structure } && blocks.any { it is GrammarBlock.Examples })
    return GrammarNote(note.string("id"), group, note.string("level"), note.string("title${language.grammarFieldSuffix}"),
        note.string("titleFa"), note.array("aliases${language.grammarFieldSuffix}").strings(), note.array("aliasesFa").strings(),
        note.array("related").strings(), blocks,
        note["titleEn"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: note.string("title${language.grammarFieldSuffix}"))
}

private fun JsonObject.cell(key: String = "text"): GrammarCell {
    val text = string(key)
    val ranges = array("highlights").indexedMap { i ->
        val range = array("highlights").array(i)
        require(range.size == 2)
        HighlightRange(range.int(0), range.int(1)).also {
            require(it.start >= 0 && it.end > it.start && it.end <= text.length)
        }
    }
    return GrammarCell(text, ranges)
}
private fun JsonArray.cells() = indexedMap { obj(it).cell() }
private fun JsonArray.strings() = indexedMap { string(it) }
private fun JsonArray.paragraphs() = indexedMap {
    val paragraph = obj(it)
    val kind = paragraph.string("kind")
    require(kind == "p" || kind == "li")
    GrammarParagraph(kind, paragraph.string("text"))
}
private fun <T> JsonArray.indexedMap(transform: (Int) -> T): List<T> = (0 until size).map(transform)

private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.int
private fun JsonObject.array(key: String) = getValue(key).jsonArray
private fun JsonArray.array(index: Int) = get(index).jsonArray
private fun JsonArray.obj(index: Int) = get(index).jsonObject
private fun JsonArray.string(index: Int) = get(index).jsonPrimitive.content
private fun JsonArray.int(index: Int) = get(index).jsonPrimitive.int

/** Version-one packs name learning-language fields with their language-code suffix. */
internal val LanguageSpec.grammarFieldSuffix: String get() = code.replaceFirstChar { it.uppercase() }
