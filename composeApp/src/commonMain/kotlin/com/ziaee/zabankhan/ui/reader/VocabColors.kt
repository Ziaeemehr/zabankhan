package com.ziaee.zabankhan.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ziaee.zabankhan.srs.VocabStatus
import kotlin.math.ceil

@Composable
fun VocabStatus.color(): Color = color(MaterialTheme.colorScheme.surface.luminance() < 0.5f)

/** Accent color for dots, rules, icons, and other non-text status cues. */
fun VocabStatus.color(dark: Boolean): Color {
    return when (this) {
        VocabStatus.NEW -> if (dark) Color(0xFFC4B5FD) else Color(0xFF6D28D9)
        VocabStatus.LEARNING -> if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
        VocabStatus.REVIEWING -> if (dark) Color(0xFF67E8F9) else Color(0xFF087F8C)
        VocabStatus.LEARNED -> if (dark) Color(0xFF86EFAC) else Color(0xFF15803D)
    }
}

@Composable
fun VocabStatus.pieColor(): Color = pieColor(MaterialTheme.colorScheme.surface.luminance() < 0.5f)

/** More saturated, mutually distinct palette for chart slices, where [color] alone can look
 * too close between adjacent statuses (e.g. REVIEWING's teal next to LEARNED's green). */
fun VocabStatus.pieColor(dark: Boolean): Color {
    return when (this) {
        VocabStatus.NEW -> if (dark) Color(0xFFB794F6) else Color(0xFF7C3AED)
        VocabStatus.LEARNING -> if (dark) Color(0xFFFFB84D) else Color(0xFFF59E0B)
        VocabStatus.REVIEWING -> if (dark) Color(0xFF5CC8FF) else Color(0xFF0EA5E9)
        VocabStatus.LEARNED -> if (dark) Color(0xFF6EE7A8) else Color(0xFF22C55E)
    }
}

@Composable
fun VocabStatus.containerColor(): Color = containerColor(MaterialTheme.colorScheme.surface.luminance() < 0.5f)

/** Tint intended to sit behind the theme's on-surface color or reading-page ink. */
fun VocabStatus.containerColor(dark: Boolean): Color {
    return when (this) {
        VocabStatus.NEW -> if (dark) Color(0xFF2E2547) else Color(0xFFF3EEFF)
        VocabStatus.LEARNING -> if (dark) Color(0xFF3D2E18) else Color(0xFFFFF3DC)
        VocabStatus.REVIEWING -> if (dark) Color(0xFF17353A) else Color(0xFFE5F7F8)
        VocabStatus.LEARNED -> if (dark) Color(0xFF193824) else Color(0xFFE8F7EC)
    }
}

@Composable
fun leitnerBoxColor(box: Int): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (box.coerceIn(1, 5)) {
        1 -> if (dark) Color(0xFFFF8A65) else Color(0xFFD84315)
        2 -> if (dark) Color(0xFFFFCA5C) else Color(0xFFCA8A04)
        3 -> if (dark) Color(0xFFBEDE72) else Color(0xFF65A30D)
        4 -> if (dark) Color(0xFF5EEAD4) else Color(0xFF0F8A7B)
        else -> if (dark) Color(0xFF86EFAC) else Color(0xFF15803D)
    }
}

@Composable fun boxColor(box: Int): Color = leitnerBoxColor(box)
