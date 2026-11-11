package com.slideindex.app.gesture
import com.slideindex.app.overlay.asGestureActionRunner

import android.os.Handler
import android.os.Looper
import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.EdgeContinuedOverlayLaunchCoordinator
import com.slideindex.app.overlay.toFloatBallPickDockSide
import com.slideindex.app.overlay.OverlayPanelMode
import com.slideindex.app.settings.actionFor
import com.slideindex.app.settings.resolvedTriggerMode
import com.slideindex.app.util.ContinuousAdjustController

internal fun GestureSession.dispatchMoveTimeGesture(
    classification: SwipeClassification,
    rawX: Float,
    rawY: Float,
    localX: Float,
    localY: Float
) {
    val action = sessionSettings.actionFor(sessionSide, classification.trigger, sessionActiveHandleId)

    if (action == GestureAction.None || action is GestureAction.ClickPassthrough) return

    sessionMoveTimeActionFired = true
    sessionCallbacks.cancelDelayed(sessionLongPressCheckRunnable)
    sessionPathRecognizer.disqualifyLongPress()

    handleClassifiedGesture(
        classification = classification,
        rawX = rawX,
        rawY = rawY,
        localX = localX,
        localY = localY,
        gestureStartRawY = sessionPathRecognizer.gestureStartRawY()
    )
}

