package com.ziaee.zabankhan.speech

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The singleton engine reports the actual spoken language, including non-active library texts. */
object SpeechActivity {
    private val current = MutableStateFlow<String?>(null)
    val language = current.asStateFlow()
    internal fun speaking(lang: String) { current.value = lang }
}
class ActivityTts(private val engine: Tts) : Tts by engine {
    override fun speak(text: String, langCode: String, rate: Float, onDone: () -> Unit) =
        speak(text, langCode, rate, onError = {}, onDone = onDone)

    override fun speak(text: String, langCode: String, rate: Float, onError: () -> Unit, onDone: () -> Unit) {
        learningSpeechLanguage(langCode)
        SpeechActivity.speaking(langCode)
        engine.speak(text, langCode, rate, onError, onDone)
    }
}
