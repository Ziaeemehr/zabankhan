package com.ziaee.zabankhan

import androidx.compose.runtime.mutableStateOf
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
    return ComposeUIViewController { App(containerState.value) }
}