internal fun GestureSession.trackContinuousGesture(
    classification: SwipeClassification,
    rawX: Float,
    rawY: Float,
    localX: Float,
    localY: Float
) {
    val action = sessionSettings.actionFor(sessionSide, classification.trigger, sessionActiveHandleId)

    // 「页面滚动」在**持续触发**下 = 按住并拖动（手指按着不放，由移动量决定滚动方向与节奏）。
    if (action is GestureAction.PageScroll) {
        if (!sessionPageScrollHoldActive) enterPageScrollHoldMode(rawY)
        return
    }

    if (!action.supportsContinuousTracking(classification.trigger)) return

    when (action) {
        is GestureAction.OpenIndex -> {
            if (!sessionIndexMode) {
                enterIndexMode(localX, localY)
            } else if (sessionIndexSession.updateSelection(localX, localY)) {
                sessionCallbacks.onRequestInvalidate()
            }
        }

        GestureAction.TaskSwitcher -> {
            if (sessionPanelMode != OverlayPanelMode.TASK_SWITCHER) {
                sessionContinuousPick.taskSwitcher = true
                openPanel(OverlayPanelMode.TASK_SWITCHER)
            }
        }

        is GestureAction.QuickLauncher -> {
            val resolvedPanelId = QuickLauncherPanelDefaults.resolvePanelId(
                sessionSettings.quickLauncherPanels,
                action.panelId
            )
            // 这次手势刚刚把同一个钉住面板关掉了 → 它就是那次关闭手势，不要再把它开回来。
            if (consumeClosedPinnedQuickLauncher(resolvedPanelId)) {
                EdgeDiag.log("dispatch", "trackContinuousGesture → 钉住面板已被本次手势关闭，吞掉重开")
            } else if (sessionPanelMode != OverlayPanelMode.QUICK_LAUNCHER ||
                sessionQuickLauncherPanelId != resolvedPanelId
            ) {
                sessionContinuousPick.quickLauncher = true
                openQuickLauncherPanel(action)
            }
        }

        GestureAction.ShellCommandPanel -> {
            if (!sessionContinuousPick.shell) {
                sessionContinuousPick.shell = true
                sessionCallbacks.onOpenShellCommandPanel(continuousPick = true)
            }
        }

        GestureAction.HoneycombLauncher -> {
            if (!sessionContinuousPick.honeycomb) {
                val shown = sessionCallbacks.onShowHoneycombLauncher(continuousPick = true, rawX, rawY)
                if (shown) {
                    sessionContinuousPick.honeycomb = true
                    sessionCallbacks.hapticConfirmLaunch()
                }
            } else {
                sessionCallbacks.onHoneycombLauncherPointerMove(rawX, rawY)
            }
        }

        GestureAction.RingLauncher -> {
            if (!sessionContinuousPick.ringLauncher) {
                val shown = sessionCallbacks.onShowRingLauncher(continuousPick = true, rawX, rawY)
                if (shown) {
                    sessionContinuousPick.ringLauncher = true
                    sessionCallbacks.hapticConfirmLaunch()
                }
            } else {
                sessionCallbacks.onRingLauncherPointerMove(rawX, rawY)
            }
        }

        GestureAction.AppCarouselSwitcher -> {
            if (!sessionContinuousPick.appCarouselSwitcher) {
                val shown = com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.show(
                    sessionActionExecutor.context,
                    sessionSettings,
                    rawX,
                    rawY,
                    externalTracking = true
                )
                if (shown) {
                    sessionContinuousPick.appCarouselSwitcher = true
                    sessionCallbacks.hapticConfirmLaunch()
                }
            } else {
                com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.onExternalMove(rawX, rawY)
            }
        }

        is GestureAction.QuickWheel -> {
            if (!sessionContinuousPick.quickWheel) {
                val shown = com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow.show(
                    context = sessionActionExecutor.context,
                    settings = sessionSettings,
                    wheelId = action.wheelId,
                    anchorRawX = rawX,
                    anchorRawY = rawY,
                    actionExecutor = sessionActionExecutor.asGestureActionRunner(),
                    externalTracking = true,
                    shape = action.shape,
                    manualSectorMask = action.manualSectorMask,
                    anchorMode = action.anchorMode
                )
                if (shown) {
                    sessionContinuousPick.quickWheel = true
                    sessionCallbacks.hapticConfirmLaunch()
                }
            } else {
                com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow.onExternalMove(rawX, rawY)
            }
        }

        GestureAction.FingertipRing -> {
            if (!sessionContinuousPick.fingertipRing) {
                val shown = sessionCallbacks.onShowFingertipRing(
                    continuousPick = true,
                    rawX = rawX,
                    rawY = rawY
                )
                if (shown) {
                    sessionContinuousPick.fingertipRing = true
                    sessionCallbacks.hapticConfirmLaunch()
                }
            } else {
                sessionCallbacks.onFingertipRingPointerMove(rawX, rawY)
            }
        }

        GestureAction.AdjustVolume -> enterAdjustMode(ContinuousAdjustController.Mode.VOLUME, rawY)

        GestureAction.AdjustBrightness -> enterAdjustMode(ContinuousAdjustController.Mode.BRIGHTNESS, rawY)

        GestureAction.FloatingPointer -> {
            if (!sessionMoveTimeActionFired &&
                sessionPathRecognizer.hasMetThreshold(
                    classification.trigger,
                    rawX,
                    rawY,
                    classifyOptions()
                )
            ) {
                sessionMoveTimeActionFired = true
                sessionCallbacks.cancelDelayed(sessionLongPressCheckRunnable)
                sessionPathRecognizer.disqualifyLongPress()
                sessionCallbacks.hapticConfirmLaunch()
                val hostContext = OverlayDependencyAccess.overlayHostContext() ?: return
                EdgeContinuedOverlayLaunchCoordinator.scheduleFloatingPointer(
                    context = hostContext,
                    settings = sessionSettings,
                    rawX = rawX,
                    rawY = rawY
                )
            }
        }

        GestureAction.RegionalScreenshotPick -> {
            if (!sessionMoveTimeActionFired &&
                sessionPathRecognizer.hasMetThreshold(
                    classification.trigger,
                    rawX,
                    rawY,
                    classifyOptions()
                )
            ) {
                sessionMoveTimeActionFired = true
                sessionCallbacks.cancelDelayed(sessionLongPressCheckRunnable)
                sessionPathRecognizer.disqualifyLongPress()
                sessionCallbacks.hapticConfirmLaunch()
                val hostContext = OverlayDependencyAccess.overlayHostContext() ?: return
                val screenWidth = hostContext.resources.displayMetrics.widthPixels.toFloat()
                EdgeContinuedOverlayLaunchCoordinator.scheduleRegionalPick(
                    context = hostContext,
                    settings = sessionSettings,
                    gestureStartRawY = sessionPathRecognizer.gestureStartRawY(),
                    edgeSide = sessionSide.toFloatBallPickDockSide(rawX, screenWidth),
                    rawX = rawX,
                    rawY = rawY
                )
            }
        }

        else -> Unit
    }
}

