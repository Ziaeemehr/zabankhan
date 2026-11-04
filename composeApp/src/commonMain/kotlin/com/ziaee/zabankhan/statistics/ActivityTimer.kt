package com.ziaee.zabankhan.statistics

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.ActivityLogEntry
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource

/** Monotonic accounting; split measured intervals at local midnight. */
class ActivityTimer(
    private val requiresInteraction: Boolean,
    private val elapsedMs: () -> Long,
    private val wallMs: () -> Long,
    private val zone: () -> TimeZone = TimeZone::currentSystemDefault,
) {
    private var lastElapsed = elapsedMs()
    private var lastWall = wallMs()
    private var lastInteraction: Long? = null
    private var enabled = false
    private val pending = mutableMapOf<String, Long>()
    fun setEnabled(value: Boolean) { account(); enabled = value }
    fun interact() { account(); lastInteraction = elapsedMs() }
    fun drain(): Map<String, Long> { account(); return pending.toMap().also { pending.clear() } }
    private fun account() {
        val now = elapsedMs()
        val end = if (requiresInteraction) minOf(now, lastInteraction?.plus(60_000) ?: lastElapsed) else now
        val duration = if (enabled) (end - lastElapsed).coerceAtLeast(0) else 0
        var startWall = lastWall
        val endWall = startWall + duration
        val tz = zone()
        while (startWall < endWall) {
            val day = Instant.fromEpochMilliseconds(startWall).toLocalDateTime(tz).date
            val next = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
            val stop = minOf(endWall, next)
            pending[day.toString()] = (pending[day.toString()] ?: 0) + stop - startWall
            startWall = stop
        }
        lastElapsed = now; lastWall = wallMs()
    }
}

class ScreenActivityTimer(private val timer: ActivityTimer, private val write: (Map<String, Long>) -> Unit) {
    private var resumed = false
    private var eligible = false
    fun setResumed(value: Boolean) { resumed = value; timer.setEnabled(resumed && eligible); if (!value) flush() }
    fun setEligible(value: Boolean) { eligible = value; timer.setEnabled(resumed && eligible) }
    fun interact() = timer.interact()
    fun flush() = write(timer.drain())
}

enum class ActivityKind { READING, LISTENING, WATCHING }
// Final flushes must survive navigation and the screen's composition scope.
private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val writeMutex = Mutex()

@Composable fun TrackActivity(container: AppContainer, key: Any, lang: String, kind: ActivityKind, eligible: Boolean = true) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val timer = remember(container, key, lang, kind) {
        val origin = TimeSource.Monotonic.markNow()
        // Visible reader time is requested for H2; interaction expiry remains available in ActivityTimer.
        ScreenActivityTimer(ActivityTimer(false, { origin.elapsedNow().inWholeMilliseconds }, { Clock.System.now().toEpochMilliseconds() })) { rows ->
            if (rows.isNotEmpty()) writes.launch {
                writeMutex.withLock {
                    val dao = container.database.activity()
                    rows.forEach { (date, ms) ->
                        val row = dao.get(date, lang) ?: ActivityLogEntry(date, lang)
                        dao.upsert(when (kind) {
                            ActivityKind.READING -> row.copy(readingMs = row.readingMs + ms)
                            ActivityKind.LISTENING -> row.copy(listeningMs = row.listeningMs + ms)
                            ActivityKind.WATCHING -> row.copy(watchingMs = row.watchingMs + ms)
                        })
                    }
                }
            }
        }
    }
    SideEffect { timer.setEligible(eligible) }
    DisposableEffect(timer, lifecycle) {
        timer.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ -> timer.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        lifecycle.addObserver(observer)
        onDispose { timer.setResumed(false); lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(timer) { while (true) { delay(15_000); timer.flush() } }
}
