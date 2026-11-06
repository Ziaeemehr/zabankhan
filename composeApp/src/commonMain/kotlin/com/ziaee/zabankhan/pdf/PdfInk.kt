package com.ziaee.zabankhan.pdf

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class InkStroke(
    val colorKey: String = "primary",
    val width: Float = .004f,
    val points: List<Float>,
)

fun encodeInk(strokes: List<InkStroke>): String = Json.encodeToString(strokes)

fun decodeInk(value: String): List<InkStroke> = try {
    Json.decodeFromString<List<InkStroke>>(value).filter { stroke ->
        stroke.colorKey in listOf("primary", "secondary", "tertiary") &&
            stroke.width.isFinite() && stroke.width > 0f && stroke.width <= 1f &&
            stroke.points.size >= 2 && stroke.points.size % 2 == 0 &&
            stroke.points.all { it.isFinite() && it in 0f..1f }
    }
} catch (_: IllegalArgumentException) { emptyList() }

/** Keep points at least [distance] apart in normalized page coordinates. */
fun simplifyInkPoints(points: List<Float>, distance: Float = .001f): List<Float> {
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
