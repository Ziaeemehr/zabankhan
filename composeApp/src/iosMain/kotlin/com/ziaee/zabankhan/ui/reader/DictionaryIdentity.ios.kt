package com.ziaee.zabankhan.ui.reader

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCompatibilityMapping

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal actual fun dictionaryNormalizeCompatibility(text: String): String =
    NSString.create(string = text).precomposedStringWithCompatibilityMapping
