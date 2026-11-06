package com.ziaee.zabankhan.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import com.ziaee.zabankhan.ui.EditorialPrimaryButton
import androidx.compose.runtime.*
import com.ziaee.zabankhan.app.AppViewModel
import com.ziaee.zabankhan.data.*
import com.ziaee.zabankhan.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.stringResource

@Composable fun PdfAddMenu(vm: AppViewModel, settings: AppSettings, addText: () -> Unit, open: (Long) -> Unit, watch: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    val picker = rememberPdfPicker { path ->
        if (path != null) vm.action {
            val title = withContext(Dispatchers.IO) {
                runCatching { FileSystem.SYSTEM.read((path + ".title").toPath()) { readUtf8() } }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/').substringBeforeLast('.')
            }
            open(vm.container.texts.insert(TextDocument(lang = requireNotNull(settings.learningLang), title = title, body = "", pdfPath = path)))
        }
    }
    Box {
        EditorialPrimaryButton(stringResource(Res.string.add_text), onClick = { expanded = true })
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.add_text)) }, onClick = { expanded = false; addText() })
            DropdownMenuItem(text = { Text(stringResource(Res.string.pdf_import)) }, onClick = { expanded = false; picker() })
            if (watch != null) DropdownMenuItem(text = { Text(stringResource(Res.string.watch_video)) }, onClick = { expanded = false; watch() })
        }
    }
}
