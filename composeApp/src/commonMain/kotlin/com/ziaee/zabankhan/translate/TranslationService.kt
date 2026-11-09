package com.ziaee.zabankhan.translate

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class TranslationService(private val http: HttpClient) {
    var lastEngine: String = "google-gtx"
        private set

    suspend fun translate(text: String, targetLang: String, sourceLang: String): String {
        val translated = try {
            parseGoogleResponse(googleRequest(text, targetLang, sourceLang))
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            try {
                val body = read("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=${encode(sourceLang)}&tl=${encode(targetLang)}&q=${encode(text)}")
                val first = Json.parseToJsonElement(body).jsonArray.first()
                val primitive = (if (first is JsonArray) first.first() else first) as? JsonPrimitive
                check(primitive?.isString == true) { "Invalid Chrome translation" }
                val value = primitive.content
                check(value.isNotBlank())
                lastEngine = "google-clients5"
                value.trim()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                val body = read("https://api.mymemory.translated.net/get?q=${encode(text)}&langpair=${encode(sourceLang)}|${encode(targetLang)}")
                val payload = Json.parseToJsonElement(body).jsonObject
                val status = payload["responseStatus"]
                check(status == null || (status as? JsonPrimitive)?.intOrNull == 200) { "MyMemory translation failed" }
                val primitive = payload.getValue("responseData").jsonObject.getValue("translatedText") as? JsonPrimitive
                check(primitive?.isString == true) { "Invalid MyMemory translation" }
                val value = primitive.content
                check(value.isNotBlank()) { "Empty translation from MyMemory" }
                lastEngine = "mymemory"
                value
            }
        }
        return polishTranslation(translated, targetLang)
    }

    /** Dictionary fallback uses Google's endpoints even when another translation engine is configured. */
    suspend fun translateWithGoogle(text: String, targetLang: String, sourceLang: String): String =
        polishTranslation(parseGoogleResponse(googleRequest(text, targetLang, sourceLang)), targetLang)

    suspend fun translateUnlessSameLanguage(text: String, targetLang: String, sourceLang: String): String? {
        if (baseLanguage(sourceLang) == baseLanguage(targetLang)) return null
        if (baseLanguage(sourceLang) == "auto") {
            try {
                val body = googleRequest(text, targetLang, sourceLang)
                val detected = (Json.parseToJsonElement(body).jsonArray.getOrNull(2) as? JsonPrimitive)?.contentOrNull
                if (detected != null && baseLanguage(detected) == baseLanguage(targetLang)) return null
                return polishTranslation(parseGoogleResponse(body), targetLang).takeUnless { isSameText(it, text) }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* Use the full provider fallback below. */ }
        }
        return translate(text, targetLang, sourceLang).takeUnless { isSameText(it, text) }
    }

    suspend fun translateLines(lines: List<String>, targetLang: String, sourceLang: String): List<String>? {
        if (lines.isEmpty()) return emptyList()
        val text = lines.joinToString("\n") { it.replace(Regex("[\\r\\n]+"), " ") }
        return splitTranslatedLines(translate(text, targetLang, sourceLang), lines.size)
    }

    suspend fun wordSenses(word: String, targetLang: String, sourceLang: String): List<WordSense> = try {
        parseWordSenses(googleRequest(word, targetLang, sourceLang, true))
            .map { it.copy(term = polishTranslation(it.term, targetLang)) }
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (_: Exception) { emptyList() }

    private suspend fun googleRequest(text: String, target: String, source: String, dictionary: Boolean = false): String =
        requestGoogleWithFallback { chrome ->
            read(googleSingleUrl(text, target, source, dictionary, chrome)).also {
                lastEngine = if (chrome) "google-clients5" else "google-gtx"
            }
        }

    private suspend fun read(url: String): String {
        val requestClient = http.config {
            install(HttpTimeout) { connectTimeoutMillis = 10_000; socketTimeoutMillis = 15_000 }
        }
        try {
            val response = requestClient.get(url) {
                expectSuccess = false
                header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36")
            }
            check(response.status.value in 200..299) { "Translation HTTP ${response.status.value}" }
            return response.bodyAsText()
        } finally { requestClient.close() }
    }

    companion object {
        fun baseLanguage(code: String): String = code.substringBefore('-').substringBefore('_').lowercase()
        fun isSameText(translated: String, source: String): Boolean {
            fun norm(s: String) = s.lowercase().replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک').filter { it.isLetterOrDigit() }
            return norm(translated) == norm(source)
        }
    }
}

data class WordSense(val partOfSpeech: String, val term: String, val score: Double)

internal fun splitTranslatedLines(translated: String, count: Int): List<String>? =
    translated.split("\n").map { it.trim() }.takeIf { it.size == count && it.none(String::isBlank) }

internal fun parseGoogleResponse(body: String): String {
    val segments = Json.parseToJsonElement(body).jsonArray[0].jsonArray
    val result = segments.mapNotNull { (it as? JsonArray)?.getOrNull(0)?.let { value ->
        (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    } }.joinToString("").trim()
    check(result.isNotEmpty()) { "Empty translation from Google endpoint" }
    return result
}

internal fun parseWordSenses(body: String): List<WordSense> = runCatching {
    val outer = Json.parseToJsonElement(body).jsonArray
    val groups = outer.getOrNull(1)?.takeUnless { it is JsonNull }?.jsonArray ?: return@runCatching emptyList()
    // Google's Persian dictionary data often drops the madda ("اپارتمان") that its own translation keeps.
    val direct = runCatching { parseGoogleResponse(body) }.getOrNull()
    fun spelled(term: String) = if (direct != null && term != direct && term == direct.replace('آ', 'ا')) direct else term
    val seen = mutableSetOf<String>()
    buildList {
        for (raw in groups) {
            val group = raw.jsonArray
            val pos = group[0].jsonPrimitive.content
            val scored = group.getOrNull(2)?.takeUnless { it is JsonNull }?.jsonArray
            val senses = if (!scored.isNullOrEmpty()) scored.map {
                val entry = it.jsonArray
                WordSense(pos, spelled(entry[0].jsonPrimitive.content), entry[3].jsonPrimitive.double)
            }.sortedByDescending { it.score } else group[1].jsonArray.map { WordSense(pos, spelled(it.jsonPrimitive.content), 0.0) }
            senses.filter { it.term.isNotBlank() && seen.add(it.term) }.forEach { add(it) }
        }
    }
}.getOrDefault(emptyList())

/** application/x-www-form-urlencoded, matching the original URLEncoder. */
internal fun encode(text: String): String = buildString {
    val digits = "0123456789ABCDEF"
    for (byte in text.encodeToByteArray()) {
        val n = byte.toInt() and 255
        when {
            n in 65..90 || n in 97..122 || n in 48..57 || n in listOf(45, 95, 46, 42) -> append(n.toChar())
            n == 32 -> append('+')
            else -> { append('%'); append(digits[n shr 4]); append(digits[n and 15]) }
        }
    }
}

internal fun googleSingleUrl(text: String, targetLang: String, sourceLang: String, dictionary: Boolean, chrome: Boolean): String {
    val endpoint = if (chrome) "https://clients5.google.com/translate_a/single?client=dict-chrome-ex" else "https://translate.googleapis.com/translate_a/single?client=gtx"
    return "$endpoint&sl=${encode(sourceLang)}&tl=${encode(targetLang)}&dt=t${if (dictionary) "&dt=bd" else ""}&q=${encode(text)}"
}

internal suspend fun requestGoogleWithFallback(request: suspend (Boolean) -> String): String {
    suspend fun validated(chrome: Boolean): String = request(chrome).also { Json.parseToJsonElement(it).jsonArray }
    return try { validated(false) } catch (cancelled: CancellationException) { throw cancelled
    } catch (_: Exception) { validated(true) }
}