internal fun GestureSession.handleClassifiedGesture(
    classification: SwipeClassification,
    rawX: Float,
    rawY: Float,
    localX: Float,
    localY: Float,
    gestureStartRawY: Float
) {
    val action = sessionSettings.actionFor(sessionSide, classification.trigger, sessionActiveHandleId)

    // 引导等观察者要知道"用户真的做成了哪个手势"；纯通知，不影响下面的分发。
    GesturePerformedEvents.notify(action)

    when (action) {
        is GestureAction.OpenIndex -> enterIndexMode(localX, localY)

        // 「晃动菜单」只在晃动方向上有意义，边缘手势分发到这里直接忽略。
        is GestureAction.OpenShakeMenu -> Unit

        GestureAction.AdjustVolume, GestureAction.AdjustBrightness -> {
            val adjustControllerMode = when (action) {
                GestureAction.AdjustVolume -> ContinuousAdjustController.Mode.VOLUME
                GestureAction.AdjustBrightness -> ContinuousAdjustController.Mode.BRIGHTNESS
            }
            val triggerMode = sessionSettings.resolvedTriggerMode(sessionSide, classification.trigger, sessionActiveHandleId)
            when (triggerMode) {
                GestureTriggerMode.CONTINUOUS -> endSession()
                GestureTriggerMode.IMMEDIATE -> {
                    val fraction = sessionActionExecutor.readCurrentAdjustFraction(adjustControllerMode)
                    sessionCallbacks.hapticConfirmLaunch()
                    sessionCallbacks.onShowAdjustPanel(adjustControllerMode, fraction, rawY, deferWindowLayout = true)
                }
                GestureTriggerMode.ON_RELEASE, GestureTriggerMode.DEFAULT -> {
                    val fraction = sessionActionExecutor.readCurrentAdjustFraction(adjustControllerMode)
                    sessionCallbacks.hapticConfirmLaunch()
                    sessionCallbacks.onShowAdjustPanel(adjustControllerMode, fraction, rawY)
                    endSession()
                }
            }
        }

        is GestureAction.QuickLauncher -> {
            sessionContinuousPick.quickLauncher = false
            sessionCallbacks.hapticConfirmLaunch()
            val resolvedPanelId = QuickLauncherPanelDefaults.resolvePanelId(
                sessionSettings.quickLauncherPanels,
                action.panelId
            )
            EdgeDiag.log(
                "dispatch",
                "handleClassifiedGesture → QuickLauncher（松手/即时路径直接开面板）panelId='${action.panelId}'"
            )
            // 同一手势 = 开关：这一下刚刚关掉了这个钉住面板，就不要再打开它。
            if (consumeClosedPinnedQuickLauncher(resolvedPanelId)) {
                EdgeDiag.log("dispatch", "handleClassifiedGesture → 钉住面板已被本次手势关闭（开关语义）")
                endSession()
            } else {
                openQuickLauncherPanel(action)
            }
        }

        is GestureAction.ShellCommandPanel -> {
            sessionContinuousPick.shell = false
            sessionCallbacks.hapticConfirmLaunch()
            sessionCallbacks.onOpenShellCommandPanel(continuousPick = false)
            // Activity ???? edge session???????? UP ???? active ?????
            endSession()
        }

        is GestureAction.HoneycombLauncher -> {
            sessionContinuousPick.honeycomb = false
            sessionCallbacks.hapticConfirmLaunch()
            val triggerMode = sessionSettings.resolvedTriggerMode(
                sessionSide,
                classification.trigger,
                sessionActiveHandleId
            )
            val forceBrowse = triggerMode == GestureTriggerMode.ON_RELEASE ||
                triggerMode == GestureTriggerMode.IMMEDIATE
            sessionCallbacks.onShowHoneycombLauncher(
                continuousPick = false,
                rawX = rawX,
                rawY = rawY,
                forceBrowseMode = forceBrowse
            )
            if (triggerMode != GestureTriggerMode.IMMEDIATE) {
                endSession()
            }
        }

        GestureAction.RingLauncher -> {
            sessionContinuousPick.ringLauncher = false
            sessionCallbacks.hapticConfirmLaunch()
            sessionCallbacks.onShowRingLauncher(continuousPick = false, rawX, rawY)
            endSession()
        }

        GestureAction.AppCarouselSwitcher -> {
            sessionContinuousPick.appCarouselSwitcher = false
            sessionCallbacks.hapticConfirmLaunch()
            com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.show(
                sessionActionExecutor.context,
                sessionSettings,
                rawX,
                rawY,
                externalTracking = false
            )
            val triggerMode = sessionSettings.resolvedTriggerMode(
                sessionSide,
                classification.trigger,
                sessionActiveHandleId
            )
            if (triggerMode != GestureTriggerMode.IMMEDIATE) {
                endSession()
            }
        }

        GestureAction.FingertipRing -> {
            sessionContinuousPick.fingertipRing = false
            sessionCallbacks.hapticConfirmLaunch()
            sessionCallbacks.onShowFingertipRing(
                continuousPick = false,
                rawX = rawX,
                rawY = rawY
            )
            val triggerMode = sessionSettings.resolvedTriggerMode(
                sessionSide,
                classification.trigger,
                sessionActiveHandleId
            )
            if (triggerMode != GestureTriggerMode.IMMEDIATE) {
                endSession()
            }
        }

        GestureAction.HolographicLauncher -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(
                action,
                sessionSettings,
                anchorRawX = rawX,
                anchorRawY = rawY
            )
            endSession()
        }

        is GestureAction.TaskSwitcher -> {
            sessionContinuousPick.taskSwitcher = false
            sessionCallbacks.hapticConfirmLaunch()
            openPanel(OverlayPanelMode.TASK_SWITCHER)
        }

        is GestureAction.LaunchApp -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(action, sessionSettings)
            endSession()
        }

        is GestureAction.LaunchShortcut -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(action, sessionSettings)
            endSession()
        }

        GestureAction.Back, GestureAction.Home, GestureAction.Recents -> {
            sessionActionExecutor.execute(action, sessionSettings)
            endSession()
        }

        GestureAction.CloseCurrentApp, GestureAction.ForceStopCurrentApp,
        GestureAction.FreeWindowCurrentApp -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(action, sessionSettings)
            endSession()
        }

        GestureAction.ClickPassthrough -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.dispatchClickPassthrough(rawX, rawY, ::endSession)
        }

        GestureAction.FloatingPointer -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(
                action,
                sessionSettings,
                anchorRawX = rawX,
                anchorRawY = rawY,
                continueTouch = false,
            )
            endSession()
        }

        GestureAction.Flashlight, GestureAction.LaunchAssistant,
        GestureAction.VoiceSearch, GestureAction.VoiceAssistant,
        -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(action, sessionSettings)
            endSession()
        }

        GestureAction.ToggleMute,
        GestureAction.ToggleDnd,
        GestureAction.ScreenRecord,
        GestureAction.ToggleWifi,
        GestureAction.ToggleMobileData,
        GestureAction.ToggleNfc,
        GestureAction.ToggleBluetooth,
        GestureAction.ToggleAutoBrightness,
        GestureAction.ToggleAutoRotate,
        GestureAction.ForcePortrait,
        GestureAction.ForceLandscape,
        GestureAction.SwitchInputMethod,
        GestureAction.MediaPlayPause,
        GestureAction.MediaPrevious,
        GestureAction.MediaNext,
        GestureAction.PreviousApp,
        GestureAction.OpenNotifications,
        GestureAction.ReadLatestMessage,
        GestureAction.ReadLatestMessageInSmallWindow,
        GestureAction.ClearNotifications,
        GestureAction.OpenQuickSettings,
        GestureAction.LockScreen,
        GestureAction.LockScreenAndSilenceRing,
        GestureAction.LockScreenAndMuteAll,
        GestureAction.Screenshot,
        GestureAction.FullscreenScreenshotPick,
        GestureAction.RegionalScreenshotPick,
        GestureAction.SearchPanel,
        GestureAction.FileBrowser,
        GestureAction.PowerMenu,
        GestureAction.KeepScreenOn,
        GestureAction.SnoozeOverlays,
        GestureAction.ScrollToTop,
        GestureAction.ScrollToBottom,
        GestureAction.QuickToolsOverlay,
        GestureAction.QuickMenu,
        GestureAction.HolographicLauncher,
        GestureAction.WidgetPopupOverlay,
        GestureAction.StashPanel,
        GestureAction.ClipboardPanel,
        GestureAction.ClipboardFloat,
        GestureAction.ClipboardPick,
        GestureAction.ClipboardPaste,
        GestureAction.PointerGestureRecorder,
        GestureAction.PointerRealtimeGesture,
        GestureAction.OpenFloatingPointerRadialMenu,
        GestureAction.VolumePanel,
        GestureAction.ScreenTranslate,
        GestureAction.ScreenSearch,
        GestureAction.UniversalCopy,
        GestureAction.FreezerPanel,
        GestureAction.Refreeze,
        GestureAction.Remind,
        GestureAction.TimedDnd,
        GestureAction.Remind1m,
        GestureAction.Remind3m,
        GestureAction.Remind5m,
        GestureAction.Remind10m,
        GestureAction.Remind15m,
        GestureAction.OpenInternetPanel,
        GestureAction.OpenVolumePanel,
        GestureAction.OpenVolumeSlider,
        GestureAction.VolumeUp,
        GestureAction.VolumeDown,
        GestureAction.CurrentAppInfo,
        GestureAction.ScreenOffKeepAwake,
        GestureAction.TouchBlock,
        GestureAction.ScreenControl,
        GestureAction.ScreenDraw,
        GestureAction.GalleryImageEdit,
        GestureAction.WechatScan,
        GestureAction.WechatPayCode,
        GestureAction.AlipayPayCode,
        GestureAction.AlipayScan,
        GestureAction.PinToScreen,
        GestureAction.ForegroundActivityInspector,
        GestureAction.SmartScreenshot,
        is GestureAction.SimulateKeyEvent,
        is GestureAction.SimulatePointerSwipe,
        is GestureAction.ExecuteShellCommand,
        is GestureAction.RunMacro,
        GestureAction.PageScroll,
        is GestureAction.OpenLink,
        is GestureAction.QuickWheel,
        GestureAction.ToggleShakeGesture,
        GestureAction.ToggleFloatBall,
        -> {
            sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(
                action,
                sessionSettings,
                anchorRawX = rawX,
                anchorRawY = rawY,
                continueTouch = sessionMoveTimeActionFired
            )
            endSession()
        }

        GestureAction.None,
        GestureAction.CornerInnerCancel,
        GestureAction.CornerInnerPinWheel,
        -> endSession()
    }
}

