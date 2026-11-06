@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.grammar

import com.ziaee.zabankhan.resources.Res
import kotlinx.cinterop.*
import platform.CoreFoundation.CFURLCreateFromFileSystemRepresentation
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.*
import platform.CoreText.CTFontManagerRegisterFontsForURL
import platform.CoreText.kCTFontManagerScopeProcess
import platform.Foundation.*
import platform.UIKit.*
import kotlin.math.ceil
import kotlin.math.floor

private const val PAGE_WIDTH = 595.0
private const val PAGE_HEIGHT = 842.0
private const val MARGIN = 44.0
private const val FOOTER_HEIGHT = 46.0
private const val CELL_PADDING = 5.0

/** UIKit equivalent of the reference's A4 StaticLayout writer, with line-level page breaks. */
internal fun writeGrammarPdf(blocks: List<GrammarPdfBlock>, appName: String, path: String) {
    listOf("regular", "medium").forEach { weight ->
        val filePath = NSURL.URLWithString(Res.getUri("font/vazirmatn_$weight.ttf"))?.path
        if (filePath != null) memScoped {
            val url = CFURLCreateFromFileSystemRepresentation(null, filePath.cstr.ptr.reinterpret(), filePath.encodeToByteArray().size.toLong(), false)
            if (url != null) {
                try { CTFontManagerRegisterFontsForURL(url, kCTFontManagerScopeProcess, null) }
                finally { CFRelease(url) }
            }
        }
    }
    fun font(size: Double, bold: Boolean = false): UIFont =
        UIFont.fontWithName(if (bold) "Vazirmatn-Medium" else "Vazirmatn-Regular", size)
            ?: if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size)
    fun attributed(text: GrammarPdfText, size: Double, bold: Boolean = false, color: UIColor = UIColor.blackColor): NSAttributedString {
        val style = NSMutableParagraphStyle().apply {
            setAlignment(if (text.isRtl) NSTextAlignmentRight else NSTextAlignmentLeft)
            setBaseWritingDirection(if (text.isRtl) NSWritingDirectionRightToLeft else NSWritingDirectionLeftToRight)
        }
        val value = if (text.isRtl) text.text.replace('→', '←') else text.text
        return NSMutableAttributedString.create(string = value, attributes = mapOf<Any?, Any?>(
            NSFontAttributeName to font(size, bold), NSForegroundColorAttributeName to color,
            NSParagraphStyleAttributeName to style
        )).apply {
            text.bold.forEach { range ->
                if (range.first >= 0 && range.last < value.length && range.first <= range.last) {
                    addAttribute(NSFontAttributeName, font(size, true), NSMakeRange(range.first.toULong(), (range.last - range.first + 1).toULong()))
                }
            }
        }
    }
    fun slice(text: GrammarPdfText, start: Int, end: Int) = GrammarPdfText(
        text.text.substring(start, end), text.bold.mapNotNull { range ->
            val first = maxOf(start, range.first)
            val last = minOf(end - 1, range.last)
            if (first <= last) (first - start)..(last - start) else null
        }, text.isRtl
    )
    fun lines(text: GrammarPdfText, size: Double, width: Double, bold: Boolean = false): List<GrammarPdfText> {
        if (text.text.isEmpty()) return listOf(text)
        val result = mutableListOf<GrammarPdfText>()
        var start = 0
        while (start < text.text.length) {
            val newline = text.text.indexOf('\n', start).let { if (it < 0) text.text.length else it }
            if (newline == start) { result += slice(text, start, start); start++; continue }
            var low = start + 1
            var high = newline
            var end = low
            while (low <= high) {
                val mid = (low + high) / 2
                val fits = attributed(slice(text, start, mid), size, bold).size().useContents { this.width <= width }
                if (fits) { end = mid; low = mid + 1 } else high = mid - 1
            }
            if (end < newline) {
                val space = text.text.lastIndexOf(' ', end - 1)
                if (space > start) end = space
                if (end < text.text.length && text.text[end].isLowSurrogate() && end > start && text.text[end - 1].isHighSurrogate()) end--
                if (end == start) end = (start + 2).coerceAtMost(newline)
            }
            result += slice(text, start, end)
            start = end
            while (start < newline && text.text[start] == ' ') start++
            if (start == newline && newline < text.text.length) start++
        }
        return result
    }
    val width = PAGE_WIDTH - 2 * MARGIN
    val bottom = PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT
    check(UIGraphicsBeginPDFContextToFile(path, CGRectMake(0.0, 0.0, PAGE_WIDTH, PAGE_HEIGHT), null))
    var page = 0
    var y = MARGIN
    fun footer() {
        if (page == 0) return
        val top = bottom + 10.0
        UIColor.lightGrayColor.setStroke()
        UIBezierPath().apply {
            moveToPoint(CGPointMake(MARGIN, top))
            addLineToPoint(CGPointMake(PAGE_WIDTH - MARGIN, top))
            lineWidth = 0.7
            stroke()
        }
        attributed(GrammarPdfText(appName, rtl = false), 8.0, color = UIColor.grayColor)
            .drawInRect(CGRectMake(MARGIN, top + 2, width, 14.0))
        attributed(GrammarPdfText(page.toString(), rtl = true), 8.0, color = UIColor.grayColor)
            .drawInRect(CGRectMake(PAGE_WIDTH - MARGIN - 30, top + 2, 30.0, 14.0))
    }
    fun newPage() {
        footer()
        UIGraphicsBeginPDFPage()
        page++
        y = MARGIN
    }
    fun lineHeight(size: Double) = ceil(font(size).lineHeight * 1.25)
    fun flowing(text: GrammarPdfText, size: Double, bold: Boolean = false, indent: Double = 0.0, color: UIColor = UIColor.blackColor) {
        val height = lineHeight(size)
        lines(text, size, width - 2 * indent, bold).forEach { line ->
            if (page == 0 || y + height > bottom) newPage()
            attributed(line, size, bold, color).drawInRect(CGRectMake(MARGIN + indent, y, width - 2 * indent, height))
            y += height
        }
    }
    try {
        blocks.forEach { block ->
            when (block) {
                is GrammarPdfBlock.Heading -> {
                    val size = when (block.level) { 1 -> 18.0; 2 -> 13.5; else -> 11.5 }
                    y += if (page == 0) 0.0 else if (block.level == 1) 6.0 else 10.0
                    if (page != 0 && y + lineHeight(size) + 30 > bottom) newPage()
                    flowing(GrammarPdfText(block.text), size, true)
                    y += 6
                }
                is GrammarPdfBlock.Paragraph -> {
                    flowing(block.text, 11.0, indent = if (block.quote) 10.0 else 0.0,
                        color = if (block.quote) UIColor.darkGrayColor else UIColor.blackColor)
                    y += 7
                }
                is GrammarPdfBlock.Table -> {
                    val columns = block.header.size.coerceAtLeast(1)
                    val columnWidth = width / columns
                    val mirrored = (block.header + block.rows.mapNotNull { it.firstOrNull() }).any { it.isRtl }
                    (listOf(block.header) + block.rows).forEachIndexed { index, row ->
                        val rowLines = row.take(columns).map { lines(it, 9.5, columnWidth - 2 * CELL_PADDING, index == 0) }
                        val count = rowLines.maxOfOrNull { it.size } ?: 1
                        val height = lineHeight(9.5)
                        if (page == 0 || y + count * height + 2 * CELL_PADDING > bottom && y > MARGIN) newPage()
                        var first = 0
                        while (first < count) {
                            if (y + height + 2 * CELL_PADDING > bottom) newPage()
                            val shown = floor((bottom - y - 2 * CELL_PADDING) / height).toInt().coerceAtLeast(1).coerceAtMost(count - first)
                            val rowHeight = shown * height + 2 * CELL_PADDING
                            rowLines.forEachIndexed { column, cellLines ->
                                val left = MARGIN + (if (mirrored) columns - 1 - column else column) * columnWidth
                                UIColor.lightGrayColor.setStroke()
                                UIBezierPath.bezierPathWithRect(CGRectMake(left, y, columnWidth, rowHeight)).apply { lineWidth = 0.7; stroke() }
                                cellLines.drop(first).take(shown).forEachIndexed { line, value ->
                                    attributed(value, 9.5, index == 0).drawInRect(CGRectMake(left + CELL_PADDING, y + CELL_PADDING + line * height, columnWidth - 2 * CELL_PADDING, height))
                                }
                            }
                            y += rowHeight
                            first += shown
                        }
                    }
                    y += 9
                }
            }
        }
        if (page == 0) newPage()
        footer()
    } finally {
        UIGraphicsEndPDFContext()
    }
}
