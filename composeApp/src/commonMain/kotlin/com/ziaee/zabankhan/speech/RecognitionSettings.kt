package com.ziaee.zabankhan.speech

import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.lang.LanguageSpec

object RecognitionSettings {
    var engine: String = "offline"; internal set
}
data class RecognitionSupport(val downloadableModels: Boolean, val systemAvailable: Boolean, val onDeviceAvailable: Boolean)
expect fun recognitionSupport(language: LanguageSpec): RecognitionSupport
expect suspend fun changeOfflineModel(directory: String, change: () -> Unit)
