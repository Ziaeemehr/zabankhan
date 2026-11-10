package com.ziaee.zabankhan.sync.library

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.ziaee.zabankhan.backup.DriveBackupClient
import com.ziaee.zabankhan.backup.backupPaths
import com.ziaee.zabankhan.backup.libraryFileDeviceId
import com.ziaee.zabankhan.backup.sha256Hex
import com.ziaee.zabankhan.data.AppDatabase
import com.ziaee.zabankhan.data.LibrarySyncShadow
import com.ziaee.zabankhan.data.TextDocument
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.time.Clock

private val HASH = Regex("[0-9a-f]{64}")
private const val MAX_BLOB_BYTES = 256L * 1024 * 1024
private const val MAX_INLINE_BODY_BYTES = 64L * 1024 * 1024

data class BlobPlacement(val path: String, val download: Boolean)

/** Picks the final local path for a blob; never overwrites a different file. [hashOf] is null when no file exists. */
fun placeBlob(dir: String, name: String, hash: String, hashOf: (String) -> String?): BlobPlacement {
    val safe = name.substringAfterLast('/').substringAfterLast('\\').takeIf { it.isNotBlank() && it != "." && it != ".." } ?: hash
    val dot = safe.lastIndexOf('.')
    val base = if (dot > 0) safe.take(dot) else safe
    val ext = if (dot > 0) safe.drop(dot) else ""
    for (candidate in listOf(safe, "$base-${hash.take(8)}$ext", "$base-$hash$ext")) {
        val path = "$dir/$candidate"
        val existing = hashOf(path)
        if (existing == hash) return BlobPlacement(path, false)
        if (existing == null) return BlobPlacement(path, true)
    }
    error("No free file name for blob")
}

fun pathsSafeToDelete(deletedPath: String?, remainingPaths: Collection<String>): Boolean = deletedPath != null && deletedPath !in remainingPaths

data class LibrarySyncResult(val applied: Int, val uploadedBlobs: Int, val failedBlobs: Int)

private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.hash(key: String) = str(key)?.takeIf { HASH.matches(it) }

class LibrarySyncEngine(private val database: AppDatabase, http: HttpClient, private val deviceId: String, private val filesRoot: () -> String = { backupPaths().files }) {
    private val drive = DriveBackupClient(http)
    private val mutex = Mutex()
    private val fileHashes = mutableMapOf<String, FileHashCacheEntry>()
    private val fs = FileSystem.SYSTEM

    private fun hashOf(path: String): String? {
        val p = path.toPath()
        return if (runCatching { fs.metadata(p).isRegularFile }.getOrDefault(false)) runCatching { sha256Hex(p) }.getOrNull() else null
    }

    /** Resolves (downloading when needed) the files of one remote text. Files created here are appended to [created]. */
    private suspend fun prepareText(token: String, item: LibraryItem, old: TextDocument?, created: MutableList<String>): TextFilePaths {
        val p = requireNotNull(item.payload)
        val root = filesRoot()
        suspend fun file(dirName: String, hashKey: String, nameKey: String, oldPath: String?): String? {
            val hash = p.hash(hashKey) ?: return null
            if (oldPath != null && hashOf(oldPath) == hash) return oldPath
            val placement = placeBlob("$root/$dirName", p.str(nameKey) ?: hash, hash, ::hashOf)
            if (placement.download) { drive.downloadPdfBlob(token, hash, placement.path, MAX_BLOB_BYTES); created += placement.path }
            return placement.path
        }
        val inline = p.str("hasBodyInline") == "true"
        val bodyHash = p.hash("bodyHash")
        val inlineBody = if (inline && bodyHash != null) drive.downloadBlob(token, bodyHash, MAX_INLINE_BODY_BYTES).decodeToString() else null
        return TextFilePaths(
            file("pdfs", "pdfHash", "pdfName", old?.pdfPath),
            if (inline) null else file("text_bodies", "bodyHash", "bodyName", old?.bodyPath),
            file("text_images", "imageHash", "imageName", old?.imagePath),
            inlineBody,
        )
    }

