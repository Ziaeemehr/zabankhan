package com.ziaee.zabankhan.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ziaee.zabankhan.resources.*
import org.jetbrains.compose.resources.stringResource

private val presetInkColors = listOf(
    "#FF171717", "#FF667085", "#FF98A2B3", "#FFD0D5DD",
    "#FFB42318", "#FFDC6803", "#FFCA8A04", "#FF3F6212",
    "#FF15803D", "#FF0F766E", "#FF026AA2", "#FF175CD3",
    "#FF5925DC", "#FF7A5AF8", "#FFC11574", "#FFD92D20",
)
private val inkHueSpectrum = Brush.horizontalGradient(
    (0..6).map { Color.hsv(it * 60f, 1f, 1f) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfInkColorPicker(
    selected: String,
    savedColors: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (color: String, addToPalette: Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val currentColor = inkColor(selected, scheme.primary, scheme.secondary, scheme.tertiary)
    val initial = remember(currentColor) { currentColor.inkHsv() }
    var hue by rememberSaveable(selected) { mutableFloatStateOf(initial.hue) }
    var saturation by rememberSaveable(selected) { mutableFloatStateOf(initial.saturation) }
    var value by rememberSaveable(selected) { mutableFloatStateOf(initial.value) }
    var swatchKey by rememberSaveable(selected) { mutableStateOf<String?>(selected) }
    val previewColor = swatchKey?.let { inkColor(it, scheme.primary, scheme.secondary, scheme.tertiary) }
        ?: Color.hsv(hue, saturation, value)
    val colorKey = swatchKey ?: "#" + previewColor.toArgb().toUInt().toString(16).padStart(8, '0').uppercase()
    val addToPalette = colorKey.startsWith("#") && colorKey !in presetInkColors && colorKey !in savedColors
    val hueLabel = stringResource(Res.string.pdf_ink_hue)

    fun chooseSwatch(key: String) {
        val hsv = inkColor(key, scheme.primary, scheme.secondary, scheme.tertiary).inkHsv()
        hue = hsv.hue
        saturation = hsv.saturation
        value = hsv.value
        swatchKey = key
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        title = { Text(stringResource(Res.string.pdf_ink_palette)) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        PdfInkShadeField(hue, saturation, value) { newSaturation, newValue ->
                            saturation = newSaturation
                            value = newValue
                            swatchKey = null
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Slider(
                                value = hue,
                                onValueChange = {
                                    hue = it
                                    // An achromatic swatch needs some saturation and light to show a hue change.
                                    if (saturation == 0f) saturation = .8f
                                    if (value == 0f) value = .9f
                                    swatchKey = null
                                },
                                valueRange = 0f..360f,
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hueLabel },
                                thumb = {
                                    Box(
                                        Modifier.size(22.dp).background(Color.hsv(hue, 1f, 1f), CircleShape)
                                            .border(2.dp, Color.White, CircleShape),
                                    )
                                },
                                track = {
                                    Box(Modifier.fillMaxWidth().height(12.dp).background(inkHueSpectrum, CircleShape))
                                },
                            )
                        }
                        PdfInkStrokeSample(previewColor)
                    }
                }
                if (savedColors.isNotEmpty()) {
                    PdfInkSwatchGroup(stringResource(Res.string.pdf_ink_saved_colors), savedColors, colorKey, ::chooseSwatch)
                }
                PdfInkSwatchGroup(stringResource(Res.string.pdf_ink_presets), presetInkColors, colorKey, ::chooseSwatch)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(colorKey, addToPalette) }) {
                Text(stringResource(if (addToPalette) Res.string.pdf_ink_add_color else Res.string.pdf_ink_use_color))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun PdfInkStrokeSample(color: Color) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceContainerLowest, MaterialTheme.shapes.medium)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            stringResource(Res.string.pdf_ink_stroke_preview),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(20.dp).background(color, CircleShape).border(1.dp, scheme.outlineVariant, CircleShape))
            Canvas(Modifier.weight(1f).height(22.dp)) {
                val start = Offset(4.dp.toPx(), center.y)
                val end = Offset(size.width - 4.dp.toPx(), center.y)
                drawLine(scheme.outlineVariant, start, end, 6.dp.toPx(), StrokeCap.Round)
                drawLine(color, start, end, 4.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfInkSwatchGroup(label: String, keys: List<String>, selected: String, onColor: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            Modifier.padding(start = 4.dp, top = 2.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 4, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            keys.forEach { key -> PdfInkSwatch(key, selected = key == selected, onClick = { onColor(key) }) }
        }
    }
}

@Composable
private fun PdfInkShadeField(hue: Float, saturation: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val latestChange by rememberUpdatedState(onChange)
    val fieldLabel = stringResource(Res.string.pdf_ink_shade)
    val lighter = stringResource(Res.string.pdf_ink_lighter)
    val darker = stringResource(Res.string.pdf_ink_darker)
    val moreSaturated = stringResource(Res.string.pdf_ink_more_saturated)
    val lessSaturated = stringResource(Res.string.pdf_ink_less_saturated)
    val horizontalBrush = remember(hue) { Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))) }
    val verticalBrush = remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black)) }
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1.7f).clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .semantics {
                contentDescription = fieldLabel
                customActions = listOf(
                    CustomAccessibilityAction(lighter) { latestChange(saturation, (value + .1f).coerceAtMost(1f)); true },
                    CustomAccessibilityAction(darker) { latestChange(saturation, (value - .1f).coerceAtLeast(0f)); true },
                    CustomAccessibilityAction(moreSaturated) { latestChange((saturation + .1f).coerceAtMost(1f), value); true },
                    CustomAccessibilityAction(lessSaturated) { latestChange((saturation - .1f).coerceAtLeast(0f), value); true },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun select(position: Offset) {
                        val x = (position.x / size.width).coerceIn(0f, 1f)
                        val y = (position.y / size.height).coerceIn(0f, 1f)
                        latestChange(x, 1f - y)
                    }
                    select(down.position)
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed || change.isConsumed) break
                        select(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        drawRect(horizontalBrush)
        drawRect(verticalBrush)
        val radius = minOf(9.dp.toPx(), size.minDimension / 2f)
        val marker = Offset(
            (saturation * size.width).coerceIn(radius, size.width - radius),
            ((1f - value) * size.height).coerceIn(radius, size.height - radius),
        )
        drawCircle(Color.Black.copy(alpha = .68f), radius, marker, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, (radius - 1.dp.toPx()).coerceAtLeast(0f), marker, style = Stroke(2.dp.toPx()))
    }
}

private data class InkHsv(val hue: Float, val saturation: Float, val value: Float)

private fun Color.inkHsv(): InkHsv {
    val value = maxOf(red, green, blue)
    val chroma = value - minOf(red, green, blue)
    if (chroma == 0f) return InkHsv(0f, 0f, value)
    val sector = when (value) {
        red -> (green - blue) / chroma
        green -> 2f + (blue - red) / chroma
        else -> 4f + (red - green) / chroma
    }
    return InkHsv((sector * 60f + 360f) % 360f, chroma / value, value)
}
