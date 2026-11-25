package com.ziaee.zabankhan.ui.reader

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ReaderPositionSaver(
    private val saveOffset: suspend (Int) -> Unit,
    private val markRead: suspend () -> Unit,
    initiallyCompleted: Boolean = false,
) {
    private val mutex = Mutex()
    private var completed = initiallyCompleted
    private var completionPersisted = false

    suspend fun save(offset: Int) = mutex.withLock {
        if (!completed) saveOffset(offset)
    }

    suspend fun saveEnd(endOffset: Int) = mutex.withLock {
        saveOffset(endOffset)
        completed = true
    }

    suspend fun complete(endOffset: Int) = mutex.withLock {
        if (!completionPersisted) {
            saveOffset(endOffset)
            markRead()
            completed = true
            completionPersisted = true
        }
    }

    suspend fun reset() = mutex.withLock { completed = false; completionPersisted = false }
}
