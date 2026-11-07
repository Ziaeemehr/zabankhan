package com.ziaee.zabankhan.backup

internal expect fun formatBackupTimestamp(atMs: Long): String
internal expect fun formatFileSize(bytes: Long): String
internal expect fun formatBackupSize(template: String, bytes: Long): String
