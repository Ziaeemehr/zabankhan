package com.ziaee.zabankhan.pdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.ByteString.Companion.encodeUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PdfDocumentHandle {
    /** Native rendering/extraction needs a smaller working set during flings. */
    val deferWorkWhileScrolling: Boolean get() = false
    val pageCount: Int
    suspend fun pageAspect(index: Int): Float
    suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap
    suspend fun words(index: Int): List<PdfWord>
    suspend fun pageText(index: Int): String
    fun close()
}
expect fun openPdf(path: String): PdfDocumentHandle

/** iOS reinstalls change the app container path; saved paths are re-anchored on the current files dir. */
fun rebasePdfPath(saved: String, filesDir: String): String {
    if (!saved.startsWith("/")) return "$filesDir/$saved"
    val at = saved.lastIndexOf("/pdfs/")
    return if (at < 0) saved else filesDir.trimEnd('/') + saved.substring(at)
}
interface OcrEngine {
    suspend fun recognizeWords(image: ImageBitmap, langCode: String): List<PdfWord>
}
expect fun createOcrEngine(): OcrEngine
expect fun cropPdfImage(image: ImageBitmap, rect: PdfRect): ImageBitmap
expect object AppFiles {
    val filesDir: String
    val cacheDir: String
}
@Composable expect fun rememberPdfPicker(onPicked: (path: String?) -> Unit): () -> Unit

/** A glyph may represent multiple UTF-16 characters (ligatures). Boxes use top-left coordinates. */
data class PdfCharacter(val text: String, val rect: PdfRect, val breakBefore: Boolean = false)

fun groupPdfCharacters(chars: List<PdfCharacter>): List<PdfWord> = groupPdfCharacters(chars.asSequence())

internal fun groupPdfCharacters(chars: Sequence<PdfCharacter>): List<PdfWord> {
    val result = mutableListOf<PdfWord>()
    val token = StringBuilder()
    var box: PdfRect? = null
    fun flush() {
        if (token.isNotEmpty() && box != null) result += PdfWord(token.toString(), box!!, result.size)
        token.clear(); box = null
    }
    for (glyph in chars) {
        if (glyph.breakBefore) flush()
        for (c in glyph.text) {
            val keep = c.isLetterOrDigit() || c in "'’\u200c" ||
                c.category == CharCategory.NON_SPACING_MARK || c.category == CharCategory.COMBINING_SPACING_MARK
            if (!keep) {
                // Keep sentence punctuation attached for sentence extraction, but never join words across it.
                if (c in ".!?…؟" && token.isNotEmpty()) token.append(c)
                flush()
            } else {
                token.append(c)
                box = box?.let { PdfRect(minOf(it.left, glyph.rect.left), minOf(it.top, glyph.rect.top),
                    maxOf(it.right, glyph.rect.right), maxOf(it.bottom, glyph.rect.bottom)) } ?: glyph.rect
            }
        }
    }
    flush()
    return result
}

/** Cache key includes the document identity and OCR language. No empty/error results are persisted. */
class PdfPageWords(private val path: String, private val document: PdfDocumentHandle, private val lang: String) {
    /** Page heights survive item disposal; a 1.414 placeholder above the viewport fights backward scroll. */
    val aspects = androidx.compose.runtime.mutableStateMapOf<Int, Float>()
    /** Recent rendered pages, so items re-entering during a fling do not blink or re-render. */
    private val images = LinkedHashMap<Pair<Int, Int>, androidx.compose.ui.graphics.ImageBitmap>()
    fun cachedImage(page: Int) = images.entries.lastOrNull { it.key.first == page }?.value
    suspend fun image(page: Int, width: Int, reuseLarger: Boolean = false): androidx.compose.ui.graphics.ImageBitmap =
        (if (reuseLarger) images.entries.asSequence()
            .filter { it.key.first == page && it.key.second >= width }
            .maxByOrNull { it.key.second }?.value else images[page to width])
        ?: document.renderPage(page, width).also {
            images[page to width] = it
            if (images.size > 8) images.remove(images.keys.first())
        }
    private val memory = mutableMapOf<Int, List<PdfWord>>()
    private val ocr = createOcrEngine()
    private val mutex = Mutex()
    private fun remember(page: Int, words: List<PdfWord>): List<PdfWord> {
        if (memory.size >= 12) memory.remove(memory.keys.first())
        memory[page] = words
        return words
    }
    suspend fun get(page: Int): List<PdfWord> = withContext(Dispatchers.IO) { mutex.withLock {
        memory[page]?.let { return@withLock it }
        val text = document.words(page)
        if (text.isNotEmpty()) return@withLock remember(page, text)
        val key = (path + ":" + lang).encodeUtf8().sha256().hex()
        val file = ("${AppFiles.cacheDir}/pdf-ocr/$key-$page.tsv").toPath()
        val cached = runCatching { FileSystem.SYSTEM.read(file) { readUtf8() } }.getOrNull()
            ?.let(::decodePdfWords)?.takeIf { it.isNotEmpty() }
        if (cached != null) return@withLock remember(page, cached)
        val words = ocr.recognizeWords(document.renderPage(page, 1800), lang)
        if (words.isNotEmpty()) {
            FileSystem.SYSTEM.createDirectories(file.parent!!)
            FileSystem.SYSTEM.write(file) { writeUtf8(encodePdfWords(words)) }
            remember(page, words)
        }
        words
    } }
}
fun encodePdfWords(words: List<PdfWord>): String = words.joinToString("\n") {
    listOf(it.rect.left, it.rect.top, it.rect.right, it.rect.bottom, it.text.replace('\t', ' ').replace('\n', ' ')).joinToString("\t")
}
fun decodePdfWords(tsv: String): List<PdfWord> = tsv.lineSequence().mapNotNull { line ->
    val p = line.split('\t', limit = 5)
    if (p.size != 5) null else runCatching {
        val r = PdfRect(p[0].toFloat(), p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
        require(listOf(r.left, r.top, r.right, r.bottom).all { it.isFinite() && it in 0f..1f })
        require(r.right >= r.left && r.bottom >= r.top)
        PdfWord(p[4], r, 0)
    }.getOrNull()
}.toList().mapIndexed { i, word -> word.copy(index = i) }
