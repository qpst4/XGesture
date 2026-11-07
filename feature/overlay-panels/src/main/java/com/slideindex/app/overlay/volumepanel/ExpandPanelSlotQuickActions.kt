package com.slideindex.app.overlay.volumepanel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 扩展面板槽位长按后的「小横条」。
 *
 * 交互与排布**照抄搜索面板的 app 候选卡片**（`overlay/searchpanel/SearchPanelAppQuickActions.kt`）：
 * 长按后弹一条横条 → 手指所在槽位高亮（默认就落在「打开」上，即松手不动 = 直接打开）→
 * 松手执行高亮那个；手指在屏幕右半边时横条镜像，保证默认槽位仍在手指下面。
 *
 * 两个槽位：
 * - [ExpandPanelSlotQuickAction.OPEN]：按「应用启动方式」策略打开（默认落点，等同于原来的长按直接打开）；
 * - [ExpandPanelSlotQuickAction.EDIT_MODE]：进编辑模式（排在横条右手那位）。
 *
 * 非 app 槽位（Shell / 按键 / 链接之外的动作）与空槽位**不弹横条**，长按直接进编辑模式 ——
 * 见 `VolumePanelContent` 里 `opensSomething()` 那一层判断。
 */
enum class ExpandPanelSlotQuickAction {
    OPEN,
    EDIT_MODE,
}

private val QuickActionEntries = ExpandPanelSlotQuickAction.entries

private enum class StripMode {
    LTR,
    RTL,
}

/** 手指 X ≥ 屏幕宽的这个比例 → 横条镜像（默认槽位换到另一边）。 */
private const val RTL_STRIP_MIN_SCREEN_X_FRACTION = 0.52f

private val StripShape = RoundedCornerShape(18.dp)
private val StripItemWidth = 48.dp
private val StripItemSpacing = 2.dp
private val StripPaddingH = 10.dp
private val StripPaddingV = 6.dp
private val StripScreenMargin = 8.dp
private val StripPopupGapAboveIcon = 6.dp

private fun stripModeForFingerX(fingerX: Float, screenWidthPx: Float): StripMode =
    if (fingerX >= screenWidthPx * RTL_STRIP_MIN_SCREEN_X_FRACTION) StripMode.RTL else StripMode.LTR

/** 默认落点 = 「打开」那一格（镜像后排在最右）。 */
private fun defaultOpenSlot(mode: StripMode): Int = when (mode) {
    StripMode.LTR -> 0
    StripMode.RTL -> QuickActionEntries.lastIndex
}

private fun actionForSlot(mode: StripMode, slotIndex: Int): ExpandPanelSlotQuickAction = when (mode) {
    StripMode.LTR -> QuickActionEntries[slotIndex]
    StripMode.RTL -> QuickActionEntries[QuickActionEntries.lastIndex - slotIndex]
}

