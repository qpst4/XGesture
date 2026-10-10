package com.slideindex.app.gesture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboardfloat.ClipboardPasteCoordinator
import com.slideindex.app.clipboardfloat.PasteResult
import com.slideindex.app.R
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.executor.ActionExecutorLaunch
import com.slideindex.app.gesture.executor.ActionExecutorMediaSystem
import com.slideindex.app.gesture.executor.ActionExecutorOverlayPanels
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.PickResultFromHistoryCoordinator
import com.slideindex.app.overlay.StashPanelInitialTab
import com.slideindex.app.overlay.FloatingPointerOverlayWindow
import com.slideindex.app.clipboard.ClipboardFocusReader
import com.slideindex.app.overlay.HoneycombAppPickerOverlayWindow
import com.slideindex.app.overlay.ringlauncher.RingLauncherOverlayWindow
import com.slideindex.app.overlay.holographic.HolographicLauncherOverlayWindow
import com.slideindex.app.overlay.quickwheel.QuickWheelOverlayWindow
import com.slideindex.app.overlay.OhoQuickToolsOverlayWindow
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.WidgetPopupOverlayWindow
import com.slideindex.app.service.ClipboardFloatLifecycle
import com.slideindex.app.copy.UniversalCopyOverlay
import com.slideindex.app.freezer.FreezerLaunchState
import com.slideindex.app.freezer.FreezerOperations
import com.slideindex.app.freezer.FreezerOverlayWindow
import com.slideindex.app.freezer.FreezerTab
import com.slideindex.app.overlay.volumepanel.VolumePanelOverlayWindow
import com.slideindex.app.remind.RemindDurationPickerOverlay
import com.slideindex.app.timeddnd.TimedDndDurationPickerOverlay
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.translate.overlay.ScreenTranslationController
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.ShellCommandRunner
import com.slideindex.app.util.AssistantLauncher
import com.slideindex.app.util.VoiceActionHelper
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.FlashlightHelper
import com.slideindex.app.util.InputMethodHelper
import com.slideindex.app.util.InputTapUtil
import com.slideindex.app.util.OverlayBrightnessControl
import com.slideindex.app.util.QuickToolsHelper
import com.slideindex.app.util.ScreenRecordHelper
import com.slideindex.app.util.SystemGestureActions
import com.slideindex.app.util.OverlaySnoozeController
import com.slideindex.app.search.SearchEngineLauncher
import com.slideindex.app.util.VolumeControlHelper

