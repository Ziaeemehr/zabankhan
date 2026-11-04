package com.ziaee.zabankhan.translate.llm

/** Credentials are supplied by platform storage. Never include them in diagnostics. */
class LlmSecrets(val tokens: Map<String, String> = emptyMap()) {
    fun get(name: String): String? = tokens[name]
    fun last4(name: String): String? = get(name)?.takeLast(4)
    override fun toString(): String = "LlmSecrets(••••)"
}
