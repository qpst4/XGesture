package com.slideindex.app.overlay

import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.overlay.ringlauncher.RingLauncherOverlayWindow
import com.slideindex.app.util.HapticHelper

/**
 * Bridges [GestureSession] to [EdgeGestureSessionCoordinator] without a circular construction dependency.
 */
internal class GestureSessionCallbackBridge : GestureSession.Callbacks {
    lateinit var delegate: GestureSession.Callbacks

    override fun onSessionStart(mode: OverlayPanelMode) = delegate.onSessionStart(mode)
    override fun onLeaveOpenFingerTrackingFinished() = delegate.onLeaveOpenFingerTrackingFinished()
    override fun onSessionEnd() = delegate.onSessionEnd()
    override fun onOpenShellCommandPanel(continuousPick: Boolean) =
        delegate.onOpenShellCommandPanel(continuousPick)
    override fun onShellCommandPanelContinuousRelease() =
        delegate.onShellCommandPanelContinuousRelease()
    override fun onShowHoneycombLauncher(
        continuousPick: Boolean,
        rawX: Float,
        rawY: Float,
        forceBrowseMode: Boolean
    ): Boolean = delegate.onShowHoneycombLauncher(continuousPick, rawX, rawY, forceBrowseMode)
    override fun onHoneycombLauncherPointerMove(rawX: Float, rawY: Float) =
        delegate.onHoneycombLauncherPointerMove(rawX, rawY)
    override fun onHoneycombLauncherContinuousRelease(rawX: Float, rawY: Float) =
        delegate.onHoneycombLauncherContinuousRelease(rawX, rawY)
    override fun onShowRingLauncher(
        continuousPick: Boolean,
        rawX: Float,
        rawY: Float
    ): Boolean = delegate.onShowRingLauncher(continuousPick, rawX, rawY)
    override fun onRingLauncherPointerMove(rawX: Float, rawY: Float) =
        delegate.onRingLauncherPointerMove(rawX, rawY)
    override fun onRingLauncherContinuousRelease(rawX: Float, rawY: Float) =
        delegate.onRingLauncherContinuousRelease(rawX, rawY)
    override fun onShowFingertipRing(
        continuousPick: Boolean,
        rawX: Float,
        rawY: Float
    ): Boolean = delegate.onShowFingertipRing(continuousPick, rawX, rawY)
    override fun onFingertipRingPointerMove(rawX: Float, rawY: Float) =
        delegate.onFingertipRingPointerMove(rawX, rawY)
    override fun onFingertipRingContinuousRelease(rawX: Float, rawY: Float) =
        delegate.onFingertipRingContinuousRelease(rawX, rawY)
    override fun onShowAdjustPanel(
        mode: com.slideindex.app.util.ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
        deferWindowLayout: Boolean
    ) = delegate.onShowAdjustPanel(mode, fraction, anchorRawY, deferWindowLayout)
    override fun onRequestInvalidate() = delegate.onRequestInvalidate()
    override fun hapticGestureStart() = delegate.hapticGestureStart()
    override fun hapticLongThreshold() = delegate.hapticLongThreshold()
    override fun hapticConfirmLaunch() = delegate.hapticConfirmLaunch()
    override fun scheduleDelayed(runnable: Runnable, delayMs: Long) =
        delegate.scheduleDelayed(runnable, delayMs)
    override fun cancelDelayed(runnable: Runnable) = delegate.cancelDelayed(runnable)
}

