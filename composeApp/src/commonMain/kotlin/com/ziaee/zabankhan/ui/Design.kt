package com.ziaee.zabankhan.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziaee.zabankhan.resources.*
import com.ziaee.zabankhan.ui.theme.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EditorialTopAppBar(
    title: String, back: (() -> Unit)? = null, modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleStyle: androidx.compose.ui.text.TextStyle? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    backLabel: String? = null,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier) {
        TopAppBar(
            title = {
                Column {
                    Text(title, style = titleStyle ?: editorialStyle(title).copy(fontSize = 18.sp, lineHeight = 24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium.merge(contentStyle(it)), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            },
            windowInsets = windowInsets,
            navigationIcon = { back?.let { EditorialIconButton(it, backIcon, backLabel ?: stringResource(Res.string.back)) } },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorialBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 32.dp, height = 4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f),
            ) {}
        },
        content = content,
    )
}

/** A real search input, retaining the screens' inline filtering and keyboard behavior. */
@Composable
fun EditorialSearchEntry(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = stringResource(Res.string.search),
    showFloatingLabel: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = if (showFloatingLabel) ({ Text(hint) }) else null,
        placeholder = if (showFloatingLabel) null else ({ Text(hint) }),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyLarge.merge(contentStyle(value)),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}
@Composable fun EditorialSectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZabankhanDesign.spacing.xSmall)) {
        Text(title, Modifier.weight(1f), style = editorialStyle(title, EditorialRole.SectionTitle))
        trailing?.invoke()
    }
}
@Composable fun MetadataBadge(
    text: String, modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(modifier, shape = MaterialTheme.shapes.extraSmall, color = containerColor, contentColor = contentColor) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall.merge(contentStyle(text)))
    }
}
@Composable fun EditorialProgressIndicator(progress: Float, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
@Composable fun EditorialPrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false,
) {
    Button(onClick, modifier.defaultMinSize(minHeight = ZabankhanDesign.sizes.touchTarget), enabled = enabled && !loading) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}
@Composable fun EditorialIconButton(onClick: () -> Unit, icon: ImageVector, contentDescription: String, modifier: Modifier = Modifier) {
    IconButton(onClick, modifier.size(ZabankhanDesign.sizes.touchTarget)) { Icon(icon, contentDescription) }
}
@Composable fun EditorialEmptyState(
    icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier,
    actionLabel: String? = null, onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(ZabankhanDesign.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, title, Modifier.padding(16.dp).size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, style = editorialStyle(title, EditorialRole.SectionTitle).copy(textAlign = TextAlign.Center))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) EditorialPrimaryButton(actionLabel, onAction)
    }
}
@Composable fun EditorialCard(
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
    if (onClick == null) Card(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, border = border, content = content)
    else Card(onClick, modifier.fillMaxWidth(), enabled = enabled, shape = MaterialTheme.shapes.large, colors = colors, border = border, content = content)
}

/** One line at any navigation width; reduce only when the translated label overflows. */
@Composable fun EditorialNavigationLabel(text: String) {
    val base = LocalTextStyle.current.fontSize
    var size by remember(text, base) { mutableStateOf(base) }
    Text(text, maxLines = 1, softWrap = false, fontSize = size,
        onTextLayout = { if (it.hasVisualOverflow && size.value > 9f) size *= .92f })
}

@Composable fun EditorialListRow(title: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, title, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, title, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

