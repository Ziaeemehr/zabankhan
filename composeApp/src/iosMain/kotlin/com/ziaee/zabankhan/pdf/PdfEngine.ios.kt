@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.ziaee.zabankhan.pdf

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.ziaee.zabankhan.data.iosDocumentsDir
import com.ziaee.zabankhan.lang.Languages
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.Image
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Rect
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ColorAlphaType
import platform.Foundation.*
import platform.CoreGraphics.*
import platform.PDFKit.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.*
import platform.Vision.*
import platform.darwin.NSObject
import kotlin.coroutines.resume

actual object AppFiles {
    actual val filesDir: String get() = iosDocumentsDir()
    actual val cacheDir: String get() = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
}
actual fun openPdf(path: String): PdfDocumentHandle = IosPdf(path)
private class IosPdf(path: String): PdfDocumentHandle {
    private var document: PDFDocument? = PDFDocument(uRL = NSURL.fileURLWithPath(path))
    private val mutex = Mutex()
    override val deferWorkWhileScrolling = true
    override val pageCount = requireNotNull(document) { "Cannot open PDF" }.pageCount.toInt()
    // openPdf is called on IO before the reader is published. No PDFKit access/lock for item layout.
    private val aspects = FloatArray(pageCount) { index ->
        page(index).boundsForBox(kPDFDisplayBoxCropBox).useContents { (size.height / size.width).toFloat() }
    }
    private fun page(index: Int): PDFPage {
        require(index in 0 until pageCount)
        return requireNotNull(document?.pageAtIndex(index.toULong()))
    }
    override suspend fun pageAspect(index: Int): Float = aspects[index]
    override suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap = withContext(Dispatchers.Default) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            val p = page(index)
            val aspect = aspects[index]
            val heightPx = (widthPx * aspect).toInt().coerceAtLeast(1)
            val thumbnail = p.thumbnailOfSize(CGSizeMake(widthPx.toDouble(), heightPx.toDouble()), forBox = kPDFDisplayBoxCropBox)
            currentCoroutineContext().ensureActive()
            // Draw straight into Skia pixel memory: no PNG round-trip, so no multi-MB Kotlin heap garbage per page.
            val bitmap = Bitmap().apply { allocPixels(ImageInfo(widthPx, heightPx, ColorType.BGRA_8888, ColorAlphaType.PREMUL)) }
            bitmap.withBitmapContext { context ->
                CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
                CGContextFillRect(context, CGRectMake(0.0, 0.0, widthPx.toDouble(), heightPx.toDouble()))
                // A raw bitmap context preserves CGImage's top-first memory rows, as Skia expects.
                // A UIKit-style translate/scale flip here would turn the page upside down.
                CGContextDrawImage(context, CGRectMake(0.0, 0.0, widthPx.toDouble(), heightPx.toDouble()), thumbnail.CGImage)
            }
            bitmap.setImmutable()
            bitmap.asComposeImageBitmap()
        }
    }
    override suspend fun words(index: Int): List<PdfWord> = withContext(Dispatchers.Default) {
        val job = currentCoroutineContext()
        mutex.withLock {
            job.ensureActive()
            val p = page(index)
            val text = p.string.orEmpty()
            val bounds = p.boundsForBox(kPDFDisplayBoxCropBox)
            // Consume glyphs as a stream, without retaining a page-sized Kotlin character list.
            val chars = text.asSequence().mapIndexed { i, char ->
                job.ensureActive()
                val b = p.characterBoundsAtIndex(i.toLong())
                val normalized = bounds.useContents {
                    val ox = origin.x; val oy = origin.y; val w = size.width; val h = size.height
                    b.useContents {
                        PdfRect(((origin.x - ox) / w).toFloat().coerceIn(0f,1f),
                            (1 - (origin.y - oy + size.height) / h).toFloat().coerceIn(0f,1f),
                            ((origin.x - ox + size.width) / w).toFloat().coerceIn(0f,1f),
                            (1 - (origin.y - oy) / h).toFloat().coerceIn(0f,1f))
                    }
                }
                PdfCharacter(char.toString(), normalized)
            }
            groupPdfCharacters(chars)
        }
    }
    override suspend fun pageText(index: Int): String = withContext(Dispatchers.Default) { mutex.withLock { page(index).string.orEmpty() } }
    override fun close() { runBlocking(Dispatchers.Default) { mutex.withLock { document = null } } }
}

