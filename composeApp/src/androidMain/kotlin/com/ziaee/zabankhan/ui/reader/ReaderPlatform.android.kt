package com.ziaee.zabankhan.ui.reader

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal actual val needsReadOnlyTouchSelection = false

@Composable
internal actual fun rememberReaderProcessTextActions(text: String): List<ProcessTextAction> {
    val context = LocalContext.current
    return remember(context, text) {
        val request = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        val apps = context.packageManager.queryIntentActivities(request, 0)
            .filter { it.activityInfo.exported }.map { info ->
                ReaderProcessTextApp(info.loadLabel(context.packageManager).toString(),
                    info.activityInfo.packageName, info.activityInfo.name,
                    info.activityInfo.applicationInfo.loadLabel(context.packageManager).toString())
            }
        filterAndSortProcessTextApps(apps, context.packageName).map { app ->
                ProcessTextAction(app.label) {
                    context.startActivity(Intent(request).setClassName(app.packageName, app.activityName)
                        .putExtra(Intent.EXTRA_PROCESS_TEXT, text).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true))
                }
            }
    }
}

internal actual fun formatReaderDate(timestampMs: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(timestampMs))
internal actual fun formatReaderNumber(number: Int): String = java.text.NumberFormat.getIntegerInstance().format(number)
