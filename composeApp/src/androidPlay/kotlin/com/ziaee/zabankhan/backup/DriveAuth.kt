package com.ziaee.zabankhan.backup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
private class PlayDriveAuth(val activity: Activity) : DriveAuth {
    override val configured = true
    private val preferences = activity.getSharedPreferences("drive-connection", Context.MODE_PRIVATE)
    override var connected by mutableStateOf(preferences.getBoolean("connected", false))
    val client = Identity.getAuthorizationClient(activity)
    var completion: kotlinx.coroutines.CancellableContinuation<String?>? = null
    lateinit var launchResolution: (IntentSenderRequest) -> Unit
    fun complete(token: String?) {
        if (token != null) {
            connected = true
            preferences.edit().putBoolean("connected", true).apply()
        }
        completion?.let { if (it.isActive) it.resume(token) }; completion = null
    }
    override suspend fun accessToken(): String? = suspendCancellableCoroutine { continuation ->
        check(completion == null)
        completion = continuation
        continuation.invokeOnCancellation { completion = null }
        client.authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE))).build())
            .addOnSuccessListener { result ->
                if (completion !== continuation) return@addOnSuccessListener
                if (result.hasResolution()) launchResolution(IntentSenderRequest.Builder(requireNotNull(result.pendingIntent).intentSender).build())
                else complete(result.accessToken)
            }.addOnFailureListener { error ->
                if (completion === continuation) { completion = null; if (continuation.isActive) continuation.resumeWithException(error) }
            }
    }
    override suspend fun disconnect() {
        suspendCancellableCoroutine<Unit> { continuation ->
            client.revokeAccess(RevokeAccessRequest.builder().setScopes(listOf(Scope(DRIVE_SCOPE))).build())
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(Unit) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }
        connected = false
        preferences.edit().clear().apply()
    }
}
@Composable actual fun rememberDriveAuth(): DriveAuth {
    val activity = requireNotNull(LocalContext.current.activity())
    val auth = remember(activity) { PlayDriveAuth(activity) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) auth.complete(null)
        else try { auth.complete(auth.client.getAuthorizationResultFromIntent(result.data).accessToken) }
        catch (error: Exception) {
            auth.completion?.let { if (it.isActive) it.resumeWithException(error) }; auth.completion = null
        }
    }
    auth.launchResolution = { launcher.launch(it) }
    DisposableEffect(auth) { onDispose { auth.completion?.cancel(); auth.completion = null } }
    return auth
}

internal actual suspend fun backgroundDriveAccessToken(): String? {
    val context = backupApplicationContext()
    if (!context.getSharedPreferences("drive-connection", Context.MODE_PRIVATE).getBoolean("connected", false)) return null
    return suspendCancellableCoroutine { continuation ->
        Identity.getAuthorizationClient(context).authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE))).build())
            .addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(if (result.hasResolution()) null else result.accessToken) }
            .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    }
}
