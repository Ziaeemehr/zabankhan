@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.ui

import androidx.compose.runtime.*
import com.ziaee.zabankhan.pdf.AppFiles
import com.ziaee.zabankhan.resources.*
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSURL
import platform.UIKit.*

@Composable internal actual fun rememberWordsSampleSaver(): (String, String) -> Unit {
    val failure = stringResource(Res.string.b_words_csv_import_failed)
    val close = stringResource(Res.string.b_words_action_close)
    return remember(failure, close) { { text, name ->
        var presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
        val host = presenter
        runCatching {
            requireNotNull(host)
            val directory = "${AppFiles.cacheDir}/word-exports".toPath()
            FileSystem.SYSTEM.createDirectories(directory)
            val path = directory / name.substringAfterLast('/')
            FileSystem.SYSTEM.write(path) { writeUtf8(text) }
            // The system activity sheet offers Save to Files for the CSV document.
            val sheet = UIActivityViewController(listOf(NSURL.fileURLWithPath(path.toString())), null)
            sheet.popoverPresentationController?.sourceView = host.view
            sheet.popoverPresentationController?.sourceRect = host.view.bounds
            host.presentViewController(sheet, true, null)
        }.onFailure {
            val alert = UIAlertController.alertControllerWithTitle(null, failure, UIAlertControllerStyleAlert)
            alert.addAction(UIAlertAction.actionWithTitle(close, UIAlertActionStyleDefault, null))
            host?.presentViewController(alert, true, null)
        }
    } }
}
