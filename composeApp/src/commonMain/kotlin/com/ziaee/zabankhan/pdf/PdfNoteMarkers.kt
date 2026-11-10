package com.ziaee.zabankhan.pdf

import com.ziaee.zabankhan.data.PdfTextNote
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class PdfNoteMarkerPosition(val x: Int, val y: Int)

/** Places each note beside its selected text, with a page-corner fallback while words load. */
internal fun placePdfNoteMarkers(
    notes: List<PdfTextNote>, words: List<PdfWord>, pageWidth: Int, pageHeight: Int,
    markerSize: Int, gap: Int, rtl: Boolean,
): List<PdfNoteMarkerPosition> {
    if (pageWidth <= 0 || pageHeight <= 0 || markerSize <= 0) return emptyList()
    val maxX = (pageWidth - markerSize).coerceAtLeast(0)
    val maxY = (pageHeight - markerSize).coerceAtLeast(0)
    val step = markerSize + gap
    val placed = mutableListOf<PdfNoteMarkerPosition>()
    notes.forEachIndexed { index, note ->
        val word = words.getOrNull(note.endWord)?.takeIf { note.startWord in 0..note.endWord }
        val x = if (word == null) pageWidth - markerSize - gap
            else ((if (rtl) word.rect.left else word.rect.right) * pageWidth).roundToInt() - markerSize / 2
        val y = if (word == null) gap + index * step else {
            val above = (word.rect.top * pageHeight).roundToInt() - markerSize - gap
            if (above >= 0) above else (word.rect.bottom * pageHeight).roundToInt() + gap
        }
        val boundedX = x.coerceIn(0, maxX)
        val preferredY = y.coerceIn(0, maxY)
        fun clear(candidate: Int) = placed.none {
            abs(it.x - boundedX) < markerSize && abs(it.y - candidate) < markerSize
        }
        val chosenY = (0..notes.size).asSequence()
            .flatMap { distance -> if (distance == 0) sequenceOf(preferredY)
                else sequenceOf(preferredY + distance * step, preferredY - distance * step) }
            .firstOrNull { it in 0..maxY && clear(it) } ?: preferredY
        placed += PdfNoteMarkerPosition(boundedX, chosenY)
    }
    return placed
}
