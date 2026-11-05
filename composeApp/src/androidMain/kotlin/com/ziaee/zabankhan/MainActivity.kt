package com.ziaee.zabankhan

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.backup.*
import com.ziaee.zabankhan.data.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ZabankhanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.ziaee.zabankhan.speech.initializeSpeech(this)
        com.ziaee.zabankhan.pdf.initializePdf(this)
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readIntent(intent)
        setContent { App((application as ZabankhanApp).container, request) { request = null } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }
    private fun readIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> if (intent.type == "text/plain") intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() else null
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        request = when {
            intent?.getBooleanExtra("open_review", false) == true -> AppOpenRequest(review = true, id = android.os.SystemClock.elapsedRealtime())
            !text.isNullOrBlank() -> AppOpenRequest(text = text, id = android.os.SystemClock.elapsedRealtime())
            else -> null
        }
        intent?.removeExtra("open_review")
        intent?.removeExtra(Intent.EXTRA_TEXT)
        intent?.removeExtra(Intent.EXTRA_PROCESS_TEXT)
    }
}
