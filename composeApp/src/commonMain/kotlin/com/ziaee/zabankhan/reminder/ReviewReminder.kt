package com.ziaee.zabankhan.reminder

import androidx.compose.runtime.Composable
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.data.nowMs
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.srs.VocabSrs
import com.ziaee.zabankhan.srs.reviewableCount
import com.ziaee.zabankhan.ui.remainingNewCards
import com.ziaee.zabankhan.ui.reviewCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.*
import kotlin.time.Instant

/** Resolve each date from the configured wall time so a DST gap does not shift tomorrow. */
fun nextReviewReminderAt(now: Instant, hour: Int, minute: Int, zone: TimeZone = TimeZone.currentSystemDefault()): Instant {
    require(hour in 0..23 && minute in 0..59)
    val today = now.toLocalDateTime(zone).date
    fun at(date: LocalDate) = LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)
    val candidate = at(today)
    return if (candidate > now) candidate else at(today.plus(1, DateTimeUnit.DAY))
}

suspend fun dueReminderCount(container: AppContainer, settings: AppSettings, atMs: Long = nowMs()): Int {
    val lang = settings.learningLang ?: return 0
    if (Languages.learnable.none { it.code == lang }) return 0
    val remaining = remainingNewCards(container.vocab.reviewLogSince(lang, 0), settings.dailyNewCards, VocabSrs.startOfDayMs(atMs))
    return reviewableCount(container.vocab.getAll(lang).map { it.reviewCard() }, atMs, remaining)
}

/** Called while the app is mounted; native foreground/background events refresh due counts. */
@Composable expect fun ReviewReminderLifecycle(container: AppContainer, settings: AppSettings)
@Composable expect fun rememberReminderPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit

/** Retains a notification tap until the mounted navigator consumes it, including cold launches. */
object ReviewReminderNavigation {
    private val requested = MutableStateFlow(false)
    val pending = requested.asStateFlow()
    fun openReview() { requested.value = true }
    fun consume() { requested.value = false }
}
