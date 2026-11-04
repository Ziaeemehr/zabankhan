package com.ziaee.zabankhan.translate.llm

enum class TranslationEngine { GOOGLE, COMPUTER, ONLINE }

data class LlmPrefs(
    val engine: TranslationEngine = TranslationEngine.GOOGLE,
    val computerManual: Boolean = false,
    val computerUrl: String = "http://127.0.0.1:8080",
    val computerModel: String = "gemma4",
    val onlineProvider: LlmProvider = LlmProvider.OPENROUTER,
    val customBaseUrl: String = "",
    val onlineModel: String = onlineProvider.defaultModel,
    val experimentalAccepted: Boolean = false
) {
    val currentModel: String? get() = when (engine) {
        TranslationEngine.GOOGLE -> null
        TranslationEngine.COMPUTER -> computerModel
        TranslationEngine.ONLINE -> onlineModel
    }
}
