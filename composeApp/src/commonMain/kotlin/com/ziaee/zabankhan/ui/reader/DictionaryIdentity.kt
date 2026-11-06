package com.ziaee.zabankhan.ui.reader

/** French Reader's NFKC identity retains accents and unifies compatibility spellings. */
internal expect fun dictionaryNormalizeCompatibility(text: String): String

internal fun dictionaryIdentityKey(word: String): String = dictionaryNormalizeCompatibility(word.trim()).lowercase()
