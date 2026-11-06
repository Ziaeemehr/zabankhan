package com.ziaee.zabankhan.grammar

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GrammarRecent(val id: String, val openedAt: Long)
@Serializable
data class GrammarPrefsState(
    val favourites: Set<String> = emptySet(), val recents: List<GrammarRecent> = emptyList()
)

fun toggleGrammarFavourite(ids: Set<String>, id: String): Set<String> =
    if (id in ids) ids - id else ids + id

fun normalizeGrammarRecents(recents: List<GrammarRecent>): List<GrammarRecent> =
    recents.sortedByDescending { it.openedAt }.distinctBy { it.id }.take(20)

fun openGrammarRecent(recents: List<GrammarRecent>, id: String, time: Long): List<GrammarRecent> =
    (listOf(GrammarRecent(id, time)) + normalizeGrammarRecents(recents.filterNot { it.id == id })).take(20)

// A versioned JSON envelope replaces the reference's java.net URL-encoded lines.
@Serializable private data class StoredGrammarPrefs(val version: Int = 1, val state: GrammarPrefsState)
fun encodeGrammarPrefs(state: GrammarPrefsState): String = Json.encodeToString(
    StoredGrammarPrefs(state = state.copy(recents = normalizeGrammarRecents(state.recents)))
)
fun decodeGrammarPrefs(value: String): GrammarPrefsState = runCatching {
    val stored = Json.decodeFromString<StoredGrammarPrefs>(value)
    require(stored.version == 1)
    require(stored.state.favourites.none(String::isEmpty) && stored.state.recents.none { it.id.isEmpty() })
    stored.state.copy(recents = normalizeGrammarRecents(stored.state.recents))
}.getOrDefault(GrammarPrefsState())
