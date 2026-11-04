package com.ziaee.zabankhan

import androidx.compose.ui.window.ComposeUIViewController
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

private val container by lazy {
    AppContainer(iosDatabase(), SettingsRepository(createPrefsStore(iosPrefsPath())), HttpClient(Darwin))
}
fun MainViewController() = ComposeUIViewController { App(container) }
