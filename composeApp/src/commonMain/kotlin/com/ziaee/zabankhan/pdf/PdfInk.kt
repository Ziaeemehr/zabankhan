package com.ziaee.zabankhan.pdf

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class InkCurveSegment(val controlX: Float, val controlY: Float, val endX: Float, val endY: Float)

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

/** Erase whole strokes, including dots and degenerate segments. */
fun inkHit(stroke: InkStroke, x: Float, y: Float, threshold: Float = .015f): Boolean {
    val points = stroke.points
    for (i in 0 until points.size - 1 step 2) {
        val ax = points[i]; val ay = points[i + 1]
        val bx = points.getOrElse(i + 2) { ax }; val by = points.getOrElse(i + 3) { ay }
        val dx = bx - ax; val dy = by - ay
        val length = dx * dx + dy * dy
        val t = if (length == 0f) 0f else (((x - ax) * dx + (y - ay) * dy) / length).coerceIn(0f, 1f)
        if (squaredDistance(x, y, ax + t * dx, ay + t * dy) < threshold * threshold) return true
    }
    return false
}

private fun squaredDistance(ax: Float, ay: Float, bx: Float, by: Float): Float =
    (ax - bx) * (ax - bx) + (ay - by) * (ay - by)
