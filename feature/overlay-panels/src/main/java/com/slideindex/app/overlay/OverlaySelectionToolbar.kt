package com.slideindex.app.overlay

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.border
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.slideindex.app.overlay.panels.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class OverlaySelectionToolbarActions(
    val editable: Boolean = false,
    val showSearch: Boolean = false,
    val showShare: Boolean = false,
    val showTranslate: Boolean = false,
    val onCopy: (String) -> Unit = {},
    val onCut: ((String) -> Unit)? = null,
    val onPaste: (() -> Unit)? = null,
    val onSelectAll: (() -> Unit)? = null,
    val onSearch: ((String) -> Unit)? = null,
    val onShare: ((String) -> Unit)? = null,
    val onTranslate: ((String) -> Unit)? = null
)

fun Modifier.suppressSystemTextContextMenu(): Modifier =
    filterTextContextMenuComponents { false }

fun TextLayoutResult.selectionBounds(selection: TextRange): Rect? {
    if (selection.collapsed || layoutInput.text.isEmpty()) return null
    val start = selection.min.coerceIn(0, layoutInput.text.length)
    val end = selection.max.coerceIn(0, layoutInput.text.length)
    if (start >= end) return null
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = Float.MIN_VALUE
    var bottom = Float.MIN_VALUE
    for (index in start until end) {
        val box = getBoundingBox(index)
        left = min(left, box.left)
        top = min(top, box.top)
        right = max(right, box.right)
        bottom = max(bottom, box.bottom)
    }
    return Rect(left, top, right, bottom)
}

fun LayoutCoordinates.boundsInWindow(): Rect? {
    if (!isAttached) return null
    val topLeft = localToWindow(Offset.Zero)
    val bottomRight = localToWindow(
        Offset(
            size.width.toFloat(),
            size.height.toFloat()
        )
    )
    return Rect(topLeft, bottomRight)
}

fun selectionRectInWindow(
    layoutResult: TextLayoutResult,
    selection: TextRange,
    fieldCoordinates: LayoutCoordinates,
    scrollOffsetY: Int = 0
): Rect? {
    val localRect = layoutResult.selectionBounds(selection) ?: return null
    if (!fieldCoordinates.isAttached) return null
    val scrollY = scrollOffsetY.toFloat()
    val topLeft = fieldCoordinates.localToWindow(Offset(localRect.left, localRect.top - scrollY))
    val bottomRight = fieldCoordinates.localToWindow(Offset(localRect.right, localRect.bottom - scrollY))
    return Rect(topLeft, bottomRight)
}

fun TextLayoutResult.selectionAnchorRect(
    selection: TextRange,
    scrollOffsetY: Int = 0,
    viewportHeightPx: Float = Float.MAX_VALUE
): Rect? {
    if (selection.collapsed || layoutInput.text.isEmpty()) return null
    val start = selection.min.coerceIn(0, layoutInput.text.length)
    val end = selection.max.coerceIn(0, layoutInput.text.length)
    if (start >= end) return null
    val scrollY = scrollOffsetY.toFloat()
    val viewportTop = scrollY
    val viewportBottom = scrollY + viewportHeightPx
    val startBox = getBoundingBox(start)
    val endBox = getBoundingBox((end - 1).coerceAtLeast(start))
    val startVisible = startBox.bottom > viewportTop && startBox.top < viewportBottom
    val endVisible = endBox.bottom > viewportTop && endBox.top < viewportBottom
    val anchorBox = when {
        startVisible -> startBox
        endVisible -> endBox
        else -> startBox
    }
    return Rect(
        left = anchorBox.left,
        top = anchorBox.top,
        right = anchorBox.right,
        bottom = anchorBox.bottom
    )
}

fun selectionAnchorRectInWindow(
    layoutResult: TextLayoutResult,
    selection: TextRange,
    fieldCoordinates: LayoutCoordinates,
    scrollOffsetY: Int = 0,
    viewportHeightPx: Float = Float.MAX_VALUE
): Rect? {
    val localRect = layoutResult.selectionAnchorRect(selection, scrollOffsetY, viewportHeightPx) ?: return null
    if (!fieldCoordinates.isAttached) return null
    val topLeft = fieldCoordinates.localToWindow(Offset(localRect.left, localRect.top))
    val bottomRight = fieldCoordinates.localToWindow(Offset(localRect.right, localRect.bottom))
    return Rect(topLeft, bottomRight)
}

fun selectionRectInField(
    layoutResult: TextLayoutResult,
    selection: TextRange,
    scrollOffsetY: Int = 0
): Rect? {
    val localRect = layoutResult.selectionBounds(selection) ?: return null
    if (scrollOffsetY == 0) return localRect
    val scrollY = scrollOffsetY.toFloat()
    return Rect(
        left = localRect.left,
        top = localRect.top - scrollY,
        right = localRect.right,
        bottom = localRect.bottom - scrollY
    )
}

