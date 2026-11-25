package com.ziaee.zabankhan.learning

import io.ktor.http.URLProtocol
import io.ktor.http.Url
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
enum class LearningResourceRights {
    @SerialName("unknown") UNKNOWN,
    @SerialName("link_only") LINK_ONLY,
    @SerialName("download_allowed") DOWNLOAD_ALLOWED,
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
    val rights: LearningResourceRights,
    val officialUrl: String? = null,
)

@Serializable
private data class LearningResourceCatalog(
    val schemaVersion: Int,
    val entries: List<LearningResource>,
)

interface LearningResources {
    fun entries(language: String): List<LearningResource>
}

fun LearningResources.browse(language: String, query: String = "", level: String? = null): List<LearningResource> {
    val search = query.trim()
    return entries(language)
        .filter { it.status == LearningResourceStatus.APPROVED }
        .filter { level == null || level in it.levels }
        .filter { entry ->
            entry.series.contains(search, ignoreCase = true) ||
                entry.descriptions.values.any { it.contains(search, ignoreCase = true) }
        }
        .sortedWith(compareByDescending<LearningResource> {
            it.type == LearningResourceType.MAGAZINE && it.illustrated &&
                it.audience != LearningResourceAudience.GENERAL
        }.thenBy { it.series.lowercase() })
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

        private fun isOfficialHttpsUrl(raw: String): Boolean {
            if (!raw.startsWith("https://") || raw.any { it.isWhitespace() || it == '\\' }) return false
            val url = runCatching { Url(raw) }.getOrNull() ?: return false
            return url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() && url.user == null && url.password == null
        }

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
                if (entry.status == LearningResourceStatus.APPROVED) {
                    val officialUrl = entry.officialUrl
                    require(entry.rights != LearningResourceRights.UNKNOWN &&
                        officialUrl != null && isOfficialHttpsUrl(officialUrl)) { "Invalid official resource URL or rights" }
                }
                require("fa" in entry.descriptions && entry.descriptions.all { (locale, value) ->
                    languagePattern.matches(locale) && value.isNotBlank()
                }) { "Invalid resource descriptions" }
            }
            return LocalLearningResources(catalog.entries)
        }
    }
}
