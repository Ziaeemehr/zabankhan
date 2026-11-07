package com.ziaee.zabankhan.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

internal actual fun encodeArticleJpeg(bytes: ByteArray, maxWidth: Int, quality: Int): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val (width, height) = scaledDimensions(bounds.outWidth, bounds.outHeight, maxWidth)
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, width) }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    var scaled: Bitmap? = null
    return try {
        val output = if (decoded.width != width || decoded.height != height) Bitmap.createScaledBitmap(decoded, width, height, true) else decoded
        scaled = output
        ByteArrayOutputStream().use { stream ->
            if (output.compress(Bitmap.CompressFormat.JPEG, quality, stream)) stream.toByteArray() else null
        }
    } finally {
        scaled?.takeUnless { it.isRecycled }?.recycle()
        if (decoded !== scaled && !decoded.isRecycled) decoded.recycle()
    }
}
