package com.ziaee.zabankhan.backup

import androidx.compose.runtime.Composable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Host supplies AuthenticationServices/Keychain without importing a Google SDK. */
object IosDriveBridge {
    var configured: Boolean = false
    var connected: Boolean = false
    var authorize: (((String?, String?) -> Unit) -> Unit)? = null
    var disconnect: (((String?) -> Unit) -> Unit)? = null
}
private object IosDriveAuth : DriveAuth {
    override val configured get() = IosDriveBridge.configured
    override val connected get() = IosDriveBridge.connected
    override suspend fun accessToken(): String? = suspendCancellableCoroutine { continuation ->
        val handler = IosDriveBridge.authorize
        if (handler == null) continuation.resume(null)
        else handler { token, error ->
            if (continuation.isActive) {
                if (error != null) continuation.resumeWithException(IllegalStateException(error))
                else continuation.resume(token)
            }
        }
    }
    override suspend fun disconnect() = suspendCancellableCoroutine<Unit> { continuation ->
        val handler = IosDriveBridge.disconnect
        if (handler == null) continuation.resume(Unit)
        else handler { error ->
            if (continuation.isActive) {
                if (error != null) continuation.resumeWithException(IllegalStateException(error))
                else continuation.resume(Unit)
            }
        }
    }
}
@Composable actual fun rememberDriveAuth(): DriveAuth = IosDriveAuth
