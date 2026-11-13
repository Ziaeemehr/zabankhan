package com.ziaee.zabankhan.sync.library

import com.ziaee.zabankhan.data.LibrarySyncShadow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okio.ByteString.Companion.encodeUtf8

/**
 * Stamps local changes. [restore] stamps every present item "now" so a restored state beats older remote tombstones/edits (no tombstones are made).
 * [initializeOnly] with an empty shadow stamps pos/tag/textTag 0 so an existing remote value wins on a device's first sync.
 * Stamps never go below shadow/remote time + 1, so a slow clock cannot lose to older edits. [frozen] keys are neither changed nor deleted.
 */
fun detectLocalChanges(
    current: Map<String, Pair<String, LibraryItem>>,
    shadow: Map<String, LibrarySyncShadow>,
    nowMs: Long,
    initializeOnly: Boolean,
    restore: Boolean = false,
    remoteMax: Map<String, Long> = emptyMap(),
    frozen: Set<String> = emptySet(),
): List<LibraryItem> {
    val output = mutableListOf<LibraryItem>()
    fun stamp(key: String, old: LibrarySyncShadow?) = maxOf(nowMs, (old?.updatedAtMs ?: -1) + 1, (remoteMax[key] ?: -1) + 1)
    current.forEach { (key, pair) ->
        val (hash, item) = pair
        val old = shadow[key]
        val at = when {
            !restore && old != null && old.contentHash == hash -> old.updatedAtMs
            !restore && old == null && initializeOnly && item.kind in setOf(LibraryKinds.POSITION, LibraryKinds.TAG, LibraryKinds.TEXT_TAG) -> 0L
            else -> stamp(key, old)
        }
        output += item.copy(updatedAtMs = at, deleted = false)
    }
    shadow.forEach { (key, old) ->
        if (key in current || key in frozen) return@forEach
        if (old.deleted) {
            if (nowMs - old.updatedAtMs <= TOMBSTONE_RETENTION_MS) output += LibraryItem(kind = key.substringBefore(':'), key = key, updatedAtMs = old.updatedAtMs, deleted = true, blobs = old.blobs.split(',').filter { it.isNotEmpty() })
        } else if (!initializeOnly) {
            output += LibraryItem(kind = key.substringBefore(':'), key = key, updatedAtMs = stamp(key, old), deleted = true, blobs = old.blobs.split(',').filter { it.isNotEmpty() })
        }
    }
    return output
}

private fun LibraryItem.str(key: String) = (payload?.get(key) as? JsonPrimitive)?.contentOrNull

/**
 * First sync: maps local item keys (no shadow history) to the keys of the same remote texts/folders, so they are adopted instead of duplicated.
 * Texts match by pdfHash when both have one, else by bodyHash + lang + title; folders by lang + name + (mapped) parent.
 */
fun matchLocalToRemote(local: Map<String, LibraryItem>, shadowKeys: Set<String>, remotes: List<LibraryItem>): Map<String, String> {
    val remoteLive = mergeWinners(emptyList(), remotes).values.filter { !it.deleted && it.key !in local }
    val result = linkedMapOf<String, String>()
    val claimed = mutableSetOf<String>()
    val remoteTexts = remoteLive.filter { it.kind == LibraryKinds.TEXT }.sortedBy { it.key }
    local.values.filter { it.kind == LibraryKinds.TEXT && it.key !in shadowKeys }.sortedBy { it.key }.forEach { l ->
        val r = remoteTexts.firstOrNull { it.key !in claimed && run {
            val a = l.str("pdfHash"); val b = it.str("pdfHash")
            if (a != null && b != null) a == b else l.str("bodyHash") != null && l.str("bodyHash") == it.str("bodyHash") && l.str("lang") == it.str("lang") && l.str("title") == it.str("title")
        } } ?: return@forEach
        result[l.key] = r.key; claimed += r.key
    }
    val remoteFolders = remoteLive.filter { it.kind == LibraryKinds.FOLDER }.sortedBy { it.key }
    val localFolders = local.values.filter { it.kind == LibraryKinds.FOLDER && it.key !in shadowKeys }.sortedBy { it.key }
    var progress = true
    while (progress) {
        progress = false
        for (l in localFolders) {
            if (l.key in result) continue
            val parent = l.str("parentSyncId")?.let { result["folder:$it"]?.substringAfter(':') ?: it }
            val r = remoteFolders.firstOrNull { it.key !in claimed && it.str("lang") == l.str("lang") && it.str("name") == l.str("name") && it.str("parentSyncId") == parent } ?: continue
            result[l.key] = r.key; claimed += r.key; progress = true
        }
    }
    return result
}

fun mergeWinners(local: List<LibraryItem>, remotes: List<LibraryItem>): Map<String, LibraryItem> {
    val winners = linkedMapOf<String, LibraryItem>()
    (local + remotes).forEach { candidate ->
        val old = winners[candidate.key]
        if (old == null || compareValuesBy(candidate, old, LibraryItem::updatedAtMs, LibraryItem::deviceId) > 0 ||
            (candidate.updatedAtMs == old.updatedAtMs && candidate.deviceId == old.deviceId && candidate.deleted && !old.deleted)) {
            winners[candidate.key] = candidate
        }
    }
    return winners
}

fun itemsToApply(localByKey: Map<String, LibraryItem>, winners: Map<String, LibraryItem>): List<LibraryItem> {
    val order = listOf(LibraryKinds.FOLDER, LibraryKinds.VIDEO_PLAYLIST, LibraryKinds.TEXT, LibraryKinds.POSITION, LibraryKinds.TAG, LibraryKinds.TEXT_TAG, LibraryKinds.INK, LibraryKinds.NOTE, LibraryKinds.PDF_HIGHLIGHT, LibraryKinds.HIGHLIGHT, LibraryKinds.SAVED_VIDEO, LibraryKinds.VIDEO_PLAYLIST_MEMBER)
    return winners.values.filter { winner ->
        val local = localByKey[winner.key]
        // Compare content, not origin/time: an unchanged item echoed back by another device needs no work.
        if (local == null) !winner.deleted else contentHash(local) != contentHash(winner)
    }.sortedWith(compareBy<LibraryItem> { order.indexOf(it.kind) }.thenBy { it.key })
}

@Serializable
private data class HashContent(val kind: String, val key: String, val deleted: Boolean, val payload: kotlinx.serialization.json.JsonObject?, val blobs: List<String>)

private fun JsonElement.canonical(): JsonElement = when (this) {
    is JsonObject -> JsonObject(entries.sortedBy { it.key }.associate { it.key to it.value.canonical() })
    is JsonArray -> JsonArray(map { it.canonical() })
    else -> this
}

fun contentHash(item: LibraryItem): String = Json { encodeDefaults = true; explicitNulls = false }
    .encodeToString(HashContent(item.kind, item.key, item.deleted, item.payload?.canonical() as? JsonObject, if (item.deleted) emptyList() else item.blobs.sorted()))
    .encodeUtf8().sha256().hex()
