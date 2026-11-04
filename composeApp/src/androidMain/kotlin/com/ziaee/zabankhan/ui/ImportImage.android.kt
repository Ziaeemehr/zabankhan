package com.ziaee.zabankhan.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun loadImportImage(path: String): ImageBitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    require(options.outWidth > 0 && options.outHeight > 0) { "Invalid image" }
    var sample = 1
    while (maxOf(options.outWidth, options.outHeight) / sample > 2400) sample *= 2
    options.inJustDecodeBounds = false; options.inSampleSize = sample
    return requireNotNull(BitmapFactory.decodeFile(path, options)).asImageBitmap()
}
