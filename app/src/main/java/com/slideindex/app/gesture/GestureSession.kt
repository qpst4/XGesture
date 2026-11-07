package com.slideindex.app.gesture
import com.slideindex.app.overlay.asGestureActionRunner

import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.overlay.OverlayPanelMode
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.actionFor
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.resolvedTriggerMode
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.util.ContinuousAdjustController

class GestureSession(
    private val side: PanelSide,
    private val zoneLayout: GestureZoneLayout,
    private val indexSession: SlideAlongRailSession,
    private val pathRecognizer: SwipePathRecognizer,
    private val actionExecutor: ActionExecutor,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onSessionStart(mode: OverlayPanelMode)
        fun onLeaveOpenFingerTrackingFinished() {}
        fun onOpenShellCommandPanel(continuousPick: Boolean)
        fun onShellCommandPanelContinuousRelease()
        fun onShowHoneycombLauncher(
            continuousPick: Boolean,
            rawX: Float,
            rawY: Float,
            forceBrowseMode: Boolean = false
        ): Boolean
        fun onHoneycombLauncherPointerMove(rawX: Float, rawY: Float)
        fun onHoneycombLauncherContinuousRelease(rawX: Float, rawY: Float)
        fun onShowRingLauncher(continuousPick: Boolean, rawX: Float, rawY: Float): Boolean
        fun onRingLauncherPointerMove(rawX: Float, rawY: Float)
        fun onRingLauncherContinuousRelease(rawX: Float, rawY: Float)
        fun onShowFingertipRing(continuousPick: Boolean, rawX: Float, rawY: Float): Boolean
        fun onFingertipRingPointerMove(rawX: Float, rawY: Float)
        fun onFingertipRingContinuousRelease(rawX: Float, rawY: Float)
        fun onShowAdjustPanel(
            mode: ContinuousAdjustController.Mode,
            fraction: Float,
            anchorRawY: Float,
            deferWindowLayout: Boolean = false
        )
        fun onSessionEnd()
        fun onRequestInvalidate()
        fun hapticGestureStart()
        fun hapticLongThreshold()
        fun hapticConfirmLaunch()
        fun scheduleDelayed(runnable: Runnable, delayMs: Long)
        fun cancelDelayed(runnable: Runnable)
    }

    internal var sessionSettings = AppSettings()
    internal val sessionSide: PanelSide get() = side
    internal val sessionIndexSession: SlideAlongRailSession get() = indexSession
    internal val sessionPathRecognizer: SwipePathRecognizer get() = pathRecognizer
    internal val sessionActionExecutor: ActionExecutor get() = actionExecutor
    internal val sessionCallbacks: Callbacks get() = callbacks
    internal val sessionContinuousPick = GestureSessionContinuousPick()
    internal val sessionLongPressCheckRunnable: Runnable get() = longPressCheckRunnable

    private var active = false
    internal var sessionPanelMode = OverlayPanelMode.NONE
    internal var sessionIndexMode = false
    internal var sessionAdjustMode: ContinuousAdjustController.Mode? = null
    internal var sessionAdjustLayoutAnchorRawY = 0f

    /** 「页面滚动」（按住拖动方式）本次手势内是否已激活。 */
    internal var sessionPageScrollHoldActive = false
    internal var sessionMoveTimeActionFired = false
    internal var sessionActiveHandleId = TriggerHandle.DEFAULT_ID
    internal var sessionQuickLauncherPanelId: String = ""

    /**
     * 当前快速启动器面板的「钉住」设置（见 [GestureAction.QuickLauncher.pinned]）。
     *
     * 与 [sessionQuickLauncherPanelId] 同寿：面板收起 / 会话强制复位时一起清掉，
     * 避免下一次普通呼出误继承上一次的钉住状态。
     */
    internal var sessionQuickLauncherPinned: Boolean = false

    /** 钉住范围：是否只钉在钉住那一刻的前台应用（[GestureAction.QuickLauncher.pinCurrentApp]）。 */
    internal var sessionQuickLauncherPinCurrentApp: Boolean = false

    /**
     *「刚刚被这次按下关掉的钉住面板」。
     *
     * 钉住面板靠「再划一次同一手势」关闭：按下的那一刻面板已经收掉了（会话被强制复位），
     * 如果随后手势又分类成同一个面板的打开动作，就会「关了又立刻开」，等于关不掉。
     * 记下它，让这一条一次性的手势被吞掉 —— 语义才是真正的开关。
     */
    internal var sessionClosedPinnedQuickLauncherId: String = ""

    /** 记录本次按下关掉的是哪个钉住面板（[forceReset] 之后调用，因此不受它清状态影响）。 */
    fun markClosedPinnedQuickLauncher(panelId: String) {
        sessionClosedPinnedQuickLauncherId = panelId
    }

    /** 本次手势要打开的面板是否就是刚被关掉的那个钉住面板（是 → 吞掉，不再重开）。 */
    fun consumeClosedPinnedQuickLauncher(panelId: String): Boolean {
        if (sessionClosedPinnedQuickLauncherId.isEmpty()) return false
        val match = sessionClosedPinnedQuickLauncherId == panelId
        sessionClosedPinnedQuickLauncherId = ""
        return match
    }

    private var lastRawX = 0f
    private var lastRawY = 0f
    private var lastLocalX = 0f
    private var lastLocalY = 0f

    private val thresholdTracker: GestureSessionThresholdTracker
    private val longPressCheckRunnable: Runnable
    private val hoverHapticCheckRunnable: Runnable

    init {
        thresholdTracker = GestureSessionThresholdTracker(
            pathRecognizer = pathRecognizer,
            callbacks = callbacks,
            cancelLongPressCheck = { callbacks.cancelDelayed(longPressCheckRunnable) },
            isTriggerConfigured = { trigger ->
                sessionSettings.actionFor(side, trigger, sessionActiveHandleId) !is GestureAction.None
            }
        )
        longPressCheckRunnable = Runnable {
            if (!active || sessionPanelMode != OverlayPanelMode.NONE) return@Runnable
            if (sessionContinuousPick.fingertipRingActive()) return@Runnable
            thresholdTracker.maybeHapticLongPress(lastRawX, lastRawY)
            if (!pathRecognizer.isLongPressArmed()) return@Runnable
            val classification = pathRecognizer.classifyPartial(lastRawX, lastRawY, classifyOptions()) ?: return@Runnable
            when (sessionSettings.resolvedTriggerMode(side, classification.trigger, sessionActiveHandleId)) {
                GestureTriggerMode.IMMEDIATE -> {
                    if (sessionMoveTimeActionFired) return@Runnable
                    if (pathRecognizer.isLongPressArmed() ||
                        pathRecognizer.hasMetThreshold(
                            classification.trigger,
                            lastRawX,
                            lastRawY,
                            classifyOptions()
                        )
                    ) {
                        dispatchMoveTimeGesture(
                            classification,
                            lastRawX,
                            lastRawY,
                            lastLocalX,
                            lastLocalY
                        )
                    }
                }
                GestureTriggerMode.CONTINUOUS -> {
                    trackContinuousGesture(
                        classification = classification,
                        rawX = lastRawX,
                        rawY = lastRawY,
                        localX = lastLocalX,
                        localY = lastLocalY
                    )
                }
                GestureTriggerMode.ON_RELEASE, GestureTriggerMode.DEFAULT -> Unit
            }
        }
        hoverHapticCheckRunnable = Runnable {
            if (!active || sessionPanelMode != OverlayPanelMode.NONE || sessionMoveTimeActionFired) return@Runnable
            pathRecognizer.onTouchMove(lastRawX, lastRawY)
            syncPathHaptics(lastRawX, lastRawY)
        }
    }

    fun applySettings(newSettings: AppSettings) {
        sessionSettings = newSettings
        indexSession.applySettings(newSettings)
        pathRecognizer.applyAngles(newSettings.gestureAngles)
        pathRecognizer.applyHoverSettings(
            durationMs = newSettings.swipeHoverDurationMs.toLong(),
        )
        applyActiveHandleDistances()
    }

    private fun applyActiveHandleDistances() {
        val handle = sessionSettings.triggerHandle(side, sessionActiveHandleId)
            ?: sessionSettings.primaryTriggerHandle(side)
        pathRecognizer.applyDistances(handle.shortSwipeDistanceDp, handle.longSwipeDistanceDp)
    }

    fun isActive(): Boolean = active

    fun panelMode(): OverlayPanelMode = sessionPanelMode

    fun isAdjustMode(): Boolean = sessionAdjustMode != null

    fun adjustModeOrNull(): ContinuousAdjustController.Mode? = sessionAdjustMode

    /** True after an IMMEDIATE action fired on this touch; blocks further in-gesture tracking. */
    fun isMoveTimeActionLocked(): Boolean = sessionMoveTimeActionFired

    /** Ends the initiating IMMEDIATE swipe while keeping the opened panel active. */
    fun releaseImmediateGestureLock(): Boolean {
        if (!sessionMoveTimeActionFired) return false
        sessionMoveTimeActionFired = false
        // leave-open 面板保留 sessionPanelMode，但手指手势必须结束，否则 scene/触摸态会悬空。
        active = false
        callbacks.cancelDelayed(longPressCheckRunnable)
        callbacks.onRequestInvalidate()
        return true
    }

    /**
     * Clears finger-tracking for leave-open panels (QL / TaskSwitcher / Shell) while keeping
     * [sessionPanelMode]. No-op while continuous-pick is still active.
     */
    fun finishLeaveOpenFingerTracking() {
        when (sessionPanelMode) {
            OverlayPanelMode.QUICK_LAUNCHER,
            OverlayPanelMode.TASK_SWITCHER,
            OverlayPanelMode.SHELL_COMMANDS,
            -> {
                if (sessionContinuousPick.quickLauncherActive() ||
                    sessionContinuousPick.taskSwitcherActive() ||
                    sessionContinuousPick.shellActive()
                ) {
                    EdgeDiag.log("leaveOpen", "跳过收尾：continuousPick 仍活跃 → panel=$sessionPanelMode")
                    return
                }
                if (!active && !sessionMoveTimeActionFired) {
                    EdgeDiag.log("leaveOpen", "跳过收尾：已非按住且未锁定 → panel=$sessionPanelMode")
                    return
                }
                active = false
                sessionMoveTimeActionFired = false
                callbacks.cancelDelayed(longPressCheckRunnable)
                callbacks.onRequestInvalidate()
                EdgeDiag.log("leaveOpen", "结束手指跟踪并保留 panel=$sessionPanelMode")
                callbacks.onLeaveOpenFingerTrackingFinished()
            }
            else -> Unit
        }
    }

    fun taskSwitcherContinuousPickActive(): Boolean = sessionContinuousPick.taskSwitcherActive()

    fun quickLauncherPanelId(): String = sessionQuickLauncherPanelId

    /** 当前快速启动器面板是否处于「钉住」模式（松手后由它自己常驻）。 */
    fun quickLauncherPinned(): Boolean = sessionQuickLauncherPinned

    /** 钉住是否只限钉住那一刻的前台应用。 */
    fun quickLauncherPinCurrentApp(): Boolean = sessionQuickLauncherPinCurrentApp

    /** 钉住面板仍在屏上、但手指手势已经结束（= 可以点按 / 不会再被会话收掉）。 */
    fun quickLauncherPinnedIdle(): Boolean =
        sessionQuickLauncherPinned &&
            sessionPanelMode == OverlayPanelMode.QUICK_LAUNCHER &&
            !active

    fun setQuickLauncherPanelId(panelId: String) {
        sessionQuickLauncherPanelId = panelId
    }

    fun quickLauncherContinuousPickActive(): Boolean = sessionContinuousPick.quickLauncherActive()

    fun clearQuickLauncherContinuousPick() {
        sessionContinuousPick.clearQuickLauncher()
    }

    fun shellCommandContinuousPickActive(): Boolean = sessionContinuousPick.shellActive()

    fun clearShellContinuousPick() {
        sessionContinuousPick.clearShell()
    }

    fun honeycombContinuousPickActive(): Boolean = sessionContinuousPick.honeycombActive()

    fun clearHoneycombContinuousPick() {
        sessionContinuousPick.clearHoneycomb()
    }

    fun ringLauncherContinuousPickActive(): Boolean = sessionContinuousPick.ringLauncherActive()

    fun clearRingLauncherContinuousPick() {
        sessionContinuousPick.clearRingLauncher()
    }

    fun clearFingertipRingContinuousPick() {
        sessionContinuousPick.clearFingertipRing()
    }

    fun quickWheelContinuousPickActive(): Boolean = sessionContinuousPick.quickWheelActive()

    fun clearQuickWheelContinuousPick() {
        sessionContinuousPick.clearQuickWheel()
    }

    fun isContinuousPickActive(): Boolean =
        sessionContinuousPick.honeycombActive() ||
            sessionContinuousPick.ringLauncherActive() ||
            sessionContinuousPick.appCarouselSwitcherActive() ||
            sessionContinuousPick.fingertipRingActive() ||
            sessionContinuousPick.quickWheelActive() ||
            sessionContinuousPick.shellActive() ||
            sessionContinuousPick.taskSwitcherActive() ||
            sessionContinuousPick.quickLauncherActive()

    fun adjustAnchorRawY(): Float = sessionAdjustLayoutAnchorRawY

    fun activeHandleId(): String = sessionActiveHandleId

    fun onTouchDown(rawX: Float, rawY: Float, localX: Float, localY: Float): Boolean {
        if (active) return false
        if (sessionSettings.triggerHandles(side).isEmpty()) return false
        if (!zoneLayout.containsTriggerAtScreen(rawX, rawY, localX, localY)) return false

        val matchedHandleId = zoneLayout.findTriggerHandleAtScreen(rawX, rawY)
            ?: zoneLayout.findTriggerHandleAt(localX, localY)
            ?: return false
        return beginTouchDown(matchedHandleId, rawX, rawY, localX, localY)
    }

    /** Capture strip already received the touch; skip global hit-test (rotation-safe). */
    fun onTouchDownForHandle(
        handleId: String,
        rawX: Float,
        rawY: Float,
        localX: Float,
        localY: Float
    ): Boolean {
        if (active) return false
        if (sessionSettings.triggerHandles(side).isEmpty()) return false
        if (sessionSettings.triggerHandle(side, handleId) == null) return false
        return beginTouchDown(handleId, rawX, rawY, localX, localY)
    }

    private fun beginTouchDown(
        handleId: String,
        rawX: Float,
        rawY: Float,
        localX: Float,
        localY: Float
    ): Boolean {
        sessionActiveHandleId = handleId
        applyActiveHandleDistances()
        active = true
        EdgeDiag.marker("触钮 DOWN side=$side handle=$handleId raw=($rawX,$rawY)")
        sessionIndexMode = false
        sessionAdjustMode = null
        sessionAdjustLayoutAnchorRawY = 0f
        sessionPanelMode = OverlayPanelMode.NONE
        sessionMoveTimeActionFired = false
        thresholdTracker.reset()
        sessionContinuousPick.reset()
        lastRawX = rawX
        lastRawY = rawY
        lastLocalX = localX
        lastLocalY = localY
        OverlayService.captureGestureForegroundPackage()
        val stripBounds = zoneLayout.screenTriggerStripBounds(sessionActiveHandleId)
        pathRecognizer.onTouchDown(rawX, rawY, stripBounds)
        pathRecognizer.applyCompoundGestureGate(classifyOptions())
        callbacks.scheduleDelayed(longPressCheckRunnable, SwipePathRecognizer.LONG_PRESS_MS)
        return true
    }

    fun onTouchMove(rawX: Float, rawY: Float, localX: Float, localY: Float) {
        if (!active) return

        lastRawX = rawX
        lastRawY = rawY
        lastLocalX = localX
        lastLocalY = localY

        val currentAdjustMode = sessionAdjustMode
        if (currentAdjustMode != null) {
            actionExecutor.updateContinuousAdjust(currentAdjustMode, rawY)
            callbacks.onRequestInvalidate()
            return
        }

        // 「页面滚动」按住拖动方式：手指移动只用来决定方向与节奏，滚动本身由控制器发。
        if (sessionPageScrollHoldActive) {
            actionExecutor.updatePageScrollHoldDrag(rawY)
            return
        }

        if (sessionContinuousPick.honeycombActive()) {
            callbacks.onHoneycombLauncherPointerMove(rawX, rawY)
            return
        }

        if (sessionContinuousPick.ringLauncherActive()) {
            callbacks.onRingLauncherPointerMove(rawX, rawY)
            return
        }

        if (sessionContinuousPick.appCarouselSwitcherActive()) {
            com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.onExternalMove(rawX, rawY)
            return
        }

        if (sessionContinuousPick.fingertipRingActive()) {
            callbacks.onFingertipRingPointerMove(rawX, rawY)
            return
        }

        if (sessionContinuousPick.quickWheelActive()) {
            com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow.onExternalMove(rawX, rawY)
            return
        }

        if (sessionPanelMode != OverlayPanelMode.NONE) {
            if (sessionIndexMode && indexSession.updateSelection(localX, localY)) {
                callbacks.onRequestInvalidate()
            }
            return
        }

        if (sessionMoveTimeActionFired) return

        pathRecognizer.onTouchMove(rawX, rawY)
        syncPathHaptics(rawX, rawY)

        val classification = pathRecognizer.classifyPartial(rawX, rawY, classifyOptions()) ?: return

        when (sessionSettings.resolvedTriggerMode(side, classification.trigger, sessionActiveHandleId)) {
            GestureTriggerMode.IMMEDIATE -> {
                if (!sessionMoveTimeActionFired &&
                    pathRecognizer.hasMetThreshold(
                        classification.trigger,
                        rawX,
                        rawY,
                        classifyOptions()
                    )
                ) {
                    dispatchMoveTimeGesture(classification, rawX, rawY, localX, localY)
                }
            }
            GestureTriggerMode.CONTINUOUS -> {
                trackContinuousGesture(classification, rawX, rawY, localX, localY)
            }
            GestureTriggerMode.ON_RELEASE, GestureTriggerMode.DEFAULT -> Unit
        }
    }

    fun onTouchUp(rawX: Float, rawY: Float, localX: Float, localY: Float) {
        if (!active) return

        when (sessionPanelMode) {
            OverlayPanelMode.INDEX -> {
                if (releaseImmediateGestureLock()) return
                confirmIndexSelection()
            }

            OverlayPanelMode.QUICK_LAUNCHER, OverlayPanelMode.TASK_SWITCHER,
            OverlayPanelMode.SHELL_COMMANDS -> finishLeaveOpenFingerTracking()

            OverlayPanelMode.NONE -> {
                if (sessionContinuousPick.honeycombActive()) {
                    sessionContinuousPick.clearHoneycomb()
                    callbacks.onHoneycombLauncherContinuousRelease(rawX, rawY)
                    endSession()
                    return
                }

                if (sessionContinuousPick.ringLauncherActive()) {
                    sessionContinuousPick.clearRingLauncher()
                    callbacks.onRingLauncherContinuousRelease(rawX, rawY)
                    // 圆环槽位可能绑的是「快速启动器 / 任务切换器 / 应用索引」这类边缘托管面板：
                    // 松手确认时它已经在 openDiscretePanel() 里开好了，这里再 endSession() 会把
                    // 刚开的面板和画布一起收掉（表现为「松手后什么都没弹出」）。与指尖圆环同一规则。
                    finishContinuousPickRelease()
                    return
                }

                if (sessionContinuousPick.appCarouselSwitcherActive()) {
                    sessionContinuousPick.clearAppCarouselSwitcher()
                    com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.confirmContinuousRelease(
                        rawX,
                        rawY
                    )
                    endSession()
                    return
                }

                if (sessionContinuousPick.quickWheelActive()) {
                    sessionContinuousPick.clearQuickWheel()
                    com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow.confirmContinuousRelease(
                        rawX,
                        rawY,
                    )
                    endSession()
                    return
                }

                if (sessionContinuousPick.fingertipRingActive()) {
                    sessionContinuousPick.clearFingertipRing()
                    callbacks.onFingertipRingContinuousRelease(rawX, rawY)
                    finishContinuousPickRelease()
                    return
                }

                if (sessionContinuousPick.shell) {
                    sessionContinuousPick.clearShell()
                    callbacks.onShellCommandPanelContinuousRelease()
                    endSession()
                    return
                }

                if (sessionPageScrollHoldActive) {
                    stopPageScrollHoldDrag()
                    endSession()
                    return
                }

                if (sessionAdjustMode != null) {
                    val mode = sessionAdjustMode!!
                    val anchorRawY = sessionAdjustLayoutAnchorRawY
                    val previewFraction = actionExecutor.adjustFraction()
                    actionExecutor.endContinuousAdjust()
                    val fraction = if (mode == ContinuousAdjustController.Mode.BRIGHTNESS) {
                        previewFraction
                    } else {
                        actionExecutor.readCurrentAdjustFraction(mode)
                    }
                    callbacks.onShowAdjustPanel(mode, fraction, anchorRawY)
                    endSession()
                    return
                }

                if (sessionMoveTimeActionFired) {
                    endSession()
                    return
                }

                pathRecognizer.onTouchMove(rawX, rawY)
                syncPathHaptics(rawX, rawY)

                val gestureStartRawY = pathRecognizer.gestureStartRawY()
                val classification = pathRecognizer.classifyOnUp(rawX, rawY, classifyOptions()) ?: run {
                    EdgeDiag.log("onUp", "分类失败(null) → endSession，本次手势不触发任何动作")
                    endSession()
                    return
                }

                val mode = sessionSettings.resolvedTriggerMode(side, classification.trigger, sessionActiveHandleId)
                if (mode == GestureTriggerMode.IMMEDIATE) {
                    if (!sessionMoveTimeActionFired) {
                        dispatchMoveTimeGesture(classification, rawX, rawY, localX, localY)
                    }
                    endSession()
                    return
                }

                val action = sessionSettings.actionFor(side, classification.trigger, sessionActiveHandleId)
                EdgeDiag.log(
                    "onUp",
                    "trigger=${classification.trigger} resolvedMode=$mode action=${action.type} " +
                        "inward=${classification.inwardDelta} vertical=${classification.verticalDelta} " +
                        "handle=$sessionActiveHandleId"
                )
                if (mode == GestureTriggerMode.CONTINUOUS && !sessionIndexMode &&
                    (sessionMoveTimeActionFired || action.supportsContinuousTracking(classification.trigger))
                ) {
                    endSession()
                    return
                }

                if (classification.trigger == GestureTriggerType.SHORT_SINGLE_TAP) {
                    val slopPx = pathRecognizer.tapDisqualifyMovementPx(classifyOptions())
                    val dispatch: (SwipeClassification) -> Unit = { resolved ->
                        handleClassifiedGesture(resolved, rawX, rawY, localX, localY, gestureStartRawY)
                    }
                    EdgeTriggerDoubleTapCoordinator.onSingleTapCandidate(
                        settings = sessionSettings,
                        side = side,
                        handleId = sessionActiveHandleId,
                        upX = rawX,
                        upY = rawY,
                        slopPx = slopPx,
                        onSingleTap = { dispatch(classification) },
                        onDoubleTap = {
                            dispatch(
                                SwipeClassification(
                                    trigger = GestureTriggerType.SHORT_DOUBLE_TAP,
                                    inwardDelta = classification.inwardDelta,
                                    verticalDelta = classification.verticalDelta,
                                )
                            )
                        },
                    )
                    finishLeaveOpenFingerTracking()
                    return
                }

                handleClassifiedGesture(classification, rawX, rawY, localX, localY, gestureStartRawY)
                // ON_RELEASE 打开 leave-open 面板后结束手指跟踪，避免 active 一直为 true。
                finishLeaveOpenFingerTracking()
            }
        }
    }

    internal fun openPanel(mode: OverlayPanelMode) {
        sessionPanelMode = mode
        sessionIndexMode = false
        EdgeDiag.log(
            "panel",
            "openPanel mode=$mode active=$active moveTimeFired=$sessionMoveTimeActionFired " +
                "panelId=$sessionQuickLauncherPanelId"
        )
        callbacks.onSessionStart(mode)
        callbacks.onRequestInvalidate()
    }

    internal fun openQuickLauncherPanel(action: GestureAction.QuickLauncher) {
        sessionQuickLauncherPanelId = QuickLauncherPanelDefaults.resolvePanelId(
            sessionSettings.quickLauncherPanels,
            action.panelId
        )
        sessionQuickLauncherPinned = action.pinned
        sessionQuickLauncherPinCurrentApp = action.pinned && action.pinCurrentApp
        EdgeDiag.log(
            "panel",
            "openQuickLauncherPanel actionPanelId='${action.panelId}' → resolved='$sessionQuickLauncherPanelId' " +
                "pinned=${action.pinned} pinCurrentApp=${action.pinCurrentApp} " +
                "panels=${sessionSettings.quickLauncherPanels.size}"
        )
        openPanel(OverlayPanelMode.QUICK_LAUNCHER)
    }

    fun openDiscretePanel(
        action: GestureAction,
        localX: Float,
        localY: Float,
        rawX: Float,
        rawY: Float
    ) {
        forceReset(notifySessionEnd = false)
        pathRecognizer.seedExternalAnchor(rawX, rawY)
        when (action) {
            is GestureAction.OpenIndex -> {
                active = true
                enterIndexMode(localX, localY)
            }
            is GestureAction.QuickLauncher -> {
                sessionContinuousPick.quickLauncher = false
                active = true
                openQuickLauncherPanel(action)
            }
            is GestureAction.TaskSwitcher -> {
                sessionContinuousPick.taskSwitcher = false
                active = true
                openPanel(OverlayPanelMode.TASK_SWITCHER)
            }
            is GestureAction.ShellCommandPanel -> {
                sessionContinuousPick.shell = false
                callbacks.onOpenShellCommandPanel(continuousPick = false)
            }
            is GestureAction.HoneycombLauncher -> {
                sessionContinuousPick.honeycomb = false
                callbacks.onShowHoneycombLauncher(continuousPick = false, rawX, rawY)
            }
            GestureAction.RingLauncher -> {
                sessionContinuousPick.ringLauncher = false
                callbacks.onShowRingLauncher(continuousPick = false, rawX, rawY)
            }
            GestureAction.AppCarouselSwitcher -> {
                sessionCallbacks.hapticConfirmLaunch()
                val x = rawX
                val y = rawY
                com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.show(
                    actionExecutor.context,
                    sessionSettings,
                    x,
                    y,
                    externalTracking = false
                )
            }
            is GestureAction.QuickWheel -> {
                sessionContinuousPick.quickWheel = false
                sessionCallbacks.hapticConfirmLaunch()
                com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow.show(
                    context = actionExecutor.context,
                    settings = sessionSettings,
                    wheelId = action.wheelId,
                    anchorRawX = rawX,
                    anchorRawY = rawY,
                    actionExecutor = actionExecutor.asGestureActionRunner(),
                    externalTracking = false,
                    shape = action.shape,
                    manualSectorMask = action.manualSectorMask,
                    anchorMode = action.anchorMode
                )
            }
            GestureAction.HolographicLauncher -> {
                callbacks.hapticConfirmLaunch()
                actionExecutor.execute(
                    action,
                    sessionSettings,
                    anchorRawX = rawX,
                    anchorRawY = rawY
                )
            }
            GestureAction.AdjustVolume -> {
                active = true
                val fraction = actionExecutor.readCurrentAdjustFraction(ContinuousAdjustController.Mode.VOLUME)
                callbacks.onShowAdjustPanel(ContinuousAdjustController.Mode.VOLUME, fraction, rawY)
            }
            GestureAction.AdjustBrightness -> {
                active = true
                val fraction = actionExecutor.readCurrentAdjustFraction(ContinuousAdjustController.Mode.BRIGHTNESS)
                callbacks.onShowAdjustPanel(ContinuousAdjustController.Mode.BRIGHTNESS, fraction, rawY)
            }
            else -> Unit
        }
    }

    fun endSession() {
        if (!active && sessionPanelMode == OverlayPanelMode.NONE && sessionAdjustMode == null) return
        EdgeDiag.log("reset", "endSession panel=$sessionPanelMode active=$active")
        forceReset(notifySessionEnd = true)
    }

    /**
     * 「持续触发」松手后的统一收尾（圆环 / 指尖圆环共用）。
     *
     * 松手确认可能已经在 [openDiscretePanel] 里开出了新的 leave-open 面板：这种情况下只能结束
     * 手指跟踪、**不能** [endSession]，否则刚开的面板会随 `forceReset` 一起消失。
     */
    private fun finishContinuousPickRelease() {
        when (continuousReleaseHandoff(sessionPanelMode)) {
            ContinuousReleaseHandoff.END_SESSION -> endSession()
            ContinuousReleaseHandoff.KEEP_FINGER_TRACKING -> finishLeaveOpenFingerTracking()
            ContinuousReleaseHandoff.KEEP_INDEX -> Unit
        }
    }

    fun forceReset(notifySessionEnd: Boolean = true) {
        val shouldNotify = notifySessionEnd &&
            (active || sessionPanelMode != OverlayPanelMode.NONE || sessionAdjustMode != null)
        if (shouldNotify || sessionPanelMode != OverlayPanelMode.NONE || active) {
            EdgeDiag.logStack(
                "reset",
                "forceReset notify=$notifySessionEnd before: panel=$sessionPanelMode active=$active " +
                    "moveTimeFired=$sessionMoveTimeActionFired adjust=$sessionAdjustMode"
            )
        }

        active = false
        sessionIndexMode = false
        sessionAdjustMode = null
        sessionAdjustLayoutAnchorRawY = 0f
        sessionPanelMode = OverlayPanelMode.NONE
        sessionMoveTimeActionFired = false
        sessionContinuousPick.reset()
        thresholdTracker.reset()
        sessionActiveHandleId = TriggerHandle.DEFAULT_ID
        sessionQuickLauncherPanelId = ""
        sessionQuickLauncherPinned = false
        sessionQuickLauncherPinCurrentApp = false
        sessionClosedPinnedQuickLauncherId = ""

        callbacks.cancelDelayed(longPressCheckRunnable)
        callbacks.cancelDelayed(hoverHapticCheckRunnable)
        actionExecutor.endContinuousAdjust()
        stopPageScrollHoldDrag()
        pathRecognizer.reset()
        indexSession.endSession()

        if (shouldNotify) {
            callbacks.onSessionEnd()
        }
        callbacks.onRequestInvalidate()
    }

    fun reset() {
        forceReset(notifySessionEnd = true)
    }

    internal fun classifyOptions(): SwipePathRecognizer.ClassifyOptions {
        val preferLenientTap = sessionSettings.actionFor(
            side,
            GestureTriggerType.SHORT_SINGLE_TAP,
            sessionActiveHandleId
        ) is GestureAction.ClickPassthrough
        val base = if (preferLenientTap) {
            SwipePathRecognizer.ClassifyOptions.LENIENT_SINGLE_TAP
        } else {
            SwipePathRecognizer.ClassifyOptions.DEFAULT
        }
        return base.copy(
            isTriggerConfigured = { trigger ->
                sessionSettings.actionFor(side, trigger, sessionActiveHandleId) !is GestureAction.None
            }
        )
    }

    private fun syncPathHaptics(rawX: Float, rawY: Float) {
        if (!pathRecognizer.longPressEligible()) {
            callbacks.cancelDelayed(longPressCheckRunnable)
        }
        thresholdTracker.maybeHapticLongPress(rawX, rawY)
        thresholdTracker.trackDistanceHaptics(rawX, rawY)
        scheduleHoverHapticIfNeeded()
        maybeDispatchHoverGesture()
    }

    /**
     * 短滑悬停计时结束后手指可能已静止，不会再收到 MOVE；
     * 在悬停满足时立即按触发模式派发持续/即时动作，避免等到松手才弹出圆环等 overlay。
     */
    private fun maybeDispatchHoverGesture() {
        if (!active || sessionPanelMode != OverlayPanelMode.NONE || sessionMoveTimeActionFired) return
        if (sessionAdjustMode != null) return
        if (sessionContinuousPick.fingertipRingActive() ||
            sessionContinuousPick.honeycombActive() ||
            sessionContinuousPick.ringLauncherActive() ||
            sessionContinuousPick.appCarouselSwitcherActive() ||
            sessionContinuousPick.quickWheelActive()
        ) {
            return
        }
        if (!pathRecognizer.isHoverSatisfied()) return

        val classification = pathRecognizer.classifyPartial(lastRawX, lastRawY, classifyOptions()) ?: return
        if (!classification.trigger.isHoverSwipe) return

        when (sessionSettings.resolvedTriggerMode(side, classification.trigger, sessionActiveHandleId)) {
            GestureTriggerMode.CONTINUOUS -> trackContinuousGesture(
                classification = classification,
                rawX = lastRawX,
                rawY = lastRawY,
                localX = lastLocalX,
                localY = lastLocalY
            )
            GestureTriggerMode.IMMEDIATE -> {
                if (pathRecognizer.hasMetThreshold(
                        classification.trigger,
                        lastRawX,
                        lastRawY,
                        classifyOptions()
                    )
                ) {
                    dispatchMoveTimeGesture(
                        classification,
                        lastRawX,
                        lastRawY,
                        lastLocalX,
                        lastLocalY
                    )
                }
            }
            GestureTriggerMode.ON_RELEASE, GestureTriggerMode.DEFAULT -> Unit
        }
    }

    private fun scheduleHoverHapticIfNeeded() {
        callbacks.cancelDelayed(hoverHapticCheckRunnable)
        if (pathRecognizer.isHoverSatisfied()) return
        val hoverTrigger = pathRecognizer.activeHoverTrigger() ?: return
        if (sessionSettings.actionFor(side, hoverTrigger, sessionActiveHandleId) is GestureAction.None) return
        val remaining = pathRecognizer.hoverHoldRemainingMs() ?: return
        callbacks.scheduleDelayed(hoverHapticCheckRunnable, remaining)
    }

    internal fun enterAdjustMode(mode: ContinuousAdjustController.Mode, rawY: Float) {
        if (sessionAdjustMode == mode) {
            actionExecutor.updateContinuousAdjust(mode, rawY)
            return
        }
        if (actionExecutor.beginContinuousAdjust(mode, rawY)) {
            val enteringAdjust = sessionAdjustMode == null
            sessionAdjustMode = mode
            if (enteringAdjust) {
                sessionAdjustLayoutAnchorRawY = rawY
                callbacks.onSessionStart(OverlayPanelMode.NONE)
            }
            actionExecutor.updateContinuousAdjust(mode, rawY)
            callbacks.onRequestInvalidate()
        }
    }

    /**
     * 进入「页面滚动」的按住拖动方式：本次手势余下的移动都交给控制器决定滚动方向与节奏。
     */
    internal fun enterPageScrollHoldMode(rawY: Float) {
        if (!sessionPageScrollHoldActive) {
            sessionPageScrollHoldActive = true
            callbacks.onSessionStart(OverlayPanelMode.NONE)
        }
        actionExecutor.beginPageScrollHoldDrag(rawY)
    }

    internal fun stopPageScrollHoldDrag() {
        if (!sessionPageScrollHoldActive) return
        sessionPageScrollHoldActive = false
        actionExecutor.endPageScrollHoldDrag()
    }

    internal fun enterIndexMode(localX: Float, localY: Float) {
        sessionIndexMode = true
        sessionPanelMode = OverlayPanelMode.INDEX
        // 「应用索引」有三条开启路径（持续触发的移动分发、松手分发、外部入口），
        // 通知挂在这里才不会漏：引导靠它判断"用户自己滑出了索引"。
        GesturePerformedEvents.notify(GestureAction.OpenIndex)
        callbacks.onSessionStart(OverlayPanelMode.INDEX)
        indexSession.updateSelection(localX, localY)
        callbacks.onRequestInvalidate()
    }

    /**
     * 索引面板的「确认高亮项」：启动当前高亮应用（含分身 / 长按小窗）并收掉面板。
     *
     * 索引面板有两种存活形态，必须共用这一份实现：
     * - 手指仍按住（松手触发刚打开、持续触发拖动中）→ 走 [onTouchUp] 的 INDEX 分支；
     * - 手指已抬起、面板以 leave-open 形态留在屏上（即时触发）→ 此时 [active] 已被
     *   [releaseImmediateGestureLock] 清成 false，不能再让 `onTouchUp` 的 active 守卫把它挡掉，
     *   由 `IndexPanelRenderer` 的抬指路径直接调用本方法。
     */
    fun confirmIndexSelection() {
        val app = indexSession.highlightedApp
        val longPressArmed = indexSession.longPressArmed
        endSession()
        app?.let {
            callbacks.hapticConfirmLaunch()
            if (it.isClone) {
                // 分身实例：同包名但要在别的 user 下启动。
                actionExecutor.launchAppInUser(it.packageName, it.cloneUserId, sessionSettings)
            } else {
                actionExecutor.execute(
                    GestureAction.LaunchApp(it.packageName),
                    sessionSettings,
                    longPressArmed
                )
            }
        }
    }

    /** @return true if the overlay session should end after this quick-launcher tap. */
    fun performQuickLauncherAction(
        action: GestureAction,
        localX: Float,
        localY: Float,
        rawY: Float,
        confirmHaptic: Boolean = true
    ): Boolean = dispatchQuickLauncherAction(action, localX, localY, rawY, confirmHaptic)
}
