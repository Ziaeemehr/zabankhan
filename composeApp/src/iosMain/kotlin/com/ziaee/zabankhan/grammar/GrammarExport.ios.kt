@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.grammar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ziaee.zabankhan.pdf.AppFiles
import com.ziaee.zabankhan.resources.*
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSURL
import platform.UIKit.*

@Composable actual fun rememberGrammarExport(): (text: String, fileName: String?, save: Boolean) -> Unit {
    val saveFailure = stringResource(Res.string.grammar_save_failure)
    val shareFailure = stringResource(Res.string.grammar_share_failure)
    return remember(saveFailure, shareFailure) {
        { text, fileName, save ->
            runCatching {
                val item: Any = if (fileName == null && !save) text else {
                    val path = grammarExportPath(grammarExportFileName(fileName, "note.md"))
                    FileSystem.SYSTEM.write(path) { writeUtf8(text) }
                    NSURL.fileURLWithPath(path.toString())
                }
                // The system sheet includes Save to Files for document exports.
                presentGrammarShareSheet(item)
            }.onFailure { showGrammarExportFailure(if (save) saveFailure else shareFailure) }
        }
    }
}

@Composable actual fun rememberGrammarPdfExport(): (List<GrammarPdfBlock>, String, String) -> Unit {
    val failure = stringResource(Res.string.grammar_share_failure)
    return remember(failure) {
        { blocks, fileName, appName ->
            runCatching {
                val path = grammarExportPath(grammarExportFileName(fileName, "note.pdf"))
                writeGrammarPdf(blocks, appName, path.toString())
                presentGrammarShareSheet(NSURL.fileURLWithPath(path.toString()))
            }.onFailure { showGrammarExportFailure(failure) }
        }
    }
}

private fun grammarExportPath(name: String): okio.Path {
    val directory = "${AppFiles.cacheDir}/grammar-exports".toPath()
    FileSystem.SYSTEM.createDirectories(directory)
    return directory / name
}

private fun grammarPresenter(): UIViewController? {
    var presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
    return presenter
}

private fun presentGrammarShareSheet(item: Any) {
    val presenter = grammarPresenter() ?: error("No active view controller")
    val sheet = UIActivityViewController(listOf(item), null)
    sheet.popoverPresentationController?.sourceView = presenter.view
    sheet.popoverPresentationController?.sourceRect = presenter.view.bounds
    presenter.presentViewController(sheet, true, null)
}

private fun showGrammarExportFailure(message: String) {
    val presenter = grammarPresenter() ?: return
    val alert = UIAlertController.alertControllerWithTitle(null, message, UIAlertControllerStyleAlert)
    alert.addAction(UIAlertAction.actionWithTitle("OK", UIAlertActionStyleDefault, null))
    presenter.presentViewController(alert, true, null)
}
