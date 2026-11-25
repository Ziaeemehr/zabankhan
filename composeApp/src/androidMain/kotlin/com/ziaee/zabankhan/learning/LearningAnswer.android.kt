package com.ziaee.zabankhan.learning

import java.text.Normalizer

internal actual fun normalizeLearningNfc(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFC)
