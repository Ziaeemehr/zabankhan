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

data class MigrationSummary(val texts: Int, val words: Int, val highlights: Int, val reviews: Int, val skipped: Int, val missingPdfs: Int = 0, val tags: Int = 0, val examAttempts: Int = 0, val studySessions: Int = 0, val resources: Int = 0, val headlines: Int = 0)
data class FrenchReaderSnapshot(
    val texts: List<TextDocument>, val folders: List<LibraryFolder>, val words: List<VocabEntry>,
    val lists: List<VocabList>, val highlights: List<HighlightEntry>, val reviews: List<ReviewLogEntry>,
    val tags: List<LibraryTag> = emptyList(), val textTags: List<TextTagCrossRef> = emptyList(),
    val examAttempts: List<ExamAttempt> = emptyList(), val studySources: List<StudySource> = emptyList(),
    val studySessions: List<StudySession> = emptyList(), val resources: List<ResourceLink> = emptyList(),
    val headlines: List<HeadlineEntity> = emptyList(), val activity: List<ActivityLogEntry> = emptyList(),
    val shadowAttempts: List<ShadowAttempt> = emptyList(),
)

private class LegacyRow(private val statement: SQLiteStatement) {
    private val columns = (0 until statement.getColumnCount()).associateBy { statement.getColumnName(it) }
    fun text(name: String): String? = columns[name]?.takeUnless { statement.isNull(it) }?.let(statement::getText)
    fun long(name: String): Long? = columns[name]?.takeUnless { statement.isNull(it) }?.let(statement::getLong)
    fun double(name: String): Double? = text(name)?.toDoubleOrNull()
    fun number(name: String, default: Long = 0): Long = long(name) ?: default
}
private fun <T> SQLiteConnection.rows(table: String, read: (LegacyRow) -> T): List<T> {
    val exists = prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?").use {
        it.bindText(1, table); it.step()
    }
    if (!exists) return emptyList()
    return prepare("SELECT * FROM $table").use { statement -> buildList {
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
            author = row.text("author"), license = row.text("license"), publishedAt = row.long("publishedAt"),
            imagePath = row.text("imagePath")?.let { path -> legacyFile(entries, path, "text_images")?.let { copyPdf(path, it) } },
            lastChunkIndex = row.number("lastChunkIndex").toInt(), lastPositionMs = row.number("lastPositionMs"),
            voice = row.text("voice").orEmpty(), ratePercent = row.number("ratePercent").toInt(), translationLang = row.text("translationLang") ?: Languages.PERSIAN.code,
            level = row.text("level"), pdfPath = pdf, pinned = row.number("pinned") != 0L, readMark = row.number("readMark").toInt())
    }
    return FrenchReaderSnapshot(
        texts = texts,
        folders = database.rows("library_folders") { row -> LibraryFolder(row.number("id"), lang, row.text("name").orEmpty(), row.long("parentId"), row.number("createdAtMs")) },
        words = database.rows("vocab") { row -> VocabEntry(
            id = row.number("id"), lang = lang, word = row.text("word").orEmpty(), sentence = row.text("sentence").orEmpty(),
            dictionaryUrl = row.text("dictionaryUrl"), textId = row.long("textId"), meaning = row.text("meaning"), meaningLang = Languages.PERSIAN.code,
            listId = row.long("listId"), learned = row.number("learned") != 0L, createdAtMs = row.number("createdAtMs"),
            leitnerBox = row.number("leitnerBox", 1).toInt().coerceIn(1, 5), nextReviewAtMs = row.number("nextReviewAtMs"), lastReviewedAtMs = row.long("lastReviewedAtMs")) },
        lists = database.rows("vocab_lists") { row -> VocabList(row.number("id"), lang, row.text("name").orEmpty(), row.number("createdAtMs")) },
        highlights = database.rows("highlights") { row -> HighlightEntry(row.number("id"), row.number("textId"), row.number("startOffset").toInt(), row.number("endOffset").toInt(), row.text("colorKey") ?: "yellow", row.number("createdAtMs")) },
        reviews = database.rows("review_log") { row -> ReviewLogEntry(row.number("id"), row.number("entryId"), lang, row.number("timestampMs"), row.number("knew") != 0L, row.number("boxBefore", 1).toInt(), row.number("boxAfter", 1).toInt()) },
        tags = database.rows("library_tags") { r -> LibraryTag(r.number("id"), r.text("name").orEmpty(), r.number("createdAtMs"), lang) },
        textTags = database.rows("text_tags") { r -> TextTagCrossRef(r.number("textId"), r.number("tagId")) },
        examAttempts = database.rows("exam_attempts") { r -> ExamAttempt(r.number("id"), r.text("examId").orEmpty(), r.text("questionId").orEmpty(), r.text("questionFr").orEmpty(), r.text("answer").orEmpty(), r.double("score"), r.text("note").orEmpty(), r.number("createdAtMs"), r.number("updatedAtMs"), lang) },
        studySources = database.rows("study_source") { r -> StudySource(r.number("id"), r.text("name").orEmpty(), SourceKind.fromCode(r.text("kind").orEmpty()), r.text("default_skill")?.let(StudySkill::fromCode), r.text("note"), r.number("archived") != 0L, r.number("created_at"), lang) },
        studySessions = database.rows("study_session") { r -> StudySession(r.number("id"), r.number("date"), r.number("duration_min").toInt(), StudySkill.fromCode(r.text("skill").orEmpty()), r.long("source_id"), r.text("note"), r.number("created_at"), r.number("updated_at"), lang) },
        resources = database.rows("resources") { r -> ResourceLink(r.number("id"), r.text("title").orEmpty(), r.text("url").orEmpty(), r.text("imageUrl"), r.number("createdAtMs"), r.text("category") ?: "other", r.text("description"), r.text("level"), lang) },
        headlines = database.rows("headlines") { r -> HeadlineEntity(r.text("sourceId").orEmpty(), r.text("sourceLabel").orEmpty(), r.text("externalId").orEmpty(), r.text("title").orEmpty(), r.text("snippet").orEmpty(), r.text("articleUrl").orEmpty(), r.text("imageUrl"), r.long("publishedAtMs"), r.number("cachedAtMs"), lang) },
        activity = database.rows("activity_log") { r -> ActivityLogEntry(r.text("date").orEmpty(), lang, r.number("readingMs"), r.number("listeningMs"), r.number("watchingMs")) },
        shadowAttempts = database.rows("shadow_attempts") { r -> ShadowAttempt(id = r.number("id"), lang = lang, textId = r.long("textId"), sentence = "", chunkIndex = r.number("chunkIndex").toInt(), sentenceIndex = r.number("sentenceIndex").toInt(), engine = r.text("engine").orEmpty(), matched = r.number("matched").toInt(), total = r.number("total").toInt(), paceRatio = r.double("paceRatio")?.toFloat(), timestampMs = r.number("timestampMs")) },
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
                val snapshot = readFrenchReaderSnapshot(db, entries) { oldPath, pdf ->
                    val image = legacyFile(entries, oldPath, "text_images") != null
                    val target = root / (if (image) "text_images" else "pdfs") / "french-reader-${ZipArchive.crc32(pdf)}-${pdf.size}.${if (image) "img" else "pdf"}"
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
