package com.ziaee.zabankhan.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.ziaee.zabankhan.resources.*

import kotlin.math.roundToInt

/** Intercepts the framework's own text-selection toolbar trigger (fired by [BasicTextField]
 * when a selection is active) purely to read its on-screen anchor [rect] and its
 * ready-made [onCopyRequested] clipboard callback -- the actual toolbar UI shown is
 * [SelectionToolbarContent], not the platform's default copy/paste bubble. */
class SelectionToolbarController : TextToolbar, TextContextMenuProvider {
    var touchSelectionRect by mutableStateOf<Rect?>(null)

    // Compose 1.9 Android routes selection through TextContextMenuProvider; older/iOS paths
    // still use TextToolbar. Both feed the identical copied reference popup.
    var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    private var menuGeneration = 0
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val token = ++menuGeneration
        try {
            val anchor = snapshotFlow { coordinates }.filterNotNull().first()
            snapshotFlow { dataProvider.contentBounds(anchor) }.collect { bounds ->
                if (!anchor.isAttached || token != menuGeneration) return@collect
                val origin = anchor.localToWindow(Offset(bounds.left, bounds.top))
                rect = Rect(origin, bounds.size)
                status = TextToolbarStatus.Shown
            }
        } finally {
            if (token == menuGeneration) hide()
        }
    }

    var rect by mutableStateOf<Rect?>(null)
        private set
    var onCopyRequested by mutableStateOf<(() -> Unit)?>(null)
        private set
    override var status: TextToolbarStatus by mutableStateOf(TextToolbarStatus.Hidden)
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        this.rect = rect
        this.onCopyRequested = onCopyRequested
        status = TextToolbarStatus.Shown
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
    }
}

/** Positions a [androidx.compose.ui.window.Popup] just above [rect] (a global-coordinates
 * selection anchor from [SelectionToolbarController]), centered horizontally on it and
 * clamped to stay within the window. */
class SelectionRectPositionProvider(
    private val rect: Rect,
    private val marginPx: Float
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).toFloat().coerceAtLeast(0f)
        val x = (rect.left + rect.width / 2f - popupContentSize.width / 2f).coerceIn(0f, maxX)
        val above = rect.top - popupContentSize.height - marginPx
        val below = rect.bottom + marginPx
        // Prefer above; go below when above would be clipped and below fits, so the selection stays visible.
        val y = if (above >= 0f || below + popupContentSize.height > windowSize.height) above.coerceAtLeast(0f) else below
        return IntOffset(x.roundToInt(), y.roundToInt())
    }
}

/** The floating toolbar shown for a multi-word selection. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionToolbarContent(
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onListen: () -> Unit,
    onNote: (() -> Unit)? = null,
    onHighlight: () -> Unit,
    onChooseHighlightColor: () -> Unit,
    onDeleteHighlight: (() -> Unit)?,
    onMore: () -> Unit,
    onTranslate: (() -> Unit)? = null,
    showReadingActions: Boolean = true,
    showListen: Boolean = showReadingActions,
    onExtend: (() -> Unit)? = null,
    onDefine: (() -> Unit)? = null
) {
    val actions = buildList {
        onDefine?.let { add(ToolbarItem(Icons.Default.MenuBook, stringResource(Res.string.selection_action_define), it)) }
        if (showReadingActions) {
            add(ToolbarItem(Icons.Default.FormatColorFill, stringResource(Res.string.selection_action_highlight), onHighlight))
            add(ToolbarItem(Icons.Default.Palette, stringResource(Res.string.highlight_choose_color), onChooseHighlightColor))
            onDeleteHighlight?.let { add(ToolbarItem(Icons.Default.Delete, stringResource(Res.string.highlight_delete), it)) }
        }
        onExtend?.let { add(ToolbarItem(Icons.Default.SelectAll, stringResource(Res.string.selection_extend), it)) }
        onNote?.let { add(ToolbarItem(Icons.Default.NoteAdd, stringResource(Res.string.pdf_note_add), it)) }
        add(ToolbarItem(Icons.Default.ContentCopy, stringResource(Res.string.selection_action_copy), onCopy))
        if (onDefine == null) add(ToolbarItem(Icons.Default.BookmarkAdd, stringResource(Res.string.selection_action_save), onSave))
        if (showListen) add(ToolbarItem(Icons.Default.VolumeUp, stringResource(Res.string.selection_action_listen), onListen))
        onTranslate?.let { add(ToolbarItem(Icons.Default.Translate, stringResource(Res.string.pdf_translate), it)) }
        if (showReadingActions) add(ToolbarItem(Icons.Default.MoreVert, stringResource(Res.string.selection_action_more), onMore))
    }
    // Icon-only buttons on one continuous half ring whose open side faces the selection,
    // so no selected text is covered.
    androidx.compose.ui.layout.Layout(content = {
        Surface(shape = HalfRingShape, tonalElevation = 2.dp, shadowElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface) {}
        actions.forEach { action ->
            IconButton(onClick = action.onClick, modifier = Modifier.size(40.dp).semantics { contentDescription = action.label }) {
                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }) { measurables, _ ->
        val button = 40.dp.toPx()
        val band = 48.dp.toPx()
        val radius = semicircleRadius(actions.size, 42.dp.toPx())
        val width = (2 * radius + band).toInt()
        val height = (radius + band).toInt()
        val background = measurables.first().measure(androidx.compose.ui.unit.Constraints.fixed(width, height))
        val buttons = measurables.drop(1).map { it.measure(androidx.compose.ui.unit.Constraints()) }
        layout(width, height) {
            background.place(0, 0)
            buttons.forEachIndexed { index, placeable ->
                val (x, y) = semicirclePosition(index, buttons.size, radius)
                // semicirclePosition is relative to a radius-sized box; centre each button on the band.
                placeable.place((x + band / 2 - button / 2).toInt(), (y + band / 2 - button / 2).toInt())
            }
        }
    }
}

/** Half annulus with round ends; the band is 48 dp thick and the shape fills its box. */
private val HalfRingShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    // The layout makes width = 2r + band and height = r + band, so this is band / 2.
    val half = size.height - size.width / 2f
    val band = 2f * half
    val radius = size.width / 2f - half
    val cx = size.width / 2f
    val cy = size.height - half
    val outer = androidx.compose.ui.geometry.Rect(cx - radius - half, cy - radius - half, cx + radius + half, cy + radius + half)
    val ring = androidx.compose.ui.graphics.Path().apply {
        arcTo(outer, 180f, 180f, true)
        if (radius > half) arcTo(androidx.compose.ui.geometry.Rect(cx - radius + half, cy - radius + half, cx + radius - half, cy + radius - half), 0f, -180f, false)
        close()
    }
    val caps = androidx.compose.ui.graphics.Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(cx - radius, cy), band / 2f))
        addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(cx + radius, cy), band / 2f))
    }
    op(ring, caps, androidx.compose.ui.graphics.PathOperation.Union)
}

