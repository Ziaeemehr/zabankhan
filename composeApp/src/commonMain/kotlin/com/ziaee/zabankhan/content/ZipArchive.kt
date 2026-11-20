package com.ziaee.zabankhan.content

import okio.Buffer
import okio.buffer
import okio.use

internal expect fun inflateRaw(bytes: ByteArray, expectedSize: Int): ByteArray

/** ZIP32 only. Bounds and CRC are checked before callers receive any files. */
object ZipArchive {
    private fun ByteArray.u16(p: Int): Int {
        require(p >= 0 && p <= size - 2) { "Truncated ZIP" }
        return (this[p].toInt() and 255) or ((this[p + 1].toInt() and 255) shl 8)
    }
    private fun ByteArray.u32(p: Int): Long = u16(p).toLong() or (u16(p + 2).toLong() shl 16)
    fun read(bytes: ByteArray, maxBytes: Long = 100L * 1024 * 1024): Map<String, ByteArray> {
        val end = (bytes.size - 22 downTo maxOf(0, bytes.size - 65557)).firstOrNull {
            bytes.u32(it) == 0x06054b50L && it + 22 + bytes.u16(it + 20) == bytes.size
        } ?: error("Missing ZIP central directory")
        require(bytes.u16(end + 4) == 0 && bytes.u16(end + 6) == 0 && bytes.u16(end + 8) == bytes.u16(end + 10)) { "Multipart ZIP unsupported" }
        val count = bytes.u16(end + 10)
        require(count != 65535) { "ZIP64 unsupported" }
        val centralStart = bytes.u32(end + 16)
        require(centralStart + bytes.u32(end + 12) == end.toLong()) { "Invalid central directory" }
        var p = centralStart.toInt()
        var total = 0L
        val entries = linkedMapOf<String, ByteArray>()
        repeat(count) {
            require(bytes.u32(p) == 0x02014b50L) { "Invalid ZIP entry" }
            val flags = bytes.u16(p + 8)
            val method = bytes.u16(p + 10)
            val crc = bytes.u32(p + 16)
            val compressed = bytes.u32(p + 20)
            val uncompressed = bytes.u32(p + 24)
            val nameSize = bytes.u16(p + 28)
            val next = p.toLong() + 46 + nameSize + bytes.u16(p + 30) + bytes.u16(p + 32)
            require(next <= end && compressed <= Int.MAX_VALUE && uncompressed <= Int.MAX_VALUE) { "ZIP bounds exceeded" }
            require(flags and 1 == 0 && method in listOf(0, 8)) { "Unsupported ZIP compression/encryption" }
            val name = bytes.copyOfRange(p + 46, p + 46 + nameSize).decodeToString()
            require(name.isNotBlank() && !name.startsWith('/') && '\\' !in name && ':' !in name && '\u0000' !in name && name.split('/').none { it == ".." }) { "Unsafe ZIP path" }
            total += uncompressed
            require(total <= maxBytes) { "ZIP uncompressed size exceeds limit" }
            val local = bytes.u32(p + 42)
            require(local < centralStart && bytes.u32(local.toInt()) == 0x04034b50L) { "Invalid local ZIP entry" }
            val start = local + 30 + bytes.u16(local.toInt() + 26) + bytes.u16(local.toInt() + 28)
            require(start + compressed <= centralStart) { "Truncated ZIP data" }
            val packed = bytes.copyOfRange(start.toInt(), (start + compressed).toInt())
            val data = if (method == 0) packed else inflateRaw(packed, uncompressed.toInt())
            require(data.size.toLong() == uncompressed && crc32(data) == crc) { "ZIP checksum/size mismatch" }
            if (!name.endsWith('/')) {
                require(name !in entries) { "Duplicate ZIP entry" }
                entries[name] = data
            }
            p = next.toInt()
        }
        require(p == end) { "Invalid ZIP directory length" }
        return entries
    }
    fun writeStored(entries: Map<String, ByteArray>): ByteArray {
        require(entries.size < 65535)
        val out = Buffer()
        val central = Buffer()
        entries.forEach { (name, bytes) ->
            val n = name.encodeToByteArray()
            require(n.size < 65535)
            val offset = out.size
            val crc = crc32(bytes).toInt()
            out.writeIntLe(0x04034b50).writeShortLe(20).writeShortLe(0x800).writeShortLe(0).writeIntLe(0)
                .writeIntLe(crc).writeIntLe(bytes.size).writeIntLe(bytes.size).writeShortLe(n.size).writeShortLe(0).write(n).write(bytes)
            central.writeIntLe(0x02014b50).writeShortLe(20).writeShortLe(20).writeShortLe(0x800).writeShortLe(0).writeIntLe(0)
                .writeIntLe(crc).writeIntLe(bytes.size).writeIntLe(bytes.size).writeShortLe(n.size).writeShortLe(0).writeShortLe(0)
                .writeShortLe(0).writeShortLe(0).writeIntLe(0).writeIntLe(offset.toInt()).write(n)
        }
        val start = out.size.toInt()
        val length = central.size.toInt()
        out.writeAll(central)
        out.writeIntLe(0x06054b50).writeShortLe(0).writeShortLe(0).writeShortLe(entries.size).writeShortLe(entries.size)
            .writeIntLe(length).writeIntLe(start).writeShortLe(0)
        return out.readByteArray()
    }
    /** Exact size of the archive [writeStored] produces for entries of these names and byte sizes. */
    fun storedSize(entries: Map<String, Long>): Long =
        entries.entries.sumOf { (name, size) -> 76L + 2 * name.encodeToByteArray().size + size } + 22

