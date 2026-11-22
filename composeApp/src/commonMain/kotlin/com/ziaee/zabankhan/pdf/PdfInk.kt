package com.ziaee.zabankhan.pdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class InkCurveSegment(val controlX: Float, val controlY: Float, val endX: Float, val endY: Float)

internal const val PDF_INK_MIN_WIDTH = .0015f
internal const val PDF_INK_MAX_WIDTH = .012f
internal const val PDF_ERASER_MIN_SIZE = .005f
internal const val PDF_ERASER_DEFAULT_SIZE = .015f
internal const val PDF_ERASER_MAX_SIZE = .06f

/** Suppress small deviations in screen space and let larger moves pass with minimal lag. */
internal fun smoothInkPoint(previous: Offset, raw: Offset, page: Size, pixelsPerDp: Float): Offset {
    val delta = raw - previous
    val distanceDp = Offset(delta.x * page.width, delta.y * page.height).getDistance() / pixelsPerDp
    val response = ((distanceDp - 1f) / 7f).coerceIn(0f, 1f)
    val weight = .2f + .8f * response
    return previous + delta * weight
}

/** Quadratic smoothing segments: each interior sample controls a curve ending at the next midpoint. */
fun inkCurveSegments(points: List<Float>): List<InkCurveSegment> {
    val count = points.size / 2
    if (count < 3) return emptyList()
    return (1 until count - 1).map { index ->
        val x = points[index * 2]; val y = points[index * 2 + 1]
        InkCurveSegment(x, y, (x + points[(index + 1) * 2]) / 2f, (y + points[(index + 1) * 2 + 1]) / 2f)
    }
}

@Serializable
data class InkStroke(
    val colorKey: String = "primary",
    val width: Float = .004f,
    val points: List<Float>,
    val highlighter: Boolean = false,
)

fun encodeInk(strokes: List<InkStroke>): String = Json.encodeToString(strokes.map { stroke ->
    stroke.copy(points = stroke.points.map { kotlin.math.round(it * 10_000f) / 10_000f })
})

private fun validInkColor(value: String): Boolean = value in setOf("primary", "secondary", "tertiary") ||
    (value.length == 9 && value[0] == '#' && value.drop(1).all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' })

fun decodeInk(value: String): List<InkStroke> = try {
    Json.decodeFromString<List<InkStroke>>(value).filter { stroke ->
        validInkColor(stroke.colorKey) &&
            stroke.width.isFinite() && stroke.width > 0f && stroke.width <= 1f &&
            stroke.points.size >= 2 && stroke.points.size % 2 == 0 &&
            stroke.points.all { it.isFinite() && it in 0f..1f }
    }
} catch (_: IllegalArgumentException) { emptyList() }

/** Keep points at least [distance] apart in normalized page coordinates. */
fun simplifyInkPoints(points: List<Float>, distance: Float = .0004f): List<Float> {
    val result = mutableListOf<Float>()
    for (i in 0 until points.size - 1 step 2) {
        if (result.isEmpty() || squaredDistance(points[i], points[i + 1], result[result.size - 2], result.last()) >= distance * distance) {
            result += points[i]; result += points[i + 1]
        }
    }
    return result
}

/** Erase whole strokes. Scale normalized Y by page height / width to measure in page-width units. */
fun inkHit(stroke: InkStroke, x: Float, y: Float, threshold: Float = PDF_ERASER_DEFAULT_SIZE, yScale: Float = 1f): Boolean {
    val points = stroke.points
    val scaledY = y * yScale
    for (i in 0 until points.size - 1 step 2) {
        val ax = points[i]; val ay = points[i + 1] * yScale
        val bx = points.getOrElse(i + 2) { ax }; val by = points.getOrElse(i + 3) { points[i + 1] } * yScale
        val dx = bx - ax; val dy = by - ay
        val length = dx * dx + dy * dy
        val t = if (length == 0f) 0f else (((x - ax) * dx + (scaledY - ay) * dy) / length).coerceIn(0f, 1f)
        if (squaredDistance(x, scaledY, ax + t * dx, ay + t * dy) < threshold * threshold) return true
    }
    return false
}

/** Erase only the part of [stroke] within [threshold] of (x, y); the rest is split into new strokes. */
fun inkErasePart(stroke: InkStroke, x: Float, y: Float, threshold: Float, yScale: Float = 1f): List<InkStroke> {
    if (!inkHit(stroke, x, y, threshold, yScale)) return listOf(stroke)
    val p = stroke.points
    // Densify so long segments are cut where the eraser touches them, not at their far endpoints.
    val dense = mutableListOf(p[0], p[1])
    for (i in 2 until p.size - 1 step 2) {
        val ax = p[i - 2]; val ay = p[i - 1]; val bx = p[i]; val by = p[i + 1]
        val len = kotlin.math.sqrt(squaredDistance(ax, ay * yScale, bx, by * yScale))
        val n = kotlin.math.ceil(len / (threshold / 3f)).toInt().coerceIn(1, 200)
        for (s in 1..n) { val t = s.toFloat() / n; dense += ax + (bx - ax) * t; dense += ay + (by - ay) * t }
    }
    val result = mutableListOf<InkStroke>()
    var run = mutableListOf<Float>()
    fun flush() { if (run.size >= 4) result += stroke.copy(points = run); run = mutableListOf() }
    for (i in 0 until dense.size - 1 step 2) {
        if (squaredDistance(x, y * yScale, dense[i], dense[i + 1] * yScale) < threshold * threshold) flush()
        else { run += dense[i]; run += dense[i + 1] }
    }
    flush()
    return result.map { it.copy(points = simplifyInkPoints(it.points)) }.filter { it.points.size >= 4 }
}

private fun squaredDistance(ax: Float, ay: Float, bx: Float, by: Float): Float =
    (ax - bx) * (ax - bx) + (ay - by) * (ay - by)
