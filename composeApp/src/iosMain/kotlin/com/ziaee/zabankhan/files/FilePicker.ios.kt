@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.ziaee.zabankhan.files

import androidx.compose.runtime.*
import com.ziaee.zabankhan.pdf.AppFiles
import kotlinx.coroutines.*
import okio.FileSystem
import okio.HashingSink
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.*
import platform.darwin.NSObject

private class FilePickerDelegate(val types: List<FileKind>, val scope: CoroutineScope, val picked: (PickedFile?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { picked(null) }
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return picked(null)
        val scoped = url.startAccessingSecurityScopedResource()
        scope.launch {
            val result = try { withContext(Dispatchers.IO) {
                val extension = url.pathExtension.orEmpty().lowercase()
                val kind = requireNotNull(types.firstOrNull { extension in it.extensions })
                val directory = (AppFiles.filesDir + "/" + when(kind) {
                    FileKind.PDF -> "pdfs"; FileKind.VIDEO, FileKind.SUBTITLE -> "videos"; else -> "imports"
                }).toPath()
                val fs = FileSystem.SYSTEM
                fs.createDirectories(directory)
                val temporary = directory / (NSUUID().UUIDString + ".tmp")
                try {
                    val hashing = HashingSink.sha256(fs.sink(temporary))
                    hashing.buffer().use { output -> fs.source(requireNotNull(url.path).toPath()).buffer().use { output.writeAll(it) } }
                    val target = directory / (hashing.hash.hex() + ".$extension")
                    if (!fs.exists(target)) fs.atomicMove(temporary, target)
                    val name = url.lastPathComponent.orEmpty()
                    fs.write((target.toString() + ".title").toPath()) { writeUtf8(name.substringBeforeLast('.')) }
                    PickedFile(target.toString(), name)
                } finally { fs.delete(temporary, mustExist = false) }
            } } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            finally { if (scoped) url.stopAccessingSecurityScopedResource() }
            picked(result)
        }
    }
}
@Composable actual fun rememberFilePicker(types: List<FileKind>, onPicked: (PickedFile?) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    val delegate = remember(types) { FilePickerDelegate(types, scope) { callback(it) } }
    return remember(types) { {
        val contentTypes = types.flatMap { it.extensions }.mapNotNull { UTType.typeWithFilenameExtension(it) }.ifEmpty { listOf(UTTypeData) }
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypes, asCopy = true)
        picker.delegate = delegate
        var root = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (root?.presentedViewController != null) root = root.presentedViewController
        root?.presentViewController(picker, true, null) ?: callback(null)
    } }
}
