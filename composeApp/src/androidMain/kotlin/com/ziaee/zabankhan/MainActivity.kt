package com.ziaee.zabankhan

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.backup.*
import com.ziaee.zabankhan.data.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ziaee.zabankhan.files.*

class ZabankhanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.ziaee.zabankhan.speech.initializeSpeech(this)
        com.ziaee.zabankhan.pdf.initializePdf(this)
        initializeSecretStore(this)
        initializeBackup(this)
        BackupRuntime.onRestored = {
            container.close()
            container = newContainer()
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (intent != null) startActivity(intent)
        }
    }
    private fun newContainer(): AppContainer {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return AppContainer(androidDatabase(this), SettingsRepository(createPrefsStore(androidPrefsPath(this), scope), scope), HttpClient(OkHttp))
    }
    private val containerLock = Any()
    private var currentContainer by mutableStateOf<AppContainer?>(null)
    var container: AppContainer
        get() = synchronized(containerLock) { currentContainer ?: newContainer().also { currentContainer = it } }
        private set(value) { synchronized(containerLock) { currentContainer = value } }

}
class MainActivity : ComponentActivity() {
    private var request by mutableStateOf<AppOpenRequest?>(null)
    private var incomingFileJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        setContent { App((application as ZabankhanApp).container, request) { request = null } }
    }
    override fun onResume() {
        super.onResume()
        DriveSyncLifecycle.onAppActive((application as ZabankhanApp).container)
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            val container = (application as ZabankhanApp).container
            AutoBackup.onAppBackgrounded(container)
            DriveSyncLifecycle.onAppBackgrounded(container)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }
    private fun readIntent(intent: Intent?) {
        incomingFileJob?.cancel()
        val uri = incomingTextUri(intent)
        val fileShare = uri != null && (intent?.action == Intent.ACTION_VIEW || intent?.type?.startsWith("text/") == true)
        val sharedVideo = intent?.dataString?.let(com.ziaee.zabankhan.watch.YoutubeUrl::videoId)
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> if (intent.type == "text/plain") intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() else null
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        val base = when {
            intent?.getBooleanExtra("open_review", false) == true -> AppOpenRequest(review = true, id = android.os.SystemClock.elapsedRealtime())
            sharedVideo != null -> AppOpenRequest(youtubeVideoId = sharedVideo, id = android.os.SystemClock.elapsedRealtime())
            !text.isNullOrBlank() -> AppOpenRequest(text = text, id = android.os.SystemClock.elapsedRealtime())
            else -> null
        }
        request = if (fileShare) null else base
        if (fileShare) incomingFileJob = lifecycleScope.launch {
            val picked = try { withContext(Dispatchers.IO) { copyPickedFile(this@MainActivity, uri!!, listOf(FileKind.TEXT)) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            request = AppOpenRequest(file = picked, importFailed = picked == null, id = android.os.SystemClock.elapsedRealtime())
        }
        intent?.removeExtra("open_review")
        intent?.removeExtra(Intent.EXTRA_TEXT)
        intent?.removeExtra(Intent.EXTRA_PROCESS_TEXT)
        intent?.removeExtra(Intent.EXTRA_STREAM)
        intent?.data = null
        intent?.clipData = null
    }

}

internal fun incomingTextUri(intent: Intent?): android.net.Uri? {
    if (intent == null) return null
    if (intent.action == Intent.ACTION_SEND) {
        val stream = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            else legacyStream(intent)
        return stream ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }
    return intent.data?.takeIf { intent.action == Intent.ACTION_VIEW && it.scheme == "content" }
}

@Suppress("DEPRECATION")
private fun legacyStream(intent: Intent): android.net.Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri
