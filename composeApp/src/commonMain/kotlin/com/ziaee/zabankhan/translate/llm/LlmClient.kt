package com.ziaee.zabankhan.translate.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.Url
import kotlinx.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class LlmError(val kind: Kind, val detail: String? = null) : Exception(kind.name) {
    enum class Kind { MISSING_KEY, UNAUTHORIZED, PAYMENT, FORBIDDEN, NOT_FOUND, RATE_LIMIT, UNREACHABLE, BAD_RESPONSE }
}

internal fun providerErrorMessage(errorBody: String?, secret: String? = null): String? = runCatching {
    val error = Json.parseToJsonElement(errorBody.orEmpty()).jsonObject["error"]
    var message = (if (error is JsonObject) error["message"] else error)?.jsonPrimitive?.contentOrNull?.trim() ?: return@runCatching null
    secret?.trim()?.takeIf { it.length >= 4 }?.let { message = message.replace(it, "••••") }
    keyShaped.replace(message, "••••").take(200).takeIf { it.isNotEmpty() }
}.getOrNull()
private val keyShaped = Regex("\\b(sk|pk|or|key)[-_][A-Za-z0-9_-]{8,}|\\b[A-Za-z0-9_-]{32,}")

internal fun completionUrl(baseUrl: String, computer: Boolean): String {
    val base = baseUrl.trim().trimEnd('/')
    return base + (if (computer && !Url(base).encodedPath.endsWith("/v1")) "/v1" else "") + "/chat/completions"
}
internal fun rejectsJsonMode(errorBody: String?): Boolean = errorBody != null &&
    Regex("response_format|json_object|json mode", RegexOption.IGNORE_CASE).containsMatchIn(errorBody)
internal fun parseLlmContent(content: String): JsonObject = try {
    val start = content.indexOf('{')
    val end = content.lastIndexOf('}')
    if (start < 0 || end < start) throw LlmError(LlmError.Kind.BAD_RESPONSE)
    Json.parseToJsonElement(content.substring(start, end + 1)).jsonObject
} catch (_: Exception) { throw LlmError(LlmError.Kind.BAD_RESPONSE) }

class LlmClient(
    private val http: HttpClient,
    baseUrl: String,
    private val model: String,
    private val token: String?,
    private val computer: Boolean,
    private val provider: LlmProvider? = null,
    private val refindBaseUrl: (suspend () -> String?)? = null
) {
    private var activeBaseUrl = baseUrl
    internal suspend fun complete(prompt: LlmPrompt): JsonObject {
        suspend fun request(url: String): JsonObject = try { complete(prompt, true, url) }
            catch (_: JsonModeUnsupported) { complete(prompt, false, url) }
        return try { request(activeBaseUrl) } catch (error: LlmError) {
            if (error.kind != LlmError.Kind.UNREACHABLE) throw error
            val newUrl = refindBaseUrl?.invoke() ?: throw error
            activeBaseUrl = newUrl
            request(newUrl)
        }
    }
    private class JsonModeUnsupported : Exception()
    private suspend fun complete(prompt: LlmPrompt, jsonMode: Boolean, requestBaseUrl: String): JsonObject {
        val requestClient = http.config {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = if (computer) 4_000 else 10_000
                socketTimeoutMillis = 90_000
            }
        }
        try {
            val body = buildJsonObject {
                put("model", model); put("temperature", 0.2)
                putJsonArray("messages") {
                    add(buildJsonObject { put("role", "system"); put("content", prompt.system) })
                    add(buildJsonObject { put("role", "user"); put("content", prompt.user) })
                }
                if (jsonMode) putJsonObject("response_format") { put("type", "json_object") }
            }
            val response = requestClient.post(completionUrl(requestBaseUrl, computer)) {
                expectSuccess = false
                header("Content-Type", "application/json")
                token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
                if (provider == LlmProvider.OPENROUTER) {
                    header("HTTP-Referer", "https://github.com/Ziaeemehr/FrenchReaderSpike")
                    header("X-Title", "French Reader")
                }
                setBody(body.toString())
            }
            val value = response.bodyAsText()
            val status = response.status.value
            if (status !in 200..299) {
                if (jsonMode && status == 400 && rejectsJsonMode(value)) throw JsonModeUnsupported()
                throw LlmError(when (status) {
                    401 -> LlmError.Kind.UNAUTHORIZED
                    402 -> LlmError.Kind.PAYMENT
                    403 -> LlmError.Kind.FORBIDDEN
                    404 -> LlmError.Kind.NOT_FOUND
                    429 -> LlmError.Kind.RATE_LIMIT
                    else -> LlmError.Kind.BAD_RESPONSE
                }, providerErrorMessage(value, token))
            }
            val content = Json.parseToJsonElement(value).jsonObject.getValue("choices").jsonArray[0]
                .jsonObject.getValue("message").jsonObject.getValue("content").jsonPrimitive
            if (!content.isString) throw LlmError(LlmError.Kind.BAD_RESPONSE)
            return parseLlmContent(content.content)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: LlmError) { throw error
        } catch (error: JsonModeUnsupported) { throw error
        } catch (_: IOException) { throw LlmError(LlmError.Kind.UNREACHABLE)
        } catch (_: Exception) { throw LlmError(LlmError.Kind.BAD_RESPONSE)
        } finally { requestClient.close() }
    }
}
