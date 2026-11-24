package com.ziaee.zabankhan.update

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.data.nowMs
import com.ziaee.zabankhan.resources.*
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val AUTO_CHECK = "auto_check"
private const val LAST_CHECK = "last_check_ms"
private const val LAST_NOTIFIED = "last_notified_version"
private const val DAY_MS = 86_400_000L

private fun Context.updatePrefs() = getSharedPreferences("app_update", Context.MODE_PRIVATE)

@Suppress("DEPRECATION")
private fun Context.githubUpdateVersion(): String? {
    val installer = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            packageManager.getInstallerPackageName(packageName)
        }
    }.getOrNull()
    if (installer in setOf("com.android.vending", "com.farsitel.bazaar", "ir.mservices.market")) return null
    return runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
}

@Composable
internal actual fun UpdateReminder(http: HttpClient, snackbar: SnackbarHostState, show: Boolean) {
    val context = LocalContext.current
    val installed = remember(context) { context.githubUpdateVersion() } ?: return
    val prefs = remember(context) { context.updatePrefs() }
    val uri = LocalUriHandler.current
    val action = stringResource(Res.string.update_open_release)
    val message = stringResource(Res.string.update_available)
    LaunchedEffect(http, show) {
        val now = nowMs()
        val last = prefs.getLong(LAST_CHECK, 0L)
        if (!show || !prefs.getBoolean(AUTO_CHECK, true) || (last <= now && now - last < DAY_MS)) return@LaunchedEffect
        prefs.edit().putLong(LAST_CHECK, now).apply()
        val version = try {
            checkAndroidRelease(http, installed)
        } catch (_: TimeoutCancellationException) {
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return@LaunchedEffect
        if (version == prefs.getString(LAST_NOTIFIED, null)) return@LaunchedEffect
        prefs.edit().putString(LAST_NOTIFIED, version).apply()
        if (snackbar.showSnackbar(message, action, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
            runCatching { uri.openUri("$RELEASES_URL/tag/v$version") }
        }
    }
}

@Composable
internal actual fun UpdateSettings(http: HttpClient) {
    val context = LocalContext.current
    val installed = remember(context) { context.githubUpdateVersion() } ?: return
    val prefs = remember(context) { context.updatePrefs() }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var enabled by remember(prefs) { mutableStateOf(prefs.getBoolean(AUTO_CHECK, true)) }
    var checking by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<StringResource?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = enabled, role = Role.Switch, onValueChange = { value ->
                enabled = value
                prefs.edit().putBoolean(AUTO_CHECK, value).apply()
            }),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.update_auto_check), Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = null)
        }
        OutlinedButton(enabled = !checking, onClick = {
            checking = true
            available = null
            status = null
            scope.launch {
                try {
                    available = checkAndroidRelease(http, installed)
                    if (available == null) status = Res.string.update_up_to_date
                } catch (_: TimeoutCancellationException) {
                    status = Res.string.update_check_failed
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    status = Res.string.update_check_failed
                } finally {
                    checking = false
                }
            }
        }) {
            Text(stringResource(if (checking) Res.string.update_checking else Res.string.update_check))
        }
        status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
        available?.let { version ->
            Text(stringResource(Res.string.update_version_available, version))
            TextButton(onClick = {
                runCatching { uri.openUri("$RELEASES_URL/tag/v$version") }
            }) { Text(stringResource(Res.string.update_open_release)) }
        }
    }
}
