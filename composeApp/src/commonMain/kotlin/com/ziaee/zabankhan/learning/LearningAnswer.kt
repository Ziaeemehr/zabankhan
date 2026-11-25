package com.ziaee.zabankhan.learning

data class TextAnswerPolicy(
    val ignoreCase: Boolean = false,
    val ignoreFinalPunctuation: Boolean = false,
)

internal expect fun normalizeLearningNfc(text: String): String

private val answerWhitespace = Regex("\\s+")

private fun normalizeAnswer(text: String, policy: TextAnswerPolicy): String {
    val spaced = text.replace('’', '\'').trim().replace(answerWhitespace, " ")
    val punctuated = if (policy.ignoreFinalPunctuation) {
        spaced.trimEnd('.', '!', '?', '؟', '…').trimEnd()
    } else {
        spaced
    }
    return normalizeLearningNfc(if (policy.ignoreCase) punctuated.lowercase() else punctuated)
}

fun matchesLearningAnswer(
    response: String,
    accepted: Set<String>,
    policy: TextAnswerPolicy = TextAnswerPolicy(),
): Boolean {
    val normalized = normalizeAnswer(response, policy)
    return normalized.isNotEmpty() && accepted.any { normalizeAnswer(it, policy) == normalized }
}

fun matchesLearningSelection(response: List<String>, accepted: Set<String>): Boolean =
    accepted.isNotEmpty() && response.size == accepted.size && response.toSet() == accepted
