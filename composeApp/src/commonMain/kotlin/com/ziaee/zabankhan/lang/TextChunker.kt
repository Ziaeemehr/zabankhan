package com.ziaee.zabankhan.lang

/** Splits imported plain text or Markdown into deterministic TTS-sized blocks. */
object TextChunker {
    private const val MAX_CHUNK_CHARS = 1200
    private val HEADER_LINE = Regex("^#{1,6}\\s+.*$")
    private val LIST_LINE = Regex("^([-*+]|\\d+\\.)\\s+.*$")
    private val SYMBOL_BULLET = Regex("^[•▪◦‣●■►▶✓✔➤→]\\s+(.*)$")
    private val BARE_URL = Regex("(?i)(?:https?://|www\\.)\\S+")
    private val EMPTY_LABEL = Regex("(?i)^(?:source|lien)\\s*:\\s*$")
    private val ASCII_SEPARATOR = Regex("^(?:[-*_~=]\\s*){3,}$")
    private val EM_DASH_SEPARATOR = Regex("^(?:—\\s*){2,}$")
    private val EPUB_IMAGE_LINE = Regex("^!\\[[^]]*]\\(epubimg:[^)]+\\)$")

    fun chunk(rawText: String, lang: LanguageSpec): List<String> {
        val lines = rawText.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<String>()
        val current = mutableListOf<String>()
        val tableLines = mutableListOf<String>()

        fun flushTable() {
            if (tableLines.isEmpty()) return
            val table = tableLines.joinToString("\n")
            if (isTable(table)) blocks.add(table) else current.addAll(tableLines)
            tableLines.clear()
        }

        fun flush() {
            flushTable()
            if (current.isNotEmpty()) {
                blocks.add(current.joinToString(" ").trim())
                current.clear()
            }
        }

        for (rawLine in lines) {
            val line = cleanLine(rawLine)
            if (line != null && line.startsWith("|")) {
                if (tableLines.isEmpty() && current.isNotEmpty()) flush()
                tableLines.add(line)
                continue
            }
            flushTable()
            when {
                line == null -> flush()
                HEADER_LINE.matches(line) -> {
                    flush()
                    blocks.add(line)
                }
                LIST_LINE.matches(line) -> {
                    flush()
                    blocks.add(line)
                }
                EPUB_IMAGE_LINE.matches(line) -> {
                    flush()
                    blocks.add(line)
                }
                else -> current.add(line)
            }
        }
        flush()

        val out = mutableListOf<String>()
        for (block in blocks) {
            val isSpecial = HEADER_LINE.matches(block) || LIST_LINE.matches(block) ||
                EPUB_IMAGE_LINE.matches(block) || isTable(block)
            if (!isSpecial && block.length > MAX_CHUNK_CHARS) {
                out.addAll(splitLongParagraph(block, lang))
            } else {
                out.add(block)
            }
        }
        return out.filter { block ->
            EPUB_IMAGE_LINE.matches(block) || hasSpeechText(block)
        }
    }

    private fun cleanLine(rawLine: String): String? {
        var line = rawLine.trim()
        if (line.isEmpty() || isSeparatorLine(line)) return null

        SYMBOL_BULLET.matchEntire(line)?.let { match ->
            line = "- ${match.groupValues[1]}"
        }

        line = BARE_URL.replace(line) { match ->
            val start = match.range.first
            if (start >= 2 && line[start - 2] == ']' && line[start - 1] == '(') match.value else ""
        }.replace(Regex("[ \\t]+"), " ").trim()

        return line.takeUnless { it.isEmpty() || EMPTY_LABEL.matches(it) }
    }

    private fun isSeparatorLine(line: String): Boolean {
        if (ASCII_SEPARATOR.matches(line) || EM_DASH_SEPARATOR.matches(line)) return true
        return line.any { !it.isWhitespace() } &&
            line.all { it.isWhitespace() || it.code in 0x2500..0x257F }
    }

    private fun splitLongParagraph(paragraph: String, lang: LanguageSpec): List<String> {
        val pieces = splitSentences(paragraph, lang)
            .map { paragraph.substring(it) }.flatMap(::splitOversizedUnit)
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        for (p in pieces) {
            if (buf.isNotEmpty() && buf.length + 1 + p.length > MAX_CHUNK_CHARS) {
                out.add(buf.toString().trim())
                buf.clear()
            }
            if (buf.isNotEmpty()) buf.append(" ")
            buf.append(p)
        }
        if (buf.isNotEmpty()) out.add(buf.toString().trim())
        return out
    }

    private fun splitOversizedUnit(unit: String): List<String> {
        if (unit.length <= MAX_CHUNK_CHARS) return listOf(unit)
        val result = mutableListOf<String>()
        var remaining = unit.trim()
        while (remaining.length > MAX_CHUNK_CHARS) {
            val whitespace = remaining.lastIndexOf(' ', MAX_CHUNK_CHARS)
            val cut = whitespace.takeIf { it > 0 } ?: MAX_CHUNK_CHARS
            result += remaining.substring(0, cut).trimEnd()
            remaining = remaining.substring(cut).trimStart()
        }
        if (remaining.isNotEmpty()) result += remaining
        return result
    }
    private val TABLE_DELIMITER_ROW = Regex("^\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?$")
    private val INLINE_IMAGE = Regex("!\\[[^]]*]\\([^)]*\\)")
    private val INLINE_LINK = Regex("\\[([^]]*)]\\([^)]*\\)")

    private fun isTable(block: String): Boolean {
        val lines = block.trim().lines().map { it.trim() }
        return lines.size >= 2 && lines[0].startsWith("|") &&
            TABLE_DELIMITER_ROW.matches(lines[1])
    }

    private fun hasSpeechText(block: String): Boolean {
        val body = block.replace(Regex("^(?:#{1,6}|[-*+]|\\d+\\.)\\s+"), "")
        val visible = INLINE_LINK.replace(INLINE_IMAGE.replace(body, "")) { it.groupValues[1] }
        return visible.any { it.isLetterOrDigit() }
    }

}
