package com.ziaee.zabankhan.ui

import androidx.compose.runtime.Composable

/** French Reader's CreateDocument CSV sample export, with a native Files action on iOS. */
@Composable internal expect fun rememberWordsSampleSaver(): (text: String, fileName: String) -> Unit
