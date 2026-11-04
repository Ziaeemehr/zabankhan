package com.ziaee.zabankhan

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

class ZabankhanApp : Application() {
    override fun onCreate() { super.onCreate(); com.ziaee.zabankhan.speech.initializeSpeech(this); com.ziaee.zabankhan.pdf.initializePdf(this) }
    val container by lazy {
        AppContainer(androidDatabase(this), SettingsRepository(createPrefsStore(androidPrefsPath(this))), HttpClient(OkHttp))
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ZabankhanApp).container
        setContent { App(container) }
    }
}
