package com.ziaee.zabankhan.pdf

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class IosPdfRenderingTest {
    @Test fun previewUpgradesAndDoesNotReplaceCachedFullResolution() = runTest {
        val widths = mutableListOf<Int>()
        val document = object : PdfDocumentHandle {
            override val pageCount = 1
            override suspend fun pageAspect(index: Int) = 1f
            override suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap {
                widths += widthPx
                return ImageBitmap(widthPx, widthPx)
            }
            override suspend fun words(index: Int) = emptyList<PdfWord>()
            override suspend fun pageText(index: Int) = ""
            override fun close() = Unit
        }
        val cache = PdfPageWords("render-test", document, "en")
        val preview = cache.image(0, 50, reuseLarger = true)
        assertSame(preview, cache.image(0, 50, reuseLarger = true))
        val full = cache.image(0, 100, reuseLarger = true)
        assertSame(full, cache.image(0, 50, reuseLarger = true))
        assertEquals(listOf(50, 100), widths)
        // Android's default still requests the exact cached size.
        assertSame(preview, cache.image(0, 50))
    }

    @Test fun renderedAndCroppedPageKeepTopAndBottomUpright() = runTest {
        val file = (NSTemporaryDirectory() + "pdf-orientation-${NSUUID().UUIDString}.pdf").toPath()
        withContext(Dispatchers.Default) {
            FileSystem.SYSTEM.write(file) { writeUtf8(twoColorPdf()) }
            try {
                val document = openPdf(file.toString())
                try {
                    val image = document.renderPage(0, 100)
                    assertEquals(0xFFFF0000.toInt(), pixel(image, 50, 25), "top half must be red")
                    assertEquals(0xFF0000FF.toInt(), pixel(image, 50, 75), "bottom half must be blue")
                    val crop = cropPdfImage(image, PdfRect(0f, 0f, 1f, .5f))
                    assertEquals(0xFFFF0000.toInt(), pixel(crop, 50, 25))
                } finally { document.close() }
            } finally { FileSystem.SYSTEM.delete(file) }
        }
    }

    private fun pixel(image: ImageBitmap, x: Int, y: Int): Int {
        val pixels = IntArray(1)
        image.readPixels(pixels, startX = x, startY = y, width = 1, height = 1)
        return pixels[0]
    }

    /** PDF coordinates are bottom-left: red at the top, blue at the bottom. ASCII offsets are bytes. */
    private fun twoColorPdf(): String {
        val stream = "1 0 0 rg 0 50 100 50 re f\n0 0 1 rg 0 0 100 50 re f\n"
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 100 100] /Resources << >> /Contents 4 0 R >>",
            "<< /Length ${stream.length} >>\nstream\n${stream}endstream",
        )
        val pdf = StringBuilder("%PDF-1.4\n")
        val offsets = objects.mapIndexed { index, body ->
            val offset = pdf.length
            pdf.append("${index + 1} 0 obj\n$body\nendobj\n")
            offset
        }
        val xref = pdf.length
        pdf.append("xref\n0 5\n0000000000 65535 f \n")
        offsets.forEach { pdf.append(it.toString().padStart(10, '0') + " 00000 n \n") }
        pdf.append("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return pdf.toString()
    }
}
