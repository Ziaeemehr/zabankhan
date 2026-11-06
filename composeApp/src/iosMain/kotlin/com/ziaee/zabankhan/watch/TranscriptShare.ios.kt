@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ziaee.zabankhan.pdf.AppFiles
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@Composable internal actual fun rememberTranscriptShare(): (text: String, fileName: String?) -> Unit = remember {
    { text, fileName ->
        val item: Any = if (fileName == null) text else {
            val directory = "${AppFiles.cacheDir}/transcript-exports".toPath()
            FileSystem.SYSTEM.createDirectories(directory)
            val path = directory / fileName
            FileSystem.SYSTEM.write(path) { writeUtf8(text) }
            NSURL.fileURLWithPath(path.toString())
        }
        val sheet = UIActivityViewController(listOf(item), null)
        var presenter: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
        presenter?.let {
            sheet.popoverPresentationController?.sourceView = it.view
            sheet.popoverPresentationController?.sourceRect = it.view.bounds
            it.presentViewController(sheet, true, null)
        }
    }
}
