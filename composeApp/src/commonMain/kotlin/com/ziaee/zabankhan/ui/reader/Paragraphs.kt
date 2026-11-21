package com.ziaee.zabankhan.ui.reader

import androidx.compose.ui.text.TextRange
import com.ziaee.zabankhan.lang.*

data class Paragraph(val text: String, val offset: Int) {
    val block: ParsedBlock by lazy { MarkdownParser.parse(text) }
    /** Absolute original UTF-16 offsets, one per displayed character. */
    val sourceOffsets: List<Int> by lazy { displaySourceOffsets(text, block).map { it + offset } }
}

/** Same heading/list/image/table boundaries as TextChunker, retaining original UTF-16 offsets. */
fun paragraphs(body: String): List<Paragraph> {
    val lines = Regex("[^\\r\\n]+").findAll(body).toList()
    val result = mutableListOf<Paragraph>()
    var start = -1
    var end = -1
    var table = false
    fun flush() {
        if (start >= 0) result += Paragraph(body.substring(start, end), start)
        start = -1; end = -1; table = false
    }
    val special = Regex("^(#{1,6}\\s+|([-*+•▪◦‣●■►▶✓✔➤→]|\\d+\\.)\\s+|!\\[[^]]*]\\(epubimg:)")
    for (line in lines) {
        if (line.value.isBlank()) { flush(); continue }
        val raw = line.value.trim()
        val isTable = raw.startsWith("|")
        val blankGap = end >= 0 && body.substring(end, line.range.first).count { it == '\n' } > 1
        if (blankGap || table != isTable || special.containsMatchIn(raw)) flush()
        if (start < 0) start = line.range.first
        end = line.range.last + 1
        table = isTable
        if (special.containsMatchIn(raw)) flush()
    }
    flush()
    return result
}

/** Map copied MarkdownParser output back through removed syntax and collapsed whitespace. */
internal fun displaySourceOffsets(raw: String, block: ParsedBlock): List<Int> {
    if (block.plainText.isEmpty()) return emptyList()
    if (block.type == BlockType.TABLE) return tableSourceOffsets(raw, block)
    val visible = BooleanArray(raw.length) { true }
    fun hide(start: Int, end: Int) { for (i in start.coerceAtLeast(0) until end.coerceAtMost(raw.length)) visible[i] = false }
    val heading = Regex("^\\s*#{1,6}\\s+").find(raw)
    val list = Regex("^\\s*([-*+•▪◦‣●■►▶✓✔➤→]|\\d+\\.)\\s+").find(raw)
    if (block.type == BlockType.HEADER) heading?.let { hide(it.range.first, it.range.last + 1) }
    if (block.type == BlockType.LIST_ITEM) list?.let { hide(it.range.first, it.range.last + 1) }
    Regex("!?\\[[^]]*]\\([^)]*\\)").findAll(raw).forEach { match ->
        val bracket = raw.indexOf(']', match.range.first)
        if (raw[match.range.first] == '!') hide(match.range.first, match.range.last + 1)
        else { hide(match.range.first, match.range.first + 1); hide(bracket, match.range.last + 1) }
    }
    MarkdownParser.SPAN_TAG.findAll(raw).forEach { hide(it.range.first, it.range.last + 1) }
    var cursor = 0
    return block.plainText.map { character ->
        var at = cursor
        while (at < raw.length && (!visible[at] || if (character.isWhitespace()) !raw[at].isWhitespace() && raw[at] != '|' else raw[at] != character)) at++
        if (at >= raw.length) at = cursor.coerceAtMost(raw.lastIndex)
        cursor = at + 1
        at
    }
}


/** Table padding emits synthetic tabs; map each real cell independently so it never consumes the next row. */
private fun tableSourceOffsets(raw: String, block: ParsedBlock): List<Int> {
    val lines = Regex("[^\\r\\n]+").findAll(raw).filter { it.value.isNotBlank() }.toList()
    val rows = listOfNotNull(lines.firstOrNull()) + lines.drop(2)
    val positions = MutableList(block.plainText.length) { 0 }
    rows.forEachIndexed { rowIndex, line ->
        val cells = block.tableRows.getOrNull(rowIndex) ?: return@forEachIndexed
        var start = line.range.first + line.value.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        var end = line.range.last + 1
        while (end > start && raw[end - 1].isWhitespace()) end--
        if (start < end && raw[start] == '|') start++
        if (start < end && raw[end - 1] == '|' && (end < 2 || raw[end - 2] != '\\')) end--
        val rawCells = mutableListOf<Pair<Int, Int>>()
        var cellStart = start
        var index = start
        while (index < end) {
            if (raw[index] == '\\' && index + 1 < end && raw[index + 1] == '|') { index += 2; continue }
            if (raw[index] == '|') { rawCells += cellStart to index; cellStart = index + 1 }
            index++
        }
        rawCells += cellStart to end
        cells.forEachIndexed { column, cell ->
            val source = rawCells.getOrNull(column)
            if (cell.start > 0 && positions.getOrNull(cell.start - 1) != null &&
                block.plainText[cell.start - 1].isWhitespace()) {
                positions[cell.start - 1] = source?.first?.minus(1)?.coerceAtLeast(line.range.first) ?: end
            }
            if (source != null && cell.start < cell.end) {
                val cellRaw = raw.substring(source.first, source.second)
                val plain = block.plainText.substring(cell.start, cell.end)
                val mapped = displaySourceOffsets(cellRaw, ParsedBlock(BlockType.PARAGRAPH, plainText = plain))
                mapped.forEachIndexed { local, original -> positions[cell.start + local] = source.first + original }
            }
        }
        // A row separator points at its actual newline; extra padded cells point at the row end.
        val rowEnd = cells.lastOrNull()?.end ?: 0
        if (rowEnd < positions.size && block.plainText[rowEnd] == '\n') positions[rowEnd] = line.range.last + 1
    }
    return positions
}

