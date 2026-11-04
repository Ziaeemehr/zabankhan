package com.ziaee.zabankhan.reminder

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ziaee.zabankhan.MainActivity
import com.ziaee.zabankhan.ZabankhanApp
import com.ziaee.zabankhan.app.AppContainer
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.getString
import kotlin.time.Clock

private const val REMINDER_ID = 4701
private const val CHANNEL = "vocabulary_review"
private fun pendingAlarm(context: Context) = PendingIntent.getBroadcast(context, REMINDER_ID,
    Intent(context, ReviewReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

fun scheduleAndroidReviewReminder(context: Context, settings: AppSettings) {
    val alarm = context.getSystemService(AlarmManager::class.java)
    val pending = pendingAlarm(context)
    alarm.cancel(pending)
    if (!settings.reminderEnabled || settings.learningLang == null) return
    val at = nextReviewReminderAt(Clock.System.now(), settings.reminderHour, settings.reminderMinute).toEpochMilliseconds()
    // Inexact alarms remain available without the special exact-alarm access on recent Android.
    if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    } else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
}

@Composable actual fun rememberReminderPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { latest(it) }
    return {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else latest(true)
    }
}

@Composable actual fun ReviewReminderLifecycle(container: AppContainer, settings: AppSettings) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(settings)
    LaunchedEffect(settings) { scheduleAndroidReviewReminder(context, settings) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_STOP) scheduleAndroidReviewReminder(context, latest)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

class ReviewReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as ZabankhanApp).container
                scheduleAndroidReviewReminder(context, container.settings.current())
            } finally { pending.finish() }
        }
    }
}

class ReviewReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as ZabankhanApp).container
                val settings = container.settings.current()
                scheduleAndroidReviewReminder(context, settings)
                if (!settings.reminderEnabled) return@launch
                val count = dueReminderCount(container, settings)
                if (count == 0) return@launch
                val title = getString(Res.string.h1_reminder_title)
                val body = getString(Res.string.h1_reminder_waiting, count)
                withContext(Dispatchers.Main) {
                    if (!container.settings.current().reminderEnabled) return@withContext
                    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@withContext
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.createNotificationChannel(NotificationChannel(CHANNEL, title, NotificationManager.IMPORTANCE_DEFAULT))
                    val open = PendingIntent.getActivity(context, REMINDER_ID,
                        Intent(context, MainActivity::class.java).putExtra("open_review", true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    manager.notify(REMINDER_ID, Notification.Builder(context, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title).setContentText(body)
                        .setContentIntent(open).setAutoCancel(true).build())
                }
            } finally { pending.finish() }
        }
    }
}
