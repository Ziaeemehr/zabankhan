package com.ziaee.zabankhan.sync

import com.ziaee.zabankhan.data.SyncJournalEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

const val DRIVE_SYNC_SCHEMA_VERSION = 1
const val DRIVE_SYNC_FILE_PREFIX = "zabankhan-sync-"
const val MAX_SYNC_FILE_BYTES = 32 * 1024 * 1024

@Serializable
data class SyncOperation(
    val operationId: String,
    val deviceId: String,
    val sequence: Long,
    val entityType: String,
    val entitySyncId: String,
    val baseRevision: Long?,
    val operationType: String,
    val payload: String?,
)

@Serializable
data class DeviceChangeFile(
    val schemaVersion: Int,
    val accountId: String,
    val deviceId: String,
    val lastSequence: Long,
    val operations: List<SyncOperation>,
)

data class ValidatedDeviceChangeFile(val file: DeviceChangeFile, val encodedBytes: ByteArray)

object SyncWireFormat {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun encode(file: DeviceChangeFile): ByteArray {
        require(file.schemaVersion == DRIVE_SYNC_SCHEMA_VERSION) { "Unsupported local sync schema" }
        return json.encodeToString(file).encodeToByteArray().also {
            require(it.size <= MAX_SYNC_FILE_BYTES) { "Sync journal is too large" }
        }
    }

    fun decode(bytes: ByteArray, expectedAccountId: String, expectedDeviceId: String? = null): ValidatedDeviceChangeFile {
        require(bytes.size <= MAX_SYNC_FILE_BYTES) { "Remote sync journal is too large" }
        val file = json.decodeFromString<DeviceChangeFile>(bytes.decodeToString())
        require(file.schemaVersion <= DRIVE_SYNC_SCHEMA_VERSION) { "Drive data was created by a newer app" }
        require(file.schemaVersion == DRIVE_SYNC_SCHEMA_VERSION) { "Unsupported Drive sync data version" }
        require(file.accountId == expectedAccountId) { "Drive data belongs to a different Google account" }
        require(file.accountId.isNotBlank() && file.deviceId.isNotBlank() && file.deviceId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "Invalid Drive sync file identity"
        }
        expectedDeviceId?.let { require(file.deviceId == it) { "Drive sync file has a mismatched device identity" } }
        require(file.operations.size <= 250_000) { "Drive sync journal contains too many operations" }
        require(file.operations.all { it.deviceId == file.deviceId && it.sequence > 0 }) { "Invalid Drive sync operation identity" }
        require(file.operations.map { it.operationId }.distinct().size == file.operations.size) { "Duplicate Drive sync operation IDs" }
        require(file.operations.map { it.sequence }.distinct().size == file.operations.size) { "Duplicate Drive sync sequence numbers" }
        require(file.lastSequence >= (file.operations.maxOfOrNull { it.sequence } ?: 0L)) { "Invalid Drive sync sequence cursor" }
        file.operations.forEach(::validateOperation)
        return ValidatedDeviceChangeFile(file, bytes)
    }

    private fun validateOperation(operation: SyncOperation) {
        val allowed = when (operation.entityType) {
            "card" -> setOf("upsert", "review_state", "delete", "resolve_upsert", "resolve_delete")
            "list" -> setOf("upsert", "delete", "resolve_upsert", "resolve_delete")
            "review" -> setOf("upsert", "delete")
            else -> error("Drive data contains an unsupported entity type")
        }
        require(operation.operationId.isNotBlank() && operation.entitySyncId.isNotBlank()) { "Invalid Drive sync operation identity" }
        require(operation.baseRevision == null || operation.baseRevision >= 0L) { "Invalid Drive sync base revision" }
        require(operation.operationType in allowed) { "Drive data contains an unsupported operation" }
        val needsPayload = operation.operationType.endsWith("upsert") || operation.operationType == "review_state" ||
            (operation.entityType == "review" && operation.operationType == "delete" && operation.payload != null)
        require(!needsPayload || operation.payload != null) { "Drive sync operation is missing its data" }
        operation.payload?.let { raw ->
            val data = runCatching { json.decodeFromString<JsonObject>(raw) }.getOrElse { error("Drive sync operation has invalid data") }
            require(data.string("syncId") == operation.entitySyncId || operation.entityType == "review") {
                "Drive sync operation identity does not match its data"
            }
            if (operation.entityType == "review" && operation.operationType == "upsert") {
                require(data.string("syncId") == operation.entitySyncId && data.string("cardSyncId") != null) {
                    "Drive review operation is missing its card reference"
                }
            }
            val required = when {
                operation.entityType == "card" && operation.operationType != "review_state" -> listOf("lang", "word", "revision")
                operation.entityType == "card" -> listOf("lang", "revision")
                operation.entityType == "list" -> listOf("lang", "name", "revision")
                operation.entityType == "review" && operation.operationType == "upsert" -> listOf("timestampMs", "cardSyncId")
                else -> emptyList()
            }
            require(required.all { data.containsKey(it) }) { "Drive sync operation is missing required fields" }
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.let { runCatching { it.toString().trim('"') }.getOrNull() }

    fun toWire(entry: SyncJournalEntry) = SyncOperation(
        operationId = entry.operationId,
        deviceId = entry.deviceId,
        sequence = entry.sequence,
        entityType = entry.entityType,
        entitySyncId = entry.entitySyncId,
        baseRevision = entry.baseRevision,
        operationType = entry.operationType,
        payload = entry.payload,
    )
}
