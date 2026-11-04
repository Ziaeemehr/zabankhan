package com.ziaee.zabankhan.backup

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.content.ZipArchive
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.lang.Languages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random

data class MigrationSummary(val texts: Int, val words: Int, val highlights: Int, val reviews: Int, val skipped: Int, val missingPdfs: Int = 0)
data class FrenchReaderSnapshot(
    val texts: List<TextDocument>, val folders: List<LibraryFolder>, val words: List<VocabEntry>,
    val lists: List<VocabList>, val highlights: List<HighlightEntry>, val reviews: List<ReviewLogEntry>,
)

private class LegacyRow(private val statement: SQLiteStatement) {
    private val columns = (0 until statement.getColumnCount()).associateBy { statement.getColumnName(it) }
    fun text(name: String): String? = columns[name]?.takeUnless { statement.isNull(it) }?.let(statement::getText)
    fun long(name: String): Long? = columns[name]?.takeUnless { statement.isNull(it) }?.let(statement::getLong)
    fun number(name: String, default: Long = 0): Long = long(name) ?: default
}
private fun <T> SQLiteConnection.rows(table: String, read: (LegacyRow) -> T): List<T> {
    val exists = prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?").use {
        it.bindText(1, table); it.step()
    }
    if (!exists) return emptyList()
    return prepare("SELECT * FROM $table ORDER BY id").use { statement -> buildList {
        while (statement.step()) add(read(LegacyRow(statement)))
    } }
}

/** Absolute old Android sandbox paths are resolved only inside the already validated ZIP. */
internal fun legacyFile(entries: Map<String, ByteArray>, oldPath: String?, root: String): ByteArray? {
    if (oldPath.isNullOrBlank()) return null
    val normalized = oldPath.replace('\\', '/')
    if (normalized.split('/').any { it == ".." }) return null
    val relative = when {
        normalized.startsWith("$root/") -> normalized
        normalized.contains("/$root/") -> "$root/" + normalized.substringAfterLast("/$root/")
        '/' !in normalized -> "$root/$normalized"
        else -> return null
    }
    return entries[relative]
}

/** The source connection must be read-only. Missing optional columns support older backups. */
fun readFrenchReaderSnapshot(
    database: SQLiteConnection,
    entries: Map<String, ByteArray> = emptyMap(),
    copyPdf: (oldPath: String, bytes: ByteArray) -> String = { _, _ -> error("PDF destination required") },
): FrenchReaderSnapshot {
    val lang = Languages.FRENCH.code
    val texts = database.rows("texts") { row ->
        val inline = row.text("rawText").orEmpty()
        val body = legacyFile(entries, row.text("bodyPath"), "text_bodies")?.decodeToString() ?: inline
        val pdf = row.text("pdfPath")?.let { path -> legacyFile(entries, path, "pdfs")?.let { copyPdf(path, it) } }
        TextDocument(id = row.number("id"), lang = lang, title = row.text("title").orEmpty(), body = body,
            createdAtMs = row.number("createdAtMs"), lastAccessedAtMs = row.number("lastAccessedAtMs"),
            folderId = row.long("folderId"), sourceUrl = row.text("sourceUrl"), sourceName = row.text("sourceName"),
            level = row.text("level"), pdfPath = pdf, pinned = row.number("pinned") != 0L, readMark = row.number("readMark").toInt())
    }
    return FrenchReaderSnapshot(
        texts = texts,
        folders = database.rows("library_folders") { row -> LibraryFolder(row.number("id"), lang, row.text("name").orEmpty(), row.long("parentId"), row.number("createdAtMs")) },
        words = database.rows("vocab") { row -> VocabEntry(
            id = row.number("id"), lang = lang, word = row.text("word").orEmpty(), sentence = row.text("sentence").orEmpty(),
            textId = row.long("textId"), meaning = row.text("meaning"), meaningLang = Languages.PERSIAN.code,
            listId = row.long("listId"), learned = row.number("learned") != 0L, createdAtMs = row.number("createdAtMs"),
            leitnerBox = row.number("leitnerBox", 1).toInt().coerceIn(1, 5), nextReviewAtMs = row.number("nextReviewAtMs"), lastReviewedAtMs = row.long("lastReviewedAtMs")) },
        lists = database.rows("vocab_lists") { row -> VocabList(row.number("id"), lang, row.text("name").orEmpty(), row.number("createdAtMs")) },
        highlights = database.rows("highlights") { row -> HighlightEntry(row.number("id"), row.number("textId"), row.number("startOffset").toInt(), row.number("endOffset").toInt(), row.text("colorKey") ?: "yellow", row.number("createdAtMs")) },
        reviews = database.rows("review_log") { row -> ReviewLogEntry(row.number("id"), row.number("entryId"), lang, row.number("timestampMs"), row.number("knew") != 0L, row.number("boxBefore", 1).toInt(), row.number("boxAfter", 1).toInt()) },
    )
}

private val importMutex = Mutex()
private const val MAX_LEGACY_BACKUP_BYTES = 256L * 1024 * 1024

suspend fun importFrenchReaderBackup(container: AppContainer, path: String): MigrationSummary = importMutex.withLock {
    withContext(Dispatchers.IO) {
        val fs = FileSystem.SYSTEM
        require((fs.metadata(path.toPath()).size ?: 0) <= MAX_LEGACY_BACKUP_BYTES) { "Backup too large" }
        val entries = ZipArchive.read(fs.read(path.toPath()) { readByteArray() }, MAX_LEGACY_BACKUP_BYTES)
        val bytes = requireNotNull(entries["database/french_reader.db"] ?: entries["french_reader.db"]) { "Missing French Reader database" }
        require(bytes.size >= 100 && bytes.copyOfRange(0, 16).decodeToString() == "SQLite format 3\u0000") { "Invalid SQLite database" }
        val root = backupPaths().files.toPath()
        val stage = root / "legacy-import-${Random.nextLong().toULong()}"
        fs.createDirectories(stage)
        val created = mutableListOf<okio.Path>()
        var committed = false
        try {
            val dbPath = stage / "french_reader.db"
            fs.write(dbPath) { write(bytes) }
            // SQLITE_OPEN_READONLY (0x1); never upgrade or alter the source database.
            val result = BundledSQLiteDriver().open(dbPath.toString(), 0x1).use { db ->
                db.prepare("PRAGMA quick_check").use { check(it.step() && it.getText(0) == "ok") { "Damaged backup" } }
                db.prepare("SELECT name FROM sqlite_master WHERE name IN ('texts','vocab')").use {
                    var count = 0; while (it.step()) count++; require(count == 2) { "Not a French Reader backup" }
                }
                val snapshot = readFrenchReaderSnapshot(db, entries) { _, pdf ->
                    val target = root / "pdfs" / "french-reader-${ZipArchive.crc32(pdf)}-${pdf.size}.pdf"
                    fs.createDirectories(target.parent!!)
                    if (!fs.exists(target)) { fs.write(target) { write(pdf) }; created += target }
                    target.toString()
                }
                val missing = db.rows("texts") { it.text("pdfPath") }.count { !it.isNullOrBlank() && legacyFile(entries, it, "pdfs") == null }
                container.database.migration().importSnapshot(snapshot).copy(missingPdfs = missing)
            }
            committed = true
            result
        } finally {
            if (!committed) created.forEach { fs.delete(it, mustExist = false) }
            fs.deleteRecursively(stage, mustExist = false)
        }
    }
}