internal enum class ChunkStatus { PENDING, LOADING, READY, ERROR }
internal data class SentenceBoundary(val text: String, val offsetMs: Double, val durationMs: Double)
internal data class ChunkState(
    val block: ParsedBlock,
    val sourceOffsets: List<Int> = emptyList(),
    val status: ChunkStatus = ChunkStatus.PENDING,
    val sentences: List<SentenceBoundary> = emptyList(),
    val translation: String? = null,
    val translationStatus: ChunkStatus = ChunkStatus.PENDING,
    val translationSource: com.ziaee.zabankhan.translate.ExampleTranslation? = null
) {
    val text get() = block.plainText
    fun readingDisplayText() = text
    fun sourceOffset(index: Int): Int = sourceOffsets.getOrElse(index) { sourceOffsets.lastOrNull()?.plus(1) ?: index }
    fun sourceEnd(exclusive: Int): Int = if (exclusive <= 0) sourceOffset(0) else sourceOffset(exclusive - 1) + 1
}

internal sealed interface SelectionKind {
    data class Word(val word: String, val range: TextRange) : SelectionKind
    data class Phrase(val text: String, val range: TextRange) : SelectionKind
}

/** Copied selection classification, with language-aware word boundaries in place of French rules. */
internal fun classifySelection(text: String, selection: TextRange, lang: LanguageSpec): SelectionKind? {
    if (selection.collapsed || text.isEmpty()) return null
    val rawStart = selection.min.coerceIn(0, text.length)
    val rawEnd = selection.max.coerceIn(0, text.length)
    if (rawStart >= rawEnd) return null
    val tokens = tokenizeSurface(text, lang)
    val first = tokens.firstOrNull { it.end > rawStart && it.start < rawEnd } ?: return null
    val last = tokens.lastOrNull { it.end > rawStart && it.start < rawEnd } ?: return null
    val range = TextRange(first.start, last.end)
    return if (first == last) {
        val word = wordAt(text, last.end - 1, lang) ?: last
        SelectionKind.Word(word.text, range)
    } else SelectionKind.Phrase(text.substring(range.min, range.max), range)
}

/** Copy rendered text from the covered blocks while retaining source offsets for highlights. */
internal fun selectedReaderText(parts: List<Paragraph>, range: IntRange): String =
    parts.asSequence()
        .filter { it.offset <= range.last && it.offset + it.text.length > range.first }
        .mapNotNull { part ->
            val start = part.sourceOffsets.indexOfFirst { it in range }
            val end = part.sourceOffsets.indexOfLast { it in range }
            if (start < 0) null else part.block.plainText.substring(start, end + 1)
        }
        .joinToString("\n")

/** Reference follow thresholds, factored for deterministic viewport tests. */
internal fun followScrollDelta(itemTop: Float, itemSize: Float, viewportStart: Float,
    viewportEnd: Float, activeFraction: Float, entering: Boolean): Float? {
    val height = viewportEnd - viewportStart
    if (height <= 0f) return null
    if (entering) {
        val fits = itemTop >= viewportStart && itemTop + itemSize <= viewportEnd - height * .08f
        if (!fits) return itemTop - viewportStart
    }
    if (itemSize <= height) return null
    val activeTop = itemTop + activeFraction.coerceIn(0f, 1f) * itemSize
    return if (activeTop > viewportStart + height * .75f) activeTop - (viewportStart + height * .35f) else null
}

fun paragraphIndexAt(paragraphs: List<Paragraph>, offset: Int): Int =
    paragraphs.indexOfLast { it.offset <= offset }.coerceAtLeast(0)

/** Literal, non-overlapping matches; UTF-16 ranges remain in the source document. */
fun findTextMatches(body: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    return buildList {
        var from = 0
        while (from <= body.length - needle.length) {
            val at = body.indexOf(needle, from, ignoreCase = true)
            if (at < 0) break
            add(at until at + needle.length)
            from = at + needle.length
        }
    }
}

/** Same word snapping as the watch transcript, with invalid anchors rejected. */
internal fun phraseSelectionRange(words: List<IntRange>, anchor: Int, target: Int): IntRange? {
    if (anchor !in words.indices || target !in words.indices) return null
    return com.ziaee.zabankhan.watch.transcriptSelectionRange(words, anchor, target)
}
