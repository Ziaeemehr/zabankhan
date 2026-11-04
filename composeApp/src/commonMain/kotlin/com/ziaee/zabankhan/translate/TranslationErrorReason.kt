package com.ziaee.zabankhan.translate

import com.ziaee.zabankhan.translate.llm.LlmError

enum class TranslationErrorReason { MISSING_KEY, KEY, PAYMENT, FORBIDDEN, MODEL, RATE, UNREACHABLE, RESPONSE }
fun translationErrorReason(kind: LlmError.Kind?): TranslationErrorReason = when (kind) {
    LlmError.Kind.MISSING_KEY -> TranslationErrorReason.MISSING_KEY
    LlmError.Kind.UNAUTHORIZED -> TranslationErrorReason.KEY
    LlmError.Kind.PAYMENT -> TranslationErrorReason.PAYMENT
    LlmError.Kind.FORBIDDEN -> TranslationErrorReason.FORBIDDEN
    LlmError.Kind.NOT_FOUND -> TranslationErrorReason.MODEL
    LlmError.Kind.RATE_LIMIT -> TranslationErrorReason.RATE
    LlmError.Kind.UNREACHABLE -> TranslationErrorReason.UNREACHABLE
    else -> TranslationErrorReason.RESPONSE
}
