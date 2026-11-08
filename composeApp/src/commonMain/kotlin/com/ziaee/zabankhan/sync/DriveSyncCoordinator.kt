package com.ziaee.zabankhan.sync

import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.backup.AutoBackup
import com.ziaee.zabankhan.backup.backgroundDriveAccessToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class DriveSyncUiState(
    val syncing: Boolean = false,
    val lastSuccessfulSyncMs: Long? = null,
    val conflicts: List<com.ziaee.zabankhan.data.SyncConflict> = emptyList(),
    val error: String? = null,
    val lastResult: SyncResult? = null,
)

class DriveSyncCoordinator(private val container: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engine = DriveSyncEngine(container.database, container.http, container.syncDeviceId)
    private val mutableState = MutableStateFlow(DriveSyncUiState())
    val state: StateFlow<DriveSyncUiState> = mutableState.asStateFlow()
    private var localChangeJob: kotlinx.coroutines.Job? = null

    init {
        scope.launch {
            combine(
                container.database.sync().observeMetadata("last_successful_sync_ms"),
                container.database.sync().observeUnresolvedConflicts(),
            ) { timestamp, conflicts ->
                mutableState.value.copy(lastSuccessfulSyncMs = timestamp?.toLongOrNull(), conflicts = conflicts)
            }.collect { mutableState.value = it }
        }
    }

    fun onLocalChange() {
        localChangeJob?.cancel()
        localChangeJob = scope.launch {
            delay(1_200)
            syncSilently()
        }
    }

    suspend fun syncSilently() {
        val token = try { backgroundDriveAccessToken() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutableState.value = mutableState.value.copy(error = error.message); return }
        if (!token.isNullOrBlank()) {
            try { syncNow(token) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* State already contains an actionable error. */ }
        }
    }

    suspend fun syncNow(token: String): SyncResult {
        mutableState.value = mutableState.value.copy(syncing = true, error = null)
        try {
            val result = engine.syncOnce(token) { initial ->
                if (initial) AutoBackup.protectBeforeSync(container)
            }
            mutableState.value = mutableState.value.copy(syncing = false, error = null, lastResult = result)
            return result
        } catch (cancelled: CancellationException) {
            mutableState.value = mutableState.value.copy(syncing = false)
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(syncing = false, error = error.message ?: "Sync failed")
            throw error
        }
    }

    suspend fun protectBeforeConflictResolution() = AutoBackup.protectBeforeSync(container)

    suspend fun resolveConflict(conflictId: String, choice: ConflictChoice, token: String): SyncResult {
        AutoBackup.protectBeforeSync(container)
        engine.resolveConflict(conflictId, choice)
        return syncNow(token)
    }

    fun close() { scope.cancel() }
}
