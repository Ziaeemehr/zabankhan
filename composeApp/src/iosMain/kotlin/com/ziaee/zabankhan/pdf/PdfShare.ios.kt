@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

@Composable internal actual fun rememberPdfShare(): (String) -> Unit = remember { { path: String ->
    val controller = UIActivityViewController(listOf(NSURL.fileURLWithPath(path)), null)
    var presenter: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (presenter?.presentedViewController != null) presenter = presenter?.presentedViewController
    presenter?.let {
        controller.popoverPresentationController?.sourceView = it.view
        controller.popoverPresentationController?.sourceRect = it.view.bounds
        it.presentViewController(controller, true, null)
    }
} }
