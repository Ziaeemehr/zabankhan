package com.ziaee.zabankhan.ui.reader

import androidx.compose.runtime.Composable
import platform.Foundation.*

/** iOS has no equivalent to Android's installed ACTION_PROCESS_TEXT activity registry. */
@Composable
internal actual fun rememberReaderProcessTextActions(text: String): List<ProcessTextAction> = emptyList()
internal actual val needsReadOnlyTouchSelection = true

internal actual fun formatReaderDate(timestampMs: Long): String = NSDateFormatter().apply {
    dateStyle = NSDateFormatterMediumStyle
    timeStyle = NSDateFormatterNoStyle
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(timestampMs.toDouble() / 1000.0))
internal actual fun formatReaderNumber(number: Int): String = NSNumberFormatter().apply {
    numberStyle = NSNumberFormatterDecimalStyle
}.stringFromNumber(NSNumber(number)) ?: number.toString()
