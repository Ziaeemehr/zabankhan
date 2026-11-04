package com.ziaee.zabankhan.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable fun boxColor(box: Int): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (box.coerceIn(1, 5)) {
        1 -> if (dark) Color(0xFFFF8A65) else Color(0xFFD84315)
        2 -> if (dark) Color(0xFFFFCA5C) else Color(0xFFCA8A04)
        3 -> if (dark) Color(0xFFBEDE72) else Color(0xFF65A30D)
        4 -> if (dark) Color(0xFF5EEAD4) else Color(0xFF0F8A7B)
        else -> if (dark) Color(0xFF86EFAC) else Color(0xFF15803D)
    }
}
