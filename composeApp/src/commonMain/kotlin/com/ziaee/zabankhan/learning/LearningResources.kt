package com.ziaee.zabankhan.learning

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class LearningResourceType {
    @SerialName("magazine") MAGAZINE,
    @SerialName("reader") READER,
    @SerialName("textbook") TEXTBOOK,
    @SerialName("audio") AUDIO,
    @SerialName("video") VIDEO,
    @SerialName("website") WEBSITE,
}

@Serializable
enum class LearningResourceAudience {
    @SerialName("children") CHILDREN,
    @SerialName("teenagers") TEENAGERS,
    @SerialName("general") GENERAL,
}

@Serializable
enum class LearningResourceStatus {
    @SerialName("draft") DRAFT,
    @SerialName("approved") APPROVED,
}

@Serializable
data class LearningResource(
    val id: String,
    val language: String,
    val type: LearningResourceType,
    val audience: LearningResourceAudience,
    val illustrated: Boolean,
    val series: String,
    val levels: List<String>,
    val descriptions: Map<String, String>,
    val provenance: String,
    val status: LearningResourceStatus,
)

@Serializable
private data class LearningResourceCatalog(
    val schemaVersion: Int,
    val entries: List<LearningResource>,
)

interface LearningResources {
    fun entries(language: String): List<LearningResource>
}

class LocalLearningResources private constructor(
    private val resources: List<LearningResource>,
) : LearningResources {
    override fun entries(language: String): List<LearningResource> =
        resources.filter { it.language == language }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val idPattern = Regex("[a-z0-9]+(?:[.-][a-z0-9]+)+")
        private val languagePattern = Regex("[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")
        private val levels = setOf("A1", "A2", "B1", "B2", "C1", "C2")

        fun fromJson(text: String): LocalLearningResources {
            val catalog = json.decodeFromString<LearningResourceCatalog>(text)
            require(catalog.schemaVersion == 1) { "Unsupported learning catalog schema" }
            val ids = mutableSetOf<String>()
            for (entry in catalog.entries) {
                require(idPattern.matches(entry.id) && ids.add(entry.id)) { "Invalid or duplicate resource ID" }
                require(languagePattern.matches(entry.language)) { "Invalid resource language" }
                require(entry.series.isNotBlank() && entry.provenance.isNotBlank()) { "Missing resource metadata" }
                require(entry.levels.isNotEmpty() && entry.levels.all { it in levels } &&
                    entry.levels.size == entry.levels.toSet().size) { "Invalid resource levels" }
                require("fa" in entry.descriptions && entry.descriptions.all { (locale, value) ->
                    languagePattern.matches(locale) && value.isNotBlank()
                }) { "Invalid resource descriptions" }
            }
            return LocalLearningResources(catalog.entries)
        }
    }
}
