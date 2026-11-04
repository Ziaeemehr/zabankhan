package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.content.*
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.files.*
import com.ziaee.zabankhan.lang.Languages
import com.ziaee.zabankhan.pdf.createOcrEngine
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.stringResource

expect fun loadImportImage(path: String): ImageBitmap

@Composable
fun ImportAddMenu(vm: AppViewModel, settings: AppSettings, addText: () -> Unit, open: (Long) -> Unit, watch: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    var previewTitle by remember { mutableStateOf("") }
    var pendingAnki by remember { mutableStateOf<String?>(null) }
    var keepProgress by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<WordImportResult?>(null) }
    var sample by remember { mutableStateOf(false) }
    val fallback = stringResource(Res.string.h1_imported_words)
    val spec = requireNotNull(Languages.learnable.firstOrNull { it.code == settings.learningLang })
    val repository = remember(vm, spec, settings.meaningLang) { ImportRepository(vm.container.database, spec, settings.meaningLang) }
    fun picked(kind: FileKind): (PickedFile?) -> Unit = { file ->
        if (file != null) vm.action {
            busy = true
            try {
                when (kind) {
                    FileKind.PDF -> open(withContext(Dispatchers.IO) { vm.container.texts.insert(TextDocument(lang = spec.code, title = file.displayName.substringBeforeLast('.'), body = "", pdfPath = file.path)) })
                    FileKind.TEXT -> open(withContext(Dispatchers.IO) { repository.text(FileSystem.SYSTEM.read(file.path.toPath()) { readUtf8() }, file.displayName) })
                    FileKind.EPUB -> open(withContext(Dispatchers.IO) { repository.epub(FileSystem.SYSTEM.read(file.path.toPath()) { readByteArray() }) })
                    FileKind.IMAGE -> {
                        val words = withContext(Dispatchers.IO) { createOcrEngine().recognizeWords(loadImportImage(file.path), spec.code) }
                        previewTitle = file.displayName.substringBeforeLast('.')
                        preview = words.joinToString(" ") { it.text }
                    }
                    FileKind.CSV -> result = withContext(Dispatchers.IO) { repository.csv(FileSystem.SYSTEM.read(file.path.toPath()) { readUtf8() }, fallback) }
                    FileKind.JSON -> pendingAnki = withContext(Dispatchers.IO) { FileSystem.SYSTEM.read(file.path.toPath()) { readUtf8() } }
                    else -> Unit
                }
            } finally { busy = false }
        }
    }
    val pdf = rememberFilePicker(listOf(FileKind.PDF), picked(FileKind.PDF))
    val text = rememberFilePicker(listOf(FileKind.TEXT), picked(FileKind.TEXT))
    val epub = rememberFilePicker(listOf(FileKind.EPUB), picked(FileKind.EPUB))
    val image = rememberFilePicker(listOf(FileKind.IMAGE), picked(FileKind.IMAGE))
    val csv = rememberFilePicker(listOf(FileKind.CSV), picked(FileKind.CSV))
    val json = rememberFilePicker(listOf(FileKind.JSON), picked(FileKind.JSON))
    Box {
        Button(enabled = !busy, onClick = { expanded = true }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(Res.string.add_text)) }
        DropdownMenu(expanded, { expanded = false }) {
            val options = listOf(
                Res.string.add_text to addText,
                Res.string.pdf_import to pdf,
                Res.string.h1_import_text to text,
                Res.string.h1_import_epub to epub,
                Res.string.h1_import_image to image,
                Res.string.h1_import_csv to csv,
                Res.string.h1_import_anki to json,
                Res.string.h1_sample_csv to { sample = true },
                Res.string.watch_video to watch,
            )
            options.forEach { (label, action) -> DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { expanded = false; action() }) }
        }
    }
    preview?.let { body ->
        AlertDialog(onDismissRequest = { preview = null }, title = { Text(stringResource(Res.string.h1_ocr_preview)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(previewTitle, { previewTitle = it }, label = { Text(stringResource(Res.string.title)) })
                OutlinedTextField(body, { preview = it }, modifier = Modifier.heightIn(min = 180.dp, max = 360.dp), textStyle = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
            }
        }, confirmButton = {
            TextButton(enabled = body.isNotBlank() && previewTitle.isNotBlank(), onClick = {
                preview = null
                vm.action { open(vm.container.texts.insert(TextDocument(lang = spec.code, title = previewTitle.trim(), body = body))) }
            }) { Text(stringResource(Res.string.save)) }
        }, dismissButton = { TextButton(onClick = { preview = null }) { Text(stringResource(Res.string.cancel)) } })
    }
    pendingAnki?.let { jsonText ->
        AlertDialog(onDismissRequest = { pendingAnki = null }, title = { Text(stringResource(Res.string.h1_import_anki)) }, text = {
            Row { Checkbox(keepProgress, { keepProgress = it }); Text(stringResource(Res.string.h1_keep_progress)) }
        }, confirmButton = { TextButton(onClick = { pendingAnki = null; vm.action { result = withContext(Dispatchers.IO) { repository.anki(jsonText, keepProgress) } } }) { Text(stringResource(Res.string.h1_import)) } }, dismissButton = { TextButton(onClick = { pendingAnki = null }) { Text(stringResource(Res.string.cancel)) } })
    }
    result?.let { report -> AlertDialog(onDismissRequest = { result = null }, title = { Text(stringResource(Res.string.h1_import_complete)) }, text = { Text(stringResource(Res.string.h1_import_counts, report.imported, report.skipped)) }, confirmButton = { TextButton(onClick = { result = null }) { Text(stringResource(Res.string.back)) } }) }
    if (sample) {
        val clipboard = LocalClipboardManager.current
        val sampleText = "word,meaning,sentence,deck\nhello,سلام,Hello world!,Basics\n\"thank you\",متشکرم,Thank you for your help.,Basics\n"
        AlertDialog(onDismissRequest = { sample = false }, title = { Text(stringResource(Res.string.h1_sample_csv)) }, text = { Text(sampleText) }, confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(sampleText)); sample = false }) { Text(stringResource(Res.string.h1_copy)) } })
    }
}
