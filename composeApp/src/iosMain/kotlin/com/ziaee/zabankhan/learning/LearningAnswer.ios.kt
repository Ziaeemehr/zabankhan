package com.ziaee.zabankhan.learning

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun normalizeLearningNfc(text: String): String =
    (text as NSString).precomposedStringWithCanonicalMapping
