package com.ziaee.zabankhan.pdf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PdfInkToolbar(
    modifier: Modifier, minimized: Boolean, expanded: Boolean, penColor: String, recentColors: List<String>,
    savedColors: List<String>,
    toolSize: Float, eraser: Boolean, highlighter: Boolean, fingerInk: Boolean, smoothPen: Boolean,
    partialEraser: Boolean, onPartialEraser: (Boolean) -> Unit, vertical: Boolean, onToggleOrientation: () -> Unit,
    onMinimize: () -> Unit, onRestore: () -> Unit, onExpandedChange: (Boolean) -> Unit,
    onMove: (Offset) -> Unit, onDragEnd: () -> Unit,
    onColor: (String) -> Unit, onSaveColor: (String) -> Unit, onWidth: (Float) -> Unit, onEraser: (Boolean) -> Unit,
    onHighlighter: (Boolean) -> Unit, onFinger: (Boolean) -> Unit, onSmoothPen: (Boolean) -> Unit,
    onToggleEraser: () -> Unit,
    onUndo: () -> Unit, canUndo: Boolean,
) {
    var paletteOpen by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val ink = inkColor(penColor, colors.primary, colors.secondary, colors.tertiary)
    val penLabel = stringResource(Res.string.pdf_pen)
    val highlighterLabel = stringResource(Res.string.pdf_ink_highlighter)
    val eraserLabel = stringResource(Res.string.pdf_pen_eraser)
    val toolIcon = when { eraser -> Icons.Default.Delete; highlighter -> Icons.Default.Highlight; else -> Icons.Default.Edit }
    val toolLabel = when { eraser -> eraserLabel; highlighter -> highlighterLabel; else -> penLabel }
    val moveLabel = stringResource(Res.string.pdf_ink_toolbar_move)
    val paletteLabel = stringResource(Res.string.pdf_ink_palette)
    val border = BorderStroke(1.dp, colors.outlineVariant)
    val latestMove by rememberUpdatedState(onMove)
    val latestDragEnd by rememberUpdatedState(onDragEnd)
    val dragHandle = Modifier.pointerInput(Unit) {
        detectDragGestures(onDragEnd = { latestDragEnd() }) { change, delta ->
            change.consume()
            latestMove(delta)
        }
    }

    if (minimized) {
        Surface(
            modifier.size(52.dp).combinedClickable(onClick = onRestore, onLongClick = onToggleEraser).then(dragHandle),
            shape = CircleShape, color = colors.surfaceContainerHigh, border = border, shadowElevation = 5.dp,
        ) {
            Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                Icon(toolIcon, stringResource(Res.string.pdf_ink_toolbar_restore), Modifier.size(22.dp))
                if (!eraser) Box(
                    Modifier.align(Alignment.BottomEnd).size(12.dp).background(ink, CircleShape)
                        .border(1.dp, colors.outlineVariant, CircleShape),
                )
            }
        }
        return
    }

    BoxWithConstraints(modifier) {
        val toolbarItems: @Composable () -> Unit = {
            Box(
                Modifier.size(width = if (vertical) 44.dp else 28.dp, height = if (vertical) 28.dp else 44.dp)
                    .then(dragHandle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.DragIndicator, moveLabel, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            }
            PdfInkToolbarGroup(vertical, colors.surfaceContainerLow) {
                PdfInkToolButton(Icons.Default.Edit, penLabel, !eraser && !highlighter) {
                    onEraser(false)
                    onHighlighter(false)
                }
                PdfInkToolButton(Icons.Default.Highlight, highlighterLabel, highlighter, onHighlighter)
                PdfInkToolButton(Icons.Default.Delete, eraserLabel, eraser, onEraser)
            }
            PdfInkToolbarGroup(vertical, colors.surfaceContainerLow) {
                PdfInkSwatch(penColor, selected = false, label = paletteLabel, modifier = Modifier.size(42.dp)) {
                    paletteOpen = true
                }
                Box {
                    val settingsLabel = stringResource(Res.string.pdf_pen_settings)
                    PdfInkToolButton(Icons.Default.Tune, settingsLabel, expanded, onExpandedChange)
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { onExpandedChange(false) },
                        modifier = Modifier.width(224.dp),
                        shape = MaterialTheme.shapes.large,
                        containerColor = colors.surfaceContainerHigh,
                        border = border,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(toolIcon, null, Modifier.size(18.dp), tint = colors.primary)
                                Text(toolLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                if (!eraser) {
                                    IconButton(onClick = {
                                        onExpandedChange(false)
                                        paletteOpen = true
                                    }, modifier = Modifier.size(42.dp)) {
                                        Icon(Icons.Default.Palette, paletteLabel, Modifier.size(20.dp))
                                    }
                                }
                            }
                            if (!eraser) {
                                FlowRow(
                                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    maxItemsInEachRow = 3,
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    recentColors.forEach { key ->
                                        PdfInkSwatch(key, key == penColor, modifier = Modifier.size(42.dp)) { onColor(key) }
                                    }
                                }
                            }
                            PdfInkWidthControl(toolSize, eraser, ink, highlighter, onWidth)
                            Spacer(Modifier.height(4.dp))
                            if (eraser) {
                                PdfInkSettingSwitch(Icons.Default.ContentCut, stringResource(Res.string.pdf_partial_eraser), partialEraser, onPartialEraser)
                            } else if (!highlighter) {
                                PdfInkSettingSwitch(Icons.Default.Gesture, stringResource(Res.string.pdf_smooth_pen), smoothPen, onSmoothPen)
                            }
                            PdfInkSettingSwitch(Icons.Default.TouchApp, stringResource(Res.string.pdf_pen_finger), fingerInk, onFinger)
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = colors.outlineVariant)
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.pdf_pen_undo)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(20.dp)) },
                            enabled = canUndo,
                            onClick = onUndo,
                        )
                    }
                }
            }
            PdfInkToolbarGroup(vertical, Color.Transparent) {
                IconButton(onClick = onToggleOrientation, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (vertical) Icons.Default.SwapHoriz else Icons.Default.SwapVert,
                        stringResource(if (vertical) Res.string.pdf_ink_toolbar_horizontal else Res.string.pdf_ink_toolbar_vertical),
                        Modifier.size(19.dp), tint = colors.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onMinimize, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Remove, stringResource(Res.string.pdf_ink_toolbar_minimize), Modifier.size(19.dp), tint = colors.onSurfaceVariant)
                }
            }
        }
        Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHigh, border = border, shadowElevation = 5.dp) {
            if (vertical) {
                Column(
                    Modifier.heightIn(max = this@BoxWithConstraints.maxHeight).verticalScroll(rememberScrollState()).padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { toolbarItems() }
            } else {
                FlowRow(
                    Modifier.widthIn(max = this@BoxWithConstraints.maxWidth).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { toolbarItems() }
            }
        }
    }
    if (paletteOpen) {
        PdfInkColorPicker(penColor, savedColors, onDismiss = { paletteOpen = false }) { color, addToPalette ->
            onColor(color)
            if (addToPalette) onSaveColor(color)
            paletteOpen = false
        }
    }
}