fun selectionBoundsInWindow(
    layoutResult: TextLayoutResult,
    selection: TextRange,
    fieldCoordinates: LayoutCoordinates
): Rect? {
    val localRect = layoutResult.selectionBounds(selection) ?: return null
    if (!fieldCoordinates.isAttached) return null
    val topLeft = fieldCoordinates.localToWindow(Offset(localRect.left, localRect.top))
    val bottomRight = fieldCoordinates.localToWindow(Offset(localRect.right, localRect.bottom))
    return Rect(topLeft, bottomRight)
}

fun Rect.intersectsVertically(other: Rect, gapPx: Int = 0): Boolean {
    val gap = gapPx.toFloat()
    return bottom + gap > other.top && top - gap < other.bottom
}

data class OverlaySelectionToolbarPlacement(
    val anchorRect: Rect,
    val selectionRect: Rect?,
    val viewportRect: Rect
)

fun overlaySelectionToolbarPlacement(
    layoutResult: TextLayoutResult?,
    selection: TextRange,
    fieldCoordinates: LayoutCoordinates,
    viewportCoordinates: LayoutCoordinates?,
    scrollOffsetY: Int,
    viewportHeightPx: Float,
    windowSize: IntSize
): OverlaySelectionToolbarPlacement? {
    val anchorRect = if (layoutResult != null) {
        selectionAnchorRectInWindow(
            layoutResult,
            selection,
            fieldCoordinates,
            scrollOffsetY,
            viewportHeightPx
        )
    } else {
        fieldCenterSelectionRect(fieldCoordinates)
    } ?: return null
    val viewportRect = viewportCoordinates?.boundsInWindow()
        ?: Rect(
            left = 0f,
            top = 0f,
            right = windowSize.width.toFloat(),
            bottom = windowSize.height.toFloat()
        )
    // 没有 textLayoutResult 时（搜索面板的 TextField 不暴露文本布局）退回用**整个输入框矩形**
    // 当"要避开的区域"：否则遮挡检测拿到 null 会直接放行，工具条被夹回框内、正好压住文字。
    val selectionRect = (
        layoutResult?.let { selectionBoundsInWindow(it, selection, fieldCoordinates) }
            ?: fieldCoordinates.boundsInWindow()
        )?.let { rect ->
        // 选区先裁到可视区内：长文本全选时包围盒会超出视口、铺满整个窗口，
        // 不裁的话工具条探到视口外也会被判成"压住文字"，全选场景就无解了。
        Rect(
            left = max(rect.left, viewportRect.left),
            top = max(rect.top, viewportRect.top),
            right = min(rect.right, viewportRect.right),
            bottom = min(rect.bottom, viewportRect.bottom),
        ).takeIf { it.right > it.left && it.bottom > it.top }
    }
    return OverlaySelectionToolbarPlacement(
        anchorRect = anchorRect,
        selectionRect = selectionRect,
        viewportRect = viewportRect
    )
}

fun selectionRectForToolbar(
    layoutResult: TextLayoutResult?,
    selection: TextRange,
    fieldCoordinates: LayoutCoordinates,
    scrollOffsetY: Int = 0,
    viewportHeightPx: Float = Float.MAX_VALUE
): Rect? {
    if (layoutResult != null) {
        selectionAnchorRectInWindow(
            layoutResult,
            selection,
            fieldCoordinates,
            scrollOffsetY,
            viewportHeightPx
        )?.let { return it }
    }
    return fieldCenterSelectionRect(fieldCoordinates)
}

fun fieldCenterSelectionRect(fieldCoordinates: LayoutCoordinates): Rect? {
    if (!fieldCoordinates.isAttached) return null
    val topLeft = fieldCoordinates.localToWindow(Offset.Zero)
    val bottomRight = fieldCoordinates.localToWindow(
        Offset(
            fieldCoordinates.size.width.toFloat(),
            fieldCoordinates.size.height.toFloat()
        )
    )
    val midY = (topLeft.y + bottomRight.y) / 2f
    val insetX = (bottomRight.x - topLeft.x) * 0.2f
    return Rect(
        left = topLeft.x + insetX,
        top = midY - 12f,
        right = bottomRight.x - insetX,
        bottom = midY + 12f
    )
}

fun selectedText(text: String, selection: TextRange): String? {
    if (selection.collapsed) return null
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    if (start >= end) return null
    return text.substring(start, end)
}

