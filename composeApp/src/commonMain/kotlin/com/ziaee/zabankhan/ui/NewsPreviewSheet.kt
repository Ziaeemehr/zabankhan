package com.ziaee.zabankhan.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.theme.EditorialRole
import com.ziaee.zabankhan.ui.theme.ZabankhanDesign
import com.ziaee.zabankhan.ui.theme.editorialStyle
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Ported from French Reader's ui/home/NewsPreviewSheet.kt. Shows a selected headline's metadata without fetching its
 * article body; the primary action downloads and opens it, or reopens the existing document. A failure keeps the
 * sheet open with a retryable error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsPreviewSheet(
    headline: Headline, isImporting: Boolean, hasError: Boolean, isAlreadyDownloaded: Boolean,
    onDownloadOrOpen: () -> Unit, onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            NewsCardImage(headline, Modifier.fillMaxWidth().height(180.dp))
            Spacer(Modifier.height(16.dp))
            // Source content: direction follows the text, not the interface.
            Text(headline.item.title, Modifier.fillMaxWidth(), style = editorialStyle(headline.item.title, EditorialRole.SectionTitle))
            Spacer(Modifier.height(8.dp))
            Text(headline.item.snippet, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium.merge(contentStyle(headline.item.snippet)))
            Spacer(Modifier.height(ZabankhanDesign.spacing.xSmall))
            Row {
                val dateSuffix = headline.item.publishedAtMs?.let { ms ->
                    val time = kotlin.time.Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
                    " · ${time.date} ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
                }.orEmpty()
                MetadataBadge(headline.source.name + dateSuffix)
            }
            Spacer(Modifier.height(16.dp))
            if (hasError) {
                Text(stringResource(Res.string.error_generic), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            EditorialPrimaryButton(
                stringResource(if (isAlreadyDownloaded) Res.string.preview_action_open_library else Res.string.preview_action_download_read),
                onClick = onDownloadOrOpen, enabled = !isImporting, loading = isImporting, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