@Composable
private fun PdfInkToolbarGroup(vertical: Boolean, backgroundColor: Color, content: @Composable () -> Unit) {
    val modifier = Modifier.background(backgroundColor, MaterialTheme.shapes.large).padding(2.dp)
    if (vertical) Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { content() }
    else Row(modifier, verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
private fun PdfInkToolButton(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.size(42.dp),
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = colors.onSurfaceVariant,
            checkedContainerColor = colors.primaryContainer,
            checkedContentColor = colors.onPrimaryContainer,
        ),
    ) {
        Icon(icon, label, Modifier.size(20.dp))
    }
}

@Composable
internal fun PdfInkSwatch(
    key: String,
    selected: Boolean,
    label: String? = null,
    modifier: Modifier = Modifier.size(48.dp),
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val color = inkColor(key, colors.primary, colors.secondary, colors.tertiary)
    val name = label ?: if (key.startsWith("#")) "#${key.takeLast(6)}" else key
    IconButton(onClick = onClick, modifier = modifier.semantics {
        contentDescription = name
        this.selected = selected
    }) {
        val ring = if (selected) Modifier.border(2.dp, colors.primary, CircleShape) else Modifier
        Box(Modifier.size(34.dp).then(ring), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(26.dp).background(color, CircleShape).border(1.dp, colors.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(
                    Icons.Default.Check, null, Modifier.size(15.dp),
                    tint = if (color.luminance() > 0.179f) Color.Black else Color.White,
                )
            }
        }
    }
}

@Composable
private fun PdfInkSettingSwitch(icon: ImageVector, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfInkWidthControl(
    value: Float,
    eraser: Boolean,
    ink: Color = MaterialTheme.colorScheme.primary,
    highlighter: Boolean = false,
    onWidth: (Float) -> Unit,
) {
    val range = if (eraser) PDF_ERASER_MIN_SIZE..PDF_ERASER_MAX_SIZE else PDF_INK_MIN_WIDTH..PDF_INK_MAX_WIDTH
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val previewHeight = (2f + fraction * 8f).dp
    val valueLabel = "${(value * 1000f).toInt() / 10f}%"
    val description = stringResource(if (eraser) Res.string.pdf_eraser_size else Res.string.pdf_pen_thickness)
    val sliderInteractions = remember { MutableInteractionSource() }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(description, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = value,
                onValueChange = onWidth,
                valueRange = range,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = description },
                interactionSource = sliderInteractions,
                thumb = { SliderDefaults.Thumb(sliderInteractions, thumbSize = DpSize(18.dp, 18.dp)) },
                track = { state ->
                    SliderDefaults.Track(
                        sliderState = state,
                        modifier = Modifier.height(6.dp),
                        drawStopIndicator = null,
                        thumbTrackGapSize = 2.dp,
                    )
                },
            )
            val previewInk = if (highlighter) ink.copy(alpha = .4f) else ink
            val outline = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                if (eraser) {
                    val radius = previewHeight.toPx() / 2 + 2.dp.toPx()
                    drawCircle(outline, radius + 1.dp.toPx())
                    drawCircle(previewInk.copy(alpha = .22f), radius)
                } else {
                    val start = Offset(6.dp.toPx(), center.y)
                    val end = Offset(size.width - 6.dp.toPx(), center.y)
                    drawLine(outline, start, end, previewHeight.toPx() + 2.dp.toPx(), StrokeCap.Round)
                    drawLine(previewInk, start, end, previewHeight.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}
