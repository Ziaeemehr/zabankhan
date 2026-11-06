package com.ziaee.zabankhan.backup
import androidx.compose.runtime.Composable
private object UnavailableDrive : DriveAuth {
    override val configured = false
    override val connected = false
    override suspend fun accessToken(): String? = null
    override suspend fun disconnect() {}
}
@Composable actual fun rememberDriveAuth(): DriveAuth = UnavailableDrive
