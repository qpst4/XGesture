package com.slideindex.app.overlay

import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.opensSomething
import com.slideindex.app.settings.resolvedLaunchPolicy

internal class QuickLauncherPickResolver(
    private val touch: QuickLauncherTouchHandler
) {
    private val ctrl get() = touch.ctrl
    private val host get() = touch.host
    private val scrollHandler get() = touch.scrollHandler

    fun continuousPickReady(): Boolean = host.panelEnterProgress() >= 1f

    fun clearHighlight() {
        host.panelGridSession().clearHighlight()
        ctrl.quickLauncherContinuousHapticIndex = -1
        ctrl.cancelFolderHover()
        cancelLongPress()
    }

    fun updateHighlight(
        localX: Float,
        localY: Float,
        touchX: Float,
        eventTime: Long,
        haptic: Boolean
    ) {
        if (scrollHandler.pageInteractionActive()) return
        val panelRect = ctrl.quickLauncherPanelRect()
        if (!isSelectableTouch(localX, localY, panelRect) ||
            ctrl.quickLauncherPanelController.toolbarContains(touchX, localY)
        ) {
            clearHighlight()
            return
        }
        val effectiveX = if (host.panelContentRect().contains(touchX, localY)) {
            touchX
        } else {
            edgeProjectedX(localY)
        }
        val prev = host.panelGridSession().highlightedIndex
        host.panelGridSession().updateHighlight(effectiveX, localY)
        if (host.panelGridSession().highlightedIndex != prev) {
            syncPressTracking(eventTime)
            checkFolderHover(host.panelGridSession().highlightedIndex)
        }
        if (haptic && host.panelGridSession().highlightedIndex != prev &&
            host.panelGridSession().highlightedIndex >= 0
        ) {
            if (host.panelGridSession().highlightedIndex != ctrl.quickLauncherContinuousHapticIndex) {
                host.hapticTick()
                ctrl.quickLauncherContinuousHapticIndex = host.panelGridSession().highlightedIndex
            }
        }
    }

    private fun checkFolderHover(localIndex: Int) {
        ctrl.cancelFolderHover()
        if (localIndex < 0 || ctrl.folderOpen) return
        val item = host.panelGridSession().highlightedQuickItem() ?: return
        if (item.type != QuickLauncherItemType.FOLDER) return
        val pageSize = ctrl.quickLauncherPageSize()
        val pageStart = ctrl.quickLauncherPageIndex * pageSize
        val globalIdx = pageStart + localIndex
        val runnable = Runnable {
            if (host.panelGridSession().highlightedIndex == localIndex && !ctrl.folderOpen) {
                ctrl.openFolder(globalIdx)
            }
        }
        ctrl.folderHoverRunnable = runnable
        host.postDelayed(runnable, 180L)
    }

    fun isSelectableTouch(
        localX: Float,
        localY: Float,
        panelRect: RectF
    ): Boolean {
        if (panelRect.isEmpty) return false
        val contentRect = ctrl.quickLauncherPanelController.combinedContentRect(panelRect)
        val touchX = host.panelEnterAdjustedX(localX, contentRect)
        if (ctrl.quickLauncherPanelController.toolbarContains(touchX, localY)) return true
        if (localY < contentRect.top || localY > contentRect.bottom) return false
        if (panelRect.contains(touchX, localY)) return true
        return isInApproachZone(localX, panelRect)
    }

    fun updateEdgeTracking(rawY: Float, localX: Float, localY: Float): Boolean {
        if (!host.zoneLayout().containsTrigger(localX, localY)) return false
        val continuousPick = host.gestureSession().quickLauncherContinuousPickActive()
        if (!shouldFreezeAnchor()) {
            ctrl.quickLauncherAnchorRawY = rawY
            ctrl.quickLauncherFrozenAnchorLocalY = null
        }
        if (continuousPick && continuousPickReady()) {
            val panelRect = ctrl.quickLauncherPanelRect()
            if (isSelectableTouch(localX, localY, panelRect)) {
                val touchX = host.panelEnterAdjustedX(localX, panelRect)
                if (!scrollHandler.pageInteractionActive()) {
                    scrollHandler.applyEdgeAutoPage(touchX)
                    updateHighlight(
                        localX,
                        localY,
                        touchX,
                        android.os.SystemClock.uptimeMillis(),
                        haptic = true
                    )
                } else {
                    clearHighlight()
                }
            } else {
                clearHighlight()
            }
        } else if (!continuousPick) {
            clearHighlight()
        }
        host.invalidate()
        return true
    }

    fun performUpAction(
        event: MotionEvent,
        touchX: Float,
        localX: Float,
        localY: Float
    ): Boolean {
        if (ctrl.quickLauncherPanelController.editMode) return false
        val panelRect = ctrl.quickLauncherPanelRect()
        if (!isSelectableTouch(localX, localY, panelRect)) return false
        val item = host.panelGridSession().highlightedQuickItem() ?: return false
        val longPress = longPressTriggered(event)
        cancelLongPress()
        return when (item.type) {
            QuickLauncherItemType.FOLDER -> {
                val pageSize = ctrl.quickLauncherPageSize()
                val pageStart = ctrl.quickLauncherPageIndex * pageSize
                val localIdx = host.panelGridSession().highlightedIndex
                val targetGlobal = pageStart + localIdx
                ctrl.openFolder(targetGlobal)
                false
            }
            QuickLauncherItemType.ACTION -> {
                val action = QuickLauncherItemCodec.parseActionPayload(item.payload) ?: return false
                val sessionEnds = host.gestureSession().performQuickLauncherAction(
                    action,
                    localX,
                    localY,
                    event.rawX,
                    event.rawY,
                    confirmHaptic = longPress
                )
                // 钉住：面板里的「动作」类条目（不换前台的那些）执行完不收起面板，才能一连按好几个
                // （加粗 → 下划线 → 编号……）；会换前台的动作（打开 App / 链接等）照旧收起 ——
                // 用户的注意力已经离开这个面板了，面板再浮在上面只会挡路。
                if (sessionEnds &&
                    host.gestureSession().quickLauncherPinned() &&
                    !action.opensSomething()
                ) {
                    false
                } else {
                    // 会换前台的动作：面板收起，但「只在本应用」要记住等回来恢复。
                    if (sessionEnds && action.opensSomething()) {
                        ctrl.recordPinnedHiddenByLaunch()
                    }
                    sessionEnds
                }
            }
            else -> {
                if (longPress) {
                    host.hapticConfirmLaunch()
                }
                val launched = host.actionExecutor().launchQuickItem(
                    item,
                    host.settings(),
                    longPressArmed = longPress,
                    anchorRawX = event.rawX,
                    anchorRawY = event.rawY,
                    panelSide = host.side(),
                )
                // ⚠️ 不能拿 `launched` 当"有没有换前台"的判据：[launchQuickItem] 对 APP 类型
                // **恒定返回 false**（那个返回值的语义是"是否走了 action 直通"，不是"有没有启动"）。
                // 用它会让"从面板里点 App 跳走"这一路漏记「待恢复」，回到原应用时面板回不来。
                // APP / SHORTCUT 必然把前台交出去，所以按**条目类型**判断。
                if (item.type == QuickLauncherItemType.APP ||
                    item.type == QuickLauncherItemType.SHORTCUT
                ) {
                    ctrl.recordPinnedHiddenByLaunch()
                }
                ctrl.quickLauncherLaunchEndDeferMs = if (launched) 280L else 0L
                true
            }
        }
    }

    fun syncPressTracking(eventTime: Long) {
        val index = host.panelGridSession().highlightedIndex
        if (index >= 0) {
            if (index != ctrl.quickLauncherPressIndex) {
                scheduleLongPress(index)
            }
            ctrl.quickLauncherPressIndex = index
            ctrl.quickLauncherPressDownTime = eventTime
        } else {
            cancelLongPress()
            ctrl.quickLauncherPressIndex = -1
            ctrl.quickLauncherPressDownTime = 0L
        }
    }

    fun cancelLongPress() {
        ctrl.quickLauncherLongPressRunnable?.let { host.removeCallbacks(it) }
        ctrl.quickLauncherLongPressRunnable = null
        ctrl.quickLauncherLongPressIndex = -1
        ctrl.quickLauncherLongPressArmed = false
    }

    private fun scheduleLongPress(index: Int) {
        cancelLongPress()
        if (!longPressEligible()) return
        ctrl.quickLauncherLongPressIndex = index
        val runnable = Runnable {
            if (host.panelGridSession().highlightedIndex == ctrl.quickLauncherLongPressIndex &&
                ctrl.quickLauncherLongPressIndex >= 0
            ) {
                ctrl.quickLauncherLongPressArmed = true
                host.hapticLongThreshold()
                host.invalidate()
            }
        }
        ctrl.quickLauncherLongPressRunnable = runnable
        host.postDelayed(runnable, host.settings().effectiveLongPressDurationMs().toLong())
    }

    private fun longPressEligible(): Boolean {
        val item = host.panelGridSession().highlightedQuickItem() ?: return false
        if (item.type != QuickLauncherItemType.APP && item.type != QuickLauncherItemType.SHORTCUT) return false
        return host.settings().freeWindowEnabled && host.settings().resolvedLaunchPolicy().usesLongPress()
    }

    internal fun longPressTriggered(event: MotionEvent): Boolean {
        if (ctrl.quickLauncherLongPressArmed) return true
        if (!longPressEligible()) {
            return false
        }
        if (ctrl.quickLauncherPressIndex < 0 ||
            ctrl.quickLauncherPressIndex != host.panelGridSession().highlightedIndex
        ) {
            return false
        }
        return event.eventTime - ctrl.quickLauncherPressDownTime >= host.settings().effectiveLongPressDurationMs()
    }

    private fun edgeProjectedX(localY: Float): Float {
        val rowCells = host.panelGridSession().cellBounds.filter { (_, rect) ->
            localY >= rect.top && localY <= rect.bottom
        }
        if (rowCells.isEmpty()) return host.panelContentRect().centerX()
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> rowCells.minByOrNull { it.second.left }?.second?.centerX()
                ?: host.panelContentRect().centerX()
            PanelSide.RIGHT -> rowCells.maxByOrNull { it.second.right }?.second?.centerX()
                ?: host.panelContentRect().centerX()
        }
    }

    private fun isInApproachZone(localX: Float, panelRect: RectF): Boolean {
        val trigger = host.activeTriggerZoneRect()
        return when (host.side()) {
            PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> localX >= trigger.right && localX <= panelRect.left
            PanelSide.RIGHT -> localX <= trigger.left && localX >= panelRect.right
        }
    }

    private fun shouldFreezeAnchor(): Boolean =
        host.gestureSession().panelMode() == OverlayPanelMode.QUICK_LAUNCHER
}
