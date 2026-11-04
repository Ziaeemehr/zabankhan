package com.ziaee.zabankhan.translate.llm

import com.ziaee.zabankhan.translate.*

internal fun translationCacheKey(model: String, source: String, target: String, text: String): String =
    com.ziaee.zabankhan.translate.translationCacheKey("llm:t:$model", source, target, text)
internal fun meaningCacheKey(model: String, source: String, target: String, word: String, sentence: String?): String =
    com.ziaee.zabankhan.translate.translationCacheKey("llm:m:$model", source, target, "${word.length}:$word${sentence?.let { "${it.length}:$it" } ?: "null"}")

class LlmCache(private val cache: KeyValueCache) {
    suspend fun get(text: String, source: String, target: String, model: String): String? =
        read(translationCacheKey(model, source, target, text), target)
    suspend fun put(text: String, source: String, target: String, model: String, value: String) =
        cache.put(translationCacheKey(model, source, target, text), value)
    suspend fun getRegenerated(text: String, source: String, target: String, model: String): String? =
        read("regenerated-" + translationCacheKey(model, source, target, text), target)
    suspend fun putRegenerated(text: String, source: String, target: String, model: String, value: String) =
        cache.put("regenerated-" + translationCacheKey(model, source, target, text), value)
    suspend fun getMeaning(word: String, sentence: String?, source: String, target: String, model: String): String? =
        read(meaningCacheKey(model, source, target, word, sentence), target)
    suspend fun putMeaning(word: String, sentence: String?, source: String, target: String, model: String, value: String) =
        cache.put(meaningCacheKey(model, source, target, word, sentence), value)
    private suspend fun read(key: String, target: String): String? =
        cache.get(key)?.takeIf { it.isNotBlank() }?.let { polishTranslation(it, target) }
}
