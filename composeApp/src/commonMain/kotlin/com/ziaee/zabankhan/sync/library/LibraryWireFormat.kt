package com.ziaee.zabankhan.sync.library

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object LibraryWireFormat {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun encode(file: LibraryStateFile): ByteArray {
        validate(file, file.accountId, fileNameDeviceId = file.deviceId)
        return json.encodeToString(file).encodeToByteArray().also { require(it.size <= MAX_LIBRARY_FILE_BYTES) { "Library state file is too large" } }
    }

    fun decode(bytes: ByteArray, expectedAccountId: String, fileNameDeviceId: String): LibraryStateFile {
        require(bytes.size <= MAX_LIBRARY_FILE_BYTES) { "Remote library state file is too large" }
        val file = json.decodeFromString<LibraryStateFile>(bytes.decodeToString())
        validate(file, expectedAccountId, fileNameDeviceId)
        return file
    }

    private fun validate(file: LibraryStateFile, expectedAccountId: String, fileNameDeviceId: String) {
        require(file.schemaVersion <= LIBRARY_SCHEMA_VERSION) { "Drive data was created by a newer app" }
        require(file.schemaVersion == LIBRARY_SCHEMA_VERSION) { "Unsupported Drive library data version" }
        require(file.accountId == expectedAccountId) { "Drive data belongs to a different Google account" }
        require(file.accountId.isNotBlank() && validId(file.deviceId)) { "Invalid Drive library file identity" }
        require(file.deviceId == fileNameDeviceId) { "Drive library file has a mismatched device identity" }
        require(file.items.map { it.key }.distinct().size == file.items.size) { "Duplicate library item keys" }
        file.items.forEach { item ->
            require(item.kind in LibraryKinds.all) { "Drive data contains an unsupported library item kind" }
            require(item.key.isNotBlank() && item.updatedAtMs >= 0) { "Invalid Drive library item identity" }
            require(item.deleted || item.payload != null) { "Drive library item is missing its data" }
            require(item.deviceId.isEmpty() || item.deviceId == file.deviceId) { "Invalid Drive library item device identity" }
        }
    }

    private fun validId(value: String) = value.isNotBlank() && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}
