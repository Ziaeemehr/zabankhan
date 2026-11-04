package com.ziaee.zabankhan.discover

import com.ziaee.zabankhan.content.htmlToParagraphs
import com.ziaee.zabankhan.content.markupTags

/** Site selectors mirror French Reader; fallback chooses the largest paragraph container. */
object ArticleExtractor {
    fun extractFranceInfoArticle(html: String): String? = extractBlocks(html, "c-body", setOf("p", "h2", "h3"))
    fun extractRfiFacileTranscript(html: String): String? = extractBlocks(html, "m-transcription__content", setOf("p"))
    fun extract(html: String): String? = extractRfiFacileTranscript(html) ?: extractFranceInfoArticle(html) ?: extractFallback(html)
    fun extractFallback(html: String): String? {
        val candidates = scanBlocks(html, null, setOf("p"))
        val byContainer = linkedMapOf<Int, MutableList<Block>>()
        candidates.forEach { block -> block.parents.forEach { parent -> byContainer.getOrPut(parent) { mutableListOf() } += block } }
        return byContainer.values.maxByOrNull { blocks -> blocks.sumOf { it.text.length } }
            ?.joinToString("\n\n") { it.text }?.takeIf { it.isNotBlank() }
    }
    private fun extractBlocks(html: String, requiredClass: String, tags: Set<String>): String? = scanBlocks(html, requiredClass, tags)
        .joinToString("\n\n") { it.text }.takeIf { it.isNotBlank() }
    private data class Block(val parents: List<Int>, val text: String)
    private data class Node(val tag: String, val id: Int, val selected: Boolean, val ignored: Boolean)
    private fun scanBlocks(html: String, requiredClass: String?, blockTags: Set<String>): List<Block> {
        val tagPattern = Regex("<!--.*?-->|<[^>]+>", RegexOption.DOT_MATCHES_ALL)
        val stack = mutableListOf<Node>()
        val blocks = mutableListOf<Block>()
        var sequence = 0
        var blockStart: Int? = null
        var blockTag = ""
        var blockParents = listOf(0)
        val content = StringBuilder()
        var cursor = 0
        fun append(end: Int) {
            if (blockStart != null && stack.none { it.ignored }) content.append(html.substring(cursor, end))
        }
        fun flush() {
            if (blockStart != null) htmlToParagraphs(content.toString()).replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }?.let { blocks += Block(blockParents, it) }
            blockStart = null
            content.clear()
        }
        for (match in tagPattern.findAll(html)) {
            append(match.range.first)
            val tag = markupTags(match.value).firstOrNull()
            if (tag != null) {
                if (tag.closing) {
                    if (tag.name == blockTag && blockStart != null) flush()
                    val index = stack.indexOfLast { it.tag == tag.name }
                    if (index >= 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
                } else {
                    val classes = tag.attributes["class"].orEmpty().split(Regex("\\s+"))
                    val ignored = tag.name in setOf("script", "style", "nav", "header", "footer", "aside", "head") || "media-embed" in classes || stack.any { it.ignored }
                    val selected = requiredClass == null || requiredClass in classes || stack.any { it.selected }
                    if (tag.name in blockTags && selected && !ignored) {
                        flush()
                        blockStart = match.range.last + 1
                        blockTag = tag.name
                        // Paragraph wrappers are often per-paragraph: use the nearest semantic container.
                        blockParents = stack.lastOrNull { it.tag in setOf("article", "main") }?.let { listOf(it.id) }
                            ?: stack.filter { it.tag in setOf("section", "div") }.map { it.id }.ifEmpty { listOf(0) }
                    } else if (tag.name == "br" && blockStart != null && !ignored) content.append(' ')
                    if (tag.name !in setOf("br", "img", "meta", "link", "input", "hr", "source", "wbr") && !match.value.trimEnd().endsWith("/>")) stack += Node(tag.name, ++sequence, selected, ignored)
                }
            }
            cursor = match.range.last + 1
        }
        append(html.length)
        flush()
        return blocks
    }
}
