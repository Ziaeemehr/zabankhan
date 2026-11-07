package com.ziaee.zabankhan.pdf

import androidx.compose.runtime.Composable

@Composable
expect fun PencilDoubleTapEffect(enabled: Boolean, onToggle: () -> Unit)