fun cutTextFieldValue(
    value: TextFieldValue,
    onCopy: (String) -> Unit
): TextFieldValue {
    val selected = selectedText(value.text, value.selection) ?: return value
    onCopy(selected)
    val start = value.selection.min
    val end = value.selection.max
    val newText = buildString {
        append(value.text.substring(0, start))
        append(value.text.substring(end))
    }
    return TextFieldValue(newText, TextRange(start))
}

fun pasteIntoTextFieldValue(
    context: android.content.Context,
    value: TextFieldValue
): TextFieldValue {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return value
    val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    if (clipText.isEmpty()) return value
    val start = value.selection.min
    val end = value.selection.max
    val newText = buildString {
        append(value.text.substring(0, start))
        append(clipText)
        append(value.text.substring(end))
    }
    val cursor = start + clipText.length
    return TextFieldValue(newText, TextRange(cursor))
}

fun copyTextToClipboard(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("selection", text))
}

@Composable
fun OverlaySelectionToolbarOverlay(
    visible: Boolean,
    selection: TextRange,
    text: String,
    textLayoutResult: TextLayoutResult?,
    fieldCoordinates: LayoutCoordinates?,
    viewportCoordinates: LayoutCoordinates?,
    scrollOffsetYProvider: () -> Int,
    viewportHeightPx: Float,
    actions: OverlaySelectionToolbarActions
) {
    OverlaySelectionToolbarPopup(
        visible = visible,
        selection = selection,
        text = text,
        textLayoutResult = textLayoutResult,
        fieldCoordinates = fieldCoordinates,
        viewportCoordinates = viewportCoordinates,
        actions = actions,
        scrollOffsetYProvider = scrollOffsetYProvider,
        viewportHeightPx = viewportHeightPx
    )
}

@Composable
fun OverlaySelectionToolbarPopup(
    visible: Boolean,
    selection: TextRange,
    text: String,
    textLayoutResult: TextLayoutResult?,
    fieldCoordinates: LayoutCoordinates?,
    /** 工具条可活动范围；null = 整个窗口（搜索面板用：不能只限于输入框那一块）。 */
    viewportCoordinates: LayoutCoordinates? = null,
    actions: OverlaySelectionToolbarActions,
    scrollOffsetYProvider: () -> Int = { 0 },
    viewportHeightPx: Float = Float.MAX_VALUE
) {
    if (!visible || selection.collapsed || fieldCoordinates == null) return
    val selected = selectedText(text, selection) ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    val popupPositionProvider = OverlaySelectionToolbarPositionProvider(
        placementProvider = { windowSize ->
            overlaySelectionToolbarPlacement(
                layoutResult = textLayoutResult,
                selection = selection,
                fieldCoordinates = fieldCoordinates,
                viewportCoordinates = viewportCoordinates,
                scrollOffsetY = scrollOffsetYProvider(),
                viewportHeightPx = viewportHeightPx,
                windowSize = windowSize
            )
        },
        gapPx = gapPx
    )
    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = {},
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = true
        )
    ) {
        OverlaySelectionToolbarBar(
            actions = actions,
            selectedText = selected,
            onCopy = {
                copyTextToClipboard(context, selected)
                actions.onCopy(selected)
            },
            onCut = {
                actions.onCut?.invoke(selected)
            },
            onPaste = {
                actions.onPaste?.invoke()
            },
            onSelectAll = {
                actions.onSelectAll?.invoke()
            },
            onSearch = {
                actions.onSearch?.invoke(selected)
            },
            onShare = {
                actions.onShare?.invoke(selected)
            },
            onTranslate = {
                actions.onTranslate?.invoke(selected)
            }
        )
    }
}

@Composable
private fun OverlaySelectionToolbarBar(
    actions: OverlaySelectionToolbarActions,
    selectedText: String,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onTranslate: () -> Unit
) {
    val isDark = LocalAppDarkTheme.current
    val backgroundColor = if (isDark) Color(0xFF2A2A2C) else Color.White
    val borderColor = if (isDark) Color(0xFF4A4A4E) else Color(0xFFE0E0E0)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = backgroundColor,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        modifier = Modifier.border(1.dp, borderColor, RoundedCornerShape(22.dp))
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            OverlaySelectionToolbarIcon(
                icon = Icons.Outlined.ContentCopy,
                contentDescription = stringResource(android.R.string.copy),
                onClick = onCopy
            )
            if (actions.editable) {
                OverlaySelectionToolbarIcon(
                    icon = Icons.Outlined.ContentCut,
                    contentDescription = stringResource(android.R.string.cut),
                    enabled = selectedText.isNotEmpty(),
                    onClick = onCut
                )
                OverlaySelectionToolbarIcon(
                    icon = Icons.Outlined.ContentPaste,
                    contentDescription = stringResource(android.R.string.paste),
                    onClick = onPaste
                )
            }
            if (actions.onSelectAll != null) {
                OverlaySelectionToolbarIcon(
                    icon = Icons.Outlined.SelectAll,
                    contentDescription = stringResource(R.string.float_ball_action_select_all),
                    onClick = onSelectAll
                )
            }
            if (actions.showSearch && actions.onSearch != null) {
                OverlaySelectionToolbarIcon(
                    icon = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.gesture_action_search_panel),
                    onClick = onSearch
                )
            }
            if (actions.showShare && actions.onShare != null) {
                OverlaySelectionToolbarIcon(
                    icon = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.float_ball_action_share),
                    onClick = onShare
                )
            }
            if (actions.showTranslate && actions.onTranslate != null) {
                OverlaySelectionToolbarIcon(
                    icon = Icons.Outlined.Translate,
                    contentDescription = stringResource(R.string.float_ball_translate_panel_title),
                    onClick = onTranslate
                )
            }
        }
    }
}

