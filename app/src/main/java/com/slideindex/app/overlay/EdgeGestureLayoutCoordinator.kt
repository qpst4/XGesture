package com.slideindex.app.overlay

import android.content.res.Resources
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.CollapsedWindowBounds
import com.slideindex.app.settings.AppSettings

internal sealed class OverlayTouchLayout {
    data class TriggerCollapsed(val bounds: CollapsedWindowBounds) : OverlayTouchLayout()
    data class GestureTracking(val bounds: CollapsedWindowBounds) : OverlayTouchLayout()
    data object FullScreen : OverlayTouchLayout()
    data object AdjustPanel : OverlayTouchLayout()
}

internal class EdgeGestureLayoutCoordinator(
    private val context: android.content.Context,
    private val resources: Resources,
    private val zoneLayout: GestureZoneLayout,
    private val gestureSession: GestureSession,
    private val adjustPanelController: AdjustPanelOverlayController,
    private val quickLauncherController: QuickLauncherOverlayController,
    private val shellCoordinator: ShellPanelOverlayController,
    private val settingsProvider: () -> AppSettings,
    private val previewModeProvider: () -> Boolean,
    private val viewSizeProvider: () -> Pair<Int, Int>,
    private val onSessionEnd: () -> Unit,
    /**
     * 权威屏幕尺寸，由 [SideOverlayController] 提供（与触摸捕获窗用的是同一份）。
     *
     * 绘制与命中必须共用同一把尺子：浮层 view 的 context 是 `createWindowContext()` 造出来的
     * WindowContext，它的 `currentWindowMetrics` 在部分 ROM（实测 Flyme）上给出的是窗口/最小应用
     * 边界而不是真实屏幕，会让绘制按错误的纵向基准算触钮的位置与长度——表现就是「看得见的触钮」
     * 和「划得到的触钮」不在同一个地方。
     */
    private val screenSizeProvider: () -> Pair<Int, Int>? = { null },
) {
    var overlayTouchLayout: OverlayTouchLayout = OverlayTouchLayout.FullScreen
        private set

    fun applyExpandedOverlayLayout() {
        overlayTouchLayout = OverlayTouchLayout.FullScreen
        syncZoneLayout()
    }

    fun applyGestureTrackingLayout(bounds: CollapsedWindowBounds) {
        overlayTouchLayout = OverlayTouchLayout.GestureTracking(bounds)
        syncZoneLayout()
    }

    fun applyAdjustPanelOverlayLayout() {
        overlayTouchLayout = OverlayTouchLayout.AdjustPanel
        syncZoneLayout()
    }

    fun syncZoneLayout() = syncZoneLayout(settingsProvider())

    fun syncZoneLayout(settings: AppSettings) {
        val (widthPx, heightPx) = resolveScreenSizePx()
        zoneLayout.update(
            settings = settings,
            viewWidth = widthPx,
            viewHeight = heightPx,
            density = resources.displayMetrics.density,
            sessionActive = gestureSession.isActive(),
            previewMode = previewModeProvider(),
            layoutHeight = heightPx,
            windowOffsetY = 0f,
            screenWidthPx = widthPx,
            screenHeightPx = heightPx
        )
    }

    /**
     * 屏幕尺寸优先级：controller 的权威值 → 本 view 的 `displayMetrics`（真实屏幕，不受 WindowContext
     * 影响）→ 最后才退回窗口尺寸快照。
     */
    private fun resolveScreenSizePx(): Pair<Int, Int> {
        screenSizeProvider()?.let { size ->
            if (size.first > 0 && size.second > 0) return size
        }
        val displayMetrics = resources.displayMetrics
        if (displayMetrics.widthPixels > 0 && displayMetrics.heightPixels > 0) {
            return displayMetrics.widthPixels to displayMetrics.heightPixels
        }
        val snapshot = OverlayScreenMetrics.snapshot(context)
        return snapshot.widthPx to snapshot.heightPx
    }

    /**
     * 面板落位读的「触发带」。
     *
     * ⚠️ 会话激活时不能无条件用 `activeHandleId` 的矩形：`sessionActiveHandleId` 默认恒为
     * [com.slideindex.app.gesture.TriggerHandle.DEFAULT_ID]（"default"，永不为 null），而
     * `triggerZoneRect(id)` 内部查的是 `allTriggerHandles`（**包含已关闭的触钮**）。于是
     * 一个触钮都没开的侧（外部触发 / 全局触控打开的会话）也会拿到一条「幽灵带」，面板锚点被
     * `coerceIn(trigger.top, trigger.bottom)` 夹进那条带里 —— 真机表现就是「面板最高只到屏幕中间」。
     *
     * 修法：只有这个触钮**确实启用**时才用它的矩形；否则退回「启用触钮的并集」。
     * 并集为空时返回空矩形，上层 [EdgeGestureOverlayView.activeTriggerZoneRect] 会合成一条
     * 零高带（纵向=本次触发高度），面板就能跟手指到任意高度。
     */
    fun activeTriggerZoneRect(): android.graphics.RectF {
        if (!gestureSession.isActive()) return zoneLayout.triggerZoneUnionRect()
        val activeId = gestureSession.activeHandleId()
        // triggerZoneRects() 已经过滤过 enabled：这条判断等价于「该触钮确实是开着的」。
        val enabled = zoneLayout.triggerZoneRects().any { it.first == activeId }
        return if (enabled) {
            zoneLayout.triggerZoneRect(activeId)
        } else {
            zoneLayout.triggerZoneUnionRect()
        }
    }

    fun needsPresentationDirectTouch(): Boolean {
        if (gestureSession.panelMode() != OverlayPanelMode.NONE) return true
        // 退场动画期间不再需要直触：那 160ms 里任何落在屏上的触摸都会被这扇全屏窗吃掉，
        // 用户感受就是"条快消失时点哪都没反应"。
        if (adjustPanelController.hasAdjustPanel() && !adjustPanelController.isDismissing()) {
            return true
        }
        if (previewModeProvider()) return false
        if (OverlayTrampolineGuard.blocksOverlayPresentationTouch()) return false
        if (quickLauncherController.isComposeOverlayDialogShowing() ||
            shellCoordinator.isAuxiliaryDialogShowing()
        ) {
            return true
        }
        return false
    }

    fun presentationShouldPassthroughTouches(): Boolean =
        shellCoordinator.isAuxiliaryDialogShowing() ||
            quickLauncherController.isComposeOverlayDialogShowing()

    fun composeOverlayDialogShowing(): Boolean =
        shellCoordinator.isAuxiliaryDialogShowing() ||
            quickLauncherController.isComposeOverlayDialogShowing()

    fun keepsOverlayExpanded(): Boolean =
        OverlayTrampolineGuard.blocksOverlayPresentationTouch() ||
            gestureSession.isActive() ||
            gestureSession.panelMode() != OverlayPanelMode.NONE ||
            adjustPanelController.hasAdjustPanel() ||
            (gestureSession.panelMode() == OverlayPanelMode.SHELL_COMMANDS &&
                shellCoordinator.hasActiveUi())

    fun notifyOverlayLayoutIfNeeded() {
        if (!keepsOverlayExpanded() && !gestureSession.isActive()) {
            onSessionEnd()
        }
    }
}