@Composable
fun ExpandPanelSlotQuickActionTarget(
    enabled: Boolean,
    /** 「打开」会以全屏还是小窗打开 —— 按「应用启动方式」策略解析出来的结果，只影响图标与文案。 */
    openFullscreen: Boolean,
    settings: AppSettings,
    onAction: (ExpandPanelSlotQuickAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }
    val view = LocalView.current
    val density = LocalDensity.current
    val yCancelThresholdPx = with(density) { 56.dp.toPx() }
    val itemWidthPx = with(density) { StripItemWidth.toPx() }
    val itemSpacingPx = with(density) { StripItemSpacing.toPx() }
    val rowPaddingStartPx = with(density) { StripPaddingH.toPx() }
    val rowPaddingEndPx = with(density) { StripPaddingH.toPx() }
    val totalItemStridePx = itemWidthPx + itemSpacingPx
    val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val screenMarginPx = with(density) { StripScreenMargin.toPx() }
    val stripHeightAboveAnchorPx = with(density) { 42.dp.toPx() }
    val popupGapAbovePx = with(density) { StripPopupGapAboveIcon.toPx() }

    var anchorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var hoveredSlotIndex by remember { mutableIntStateOf(-1) }
    var stripMode by remember { mutableStateOf(StripMode.LTR) }
    var dragStartFingerY by remember { mutableFloatStateOf(0f) }
    var rowAnchorLeftInWindow by remember { mutableFloatStateOf(0f) }

    fun stripWidthPx(): Float {
        val count = QuickActionEntries.size
        return rowPaddingStartPx + rowPaddingEndPx +
            count * itemWidthPx +
            (count - 1).coerceAtLeast(0) * itemSpacingPx
    }

    fun clampRowAnchorLeft(left: Float): Float {
        val rowWidth = stripWidthPx()
        return left.coerceIn(
            screenMarginPx,
            (screenWidthPx - rowWidth - screenMarginPx).coerceAtLeast(screenMarginPx),
        )
    }

    fun slotIndexAtFingerX(fingerX: Float): Int {
        val localX = fingerX - rowAnchorLeftInWindow - rowPaddingStartPx
        return (localX / totalItemStridePx).toInt().coerceIn(0, QuickActionEntries.lastIndex)
    }

    fun anchorRowWithSlotUnderFinger(fingerX: Float, slotIndex: Int) {
        val desiredLeft = fingerX - rowPaddingStartPx - slotIndex * totalItemStridePx - itemWidthPx / 2f
        rowAnchorLeftInWindow = clampRowAnchorLeft(desiredLeft)
    }

    fun endDragSession() {
        isDragging = false
        hoveredSlotIndex = -1
        rowAnchorLeftInWindow = 0f
        stripMode = StripMode.LTR
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { anchorCoordinates = it }
            .pointerInput(screenWidthPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        val coords = anchorCoordinates ?: return@detectDragGesturesAfterLongPress
                        val finger = coords.localToWindow(startOffset)
                        dragStartFingerY = finger.y
                        val mode = stripModeForFingerX(finger.x, screenWidthPx)
                        stripMode = mode
                        val defaultSlot = defaultOpenSlot(mode)
                        anchorRowWithSlotUnderFinger(finger.x, defaultSlot)
                        hoveredSlotIndex = defaultSlot
                        isDragging = true
                        HapticHelper.longThreshold(view, settings)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val coords = anchorCoordinates ?: return@detectDragGesturesAfterLongPress
                        val finger = coords.localToWindow(change.position)
                        if (abs(finger.y - dragStartFingerY) > yCancelThresholdPx) {
                            if (hoveredSlotIndex != -1) {
                                hoveredSlotIndex = -1
                                HapticHelper.appTick(view, settings)
                            }
                        } else {
                            val index = slotIndexAtFingerX(finger.x)
                            if (index != hoveredSlotIndex) {
                                hoveredSlotIndex = index
                                HapticHelper.appTick(view, settings)
                            }
                        }
                    },
                    onDragEnd = {
                        if (isDragging && hoveredSlotIndex in QuickActionEntries.indices) {
                            onAction(actionForSlot(stripMode, hoveredSlotIndex))
                        }
                        endDragSession()
                    },
                    onDragCancel = { endDragSession() },
                )
            },
    ) {
        content()

        if (isDragging) {
            val coords = anchorCoordinates
            if (coords != null && coords.isAttached) {
                val anchorTopLeft = coords.boundsInWindow().topLeft
                val popupOffsetX = (rowAnchorLeftInWindow - anchorTopLeft.x).roundToInt()
                val popupOffsetY = -(stripHeightAboveAnchorPx + popupGapAbovePx).roundToInt()
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(popupOffsetX, popupOffsetY),
                ) {
                    ExpandPanelSlotQuickActionStrip(
                        mode = stripMode,
                        hoveredSlotIndex = hoveredSlotIndex,
                        openFullscreen = openFullscreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandPanelSlotQuickActionStrip(
    mode: StripMode,
    hoveredSlotIndex: Int,
    openFullscreen: Boolean,
) {
    val stripBackground = Color(0xFF2C2C2E).copy(alpha = 0.94f)
    Row(
        modifier = Modifier
            .shadow(8.dp, StripShape)
            .clip(StripShape)
            .background(stripBackground)
            .padding(horizontal = StripPaddingH, vertical = StripPaddingV),
        horizontalArrangement = Arrangement.spacedBy(StripItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickActionEntries.indices.forEach { slotIndex ->
            ExpandPanelSlotQuickActionSlot(
                action = actionForSlot(mode, slotIndex),
                highlighted = slotIndex == hoveredSlotIndex,
                openFullscreen = openFullscreen,
            )
        }
    }
}

@Composable
private fun ExpandPanelSlotQuickActionSlot(
    action: ExpandPanelSlotQuickAction,
    highlighted: Boolean,
    openFullscreen: Boolean,
) {
    val icon = when (action) {
        ExpandPanelSlotQuickAction.OPEN -> if (openFullscreen) {
            Icons.Outlined.Fullscreen
        } else {
            Icons.Outlined.PictureInPictureAlt
        }
        ExpandPanelSlotQuickAction.EDIT_MODE -> Icons.Outlined.Edit
    }
    val label = when (action) {
        ExpandPanelSlotQuickAction.OPEN -> stringResource(
            if (openFullscreen) {
                R.string.quick_wheel_launch_fullscreen
            } else {
                R.string.quick_wheel_launch_free_window
            },
        )
        ExpandPanelSlotQuickAction.EDIT_MODE -> stringResource(R.string.widget_panel_edit_mode)
    }
    val iconTint = if (highlighted) Color.White else Color.White.copy(alpha = 0.72f)
    val labelColor = if (highlighted) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.62f)
    Column(
        modifier = Modifier.width(StripItemWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.size(width = StripItemWidth, height = 22.dp),
        ) {
            if (highlighted) {
                Box(
                    modifier = Modifier
                        .offset(y = (-2).dp)
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(18.dp),
                tint = iconTint,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
