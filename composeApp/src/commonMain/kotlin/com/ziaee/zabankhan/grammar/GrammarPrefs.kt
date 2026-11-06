package com.ziaee.zabankhan.grammar

import com.ziaee.zabankhan.data.nowMs
import com.ziaee.zabankhan.translate.KeyValueCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owned by the app container; each learning language has independent favourites and recents. */
class GrammarPrefs(private val cache: KeyValueCache, languageCode: String) {
    private val key = "grammar_prefs:v1:$languageCode"
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(GrammarPrefsState())
    private var loaded = false
    val state: StateFlow<GrammarPrefsState> = mutableState.asStateFlow()

    suspend fun load() = mutex.withLock { loadLocked() }
    suspend fun toggleFavourite(id: String) = mutex.withLock {
        loadLocked()
        save(mutableState.value.copy(favourites = toggleGrammarFavourite(mutableState.value.favourites, id)))
    }
    suspend fun opened(id: String) = mutex.withLock {
        loadLocked()
        save(mutableState.value.copy(recents = openGrammarRecent(mutableState.value.recents, id, nowMs())))
    }
    private suspend fun loadLocked() {
        if (!loaded) {
            mutableState.value = decodeGrammarPrefs(cache.get(key).orEmpty())
            loaded = true
        }
    }
    private suspend fun save(value: GrammarPrefsState) {
        cache.put(key, encodeGrammarPrefs(value))
        mutableState.value = value
    }
}
