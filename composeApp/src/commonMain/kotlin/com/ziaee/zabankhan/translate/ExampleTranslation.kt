package com.ziaee.zabankhan.translate

import com.ziaee.zabankhan.translate.llm.LlmError
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

enum class ExampleSource { EDITED, BUNDLED, MODEL, GOOGLE, DICTIONARY }

data class ExampleTranslation(val text: String, val source: ExampleSource, val modelError: LlmError.Kind? = null, val engine: String? = null)

internal fun exampleModelError(error: Throwable): LlmError.Kind = when (error) {
    is LlmError -> error.kind
    is IOException -> LlmError.Kind.UNREACHABLE
    else -> LlmError.Kind.BAD_RESPONSE
}

internal suspend fun resolveExampleTranslation(
    override: () -> String?, bundled: () -> String?, personal: () -> String?,
    live: suspend () -> String?, google: suspend () -> String,
    forceModel: Boolean = false
): ExampleTranslation {
    override()?.let { return ExampleTranslation(it, ExampleSource.EDITED) }
    if (!forceModel) {
        personal()?.let { return ExampleTranslation(it, ExampleSource.MODEL) }
        bundled()?.let { return ExampleTranslation(it, ExampleSource.BUNDLED) }
    }
    var modelError: LlmError.Kind? = null
    try {
        val translated = live()
        override()?.let { return ExampleTranslation(it, ExampleSource.EDITED) }
        translated?.let { return ExampleTranslation(it, ExampleSource.MODEL) }
        if (forceModel) throw LlmError(LlmError.Kind.UNREACHABLE)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        override()?.let { return ExampleTranslation(it, ExampleSource.EDITED) }
        modelError = exampleModelError(error)
        if (forceModel) throw (error as? LlmError ?: LlmError(modelError))
    }
    val translated = try {
        google()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        override()?.let { return ExampleTranslation(it, ExampleSource.EDITED) }
        throw error
    }
    override()?.let { return ExampleTranslation(it, ExampleSource.EDITED) }
    return ExampleTranslation(translated, ExampleSource.GOOGLE, modelError)
}
