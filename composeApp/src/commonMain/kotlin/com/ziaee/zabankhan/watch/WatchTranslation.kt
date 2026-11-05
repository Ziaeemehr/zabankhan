package com.ziaee.zabankhan.watch

import androidx.compose.runtime.*

internal data class SubtitleTranslationState(val text: String? = null, val loading: Boolean = false, val failed: Boolean = false)

@Composable
internal fun rememberSubtitleTranslation(
    cueIndex: Int,
    cueText: String?,
    sourceLang: String,
    targetLang: String,
    shown: Boolean,
    retry: Int,
    translate: suspend (String, String, String) -> Result<String>,
): SubtitleTranslationState {
    var state by remember { mutableStateOf(SubtitleTranslationState()) }
    LaunchedEffect(cueIndex, cueText, sourceLang, shown, targetLang, retry) {
        state = SubtitleTranslationState()
        if (shown && cueText != null) {
            state = SubtitleTranslationState(loading = true)
            try {
                val result = translate(cueText, sourceLang, targetLang)
                state = SubtitleTranslationState(text = result.getOrNull(), failed = result.isFailure)
            } finally { state = state.copy(loading = false) }
        }
    }
    return state
}
