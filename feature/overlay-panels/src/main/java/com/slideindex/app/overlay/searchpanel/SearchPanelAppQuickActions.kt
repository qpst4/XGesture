package com.slideindex.app.overlay.searchpanel

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
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.platform.LocalContext
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
import com.slideindex.app.R
import com.slideindex.app.freezer.FreezerAppState
import com.slideindex.app.freezer.FreezerOperations
import com.slideindex.app.launcher.AppTarget
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import kotlin.math.roundToInt

enum class SearchPanelAppQuickAction {
    FREE_WINDOW,
    SHARE,
    FREEZE,
    PAUSE,
    DETAILS,
}

private val QuickActionEntries = SearchPanelAppQuickAction.entries

private enum class SearchPanelAppQuickActionStripMode {
    LTR,
    RTL,
}

/** Finger X >= this fraction of screen width → mirrored strip (小窗 on the right). */
private const val RTL_STRIP_MIN_SCREEN_X_FRACTION = 0.52f

private val StripShape = RoundedCornerShape(18.dp)
private val StripItemWidth = 44.dp
private val StripItemSpacing = 2.dp
private val StripPaddingH = 10.dp
private val StripPaddingV = 6.dp
private val StripScreenMargin = 8.dp
private val StripPopupGapAboveIcon = 6.dp

private fun stripModeForFingerX(fingerX: Float, screenWidthPx: Float): SearchPanelAppQuickActionStripMode =
    if (fingerX >= screenWidthPx * RTL_STRIP_MIN_SCREEN_X_FRACTION) {
        SearchPanelAppQuickActionStripMode.RTL
    } else {
        SearchPanelAppQuickActionStripMode.LTR
    }

private fun defaultFreeWindowSlot(mode: SearchPanelAppQuickActionStripMode): Int = when (mode) {
    SearchPanelAppQuickActionStripMode.LTR -> 0
    SearchPanelAppQuickActionStripMode.RTL -> QuickActionEntries.lastIndex
}

private fun actionForSlot(
    mode: SearchPanelAppQuickActionStripMode,
    slotIndex: Int,
): SearchPanelAppQuickAction = when (mode) {
    SearchPanelAppQuickActionStripMode.LTR -> QuickActionEntries[slotIndex]
    SearchPanelAppQuickActionStripMode.RTL -> QuickActionEntries[QuickActionEntries.lastIndex - slotIndex]
}

@Composable
internal fun SearchPanelAppQuickActionTarget(
    packageName: String,
    cloneUserId: Int = 0,
    enabled: Boolean,
    settings: AppSettings,
    onAction: (SearchPanelAppQuickAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }
    val context = LocalContext.current
    val appState = FreezerOperations.stateOf(context, AppTarget(packageName, cloneUserId))
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
    val stripHeightAboveAnchorPx = with(density) { 40.dp.toPx() }
    val popupGapAbovePx = with(density) { StripPopupGapAboveIcon.toPx() }

    var anchorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var hoveredSlotIndex by remember { mutableIntStateOf(-1) }
    var stripMode by remember { mutableStateOf(SearchPanelAppQuickActionStripMode.LTR) }
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
        return (localX / totalItemStridePx)
            .toInt()
            .coerceIn(0, QuickActionEntries.lastIndex)
    }

    fun anchorRowWithSlotUnderFinger(fingerX: Float, slotIndex: Int) {
        val desiredLeft = fingerX - rowPaddingStartPx - slotIndex * totalItemStridePx - itemWidthPx / 2f
        rowAnchorLeftInWindow = clampRowAnchorLeft(desiredLeft)
    }

    fun endDragSession() {
        isDragging = false
        hoveredSlotIndex = -1
        rowAnchorLeftInWindow = 0f
        stripMode = SearchPanelAppQuickActionStripMode.LTR
    }

    Box(
        modifier = modifier.onGloballyPositioned { anchorCoordinates = it },
    ) {
        Box(
            modifier = Modifier.pointerInput(screenWidthPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        val coords = anchorCoordinates ?: return@detectDragGesturesAfterLongPress
                        val finger = coords.localToWindow(startOffset)
                        dragStartFingerY = finger.y
                        val mode = stripModeForFingerX(finger.x, screenWidthPx)
                        stripMode = mode
                        val defaultSlot = defaultFreeWindowSlot(mode)
                        anchorRowWithSlotUnderFinger(finger.x, defaultSlot)
                        hoveredSlotIndex = defaultSlot
                        isDragging = true
                        HapticHelper.longThreshold(view, settings)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val coords = anchorCoordinates ?: return@detectDragGesturesAfterLongPress
                        val finger = coords.localToWindow(change.position)
                        if (kotlin.math.abs(finger.y - dragStartFingerY) > yCancelThresholdPx) {
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
                    onDragCancel = {
                        endDragSession()
                    },
                )
            },
        ) {
            content()
        }

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
                    SearchPanelAppQuickActionStrip(
                        mode = stripMode,
                        hoveredSlotIndex = hoveredSlotIndex,
                        appState = appState,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPanelAppQuickActionStrip(
    mode: SearchPanelAppQuickActionStripMode,
    hoveredSlotIndex: Int,
    appState: FreezerAppState,
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
            SearchPanelAppQuickActionSlot(
                action = actionForSlot(mode, slotIndex),
                highlighted = slotIndex == hoveredSlotIndex,
                appState = appState,
            )
        }
    }
}

@Composable
private fun SearchPanelAppQuickActionSlot(
    action: SearchPanelAppQuickAction,
    highlighted: Boolean,
    appState: FreezerAppState,
) {
    val icon = when (action) {
        SearchPanelAppQuickAction.FREE_WINDOW -> Icons.Outlined.PictureInPictureAlt
        SearchPanelAppQuickAction.SHARE -> Icons.Outlined.Share
        SearchPanelAppQuickAction.FREEZE -> if (appState.isFrozen) {
            Icons.Outlined.LockOpen
        } else {
            Icons.Outlined.AcUnit
        }
        SearchPanelAppQuickAction.PAUSE -> if (appState.isPaused) {
            Icons.Outlined.PlayArrow
        } else {
            Icons.Outlined.Pause
        }
        SearchPanelAppQuickAction.DETAILS -> Icons.Outlined.Info
    }
    val label = when (action) {
        SearchPanelAppQuickAction.FREE_WINDOW ->
            stringResource(R.string.search_panel_app_quick_action_free_window)
        SearchPanelAppQuickAction.SHARE ->
            stringResource(R.string.search_panel_app_quick_action_share)
        SearchPanelAppQuickAction.FREEZE -> if (appState.isFrozen) {
            stringResource(R.string.freezer_action_unfreeze)
        } else {
            stringResource(R.string.freezer_action_freeze)
        }
        SearchPanelAppQuickAction.PAUSE -> if (appState.isPaused) {
            stringResource(R.string.freezer_action_unpause)
        } else {
            stringResource(R.string.freezer_action_pause)
        }
        SearchPanelAppQuickAction.DETAILS ->
            stringResource(R.string.search_panel_app_quick_action_details)
    }
    val iconTint = if (highlighted) {
        Color.White
    } else {
        Color.White.copy(alpha = 0.72f)
    }
    val labelColor = if (highlighted) {
        Color.White.copy(alpha = 0.95f)
    } else {
        Color.White.copy(alpha = 0.62f)
    }
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
