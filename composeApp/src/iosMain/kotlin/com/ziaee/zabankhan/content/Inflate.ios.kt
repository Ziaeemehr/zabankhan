@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.content

import kotlinx.cinterop.*
import platform.zlib.*

internal actual fun inflateRaw(bytes: ByteArray, expectedSize: Int): ByteArray = memScoped {
    val stream = alloc<z_stream>()
    stream.zalloc = null; stream.zfree = null; stream.opaque = null
    require(inflateInit2_(stream.ptr, -15, ZLIB_VERSION, sizeOf<z_stream>().toInt()) == Z_OK) { "Cannot initialize DEFLATE" }
    try {
        val output = ByteArray(expectedSize + 1)
        bytes.usePinned { input -> output.usePinned { target ->
            stream.next_in = input.addressOf(0).reinterpret()
            stream.avail_in = bytes.size.toUInt()
            stream.next_out = target.addressOf(0).reinterpret()
            stream.avail_out = output.size.toUInt()
            require(inflate(stream.ptr, Z_FINISH) == Z_STREAM_END && stream.total_out.toLong() == expectedSize.toLong() && stream.avail_in == 0u) { "Invalid DEFLATE stream" }
        } }
        output.copyOf(expectedSize)
    } finally { inflateEnd(stream.ptr) }
}
