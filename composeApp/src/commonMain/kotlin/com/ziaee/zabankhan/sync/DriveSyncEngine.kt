package com.ziaee.zabankhan.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.ziaee.zabankhan.backup.DriveBackupClient
import com.ziaee.zabankhan.data.*
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock

data class SyncResult(
    val uploadedOperations: Int,
    val appliedOperations: Int,
    val conflicts: Int,
    val lastSuccessfulSyncMs: Long,
)

enum class ConflictChoice { KEEP_LOCAL, KEEP_DRIVE, KEEP_BOTH }

class DriveSyncEngine(
    private val database: AppDatabase,
    private val http: HttpClient,
    private val deviceId: String,
) {
    private val drive = DriveBackupClient(http)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun syncOnce(
        token: String,
        beforeApply: suspend (isInitialSync: Boolean) -> Unit = {},
    ): SyncResult = mutex.withLock {
        val accountId = drive.accountId(token)
        val syncDao = database.sync()
        val savedAccountId = syncDao.getMetadata("account_id")
        require(savedAccountId == null || savedAccountId == accountId) {
            "Drive is signed in to a different Google account than the account used for this sync."
        }

        val listedFiles = drive.listSyncFiles(token)
        require(listedFiles.map { it.name }.distinct().size == listedFiles.size) {
            "Drive contains duplicate sync files for a device. No data was applied; remove the duplicate sync file and try again."
        }
        val remoteFiles = listedFiles.map { remote ->
            val remoteDeviceId = remote.name.removePrefix(DRIVE_SYNC_FILE_PREFIX).removeSuffix(".json")
            val decoded = SyncWireFormat.decode(drive.downloadSyncFile(token, remote.id), accountId, remoteDeviceId)
            remoteDeviceId to decoded.file
        }
        syncDao.setMetadata(SyncMetadataEntry("account_id", accountId))
        database.vocab().seedInitialSyncJournal()
        syncDao.assignUnattributedOperations(deviceId)
        syncDao.setMetadata(SyncMetadataEntry("device_id", deviceId))

        val restorePending = syncDao.getMetadata("restore_pending") == "1"
        val initial = syncDao.getMetadata("first_sync_completed") != "1"
        val remoteOperations = remoteFiles.flatMap { it.second.operations }
            .sortedWith(compareBy<SyncOperation>({ it.deviceId }, { it.sequence }, { it.operationId }))
        if (restorePending) beforeApply(true) else if (initial || remoteOperations.isNotEmpty()) beforeApply(initial)

        var appliedCount = 0
        val skippedDeletes = mutableSetOf<String>()
        val reemittedReviewIds = mutableSetOf<String>()
        val localOperationIds = syncDao.allOperations().mapTo(mutableSetOf()) { it.operationId }
        val skippedOperationIds = if (restorePending) opsToSkipAfterRestore(remoteOperations, localOperationIds, deviceId) else emptySet()
        val unresolvedCount = database.useWriterConnection { connection -> connection.immediateTransaction {
            val sync = database.sync()
            var pending = remoteOperations
            var progress: Boolean
            do {
                progress = false
                val deferred = mutableListOf<SyncOperation>()
                for (operation in pending) {
                    if (sync.wasApplied(operation.operationId)) continue
                    if (operation.operationId in skippedOperationIds) {
                        sync.markApplied(AppliedSyncOperation(operation.operationId, operation.deviceId, operation.sequence))
                        if (operation.entityType == "list" || operation.entityType == "card") skippedDeletes += operation.entityType + "\u0000" + operation.entitySyncId
                        appliedCount++
                        progress = true
                        continue
                    }
                    val applied = applyOperation(operation, sync)
                    if (applied) {
                        sync.markApplied(AppliedSyncOperation(operation.operationId, operation.deviceId, operation.sequence))
                        appliedCount++
                        progress = true
                    } else deferred += operation
                }
                pending = deferred
            } while (progress && pending.isNotEmpty())
            if (restorePending) {
                val vocab = database.vocab()
                skippedDeletes.filter { it.startsWith("list\u0000") }.forEach { key ->
                    val syncId = key.substringAfter('\u0000')
                    vocab.getListBySyncId(syncId)?.let { vocab.resolveList(syncId, it, it.syncRevision) }
                }
                skippedDeletes.filter { it.startsWith("card\u0000") }.forEach { key ->
                    val syncId = key.substringAfter('\u0000')
                    vocab.getBySyncId(syncId)?.let { current ->
                        val resolved = current.copy(syncRevision = current.syncRevision + 1L)
                        vocab.resolveCard(syncId, current, current.syncRevision)
                        reemittedReviewIds += vocab.reemitReviewsForCard(resolved)
                    }
                }
                skippedDeletes.filter { it.startsWith("review\u0000") }.forEach { key ->
                    val syncId = key.substringAfter('\u0000')
                    if (syncId !in reemittedReviewIds) vocab.getReviewBySyncId(syncId)?.let { review ->
                        vocab.getById(review.entryId)?.let { card -> vocab.reemitReview(review, card) }
                    }
                }
                sync.clearMetadata("restore_pending")
            }
            sync.setMetadata(SyncMetadataEntry("first_sync_completed", "1"))
            sync.unresolvedConflicts().size
        } }

        val ownOperations = syncDao.operationsForDevice(deviceId)
        val lastSequence = ownOperations.maxOfOrNull { it.sequence } ?: 0L
        val ownFile = DeviceChangeFile(
            schemaVersion = DRIVE_SYNC_SCHEMA_VERSION,
            accountId = accountId,
            deviceId = deviceId,
            lastSequence = lastSequence,
            operations = ownOperations.map(SyncWireFormat::toWire),
        )
        drive.uploadSyncFile(token, deviceId, SyncWireFormat.encode(ownFile))
        val successAt = Clock.System.now().toEpochMilliseconds()
        syncDao.setMetadata(SyncMetadataEntry("last_successful_sync_ms", successAt.toString()))
        SyncResult(ownOperations.size, appliedCount, unresolvedCount, successAt)
    }

    private suspend fun applyOperation(operation: SyncOperation, sync: SyncDao): Boolean = when (operation.entityType) {
        "card" -> applyCard(operation, sync)
        "list" -> applyList(operation, sync)
        "review" -> applyReview(operation)
        else -> error("Drive data contains an unsupported entity type")
    }

    private suspend fun applyCard(operation: SyncOperation, sync: SyncDao): Boolean {
        if (operation.operationType == "review_state") return true // The associated review event carries a replayable schedule snapshot.
        val vocab = database.vocab()
        val existing = vocab.getBySyncId(operation.entitySyncId)
        val resolving = operation.operationType.startsWith("resolve_")
        if (operation.operationType == "delete" || operation.operationType == "resolve_delete") {
            if (existing == null) return true
            if (!resolving && existing.syncRevision != operation.baseRevision) {
                saveConflict(operation, "card", existing.syncId, cardJson(existing, vocab), sync)
                return true
            }
            vocab.deleteRaw(existing)
            return true
        }
        val payload = parsePayload(operation.payload)
        val revision = payload.long("revision") ?: return true
        if (operation.deviceId == deviceId && existing != null && existing.syncRevision >= revision) return true
        val listSyncId = payload.string("listSyncId")
        val list = listSyncId?.let { vocab.getListBySyncId(it) }
        if (listSyncId != null && list == null) return false
        val localListId = list?.id
        val incoming = VocabEntry(
            id = existing?.id ?: 0L,
            lang = payload.string("lang") ?: return true,
            word = payload.string("word") ?: return true,
            sentence = payload.string("sentence").orEmpty(),
            textId = existing?.textId,
            meaning = payload.string("meaning"),
            dictionaryUrl = payload.string("dictionaryUrl"),
            meaningLang = payload.string("meaningLang") ?: "fa",
            listId = localListId,
            learned = payload.boolean("learned") ?: false,
            createdAtMs = payload.long("createdAtMs") ?: 0L,
            leitnerBox = payload.long("leitnerBox")?.toInt() ?: 1,
            nextReviewAtMs = payload.long("nextReviewAtMs") ?: 0L,
            lastReviewedAtMs = payload.long("lastReviewedAtMs"),
            syncId = operation.entitySyncId,
            syncRevision = revision,
        )
        if (resolving) {
            if (existing == null) vocab.insertRaw(incoming) else vocab.updateRaw(incoming)
            return true
        }
        if (existing == null && operation.baseRevision != null && hasConcurrentEdit(operation, sync)) {
            saveConflict(operation, "card", operation.entitySyncId, "", sync)
            return true
        }
        if (operation.baseRevision == null && existing != null) {
            if (!sameCard(existing, incoming)) saveConflict(operation, "card", existing.syncId, cardJson(existing, vocab), sync)
            return true
        }
        if (existing != null && operation.baseRevision != existing.syncRevision) {
            if (operation.baseRevision != null && hasConcurrentEdit(operation, sync)) {
                saveConflict(operation, "card", existing.syncId, cardJson(existing, vocab), sync)
            } else if (existing.syncRevision < revision) {
                saveConflict(operation, "card", existing.syncId, cardJson(existing, vocab), sync)
            }
            return true
        }
        if (existing == null) vocab.insertRaw(incoming) else vocab.updateRaw(incoming)
        return true
    }

    private suspend fun applyList(operation: SyncOperation, sync: SyncDao): Boolean {
        val vocab = database.vocab()
        val existing = vocab.getListBySyncId(operation.entitySyncId)
        val resolving = operation.operationType.startsWith("resolve_")
        if (operation.operationType == "delete" || operation.operationType == "resolve_delete") {
            if (existing == null) return true
            if (!resolving && existing.syncRevision != operation.baseRevision) {
                saveConflict(operation, "list", existing.syncId, listJson(existing), sync)
            } else vocab.deleteListRaw(existing)
            return true
        }
        val payload = parsePayload(operation.payload)
        val revision = payload.long("revision") ?: return true
        val incoming = VocabList(
            id = existing?.id ?: 0L,
            lang = payload.string("lang") ?: return true,
            name = payload.string("name") ?: return true,
            createdAtMs = payload.long("createdAtMs") ?: 0L,
            syncId = operation.entitySyncId,
            syncRevision = revision,
        )
        if (resolving) {
            if (existing == null) vocab.insertListRaw(incoming) else vocab.updateListRaw(incoming)
            return true
        }
        if (existing == null && operation.baseRevision != null && hasConcurrentEdit(operation, sync)) {
            saveConflict(operation, "list", operation.entitySyncId, "", sync)
            return true
        }
        if (operation.deviceId == deviceId && existing != null && existing.syncRevision >= revision) return true
        if (operation.baseRevision == null && existing != null) {
            if (existing.name != incoming.name) saveConflict(operation, "list", existing.syncId, listJson(existing), sync)
            return true
        }
        if (existing != null && operation.baseRevision != existing.syncRevision) {
            if (hasConcurrentEdit(operation, sync) || existing.syncRevision < revision) {
                saveConflict(operation, "list", existing.syncId, listJson(existing), sync)
            }
            return true
        }
        if (existing == null) vocab.insertListRaw(incoming) else vocab.updateListRaw(incoming)
        return true
    }

    private suspend fun applyReview(operation: SyncOperation): Boolean {
        val vocab = database.vocab()
        val existing = vocab.getReviewBySyncId(operation.entitySyncId)
        if (operation.operationType == "delete") {
            if (existing != null) vocab.deleteReviewLogRaw(existing.id)
            val restore = operation.payload?.let(::parsePayload)
            val cardSyncId = restore?.string("syncId")
            val card = cardSyncId?.let { vocab.getBySyncId(it) }
            if (card != null) recomputeSchedule(card, operation)
            return true
        }
        val payload = parsePayload(operation.payload)
        val cardSyncId = payload.string("cardSyncId") ?: return false
        val card = vocab.getBySyncId(cardSyncId) ?: return false
        val review = ReviewLogEntry(
            id = existing?.id ?: 0L,
            entryId = card.id,
            lang = payload.string("lang") ?: card.lang,
            timestampMs = payload.long("timestampMs") ?: 0L,
            knew = payload.boolean("knew") ?: false,
            boxBefore = payload.long("boxBefore")?.toInt() ?: 1,
            boxAfter = payload.long("boxAfter")?.toInt() ?: 1,
            syncId = operation.entitySyncId,
            scheduleSnapshot = payload.string("scheduleSnapshot"),
        )
        if (existing == null) vocab.insertReviewLogRaw(review) else vocab.updateReviewLogRaw(review)
        recomputeSchedule(card, operation)
        return true
    }

    private suspend fun recomputeSchedule(card: VocabEntry, operation: SyncOperation) {
        val vocab = database.vocab()
        val latest = vocab.allReviews().asSequence()
            .filter { it.entryId == card.id && it.scheduleSnapshot != null }
            .maxWithOrNull(compareBy<ReviewLogEntry>({ it.timestampMs }, { it.syncId }))
        val snapshot = latest?.scheduleSnapshot ?: operation.payload?.let(::parsePayload)?.let { fallback ->
            if (fallback.string("syncId") == card.syncId) fallback.rawJson() else null
        } ?: return
        val state = parsePayload(snapshot)
        val updated = card.copy(
            learned = state.boolean("learned") ?: card.learned,
            leitnerBox = state.long("leitnerBox")?.toInt() ?: card.leitnerBox,
            nextReviewAtMs = state.long("nextReviewAtMs") ?: card.nextReviewAtMs,
            lastReviewedAtMs = state.long("lastReviewedAtMs") ?: card.lastReviewedAtMs,
            syncRevision = maxOf(card.syncRevision, state.long("revision") ?: card.syncRevision),
        )
        vocab.updateRaw(updated)
    }

    private suspend fun hasConcurrentEdit(operation: SyncOperation, sync: SyncDao): Boolean {
        val base = operation.baseRevision ?: return false
        return sync.allOperations().any {
            it.entitySyncId == operation.entitySyncId && it.entityType == operation.entityType &&
                it.deviceId == deviceId && it.deviceId != operation.deviceId && it.baseRevision == base &&
                it.operationType != "review_state"
        }
    }

    private suspend fun saveConflict(operation: SyncOperation, entityType: String, syncId: String, localPayload: String, sync: SyncDao) {
        sync.saveConflict(SyncConflict(
            conflictId = "${operation.deviceId}:${operation.operationId}",
            entityType = entityType,
            entitySyncId = syncId,
            localOperationId = database.sync().allOperations().lastOrNull {
                it.entityType == entityType && it.entitySyncId == syncId && it.deviceId == deviceId
            }?.operationId ?: "local-current",
            remoteOperationId = operation.operationId,
            localPayload = localPayload,
            remotePayload = operation.payload.orEmpty(),
            baseRevision = operation.payload?.let(::parsePayload)?.long("revision") ?: operation.baseRevision ?: 0L,
            remoteOperationType = operation.operationType,
        ))
    }

    suspend fun resolveConflict(conflictId: String, choice: ConflictChoice) {
        database.useWriterConnection { connection -> connection.immediateTransaction {
            val sync = database.sync()
            val conflict = sync.getConflict(conflictId) ?: return@immediateTransaction
            val vocab = database.vocab()
            val localPayload = conflict.localPayload.takeIf { it.isNotBlank() }?.let(::parsePayload)
            val remotePayload = conflict.remotePayload.takeIf { it.isNotBlank() }?.let(::parsePayload)
            when (conflict.entityType) {
                "card" -> {
                    val current = vocab.getBySyncId(conflict.entitySyncId)
                    val local = localPayload?.let { cardFromPayload(it, conflict.entitySyncId, current?.id ?: 0L, vocab) }
                    val remote = if (conflict.remoteOperationType.endsWith("delete")) null else
                        remotePayload?.let { cardFromPayload(it, conflict.entitySyncId, current?.id ?: 0L, vocab) }
                    when (choice) {
                        ConflictChoice.KEEP_LOCAL -> vocab.resolveCard(conflict.entitySyncId, local, conflict.baseRevision)
                        ConflictChoice.KEEP_DRIVE -> vocab.resolveCard(conflict.entitySyncId, remote, conflict.baseRevision)
                        ConflictChoice.KEEP_BOTH -> {
                            requireNotNull(local) { "There is no local card version to keep" }
                            requireNotNull(remote) { "There is no Drive card version to keep" }
                            vocab.resolveCard(conflict.entitySyncId, local, conflict.baseRevision)
                            vocab.insert(remote.copy(id = 0L, syncId = newCardSyncId(), syncRevision = 0L, textId = null))
                        }
                    }
                }
                "list" -> {
                    val current = vocab.getListBySyncId(conflict.entitySyncId)
                    val local = localPayload?.let { listFromPayload(it, conflict.entitySyncId, current?.id ?: 0L) }
                    val remote = if (conflict.remoteOperationType.endsWith("delete")) null else
                        remotePayload?.let { listFromPayload(it, conflict.entitySyncId, current?.id ?: 0L) }
                    val chosen = if (choice == ConflictChoice.KEEP_DRIVE) remote else local
                    vocab.resolveList(conflict.entitySyncId, chosen, conflict.baseRevision)
                }
            }
            sync.markConflictResolved(conflictId, Clock.System.now().toEpochMilliseconds())
        } }
    }

    private suspend fun cardFromPayload(payload: JsonObject, syncId: String, id: Long, vocab: VocabDao): VocabEntry? {
        val listSyncId = payload.string("listSyncId")
        val list = listSyncId?.let { vocab.getListBySyncId(it) }
        if (listSyncId != null && list == null) return null
        return VocabEntry(
            id = id, lang = payload.string("lang") ?: return null, word = payload.string("word") ?: return null,
            sentence = payload.string("sentence").orEmpty(), meaning = payload.string("meaning"),
            dictionaryUrl = payload.string("dictionaryUrl"), meaningLang = payload.string("meaningLang") ?: "fa",
            listId = list?.id, learned = payload.boolean("learned") ?: false,
            createdAtMs = payload.long("createdAtMs") ?: 0L, leitnerBox = payload.long("leitnerBox")?.toInt() ?: 1,
            nextReviewAtMs = payload.long("nextReviewAtMs") ?: 0L, lastReviewedAtMs = payload.long("lastReviewedAtMs"),
            syncId = syncId, syncRevision = payload.long("revision") ?: 0L,
        )
    }

    private fun listFromPayload(payload: JsonObject, syncId: String, id: Long): VocabList? {
        val lang = payload.string("lang") ?: return null
        val name = payload.string("name") ?: return null
        return VocabList(
            id = id, lang = lang, name = name, createdAtMs = payload.long("createdAtMs") ?: 0L,
            syncId = syncId, syncRevision = payload.long("revision") ?: 0L,
        )
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    private fun newCardSyncId(): String = kotlin.uuid.Uuid.random().toString().replace("-", "")

    private suspend fun cardJson(card: VocabEntry, vocab: VocabDao): String {
        val listSyncId = card.listId?.let { vocab.getListById(it)?.syncId }
        return Json.encodeToString(buildJsonObject {
            put("syncId", card.syncId); put("revision", card.syncRevision); put("lang", card.lang)
            put("word", card.word); put("sentence", card.sentence); card.meaning?.let { put("meaning", it) }
            card.dictionaryUrl?.let { put("dictionaryUrl", it) }; put("meaningLang", card.meaningLang)
            put("listSyncId", listSyncId); put("learned", card.learned); put("createdAtMs", card.createdAtMs)
            put("leitnerBox", card.leitnerBox); put("nextReviewAtMs", card.nextReviewAtMs)
            card.lastReviewedAtMs?.let { put("lastReviewedAtMs", it) }
        })
    }

    private fun listJson(list: VocabList): String = Json.encodeToString(buildJsonObject {
        put("syncId", list.syncId); put("revision", list.syncRevision); put("lang", list.lang)
        put("name", list.name); put("createdAtMs", list.createdAtMs)
    })

    private fun sameCard(a: VocabEntry, b: VocabEntry): Boolean = sameCardContent(a, b)

    private fun parsePayload(payload: String?): JsonObject =
        payload?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: JsonObject(emptyMap())

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
    private fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
    private fun JsonObject.rawJson(): String = Json.encodeToString(this)
}

internal fun sameCardContent(a: VocabEntry, b: VocabEntry): Boolean =
    a.lang == b.lang && a.word == b.word && a.sentence == b.sentence && a.meaning == b.meaning &&
        a.dictionaryUrl == b.dictionaryUrl && a.meaningLang == b.meaningLang && a.syncRevision == b.syncRevision

internal fun opsToSkipAfterRestore(
    remote: List<SyncOperation>,
    localOperationIds: Set<String>,
    deviceId: String,
): Set<String> = remote.asSequence()
    .filter { it.deviceId == deviceId && it.operationId !in localOperationIds }
    .filter { it.entityType in setOf("card", "list", "review") && it.operationType in setOf("delete", "resolve_delete") }
    .map { it.operationId }
    .toSet()
