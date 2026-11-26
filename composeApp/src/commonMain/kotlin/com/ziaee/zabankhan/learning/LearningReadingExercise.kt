package com.ziaee.zabankhan.learning

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal class LearningReadingExercise private constructor(
    val id: String,
    val revision: Int,
    val level: String,
    val targetLanguage: String,
    val objective: Map<String, String>,
    val stimulus: String,
    val prompt: Map<String, String>,
    val options: Map<String, String>,
    private val acceptedOptionIds: List<String>,
    val evidence: String,
    private val feedback: Map<String, String>,
    private val distractorFeedback: Map<String, String>,
) {
    init {
        require(id.isNotBlank() && revision > 0 && level.isNotBlank() && targetLanguage.isNotBlank())
        require(listOf(objective, prompt, feedback).all { translations ->
            !translations["fa"].isNullOrBlank() &&
                translations.all { (locale, text) -> locale.isNotBlank() && text.isNotBlank() }
        })
        require(evidence.isNotBlank() && evidence in stimulus)
        require(options.size >= 2 && options.all { (key, text) -> key.isNotBlank() && text.isNotBlank() })
        require(options.values.toSet().size == options.size)
        require(acceptedOptionIds.size == 1 && acceptedOptionIds.single() in options)
        require(distractorFeedback.keys == options.keys - acceptedOptionIds.single())
        require(distractorFeedback.values.none { it.isBlank() })
    }

    fun evaluate(optionId: String): LearningReadingResult? {
        if (optionId !in options) return null
        val correct = optionId == acceptedOptionIds.single()
        val explanation = if (correct) feedback.getValue("fa") else distractorFeedback.getValue(optionId)
        return LearningReadingResult(correct, explanation)
    }

    companion object {
        fun fromJson(text: String): LearningReadingExercise = Json.decodeFromString(text)
    }
}

internal data class LearningReadingResult(val isCorrect: Boolean, val feedback: String)
