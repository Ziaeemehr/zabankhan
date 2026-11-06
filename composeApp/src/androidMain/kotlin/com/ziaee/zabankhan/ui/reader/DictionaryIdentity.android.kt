package com.ziaee.zabankhan.ui.reader

internal actual fun dictionaryNormalizeCompatibility(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
