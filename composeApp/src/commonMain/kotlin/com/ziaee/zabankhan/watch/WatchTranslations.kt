package com.ziaee.zabankhan.watch

import androidx.compose.runtime.mutableStateMapOf
import com.ziaee.zabankhan.translate.TranslationOverrides
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class WatchTranslationState(
    val text: String? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val corrected: Boolean = false,
)

/** A window is bounded by sentence units, even when adjacent units have identical text. */
internal fun translationPrefetchWindow(units: List<SubtitleCue>, current: Int): List<String> =
    if (current !in units.indices) emptyList()
    else units.drop(current).take(3).map { it.text }.filter { it.isNotBlank() }.distinct()

/** One instance belongs to one source/meaning-language pair. */
internal class WatchTranslations(
    private val overrides: TranslationOverrides,
    private val source: String,
    private val target: String,
    private val translate: suspend (String) -> Result<String>,
) {
    val states = mutableStateMapOf<String, WatchTranslationState>()
    private val locks = mutableMapOf<String, Mutex>()

    suspend fun load(text: String, retry: Boolean = false) {
        locks.getOrPut(text) { Mutex() }.withLock {
            val previous = states[text]
            if (!retry && previous != null && !previous.loading) return
            states[text] = WatchTranslationState(loading = true)
            try {
                val corrected = overrides.getOverride(text, source, target)
                states[text] = if (corrected != null) WatchTranslationState(text = corrected, corrected = true)
                else {
                    val result = translate(text)
                    WatchTranslationState(text = result.getOrNull(), failed = result.isFailure)
                }
            } catch (cancelled: CancellationException) {
                states.remove(text)
                throw cancelled
            } catch (_: Exception) {
                states[text] = WatchTranslationState(failed = true)
            }
        }
    }

    suspend fun save(text: String, value: String?) {
        locks.getOrPut(text) { Mutex() }.withLock {
            if (value == null) {
                overrides.removeOverride(text, source, target)
                states.remove(text)
            } else {
                overrides.putOverride(text, source, target, value)
                states[text] = WatchTranslationState(text = value, corrected = true)
            }
        }
        if (value == null) load(text)
    }
}
