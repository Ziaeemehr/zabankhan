package com.ziaee.zabankhan.ui.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderPositionSaverTest {
    @Test fun terminalPositionAndReadMarkWinOverLateViewportWrites() = runTest {
        val saved = mutableListOf<Int>()
        var markedRead = false
        val saver = ReaderPositionSaver({ saved += it }, { markedRead = true })
        saver.save(28)
        saver.complete(100)
        saver.save(28)
        assertEquals(listOf(28, 100), saved)
        assertTrue(markedRead)
    }

    @Test fun completionWaitsForAnActiveViewportSave() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val saved = mutableListOf<Int>()
        val saver = ReaderPositionSaver({ offset ->
            if (offset == 28) { entered.complete(Unit); release.await() }
            saved += offset
        }, {})
        val viewportSave = launch { saver.save(28) }
        entered.await()
        val completion = async { saver.complete(100) }
        release.complete(Unit)
        viewportSave.join(); completion.await()
        assertEquals(listOf(28, 100), saved)
    }

    @Test fun reopenedCompletedPositionIgnoresInitialViewportButAllowsManualNavigation() = runTest {
        val saved = mutableListOf<Int>()
        val saver = ReaderPositionSaver({ saved += it }, {}, initiallyCompleted = true)
        saver.save(80)
        saver.reset()
        saver.save(80)
        assertEquals(listOf(80), saved)
    }

    @Test fun initiallyCompletedStillPersistsExplicitCompletionAndBlocksLateViewport() = runTest {
        val saved = mutableListOf<Int>()
        var markedRead = false
        val saver = ReaderPositionSaver({ saved += it }, { markedRead = true }, initiallyCompleted = true)
        saver.complete(100)
        saver.save(80)
        assertEquals(listOf(100), saved)
        assertTrue(markedRead)
    }
}
