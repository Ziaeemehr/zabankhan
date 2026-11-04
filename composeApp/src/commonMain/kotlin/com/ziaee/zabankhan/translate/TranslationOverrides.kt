package com.ziaee.zabankhan.translate

/** Storage owns deletion; an empty value is an override tombstone. */
class TranslationOverrides(private val cache: KeyValueCache) {
    suspend fun getOverride(text: String, source: String, target: String): String? =
        cache.get(translationCacheKey("override", source, target, text))?.takeIf { it.isNotBlank() }
    suspend fun putOverride(text: String, source: String, target: String, translation: String) =
        cache.put(translationCacheKey("override", source, target, text), translation)
    suspend fun removeOverride(text: String, source: String, target: String) =
        cache.put(translationCacheKey("override", source, target, text), "")
}
