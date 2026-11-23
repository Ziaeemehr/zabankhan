package com.ziaee.zabankhan.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import com.ziaee.zabankhan.ui.EditorialActionMenu
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
        EditorialActionMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.add_text)) },
                leadingIcon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { expanded = false; addText() })
            DropdownMenuItem(text = { Text(stringResource(Res.string.pdf_import)) },
                leadingIcon = { Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { expanded = false; picker() })
            if (watch != null) DropdownMenuItem(text = { Text(stringResource(Res.string.watch_video)) },
                leadingIcon = { Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { expanded = false; watch() })
        }
    }
}
