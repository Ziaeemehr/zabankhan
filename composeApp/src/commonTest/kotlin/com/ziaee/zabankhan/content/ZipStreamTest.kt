package com.ziaee.zabankhan.content

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ZipStreamTest {
    @Test fun streamedZipMatchesInMemoryZipAndReadsBack() {
        val big = ByteArray(200_000) { (it * 31 % 251).toByte() } // spans several 64 KiB chunks
        val entries = linkedMapOf("manifest.json" to "{}".encodeToByteArray(), "pdfs/é.pdf" to big, "empty" to ByteArray(0))
        val sizes = entries.mapValues { it.value.size.toLong() }
        val out = Buffer()
        ZipArchive.writeStored(out, sizes) { name -> Buffer().write(entries.getValue(name)) }
        val streamed = out.readByteArray()
        assertContentEquals(ZipArchive.writeStored(entries), streamed)
        assertEquals(ZipArchive.storedSize(sizes), streamed.size.toLong())
        assertContentEquals(big, ZipArchive.read(streamed).getValue("pdfs/é.pdf"))
    }
}
