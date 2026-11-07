package com.ziaee.zabankhan.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File

class SharedModel<T : Any>(
    private val load: suspend () -> T,
    private val close: (T) -> Unit
) {
    private val mutex = Mutex()
    private var model: T? = null

    suspend fun <R> withModel(block: suspend (T) -> R): R = mutex.withLock {
        val current = model ?: load().also { model = it }
        block(current)
    }

    suspend fun change(block: () -> Unit) = mutex.withLock {
        model?.let(close); model = null
        block()
    }

    suspend fun invalidate() = mutex.withLock {
        val current = model
        model = null
        current?.let(close)
    }
}

object VoskModels {
    private val shared = mutableMapOf<String, SharedModel<Model>>()
    @Synchronized
    fun forDir(dir: File): SharedModel<Model> = shared.getOrPut(dir.absolutePath) {
        SharedModel(load = { withContext(Dispatchers.Default) { Model(dir.absolutePath) } }, close = Model::close)
    }
}
actual suspend fun changeOfflineModel(directory: String, change: () -> Unit) =
    VoskModels.forDir(File(directory)).change(change)