/** Borrow native Skia storage; keep the pixmap alive until CoreGraphics releases the context. */
private inline fun <T> Bitmap.withBitmapContext(block: (CGContextRef) -> T): T {
    val pixels = requireNotNull(peekPixels())
    try {
        val colorSpace = requireNotNull(CGColorSpaceCreateDeviceRGB())
        try {
            val context = requireNotNull(CGBitmapContextCreate(interpretCPointer<COpaque>(pixels.addr),
                width.toULong(), height.toULong(), 8u, (rowBytesAsPixels * 4).toULong(), colorSpace,
                CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or kCGBitmapByteOrder32Little))
            try { return block(context) } finally { CGContextRelease(context) }
        } finally { CGColorSpaceRelease(colorSpace) }
    } finally { pixels.close() }
}
actual fun cropPdfImage(image: ImageBitmap, rect: PdfRect): ImageBitmap {
    val left = (rect.left * image.width).toInt().coerceIn(0, image.width - 1)
    val top = (rect.top * image.height).toInt().coerceIn(0, image.height - 1)
    val width = ((rect.right * image.width).toInt() - left).coerceIn(1, image.width - left)
    val height = ((rect.bottom * image.height).toInt() - top).coerceIn(1, image.height - top)
    val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
    val canvas = Canvas(bitmap)
    try {
        canvas.drawImageRect(Image.makeFromBitmap(image.asSkiaBitmap()),
            Rect.makeXYWH(left.toFloat(), top.toFloat(), width.toFloat(), height.toFloat()),
            Rect.makeWH(width.toFloat(), height.toFloat()))
    } finally { canvas.close() }
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}
actual fun createOcrEngine(): OcrEngine = object : OcrEngine {
    override suspend fun recognizeWords(image: ImageBitmap, langCode: String): List<PdfWord> = withContext(Dispatchers.Default) {
        val language = requireNotNull(Languages.byCode(langCode))
        currentCoroutineContext().ensureActive()
        val request = VNRecognizeTextRequest()
        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        request.recognitionLanguages = listOf(language.code)
        request.usesLanguageCorrection = true
        // Vision accepts native pixels directly; avoid PNG -> ByteArray -> NSData -> UIImage.
        image.asSkiaBitmap().withBitmapContext { context ->
            val cgImage = requireNotNull(CGBitmapContextCreateImage(context))
            try {
                val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any>())
                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation { request.cancel() }
                    if (continuation.isActive) memScoped {
                        val error = alloc<ObjCObjectVar<NSError?>>()
                        val success = handler.performRequests(listOf(request), error.ptr)
                        // Cancellation can interrupt Vision; don't turn that into a reader error.
                        if (continuation.isActive) {
                            if (success) continuation.resume(Unit)
                            else continuation.resumeWith(Result.failure(IllegalStateException(error.value?.localizedDescription ?: "OCR failed")))
                        }
                    }
                }
            } finally { CGImageRelease(cgImage) }
        }
        currentCoroutineContext().ensureActive()
        val result = mutableListOf<PdfWord>()
        for (observation in request.results.orEmpty().filterIsInstance<VNRecognizedTextObservation>()) {
            currentCoroutineContext().ensureActive()
            val recognized = observation.topCandidates(1u).firstOrNull() as? VNRecognizedText ?: continue
            val text = recognized.string
            for (match in Regex("[^\\s]+").findAll(text)) {
                val rectangle = recognized.boundingBoxForRange(NSMakeRange(match.range.first.toULong(), match.value.length.toULong()), null) ?: continue
                val box = rectangle.boundingBox.useContents {
                    PdfRect(origin.x.toFloat(), (1 - origin.y - size.height).toFloat(),
                        (origin.x + size.width).toFloat(), (1 - origin.y).toFloat())
                }
                result += PdfWord(match.value, box, result.size)
            }
        }
        result
    }
}
@Composable actual fun rememberPdfPicker(onPicked: (path: String?) -> Unit): () -> Unit =
    com.ziaee.zabankhan.files.rememberFilePicker(listOf(com.ziaee.zabankhan.files.FileKind.PDF)) { onPicked(it?.path) }
