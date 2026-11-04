package com.ziaee.zabankhan.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ziaee.zabankhan.lang.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okio.ByteString.Companion.toByteString

internal lateinit var pdfContext: Context
actual object AppFiles {
    actual val filesDir: String get() = pdfContext.filesDir.absolutePath
    actual val cacheDir: String get() = pdfContext.cacheDir.absolutePath
}
fun initializePdf(context: Context) {
    pdfContext = context.applicationContext
    PDFBoxResourceLoader.init(pdfContext)
}
actual fun openPdf(path: String): PdfDocumentHandle = AndroidPdf(path)

private class AndroidPdf(private val path: String) : PdfDocumentHandle {
    private val descriptor = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try { PdfRenderer(descriptor) } catch (e: Throwable) { descriptor.close(); throw e }
    private val lock = Mutex()
    private var closed = false
    override val pageCount = renderer.pageCount
    private val bitmaps = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    override suspend fun pageAspect(index: Int) = withContext(Dispatchers.IO) {
        lock.withLock {
            check(!closed)
            renderer.openPage(index).use { it.height.toFloat() / it.width }
        }
    }
    override suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap = withContext(Dispatchers.IO) {
        lock.withLock {
            check(!closed)
            val width = widthPx.coerceIn(1, 4096)
            val key = "$index:$width"
            bitmaps.get(key)?.let { return@withLock it.asImageBitmap() }
            renderer.openPage(index).use { page ->
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.put(key, bitmap)
                bitmap.asImageBitmap()
            }
        }
    }
    override suspend fun words(index: Int): List<PdfWord> = withContext(Dispatchers.IO) {
        lock.withLock {
            check(!closed)
            PDDocument.load(File(path)).use { doc ->
                val page = doc.getPage(index)
                val box = page.cropBox ?: page.mediaBox
                val chars = mutableListOf<PdfCharacter>()
                val stripper = object : PDFTextStripper() {
                    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                        var previous: TextPosition? = null
                        textPositions.forEach { p ->
                            val old = previous
                            val gap = old == null || kotlin.math.abs(p.yDirAdj - old.yDirAdj) > old.heightDir * .6f ||
                                p.xDirAdj - (old.xDirAdj + old.widthDirAdj) > p.widthOfSpace * .45f
                            val rect = normalizePdfBounds(PdfRect(p.xDirAdj, p.yDirAdj - p.heightDir,
                                p.xDirAdj + p.widthDirAdj, p.yDirAdj), box.width, box.height, p.dir.toInt(), page.rotation)
                            chars += PdfCharacter(p.unicode.orEmpty(), rect, gap)
                            previous = p
                        }
                    }
                }.apply { startPage = index + 1; endPage = index + 1; sortByPosition = false }
                stripper.getText(doc)
                groupPdfCharacters(chars)
            }
        }
    }
    override suspend fun pageText(index: Int) = words(index).joinToString(" ") { it.text }
    override fun close() {
        // All access, including disposal, shares the same mutex. Wait off the UI thread in caller.
        runBlocking { lock.withLock { if (!closed) { closed = true; bitmaps.evictAll(); renderer.close(); descriptor.close() } } }
    }
}
actual fun createOcrEngine(): OcrEngine = object : OcrEngine {
    override suspend fun recognizeWords(image: ImageBitmap, langCode: String): List<PdfWord> {
        val lang = requireNotNull(Languages.byCode(langCode))
        // Bundled recognizer supports Latin script; do not silently misrecognize Arabic-script scans.
        require(lang.script == Script.LATIN) { "Bundled OCR does not support ${lang.nativeName}" }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(image.asAndroidBitmap(), 0))
                    .addOnSuccessListener { text ->
                        val words = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { e ->
                            e.boundingBox?.let { b -> PdfWord(e.text, PdfRect(
                                (b.left.toFloat() / image.width).coerceIn(0f, 1f), (b.top.toFloat() / image.height).coerceIn(0f, 1f),
                                (b.right.toFloat() / image.width).coerceIn(0f, 1f), (b.bottom.toFloat() / image.height).coerceIn(0f, 1f)), 0) }
                        }.mapIndexed { i, w -> w.copy(index = i) }
                        if (continuation.isActive) continuation.resume(words)
                    }.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            }
        } finally { recognizer.close() }
    }
}
actual fun cropPdfImage(image: ImageBitmap, rect: PdfRect): ImageBitmap {
    val x = (rect.left * image.width).toInt().coerceIn(0, image.width - 1)
    val y = (rect.top * image.height).toInt().coerceIn(0, image.height - 1)
    val w = ((rect.right * image.width).toInt() - x).coerceIn(1, image.width - x)
    val h = ((rect.bottom * image.height).toInt() - y).coerceIn(1, image.height - y)
    return Bitmap.createBitmap(image.asAndroidBitmap(), x, y, w, h).asImageBitmap()
}
@Composable actual fun rememberPdfPicker(onPicked: (path: String?) -> Unit): () -> Unit =
    com.ziaee.zabankhan.files.rememberFilePicker(listOf(com.ziaee.zabankhan.files.FileKind.PDF)) { onPicked(it?.path) }
