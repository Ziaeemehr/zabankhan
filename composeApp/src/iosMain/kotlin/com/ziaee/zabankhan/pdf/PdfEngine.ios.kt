@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.ziaee.zabankhan.pdf

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import platform.Foundation.*
import platform.CoreGraphics.*
import platform.PDFKit.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.*
import platform.Vision.*
import platform.darwin.NSObject
import platform.posix.memcpy
import okio.HashingSink
import okio.buffer
import okio.use
import okio.FileSystem
import okio.Path.Companion.toPath

actual object AppFiles {
    actual val filesDir: String get() = iosDocumentsDir()
    actual val cacheDir: String get() = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
}
private fun NSData.bytesArray(): ByteArray = ByteArray(length.toInt()).also { result ->
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
}
private fun UIImage.bitmap(): ImageBitmap =
    Image.makeFromEncoded(requireNotNull(UIImagePNGRepresentation(this)).bytesArray()).toComposeImageBitmap()

actual fun openPdf(path: String): PdfDocumentHandle = IosPdf(path)
private class IosPdf(path: String): PdfDocumentHandle {
    private var document: PDFDocument? = PDFDocument(uRL = NSURL.fileURLWithPath(path))
    private val mutex = Mutex()
    override val pageCount = requireNotNull(document) { "Cannot open PDF" }.pageCount.toInt()
    private fun page(index: Int): PDFPage {
        require(index in 0 until pageCount)
        return requireNotNull(document?.pageAtIndex(index.toULong()))
    }
    override suspend fun pageAspect(index: Int): Float = withContext(Dispatchers.Default) {
        mutex.withLock { page(index).boundsForBox(kPDFDisplayBoxCropBox).useContents { (size.height / size.width).toFloat() } }
    }
    override suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap = withContext(Dispatchers.Default) {
        mutex.withLock {
            val p = page(index)
            val aspect = p.boundsForBox(kPDFDisplayBoxCropBox).useContents { size.height / size.width }
            p.thumbnailOfSize(CGSizeMake(widthPx.toDouble(), widthPx * aspect), forBox = kPDFDisplayBoxCropBox).bitmap()
        }
    }
    override suspend fun words(index: Int): List<PdfWord> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val p = page(index)
            val text = p.string.orEmpty()
            val bounds = p.boundsForBox(kPDFDisplayBoxCropBox)
            val chars = text.mapIndexed { i, char ->
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
    override fun close() { runBlocking { mutex.withLock { document = null } } }
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
    return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
}
actual fun createOcrEngine(): OcrEngine = object : OcrEngine {
    override suspend fun recognizeWords(image: ImageBitmap, langCode: String): List<PdfWord> = withContext(Dispatchers.Default) {
        val language = requireNotNull(Languages.byCode(langCode))
        // Encode via Skia without using UIKit drawing on a worker thread.
        val bytes = requireNotNull(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData()).bytes
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val uiImage = requireNotNull(UIImage.imageWithData(data))
        val request = VNRecognizeTextRequest()
        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        request.recognitionLanguages = listOf(language.code)
        request.usesLanguageCorrection = true
        val handler = VNImageRequestHandler(cGImage = requireNotNull(uiImage.CGImage), options = emptyMap<Any?, Any>())
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            check(handler.performRequests(listOf(request), error.ptr)) { error.value?.localizedDescription ?: "OCR failed" }
        }
        val result = mutableListOf<PdfWord>()
        for (observation in request.results.orEmpty().filterIsInstance<VNRecognizedTextObservation>()) {
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
private class PdfPickerDelegate(val picked: (String?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { picked(null) }
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return picked(null)
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            val source = requireNotNull(url.path).toPath()
            val fs = FileSystem.SYSTEM
            val directory = "${AppFiles.filesDir}/pdfs".toPath()
            fs.createDirectories(directory)
            val temporary = directory / (NSUUID().UUIDString + ".tmp")
            val target = try {
                val hashing = HashingSink.sha256(fs.sink(temporary))
                hashing.buffer().use { output -> fs.source(source).buffer().use { input -> output.writeAll(input) } }
                val destination = directory / (hashing.hash.hex() + ".pdf")
                if (!fs.exists(destination)) fs.atomicMove(temporary, destination)
                destination
            } finally { fs.delete(temporary, mustExist = false) }
            fs.write((target.toString() + ".title").toPath()) { writeUtf8(url.lastPathComponent.orEmpty().substringBeforeLast('.')) }
            picked(target.toString())
        } catch (_: Exception) { picked(null) }
        finally { if (scoped) url.stopAccessingSecurityScopedResource() }
    }
}
@Composable actual fun rememberPdfPicker(onPicked: (path: String?) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val delegate = remember { PdfPickerDelegate { callback(it) } }
    return remember {
        {
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF), asCopy = true)
            picker.delegate = delegate
            var root = UIApplication.sharedApplication.keyWindow?.rootViewController
            while (root?.presentedViewController != null) root = root.presentedViewController
            root?.presentViewController(picker, true, null) ?: callback(null)
        }
    }
}
