package com.ziaee.zabankhan.images

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect

internal actual fun encodeArticleJpeg(bytes: ByteArray, maxWidth: Int, quality: Int): ByteArray? {
    val decoded = Image.makeFromEncoded(bytes)
    try {
        val (width, height) = scaledDimensions(decoded.width, decoded.height, maxWidth)
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
        try {
            val canvas = Canvas(bitmap)
            try { canvas.drawImageRect(decoded, Rect.makeWH(width.toFloat(), height.toFloat())) }
            finally { canvas.close() }
            val output = Image.makeFromBitmap(bitmap)
            return try { output.encodeToData(EncodedImageFormat.JPEG, quality)?.bytes }
            finally { output.close() }
        } finally { bitmap.close() }
    } finally { decoded.close() }
}
