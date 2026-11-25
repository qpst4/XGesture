package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.settings.AppSettings

internal class EdgeGesturePanelRenderer(
    private val side: PanelSide,
    private val gestureSession: GestureSession,
    private val indexSession: com.slideindex.app.gesture.SlideAlongRailSession,
    private val adjustPanelController: AdjustPanelOverlayController,
    private val indexPanelRenderer: IndexPanelRenderer,
    private val quickLauncherController: QuickLauncherOverlayController,
    private val taskSwitcherController: TaskSwitcherOverlayController,
    private val shellCoordinator: ShellPanelOverlayController,
    private val panelEnterAnimator: OverlayPanelEnterAnimator,
    private val panelContentRect: android.graphics.RectF,
    private val zoneLayout: com.slideindex.app.gesture.GestureZoneLayout,
    private val settingsProvider: () -> AppSettings,
    private val previewModeProvider: () -> Boolean,
    private val previewContentProvider: () -> LayoutPreviewContent,
    private val previewFocusProvider: () -> LayoutPreviewFocus?,
    private val densityProvider: () -> Float,
    private val dpFn: (Float) -> Float,
    private val syncZoneLayout: () -> Unit
) {
    fun draw(canvas: Canvas, viewWidth: Int, viewHeight: Int) {
        val panelVisible = gestureSession.panelMode() != OverlayPanelMode.NONE
        if (viewWidth > 0 && viewHeight > 0 && (gestureSession.isActive() || panelVisible)) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
        if (viewWidth <= 0 || viewHeight <= 0) return
        syncZoneLayout()
        if (previewModeProvider()) {
            when (previewContentProvider()) {
                LayoutPreviewContent.TRIGGER_ONLY -> TriggerZonePreviewRenderer.draw(
                    canvas = canvas,
                    side = side,
                    settings = settingsProvider(),
                    zoneLayout = zoneLayout,
                    density = densityProvider(),
                    dp = dpFn,
                    focusHandleId = previewFocusProvider()?.handleId,
                    showSwipeDistances = previewFocusProvider()?.showSwipeDistances == true
                )
                LayoutPreviewContent.INDEX_ONLY -> indexPanelRenderer.drawLetterRail(canvas)
            }
            return
        }
        adjustPanelController.drawVisibleIndicator(canvas)
        // leave-open 面板在手指抬起后 active=false，但仍需按 panelMode 绘制。
        if (!gestureSession.isActive() && !panelVisible && !adjustPanelController.hasAdjustPanel()) return
        when (gestureSession.panelMode()) {
            OverlayPanelMode.INDEX -> {
                panelEnterAnimator.drawWithAnimation(canvas, indexPanelRenderer.indexPanelContentRect()) {
                    indexPanelRenderer.drawLetterRail(canvas)
                    if (indexSession.selectedLetter != null) {
                        indexPanelRenderer.drawAppGrid(canvas)
                        indexPanelRenderer.drawLetterBubble(canvas)
                    } else {
                        indexPanelRenderer.clearGridPopupBlur()
                    }
                }
            }
            OverlayPanelMode.QUICK_LAUNCHER -> {
                val contentRect = quickLauncherController.enterContentRect()
                panelEnterAnimator.drawWithAnimation(canvas, contentRect) {
                    quickLauncherController.draw(canvas)
                }
            }
            OverlayPanelMode.TASK_SWITCHER -> taskSwitcherController.draw(canvas)
            OverlayPanelMode.SHELL_COMMANDS ->
                shellCoordinator.draw(canvas, panelEnterAnimator.progress, panelContentRect)
            OverlayPanelMode.NONE -> adjustPanelController.syncAdjustIndicatorAnimation()
        }
    }
}
