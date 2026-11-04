package com.ziaee.zabankhan.watch

import io.ktor.http.Url

object YoutubeUrl {
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")
    fun videoId(input: String): String? {
        val value = input.trim()
        if (idPattern.matches(value)) return value
        return runCatching {
            val uri = Url(if ("://" in value) value else "https://$value")
            if (uri.protocol.name !in listOf("http", "https") || !uri.user.isNullOrEmpty() || !uri.password.isNullOrEmpty()) return null
            val path = uri.encodedPath.trim('/').split('/')
            val id = when (uri.host.lowercase()) {
                "youtu.be", "www.youtu.be" -> path.singleOrNull()
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com" -> when {
                    path.firstOrNull() in listOf("shorts", "embed", "live", "v") && path.size == 2 -> path[1]
                    uri.encodedPath == "/watch" -> uri.parameters["v"]
                    else -> null
                }
                else -> null
            }
            id?.takeIf(idPattern::matches)
        }.getOrNull()
    }
    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
}
