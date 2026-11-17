package com.slideindex.app.overlay

import android.util.Log
import android.view.MotionEvent
import com.slideindex.app.overlay.layout.TaskSwitcherPanelLayout
import com.slideindex.app.settings.TaskSwitcherCloseMode
import com.slideindex.app.util.RecentAppEntry
import com.slideindex.app.util.RecentTasksLoader
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.TaskSwitcherLockStore
import com.slideindex.app.util.TaskSwitcherMenuActions

internal class TaskSwitcherTouchHandler(
    internal val ctrl: TaskSwitcherOverlayController
) {
    internal val host get() = ctrl.host
    internal val pickResolver = TaskSwitcherPickResolver(this)
    internal val scrollHandler = TaskSwitcherScrollHandler(this)
    internal val contextMenuHandler = TaskSwitcherContextMenuHandler(this)
    internal val longPressHandler = TaskSwitcherLongPressHandler(this)

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        ctrl.lastTaskSwitcherTouchX = localX
        ctrl.lastTaskSwitcherTouchY = localY
        if (ctrl.taskSwitcherExiting) return true
        val continuousPick = host.gestureSession().taskSwitcherContinuousPickActive()
        if (ctrl.taskSwitcherContextMenuActive() && !continuousPick) {
            if (contextMenuHandler.handleContextMenuTouch(event, localX, localY)) {
                return true
            }
        }
        val layout = ctrl.taskSwitcherLayout ?: ctrl.computeTaskSwitcherLayout().also { ctrl.taskSwitcherLayout = it }
        val touchX = host.panelEnterAdjustedX(localX, layout.panelRect)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clearTaskSwitcherPickHighlights()
                ctrl.taskSwitcherClosePressIndex = -1
                ctrl.taskSwitcherRowPressIndex = -1
                ctrl.taskSwitcherGestureScrolled = false
                if (!layout.panelRect.contains(touchX, localY)) {
                    ctrl.endTaskSwitcherSession()
                    return true
                }
                scrollHandler.beginScrollDrag(localY)
                scrollHandler.trackVelocity(event)
                if (continuousPick && continuousPickReady()) {
                    val pick = pickResolver.resolve(layout, localX, localY)
                    pickResolver.updateContinuous(layout, pick, event.eventTime, haptic = true)
                } else if (!continuousPick) {
                    val pick = pickResolver.resolve(layout, localX, localY)
                    pickResolver.apply(pick, haptic = false)
                    host.invalidate()
                    if (pick.close >= 0) {
                        ctrl.taskSwitcherClosePressIndex = pick.close
                        ctrl.taskSwitcherClosePressDownTime = event.eventTime
                        ctrl.taskSwitcherCloseLongPressTriggered = false
                        layout.rows.getOrNull(pick.close)?.entry?.packageName?.let { packageName ->
                            longPressHandler.scheduleCloseLongPress(pick.close, packageName)
                        }
                    }
                    if (pick.row >= 0) {
                        ctrl.taskSwitcherRowPressIndex = pick.row
                        ctrl.taskSwitcherRowPressDownTime = event.eventTime
                        ctrl.taskSwitcherRowLongPressTriggered = false
                        longPressHandler.scheduleRowLongPress(pick.row)
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                scrollHandler.trackVelocity(event)
                if (continuousPick && contextMenuHandler.handleContinuousMenuMove(localX, touchX, localY)) {
                    return true
                }
                if (host.gestureSession().isMoveTimeActionLocked()) {
                    if (updateTaskSwitcherEdgeTracking(event.rawY, localX, localY)) return true
                    return true
                }
                if (updateTaskSwitcherEdgeTracking(event.rawY, localX, localY)) return true
                if (continuousPick) {
                    if (!continuousPickReady()) {
                        host.invalidate()
                        return true
                    }
                    if (!pickResolver.isInteractiveTouch(localX, localY, layout)) {
                        longPressHandler.clearContinuousLongPressTracking()
                        clearTaskSwitcherPickHighlights()
                        host.invalidate()
                        return true
                    }
                    scrollHandler.applyEdgeAutoScroll(layout, localY)
                    val current = ctrl.taskSwitcherLayout ?: ctrl.computeTaskSwitcherLayout().also { ctrl.taskSwitcherLayout = it }
                    val pick = pickResolver.resolve(current, localX, localY)
                    val menu = ctrl.taskSwitcherContextMenu.takeIf { ctrl.taskSwitcherContextMenuActive() }
                    if (menu != null &&
                        !contextMenuHandler.shouldDismissMenuForContinuousSlide(current, localX, localY, menu)
                    ) {
                        host.invalidate()
                        return true
                    }
                    pickResolver.updateContinuous(current, pick, event.eventTime, haptic = true)
                    host.invalidate()
                    return true
                }
                if (ctrl.taskSwitcherClosePressIndex >= 0) {
                    val current = ctrl.taskSwitcherLayout ?: layout
                    if (pickResolver.isDownPickHeld(localX, localY, current)) {
                        return true
                    }
                    longPressHandler.cancelCloseLongPress()
                    ctrl.taskSwitcherClosePressIndex = -1
                    ctrl.taskSwitcherClosePressDownTime = 0L
                    ctrl.taskSwitcherCloseLongPressTriggered = false
                }
                if (scrollHandler.handleScrollMove(touchX, localY)) return true
                if (ctrl.taskSwitcherScrollDragging) return true
                val current = ctrl.taskSwitcherLayout ?: layout
                if (!pickResolver.isDownPickHeld(localX, localY, current)) {
                    longPressHandler.cancelRowLongPress()
                    longPressHandler.cancelCloseLongPress()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (continuousPick && contextMenuHandler.handleContinuousMenuUp(touchX, localY)) {
                    resetTaskSwitcherTouchHighlights()
                    return true
                }
                if (host.gestureSession().releaseImmediateGestureLock()) {
                    if (!continuousPick && pickResolver.isDownPickHeld(localX, localY, layout)) {
                        performTaskSwitcherUpAction(layout, event)
                    }
                    host.onInitiatingEdgeGestureReleased()
                    if (host.gestureSession().panelMode() == OverlayPanelMode.TASK_SWITCHER) {
                        host.setPanelPresentationFocus(true)
                    }
                    resetTaskSwitcherTouchHighlights()
                    return true
                }
                scrollHandler.finishScrollDrag(event)
                if (!continuousPick &&
                    (ctrl.taskSwitcherGestureScrolled || scrollHandler.isFlinging)
                ) {
                    resetTaskSwitcherTouchHighlights()
                    return true
                }
                if (continuousPick) {
                    val current = ctrl.taskSwitcherLayout ?: layout
                    if (continuousPickReady() &&
                        pickResolver.isInteractiveTouch(localX, localY, layout)
                    ) {
                        val pick = pickResolver.resolve(current, localX, localY)
                        pickResolver.updateContinuous(current, pick, event.eventTime, haptic = false)
                    } else if (!pickResolver.isInteractiveTouch(localX, localY, layout)) {
                        cancelContinuousTaskSwitcherOnLeavePanel()
                        return true
                    } else {
                        resetTaskSwitcherTouchHighlights()
                        return true
                    }
                } else if (!pickResolver.isDownPickHeld(localX, localY, layout)) {
                    resetTaskSwitcherTouchHighlights()
                    return true
                }
                performTaskSwitcherUpAction(layout, event)
                resetTaskSwitcherTouchHighlights()
                return true
            }
        }
        return false
    }

    internal fun clearTaskSwitcherPickHighlights() {
        ctrl.taskSwitcherRowHighlight = -1
        ctrl.taskSwitcherCloseHighlight = -1
        ctrl.taskSwitcherFreeWindowHighlight = -1
        ctrl.taskSwitcherCloseAllHighlight = false
        ctrl.taskSwitcherCloseHapticIndex = -1
        ctrl.taskSwitcherContinuousHapticKey = -1
    }

    internal fun cancelTaskSwitcherCloseLongPress() = longPressHandler.cancelCloseLongPress()

    internal fun cancelTaskSwitcherRowLongPress() = longPressHandler.cancelRowLongPress()

    internal fun cancelTaskSwitcherScrollMotion() = scrollHandler.cancelScrollMotion()

    internal fun continuousPickReady(): Boolean = host.panelEnterProgress() >= 1f

    private fun performTaskSwitcherUpAction(layout: TaskSwitcherPanelLayout, event: MotionEvent) {
        when {
            ctrl.taskSwitcherCloseHighlight >= 0 -> {
                val closeIndex = ctrl.taskSwitcherCloseHighlight
                val row = layout.rows.getOrNull(closeIndex)
                val packageName = row?.entry?.packageName
                val recentEntry = ctrl.recentApps.getOrNull(closeIndex)
                val isLocked = recentEntry?.isLocked == true
                val continuousPick = host.gestureSession().taskSwitcherContinuousPickActive()
                val longPress = if (continuousPick) {
                    ctrl.taskSwitcherCloseLongPressTriggered
                } else {
                    ctrl.taskSwitcherCloseLongPressTriggered ||
                        (ctrl.taskSwitcherClosePressIndex == closeIndex &&
                            event.eventTime - ctrl.taskSwitcherClosePressDownTime >= closeDwellMs())
                }
                longPressHandler.cancelCloseLongPress()
                if (packageName != null && recentEntry != null) {
                    when {
                        ctrl.taskSwitcherCloseLongPressTriggered -> Unit
                        longPress -> {
                            ctrl.taskSwitcherCloseLongPressTriggered = true
                            longPressHandler.toggleLock(closeIndex, packageName, !isLocked)
                        }
                        !isLocked -> {
                            host.hapticConfirmLaunch()
                            val entry = recentEntry
                            if (entry.taskId > 0) {
                                ctrl.recentApps.removeAll { it.taskId == entry.taskId }
                                RecentTasksLoader.removeTaskIds(listOf(entry.taskId))
                            } else {
                                ctrl.recentApps.removeAll { it.app.packageName == packageName }
                                RecentTasksLoader.removePackages(listOf(packageName))
                            }
                            TaskSwitcherLockStore.setLocked(host.context, packageName, locked = false)
                            ctrl.taskSwitcherLayout = null
                            if (ctrl.recentApps.isEmpty()) {
                                ctrl.endTaskSwitcherSession()
                            } else {
                                host.invalidate()
                            }
                            dismissTaskCards(listOf(entry))
                        }
                    }
                }
            }
            ctrl.taskSwitcherCloseAllHighlight -> {
                host.hapticConfirmLaunch()
                val entries = ctrl.recentApps.filterNot { it.isLocked }
                val packages = entries.map { it.app.packageName }
                val dismissed = packages.toSet()
                ctrl.recentApps.removeAll { it.app.packageName in dismissed }
                RecentTasksLoader.removePackages(packages)
                RecentTasksLoader.removeTaskIds(entries.map { it.taskId })
                ctrl.taskSwitcherLayout = null
                if (ctrl.recentApps.isEmpty()) {
                    ctrl.endTaskSwitcherSession()
                } else {
                    host.invalidate()
                }
                dismissTaskCards(entries)
            }
            ctrl.taskSwitcherFreeWindowHighlight >= 0 -> {
                val app = ctrl.recentApps.getOrNull(ctrl.taskSwitcherFreeWindowHighlight)?.app
                if (app != null) {
                    host.hapticConfirmLaunch()
                    ctrl.endTaskSwitcherSession(runBeforeExit = true) {
                        TaskSwitcherMenuActions.launchFreeWindow(
                            host.context,
                            app.packageName,
                            host.settings(),
                            app = app
                        )
                    }
                } else {
                    ctrl.endTaskSwitcherSession()
                }
            }
            ctrl.taskSwitcherRowHighlight >= 0 && !ctrl.taskSwitcherRowLongPressTriggered -> {
                val entry = ctrl.recentApps.getOrNull(ctrl.taskSwitcherRowHighlight)
                ctrl.endTaskSwitcherSession(runBeforeExit = true) {
                    entry?.let {
                        host.hapticConfirmLaunch()
                        host.actionExecutor().switchToRecentTask(
                            taskId = it.taskId,
                            rawIdentifier = it.rawIdentifier,
                            topComponent = it.topComponent,
                            packageName = it.app.packageName,
                            settings = host.settings()
                        )
                    }
                }
            }
        }
    }

    private fun resetTaskSwitcherTouchHighlights() {
        longPressHandler.cancelCloseLongPress()
        longPressHandler.cancelRowLongPress()
        clearTaskSwitcherPickHighlights()
        ctrl.taskSwitcherClosePressIndex = -1
        ctrl.taskSwitcherClosePressDownTime = 0L
        ctrl.taskSwitcherCloseLongPressTriggered = false
        ctrl.taskSwitcherRowPressIndex = -1
        ctrl.taskSwitcherRowPressDownTime = 0L
        ctrl.taskSwitcherRowLongPressTriggered = false
        ctrl.taskSwitcherScrollDragging = false
        host.invalidate()
    }

    private fun cancelContinuousTaskSwitcherOnLeavePanel() {
        if (!host.gestureSession().taskSwitcherContinuousPickActive()) return
        ctrl.dismissTaskSwitcherContextMenu(immediate = true)
        longPressHandler.clearContinuousLongPressTracking()
        clearTaskSwitcherPickHighlights()
        ctrl.endTaskSwitcherSession()
    }

    private fun updateTaskSwitcherEdgeTracking(rawY: Float, localX: Float, localY: Float): Boolean {
        if (!host.zoneLayout().containsTrigger(localX, localY)) return false
        val continuousPick = host.gestureSession().taskSwitcherContinuousPickActive()
        if (!ctrl.shouldFreezeTaskSwitcherAnchor()) {
            ctrl.taskSwitcherExternalAnchor = false
            ctrl.taskSwitcherAnchorRawY = rawY
            ctrl.taskSwitcherFrozenAnchorLocalY = null
            ctrl.taskSwitcherLayout = null
            scrollHandler.scrollToFollowFinger(localY)
        }
        val layout = ctrl.taskSwitcherLayout ?: ctrl.computeTaskSwitcherLayout().also { ctrl.taskSwitcherLayout = it }
        if (continuousPick) {
            if (pickResolver.isInteractiveTouch(localX, localY, layout)) {
                val pick = pickResolver.resolve(layout, localX, localY)
                val menu = ctrl.taskSwitcherContextMenu.takeIf { ctrl.taskSwitcherContextMenuActive() }
                if (menu == null ||
                    contextMenuHandler.shouldDismissMenuForContinuousSlide(layout, localX, localY, menu)
                ) {
                    if (continuousPickReady()) {
                        scrollHandler.applyEdgeAutoScroll(layout, localY)
                        pickResolver.updateContinuous(layout, pick, System.currentTimeMillis(), haptic = true)
                    }
                }
            } else {
                longPressHandler.clearContinuousLongPressTracking()
                clearTaskSwitcherPickHighlights()
            }
        } else {
            clearTaskSwitcherPickHighlights()
        }
        host.invalidate()
        return true
    }

    private fun closeDwellMs(): Long =
        if (host.gestureSession().taskSwitcherContinuousPickActive()) {
            TaskSwitcherOverlayController.TASK_SWITCHER_CLOSE_CONTINUOUS_DWELL_MS
        } else {
            TaskSwitcherOverlayController.TASK_SWITCHER_CLOSE_DWELL_MS
        }

    private fun dismissTaskCards(entries: List<RecentAppEntry>) {
        if (entries.isEmpty()) return
        val mode = host.settings().taskSwitcherCloseMode
        val needDismissRecents = mode == TaskSwitcherCloseMode.DISMISS_RECENTS || mode == TaskSwitcherCloseMode.BOTH
        val needForceStop = mode == TaskSwitcherCloseMode.FORCE_STOP || mode == TaskSwitcherCloseMode.BOTH
        Thread {
            entries.filterNot { it.isLocked }.forEach { entry ->
                if (needForceStop) {
                    TaskManagerUtil.forceStopPackage(entry.app.packageName)
                }
                if (needDismissRecents && TaskManagerUtil.hasPermission()) {
                    val removed = if (entry.taskId > 0) {
                        TaskManagerUtil.removeTaskById(entry.taskId)
                    } else {
                        TaskManagerUtil.removeTaskByPackage(entry.app.packageName)
                    }
                    if (!removed) {
                        Log.w(
                            "EdgeGestureOverlay",
                            "dismissTaskCards failed package=${entry.app.packageName} taskId=${entry.taskId}"
                        )
                    }
                }
            }
            RecentTasksLoader.requestRefreshAfterSwitch(host.appRepository())
        }.start()
    }
}