@Composable
private fun OverlaySelectionToolbarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )
    }
}

class OverlaySelectionToolbarPositionProvider(
    private val placementProvider: (IntSize) -> OverlaySelectionToolbarPlacement?,
    private val gapPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val placement = placementProvider(windowSize) ?: return IntOffset.Zero
        val anchorRect = placement.anchorRect
        val selectionRect = placement.selectionRect
        val viewportRect = placement.viewportRect

        val minX = viewportRect.left
        val maxX = (viewportRect.right - popupContentSize.width).coerceAtLeast(minX)
        val centerX = ((anchorRect.left + anchorRect.right) / 2f - popupContentSize.width / 2f)
            .roundToInt()
            .coerceIn(minX.roundToInt(), maxX.roundToInt())

        val minY = viewportRect.top
        val maxY = (viewportRect.bottom - popupContentSize.height).coerceAtLeast(minY)
        val toolbarHeight = popupContentSize.height.toFloat()

        fun toolbarOverlapsSelection(y: Float): Boolean {
            if (selectionRect == null) return false
            val toolbarRect = Rect(
                left = centerX.toFloat(),
                top = y,
                right = centerX + popupContentSize.width.toFloat(),
                bottom = y + toolbarHeight
            )
            return toolbarRect.intersectsVertically(selectionRect, gapPx)
        }

        fun clampY(y: Float): Int = y.roundToInt().coerceIn(minY.roundToInt(), maxY.roundToInt())

        val candidateYs = buildList {
            add(anchorRect.top - toolbarHeight - gapPx)
            add(anchorRect.bottom + gapPx)
            if (selectionRect != null) {
                add(selectionRect.top - toolbarHeight - gapPx)
                add(selectionRect.bottom + gapPx)
            }
        }

        for (candidate in candidateYs) {
            val clamped = clampY(candidate)
            if (!toolbarOverlapsSelection(clamped.toFloat())) {
                return IntOffset(centerX, clamped)
            }
        }

        // 视口内已经全是选中区时（点词关闭的取词面板点「全选」：视口=正文区、选区=整个正文），
        // 在视口里怎么摆都压字。放宽到整个窗口再试一次——正文可视区之外没有文字，工具条可以探出去。
        // 搜索面板的视口本来就是整窗，这一轮等价于原地重试一次，行为不变。
        val windowMaxY = (windowSize.height - toolbarHeight).roundToInt().coerceAtLeast(0)
        for (candidate in candidateYs) {
            val clamped = candidate.roundToInt().coerceIn(0, windowMaxY)
            if (!toolbarOverlapsSelection(clamped.toFloat())) {
                return IntOffset(centerX, clamped)
            }
        }

        val spaceAboveAnchor = anchorRect.top - minY
        val spaceBelowAnchor = maxY + toolbarHeight - anchorRect.bottom
        val fallbackY = if (spaceAboveAnchor >= spaceBelowAnchor) {
            clampY(anchorRect.top - toolbarHeight - gapPx)
        } else {
            clampY(anchorRect.bottom + gapPx)
        }
        return IntOffset(centerX, fallbackY)
    }
}

@Composable
fun rememberOverlaySelectionToolbarState(): OverlaySelectionToolbarState {
    return remember { OverlaySelectionToolbarState() }
}

class OverlaySelectionToolbarState {
    var textLayoutResult by mutableStateOf<TextLayoutResult?>(null)
    var fieldCoordinates by mutableStateOf<LayoutCoordinates?>(null)
    var viewportCoordinates by mutableStateOf<LayoutCoordinates?>(null)
}

fun Modifier.fieldModifier(state: OverlaySelectionToolbarState): Modifier =
    onGloballyPositioned { state.fieldCoordinates = it }

fun Modifier.viewportModifier(state: OverlaySelectionToolbarState): Modifier =
    onGloballyPositioned { state.viewportCoordinates = it }
