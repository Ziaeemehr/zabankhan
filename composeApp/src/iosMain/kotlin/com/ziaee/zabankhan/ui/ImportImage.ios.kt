package com.ziaee.zabankhan.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.skia.Image

actual fun loadImportImage(path: String): ImageBitmap = Image.makeFromEncoded(FileSystem.SYSTEM.read(path.toPath()) { readByteArray() }).toComposeImageBitmap()
