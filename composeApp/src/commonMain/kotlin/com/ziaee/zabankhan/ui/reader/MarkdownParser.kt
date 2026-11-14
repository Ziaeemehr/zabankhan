package com.ziaee.zabankhan.ui.reader

/**
 * How a TextChunker "block" should be displayed. TextChunker already
 * guarantees a HEADER or LIST_ITEM block is exactly one source line, and a
 * PARAGRAPH block is one or more wrapped lines -- see TextChunker's kdoc.
 */
enum class BlockType { HEADER, LIST_ITEM, PARAGRAPH, IMAGE, TABLE }

/**
 * A bold/italic/color run, measured as a [start, end) character range against the
 * block's plain (Markdown-syntax-stripped) display text, not against the
 * original Markdown source.
 */
data class EmphasisSpan(val start: Int, val end: Int, val bold: Boolean, val italic: Boolean, val colorArgb: Int? = null)

/** One table cell: a [start, end) range into the TABLE block's [ParsedBlock.plainText]. */
data class TableCell(val start: Int, val end: Int)

data class ParsedBlock(
    val type: BlockType,
    val headerLevel: Int = 0,
    val listOrdered: Boolean = false,
    val plainText: String,
    /** [plainText] without emoji/arrows/decorative symbols: the exact string sent to TTS and translation. */
    val spokenText: String = plainText,
    val emphasisSpans: List<EmphasisSpan> = emptyList(),
    val imageRef: String? = null,
    val imageAlt: String = "",
    /** TABLE only: rows of cells, header row first, every row padded to the same column count. */
    val tableRows: List<List<TableCell>> = emptyList()
)

/**
 * Turns one raw TextChunker block (which may still carry Markdown syntax)
 * into a [ParsedBlock]: a block type for rendering (heading size, list
 * bullet, plain paragraph) plus a Markdown-stripped [ParsedBlock.plainText]
 * -- the string ReadingScreen displays and highlights sentence-by-sentence,
 * symbols and emoji included -- and its [ParsedBlock.spokenText], the same
 * text with those symbols removed, which is what TTS/translation receive.
 * Bold/italic
 * markers are stripped too, with their positions recorded as
 * [EmphasisSpan]s so the UI can re-apply them on top of the highlighted
 * text.
 *
 * This is a small, pragmatic Markdown subset -- headings, unordered/ordered
 * list items, bold ("**x**" or "__x__"), italic ("*x*" or "_x_"), and
 * [text](url) links (kept as their visible text; the URL is dropped, never
 * auto-fetched), and inline span style colors (CSS names or #RGB/#RRGGBB).
 * Standalone EPUB image markers become image blocks. All other images are
 * dropped entirely, so remote images are never auto-loaded. Anything else
 * (tables, code fences, footnotes) just falls through to plain paragraph
 * text.
 */