class ActionExecutor(
    internal val context: Context,
    private val appRepository: AppRepository,
    private val clickPassthroughHandler: ((Float, Float, () -> Unit) -> Unit)? = null,
    overlayBrightness: OverlayBrightnessControl? = null,
    private val side: PanelSide? = null,
    onShellCommandsPersist: ((List<ShellCommand>) -> Unit)? = null
) {
    private val pasteScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaSystem = ActionExecutorMediaSystem(context, overlayBrightness)
    private val overlayPanels = ActionExecutorOverlayPanels(context, onShellCommandsPersist)
    private val launchHelper = ActionExecutorLaunch(context, appRepository, mainHandler)

    fun beginContinuousAdjust(mode: ContinuousAdjustController.Mode, rawY: Float): Boolean =
        mediaSystem.beginContinuousAdjust(mode, rawY)

    fun updateContinuousAdjust(mode: ContinuousAdjustController.Mode, rawY: Float) {
        mediaSystem.updateContinuousAdjust(mode, rawY)
    }

    fun endContinuousAdjust() {
        mediaSystem.endContinuousAdjust()
    }

    fun applyAdjustOnce(
        mode: ContinuousAdjustController.Mode,
        anchorRawY: Float,
        targetRawY: Float
    ): Float? = mediaSystem.applyAdjustOnce(mode, anchorRawY, targetRawY)

    fun readCurrentAdjustFraction(mode: ContinuousAdjustController.Mode): Float =
        mediaSystem.readCurrentAdjustFraction(mode)

    fun clearBrightnessPreview() {
        mediaSystem.clearBrightnessPreview()
    }

    fun adjustMode(): ContinuousAdjustController.Mode? = mediaSystem.adjustMode()

    fun adjustFraction(): Float = mediaSystem.adjustFraction()

    fun readRingerMode(): Int = mediaSystem.readRingerMode()

    fun cycleRingerMode(): Int? = mediaSystem.cycleRingerMode()

    fun readInterruptionFilter(): Int = mediaSystem.readInterruptionFilter()

    fun toggleDnd(): Int? = mediaSystem.toggleDnd()

    fun readAutoBrightnessEnabled(): Boolean = mediaSystem.readAutoBrightnessEnabled()

    fun toggleAutoBrightness(): Boolean? = mediaSystem.toggleAutoBrightness()

    fun readDarkModeEnabled(): Boolean = mediaSystem.readDarkModeEnabled()

    fun toggleDarkMode(): Boolean? = mediaSystem.toggleDarkMode()

    fun readVolumeFraction(stream: VolumeControlHelper.Stream): Float =
        mediaSystem.readVolumeFraction(stream)

    fun setVolumeFraction(stream: VolumeControlHelper.Stream, fraction: Float) {
        mediaSystem.setVolumeFraction(stream, fraction)
    }

    fun setBrightnessFraction(fraction: Float, previewOnly: Boolean = false) {
        mediaSystem.setBrightnessFraction(fraction, previewOnly)
    }

    fun execute(
        action: GestureAction,
        settings: AppSettings,
        longPressArmed: Boolean = false,
        anchorRawX: Float? = null,
        anchorRawY: Float? = null,
        continueTouch: Boolean = false,
        panelSide: PanelSide? = null,
        // 悬浮球手势的「手指按下位置」；只有悬浮球手势派发会传，其它入口为 null。
        gestureTouchDownRawX: Float? = null,
        gestureTouchDownRawY: Float? = null
    ): Boolean {
        val resolvedSide = panelSide ?: side
        val touchDownAnchor = FloatBallOverlayAnchorPolicy.anchorsAtTouchDown(
            action = action,
            enabled = settings.floatBallOverlayAnchorAtTouchDown
        )
        val overlayAnchorX =
            FloatBallOverlayAnchorPolicy.resolve(touchDownAnchor, anchorRawX, gestureTouchDownRawX)
        val overlayAnchorY =
            FloatBallOverlayAnchorPolicy.resolve(touchDownAnchor, anchorRawY, gestureTouchDownRawY)
        return when (action) {
            GestureAction.OpenIndex,
            GestureAction.TaskSwitcher,
            -> overlayPanels.showEdgeHostedPanel(action, anchorRawY, resolvedSide)
            is GestureAction.QuickLauncher ->
                overlayPanels.showEdgeHostedPanel(action, overlayAnchorY, resolvedSide)
            GestureAction.ShellCommandPanel -> overlayPanels.openShellCommandPanelStandalone()
            is GestureAction.ExecuteShellCommand -> executeShellCommand(action)
            is GestureAction.OpenLink -> {
                if (action.url.isBlank()) return false
                SearchEngineLauncher.launchOpenableUri(context, action.url, settings, longPressArmed)
            }
            GestureAction.None, GestureAction.ClickPassthrough,
            GestureAction.CornerInnerCancel, GestureAction.CornerInnerPinWheel,
            -> false
            GestureAction.AdjustVolume -> overlayPanels.showEdgeHostedPanel(GestureAction.AdjustVolume, anchorRawY, resolvedSide)
            GestureAction.AdjustBrightness -> overlayPanels.showEdgeHostedPanel(GestureAction.AdjustBrightness, anchorRawY, resolvedSide)
            is GestureAction.SimulatePointerSwipe -> {
                val x = anchorRawX ?: return false
                val y = anchorRawY ?: return false
                if (FloatingPointerOverlayWindow.isVisible) {
                    FloatingPointerOverlayWindow.schedulePointerSwipe(x, y, action.config)
                } else {
                    InputTapUtil.dispatchPointerSwipeAsync(x, y, action.config)
                }
                true
            }
            GestureAction.PointerGestureRecorder,
            GestureAction.PointerRealtimeGesture,
            GestureAction.OpenFloatingPointerRadialMenu,
            GestureAction.FingertipRing,
            -> false
            GestureAction.QuickToolsOverlay ->
                overlayPanels.showStandaloneOverlay(anchorRawY) { y ->
                    OhoQuickToolsOverlayWindow.show(context, settings, resolvedSide, y)
                }
            GestureAction.HoneycombLauncher ->
                overlayPanels.showStandaloneOverlay(anchorRawY) { y ->
                    val x = anchorRawX ?: (context.resources.displayMetrics.widthPixels / 2f)
                    HoneycombAppPickerOverlayWindow.show(
                        context = context,
                        settings = settings,
                        anchorRawX = x,
                        anchorRawY = y,
                        externalTracking = false,
                        onLaunch = { item, longPressArmed ->
                            launchQuickItem(
                                item,
                                settings,
                                longPressArmed = longPressArmed,
                                anchorRawY = y,
                                panelSide = panelSide,
                            )
                        }
                    )
                }
            GestureAction.RingLauncher ->
                overlayPanels.showStandaloneOverlay(overlayAnchorY) { y ->
                    val x = overlayAnchorX ?: (context.resources.displayMetrics.widthPixels / 2f)
                    RingLauncherOverlayWindow.show(
                        context = context,
                        settings = settings,
                        anchorRawX = x,
                        anchorRawY = y,
                        externalTracking = continueTouch,
                        onLaunch = { item, longPressArmed ->
                            launchQuickItem(
                                item,
                                settings,
                                longPressArmed = longPressArmed,
                                anchorRawY = y,
                                panelSide = panelSide,
                            )
                        }
                    )
                }
            GestureAction.AppCarouselSwitcher -> {
                val x = anchorRawX ?: (context.resources.displayMetrics.widthPixels / 2f)
                val y = anchorRawY ?: (context.resources.displayMetrics.heightPixels / 2f)
                com.slideindex.app.overlay.carousel.AppCarouselSwitcherOverlay.show(
                    context,
                    settings,
                    x,
                    y,
                    externalTracking = continueTouch
                )
            }
            GestureAction.HolographicLauncher ->
                overlayPanels.showStandaloneOverlay(anchorRawY) { _ ->
                    HolographicLauncherOverlayWindow.show(
                        context = context,
                        settings = settings,
                        actionExecutor = this
                    )
                }
            is GestureAction.QuickWheel ->
                overlayPanels.showStandaloneOverlay(anchorRawY) { y ->
                    val x = anchorRawX ?: (context.resources.displayMetrics.widthPixels / 2f)
                    QuickWheelOverlayWindow.show(
                        context = context,
                        settings = settings,
                        wheelId = action.wheelId,
                        anchorRawX = x,
                        anchorRawY = y,
                        actionExecutor = this,
                        externalTracking = continueTouch,
                        shape = action.shape,
                        manualSectorMask = action.manualSectorMask,
                        anchorMode = action.anchorMode,
                    )
                }
            GestureAction.WidgetPopupOverlay ->
                overlayPanels.showStandaloneOverlay(anchorRawY) { y ->
                    WidgetPopupOverlayWindow.show(context, settings, resolvedSide, y)
                }
            GestureAction.StashPanel -> FloatBallStashPanel.toggle(
                context = context,
                panelSide = resolvedSide
            )
            GestureAction.ClipboardPanel -> FloatBallStashPanel.toggle(
                context = context,
                initialTab = StashPanelInitialTab.Clipboard,
                panelSide = resolvedSide
            )
            GestureAction.ClipboardFloat -> {
                ClipboardFloatLifecycle.showExpanded(context)
                true
            }
            GestureAction.ClipboardPick -> {
                ClipboardFocusReader.read(context) { payload ->
                    PickResultFromHistoryCoordinator.openFromClipboardPayload(context, payload)
                }
                true
            }
            GestureAction.ClipboardPaste -> {
                val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return false
                val repo = ClipboardAccess.repository ?: return false
                pasteScope.launch {
                    val entry = withContext(Dispatchers.IO) { repo.peekLatestEntry() } ?: return@launch
                    ClipboardPasteCoordinator.pasteEntry(
                        service = service,
                        context = context,
                        entry = entry,
                        fvStyle = settings.clipboardPasteFvStyleEnabled,
                    ) { result ->
                        when (result) {
                            null, PasteResult.Success -> Unit
                            is PasteResult.Failure ->
                                ClipboardPasteCoordinator.toastPasteFailure(context, result.reason)
                        }
                    }
                }
                true
            }
            GestureAction.FloatingPointer -> {
                FloatingPointerOverlayWindow.toggle(
                    context,
                    settings,
                    anchorRawX,
                    anchorRawY,
                    continueTouch
                )
                true
            }
            is GestureAction.LaunchApp ->
                launchHelper.launchApp(action.packageName, settings, longPressArmed, action.windowMode)
            is GestureAction.LaunchShortcut -> {
                launchHelper.launchGestureShortcut(action, settings, longPressArmed)
                true
            }
            GestureAction.Back, GestureAction.Home, GestureAction.Recents ->
                SlideIndexAccessibilityService.perform(action)
            GestureAction.CloseCurrentApp -> {
                launchHelper.closeCurrentApp()
                true
            }
            GestureAction.ForceStopCurrentApp -> {
                launchHelper.forceStopCurrentApp()
                true
            }
            GestureAction.FreeWindowCurrentApp -> {
                launchHelper.freeWindowForegroundApp(settings)
                true
            }
            GestureAction.Flashlight -> FlashlightHelper.toggle(context)
            GestureAction.ToggleDnd -> VolumeControlHelper.toggleDnd(context) != null
            GestureAction.TimedDnd -> {
                TimedDndDurationPickerOverlay.show(context)
                true
            }
            GestureAction.ScreenRecord -> {
                ScreenRecordHelper.toggle(context)
                true
            }
            GestureAction.ToggleWifi -> QuickToolsHelper.toggleWifi(context) == true
            GestureAction.ToggleMobileData -> QuickToolsHelper.toggleMobileData(context) == true
            GestureAction.ToggleAutoBrightness ->
                com.slideindex.app.util.BrightnessControlHelper.toggleAutoBrightness(context) != null
            GestureAction.SwitchInputMethod -> InputMethodHelper.switchInputMethod(context)
            GestureAction.LaunchAssistant -> {
                AssistantLauncher.launchDefault(context)
                true
            }
            GestureAction.VoiceSearch -> VoiceActionHelper.launchVoiceSearch(context)
            GestureAction.VoiceAssistant -> VoiceActionHelper.launchVoiceCommand(context)
            GestureAction.ToggleAutoRotate -> QuickToolsHelper.toggleAutoRotate(context) != null
            GestureAction.ForcePortrait -> QuickToolsHelper.forcePortrait(context)
            GestureAction.ForceLandscape -> QuickToolsHelper.forceLandscape(context)
            GestureAction.ToggleMute -> SystemGestureActions.toggleMute(context)
            GestureAction.LockScreenAndSilenceRing -> {
                SystemGestureActions.silenceRinger(context)
                SlideIndexAccessibilityService.perform(GestureAction.LockScreen)
            }
            GestureAction.LockScreenAndMuteAll -> {
                SystemGestureActions.muteAllVolumes(context)
                SlideIndexAccessibilityService.perform(GestureAction.LockScreen)
            }
            GestureAction.MediaPlayPause -> SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            GestureAction.MediaPrevious -> SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            GestureAction.MediaNext -> SystemGestureActions.dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            GestureAction.OpenInternetPanel -> SystemGestureActions.openNativeInternetPanel(context)
            GestureAction.OpenVolumePanel -> SystemGestureActions.openNativeVolumePanel(context)
            GestureAction.VolumeUp -> {
                SystemGestureActions.volumeUp(context)
                true
            }
            GestureAction.VolumeDown -> {
                SystemGestureActions.volumeDown(context)
                true
            }
            GestureAction.CurrentAppInfo -> SystemGestureActions.openCurrentAppInfo(context)
            GestureAction.ScreenOffKeepAwake -> {
                com.slideindex.app.overlay.PseudoScreenOffOverlayWindow.toggle(context)
                true
            }
            GestureAction.PinToScreen -> {
                com.slideindex.app.overlay.PinToScreenDialog.show(context)
                true
            }
            GestureAction.ForegroundActivityInspector -> {
                com.slideindex.app.overlay.ForegroundActivityInspectorOverlayWindow.toggle(context)
                true
            }
            is GestureAction.SimulateKeyEvent -> SystemGestureActions.simulateKeyEvent(context, action.keyCode, action.isLongPress)
            GestureAction.PreviousApp,
            GestureAction.OpenNotifications,
            GestureAction.OpenQuickSettings,
            GestureAction.LockScreen,
            GestureAction.Screenshot,
            GestureAction.PowerMenu,
            GestureAction.KeepScreenOn,
            GestureAction.ScrollToTop,
            GestureAction.ScrollToBottom,
            -> SlideIndexAccessibilityService.perform(action)
            GestureAction.FullscreenScreenshotPick ->
                SlideIndexAccessibilityService.pickFullscreen(
                    context,
                    settings.floatBallOcrFallbackEnabled,
                    settings.floatBallOcrModelId
                )
            GestureAction.RegionalScreenshotPick -> {
                if (!continueTouch) return false
                com.slideindex.app.overlay.RegionalPickOverlay.show(
                    context = context,
                    appSettings = settings,
                    anchorRawX = anchorRawX,
                    anchorRawY = anchorRawY,
                    continueTouch = continueTouch
                )
                true
            }
            GestureAction.SearchPanel ->
                overlayPanels.showSearchPanel(context, settings, resolvedSide)
            GestureAction.VolumePanel -> VolumePanelOverlayWindow.show(context)
            GestureAction.ScreenTranslate -> {
                SlideIndexAccessibilityService.performScreenTranslate()
                true
            }
            GestureAction.Remind,
            GestureAction.Remind1m,
            GestureAction.Remind3m,
            GestureAction.Remind5m,
            GestureAction.Remind10m,
            GestureAction.Remind15m,
            -> {
                RemindDurationPickerOverlay.show(context)
                true
            }
            GestureAction.UniversalCopy -> {
                SlideIndexAccessibilityService.performUniversalCopy()
                true
            }
            GestureAction.ScreenSearch -> {
                SlideIndexAccessibilityService.performScreenSearch()
                true
            }
            GestureAction.FreezerPanel -> {
                // 与暂存面板同规则：开着再触发就收起，否则打开了却关不掉（动作只调 show）。
                if (FreezerOverlayWindow.isShowing) {
                    FreezerOverlayWindow.dismiss()
                } else {
                    FreezerLaunchState.setPendingInitialTab(FreezerTab.FROZEN)
                    FreezerOverlayWindow.show(context)
                }
                true
            }
            GestureAction.Refreeze -> {
                CoroutineScope(Dispatchers.IO).launch {
                    // 按每个成员自己的意图还原（上次暂停的还它暂停），没有意图的按冰箱工作模式兜底。
                    FreezerOperations.restoreIntents(
                        context = context,
                        packages = settings.freezerAppPackages,
                        fallbackPause = settings.freezerWorkMode.isPause,
                    )
                }
                true
            }
            GestureAction.SnoozeOverlays -> {
                OverlaySnoozeController.snooze(context)
                true
            }
            GestureAction.SmartScreenshot -> {
                SlideIndexAccessibilityService.performSmartScreenshot()
            }
        }
    }

    fun launchQuickItem(
        item: QuickLauncherItem,
        settings: AppSettings,
        longPressArmed: Boolean = false,
        anchorRawY: Float? = null,
        panelSide: PanelSide? = null,
    ): Boolean = launchHelper.launchQuickItem(item, settings, longPressArmed, anchorRawY) { action, appSettings, armed, y ->
        // 从圆环/蜂窝/面板里点开的条目同样要知道“当前在哪一侧”，否则打开的面板只能按
        // 宿主默认（左优先）落地：真机反馈右侧圆环里点快速启动器槽位，面板弹到左边。
        execute(action, appSettings, armed, anchorRawX = null, anchorRawY = y, panelSide = panelSide)
    }

    fun switchToRecentTask(
        taskId: Int,
        rawIdentifier: String,
        topComponent: String,
        packageName: String,
        settings: AppSettings
    ) = launchHelper.switchToRecentTask(taskId, rawIdentifier, topComponent, packageName, settings)

    fun dispatchClickPassthrough(rawX: Float, rawY: Float, onComplete: () -> Unit = {}) {
        val handler = clickPassthroughHandler
        if (handler != null) {
            handler(rawX, rawY, onComplete)
        } else {
            InputTapUtil.dispatchTap(rawX, rawY)
            onComplete()
        }
    }

    private fun executeShellCommand(action: GestureAction.ExecuteShellCommand): Boolean {
        val commandLine = action.command.trim()
        if (commandLine.isEmpty()) return false
        Thread {
            ShellCommandRunner.execute(
                context = context,
                command = ShellCommand(
                    label = "Gesture",
                    command = commandLine
                )
            )
        }.start()
        return true
    }

    internal companion object {
        const val TAG = "ActionExecutor"
    }
}
