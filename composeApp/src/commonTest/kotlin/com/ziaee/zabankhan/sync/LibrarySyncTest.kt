package com.ziaee.zabankhan.sync

import com.ziaee.zabankhan.data.LibrarySyncShadow
import com.ziaee.zabankhan.sync.library.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LibrarySyncTest {
    private fun item(key: String = "text:x", time: Long = 1, deleted: Boolean = false, device: String = "d", kind: String = "text") =
        LibraryItem(kind, key, time, deleted, if (deleted) null else buildJsonObject { put("v", 1) }, deviceId = device)
    private fun shadow(item: LibraryItem, hash: String = contentHash(item)) = LibrarySyncShadow(item.key, hash, item.updatedAtMs, item.deleted)

    @Test fun detectLocalChanges() {
        val live = item()
        val same = detectLocalChanges(mapOf(live.key to (contentHash(live) to live)), mapOf(live.key to shadow(live).copy(updatedAtMs = 10)), 20, false)
        assertEquals(10, same.single().updatedAtMs)
        val changed = live.copy(payload = buildJsonObject { put("v", 2) })
        assertEquals(20, detectLocalChanges(mapOf(live.key to (contentHash(changed) to changed)), mapOf(live.key to shadow(live)), 20, false).single().updatedAtMs)
        val deleted = detectLocalChanges(emptyMap(), mapOf(live.key to shadow(live)), 20, false).single()
        assertEquals(true, deleted.deleted); assertEquals(20, deleted.updatedAtMs)
        assertEquals(emptyList(), detectLocalChanges(emptyMap(), mapOf(live.key to shadow(live)), 20, true))
        val tomb = item(deleted = true, time = 7)
        assertEquals(7, detectLocalChanges(emptyMap(), mapOf(tomb.key to shadow(tomb)), 20, false).single().updatedAtMs)
        assertEquals(emptyList(), detectLocalChanges(emptyMap(), mapOf(tomb.key to shadow(tomb)), 7 + TOMBSTONE_RETENTION_MS + 1, false))
    }

    @Test fun newTombstoneKeepsBlobsOfDeletedItem() {
        val live = item().copy(blobs = listOf("h1", "h2"))
        val old = shadow(live).copy(blobs = "h1,h2")
        assertEquals(listOf("h1", "h2"), detectLocalChanges(emptyMap(), mapOf(live.key to old), 20, false).single().blobs)
        val tomb = old.copy(deleted = true)
        assertEquals(listOf("h1", "h2"), detectLocalChanges(emptyMap(), mapOf(live.key to tomb), 20, false).single().blobs)
    }

    @Test fun mergeWinners() {
        assertEquals(3, mergeWinners(listOf(item(time = 1)), listOf(item(time = 3))).getValue("text:x").updatedAtMs)
        assertEquals(true, mergeWinners(listOf(item(time = 1)), listOf(item(time = 2, deleted = true))).getValue("text:x").deleted)
        assertEquals(false, mergeWinners(listOf(item(time = 2)), listOf(item(time = 1, deleted = true))).getValue("text:x").deleted)
        assertEquals("z", mergeWinners(listOf(item(time = 2, device = "a")), listOf(item(time = 2, device = "z"))).getValue("text:x").deviceId)
        assertEquals(true, mergeWinners(listOf(item(time = 2, device = "z")), listOf(item(time = 2, deleted = true, device = "z"))).getValue("text:x").deleted)
    }

    @Test fun applyOrderingAndEquality() {
        val rows = listOf(item("ink:x:1", kind = "ink"), item("pos:x", kind = "pos"), item("text:x", kind = "text"), item("folder:x", kind = "folder"))
        assertEquals(listOf("folder:x", "text:x", "pos:x", "ink:x:1"), itemsToApply(emptyMap(), rows.associateBy { it.key }).map { it.key })
        assertEquals(emptyList(), itemsToApply(rows.associateBy { it.key }, rows.associateBy { it.key }))
    }

    @Test fun wireValidationAndRoundTrip() {
        val file = LibraryStateFile(1, "acct", "device_1", listOf(item(device = "device_1")))
        val bytes = LibraryWireFormat.encode(file)
        assertEquals(file, LibraryWireFormat.decode(bytes, "acct", "device_1"))
        assertFailsWith<IllegalArgumentException> { LibraryWireFormat.decode(bytes, "other", "device_1") }
        assertFailsWith<IllegalArgumentException> { LibraryWireFormat.decode(LibraryWireFormat.json.encodeToString(LibraryStateFile(LIBRARY_SCHEMA_VERSION + 1, "acct", "device_1", emptyList())).encodeToByteArray(), "acct", "device_1") }
        assertFailsWith<IllegalArgumentException> { LibraryWireFormat.decode(bytes, "acct", "other") }
        fun decodeItems(items: List<LibraryItem>) = LibraryWireFormat.decode(LibraryWireFormat.json.encodeToString(file.copy(items = items)).encodeToByteArray(), "acct", "device_1")
        assertFailsWith<IllegalArgumentException> { decodeItems(listOf(item(kind = "other", device = "device_1"))) }
        assertFailsWith<IllegalArgumentException> { decodeItems(listOf(item(device = "device_1"), item(device = "device_1"))) }
        assertFailsWith<IllegalArgumentException> { LibraryWireFormat.decode(ByteArray(MAX_LIBRARY_FILE_BYTES + 1), "acct", "device_1") }
    }

    @Test fun contentHashIgnoresBlobOrder() {
        val a = item().copy(blobs = listOf("b", "a")); val b = item().copy(blobs = listOf("a", "b"))
        assertEquals(contentHash(a), contentHash(b))
    }

    @Test fun placeBlobReusesRenamesOrDownloads() {
        val hash = "a".repeat(64)
        assertEquals(BlobPlacement("d/book.pdf", false), placeBlob("d", "book.pdf", hash) { if (it == "d/book.pdf") hash else null })
        assertEquals(BlobPlacement("d/book-${hash.take(8)}.pdf", true), placeBlob("d", "book.pdf", hash) { if (it == "d/book.pdf") "b".repeat(64) else null })
        assertEquals(BlobPlacement("d/book-${hash.take(8)}.pdf", false), placeBlob("d", "book.pdf", hash) { if (it == "d/book.pdf") "b".repeat(64) else hash })
        assertEquals("d/book.pdf", placeBlob("d", "../x/book.pdf", hash) { null }.path)
        assertEquals("d/$hash", placeBlob("d", "..", hash) { null }.path)
    }

    @Test fun deletePathMustHaveNoRemainingReferences() {
        assertEquals(false, pathsSafeToDelete("same.pdf", listOf("same.pdf")))
        assertEquals(true, pathsSafeToDelete("old.pdf", listOf("other.pdf")))
    }
}
