@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ziaee.zabankhan

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import androidx.compose.ui.window.ComposeUIViewController
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.backup.BackupRuntime
import com.ziaee.zabankhan.backup.installBackupLifecycle
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.reminder.initializeIosReviewReminders
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob

private fun newContainer(): AppContainer {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Keep iCloud device backups small: these are re-downloadable models/dictionaries and local backup copies of data that is backed up anyway.
    listOf("vosk", "own-dictionary", "Backups").forEach { com.ziaee.zabankhan.data.excludeFromICloudBackup("${com.ziaee.zabankhan.data.iosDocumentsDir()}/$it") }
    return AppContainer(iosDatabase(), SettingsRepository(createPrefsStore(iosPrefsPath(), scope), scope), HttpClient(Darwin))
}
private val containerState by lazy { mutableStateOf(newContainer()) }
fun MainViewController(): platform.UIKit.UIViewController {
    initializeIosReviewReminders()
    BackupRuntime.onRestored = {
        containerState.value.close()
        containerState.value = newContainer()
    }
    installBackupLifecycle { containerState.value }
    val request = mutableStateOf<AppOpenRequest?>(null)
    var requestId = 0L
    fun consumePendingShare() {
        val defaults = NSUserDefaults(suiteName = "group.com.ziaee.zabankhan")
        val text = defaults.stringForKey("pendingShare") ?: return
        defaults.removeObjectForKey("pendingShare")
        if (text.isNotBlank()) request.value = AppOpenRequest(text = text.take(100_000), id = ++requestId)
    }
    consumePendingShare()
    return ComposeUIViewController {
        DisposableEffect(Unit) {
            val center = NSNotificationCenter.defaultCenter
            val observer = center.addObserverForName(
                UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue,
            ) { consumePendingShare() }
            // Also check after installing the observer to cover activation during startup.
            consumePendingShare()
            onDispose { center.removeObserver(observer) }
        }
        App(containerState.value, request.value, onRequestHandled = { request.value = null })
    }
}
