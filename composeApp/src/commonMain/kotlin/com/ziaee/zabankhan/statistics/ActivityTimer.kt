package com.ziaee.zabankhan.statistics

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.ActivityLogEntry
import com.ziaee.zabankhan.data.ActivityDao
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

internal suspend fun writeActivity(dao: ActivityDao, rows: Map<String, Long>, lang: String, kind: ActivityKind) {
    try {
        writeMutex.withLock {
            rows.forEach { (date, ms) ->
                val row = dao.get(date, lang) ?: ActivityLogEntry(date, lang)
                dao.upsert(when (kind) {
                    ActivityKind.READING -> row.copy(readingMs = row.readingMs + ms)
                    ActivityKind.LISTENING -> row.copy(listeningMs = row.listeningMs + ms)
                    ActivityKind.WATCHING -> row.copy(watchingMs = row.watchingMs + ms)
                })
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Restore can close the old database before a disposed screen's final flush runs.
        // Activity accounting is best effort and must not crash the app in that race.
    }
}

/** Returns the timer so screens can report interaction; reading counts only up to 60 s after the last one. */
@Composable fun TrackActivity(container: AppContainer, key: Any, lang: String, kind: ActivityKind, eligible: Boolean = true): ScreenActivityTimer {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val timer = remember(container, key, lang, kind) {
        val origin = TimeSource.Monotonic.markNow()
        // An idle open reader must not count as study time.
        ScreenActivityTimer(ActivityTimer(kind == ActivityKind.READING, { origin.elapsedNow().inWholeMilliseconds }, { Clock.System.now().toEpochMilliseconds() })) { rows ->
            if (rows.isNotEmpty()) writes.launch {
                writeActivity(container.database.activity(), rows, lang, kind)
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
    return timer
}