object MarkdownParser {
    internal val SPAN_TAG = Regex("""</?span(?=\s|>)(?:"[^"]*"|'[^']*'|[^'">])*>""", RegexOption.IGNORE_CASE)
    private val STYLE_ATTRIBUTE = Regex("""(?:^|\s)style\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
    private val NAMED_COLORS = mapOf(
        "black" to 0x000000, "white" to 0xFFFFFF, "red" to 0xFF0000,
        "green" to 0x008000, "blue" to 0x0000FF, "yellow" to 0xFFFF00,
        "orange" to 0xFFA500, "purple" to 0x800080, "gray" to 0x808080,
        "grey" to 0x808080, "pink" to 0xFFC0CB, "cyan" to 0x00FFFF,
        "aqua" to 0x00FFFF, "magenta" to 0xFF00FF, "fuchsia" to 0xFF00FF,
        "navy" to 0x000080, "teal" to 0x008080, "maroon" to 0x800000,
        "lime" to 0x00FF00, "silver" to 0xC0C0C0, "olive" to 0x808000
    )

    private fun spanColor(tag: String): Int? {
        val attribute = STYLE_ATTRIBUTE.find(tag) ?: return null
        val style = attribute.groupValues[1].ifEmpty { attribute.groupValues[2] }
        return style.split(';').mapNotNull { declaration ->
            if (!declaration.substringBefore(':').trim().equals("color", ignoreCase = true)) return@mapNotNull null
            val value = declaration.substringAfter(':', "").trim().lowercase()
                .removeSurrounding("'").removeSurrounding("\"")
            val rgb = if (value.startsWith("#")) {
                val digits = value.drop(1)
                if (digits.length !in listOf(3, 6) || digits.any { it !in "0123456789abcdef" }) return@mapNotNull null
                val expanded = if (digits.length == 3) digits.flatMap { listOf(it, it) }.joinToString("") else digits
                expanded.toIntOrNull(16)
            } else NAMED_COLORS[value]
            rgb?.let { it or 0xFF000000.toInt() }
        }.lastOrNull()
    }
    private val HEADER = Regex("^(#{1,6})\\s+(.*)$")
    private val LIST_ITEM = Regex("^([-*+•▪◦‣●■►▶✓✔➤→]|\\d+\\.)\\s+(.*)$")
    private val EPUB_IMAGE = Regex("^!\\[([^]]*)]\\(epubimg:([^)]+)\\)$")
    private val TABLE_DELIMITER_ROW = Regex("^\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?$")

    /** True for a pipe-table block: a "|" header line followed by a "|---|" delimiter line. */
    fun isTable(rawBlock: String): Boolean {
        val lines = rawBlock.trim().lines().map { it.trim() }
        return lines.size >= 2 && lines[0].startsWith("|") && TABLE_DELIMITER_ROW.matches(lines[1])
    }

    fun parse(rawBlock: String): ParsedBlock {
        val trimmed = rawBlock.trim()

        EPUB_IMAGE.matchEntire(trimmed)?.let { match ->
            return ParsedBlock(
                type = BlockType.IMAGE,
                plainText = "",
                imageRef = match.groupValues[2],
                imageAlt = match.groupValues[1]
            )
        }

        if (isTable(trimmed)) return parseTable(trimmed)

        HEADER.find(trimmed)?.let { m ->
            val level = m.groupValues[1].length
            val (text, spans) = stripInlineEmphasis(m.groupValues[2])
            return ParsedBlock(BlockType.HEADER, headerLevel = level, plainText = text, emphasisSpans = spans)
        }

        LIST_ITEM.find(trimmed)?.let { m ->
            val ordered = m.groupValues[1].firstOrNull()?.isDigit() == true
            val (text, spans) = stripInlineEmphasis(m.groupValues[2])
            return ParsedBlock(BlockType.LIST_ITEM, listOrdered = ordered, plainText = text, emphasisSpans = spans)
        }

        // Plain paragraph: join any wrapped lines into one flowing string.
        val joined = trimmed.lines().joinToString(" ") { it.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
        val (text, spans) = stripInlineEmphasis(joined)
        return ParsedBlock(BlockType.PARAGRAPH, plainText = text, emphasisSpans = spans)
    }

    /**
     * Display text is the cells joined by tabs, rows by newlines, so every cell is a
     * plain substring for highlights and find. Speech reads it row by row instead.
     */
    private fun parseTable(trimmed: String): ParsedBlock {
        val lines = trimmed.lines().map { it.trim() }
        val rows = (listOf(lines[0]) + lines.drop(2)).filter { it.isNotEmpty() }.map(::splitRow)
        val columns = rows.maxOf { it.size }
        val text = StringBuilder()
        val spans = mutableListOf<EmphasisSpan>()
        val tableRows = rows.mapIndexed { rowIndex, cells ->
            if (rowIndex > 0) text.append('\n')
            (0 until columns).map { col ->
                if (col > 0) text.append('\t')
                val (cellText, cellSpans) = stripInlineEmphasis(
                    cells.getOrElse(col) { "" }.replace(Regex("\\s+"), " ").trim()
                )
                val start = text.length
                text.append(cellText)
                cellSpans.forEach { spans.add(it.copy(start = it.start + start, end = it.end + start)) }
                TableCell(start, text.length)
            }
        }
        val plain = text.toString()
        val spoken = tableRows.joinToString(" ") { row ->
            row.map { plain.substring(it.start, it.end) }.filter { it.isNotBlank() }
                .joinToString(", ") + "."
        }
        return ParsedBlock(
            BlockType.TABLE,
            plainText = plain,
            spokenText = spoken,
            emphasisSpans = spans,
            tableRows = tableRows
        )
    }

    /** Splits "| a | b |" into cells, keeping escaped "\\|" as a literal pipe. */
    private fun splitRow(line: String): List<String> {
        var body = line
        if (body.startsWith("|")) body = body.substring(1)
        if (body.endsWith("|") && !body.endsWith("\\|")) body = body.dropLast(1)
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length && body[i + 1] == '|') {
                cell.append('|'); i += 2; continue
            }
            if (c == '|') { cells.add(cell.toString().trim()); cell.clear() } else cell.append(c)
            i++
        }
        cells.add(cell.toString().trim())
        return cells
    }

    /**
     * Walks [raw] once, stripping `**bold**` / `__bold__` / `*italic*` /
     * `_italic_` / `[text](url)` / `![alt](url)` syntax and returning the
     * plain text plus emphasis spans measured against THAT plain text.
     * Unmatched delimiters (e.g. a lone "*" in normal prose) are left as
     * literal characters rather than risk mangling ordinary text.
     */
    private fun stripInlineEmphasis(raw: String, inheritedColor: Int? = null): Pair<String, List<EmphasisSpan>> {
        val out = StringBuilder()
        val spans = mutableListOf<EmphasisSpan>()
        var i = 0
        val n = raw.length
        val colorStack = mutableListOf<Int?>()
        var color = inheritedColor
        var colorStart = 0
        fun flushColor() {
            if (color != null && out.length > colorStart) {
                spans.add(EmphasisSpan(colorStart, out.length, bold = false, italic = false, colorArgb = color))
            }
            colorStart = out.length
        }

        while (i < n) {
            val c = raw[i]

            if (c == '<') {
                val tag = SPAN_TAG.matchAt(raw, i)
                if (tag != null) {
                    flushColor()
                    if (tag.value.startsWith("</")) {
                        color = if (colorStack.isEmpty()) inheritedColor else colorStack.removeAt(colorStack.lastIndex)
                    } else {
                        colorStack.add(color)
                        color = spanColor(tag.value) ?: color
                    }
                    i = tag.range.last + 1
                    continue
                }
            }

            // Image: ![alt](url) -- dropped entirely, no placeholder text.
            if (c == '!' && i + 1 < n && raw[i + 1] == '[') {
                val closeBracket = raw.indexOf(']', i + 2)
                val closeParen = if (closeBracket >= 0 && closeBracket + 1 < n && raw[closeBracket + 1] == '(')
                    raw.indexOf(')', closeBracket + 2) else -1
                if (closeBracket >= 0 && closeParen >= 0) {
                    i = closeParen + 1
                    continue
                }
            }

            // Link: [text](url) -> text (emphasis inside the link text still applies).
            if (c == '[') {
                val closeBracket = raw.indexOf(']', i + 1)
                val closeParen = if (closeBracket >= 0 && closeBracket + 1 < n && raw[closeBracket + 1] == '(')
                    raw.indexOf(')', closeBracket + 2) else -1
                if (closeBracket >= 0 && closeParen >= 0) {
                    flushColor()
                    val (innerText, innerSpans) = stripInlineEmphasis(raw.substring(i + 1, closeBracket), color)
                    val start = out.length
                    out.append(innerText)
                    innerSpans.forEach { spans.add(it.copy(start = it.start + start, end = it.end + start)) }
                    colorStart = out.length
                    i = closeParen + 1
                    continue
                }
            }

            // Bold: **text** or __text__
            val boldDelim = listOf("**", "__").firstOrNull { d -> raw.regionMatches(i, d, 0, d.length) }
            if (boldDelim != null) {
                val close = raw.indexOf(boldDelim, i + boldDelim.length)
                if (close > i) {
                    val start = out.length
                    flushColor()
                    val (inner, innerSpans) = stripInlineEmphasis(raw.substring(i + boldDelim.length, close), color)
                    out.append(inner)
                    innerSpans.forEach { spans += it.copy(start = it.start + start, end = it.end + start) }
                    if (out.length > start) spans.add(EmphasisSpan(start, out.length, bold = true, italic = false))
                    colorStart = out.length
                    i = close + boldDelim.length
                    continue
                }
            }

            // Italic: *text* or _text_
            if (c == '*' || c == '_') {
                val delim = c.toString()
                val close = raw.indexOf(delim, i + 1)
                if (close > i) {
                    val start = out.length
                    flushColor()
                    val (inner, innerSpans) = stripInlineEmphasis(raw.substring(i + 1, close), color)
                    out.append(inner)
                    innerSpans.forEach { spans += it.copy(start = it.start + start, end = it.end + start) }
                    if (out.length > start) spans.add(EmphasisSpan(start, out.length, bold = false, italic = true))
                    colorStart = out.length
                    i = close + 1
                    continue
                }
            }

            out.append(c)
            i++
        }
        flushColor()
        return out.toString() to spans
    }
}