    /**
     * Streams a stored (uncompressed) zip into [sink]. Each entry is opened twice (CRC pass, then copy)
     * and read in chunks, so memory stays flat however large the files are.
     */
    fun writeStored(sink: okio.BufferedSink, entries: Map<String, Long>, open: (String) -> okio.Source) {
        require(entries.size < 65535)
        val central = Buffer()
        var offset = 0L
        val chunk = ByteArray(64 * 1024)
        entries.forEach { (name, size) ->
            val n = name.encodeToByteArray()
            require(n.size < 65535 && size <= Int.MAX_VALUE && offset <= Int.MAX_VALUE)
            var crcState = -1
            var read = 0L
            open(name).buffer().use { source ->
                while (true) { val count = source.read(chunk); if (count == -1) break; crcState = crcUpdate(crcState, chunk, count); read += count }
            }
            check(read == size) { "File changed while backing up: $name" }
            val crc = crcState.inv()
            sink.writeIntLe(0x04034b50).writeShortLe(20).writeShortLe(0x800).writeShortLe(0).writeIntLe(0)
                .writeIntLe(crc).writeIntLe(size.toInt()).writeIntLe(size.toInt()).writeShortLe(n.size).writeShortLe(0).write(n)
            open(name).use { source -> check(sink.writeAll(source) == size) { "File changed while backing up: $name" } }
            central.writeIntLe(0x02014b50).writeShortLe(20).writeShortLe(20).writeShortLe(0x800).writeShortLe(0).writeIntLe(0)
                .writeIntLe(crc).writeIntLe(size.toInt()).writeIntLe(size.toInt()).writeShortLe(n.size).writeShortLe(0).writeShortLe(0)
                .writeShortLe(0).writeShortLe(0).writeIntLe(0).writeIntLe(offset.toInt()).write(n)
            offset += 30L + n.size + size
        }
        val length = central.size.toInt()
        sink.writeAll(central)
        sink.writeIntLe(0x06054b50).writeShortLe(0).writeShortLe(0).writeShortLe(entries.size).writeShortLe(entries.size)
            .writeIntLe(length).writeIntLe(offset.toInt()).writeShortLe(0)
    }

    private fun crcUpdate(start: Int, bytes: ByteArray, count: Int): Int {
        var crc = start
        for (i in 0 until count) {
            crc = crc xor (bytes[i].toInt() and 255)
            repeat(8) { crc = (crc ushr 1) xor (if (crc and 1 != 0) 0xedb88320.toInt() else 0) }
        }
        return crc
    }

    internal fun crc32(bytes: ByteArray): Long = crcUpdate(-1, bytes, bytes.size).inv().toLong() and 0xffffffffL
}