/** @return true if the overlay session should end after this quick-launcher tap. */
internal fun GestureSession.dispatchQuickLauncherAction(
    action: GestureAction,
    localX: Float,
    localY: Float,
    rawY: Float,
    confirmHaptic: Boolean = true
): Boolean {
    when (action) {
        GestureAction.OpenIndex -> {
            enterIndexMode(localX, localY)
            return false
        }
        is GestureAction.QuickLauncher -> return false
        GestureAction.TaskSwitcher -> {
            sessionContinuousPick.taskSwitcher = false
            if (confirmHaptic) sessionCallbacks.hapticConfirmLaunch()
            openPanel(OverlayPanelMode.TASK_SWITCHER)
            return false
        }
        GestureAction.ShellCommandPanel -> {
            sessionContinuousPick.shell = false
            if (confirmHaptic) sessionCallbacks.hapticConfirmLaunch()
            sessionCallbacks.onOpenShellCommandPanel(continuousPick = false)
            return true
        }
        GestureAction.HoneycombLauncher -> {
            sessionContinuousPick.honeycomb = false
            if (confirmHaptic) sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(
                GestureAction.HoneycombLauncher,
                sessionSettings,
                anchorRawY = rawY
            )
            return true
        }
        GestureAction.RingLauncher -> {
            sessionContinuousPick.ringLauncher = false
            if (confirmHaptic) sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(
                GestureAction.RingLauncher,
                sessionSettings,
                anchorRawY = rawY
            )
            return true
        }
        GestureAction.AppCarouselSwitcher -> {
            sessionContinuousPick.appCarouselSwitcher = false
            if (confirmHaptic) sessionCallbacks.hapticConfirmLaunch()
            sessionActionExecutor.execute(
                GestureAction.AppCarouselSwitcher,
                sessionSettings,
                anchorRawY = rawY
            )
            return true
        }
        GestureAction.FingertipRing -> {
            // 快速启动器 overlay 槽位禁止指尖环；旧数据应已清洗为 None，此处兜底忽略。
            return false
        }
        GestureAction.AdjustVolume, GestureAction.AdjustBrightness -> {
            val mode = when (action) {
                GestureAction.AdjustVolume -> ContinuousAdjustController.Mode.VOLUME
                GestureAction.AdjustBrightness -> ContinuousAdjustController.Mode.BRIGHTNESS
            }
            val fraction = sessionActionExecutor.applyAdjustOnce(mode, rawY, rawY)
                ?: sessionActionExecutor.readCurrentAdjustFraction(mode)
            if (confirmHaptic) sessionCallbacks.hapticConfirmLaunch()
            sessionCallbacks.onShowAdjustPanel(mode, fraction, rawY)
            return true
        }
        else -> {
            if (confirmHaptic) sessionCallbacks.hapticConfirmLaunch()
            // 全屏截图取词会在几十毫秒内按快门。这里必须**先**把快速启动器面板收起来再执行动作：
            // 面板画在 presentation 窗里且自带磨砂模糊，晚一帧收就会被拍进全屏截图
            // （表现为"截图里有一块被快速启动器模糊遮挡"，且只在卡顿时偶发）。
            // 会话已经在上面收掉了，返回 false 让调用方不要再收一次。
            if (action == GestureAction.FullscreenScreenshotPick) {
                endSession()
                sessionActionExecutor.execute(action, sessionSettings, anchorRawY = rawY)
                return false
            }
            // 全局复制同理、且更明显：它把"屏幕上看得见的文字"框出来，面板不收掉就会一起进取词浮层
            //（钉住态的面板本来就不随动作关闭，于是面板会一直留在浮层里）。所以先收会话再动作；
            // 但**不能**紧跟着立即采：presentation 窗的 removeView 是异步的，这一帧仍能采到它，
            // 隔两帧再执行，保证窗口已经摘掉、无障碍快照也已刷成空面板。
            if (action == GestureAction.UniversalCopy) {
                endSession()
                mainHandler.postDelayed(
                    { sessionActionExecutor.execute(action, sessionSettings, anchorRawY = rawY) },
                    QUICK_LAUNCHER_DISMISS_SETTLE_MS
                )
                return false
            }
            sessionActionExecutor.execute(action, sessionSettings, anchorRawY = rawY)
            return true
        }
    }
}

/**
 * 收面板 → 执行"要拍/要采整屏"的动作之间的静默期。
 *
 * `SideOverlayWindowManager.detachPresentationWindow()` 走的是 `WindowManager.removeView`，
 * `ViewRootImpl.die` 是异步的：同步紧接着去读无障碍窗口树，那一帧还能读到旧面板。
 * 32ms ≈ 两帧，够窗口摘除 + 无障碍快照刷新。
 */
private const val QUICK_LAUNCHER_DISMISS_SETTLE_MS = 32L

/** 主线程 Handler：这条延时不能走 `View.postDelayed`，面板收起后 view 已 detach，runnable 会被挂起。 */
private val mainHandler = Handler(Looper.getMainLooper())
