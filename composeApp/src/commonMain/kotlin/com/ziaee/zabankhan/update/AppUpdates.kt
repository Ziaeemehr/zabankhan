package com.ziaee.zabankhan.update

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val RELEASES_URL = "https://github.com/Ziaeemehr/zabankhan/releases"
private const val LATEST_RELEASE_API = "https://api.github.com/repos/Ziaeemehr/zabankhan/releases/latest"

private fun versionParts(version: String): List<Int>? {
    val parts = version.split('.')
    if (parts.size != 3) return null
    return parts.map { it.toIntOrNull()?.takeIf { number -> number >= 0 } ?: return null }
}

internal fun newerAndroidRelease(body: String, installedVersion: String): String? {
    val release = Json.parseToJsonElement(body).jsonObject
    if (release["draft"]?.jsonPrimitive?.booleanOrNull == true ||
        release["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
    val version = requireNotNull(release["tag_name"]?.jsonPrimitive?.contentOrNull).removePrefix("v")
    val latest = requireNotNull(versionParts(version))
    val installed = versionParts(installedVersion.substringBefore('-')) ?: return null
    val changed = latest.indices.firstOrNull { latest[it] != installed[it] } ?: return null
    if (latest[changed] < installed[changed]) return null
    val apkName = "zabankhan-$version-play.apk"
    return version.takeIf {
        release["assets"]?.jsonArray.orEmpty().any { asset ->
            val fields = asset.jsonObject
            fields["name"]?.jsonPrimitive?.contentOrNull == apkName &&
                fields["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
        }
    }
}

internal suspend fun checkAndroidRelease(http: HttpClient, installedVersion: String): String? = withTimeout(10_000) {
    val response = http.get(LATEST_RELEASE_API) { header("Accept", "application/vnd.github+json") }
    check(response.status.isSuccess()) { "GitHub release request failed: ${response.status}" }
    newerAndroidRelease(response.bodyAsText(), installedVersion)
}

@Composable internal expect fun UpdateReminder(http: HttpClient, snackbar: SnackbarHostState, show: Boolean)
@Composable internal expect fun UpdateSettings(http: HttpClient)
