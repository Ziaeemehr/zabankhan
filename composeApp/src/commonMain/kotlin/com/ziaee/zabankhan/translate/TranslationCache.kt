package com.ziaee.zabankhan.translate

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface KeyValueCache {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
}

class InMemoryKeyValueCache : KeyValueCache {
    private val values = mutableMapOf<String, String>()
    private val mutex = Mutex()
    override suspend fun get(key: String): String? = mutex.withLock { values[key] }
    override suspend fun put(key: String, value: String) { mutex.withLock { values[key] = value } }
}

/** Length-prefix fields so delimiters in text or engine IDs cannot alias a different tuple. */
fun translationCacheKey(engine: String, source: String, target: String, text: String): String {
    val raw = listOf(engine, source, target, text).joinToString("") { "${it.length}:$it" }
    var hash = 0xcbf29ce484222325uL
    for (byte in raw.encodeToByteArray()) hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3uL
    return hash.toString(16).padStart(16, '0')
}

class TranslationCache(private val cache: KeyValueCache) {
    suspend fun get(text: String, source: String, target: String, engine: String): String? =
        cache.get(translationCacheKey(engine, source, target, text))?.let { polishTranslation(it, target) }
    suspend fun put(text: String, source: String, target: String, engine: String, value: String) =
        cache.put(translationCacheKey(engine, source, target, text), value)
}
