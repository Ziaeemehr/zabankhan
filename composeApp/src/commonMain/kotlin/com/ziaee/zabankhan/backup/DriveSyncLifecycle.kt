package com.ziaee.zabankhan.backup

import com.ziaee.zabankhan.app.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Best-effort sync when a supported app returns to the foreground. */
object DriveSyncLifecycle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun onAppActive(container: AppContainer) {
        scope.launch { container.driveSync?.syncSilently() }
    }
}
