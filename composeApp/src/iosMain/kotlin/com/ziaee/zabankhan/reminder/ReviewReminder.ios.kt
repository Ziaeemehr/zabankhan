@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.reminder

import androidx.compose.runtime.*
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.jetbrains.compose.resources.getString
import platform.Foundation.*
import platform.UIKit.*
import platform.UserNotifications.*
import platform.darwin.NSObject

private const val REMINDER_ID = "vocabulary_review"

private object ReminderNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        if (didReceiveNotificationResponse.notification.request.identifier == REMINDER_ID) ReviewReminderNavigation.openReview()
        withCompletionHandler()
    }
}

/** Keep a strong reference to the delegate; UNUserNotificationCenter.delegate is weak. */
fun initializeIosReviewReminders() {
    UNUserNotificationCenter.currentNotificationCenter().delegate = ReminderNotificationDelegate
}

@Composable actual fun rememberReminderPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onResult)
    return {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { granted, _ -> latest(granted) }
    }
}

private var reminderGeneration = 0

private suspend fun scheduleIosReviewReminder(container: AppContainer, settings: AppSettings) {
    val generation = ++reminderGeneration
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.removePendingNotificationRequestsWithIdentifiers(listOf(REMINDER_ID))
    if (!settings.reminderEnabled) return
    val next = nextReviewReminderAt(Clock.System.now(), settings.reminderHour, settings.reminderMinute)
    val count = dueReminderCount(container, settings, next.toEpochMilliseconds())
    if (count <= 0 || generation != reminderGeneration) return
    val content = UNMutableNotificationContent().apply {
        setTitle(getString(Res.string.h1_reminder_title))
        setBody(getString(Res.string.h1_reminder_waiting, count))
        setSound(UNNotificationSound.defaultSound)
        setUserInfo(mapOf("open_review" to true))
    }
    val date = NSDateComponents().apply { hour = settings.reminderHour.toLong(); minute = settings.reminderMinute.toLong() }
    val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(date, true)
    center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(REMINDER_ID, content, trigger)) { _ -> }
}

@Composable actual fun ReviewReminderLifecycle(container: AppContainer, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    SideEffect { initializeIosReviewReminders() }
    val latest by rememberUpdatedState(settings)
    LaunchedEffect(container, settings) { scheduleIosReviewReminder(container, settings) }
    LaunchedEffect(container, settings.learningLang) {
        settings.learningLang?.let { lang -> container.vocab.observeAll(lang).collect { scheduleIosReviewReminder(container, latest) } }
    }
    DisposableEffect(container) {
        val center = NSNotificationCenter.defaultCenter
        val tokens = listOf(UIApplicationDidBecomeActiveNotification, UIApplicationDidEnterBackgroundNotification).map { name ->
            center.addObserverForName(name, null, NSOperationQueue.mainQueue) {
                scope.launch { scheduleIosReviewReminder(container, latest) }
            }
        }
        onDispose { tokens.forEach { center.removeObserver(it) } }
    }
}
