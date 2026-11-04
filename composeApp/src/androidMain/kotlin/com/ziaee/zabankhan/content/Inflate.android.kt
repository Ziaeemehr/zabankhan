package com.ziaee.zabankhan.content

import java.util.zip.Inflater

internal actual fun inflateRaw(bytes: ByteArray, expectedSize: Int): ByteArray {
    val inflater = Inflater(true)
    try {
        inflater.setInput(bytes)
        val result = ByteArray(expectedSize + 1)
        var offset = 0
        while (!inflater.finished() && offset < result.size) {
            val n = inflater.inflate(result, offset, result.size - offset)
            require(n > 0 || inflater.finished()) { "Invalid DEFLATE stream" }
            offset += n
        }
        require(inflater.finished() && offset == expectedSize && inflater.remaining == 0) { "Invalid DEFLATE length" }
        return result.copyOf(expectedSize)
    } finally { inflater.end() }
}
