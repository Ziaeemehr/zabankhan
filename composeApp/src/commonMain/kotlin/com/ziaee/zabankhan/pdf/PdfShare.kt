package com.ziaee.zabankhan.pdf

import androidx.compose.runtime.Composable

/** Platform share sheet for the original PDF. */
@Composable internal expect fun rememberPdfShare(): (String) -> Unit