private class ToolbarItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val onClick: () -> Unit)

/** Smallest radius that keeps neighbouring button centres [spacing] apart along the arc. */
internal fun semicircleRadius(count: Int, spacing: Float): Float =
    if (count < 2) 0f else spacing / (2f * kotlin.math.sin(kotlin.math.PI.toFloat() / (2f * (count - 1))))

/** Top-left offset of button [index] on the upper half circle, left to right. */
internal fun semicirclePosition(index: Int, count: Int, radius: Float): Pair<Float, Float> {
    if (count < 2) return 0f to 0f
    val angle = kotlin.math.PI.toFloat() * (1f - index.toFloat() / (count - 1))
    return radius + radius * kotlin.math.cos(angle) to radius - radius * kotlin.math.sin(angle)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HighlightPalettePopupContent(
    onColorSelected: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    var custom by remember { mutableStateOf(false) }
    var hue by remember { mutableStateOf(48f) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.width(274.dp).padding(10.dp)) {
            Text(
                text = stringResource(if (custom) Res.string.highlight_custom_color else Res.string.highlight_choose_color),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
            if (custom) {
                val hueLabel = stringResource(Res.string.highlight_hue)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f,
                    modifier = Modifier.semantics { contentDescription = hueLabel })
            }
            val swatches = if (custom) {
                listOf(.30f, .45f, .60f, .75f, .87f).associate { lightness ->
                    val color = Color.hsl(hue, .75f, lightness)
                    color.toHighlightKey() to color
                }
            } else HIGHLIGHT_PALETTE
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 6
            ) {
                swatches.forEach { (key, color) ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(color)
                            .clickable { onColorSelected(key) }
                            .semantics { contentDescription = color.toHighlightKey() }
                    )
                }
            }
            if (!custom) {
                TextButton(onClick = { custom = true }) {
                    Text(stringResource(Res.string.highlight_custom_color))
                }
            }
            if (custom || onBack != null) {
                IconButton(onClick = { if (custom) custom = false else onBack?.invoke() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.accessibility_back))
                }
            }
        }
    }
}

/** Replaces [SelectionToolbarContent] in the same popup when the user
 * taps More -- mirrors Android's own selection-toolbar overflow: a small anchored list of every
 * installed ACTION_PROCESS_TEXT app (Ask ChatGPT, Anki Card, Reverso Context, ...), with a Back
 * arrow to return to the main actions instead of a separate full-screen sheet. */
@Composable
fun ProcessTextAppsPopupContent(
    apps: List<ProcessTextAction>,
    onAppSelected: (ProcessTextAction) -> Unit,
    onBack: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.width(220.dp).padding(vertical = 4.dp)) {
            if (apps.isEmpty()) {
                Text(
                    text = stringResource(Res.string.process_text_apps_empty),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(apps) { app ->
                        TextButton(onClick = { onAppSelected(app) }, modifier = Modifier.fillMaxWidth()) {
                            Text(app.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            }
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.accessibility_back))
            }
        }
    }
}

/** Host-provided actions replace Android ACTION_PROCESS_TEXT queries on common targets. */
data class ProcessTextAction(val label: String, val invoke: () -> Unit)
