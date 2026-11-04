package com.ziaee.zabankhan.content

data class EpubChapter(val title: String, val text: String)
data class EpubBook(val title: String, val identifier: String, val chapters: List<EpubChapter>)
class EpubFormatException(message: String) : Exception(message)

object EpubReader {
    fun read(bytes: ByteArray): EpubBook {
        try {
            val entries = ZipArchive.read(bytes)
            val container = entries["META-INF/container.xml"]?.decodeToString() ?: error("Missing EPUB container")
            val opfPath = markupTags(container).firstOrNull { it.name == "rootfile" }?.attributes?.get("full-path") ?: error("Missing EPUB rootfile")
            val opf = entries[opfPath]?.decodeToString() ?: error("Missing EPUB package")
            val title = elementText(opf, "title") ?: "EPUB"
            val manifest = markupTags(opf).filter { it.name == "item" && !it.closing }.associateBy { it.attributes["id"] }
            val chapters = markupTags(opf).filter { it.name == "itemref" && !it.closing }.mapNotNull { ref ->
                val item = manifest[ref.attributes["idref"]] ?: return@mapNotNull null
                if (item.attributes["media-type"] !in listOf("application/xhtml+xml", "text/html") || "nav" in item.attributes["properties"].orEmpty().split(' ')) return@mapNotNull null
                val href = item.attributes["href"] ?: return@mapNotNull null
                val path = resolvePath(opfPath.substringBeforeLast('/', ""), href)
                val html = entries[path]?.decodeToString() ?: error("Missing EPUB chapter: $path")
                val text = htmlToParagraphs(html)
                if (text.isBlank()) null else EpubChapter((1..6).firstNotNullOfOrNull { elementText(html, "h$it") } ?: elementText(html, "title") ?: path.substringAfterLast('/'), text)
            }
            require(chapters.isNotEmpty()) { "No readable EPUB chapters" }
            return EpubBook(title, elementText(opf, "identifier") ?: title, chapters)
        } catch (e: Exception) { throw EpubFormatException(e.message ?: "Invalid EPUB") }
    }
    private fun resolvePath(directory: String, href: String): String {
        val path = href.substringBefore('#').substringBefore('?')
        val decoded = okio.Buffer().apply {
            var i = 0
            while (i < path.length) {
                if (path[i] == '%' && i + 2 < path.length) {
                    val n = path.substring(i + 1, i + 3).toIntOrNull(16)
                    if (n != null) { writeByte(n); i += 3; continue }
                }
                val end = if (path[i].isHighSurrogate() && i + 1 < path.length) i + 2 else i + 1
                writeUtf8(path.substring(i, end)); i = end
            }
        }.readUtf8()
        val components = mutableListOf<String>()
        for (part in "$directory/$decoded".split('/')) when (part) {
            "", "." -> Unit
            ".." -> { require(components.isNotEmpty()); components.removeAt(components.lastIndex) }
            else -> components += part
        }
        return components.joinToString("/")
    }
}
