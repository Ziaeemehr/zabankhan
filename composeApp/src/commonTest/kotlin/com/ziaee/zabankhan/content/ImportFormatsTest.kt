package com.ziaee.zabankhan.content

import kotlin.test.*
import okio.Buffer

class ImportFormatsTest {
    @Test fun storedZipRoundTripAndCorruption() {
        val expected = linkedMapOf("chapter.txt" to "Bonjour é 🌍".encodeToByteArray(), "empty" to byteArrayOf())
        val zip = ZipArchive.writeStored(expected)
        val actual = ZipArchive.read(zip)
        expected.forEach { (key, bytes) -> assertContentEquals(bytes, actual[key]) }
        zip[41] = (zip[41].toInt() xor 1).toByte()
        assertFails { ZipArchive.read(zip) }
    }
    @Test fun rawDeflateZipFixture() {
        // A DEFLATE final uncompressed block, generated here (not a platform fixture).
        val bytes = "A deflated chapter with é and فارسی".encodeToByteArray()
        val packed = Buffer().writeByte(1).writeShortLe(bytes.size).writeShortLe(bytes.size xor 65535).write(bytes).readByteArray()
        val name = "chapter.xhtml".encodeToByteArray()
        val crc = ZipArchive.crc32(bytes).toInt()
        val local = Buffer().writeIntLe(0x04034b50).writeShortLe(20).writeShortLe(0).writeShortLe(8).writeIntLe(0)
            .writeIntLe(crc).writeIntLe(packed.size).writeIntLe(bytes.size).writeShortLe(name.size).writeShortLe(0).write(name).write(packed)
        val start = local.size.toInt()
        val central = Buffer().writeIntLe(0x02014b50).writeShortLe(20).writeShortLe(20).writeShortLe(0).writeShortLe(8).writeIntLe(0)
            .writeIntLe(crc).writeIntLe(packed.size).writeIntLe(bytes.size).writeShortLe(name.size).writeShortLe(0).writeShortLe(0)
            .writeShortLe(0).writeShortLe(0).writeIntLe(0).writeIntLe(0).write(name)
        val length = central.size.toInt()
        local.writeAll(central)
        local.writeIntLe(0x06054b50).writeShortLe(0).writeShortLe(0).writeShortLe(1).writeShortLe(1).writeIntLe(length).writeIntLe(start).writeShortLe(0)
        assertContentEquals(bytes, ZipArchive.read(local.readByteArray())["chapter.xhtml"])
    }
    @Test fun zipRejectsTraversalAndLimits() {
        assertFails { ZipArchive.read(ZipArchive.writeStored(mapOf("../outside" to byteArrayOf(1)))) }
        assertFails { ZipArchive.read(ZipArchive.writeStored(mapOf("large" to ByteArray(20))), maxBytes = 10) }
        assertFails { ZipArchive.read(byteArrayOf(1, 2, 3)) }
    }
    @Test fun htmlBlocksEntitiesAndIgnoredContent() {
        val html = "<html><head><title>Hidden</title></head><body><h1>Heading &amp; title</h1><p>A <b>bold</b> word.&#160;Fine.</p><script>bad()</script><style>bad</style><nav>Menu</nav><ul><li>One</li><li>Two<br>Next</li></ul></body></html>"
        assertEquals("Heading & title\n\nA bold word. Fine.\n\nOne\n\nTwo\n\nNext", htmlToParagraphs(html))
        assertEquals("é < > \" ' 🌍", decodeEntities("&eacute; &lt; &gt; &quot; &apos; &#x1F30D;"))
        assertEquals("A&B", markupTags("<rootfile full-path='A&amp;B'/>").single().attributes["full-path"])
    }
    @Test fun epubUsesSpineOrderAndRelativePaths() {
        val entries = mapOf(
            "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>",
            "OPS/book.opf" to "<package><metadata><dc:title>My book</dc:title><dc:identifier>id</dc:identifier></metadata><manifest><item id='a' href='Text/one.xhtml' media-type='application/xhtml+xml'/><item id='b' href='Text/two.xhtml' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='b'/><itemref idref='a'/></spine></package>",
            "OPS/Text/one.xhtml" to "<h1>One</h1><p>First.</p>",
            "OPS/Text/two.xhtml" to "<h1>Two</h1><p>Second.</p>"
        ).mapValues { it.value.encodeToByteArray() }
        val book = EpubReader.read(ZipArchive.writeStored(entries))
        assertEquals("My book", book.title)
        assertEquals(listOf("Two", "One"), book.chapters.map { it.title })
        assertEquals("Two\n\nSecond.", book.chapters.first().text)
    }
    @Test fun markdownKeepsHeadingsAsOwnParagraphsAndFrontMatterLevel() {
        assertEquals("Heading\n\nA bold word and link.\n\nNext\n\nEnd.", markdownToPlainText("# Heading\nA **bold** word and [link](https://example.com).\n\n## Next\nEnd."))
        assertEquals("B1", parseFrontMatter("---\ntitle: Book\nlevel: B1\n---\nText").level)
    }
}
