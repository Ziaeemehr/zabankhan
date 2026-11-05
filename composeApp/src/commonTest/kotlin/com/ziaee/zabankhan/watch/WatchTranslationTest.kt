package com.ziaee.zabankhan.watch

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WatchTranslationTest {
    @Test fun replacingSubtitlesAtSameCueRetranslates() = runTest {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) {}
            override fun insertBottomUp(index: Int, instance: Unit) {}
            override fun remove(index: Int, count: Int) {}
            override fun move(from: Int, to: Int, count: Int) {}
            override fun onClear() {}
        }, recomposer)
        var cueText by mutableStateOf("Original.")
        var state = SubtitleTranslationState()
        val requested = mutableListOf<String>()
        try {
            composition.setContent {
                state = rememberSubtitleTranslation(0, cueText, "en", "fa", true, 0) { text, _, _ ->
                    requested += text
                    Result.success("meaning:$text")
                }
            }
            suspend fun settle() {
                Snapshot.sendApplyNotifications()
                runCurrent()
                clock.sendFrame(0L)
                runCurrent()
                Snapshot.sendApplyNotifications()
                runCurrent()
                clock.sendFrame(1L)
                runCurrent()
            }
            settle()
            assertEquals("meaning:Original.", state.text)
            cueText = "Replacement."
            settle()
            assertEquals(listOf("Original.", "Replacement."), requested)
            assertEquals("meaning:Replacement.", state.text)
        } finally {
            composition.dispose()
            recomposer.close()
            runner.cancel()
        }
    }
}