internal class EdgeGestureSessionCoordinator(
    private val view: android.view.View,
    private val gestureSession: GestureSession,
    private val panelGridSession: PanelGridSession,
    private val panelEnterAnimator: OverlayPanelEnterAnimator,
    private val adjustPanelController: AdjustPanelOverlayController,
    private val taskSwitcherController: TaskSwitcherOverlayController,
    private val quickLauncherController: QuickLauncherOverlayController,
    private val shellCoordinator: ShellPanelOverlayController,
    private val gestureAnimationCoordinator: GestureAnimationCoordinator,
    private val layoutCoordinator: EdgeGestureLayoutCoordinator,
    private val actionExecutor: ActionExecutor,
    private val settingsProvider: () -> AppSettings,
    private val runAfterLayout: (() -> Unit) -> Unit,
    private val onSessionStartCallback: () -> Unit,
    private val onAdjustPanelLayoutCallback: (Float) -> Unit,
    private val notifyPresentationTouchRequirementChanged: () -> Unit,
    private val requestInvalidate: () -> Unit,
    private val indexPanelContentRect: () -> android.graphics.RectF,
    private val onIndexSessionStart: () -> Unit = {},
    private val notifyAccessibilityStructure: () -> Unit = {}
) : GestureSession.Callbacks {
    private var lastAdjustInvalidateMs = 0L

    @Suppress("DEPRECATION")
    private fun invalidateIndexPanel() {
        val rect = indexPanelContentRect()
        if (rect.isEmpty) {
            requestInvalidate()
            return
        }
        val pad = view.resources.displayMetrics.density * 4f
        view.invalidate(
            (rect.left - pad).toInt().coerceAtLeast(0),
            (rect.top - pad).toInt().coerceAtLeast(0),
            (rect.right + pad).toInt().coerceAtMost(view.width.coerceAtLeast(1)),
            (rect.bottom + pad).toInt().coerceAtMost(view.height.coerceAtLeast(1))
        )
    }

    override fun onSessionStart(mode: OverlayPanelMode) {
        EdgeDiag.log(
            "session",
            "onSessionStart mode=$mode（panelMode 即将=${mode}，enter 动画 reset 到 progress=0）"
        )
        layoutCoordinator.syncZoneLayout()
        panelEnterAnimator.cancel()
        when (mode) {
            OverlayPanelMode.TASK_SWITCHER -> {
                panelEnterAnimator.resetToHidden()
                taskSwitcherController.onSessionStart()
            }
            OverlayPanelMode.INDEX, OverlayPanelMode.QUICK_LAUNCHER,
            OverlayPanelMode.SHELL_COMMANDS -> {
                panelEnterAnimator.resetToHidden()
                if (mode == OverlayPanelMode.SHELL_COMMANDS) {
                    shellCoordinator.onSessionStart()
                }
                if (mode == OverlayPanelMode.QUICK_LAUNCHER) {
                    quickLauncherController.onSessionStart()
                }
                if (mode == OverlayPanelMode.INDEX) {
                    onIndexSessionStart()
                }
            }
            OverlayPanelMode.NONE -> {
                panelEnterAnimator.resetToComplete()
                if (gestureSession.isAdjustMode()) {
                    adjustPanelController.onSessionStartAdjustMode()
                }
            }
        }
        panelGridSession.reset()
        onSessionStartCallback()
        notifyPresentationTouchRequirementChanged()
        notifyAccessibilityStructure()
        if (mode != OverlayPanelMode.NONE || gestureSession.isAdjustMode()) {
            gestureAnimationCoordinator.onSessionStartDismissIfNeeded()
        }
        if (mode != OverlayPanelMode.NONE) {
            runAfterLayout {
                if (gestureSession.panelMode() != mode) {
                    EdgeDiag.log(
                        "enter",
                        "闸门拦截：期望 mode=$mode 但当前 panelMode=${gestureSession.panelMode()} " +
                            "→ 跳过 onLayoutReady/startEnter（面板会停在 progress=0，表现为完全不可见）"
                    )
                    return@runAfterLayout
                }
                layoutCoordinator.syncZoneLayout()
                if (mode == OverlayPanelMode.TASK_SWITCHER) {
                    taskSwitcherController.onLayoutReady()
                }
                if (mode == OverlayPanelMode.QUICK_LAUNCHER) {
                    quickLauncherController.onLayoutReady()
                }
                EdgeDiag.log(
                    "enter",
                    "startEnter mode=$mode active=${gestureSession.isActive()} " +
                        "viewSize=${view.width}x${view.height} attached=${view.isAttachedToWindow}"
                )
                panelEnterAnimator.startEnter(
                    panelMode = mode,
                    onShellEnterEnded = { shellCoordinator.onPanelEnterAnimationEnded() },
                    onQuickLauncherEnterEnded = {
                        EdgeDiag.log(
                            "enter",
                            "快速启动器进场动画结束 progress=${panelEnterAnimator.progress} " +
                                "panelMode=${gestureSession.panelMode()}"
                        )
                        quickLauncherController.onPanelEnterAnimationEnded()
                    }
                )
            }
        }
    }

    override fun onSessionEnd() {
        EdgeDiag.logStack("session", "onSessionEnd（面板窗口会被解除全屏直触/摘除）")
        panelEnterAnimator.cancel()
        adjustPanelController.onSessionEnd()
        panelEnterAnimator.resetToComplete()
        layoutCoordinator.syncZoneLayout()
        panelGridSession.reset()
        taskSwitcherController.onSessionEnd()
        quickLauncherController.onSessionEnd()
        shellCoordinator.onSessionEnd()
        HoneycombAppPickerOverlayWindow.onGestureSessionEnd()
        RingLauncherOverlayWindow.onGestureSessionEnd()
        com.slideindex.app.overlay.fingertip.FingertipRingOverlayWindow.onGestureSessionEnd()
        com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.onGestureSessionEnd()
        com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow.onGestureSessionEnd()
        layoutCoordinator.notifyOverlayLayoutIfNeeded()
        notifyPresentationTouchRequirementChanged()
        notifyAccessibilityStructure()
    }

    override fun onLeaveOpenFingerTrackingFinished() {
        EdgeDiag.log("leaveOpen", "onLeaveOpenFingerTrackingFinished → 重算触摸/窗口需求")
        notifyPresentationTouchRequirementChanged()
    }

    override fun onOpenShellCommandPanel(continuousPick: Boolean) {
        shellCoordinator.onOpenShellCommandPanel(continuousPick)
    }

    override fun onShellCommandPanelContinuousRelease() {
        shellCoordinator.onShellCommandPanelContinuousRelease()
    }

    override fun onShowHoneycombLauncher(
        continuousPick: Boolean,
        rawX: Float,
        rawY: Float,
        forceBrowseMode: Boolean
    ): Boolean {
        val settings = settingsProvider()
        return HoneycombAppPickerOverlayWindow.show(
            context = view.context,
            settings = settings,
            anchorRawX = rawX,
            anchorRawY = rawY,
            externalTracking = continuousPick,
            forceBrowseMode = forceBrowseMode,
            onLaunch = { item, longPressArmed ->
                actionExecutor.launchQuickItem(
                    item,
                    settings,
                    longPressArmed = longPressArmed,
                    panelSide = gestureSession.sessionSide,
                )
            }
        )
    }

    override fun onHoneycombLauncherPointerMove(rawX: Float, rawY: Float) {
        HoneycombAppPickerOverlayWindow.updatePointer(rawX, rawY)
    }

    override fun onHoneycombLauncherContinuousRelease(rawX: Float, rawY: Float) {
        HoneycombAppPickerOverlayWindow.confirmSelection(
            rawX = rawX,
            rawY = rawY,
            actionExecutor = actionExecutor,
            settings = settingsProvider()
        )
        gestureSession.clearHoneycombContinuousPick()
    }

    override fun onShowRingLauncher(
        continuousPick: Boolean,
        rawX: Float,
        rawY: Float
    ): Boolean {
        if (continuousPick) {
            gestureAnimationCoordinator.hide()
        }
        val settings = settingsProvider()
        return RingLauncherOverlayWindow.show(
            context = view.context,
            settings = settings,
            anchorRawX = rawX,
            anchorRawY = rawY,
            externalTracking = continuousPick,
            onLaunch = { item, longPressArmed ->
                actionExecutor.launchQuickItem(
                    item,
                    settings,
                    longPressArmed = longPressArmed,
                    // 圆环槽位绑「快速启动器」这类边缘托管面板时，面板要贴圆环这一侧、按触发高度落位；
                    // 不传锚点会退化成屏幕正中（见 ActionExecutorOverlayPanels.showEdgeHostedPanel）。
                    anchorRawY = rawY,
                    panelSide = gestureSession.sessionSide,
                )
            },
            edgePanelSide = gestureSession.sessionSide
        )
    }

    override fun onRingLauncherPointerMove(rawX: Float, rawY: Float) {
        RingLauncherOverlayWindow.updatePointer(rawX, rawY)
    }

    override fun onRingLauncherContinuousRelease(rawX: Float, rawY: Float) {
        RingLauncherOverlayWindow.confirmSelection(
            rawX = rawX,
            rawY = rawY,
            actionExecutor = actionExecutor,
            settings = settingsProvider()
        )
        gestureSession.clearRingLauncherContinuousPick()
    }

    override fun onShowFingertipRing(
        continuousPick: Boolean,
        rawX: Float,
        rawY: Float
    ): Boolean {
        gestureAnimationCoordinator.hide()
        val settings = settingsProvider()
        val shown = com.slideindex.app.overlay.fingertip.FingertipRingOverlayWindow.show(
            context = view.context,
            settings = settings,
            anchorRawX = rawX,
            anchorRawY = rawY,
            externalTracking = continuousPick,
            executeGesture = if (continuousPick) {
                null
            } else {
                com.slideindex.app.overlay.panels.GestureActionRunner { action, s, armed, ax, ay, side ->
                    actionExecutor.execute(
                        action = action,
                        settings = s,
                        longPressArmed = armed,
                        anchorRawX = ax,
                        anchorRawY = ay,
                    )
                }
            }
        )
        if (shown && continuousPick) {
            com.slideindex.app.overlay.fingertip.FingertipRingOverlayWindow.updatePointer(rawX, rawY)
        }
        return shown
    }

    override fun onFingertipRingPointerMove(rawX: Float, rawY: Float) {
        com.slideindex.app.overlay.fingertip.FingertipRingOverlayWindow.updatePointer(rawX, rawY)
    }

    override fun onFingertipRingContinuousRelease(rawX: Float, rawY: Float) {
        com.slideindex.app.overlay.fingertip.FingertipRingOverlayWindow.confirmSelection(
            rawX = rawX,
            rawY = rawY,
            settings = settingsProvider(),
            executeGesture = com.slideindex.app.overlay.panels.GestureActionRunner { action, s, armed, ax, ay, side ->
                actionExecutor.execute(
                    action = action,
                    settings = s,
                    longPressArmed = armed,
                    anchorRawX = ax,
                    anchorRawY = ay,
                )
            },
        )
        gestureSession.clearFingertipRingContinuousPick()
    }

    override fun onShowAdjustPanel(
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
        deferWindowLayout: Boolean
    ) {
        onAdjustPanelLayoutCallback(anchorRawY)
        adjustPanelController.showAdjustPanel(mode.toPanelMode(), fraction, anchorRawY)
    }

    override fun onRequestInvalidate() {
        if (gestureSession.panelMode() == OverlayPanelMode.INDEX) {
            invalidateIndexPanel()
        } else {
            requestInvalidate()
        }
    }

    override fun hapticGestureStart() {
        logHapticPlay("gestureStart")
        HapticHelper.gestureStart(view, settingsProvider())
    }

    override fun hapticLongThreshold() {
        logHapticPlay("longThreshold")
        HapticHelper.longThreshold(view, settingsProvider())
    }

    /** 临时诊断日志：看清"哪条会话实例播了一次震动"（仅 debug 生效）。 */
    private fun logHapticPlay(kind: String) {
        if (!com.slideindex.app.BuildConfig.DEBUG) return
        android.util.Log.i(
            "GestureHaptic",
            "play=$kind session=${System.identityHashCode(this)} t=${System.currentTimeMillis()}",
        )
    }

    override fun hapticConfirmLaunch() = HapticHelper.confirmLaunch(view, settingsProvider())

    override fun scheduleDelayed(runnable: Runnable, delayMs: Long) {
        view.postDelayed(runnable, delayMs)
    }

    override fun cancelDelayed(runnable: Runnable) {
        view.removeCallbacks(runnable)
    }

    fun hapticLetterTick() = HapticHelper.letterTick(view, settingsProvider())

    fun hapticAppTick() = HapticHelper.appTick(view, settingsProvider())

    fun requestInvalidateThrottled() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastAdjustInvalidateMs < 16L) return
        lastAdjustInvalidateMs = now
        requestInvalidate()
    }
}
