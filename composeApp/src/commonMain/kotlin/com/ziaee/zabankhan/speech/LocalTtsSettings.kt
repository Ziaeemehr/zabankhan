package com.ziaee.zabankhan.speech

import androidx.compose.runtime.Composable

/** Local (Wi-Fi XTTS) voice is Android-only for now; iOS has no implementation. */
expect val localTtsSupported: Boolean
@Composable expect fun LocalTtsSettings(language: com.ziaee.zabankhan.lang.LanguageSpec)
