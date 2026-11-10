package com.ziaee.zabankhan.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class RestoreSyncTest {
    @Test
    fun skipsOnlyOwnDeletesMissingFromRestoredJournal() {
        val remote = listOf(
            op("own-lost", "device", "card", "delete"),
            op("own-known", "device", "list", "resolve_delete"),
            op("own-upsert", "device", "card", "upsert"),
            op("other-delete", "other", "card", "delete"),
        )
        assertEquals(setOf("own-lost"), opsToSkipAfterRestore(remote, setOf("own-known"), "device"))
    }

    private fun op(id: String, device: String, entity: String, type: String) = SyncOperation(
        operationId = id, deviceId = device, sequence = 1, entityType = entity,
        entitySyncId = "entity-$id", baseRevision = null, operationType = type, payload = null,
    )
}
