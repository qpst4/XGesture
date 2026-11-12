package com.slideindex.app.overlay

import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.service.QuickLauncherAddTrampoline

internal class QuickLauncherTouchHandler(
    internal val ctrl: QuickLauncherOverlayController
) {
    internal val host get() = ctrl.host
    internal val pickResolver = QuickLauncherPickResolver(this)
    internal val scrollHandler = QuickLauncherScrollHandler(this)
    internal val managementHandler = QuickLauncherManagementTouchHandler(this)
    private var lastToolbarSwitchHoverTime: Long = 0L

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (ctrl.quickLauncherExiting) return true
        val panelRect = ctrl.quickLauncherPanelRect()
        val contentRect = ctrl.quickLauncherPanelController.combinedContentRect(panelRect)
        val touchX = host.panelEnterAdjustedX(localX, contentRect)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            ctrl.quickLauncherPageSwipeStartX = touchX
            ctrl.quickLauncherPageSwipeStartY = localY
            val toolbarDown = ctrl.quickLauncherPanelController.toolbarContains(touchX, localY)
            ctrl.quickLauncherToolbarTouchActive = toolbarDown
            managementHandler.beginGesture(toolbarDown)
        }
        val toolbarTouchThisGesture = when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val armed = ctrl.quickLauncherToolbarTouchActive
                ctrl.quickLauncherToolbarTouchActive = false
                armed
            }
            else -> ctrl.quickLauncherToolbarTouchActive
        }
        // 「长按空白进编辑」已经生效时，这次松手不能再当成"点空白 → 关闭面板"。
        // 判定放在后面（子面板/翻页快照释放等各自的短路径之后），否则会连它们的收尾一起吃掉。
        val blankLongPressConsumed = event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        val continuousPick = host.gestureSession().quickLauncherContinuousPickActive()
        EdgeDiag.log(
            "qlTouch",
            "面板收到触摸 action=${MotionEvent.actionToString(event.actionMasked)} " +
                "continuousPick=$continuousPick 高亮=${host.panelGridSession().highlightedIndex} " +
                "local=($localX,$localY) adjustedX=$touchX"
        )
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            if (ctrl.folderOpen && ctrl.folderHandler.consumePageRelease()) {
                return true
            }
            if (!continuousPick && scrollHandler.consumePageRelease()) {
                return true
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            scrollHandler.cancelPageSnapAnimation()
            ctrl.quickLauncherPageChangedThisGesture = false
            ctrl.quickLauncherPageDragOffset = 0f
            ctrl.quickLauncherPageSwipeLocked = false
            ctrl.quickLauncherEdgePageZone = 0
            ctrl.quickLauncherEdgeAutoPageSeeded = false
            if (!host.gestureSession().isMoveTimeActionLocked()) {
                ctrl.quickLauncherOpeningGestureActive = false
            }
            ctrl.quickLauncherPageSwipeStartX = touchX
            ctrl.quickLauncherPageSwipeStartY = localY
            ctrl.quickLauncherPageSwipeTracking = ctrl.quickLauncherPageCount > 1 &&
                host.panelContentRect().contains(touchX, localY)
        }
        val tapGesture = (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL) &&
            managementHandler.isTapGesture(touchX, localY)
        val toolbarCommitAllowed = managementHandler.toolbarCommitAllowed()
        if (ctrl.folderOpen || ctrl.folderGestureActive) {
            if (ctrl.folderHandler.handleTouch(event, touchX, localX, localY)) {
                return true
            }
        }
        if (!continuousPick && managementHandler.handleManagementTouch(
                event,
                touchX,
                localY,
                panelRect,
                tapGesture = tapGesture,
                toolbarCommitAllowed = toolbarCommitAllowed
            )
        ) {
            return true
        }
        if (ctrl.quickLauncherPanelController.editMode && !continuousPick) {
            if (ctrl.quickLauncherPanelController.isDragging()) {
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    host.invalidate()
                }
                return true
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (continuousPick) {
                    if (pickResolver.continuousPickReady() &&
                        pickResolver.isSelectableTouch(localX, localY, panelRect)
                    ) {
                        pickResolver.updateHighlight(
                            localX,
                            localY,
                            touchX,
                            event.eventTime,
                            haptic = true
                        )
                    }
                    host.invalidate()
                    return true
                }
                if (ctrl.quickLauncherPanelController.editMode) {
                    host.invalidate()
                    return true
                }
                if (!pickResolver.isSelectableTouch(localX, localY, panelRect)) {
                    endQuickLauncherSessionAnimated()
                    host.invalidate()
                    return true
                }
                host.panelGridSession().updateHighlight(touchX, localY)
                pickResolver.syncPressTracking(event.eventTime)
                if (host.panelGridSession().highlightedIndex >= 0) {
                    host.hapticTick()
                } else {
                    // 空白处（没有任何格子被高亮）：起长按计时。到点进编辑态，
                    // 短按松手仍是原来的"关闭面板"（见 ACTION_UP 分支）。
                    ctrl.scheduleBlankLongPress(event.eventTime)
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (continuousPick && pickResolver.continuousPickReady()) {
                    ctrl.quickLauncherOpeningGestureActive = false
                }
                // 只有移动超过 10dp 才算"不是按住空白"，避免长按时手指的细微抖动把计时取消掉。
                if (ctrl.quickLauncherBlankPressDownTime > 0L) {
                    val dx = localX - ctrl.quickLauncherPageSwipeStartX
                    val dy = localY - ctrl.quickLauncherPageSwipeStartY
                    val slop = host.dp(10f)
                    if (dx * dx + dy * dy > slop * slop) {
                        ctrl.cancelBlankLongPress()
                    }
                }
                if (host.gestureSession().isMoveTimeActionLocked()) {
                    ctrl.quickLauncherPageSwipeStartX = touchX
                    ctrl.quickLauncherPageSwipeStartY = localY
                    ctrl.quickLauncherPageSwipeLocked = false
                }
                if (scrollHandler.consumePageSwipeMove(touchX, localY)) {
                    host.invalidate()
                    return true
                }
                if (host.gestureSession().isMoveTimeActionLocked() && !continuousPick) {
                    if (pickResolver.updateEdgeTracking(event.rawY, localX, localY)) return true
                    return true
                }
                if (ctrl.quickLauncherOpeningGestureActive &&
                    host.gestureSession().isMoveTimeActionLocked() &&
                    !continuousPick
                ) {
                    host.invalidate()
                    return true
                }
                if (pickResolver.updateEdgeTracking(event.rawY, localX, localY)) return true
                if (continuousPick) {
                    if (!pickResolver.continuousPickReady()) {
                        host.invalidate()
                        return true
                    }
                    if (ctrl.quickLauncherPanelController.toolbarContains(touchX, localY)) {
                        pickResolver.clearHighlight()
                        val action = ctrl.quickLauncherPanelController.resolveToolbarAction(touchX, localY, panelRect)
                        if (action == QuickLauncherPanelToolbar.ToolbarAction.SWITCH) {
                            val now = event.eventTime
                            if (now - lastToolbarSwitchHoverTime > 500L) {
                                lastToolbarSwitchHoverTime = now
                                ctrl.switchToNextPanel()
                                host.invalidate()
                                return true
                            }
                        }
                        host.invalidate()
                        return true
                    }
                    if (scrollHandler.pageInteractionActive()) {
                        host.invalidate()
                        return true
                    }
                    if (!pickResolver.isSelectableTouch(localX, localY, panelRect)) {
                        pickResolver.clearHighlight()
                        host.invalidate()
                        return true
                    }
                    scrollHandler.applyEdgeAutoPage(touchX)
                    pickResolver.updateHighlight(localX, localY, touchX, event.eventTime, haptic = true)
                    host.invalidate()
                    return true
                }
                val prev = host.panelGridSession().highlightedIndex
                if (pickResolver.isSelectableTouch(localX, localY, panelRect)) {
                    host.panelGridSession().updateHighlight(touchX, localY)
                    if (host.panelGridSession().highlightedIndex != prev) {
                        pickResolver.syncPressTracking(event.eventTime)
                    }
                    if (host.panelGridSession().highlightedIndex != prev &&
                        host.panelGridSession().highlightedIndex >= 0
                    ) {
                        host.hapticTick()
                    }
                } else {
                    pickResolver.clearHighlight()
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 长按空白进编辑已经消费掉这次手势 → 只收尾，不关闭面板。
                if (blankLongPressConsumed && ctrl.consumeBlankLongPress()) {
                    ctrl.quickLauncherOpeningGestureActive = false
                    ctrl.quickLauncherPageSwipeTracking = false
                    ctrl.quickLauncherPageSwipeLocked = false
                    ctrl.quickLauncherPageChangedThisGesture = false
                    host.invalidate()
                    return true
                }
                if (host.gestureSession().releaseImmediateGestureLock()) {
                    ctrl.quickLauncherOpeningGestureActive = false
                    host.onInitiatingEdgeGestureReleased()
                    // 钉住态**不抢焦点**：抢焦点会把输入法从下面的 App（如 Obsidian）抢到浮层上，
                    // 用户就再也打不出字了 —— 代价是返回键不再负责关面板（改用手势 / 工具栏「×」）。
                    // 添加页在途时也不抢：浮层在 Activity 之上，抢焦点会让搜索框弹不出键盘。
                    if (!host.gestureSession().quickLauncherPinned() &&
                        !QuickLauncherAddTrampoline.isActive()
                    ) {
                        host.setPanelPresentationFocus(true)
                    }
                    host.invalidate()
                    return true
                }
                ctrl.quickLauncherOpeningGestureActive = false
                if (continuousPick) {
                    if (ctrl.quickLauncherPageSwipeLocked) {
                        scrollHandler.finishPageDrag()
                        ctrl.quickLauncherPageSwipeLocked = false
                        ctrl.quickLauncherPageSwipeTracking = false
                        ctrl.quickLauncherPageChangedThisGesture = false
                        host.invalidate()
                        return true
                    }
                    if (ctrl.quickLauncherPageSnapMotion.isRunning) {
                        host.invalidate()
                        return true
                    }
                    if (managementHandler.tryCommitToolbarOnContinuousPickUp(touchX, localY, panelRect)) {
                        host.gestureSession().clearQuickLauncherContinuousPick()
                        host.gestureSession().finishLeaveOpenFingerTracking()
                        host.notifyPresentationTouchRequirementChanged()
                        host.onInitiatingEdgeGestureReleased()
                        // 这一步可能刚把「添加条目」Activity 拉起来：不要再把焦点抢回浮层，
                        // 否则 Activity 里的搜索框拿不到 IME（浮层窗口在 Activity 之上）。
                        if (!host.gestureSession().quickLauncherPinned() &&
                            !QuickLauncherAddTrampoline.isActive()
                        ) {
                            host.setPanelPresentationFocus(true)
                        }
                        ctrl.quickLauncherPageSwipeLocked = false
                        ctrl.quickLauncherPageChangedThisGesture = false
                        host.invalidate()
                        return true
                    }
                    if (pickResolver.continuousPickReady() &&
                        pickResolver.isSelectableTouch(localX, localY, panelRect)
                    ) {
                        pickResolver.updateHighlight(localX, localY, touchX, event.eventTime, haptic = false)
                        val panelModeBeforeAction = host.gestureSession().panelMode()
                        val launchedApp = host.panelGridSession().highlightedIndex >= 0 &&
                            pickResolver.performUpAction(event, touchX, localX, localY)
                        if (launchedApp) {
                            endQuickLauncherAfterLaunch(ctrl.quickLauncherLaunchEndDeferMs)
                            ctrl.quickLauncherLaunchEndDeferMs = 0L
                        } else if (!ctrl.quickLauncherPanelController.editMode &&
                            !ctrl.quickLauncherOverlayDialogHost.isShowing &&
                            host.panelGridSession().highlightedIndex < 0 &&
                            !toolbarTouchThisGesture &&
                            host.gestureSession().panelMode() == panelModeBeforeAction
                        ) {
                            endQuickLauncherSessionAnimated()
                        } else if (host.gestureSession().quickLauncherPinned() &&
                            host.gestureSession().panelMode() == OverlayPanelMode.QUICK_LAUNCHER
                        ) {
                            // 钉住：条目已经执行（面板要留下），这里把手势收尾掉 —— 面板才进入
                            // 「钉住空闲态」，窗口也才会缩到面板矩形。
                            finishGestureKeepingPinnedPanel()
                        }
                    } else if (!ctrl.quickLauncherPanelController.editMode &&
                        !ctrl.quickLauncherOverlayDialogHost.isShowing &&
                        !toolbarTouchThisGesture &&
                        ctrl.quickLauncherPageSnapMotion.isRunning != true
                    ) {
                        endQuickLauncherSessionAnimated()
                    }
                    ctrl.quickLauncherPageSwipeLocked = false
                    ctrl.quickLauncherPageChangedThisGesture = false
                    host.invalidate()
                    return true
                }
                if (ctrl.quickLauncherPageSwipeLocked) {
                    scrollHandler.finishPageDrag()
                    ctrl.quickLauncherPageSwipeLocked = false
                    ctrl.quickLauncherPageSwipeTracking = false
                    ctrl.quickLauncherPageChangedThisGesture = false
                    host.invalidate()
                    return true
                }
                if (ctrl.quickLauncherPageSnapMotion.isRunning) {
                    host.invalidate()
                    return true
                }
                if (pickResolver.isSelectableTouch(localX, localY, panelRect)) {
                    host.panelGridSession().updateHighlight(touchX, localY)
                } else {
                    pickResolver.clearHighlight()
                }
                val panelModeBeforeAction = host.gestureSession().panelMode()
                val launchedApp = !ctrl.quickLauncherPageChangedThisGesture &&
                    pickResolver.performUpAction(event, touchX, localX, localY)
                if (launchedApp) {
                    endQuickLauncherAfterLaunch(ctrl.quickLauncherLaunchEndDeferMs)
                    ctrl.quickLauncherLaunchEndDeferMs = 0L
                } else if (!ctrl.quickLauncherPanelController.editMode &&
                    !ctrl.quickLauncherOverlayDialogHost.isShowing &&
                    host.panelGridSession().highlightedIndex < 0 &&
                    !ctrl.quickLauncherPageChangedThisGesture &&
                    !toolbarTouchThisGesture &&
                    host.gestureSession().panelMode() == panelModeBeforeAction
                ) {
                    endQuickLauncherSessionAnimated()
                } else if (host.gestureSession().quickLauncherPinned() &&
                    host.gestureSession().panelMode() == OverlayPanelMode.QUICK_LAUNCHER
                ) {
                    // 钉住 + 松手触发：条目执行完面板留下，手势必须收尾（否则窗口不会缩）。
                    finishGestureKeepingPinnedPanel()
                }
                ctrl.quickLauncherPageSwipeTracking = false
                ctrl.quickLauncherPageSwipeLocked = false
                ctrl.quickLauncherPageChangedThisGesture = false
                return true
            }
        }
        return false
    }

    fun applyEditDragAutoPage(touchX: Float, panelRect: RectF): Boolean =
        scrollHandler.applyEditDragAutoPage(touchX, panelRect)

    /**
     * 钉住态的手势收尾：面板留在屏上，但手指跟踪必须结束。
     *
     * 这是「窗口缩到面板矩形」的前提：[GestureSession.quickLauncherPinnedIdle] 要求 `!active`；
     * 只清面板状态而不清 active，会让面板一直停在"手势进行中"，全屏窗也就一直盖着整屏。
     */
    private fun finishGestureKeepingPinnedPanel() {
        host.gestureSession().clearQuickLauncherContinuousPick()
        host.gestureSession().finishLeaveOpenFingerTracking()
        host.notifyPresentationTouchRequirementChanged()
        host.invalidate()
    }

    private fun endQuickLauncherAfterLaunch(deferMs: Long) {
        if (deferMs > 0L) {
            host.postDelayed({ host.gestureSession().endSession() }, deferMs)
        } else {
            host.post { host.gestureSession().endSession() }
        }
    }

    private fun endQuickLauncherSessionAnimated() {
        EdgeDiag.logStack("qlTouch", "endQuickLauncherSessionAnimated：关闭快速启动器面板")
        // 钉住：点面板外 / 面板空白处**不再收起面板**（这正是「钉住」的定义之一）。
        // 但必须把手指跟踪收尾掉 —— 否则会话一直 active，面板永远进不了「钉住空闲态」，
        // 窗口也就不会缩到面板矩形（表现为"面板外点不到下面的应用"）。
        if (host.gestureSession().quickLauncherPinned()) {
            EdgeDiag.log("qlTouch", "钉住面板：保留面板，仅收尾手指跟踪")
            finishGestureKeepingPinnedPanel()
            return
        }
        if (ctrl.quickLauncherExiting) return
        if (host.gestureSession().panelMode() != OverlayPanelMode.QUICK_LAUNCHER) {
            host.gestureSession().endSession()
            return
        }
        ctrl.quickLauncherExiting = true
        host.notifyPresentationTouchRequirementChanged()
        host.startPanelExitAnimation {
            ctrl.quickLauncherExiting = false
            host.gestureSession().endSession()
        }
    }
}