    suspend fun syncOnce(token: String, accountId: String, beforeDestructiveApply: suspend () -> Unit): LibrarySyncResult = mutex.withLock {
        val dao = database.librarySync()
        val sync = database.sync()
        val builder = LibrarySnapshotBuilder(database, fileHashes)
        var current = builder.build()
        val shadow = dao.getAll().associateBy { it.key }
        val restore = sync.getMetadata("library_restore_pending") == "1"
        val initialize = shadow.isEmpty() || restore
        val now = Clock.System.now().toEpochMilliseconds()

        val named = drive.listLibraryFiles(token).map { requireNotNull(libraryFileDeviceId(it.name)) to it }
        require(named.map { it.first }.let { it.size == it.distinct().size }) { "Duplicate Drive library file names; no library changes were applied" }
        val remotes = named.filter { it.first != deviceId }.flatMap { (remoteId, f) ->
            LibraryWireFormat.decode(drive.downloadLibraryFile(token, f.id), accountId, remoteId).items
        }
        val ownFile = named.firstOrNull { it.first == deviceId }?.second
        suspend fun previousOwn(): Map<String, LibraryItem> =
            ownFile?.let { runCatching { LibraryWireFormat.decode(drive.downloadLibraryFile(token, it.id), accountId, deviceId).items }.getOrNull() }.orEmpty().associateBy { it.key }

        if (initialize) {
            // Adopt the remote ids of texts/folders both devices already have instead of duplicating them.
            val adopt = matchLocalToRemote(current.mapValues { it.value.second }, shadow.keys, remotes)
            if (adopt.isNotEmpty()) {
                database.useWriterConnection { connection -> connection.immediateTransaction {
                    adopt.forEach { (old, new) ->
                        if (old.startsWith("text:")) dao.renameTextSyncId(old.substringAfter(':'), new.substringAfter(':'))
                        else dao.renameFolderSyncId(old.substringAfter(':'), new.substringAfter(':'))
                    }
                } }
                current = builder.build()
            }
        }
        val frozen = builder.unreadableTexts.toSet()
        val remoteMax = remotes.groupBy { it.key }.mapValues { (_, v) -> v.maxOf { it.updatedAtMs } }
        val local = detectLocalChanges(current, shadow, now, initialize, restore, remoteMax, frozen).map { it.copy(deviceId = deviceId) }
        val winners = mergeWinners(local, remotes)
        val toApply = itemsToApply(local.associateBy { it.key }, winners).filter { it.key !in frozen }

        // All downloads happen before the transaction; a text whose blobs fail is skipped this round.
        var failed = 0
        val localPaths = mutableMapOf<String, TextFilePaths>()
        val createdFiles = mutableMapOf<String, List<String>>()
        val oldPaths = mutableMapOf<String, List<String>>()
        val skipped = mutableSetOf<String>()
        for (item in toApply.filter { it.kind == LibraryKinds.TEXT && !it.deleted }) {
            val old = dao.textBySyncId(item.key.substringAfter(':'))
            val created = mutableListOf<String>()
            try {
                localPaths[item.key] = prepareText(token, item, old, created)
                createdFiles[item.key] = created
                old?.let { oldPaths[item.key] = listOfNotNull(it.pdfPath, it.bodyPath, it.imagePath) }
            } catch (e: CancellationException) { created.forEach { fs.delete(it.toPath(), mustExist = false) }; throw e }
            catch (_: Exception) {
                created.forEach { fs.delete(it.toPath(), mustExist = false) }
                skipped += item.key; failed++
            }
        }
        val applicable = toApply.filter { it.key !in skipped }
        if (applicable.any { it.kind == LibraryKinds.TEXT && it.deleted && it.key in current }) beforeDestructiveApply()

        val applier = LibraryApplier(database)
        var applied: Set<String> = emptySet()
        var deletedPaths: List<String> = emptyList()
        val final = linkedMapOf<String, LibraryItem>().also { m -> local.forEach { m[it.key] = it } }
        database.useWriterConnection { connection -> connection.immediateTransaction {
            applied = applier.apply(applicable, localPaths).toSet()
            deletedPaths = applier.deletedFilePaths()
            applicable.filter { it.key in applied }.forEach { final[it.key] = it }
            val retry = (toApply.map { it.key }.toSet() - applied) + frozen
            val after = LibrarySnapshotBuilder(database, fileHashes).build()
            val rows = final.filterKeys { it !in retry }.values.map { item ->
                val hash = when {
                    item.deleted -> contentHash(item)
                    // Applied from remote: the DB now holds that content (a lossy apply is not a local edit).
                    item.key in applied -> after[item.key]?.first ?: contentHash(item)
                    // Local content that was uploaded: an edit made during the sync window then differs next round.
                    else -> current[item.key]?.first ?: contentHash(item)
                }
                LibrarySyncShadow(item.key, hash, item.updatedAtMs, item.deleted, item.blobs.joinToString(","))
            } + retry.mapNotNull { shadow[it] }
            dao.deleteAll(); dao.upsertAll(rows)
            sync.clearMetadata("library_restore_pending")
        } }

        val textRows = dao.texts()
        val remaining = textRows.flatMap { listOfNotNull(it.pdfPath, it.bodyPath, it.imagePath) }.toSet()
        // Old files of applied texts go only when no row references them any more (the applier keeps a path unless a new file replaced it).
        val replaced = oldPaths.filterKeys { it in applied }.values.flatten()
        val unused = deletedPaths + replaced + createdFiles.filterKeys { it !in applied }.values.flatten()
        unused.filter { pathsSafeToDelete(it, remaining) }.forEach { fs.delete(it.toPath(), mustExist = false) }

        val existing = drive.listPdfBlobs(token).toMutableSet()
        val failedHashes = mutableSetOf<String>()
        val bySyncId = textRows.associateBy { it.syncId }
        var uploaded = 0
        for (item in final.values.filter { it.kind == LibraryKinds.TEXT && !it.deleted }) {
            val row = bySyncId[item.key.substringAfter(':')] ?: continue
            val p = item.payload ?: continue
            suspend fun upload(hash: String?, path: String?, bytes: ByteArray? = null, pdf: Boolean = false) {
                if (hash == null || hash in existing || hash in failedHashes) return
                try {
                    when {
                        bytes != null -> drive.uploadBlobBytes(token, hash, bytes, existing)
                        path != null && fs.exists(path.toPath()) -> if (pdf) drive.uploadPdfBlob(token, hash, path, existing) else drive.uploadBlob(token, hash, path.toPath(), existing)
                        else -> error("Local file missing")
                    }
                    existing += hash; uploaded++
                } catch (e: CancellationException) { throw e } catch (_: Exception) { failed++; failedHashes += hash }
            }
            upload(p.hash("pdfHash"), row.pdfPath, pdf = true)
            if (p.str("hasBodyInline") == "true") upload(p.hash("bodyHash"), null, row.body.encodeToByteArray()) else upload(p.hash("bodyHash"), row.bodyPath)
            upload(p.hash("imageHash"), row.imagePath)
        }
        // A text whose blobs are not on Drive must not be published (others would skip it forever): keep its previous uploaded version, or omit it.
        val blocked = final.values.filter { it.kind == LibraryKinds.TEXT && !it.deleted && it.blobs.any { b -> b in failedHashes } }.map { it.key }.toSet()
        val previous = if (blocked.isNotEmpty() || frozen.isNotEmpty()) previousOwn() else emptyMap()
        val own = final.values.filter { it.key !in blocked && (!it.deleted || now - it.updatedAtMs <= TOMBSTONE_RETENTION_MS) }.map { it.copy(deviceId = deviceId) } +
            blocked.mapNotNull { previous[it]?.takeIf { p -> !p.deleted } } + frozen.mapNotNull { previous[it] }
        drive.uploadLibraryFile(token, deviceId, LibraryWireFormat.encode(LibraryStateFile(LIBRARY_SCHEMA_VERSION, accountId, deviceId, own)))
        LibrarySyncResult(applied.size, uploaded, failed)
    }
}

fun LibrarySyncResult.errorMessage(): String? = if (failedBlobs > 0) "$failedBlobs files could not be uploaded or downloaded" else null
