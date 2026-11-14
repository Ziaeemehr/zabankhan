package com.ziaee.zabankhan.content

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.*
import kotlinx.coroutines.flow.first

/** The active learning language is authoritative; file metadata cannot override it. */
class ImportRepository(private val db: AppDatabase, private val spec: LanguageSpec, private val meaningLang: String) {
    init { require(spec in Languages.learnable) { "Not a learning language" } }
    suspend fun text(raw: String, fileName: String): Long {
        val parsed = parseFrontMatter(raw)
        val body = parsed.body.trim()
        require(body.isNotBlank()) { "No readable text" }
        return db.texts().insert(TextDocument(lang = spec.code, title = parsed.title ?: fileName.substringBeforeLast('.'), body = body, level = parsed.level))
    }
    suspend fun epub(bytes: ByteArray): Long = db.useWriterConnection { connection -> connection.immediateTransaction {
        val book = EpubReader.read(bytes)
        val folderId = db.texts().insertFolder(LibraryFolder(lang = spec.code, name = book.title))
        var first = 0L
        book.chapters.forEach { chapter ->
            val id = db.texts().insert(TextDocument(lang = spec.code, title = chapter.title, body = chapter.text, folderId = folderId, sourceName = book.title))
            if (first == 0L) first = id
        }
        first
    } }
    suspend fun csv(raw: String, fallbackDeck: String): WordImportResult = words(raw, false, fallbackDeck, false)
    /** French Reader's CSV target chooser: deck column, existing deck, or a new deck name. */
    suspend fun csv(file: CsvWordFile, target: CsvImportTarget): WordImportResult = db.useWriterConnection { connection -> connection.immediateTransaction {
        val lists = db.vocab().observeLists(spec.code).first()
        val nameById = lists.associate { it.id to it.name.lowercase() }
        val keys = db.vocab().getAll(spec.code).mapNotNull { e -> nameById[e.listId]?.let { it to e.word.lowercase() } }.toSet()
        val ids = lists.associate { it.name.lowercase() to it.id }.toMutableMap()
        val plan = planCsvImport(file, target, keys)
        val now = nowMs()
        for ((index, item) in plan.words.withIndex()) {
            val deckId = ids[item.deck.lowercase()] ?: db.vocab().insertList(VocabList(lang = spec.code, name = item.deck))
                .also { ids[item.deck.lowercase()] = it }
            db.vocab().insert(VocabEntry(lang = spec.code, meaningLang = meaningLang, word = item.word.word,
                meaning = item.word.meaning, sentence = item.word.sentence, listId = deckId,
                createdAtMs = now + index, nextReviewAtMs = now))
        }
        WordImportResult(plan.words.size, plan.skipped + file.malformedRows, plan.deckNames.count { name -> lists.none { it.name.equals(name, ignoreCase = true) } })
    } }
    suspend fun anki(raw: String, keepProgress: Boolean): WordImportResult = words(raw, true, "", keepProgress)
    private suspend fun words(raw: String, anki: Boolean, fallback: String, keep: Boolean): WordImportResult = db.useWriterConnection { connection -> connection.immediateTransaction {
        val lists = db.vocab().observeLists(spec.code).first()
        val nameById = lists.associate { it.id to it.name.lowercase() }
        val keys = db.vocab().getAll(spec.code).mapNotNull { e -> nameById[e.listId]?.let { it to e.word.lowercase() } }.toSet()
        val ids = lists.associate { it.name.lowercase() to it.id }.toMutableMap()
        suspend fun deck(name: String): Long = ids[name.lowercase()] ?: db.vocab().insertList(VocabList(lang = spec.code, name = name)).also { ids[name.lowercase()] = it }
        val now = nowMs()
        if (anki) {
            val export = parseAnkiExport(raw)
            val plan = planAnkiImport(export, keys)
            for (w in plan.words) db.vocab().insert(buildVocabEntry(w.card, w.mapped, deck(w.listName), keep, export.todayDay, now, spec.code, meaningLang))
            WordImportResult(plan.words.size, plan.skipped, plan.words.map { it.listName.lowercase() }.distinct().count { name -> lists.none { it.name.lowercase() == name } })
        } else {
            val file = parseCsvWords(raw)
            val plan = planCsvImport(file, CsvImportTarget.DeckColumn(fallback), keys)
            plan.words.forEachIndexed { i, w -> db.vocab().insert(VocabEntry(lang = spec.code, meaningLang = meaningLang, word = w.word.word, meaning = w.word.meaning, sentence = w.word.sentence, listId = deck(w.deck), createdAtMs = now + i, nextReviewAtMs = now)) }
            WordImportResult(plan.words.size, plan.skipped + file.malformedRows, plan.deckNames.count { name -> lists.none { it.name.equals(name, ignoreCase = true) } })
        }
    } }
}
data class WordImportResult(val imported: Int, val skipped: Int, val decks: Int = 0)

fun markdownToPlainText(markdown: String): String {
    val lines = markdown.lines()
    val output = mutableListOf<String>()
    val paragraph = mutableListOf<String>()
    fun flush() { if (paragraph.isNotEmpty()) { output += paragraph.joinToString(" "); paragraph.clear() } }
    for (line in lines) {
        val clean = line.trim()
        if (clean.isEmpty()) { flush(); continue }
        val heading = Regex("^#{1,6}\\s+(.*?)(?:\\s+#+)?$").matchEntire(clean)
        val value = heading?.groupValues?.get(1) ?: clean.replace(Regex("^[-*+]\\s+"), "")
        val plain = value.replace(Regex("!?\\[([^]]*)]\\([^)]+\\)"), "$1").replace(Regex("[*_`]+"), "")
        if (heading != null) { flush(); output += plain } else paragraph += plain
    }
    flush()
    return output.joinToString("\n\n")
}
