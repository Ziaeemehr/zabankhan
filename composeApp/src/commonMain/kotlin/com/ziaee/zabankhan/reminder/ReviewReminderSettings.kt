package com.ziaee.zabankhan.reminder

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.AppSettings
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.Choice
import org.jetbrains.compose.resources.stringResource

@Composable fun ReviewReminderSettings(vm: AppViewModel, settings: AppSettings) {
    val permission = rememberReminderPermissionRequest { granted ->
        vm.action { vm.container.settings.setReminderEnabled(granted) }
    }
    Text(stringResource(Res.string.h1_reminder_title), style = MaterialTheme.typography.titleMedium)
    Row {
        Switch(settings.reminderEnabled, onCheckedChange = { enabled ->
            if (enabled) permission() else vm.action { vm.container.settings.setReminderEnabled(false) }
        })
        Text(stringResource(Res.string.h1_reminder_daily))
    }
    if (settings.reminderEnabled) {
        Choice(stringResource(Res.string.h1_reminder_hour), settings.reminderHour, (0..23).map { it to it.toString().padStart(2, '0') }) { hour ->
            vm.action { vm.container.settings.setReminderTime(hour, settings.reminderMinute) }
        }
        Choice(stringResource(Res.string.h1_reminder_minute), settings.reminderMinute, (0..59).map { it to it.toString().padStart(2, '0') }) { minute ->
            vm.action { vm.container.settings.setReminderTime(settings.reminderHour, minute) }
        }
    }
}
