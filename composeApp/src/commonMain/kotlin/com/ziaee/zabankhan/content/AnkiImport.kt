package com.ziaee.zabankhan.content

import com.ziaee.zabankhan.data.VocabEntry
import com.ziaee.zabankhan.srs.VocabSrs
import kotlinx.serialization.json.*
import kotlinx.datetime.TimeZone

data class AnkiCard(
    val deck: String, val front: String, val back: String,
    val type: Int, val interval: Int, val due: Int, val reps: Int, val lapses: Int
)
data class AnkiExport(val todayDay: Int?, val cards: List<AnkiCard>)
data class MappedAnkiCard(val word: String, val meaning: String?, val sentence: String)
data class PlannedAnkiWord(val listName: String, val card: AnkiCard, val mapped: MappedAnkiCard)
data class AnkiImportPlan(val newListNames: List<String>, val words: List<PlannedAnkiWord>, val skipped: Int)

private val SOUND = Regex("""\[sound:[^\]]*]""")
private val BREAKS = Regex("""<\s*(br|/div|/p)\s*/?>""", RegexOption.IGNORE_CASE)
private val TAGS = Regex("<[^>]+>")
private val GLOSS = Regex("""^(EN|FA):\s*(.*)$""")

internal fun parseAnkiExport(json: String): AnkiExport {
    val root = Json.parseToJsonElement(json).jsonObject
    require(root["version"]?.jsonPrimitive?.intOrNull == 1) { "Unsupported Anki export version" }
    val cards = root.getValue("cards").jsonArray.map { element ->
        val c = element.jsonObject
        fun string(key: String) = c.getValue(key).jsonPrimitive.content
        fun int(key: String) = c.getValue(key).jsonPrimitive.int
        AnkiCard(string("deck"), string("front"), string("back"), int("type"), int("interval"), int("due"), int("reps"), int("lapses"))
    }
    return AnkiExport(root["todayDay"]?.jsonPrimitive?.intOrNull, cards)
}

internal fun cleanAnkiText(html: String): String {
    val text = TAGS.replace(BREAKS.replace(SOUND.replace(html, ""), "\n"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&amp;", "&")
    return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}

internal fun mapAnkiCard(card: AnkiCard): MappedAnkiCard? {
    val word = cleanAnkiText(card.front).lines().joinToString(" ").trim()
    if (word.isEmpty()) return null
    val lines = cleanAnkiText(card.back).lines().filter { it.isNotEmpty() }
    val gloss = lines.mapNotNull { GLOSS.matchEntire(it)?.groupValues?.get(2)?.takeIf { v -> v.isNotBlank() } }
    if (gloss.isEmpty()) {
        return MappedAnkiCard(word, lines.joinToString("\n").ifEmpty { null }, "")
    }
    val sentence = lines.filter { GLOSS.matchEntire(it) == null }.joinToString(" ")
    return MappedAnkiCard(word, gloss.joinToString("\n"), sentence)
}

internal fun boxForInterval(days: Int): Int = when {
    days >= 16 -> 5
    days >= 8 -> 4
    days >= 4 -> 3
    days >= 2 -> 2
    else -> 1
}

internal fun buildVocabEntry(
    card: AnkiCard, mapped: MappedAnkiCard, listId: Long, keepProgress: Boolean,
    todayDay: Int?, nowMs: Long, lang: String, meaningLang: String = "fa", zone: TimeZone = TimeZone.currentSystemDefault()
): VocabEntry {
    val base = VocabEntry(
        lang = lang, meaningLang = meaningLang, word = mapped.word, sentence = mapped.sentence,
        meaning = mapped.meaning, createdAtMs = nowMs, listId = listId,
        leitnerBox = 1, nextReviewAtMs = nowMs, lastReviewedAtMs = null
    )
    if (!keepProgress) return base
    return when (card.type) {
        2 -> {
            val box = boxForInterval(card.interval)
            val dueMs = if (todayDay != null) {
                VocabSrs.startOfDayMs(nowMs, zone) + (card.due - todayDay) * VocabSrs.DAY_MS
            } else {
                VocabSrs.dueAtMs(box, nowMs, VocabSrs.DEFAULT_INTERVAL_DAYS, zone)
            }
            base.copy(leitnerBox = box, nextReviewAtMs = dueMs, lastReviewedAtMs = nowMs, learned = box >= 5)
        }
        1, 3 -> base.copy(lastReviewedAtMs = nowMs)
        else -> base
    }
}

internal fun planAnkiImport(export: AnkiExport, existingWordKeys: Set<Pair<String, String>>): AnkiImportPlan {
    val seen = existingWordKeys.toMutableSet()
    val words = mutableListOf<PlannedAnkiWord>()
    val listNames = linkedSetOf<String>()
    var skipped = 0
    for (card in export.cards) {
        val mapped = mapAnkiCard(card)
        val key = mapped?.let { card.deck.lowercase() to it.word.lowercase() }
        if (mapped == null || key in seen) { skipped++; continue }
        seen += key!!
        listNames += card.deck
        words += PlannedAnkiWord(card.deck, card, mapped)
    }
    return AnkiImportPlan(listNames.toList(), words, skipped)
}
