package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
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
internal fun rememberPdfLibraryImport(
    vm: AppViewModel,
    language: String,
    open: (Long) -> Unit,
    onBusy: (Boolean) -> Unit = {},
): () -> Unit = rememberFilePicker(listOf(FileKind.PDF)) { file ->
    if (file != null) vm.action {
        onBusy(true)
        try {
            val id = withContext(Dispatchers.IO) {
                vm.container.texts.insert(
                    TextDocument(
                        lang = language,
                        title = file.displayName.substringBeforeLast('.'),
                        body = "",
                        pdfPath = file.path,
                    ),
                )
            }
            open(id)
        } finally {
            onBusy(false)
        }
    }
}

@Composable
fun ImportAddMenu(
    vm: AppViewModel, settings: AppSettings, addText: () -> Unit, open: (Long) -> Unit,
    watch: () -> Unit, discover: () -> Unit = {},
    openDatasets: () -> Unit = {},
    trigger: (@Composable (onClick: () -> Unit, busy: Boolean) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    var previewTitle by remember { mutableStateOf("") }
    var pendingCsv by remember { mutableStateOf<Pair<String, CsvWordFile>?>(null) }
    var pendingAnki by remember { mutableStateOf<String?>(null) }
    var keepProgress by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<WordImportResult?>(null) }
    var sample by remember { mutableStateOf(false) }
    val fallback = stringResource(Res.string.h1_imported_words)
    val spec = requireNotNull(Languages.learnable.firstOrNull { it.code == settings.learningLang })
    val lists by remember(spec.code) { vm.container.vocab.observeLists(spec.code) }.collectAsState(emptyList())
    val repository = remember(vm, spec, settings.meaningLang) { ImportRepository(vm.container.database, spec, settings.meaningLang) }
    fun picked(kind: FileKind): (PickedFile?) -> Unit = { file ->
        if (file != null) vm.action {
            busy = true
            try {
                when (kind) {
                    FileKind.TEXT -> open(withContext(Dispatchers.IO) { repository.text(FileSystem.SYSTEM.read(file.path.toPath()) { readUtf8() }, file.displayName) })
                    FileKind.EPUB -> open(withContext(Dispatchers.IO) { repository.epub(FileSystem.SYSTEM.read(file.path.toPath()) { readByteArray() }) })
                    FileKind.IMAGE -> {
                        val words = withContext(Dispatchers.IO) { createOcrEngine().recognizeWords(loadImportImage(file.path), spec.code) }
                        previewTitle = file.displayName.substringBeforeLast('.')
                        preview = words.joinToString(" ") { it.text }
                    }
                    FileKind.CSV -> pendingCsv = file.displayName.substringBeforeLast('.') to withContext(Dispatchers.IO) { parseCsvWords(FileSystem.SYSTEM.read(file.path.toPath()) { readUtf8() }) }
                    FileKind.JSON -> pendingAnki = withContext(Dispatchers.IO) { FileSystem.SYSTEM.read(file.path.toPath()) { readUtf8() } }
                    else -> Unit
                }
            } finally { busy = false }
        }
    }
    val pdf = rememberPdfLibraryImport(vm, spec.code, open) { busy = it }
    val text = rememberFilePicker(listOf(FileKind.TEXT), picked(FileKind.TEXT))
    val epub = rememberFilePicker(listOf(FileKind.EPUB), picked(FileKind.EPUB))
    val image = rememberFilePicker(listOf(FileKind.IMAGE), picked(FileKind.IMAGE))
    val csv = rememberFilePicker(listOf(FileKind.CSV), picked(FileKind.CSV))
    val json = rememberFilePicker(listOf(FileKind.JSON), picked(FileKind.JSON))
    Box {
        if (trigger != null) trigger({ expanded = true }, busy)
        else Button(enabled = !busy, onClick = { expanded = true }) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(Res.string.add_text)) }
        EditorialActionMenu(expanded, { expanded = false }) {
            val groups = listOf<List<Triple<StringResource, ImageVector, () -> Unit>>>(
                listOf(
                    Triple(Res.string.add_text, Icons.Default.Edit, addText),
                    Triple(Res.string.h2_find_content, Icons.Default.Search, discover),
                    Triple(Res.string.dataset_menu_title, Icons.Default.CollectionsBookmark, openDatasets),
                    Triple(Res.string.watch_video, Icons.Default.PlayCircle, watch),
                ),
                listOf(
                    Triple(Res.string.pdf_import, Icons.Default.PictureAsPdf, pdf),
                    Triple(Res.string.h1_import_text, Icons.Default.Description, text),
                    Triple(Res.string.h1_import_epub, Icons.Default.MenuBook, epub),
                    Triple(Res.string.h1_import_image, Icons.Default.Image, image),
                ),
                listOf(
                    Triple(Res.string.h1_import_csv, Icons.Default.TableChart, csv),
                    Triple(Res.string.h1_import_anki, Icons.Default.Style, json),
                    Triple(Res.string.h1_sample_csv, Icons.Default.ContentCopy, { sample = true }),
                ),
            )
            groups.forEachIndexed { index, options ->
                if (index > 0) ActionMenuDivider()
                options.forEach { (label, icon, action) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label), style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { expanded = false; action() },
                    )
                }
            }
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
    pendingCsv?.let { (name, file) ->
        CsvImportDialog(name.ifBlank { fallback }, file, lists, { pendingCsv = null }) { target ->
            pendingCsv = null
            vm.action { result = withContext(Dispatchers.IO) { repository.csv(file, target) } }
        }
    }
    pendingAnki?.let { jsonText ->
        AlertDialog(onDismissRequest = { pendingAnki = null }, title = { Text(stringResource(Res.string.h1_import_anki)) }, text = {
            Row { Checkbox(keepProgress, { keepProgress = it }); Text(stringResource(Res.string.h1_keep_progress)) }
        }, confirmButton = { TextButton(onClick = { pendingAnki = null; vm.action { result = withContext(Dispatchers.IO) { repository.anki(jsonText, keepProgress) } } }) { Text(stringResource(Res.string.h1_import)) } }, dismissButton = { TextButton(onClick = { pendingAnki = null }) { Text(stringResource(Res.string.cancel)) } })
    }
    result?.let { report -> AlertDialog(onDismissRequest = { result = null }, title = { EditorialTopAppBar(stringResource(Res.string.h1_import_complete), back = { result = null }) }, text = { Text(stringResource(Res.string.h1_import_counts, report.imported, report.skipped)) }, confirmButton = {}) }
    if (sample) {
        val clipboard = LocalClipboardManager.current
        val sampleText = "word,meaning,sentence,deck\nhello,سلام,Hello world!,Basics\n\"thank you\",متشکرم,Thank you for your help.,Basics\n"
        AlertDialog(onDismissRequest = { sample = false }, title = { Text(stringResource(Res.string.h1_sample_csv)) }, text = { Text(sampleText) }, confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(sampleText)); sample = false }) { Text(stringResource(Res.string.h1_copy)) } })
    }
}
