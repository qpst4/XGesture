package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import androidx.core.net.toUri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.fvgesture.FvCustomGestureOutcome
import com.slideindex.app.fvgesture.resolveCustomGesture
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.clipboard.ClipboardAccess
import com.slideindex.app.clipboardfloat.ClipboardPasteCoordinator
import com.slideindex.app.clipboardfloat.PasteResult
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.compositor.OverlayCompositor
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.inspire.PickPrefetchCache
import com.slideindex.app.service.AccessibilityTextExtractor
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.floatball.FloatBallGestureType
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.overlay.ringlauncher.RingLauncherOverlayWindow
import com.slideindex.app.ui.miuix.theme.resolveAppAccentArgb
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.HapticHelper
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RECT_MIN_SIDE_DP = 48f
/** FV n2.g.f22819b0: regional rect must be >= 3dp on both sides or screenshot is cancelled. */
private const val REGIONAL_RECT_MIN_SIDE_DP = 3f
/** Slop-phase pick cross fades in from this alpha to 1.0 before full drag starts. */
private const val PICK_PREVIEW_ALPHA_MIN = 0.25f

/**
 * Persistent float ball: ball acts as joystick, crosshair/plus acts as screen pointer.
 * Independent from [FloatingPointerOverlayWindow] (edge-gesture virtual pointer).
 */
@SuppressLint("StaticFieldLeak")
object FloatBallOverlay {
    private const val TAG = "FloatBallOverlay"
    private const val EDGE_MARGIN_DP = 8f

    /** 圆环工具栏「移动」：进入移动模式时球朝屏幕中心内移的距离（纯提示，不落盘）。 */
    private const val MOVE_MODE_HINT_DP = 14f

    /** 移动模式的自动退出时间（用户点了但没拖，几秒后自动复位）。 */
    private const val MOVE_MODE_TIMEOUT_MS = 6000L
    /** FV O0: reschedule cache rebuild after finger moves this many dp. */
    private const val CACHE_REFRESH_MOVE_DP = 3f
    /** FV O0 / I0: delay before rebuilding preview bounds cache during drag. */
    private const val CACHE_REFRESH_MS = 100L
    /** FV G4: defer first preview-bounds cache build after drag starts. */
    private const val INITIAL_CACHE_DELAY_MS = 300L
    /** Dwell after paste hint before upgrading to a11y text pick (A). */
    private const val DRAG_PASTE_PICK_UPGRADE_MS = 800L
    /** Defer chrome z-order sync until side-panel enter animation settles. */
    private const val CHROME_RAISE_DEFER_MS = 320L
    /** 确认"chrome 真的被系统摘掉"的等待时长（排除窗口过渡瞬间的误判）。 */
    private const val CHROME_REBUILD_CONFIRM_MS = 250L
    /** After deferred pick screenshot lands, let panel layout settle before chrome WM work. */
    private const val PICK_SCREENSHOT_CHROME_SETTLE_MS = 48L
    /** Fallback when deferred screenshot never arrives. */
    private const val PICK_SCREENSHOT_CHROME_FALLBACK_MS = 900L
    private const val FLOAT_BALL_PASSTHROUGH_FRAMES_BEFORE_INJECT = 6
    private const val FLOAT_BALL_PASSTHROUGH_RESTORE_DELAY_MS = 140L

    /** "提交后的 Y 预览值"顶住多久；设置落盘正常都在几十毫秒内，超时就放手避免卡住旧值。 */
    private const val PENDING_POSITION_Y_TIMEOUT_MS = 1_500L
    /** 浮点比较容差：设置落盘经过序列化，不能要求按位相等。 */
    private const val POSITION_Y_EPSILON = 0.0005f

    private var passivePickPreviewAlpha = 1f
    private var passivePickPreviewAnchor: Offset? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dragSession = FloatBallDragSession()

    private var windowManager: WindowManager? = null
    private var displayView: FloatBallDisplayHost? = null
    private var displayLayoutParams: WindowManager.LayoutParams? = null
    private var displayOwner: OverlayComposeOwner? = null
    private var touchHost: FloatBallTouchHostLayout? = null
    private var touchLayoutParams: WindowManager.LayoutParams? = null
    private var lineTouchHost: FloatBallStripHost? = null
    private var lineTouchLayoutParams: WindowManager.LayoutParams? = null
    private var ballIdleChromeOwner: OverlayComposeOwner? = null
    private var lineIdleChromeOwner: OverlayComposeOwner? = null
    private var ballIdleChromeView: ComposeView? = null
    private var lineIdleChromeView: ComposeView? = null
    private var ballIconView: FloatBallIconView? = null
    private var cursorPreviewView: FloatBallCursorPreviewView? = null
    private val screenOffDismissReceiver = ScreenOffDismissReceiver { hideCursor() }
    private var appContext: Context? = null
    private var positionYPreviewRestore: Float? = null

    /**
     * 已提交、但还没被设置回流确认的 Y 预览值。
     *
     * 滑条松手时"写设置"是异步落盘的：如果这时立刻丢掉预览值，球会先回到已保存的旧位置，
     * 等新值落盘回流后再跳过去（用户看到的就是"先回原位、再跳到正确位置"）。
     * 这里把提交值顶住，直到 incoming 的设置值追上（或超时兜底），与 [committedActiveSideUntilPersist] 同一套做法。
     */
    private var pendingPositionYFraction: Float? = null

    private val clearPendingPositionYRunnable = Runnable { pendingPositionYFraction = null }
    private var appearancePreviewRestore: FloatBallAppearancePreviewSnapshot? = null

    private data class FloatBallAppearancePreviewSnapshot(
        val sizeDp: Float,
        val opacity: Float,
        val visibleFraction: Float,
        val lineHeightFraction: Float,
        val lineWidthFraction: Float,
        val lineOpacity: Float
    )

    private var sceneState: FloatBallSceneState? = null

    private val settingsState: MutableState<AppSettings>?
        get() = sceneState?.settingsState
    private val cursorVisibleState: MutableState<Boolean>?
        get() = sceneState?.cursorVisible
    private val cursorPausedState: MutableState<Boolean>?
        get() = sceneState?.cursorPaused
    private val cursorAnchorState: MutableState<Offset>?
        get() = sceneState?.cursorAnchor
    private val selectionStartState: MutableState<Offset?>?
        get() = sceneState?.selectionStart
    private val selectionPreviewBoundsState: MutableState<Rect?>?
        get() = sceneState?.selectionPreviewBounds
    private val stripZonePreviewState: MutableState<Boolean>?
        get() = sceneState?.stripZonePreview
    private val styleVisualGenerationState: androidx.compose.runtime.MutableIntState?
        get() = sceneState?.styleVisualGeneration
    private val ballDraggingState: MutableState<Boolean>?
        get() = sceneState?.ballDragging

    private var onPositionPersisted: ((xFraction: Float, yFraction: Float) -> Unit)? = null
    private var onActiveSidePersisted: ((FloatBallSide) -> Unit)? = null
    private var pauseRunnable: Runnable? = null
    private var passiveLineRestoreRunnable: Runnable? = null
    private var deferredGifResumeRunnable: Runnable? = null
    private var deferredDragStartGeneration = 0
    private var lastPauseScheduleX = Float.NaN
    private var lastPauseScheduleY = Float.NaN
    private var cacheRefreshRunnable: Runnable? = null
    private var initialCacheRunnable: Runnable? = null
    private var lastCacheRefreshX = Float.NaN
    private var lastCacheRefreshY = Float.NaN
    private var lastDragPasteHintActive = false
    /** Locked at hover pause when paste icon is shown (FV getSelectedRect). */
    private var lockedDragPasteEditableRect: Rect? = null
    /** After paste-hint dwell, release commits a11y pick (A) instead of paste. */
    private var dragPastePickUpgraded = false
    private var dragPasteUpgradeRunnable: Runnable? = null
    private var captureSuppressed = false
    private var chromeDetachedForCapture = false
    private var isDragging = false
    private var chromeZOrderFront = true
    /** Panel opened during drag; defer WM remove/add until [restoreAfterDragEnd]. */
    private var chromeRaiseDeferredForDrag = false
    /** Non-null: only re-add triggers on these sides; null = all sides (center/fullscreen panels). */
    private var edgeChromeRaiseSides: Set<PanelSide>? = null
    private var pendingChromeRaiseRunnable: Runnable? = null

    /** 正在等待"确认 chrome 真的被摘掉"的重建窗口（见 showOrUpdate）。 */
    private var pendingChromeRebuild = false
    private var pendingPickScreenshotChromeFallback: Runnable? = null

    /**
     * 触摸窗状态：空闲 WM 小块（球区/线条区）；手势在 [ACTION_DOWN] 命中后同窗扩全屏，UP 后缩回。
     * 拖拽中禁止 bringOverlayToFront / 换窗，避免丢 MOVE/UP。
     */

    private fun setDragging(dragging: Boolean) {
        val wasDragging = isDragging
        isDragging = dragging
        when {
            dragging && !wasDragging -> {
                cancelPassiveLineRestore()
                cancelDeferredGifResume()
                cancelDeferredDragStart()
                clearSplitIdleChrome()
                displayView?.visibility = View.VISIBLE
                sceneState?.ballDragging?.value = true
                settingsState?.value?.let { syncDisplayBallLayout(it) }
            }
            !dragging && wasDragging -> {
                cancelDeferredDragStart()
                ballIconView?.setDragging(false)
                scheduleDeferredGifResume()
            }
            else -> {
                sceneState?.ballDragging?.value = dragging
            }
        }
        if (!dragging) {
            sceneState?.ballCenterPx?.value = null
        }
    }

    private fun cancelDeferredGifResume() {
        deferredGifResumeRunnable?.let { mainHandler.removeCallbacks(it) }
        deferredGifResumeRunnable = null
    }

    /** Resume GIF one frame after Compose restore — spreads release CPU spike. */
    private fun scheduleDeferredGifResume() {
        cancelDeferredGifResume()
        val host = displayView ?: run {
            sceneState?.ballDragging?.value = false
            return
        }
        val runnable = Runnable {
            deferredGifResumeRunnable = null
            if (!isDragging) {
                sceneState?.ballDragging?.value = false
            }
        }
        deferredGifResumeRunnable = runnable
        host.postOnAnimation(runnable)
    }

    private fun cancelDeferredDragStart() {
        deferredDragStartGeneration++
    }

    /** 拖出当帧刷新拖拽布局与光标层。 */
    private fun applyImmediateDragStartVisual(
        deferBallWindowMutation: Boolean,
        showCursorLayers: Boolean = true
    ) {
        cancelDeferredDragStart()
        if (!isDragging) return
        ballDraggingState?.value = true
        ballIconView?.setDragging(true)
        if (!deferBallWindowMutation || !dragOriginatedFromLine) {
            flushDragChromeLayout(syncAnchorState = true)
        }
        if (showCursorLayers) {
            setCursorLayersVisible(true)
        }
        settingsState?.value?.let { updateChromeVisibility(it) }
    }

    private var dragOriginatedFromLine = false
    private var lineDragEndedWithGesture = false
    private var dragActiveSideOverrideState: MutableState<FloatBallSide?>? = null
    private var dragActiveSideOverride: FloatBallSide?
        get() = dragActiveSideOverrideState?.value
        set(value) {
            dragActiveSideOverrideState?.value = value
        }
    private var passthroughRestorePending = false
    private var committedActiveSideUntilPersist: FloatBallSide? = null

    /**
     * 圆环工具栏「移动」进入的临时移动模式：**只在内存里**，不写任何位置设置（自由位置 CUSTOM 模式存不住，
     * FloatBallPositionMode.fromStorageKey 会把 CUSTOM 读回 RIGHT）。
     * 开启时球朝屏幕中心内移一点作为提示；拖动时只跟手、不取词；松手按落点选边 + 存竖直位置并吸附回边缘。
     */
    private var moveModeActive = false
    private var moveModeExitRunnable: Runnable? = null
    private var activeSideAtDragStart: FloatBallSide? = null
    private var finishDragRequested = false
    private var dragChromeLayoutFrameScheduled = false
    private var cursorCommitFrameScheduled = false
    private var pendingPickAnchor: Offset? = null
    private var pendingCursorFrameAnchor: Offset? = null
    private var currentDragPickAnchor = Offset.Zero
    private var cursorPreviewActive = false
    private var dragScreenBounds: OverlayScreenBounds? = null
    private var boundsLookupGeneration = 0
    /** Latched after yellow pause + small finger move; stays until drag ends (FV regional mode). */
    private var regionalPickActive = false
    private val gestureHintWindow = FloatBallGestureHintWindow()
    private var currentGestureHintType: FloatBallGestureType? = null
    /** slop 内球体已跟手，超 slop 后升级为完整取词拖。 */
    private var slopPhaseBallFollowActive = false
    private var slopPhaseFromLineStrip = false

    val isShowing: Boolean get() = displayView != null

    /** Display + touch + hint WM layers must stay above panel windows for z-order. */
    fun bringChromeAbovePanels() {
        scheduleChromeAbovePanels(delayMs = 0L)
    }

    /** Called when another overlay window is added above float-ball chrome. */
    fun notifyPanelAttachedAboveChrome(edgeSide: PanelSide? = null) {
        chromeZOrderFront = false
        edgeChromeRaiseSides = when (edgeSide) {
            null -> null
            else -> (edgeChromeRaiseSides ?: emptySet()) + edgeSide
        }
        if (isDragging) {
            chromeRaiseDeferredForDrag = true
        }
        SlideIndexAccessibilityService.notifyEdgeChromeBelowPanel()
    }

    /**
     * Edge gesture ended with no content panel: presentation is gone or was tracking-only.
     * Clears deferred z-order flags without WM remove/add (avoids trigger/ball flash on tap).
     */
    fun restoreChromeZOrderIfIdle() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreChromeZOrderIfIdle() }
            return
        }
        cancelPendingChromeRaise()
        cancelPickPanelChromeRaiseDeferred()
        chromeRaiseDeferredForDrag = false
        edgeChromeRaiseSides = null
        chromeZOrderFront = true
    }

    /**
     * Defer chrome raise until deferred pick screenshot is applied (or [onPickPanelDeferredScreenshotSkipped]).
     * Avoids stacking WM remove/add with [FloatBallPickResultPanel.updatePickScreenshot].
     */
    fun scheduleChromeAbovePanelsAfterDeferredPickScreenshot() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { scheduleChromeAbovePanelsAfterDeferredPickScreenshot() }
            return
        }
        cancelPickPanelChromeRaiseDeferred()
        val fallback = Runnable {
            pendingPickScreenshotChromeFallback = null
            scheduleChromeAbovePanels(delayMs = 0L)
        }
        pendingPickScreenshotChromeFallback = fallback
        mainHandler.postDelayed(fallback, PICK_SCREENSHOT_CHROME_FALLBACK_MS)
    }

    fun onPickPanelScreenshotApplied() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onPickPanelScreenshotApplied() }
            return
        }
        cancelPickPanelChromeRaiseDeferred()
        OverlayCompositor.bringAboveContentPanels()
        scheduleChromeAbovePanels(delayMs = PICK_SCREENSHOT_CHROME_SETTLE_MS)
    }

    fun onPickPanelDeferredScreenshotSkipped() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onPickPanelDeferredScreenshotSkipped() }
            return
        }
        cancelPickPanelChromeRaiseDeferred()
        scheduleChromeAbovePanels(delayMs = PICK_SCREENSHOT_CHROME_SETTLE_MS)
    }

    fun cancelPickPanelChromeRaiseDeferred() {
        pendingPickScreenshotChromeFallback?.let { mainHandler.removeCallbacks(it) }
        pendingPickScreenshotChromeFallback = null
    }

    /**
     * Forcefully re-adds FloatBall overlays to WindowManager top z-order to guarantee visibility above panels.
     */
    fun bringChromeAbovePanelsForce() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { bringChromeAbovePanelsForce() }
            return
        }
        chromeZOrderFront = false
        bringChromeAbovePanelsNow(forceExplicitReAdd = true)
    }

    /**
     * Coalesces chrome z-order work and defers it past panel enter animations when possible.
     * Uses [WindowManager.updateViewLayout] when chrome is already on top; otherwise re-adds views.
     */
    fun scheduleChromeAbovePanels(delayMs: Long = CHROME_RAISE_DEFER_MS) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { scheduleChromeAbovePanels(delayMs) }
            return
        }
        if (captureSuppressed) return
        if (OverlaySceneController.isEdgeGestureActive()) return
        if (isDragging) {
            if (!chromeZOrderFront) {
                chromeRaiseDeferredForDrag = true
            }
            return
        }
        pendingChromeRaiseRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingChromeRaiseRunnable = null
            bringChromeAbovePanelsNow(forceExplicitReAdd = false)
        }
        pendingChromeRaiseRunnable = runnable
        if (delayMs <= 0L) {
            mainHandler.post(runnable)
        } else {
            mainHandler.postDelayed(runnable, delayMs)
        }
    }

    private fun bringChromeAbovePanelsNow(forceExplicitReAdd: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { bringChromeAbovePanelsNow(forceExplicitReAdd) }
            return
        }
        if (OverlaySceneController.isEdgeGestureActive()) return
        if (passthroughRestorePending) return
        if (captureSuppressed) return
        if (isDragging) {
            if (!chromeZOrderFront) {
                chromeRaiseDeferredForDrag = true
            }
            return
        }
        val forceReAdd = forceExplicitReAdd || !chromeZOrderFront
        val edgeSides = edgeChromeRaiseSides
        edgeChromeRaiseSides = null
        settingsState?.value?.let { settings ->
            recoverIdleTouchCaptureLayouts(settings)
            val touchEnabled = !passthroughRestorePending && !captureSuppressed
            val splitIdle = shouldUseSplitIdleChrome(settings)
            if (touchEnabled) {
                if (!splitIdle) {
                    val display = displayView
                    val displayLp = displayLayoutParams
                    if (display != null && displayLp != null) {
                        bringOverlayToFront(display, displayLp, forceReAdd = forceReAdd)
                    }
                }
                val lineTouch = lineTouchHost
                val lineTouchLp = lineTouchLayoutParams
                if (lineTouch != null && lineTouchLp != null && lineTouch.isAttachedToWindow && lineTouch.isVisible) {
                    bringOverlayToFront(lineTouch, lineTouchLp, forceReAdd = forceReAdd)
                }
                val touch = touchHost
                val touchLp = touchLayoutParams
                if (touch != null && touchLp != null) {
                    bringOverlayToFront(touch, touchLp, forceReAdd = forceReAdd)
                }
            }
        }
        gestureHintWindow.bringToFront()
        SlideIndexAccessibilityService.bringEdgeChromeAbovePanels(forceReAdd = forceReAdd, sides = edgeSides)
        chromeZOrderFront = true
    }

    /** Run deferred z-order raise one frame after drag-end layout restore. */
    private fun flushDeferredChromeRaiseIfNeeded() {
        val needsRaise = chromeRaiseDeferredForDrag || !chromeZOrderFront
        chromeRaiseDeferredForDrag = false
        if (!needsRaise) return
        val host = displayView
        if (host != null) {
            host.postOnAnimation { scheduleChromeAbovePanels(delayMs = 0L) }
        } else {
            scheduleChromeAbovePanels(delayMs = 0L)
        }
    }

    private fun cancelPendingChromeRaise() {
        pendingChromeRaiseRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingChromeRaiseRunnable = null
    }

    fun setStripZonePreviewActive(active: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setStripZonePreviewActive(active) }
            return
        }
        stripZonePreviewState?.value = active
    }

    fun previewPositionYFraction(fraction: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { previewPositionYFraction(fraction) }
            return
        }
        val state = settingsState ?: return
        // 新的拖动接管，之前那次"待确认"的值作废。
        cancelPendingPositionY()
        if (positionYPreviewRestore == null) {
            positionYPreviewRestore = state.value.floatBallPositionYFraction
        }
        val coerced = FloatBallLayout.coercePositionYFraction(fraction)
        val updated = state.value.let { it.copy(floatBall = it.floatBall.copy(floatBallPositionYFraction = coerced)) }
        state.value = updated
        applyAllLayouts(updated)
    }

    fun endPositionYPreview(restoreIfNeeded: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { endPositionYPreview(restoreIfNeeded) }
            return
        }
        val baseline = positionYPreviewRestore
        positionYPreviewRestore = null
        val state = settingsState ?: return
        if (!restoreIfNeeded || baseline == null) {
            // 提交：保留当前预览值，等设置回流到同一个值再放手。
            pendingPositionYFraction = state.value.floatBallPositionYFraction
            mainHandler.removeCallbacks(clearPendingPositionYRunnable)
            mainHandler.postDelayed(clearPendingPositionYRunnable, PENDING_POSITION_Y_TIMEOUT_MS)
            return
        }
        cancelPendingPositionY()
        val restored = state.value.let { it.copy(floatBall = it.floatBall.copy(floatBallPositionYFraction = baseline)) }
        state.value = restored
        applyAllLayouts(restored)
    }

    fun clearPositionYPreviewRestore() {
        positionYPreviewRestore = null
        cancelPendingPositionY()
    }

    private fun cancelPendingPositionY() {
        pendingPositionYFraction = null
        mainHandler.removeCallbacks(clearPendingPositionYRunnable)
    }

    fun previewAppearance(
        sizeDp: Float? = null,
        opacity: Float? = null,
        visibleFraction: Float? = null,
        lineHeightFraction: Float? = null,
        lineWidthFraction: Float? = null,
        lineOpacity: Float? = null
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                previewAppearance(
                    sizeDp,
                    opacity,
                    visibleFraction,
                    lineHeightFraction,
                    lineWidthFraction,
                    lineOpacity
                )
            }
            return
        }
        val state = settingsState ?: return
        if (appearancePreviewRestore == null) {
            val current = state.value
            appearancePreviewRestore = FloatBallAppearancePreviewSnapshot(
                sizeDp = current.floatBallSizeDp,
                opacity = current.floatBallOpacity,
                visibleFraction = current.floatBallVisibleFraction,
                lineHeightFraction = current.floatBallLineHeightFraction,
                lineWidthFraction = current.floatBallLineWidthFraction,
                lineOpacity = current.floatBallLineOpacity
            )
        }
        val current = state.value
        val updated = current.copy(
            floatBall = current.floatBall.copy(
                floatBallSizeDp = sizeDp?.coerceIn(36f, 96f) ?: current.floatBallSizeDp,
                floatBallOpacity = opacity?.coerceIn(0f, 1f) ?: current.floatBallOpacity,
                floatBallVisibleFraction = visibleFraction?.let(FloatBallLayout::coerceVisibleFraction)
                    ?: current.floatBallVisibleFraction,
                floatBallLineHeightFraction = lineHeightFraction?.coerceIn(0.04f, 0.4f)
                    ?: current.floatBallLineHeightFraction,
                floatBallLineWidthFraction = lineWidthFraction?.coerceIn(0.01f, 0.50f)
                    ?: current.floatBallLineWidthFraction,
                floatBallLineOpacity = lineOpacity?.coerceIn(0f, 1f) ?: current.floatBallLineOpacity
            )
        )
        state.value = updated
        invalidateChrome()
        val layoutChanged = sizeDp != null || visibleFraction != null ||
            lineHeightFraction != null || lineWidthFraction != null
        if (layoutChanged) {
            applyAllLayouts(updated)
            syncTouchWindowLayout(updated)
        }
    }

    fun endAppearancePreview(restoreIfNeeded: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { endAppearancePreview(restoreIfNeeded) }
            return
        }
        val baseline = appearancePreviewRestore
        appearancePreviewRestore = null
        if (!restoreIfNeeded || baseline == null) return
        val state = settingsState ?: return
        val restored = state.value.let { current ->
            current.copy(
                floatBall = current.floatBall.copy(
                    floatBallSizeDp = baseline.sizeDp,
                    floatBallOpacity = baseline.opacity,
                    floatBallVisibleFraction = baseline.visibleFraction,
                    floatBallLineHeightFraction = baseline.lineHeightFraction,
                    floatBallLineWidthFraction = baseline.lineWidthFraction,
                    floatBallLineOpacity = baseline.lineOpacity
                )
            )
        }
        state.value = restored
        invalidateChrome()
        bumpScreenLayoutGeneration()
        applyAllLayouts(restored)
        syncTouchWindowLayout(restored)
    }

    private fun invalidateChrome() {
        ballIconView?.invalidate()
        displayView?.invalidate()
    }

    fun clearAppearancePreviewRestore() {
        appearancePreviewRestore = null
    }

    fun refreshStyleVisual() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshStyleVisual() }
            return
        }
        styleVisualGenerationState?.let { state ->
            state.intValue = state.intValue + 1
        }
        ballIconView?.invalidate()
        settingsState?.value?.let { applyAllLayouts(it) }
    }

    /**
     * 圆环启动器工具栏「移动」按钮。
     *
     * 交互（与用户约定）：
     * 1. 进入**临时移动模式**（只在内存，不写任何位置设置——自由位置 CUSTOM 模式存不住，
     *    `FloatBallPositionMode.fromStorageKey` 会把 CUSTOM 读回 RIGHT）；
     * 2. 球朝屏幕中心方向**内移约 14dp** 作为"已进入移动模式"的视觉提示；
     * 3. 这期间拖动球只跟手，**不取词**（不弹光标/放大镜/取词面板）；
     * 4. 松手时按落点选边（左半屏→左，右半屏→右）+ 保存竖直位置，球**吸附回边缘**；
     * 5. 若一直没拖，几秒后自动复位。
     */
    fun enterFreeMoveMode() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { enterFreeMoveMode() }
            return
        }
        val hostContext = appContext ?: return
        val settings = settingsState?.value ?: return
        val metrics = displayView?.resources?.displayMetrics ?: return
        Toast.makeText(hostContext, R.string.float_ball_free_move_hint, Toast.LENGTH_SHORT).show()
        moveModeActive = true
        // 关键：必须让检测器也知道，否则拖球会被当成立式手势（抬手走 onPickCancel，球被复位）。
        touchHost?.setMoveMode(true)
        scheduleMoveModeTimeout()
        // 内移提示：用 sceneState.ballCenterPx 当"当前球心覆盖值"，后续布局都会沿用它，
        // 直到 applyBallLayout 把它清空（回到正常停靠位）。
        val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(hostContext, windowManager)
        val activeSide = effectiveActiveSide(settings)
        val (centerX, centerY) =
            FloatBallLayout.ballCenterPx(settings, metrics, activeSide, screenWidthPx, screenHeightPx)
        val nudge = MOVE_MODE_HINT_DP * metrics.density
        val inward = if (centerX < screenWidthPx / 2f) nudge else -nudge
        sceneState?.ballCenterPx?.value = Offset(centerX + inward, centerY)
        syncDisplayBallLayout(settings)
    }

    /** 退出移动模式并让球回到正常停靠布局（没拖就超时复位也走这里）。 */
    private fun exitMoveMode() {
        if (!moveModeActive) return
        moveModeActive = false
        touchHost?.setMoveMode(false)
        moveModeExitRunnable?.let { mainHandler.removeCallbacks(it) }
        moveModeExitRunnable = null
        sceneState?.ballCenterPx?.value = null
        settingsState?.value?.let { applyBallLayout(it) }
    }

    private fun scheduleMoveModeTimeout() {
        moveModeExitRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { exitMoveMode() }
        moveModeExitRunnable = runnable
        mainHandler.postDelayed(runnable, MOVE_MODE_TIMEOUT_MS)
    }

    /**
     * 移动模式下落手：按落点选边 + 存竖直位置，球吸附回边缘。
     *
     * 先做**乐观更新**（立刻吸附，不等数据库回程），再异步写设置。
     */
    private fun finishMoveModeRelease(settings: AppSettings) {
        val view = displayView ?: return
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(view.context, windowManager)
        val ballSize = FloatBallLayout.ballSize(settings, density)
        val center = dragSession.clampedBallCenter(
            ballSize = ballSize,
            marginPx = FloatBallLayout.marginPx(density),
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx
        )
        val side = if (center.x < screenWidthPx / 2f) FloatBallSide.LEFT else FloatBallSide.RIGHT
        val yFraction = FloatBallLayout.coercePositionYFraction(center.y / screenHeightPx)

        moveModeActive = false
        touchHost?.setMoveMode(false)
        moveModeExitRunnable?.let { mainHandler.removeCallbacks(it) }
        moveModeExitRunnable = null
        sceneState?.ballCenterPx?.value = null

        val usesEdgeLines = FloatBallLayout.usesEdgeLines(settings)
        if (usesEdgeLines) {
            // 双边/双线模式：落点决定"当前活动侧"（仓库里已有机制，applyActiveSide 会立刻吸附并落盘）。
            applyActiveSide(side)
        }
        val targetMode = if (side == FloatBallSide.LEFT) {
            FloatBallPositionMode.LEFT
        } else {
            FloatBallPositionMode.RIGHT
        }
        val updated = if (!usesEdgeLines && settings.floatBallPositionMode != targetMode) {
            settings.copy(
                floatBall = settings.floatBall.copy(
                    floatBallPositionMode = targetMode,
                    floatBallPositionYFraction = yFraction
                )
            )
        } else {
            settings.copy(
                floatBall = settings.floatBall.copy(floatBallPositionYFraction = yFraction)
            )
        }
        settingsState?.value = updated
        applyBallLayout(updated)

        val hostContext = appContext ?: return
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext) ?: return
        overlayScope.launch {
            deps.settingsRepository.setFloatBallPositionYFraction(yFraction)
            if (usesEdgeLines) {
                deps.settingsRepository.setFloatBallActiveSide(side)
            } else if (settings.floatBallPositionMode != targetMode) {
                deps.settingsRepository.setFloatBallPositionMode(targetMode)
            }
        }
    }

    /** 应用内语言切换后重建悬浮球窗口，使 Compose/资源使用新 Locale。 */
    fun recreateForLocaleChange() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { recreateForLocaleChange() }
            return
        }
        if (!isShowing) return
        val hostContext = OverlayDependencyAccess.overlayHostContext() ?: return
        val settings = settingsState?.value ?: return
        val persistPosition = onPositionPersisted ?: return
        val persistSide = onActiveSidePersisted ?: {}
        dismiss()
        showOrUpdate(hostContext, settings, persistPosition, persistSide)
    }

    fun showOrUpdate(
        context: Context,
        settings: AppSettings,
        onPositionPersisted: (xFraction: Float, yFraction: Float) -> Unit,
        onActiveSidePersisted: (FloatBallSide) -> Unit = {}
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showOrUpdate(context, settings, onPositionPersisted, onActiveSidePersisted) }
            return
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            dismiss()
            return
        }
        if (RegionalPickOverlay.isActive) {
            settingsState?.value = settings
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext()
            ?: run {
                Log.w(TAG, "accessibility service not connected")
                return
            }

        this.onPositionPersisted = onPositionPersisted
        this.onActiveSidePersisted = onActiveSidePersisted
        if (isShowing && !areChromeWindowsAttached()) {
            // 熄屏/锁屏后系统可能摘掉 TYPE_ACCESSIBILITY_OVERLAY，本地引用仍在。
            // 先清理再重建，避免 isShowing=true 却永远不 ensureWindows。
            //
            // 真机修复：窗口过渡瞬间（例如面板出现 / 前台包名变化）isAttachedToWindow 会短暂为 false，
            // 但窗口其实还在 WM 里。此处若立刻 dismiss()+ensureWindows()，就是把悬浮球拆掉又装回 ——
            // 用户看到"每次触发面板，悬浮球闪一下"。logcat 实测调用栈：
            //   EdgeOverlayHost.refreshOverlaySuppression ← onForegroundPackageChanged
            //     ← updateForegroundPackage ← SlideIndexAccessibilityForegroundTracker
            // 因此先延后确认，仍然没挂上才真正重建。
            if (!pendingChromeRebuild) {
                pendingChromeRebuild = true
                mainHandler.postDelayed({
                    pendingChromeRebuild = false
                    if (isShowing && !areChromeWindowsAttached()) {
                        val persistPosition = onPositionPersisted
                        val persistSide = onActiveSidePersisted
                        dismiss()
                        this.onPositionPersisted = persistPosition
                        this.onActiveSidePersisted = persistSide
                        ensureWindows(hostContext, settings)
                    }
                }, CHROME_REBUILD_CONFIRM_MS)
            }
        } else if (!isShowing) {
            ensureWindows(hostContext, settings)
        } else {
            val incoming = settings
            val pendingSide = committedActiveSideUntilPersist
            if (pendingSide != null && incoming.floatBallActiveSide != pendingSide) {
                settingsState?.value =
                    incoming.copy(floatBall = incoming.floatBall.copy(floatBallActiveSide = pendingSide))
                return
            }
            if (pendingSide != null && incoming.floatBallActiveSide == pendingSide) {
                committedActiveSideUntilPersist = null
            }
            val current = settingsState?.value
            val merged = when {
                appearancePreviewRestore != null && current != null -> incoming.copy(
                    floatBall = incoming.floatBall.copy(
                        floatBallSizeDp = current.floatBallSizeDp,
                        floatBallOpacity = current.floatBallOpacity,
                        floatBallVisibleFraction = current.floatBallVisibleFraction,
                        floatBallLineHeightFraction = current.floatBallLineHeightFraction,
                        floatBallLineWidthFraction = current.floatBallLineWidthFraction,
                        floatBallLineOpacity = current.floatBallLineOpacity
                    )
                )
                // 拖动期：任何回流（别的设置写入、本次提交前后的中间态）都不许把 Y 拉回旧值。
                positionYPreviewRestore != null && current != null -> incoming.copy(
                    floatBall = incoming.floatBall.copy(
                        floatBallPositionYFraction = current.floatBallPositionYFraction
                    )
                )
                // 提交后、落盘回流前：继续顶住预览值，等 incoming 追上同一个值就放手。
                pendingPositionYFraction != null && current != null -> {
                    val pending = pendingPositionYFraction!!
                    if (kotlin.math.abs(incoming.floatBallPositionYFraction - pending) <= POSITION_Y_EPSILON) {
                        cancelPendingPositionY()
                        incoming
                    } else {
                        incoming.copy(
                            floatBall = incoming.floatBall.copy(
                                floatBallPositionYFraction = current.floatBallPositionYFraction
                            )
                        )
                    }
                }
                isDragging &&
                    current != null &&
                    incoming.floatBallActiveSide != current.floatBallActiveSide -> incoming.copy(
                    floatBall = incoming.floatBall.copy(floatBallActiveSide = current.floatBallActiveSide)
                )
                else -> incoming
            }
            settingsState?.value = merged
            if (floatBallStyleSignature(current) != floatBallStyleSignature(merged)) {
                refreshStyleVisual()
            }
            touchHost?.updateSettings(merged)
            lineTouchHost?.updateSettings(merged)
            if (!isDragging) {
                recoverIdleTouchCaptureLayouts(merged)
                restorePassiveOverlayLayout(merged)
            }
        }
    }

    fun dismiss(destroyPickPanelShell: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss(destroyPickPanelShell) }
            return
        }
        cancelPassiveLineRestore()
        cancelDeferredGifResume()
        cancelDeferredDragStart()
        moveModeActive = false
        touchHost?.setMoveMode(false)
        moveModeExitRunnable?.let { mainHandler.removeCallbacks(it) }
        moveModeExitRunnable = null
        hideCursor(restorePassive = false)
        ballIconView?.release()
        cancelPauseTimer()
        cancelPendingChromeRaise()
        val wm = windowManager
        displayView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        touchHost?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        lineTouchHost?.let { view ->
            wm?.let {
                runCatching {
                    if (view.isAttachedToWindow) {
                        it.removeView(view)
                    }
                }
            }
        }
        destroySplitIdleChrome()
        gestureHintWindow.detach()
        displayView?.dispose()
        displayOwner?.destroy()
        if (FloatBallPickResultPanel.isShowing) {
            Log.i(TAG, "dismiss: skip pick result panel destroy while panel is showing")
        } else if (destroyPickPanelShell) {
            FloatBallPickResultPanel.destroy()
        }
        displayOwner = null
        displayView = null
        displayLayoutParams = null
        touchHost = null
        touchLayoutParams = null
        lineTouchHost = null
        lineTouchLayoutParams = null
        ballIconView = null
        cursorPreviewView = null
        windowManager = null
        sceneState = null
        dragActiveSideOverrideState = null
        cancelPendingPositionY()
        onPositionPersisted = null
        onActiveSidePersisted = null
        screenOffDismissReceiver.unregister()
        appContext = null
        setDragging(false)
        dragOriginatedFromLine = false
        overlayScope.cancel()
        overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lineDragEndedWithGesture = false
        dragActiveSideOverride = null
        committedActiveSideUntilPersist = null
        activeSideAtDragStart = null
        cancelDragChromeLayoutFrame()
        cancelCursorCommitFrame()
        dragSession.reset()
        currentGestureHintType = null
        chromeZOrderFront = true
        cancelPendingChromeRaise()
        captureSuppressed = false
        chromeDetachedForCapture = false
    }

    fun relayout() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { relayout() }
            return
        }
        if (captureSuppressed) return
        settingsState?.value ?: return
        val view = displayView
        if (view != null && view.isAttachedToWindow) {
            view.post { relayoutNow() }
        } else {
            relayoutNow()
        }
    }

    fun onKeyboardImeChanged() {
        if (!isShowing || isDragging) return
        relayout()
    }

    private fun relayoutNow() {
        if (captureSuppressed) return
        val currentSettings = settingsState?.value ?: return
        if (isDragging) {
            val host = displayView ?: return
            dragScreenBounds = FloatBallScreenMetrics.bounds(host.context, windowManager)
            dragSession.refreshPointerTravel(
                settings = currentSettings,
                screenWidth = dragScreenBounds!!.width,
                screenHeight = dragScreenBounds!!.height
            )
            updatePickAndBallFromFinger(moveBallWindow = true)
        } else {
            dragScreenBounds = null
            recoverIdleTouchCaptureLayouts(currentSettings)
            applyAllLayouts(currentSettings)
        }
        bumpScreenLayoutGeneration()
    }

    private fun bumpScreenLayoutGeneration() {
        sceneState?.screenLayoutGeneration?.let { state ->
            state.intValue = state.intValue + 1
        }
    }

    fun suppressForScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suppressForScreenshotCapture() }
            return
        }
        if (captureSuppressed) return
        captureSuppressed = true
        sceneState?.chromeVisible?.value = false
        sceneState?.ballVisible?.value = false
        sceneState?.lineVisible?.value = false
        clearSplitIdleChrome()
        hideGestureHintWindow()
        hideCursor()
        detachChromeWindowsForCapture()
    }

    fun suppressChromeForRegionalPick() = suppressForScreenshotCapture()

    /** Hide float-ball chrome during edge regional pick without WM layout churn. */
    fun hideChromeForEdgeRegionalPick() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideChromeForEdgeRegionalPick() }
            return
        }
        if (sceneState == null) return
        captureSuppressed = true
        sceneState?.chromeVisible?.value = false
        sceneState?.ballVisible?.value = false
        sceneState?.lineVisible?.value = false
        dragActiveSideOverride = null
        clearSplitIdleChrome()
        hideGestureHintWindow()
        hideCursor()
    }

    fun restoreAfterScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAfterScreenshotCapture() }
            return
        }
        if (!captureSuppressed) return
        captureSuppressed = false
        reattachChromeWindowsAfterCapture()
        val settings = settingsState?.value
        if (settings != null) {
            updateChromeVisibility(settings)
            syncSplitIdleChrome(settings)
        } else {
            sceneState?.chromeVisible?.value = true
        }
    }

    fun restoreChromeAfterRegionalPick() = restoreAfterScreenshotCapture()

    fun hideChromeForRingLauncher() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideChromeForRingLauncher() }
            return
        }
        if (sceneState == null) return
        captureSuppressed = true
        sceneState?.chromeVisible?.value = false
        sceneState?.ballVisible?.value = false
        sceneState?.lineVisible?.value = false
        dragActiveSideOverride = null
        clearSplitIdleChrome()
        hideGestureHintWindow()
        cancelCursorPickPreview()
        displayView?.visibility = View.GONE
        if (!isLauncherCaptureActive()) {
            suppressTouchHostsForLauncherOverlay()
        }
    }

    fun restoreChromeAfterRingLauncher() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreChromeAfterRingLauncher() }
            return
        }
        displayView?.visibility = View.VISIBLE
        touchHost?.visibility = View.VISIBLE
        lineTouchHost?.visibility = View.VISIBLE
        restoreChromeAfterRegionalPick()
        settingsState?.value?.let { recoverIdleTouchCaptureLayouts(it) }
    }

    /** 圆环/蜂窝展示期间强制收起悬浮球触摸层（供启动器在抬手后二次确认）。 */
    fun suppressTouchHostsForActiveLauncherOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suppressTouchHostsForActiveLauncherOverlay() }
            return
        }
        suppressTouchHostsForLauncherOverlay()
    }

    fun ballCenterForRingLauncher(settings: AppSettings): Pair<Float, Float>? {
        val view = displayView ?: return null
        val metrics = view.resources.displayMetrics
        val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(view.context, windowManager)
        val activeSide = effectiveActiveSide(settings)
        val (centerX, centerY) = FloatBallLayout.ballCenterPx(
            settings = settings,
            metrics = metrics,
            activeSide = activeSide,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
        return centerX to centerY
    }

    /** 与悬浮球 Compose 窗体一致的布局密度与屏幕宽（圆环启动器须同源，避免 applicationContext 密度偏低）。 */
    fun overlayLayoutMetrics(settings: AppSettings? = null): Pair<Float, Float>? {
        val view = displayView ?: return null
        val wm = windowManager
        val viewMetrics = view.resources.displayMetrics
        val ballDensityHint = settings?.let { ballLayoutDensityHint(it, viewMetrics.density) }
        val layoutMetrics = OverlayDisplayMetrics.resolve(
            context = view.context,
            windowManager = wm,
            densityHint = ballDensityHint ?: viewMetrics.density
        )
        val screenWidth = OverlayDisplayMetrics.screenWidthPx(view.context, wm, layoutMetrics)
        return layoutMetrics.density to screenWidth
    }

    private fun ballLayoutDensityHint(settings: AppSettings, viewDensity: Float): Float {
        // 用**高度**反推密度：不处理模式下宽度会按原图比例撑开，拿宽度除以 dp 值会得到一个错误的密度。
        val ballPx = FloatBallLayout.ballSize(settings, viewDensity).heightPx.toFloat()
        return ballPx / settings.floatBallSizeDp.coerceIn(36f, 96f)
    }

    private fun detachChromeWindowsForCapture() {
        if (chromeDetachedForCapture) return
        val wm = windowManager ?: return
        displayView?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching { wm.removeViewImmediate(view) }
            }
        }
        touchHost?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching { wm.removeViewImmediate(view) }
            }
        }
        lineTouchHost?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching { wm.removeViewImmediate(view) }
            }
        }
        chromeDetachedForCapture = true
    }

    private fun reattachChromeWindowsAfterCapture() {
        if (!chromeDetachedForCapture) return
        val wm = windowManager ?: return
        val display = displayView
        val touch = touchHost
        val lineTouch = lineTouchHost
        val displayLp = displayLayoutParams
        val touchLp = touchLayoutParams
        val lineTouchLp = lineTouchLayoutParams
        if (display != null && displayLp != null && !display.isAttachedToWindow) {
            runCatching { wm.addView(display, displayLp) }
        }
        if (touch != null && touchLp != null && !touch.isAttachedToWindow) {
            runCatching { wm.addView(touch, touchLp) }
        }
        if (lineTouch != null && lineTouchLp != null && !lineTouch.isAttachedToWindow) {
            runCatching { wm.addView(lineTouch, lineTouchLp) }
        }
        chromeDetachedForCapture = false
    }

    /** Ends drag UI when the screen turns off; chrome windows stay attached. */
    fun onScreenOff() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onScreenOff() }
            return
        }
        hideCursor()
    }

    private fun ensureWindows(hostContext: Context, settings: AppSettings) {
        // ⚠️ 不要提前 warmUp 挂载处于 View.GONE 的面板窗口到 WindowManager：
        // 挂在无障碍 WindowToken 上的 View.GONE 窗口没有 Surface，在深色/浅色模式切换时 ViewRootImpl 跳过绘制且不汇报
        // reportDrawFinished，导致系统的 BLASTSyncEngine 阻塞整机转场整整 2 秒。面板在用户首次呼出时按需挂载即可。
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val overlayContext = OverlayCompose.themedContext(hostContext)
        val state = FloatBallSceneState(settings)
        sceneState = state
        dragActiveSideOverrideState = mutableStateOf(null)

        val cursorPreview = FloatBallCursorPreviewView(overlayContext).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }
        val ballIcon = FloatBallIconView(overlayContext)

        val dragCallbacks = object {
            fun onGestureHint(gestureType: FloatBallGestureType?) {
                currentGestureHintType = gestureType
                updateGestureHintWindow()
            }

            fun onPreviewStart(screenX: Float, screenY: Float) {
                showCursorPickPreview(screenX, screenY)
            }

            fun onPreviewProgress(progress: Float) {
                if (!slopPhaseBallFollowActive) {
                    updateCursorPickPreviewAlpha(progress)
                }
            }

            fun onPreviewMove(
                touchDownX: Float,
                touchDownY: Float,
                fingerX: Float,
                fingerY: Float,
                fromLineStrip: Boolean
            ) {
                updateSlopPhaseBallFollow(
                    fromLineStrip = fromLineStrip,
                    touchDownX = touchDownX,
                    touchDownY = touchDownY,
                    fingerX = fingerX,
                    fingerY = fingerY
                )
            }

            fun onPreviewCancel() {
                cancelSlopPhaseBallFollow()
                cancelCursorPickPreview()
            }

            fun onStart(touchDownX: Float, touchDownY: Float, fingerX: Float, fingerY: Float) {
                if (!slopPhaseBallFollowActive) {
                    activeSideAtDragStart = null
                    dragOriginatedFromLine = false
                    lineDragEndedWithGesture = false
                    dragActiveSideOverride = null
                }
                expandBallTouchCapture()
                showCursorAtScreenTouch(
                    touchDownX = touchDownX,
                    touchDownY = touchDownY,
                    fingerX = fingerX,
                    fingerY = fingerY,
                    deferBallWindowMutation = true
                )
            }

            fun onDrag(fingerX: Float, fingerY: Float) {
                onFingerDrag(fingerX, fingerY)
                onDragMoved()
            }

            fun onEnd() {
                if (dragOriginatedFromLine) return
                completeDragGesture()
            }

            fun onCancel() {
                if (dragOriginatedFromLine) return
                activeSideAtDragStart = null
                dragOriginatedFromLine = false
                lineDragEndedWithGesture = false
                dragActiveSideOverride = null
                hideCursor()
            }
        }

        val displayDialogOwner = OverlayComposeOwner()
        val displayHost = FloatBallDisplayHost(
            context = overlayContext,
            lineChromeOwner = displayDialogOwner,
            ballIconView = ballIcon,
            cursorPreviewView = cursorPreview
        ).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            lineChromeComposeView.setContent {
                FloatBallLineChrome(
                    sceneState = state,
                    dragActiveSideOverrideState = dragActiveSideOverrideState!!
                )
            }
        }

        val touchLayout = FloatBallTouchHostLayout(
            context = overlayContext,
            sceneState = state,
            settingsProvider = { state.settingsState.value },
            activeSideProvider = { effectiveActiveSide(state.settingsState.value) },
            screenSizeProvider = { FloatBallScreenMetrics.sizePx(overlayContext, wm) }
        ).apply {
            updateSettings(settings)
            bindBallCallbacks(
                onDragStart = { touchDownX, touchDownY, fingerX, fingerY ->
                    dragCallbacks.onStart(touchDownX, touchDownY, fingerX, fingerY)
                },
                onDrag = { fingerX, fingerY -> dragCallbacks.onDrag(fingerX, fingerY) },
                onDragEnd = { dragCallbacks.onEnd() },
                onDragCancel = { dragCallbacks.onCancel() },
                onGesture = { gestureType, rawX, rawY, touchDownX, touchDownY ->
                    hideGestureHintWindow()
                    // 移动模式下绝不执行悬浮球手势（含长按/双击）：这次拖动只是挪球。
                    if (!moveModeActive) {
                        performFloatBallGesture(
                            state.settingsState.value,
                            gestureType,
                            rawX,
                            rawY,
                            touchDownRawX = touchDownX,
                            touchDownRawY = touchDownY
                        )
                    }
                },
                onGestureHint = { hint ->
                    if (moveModeActive) {
                        hideGestureHintWindow()
                    } else {
                        dragCallbacks.onGestureHint(hint)
                    }
                },
                onPickPreviewStart = dragCallbacks::onPreviewStart,
                onPickPreviewProgress = dragCallbacks::onPreviewProgress,
                onPickPreviewMove = { touchDownX, touchDownY, fingerX, fingerY ->
                    dragCallbacks.onPreviewMove(touchDownX, touchDownY, fingerX, fingerY, fromLineStrip = false)
                },
                onPickPreviewCancel = dragCallbacks::onPreviewCancel,
                onLauncherCaptureMove = { x, y ->
                    handleFloatBallLauncherCaptureMove(x, y)
                },
                onLauncherCaptureUp = { x, y ->
                    handleFloatBallLauncherCaptureUp(
                        rawX = x,
                        rawY = y,
                        fromLineStrip = false,
                        settings = state.settingsState.value
                    )
                },
                // FV「自定义手势」：只有库里存在自定义手势且画的是曲线时才介入（见 handleFloatBallCustomGesture）。
                onCustomGestureResolve = { path, rawX, rawY, touchDownX, touchDownY ->
                    handleFloatBallCustomGesture(
                        settings = state.settingsState.value,
                        path = path,
                        rawX = rawX,
                        rawY = rawY,
                        touchDownRawX = touchDownX,
                        touchDownRawY = touchDownY
                    )
                }
            )
        }

        val lineTouchLayout = FloatBallStripHost(
            context = overlayContext,
            sceneState = state,
            settingsProvider = { state.settingsState.value },
            activeSideProvider = { effectiveActiveSide(state.settingsState.value) },
            screenSizeProvider = { FloatBallScreenMetrics.sizePx(overlayContext, wm) }
        ).apply {
            updateSettings(settings)
            bindDragCallbacks(
                onDragStart = { touchDownX, touchDownY, fingerX, fingerY ->
                    prepareLineDrag(touchDownX, touchDownY, fingerX, fingerY)
                },
                onDrag = { fingerX, fingerY ->
                    onFingerDrag(fingerX, fingerY)
                    onDragMoved()
                },
                onDragEnd = {
                    // 与球侧一致：换边在 finishDrag 里于取词提交之后执行。
                    completeDragGesture()
                },
                onDragCancel = {
                    when {
                        lineDragEndedWithGesture -> {
                            lineDragEndedWithGesture = false
                            commitLineDragSideSwap()
                            cancelDragWithoutPick()
                        }
                        hasPickPauseIntentForCommit() -> completeDragGesture()
                        else -> {
                            revertLineDragSideSwapIfNeeded()
                            cancelDragWithoutPick()
                        }
                    }
                },
                onGesture = { gestureType, rawX, rawY, touchDownX, touchDownY ->
                    lineDragEndedWithGesture = true
                    hideGestureHintWindow()
                    performFloatBallGesture(
                        state.settingsState.value,
                        gestureType,
                        rawX,
                        rawY,
                        fromLineStrip = true,
                        touchDownRawX = touchDownX,
                        touchDownRawY = touchDownY
                    )
                },
                onGestureHint = dragCallbacks::onGestureHint,
                onPickPreviewStart = dragCallbacks::onPreviewStart,
                onPickPreviewProgress = dragCallbacks::onPreviewProgress,
                onPickPreviewMove = { touchDownX, touchDownY, fingerX, fingerY ->
                    dragCallbacks.onPreviewMove(touchDownX, touchDownY, fingerX, fingerY, fromLineStrip = true)
                },
                onPickPreviewCancel = dragCallbacks::onPreviewCancel,
                onLauncherCaptureMove = { x, y ->
                    handleFloatBallLauncherCaptureMove(x, y)
                },
                onLauncherCaptureUp = { x, y ->
                    handleFloatBallLauncherCaptureUp(
                        rawX = x,
                        rawY = y,
                        fromLineStrip = true,
                        settings = state.settingsState.value
                    )
                },
                // 边缘线条（触钮）与球共用同一套 FV 自定义手势判定。
                // 必须带 fromLineStrip=true：线条恒在球停靠侧的对侧，面板侧别再靠它换算。
                onCustomGestureResolve = { path, rawX, rawY, touchDownX, touchDownY ->
                    handleFloatBallCustomGesture(
                        settings = state.settingsState.value,
                        path = path,
                        rawX = rawX,
                        rawY = rawY,
                        touchDownRawX = touchDownX,
                        touchDownRawY = touchDownY,
                        fromLineStrip = true
                    )
                }
            )
        }

        val displayLp = buildDisplayLayoutParams(hostContext)
        val touchLp = buildTouchLayoutParams(hostContext)
        val lineTouchLp = buildTouchLayoutParams(hostContext)

        val displayAdded = runCatching { wm.addView(displayHost, displayLp) }
            .onFailure { Log.e(TAG, "failed to add display overlay", it) }
            .isSuccess
        if (!displayAdded) {
            displayDialogOwner.destroy()
            return
        }

        val touchAdded = runCatching { wm.addView(touchLayout, touchLp) }
            .onFailure { Log.e(TAG, "failed to add ball touch overlay", it) }
            .isSuccess
        if (!touchAdded) {
            runCatching { wm.removeView(displayHost) }
            displayDialogOwner.destroy()
            return
        }

        // ⚠️ 不要无条件将 lineTouchLayout addView 到 WindowManager：
        // 若当前未启用边缘线条，挂一个 View.GONE 且无 Surface 的无障碍窗口会导致系统深色模式/浅色模式
        // 切换时 BLASTSyncEngine 无法收到 draw 完成信号，阻塞整机转场整整 2 秒。
        // 线条触摸窗仅在 setLineTouchHostEnabled(true) 时按需挂载。

        windowManager = wm
        displayView = displayHost
        displayLayoutParams = displayLp
        displayOwner = displayDialogOwner
        touchHost = touchLayout
        touchLayoutParams = touchLp
        lineTouchHost = lineTouchLayout
        lineTouchLayoutParams = lineTouchLp
        // 手势"按住才成立"状态卡死（UP/CANCEL 丢失）时，收回全屏触摸窗。
        touchLayout.onCaptureStalled = { recoverStuckTouchCapture() }
        lineTouchLayout.onCaptureStalled = { recoverStuckTouchCapture() }
        ballIconView = ballIcon
        cursorPreviewView = cursorPreview
        appContext = hostContext
        screenOffDismissReceiver.register(hostContext)
        gestureHintWindow.attach(hostContext, wm)

        applyAllLayouts(settings)
        scheduleChromeAbovePanels(delayMs = 0L)
        displayHost.post { scheduleChromeAbovePanels(delayMs = 0L) }
    }

    private fun releaseAllTouchCaptures() {
        touchHost?.forceEndGestureCapture()
        lineTouchHost?.cancelGesture()
        collapseBallTouchHostFromFullscreen()
        collapseLineTouchHostFromFullscreen()
    }

    /**
     * 取词/侧边条手势卡死（UP/CANCEL 丢失）兜底。
     *
     * 手势没收到收尾事件时，触摸窗会一直停在全屏可触摸状态，整屏都会被它吃掉。
     * 这里按"取消"语义收尾：清掉拖拽状态 → 解除全屏捕获 → 回到空闲布局。
     */
    private fun recoverStuckTouchCapture() {
        clearLauncherAssociatedDragState()
        releaseAllTouchCaptures()
        settingsState?.value?.let { recoverIdleTouchCaptureLayouts(it) }
    }

    /** 空闲态：从全屏捕获缩回触钮区，并同步 WM 几何（z-order 重挂前必须调用）。 */
    private fun recoverIdleTouchCaptureLayouts(settings: AppSettings) {
        if (isDragging) return
        collapseBallTouchHostFromFullscreen()
        collapseLineTouchHostFromFullscreen()
        syncTouchWindowLayout(settings)
        ensureDisplayPassthrough()
        syncSplitIdleChrome(settings)
    }

    private fun shouldUseSplitIdleChrome(settings: AppSettings): Boolean {
        if (!FloatBallLayout.shouldShowLine(settings)) return false
        if (isDragging || captureSuppressed || passthroughRestorePending) return false
        if (sceneState?.stripZonePreview?.value == true) return false
        if (cursorVisibleState?.value == true) return false
        if (cursorPreviewActive) return false
        if (passiveLineRestoreRunnable != null) return false
        return true
    }

    private fun syncSplitIdleChrome(settings: AppSettings) {
        if (!shouldUseSplitIdleChrome(settings)) {
            clearSplitIdleChrome()
            displayView?.visibility = View.VISIBLE
            return
        }
        displayView?.visibility = View.GONE
        val overlayContext = touchHost?.context ?: displayView?.context ?: return
        val state = sceneState ?: return
        if (ballIdleChromeView == null) {
            val owner = OverlayComposeOwner()
            ballIdleChromeOwner = owner
            ballIdleChromeView = OverlayCompose.createComposeView(overlayContext, owner).apply {
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setContent {
                    FloatBallIdleBallChrome(
                        sceneState = state,
                        dragActiveSideOverrideState = dragActiveSideOverrideState!!
                    )
                }
            }
        }
        if (lineIdleChromeView == null) {
            val owner = OverlayComposeOwner()
            lineIdleChromeOwner = owner
            lineIdleChromeView = OverlayCompose.createComposeView(overlayContext, owner).apply {
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setContent {
                    FloatBallIdleLineChrome(sceneState = state)
                }
            }
        }
        touchHost?.setIdleChrome(ballIdleChromeView, ballIdleChromeOwner)
        lineTouchHost?.setIdleChrome(lineIdleChromeView, lineIdleChromeOwner)
    }

    private fun clearSplitIdleChrome() {
        touchHost?.setIdleChrome(null, null)
        lineTouchHost?.setIdleChrome(null, null)
    }

    private fun destroySplitIdleChrome() {
        clearSplitIdleChrome()
        OverlayCompose.disposeComposeView(ballIdleChromeView)
        OverlayCompose.disposeComposeView(lineIdleChromeView)
        ballIdleChromeView = null
        lineIdleChromeView = null
        ballIdleChromeOwner?.destroy()
        ballIdleChromeOwner = null
        lineIdleChromeOwner?.destroy()
        lineIdleChromeOwner = null
    }

    private fun ensureDisplayPassthrough() {
        val view = displayView ?: return
        val wm = windowManager ?: return
        val params = displayLayoutParams ?: return
        val needsNotTouchable = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0
        if (!needsNotTouchable) return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (view.isAttachedToWindow) {
            runCatching { wm.updateViewLayout(view, params) }
                .onFailure { Log.w(TAG, "ensureDisplayPassthrough failed", it) }
        }
    }

    private fun syncTouchCaptureLayouts() {
        settingsState?.value?.let {
            syncBallTouchWindowLayout(it)
            syncLineTouchWindowLayout(it)
        }
    }

    private fun setLineTouchHostEnabled(enabled: Boolean) {
        val view = lineTouchHost ?: return
        val wm = windowManager ?: return
        val params = lineTouchLayoutParams ?: return
        val state = sceneState
        val settings = settingsState?.value
        if (state != null && settings != null) {
            val metrics = view.resources.displayMetrics
            val (screenW, screenH) = FloatBallScreenMetrics.sizePx(view.context, wm)
            val inactiveSide = FloatBallSide.opposite(effectiveActiveSide(settings))
            val isLandscape = isLandscapeLayout()
            val bounds = state.lineHitRect(
                settings = settings,
                metrics = metrics,
                inactiveSide = inactiveSide,
                screenWidthPx = screenW,
                screenHeightPx = screenH,
                isLandscape = isLandscape,
            )
            params.x = bounds.left
            params.y = bounds.top
            params.width = bounds.width().coerceAtLeast(1)
            params.height = bounds.height().coerceAtLeast(1)
        } else {
            params.width = 1
            params.height = 1
        }
        if (enabled) {
            view.visibility = View.VISIBLE
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            if (!view.isAttachedToWindow) {
                runCatching { wm.addView(view, params) }
            } else {
                runCatching { wm.updateViewLayout(view, params) }
            }
        } else {
            if (view.isAttachedToWindow) {
                runCatching { wm.removeView(view) }
            }
        }
    }

    /** 球体拖出：全屏捕获手势，隐藏线条触摸窗。 */
    private fun expandBallTouchCapture() {
        setLineTouchHostEnabled(false)
        expandTouchHostToFullscreen(touchHost, touchLayoutParams)
    }

    /**
     * 线条拖出：全屏扩展必须在 [lineTouchHost] 上（手势在此窗发起），
     * 不可改由球体窗接管，否则 MOVE/UP 丢失。
     */
    private fun expandLineTouchCapture() {
        setBallTouchHostPassthrough(true)
        expandTouchHostToFullscreen(lineTouchHost, lineTouchLayoutParams)
    }

    private fun expandTouchHostToFullscreen(
        view: View?,
        params: WindowManager.LayoutParams?
    ) {
        val wm = windowManager ?: return
        if (view == null || params == null) return
        if (params.width == WindowManager.LayoutParams.MATCH_PARENT &&
            params.height == WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        params.x = 0
        params.y = 0
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "expandTouchHostToFullscreen failed", it) }
    }

    private fun collapseLineTouchHostFromFullscreen() {
        val view = lineTouchHost ?: return
        val wm = windowManager ?: return
        val params = lineTouchLayoutParams ?: return
        if (params.width != WindowManager.LayoutParams.MATCH_PARENT &&
            params.height != WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        settingsState?.value?.let { syncLineTouchWindowLayout(it) }
            ?: run {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                runCatching { wm.updateViewLayout(view, params) }
            }
    }

    private fun collapseBallTouchHostFromFullscreen() {
        val view = touchHost ?: return
        val wm = windowManager ?: return
        val params = touchLayoutParams ?: return
        if (params.width != WindowManager.LayoutParams.MATCH_PARENT &&
            params.height != WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        settingsState?.value?.let { syncBallTouchWindowLayout(it) }
            ?: run {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                runCatching { wm.updateViewLayout(view, params) }
            }
    }

    private fun setBallTouchHostPassthrough(passthrough: Boolean) {
        val view = touchHost ?: return
        val wm = windowManager ?: return
        val params = touchLayoutParams ?: return
        if (passthrough) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else if (!captureSuppressed && !passthroughRestorePending) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "setBallTouchHostPassthrough failed", it) }
    }

    private fun setLineTouchHostPassthrough(passthrough: Boolean) {
        val view = lineTouchHost ?: return
        val wm = windowManager ?: return
        val params = lineTouchLayoutParams ?: return
        if (passthrough) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else if (!captureSuppressed && !passthroughRestorePending) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "setLineTouchHostPassthrough failed", it) }
    }

    /** 空闲态：球体触摸窗仅覆盖球区（WM 小块，避免挡屏）。 */
    private fun syncBallTouchWindowLayout(settings: AppSettings) {
        if (isDragging && !captureSuppressed) return
        val view = touchHost ?: return
        val wm = windowManager ?: return
        val params = touchLayoutParams ?: return
        val state = sceneState ?: return
        val isLandscape = isLandscapeLayout()
        if (FloatBallLayout.isKeyboardTriggerDisabled(settings, isLandscape)) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            params.width = 1
            params.height = 1
            runCatching { wm.updateViewLayout(view, params) }
            return
        }
        val metrics = view.resources.displayMetrics
        val (screenW, screenH) = FloatBallScreenMetrics.sizePx(view.context, wm)
        val activeSide = effectiveActiveSide(settings)
        val bounds = state.ballTouchBounds(
            settings = settings,
            metrics = metrics,
            activeSide = activeSide,
            screenWidthPx = screenW,
            screenHeightPx = screenH,
            isLandscape = isLandscape,
        )
        if (captureSuppressed || passthroughRestorePending) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width().coerceAtLeast(1)
        params.height = bounds.height().coerceAtLeast(1)
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "syncBallTouchWindowLayout failed", it) }
    }

    /** 空闲态：线条触摸窗仅覆盖线条触发区。 */
    private fun syncLineTouchWindowLayout(settings: AppSettings) {
        if (isDragging) {
            if (!dragOriginatedFromLine) {
                setLineTouchHostEnabled(false)
            }
            return
        }
        val view = lineTouchHost ?: return
        val wm = windowManager ?: return
        val params = lineTouchLayoutParams ?: return
        val state = sceneState ?: return
        val isLandscape = isLandscapeLayout()
        val showLine = state.lineVisible.value &&
            FloatBallLayout.shouldShowLine(settings) &&
            !captureSuppressed &&
            !passthroughRestorePending &&
            !FloatBallLayout.isKeyboardTriggerDisabled(settings, isLandscape)
        if (!showLine) {
            setLineTouchHostEnabled(false)
            return
        }
        val metrics = view.resources.displayMetrics
        val (screenW, screenH) = FloatBallScreenMetrics.sizePx(view.context, wm)
        val inactiveSide = FloatBallSide.opposite(effectiveActiveSide(settings))
        val bounds = state.lineHitRect(
            settings = settings,
            metrics = metrics,
            inactiveSide = inactiveSide,
            screenWidthPx = screenW,
            screenHeightPx = screenH,
            isLandscape = isLandscape,
        )
        view.visibility = View.VISIBLE
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.x = bounds.left
        params.y = bounds.top
        params.width = bounds.width().coerceAtLeast(1)
        params.height = bounds.height().coerceAtLeast(1)
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "syncLineTouchWindowLayout failed", it) }
    }

    private fun syncTouchWindowLayout(settings: AppSettings) {
        syncBallTouchWindowLayout(settings)
        syncLineTouchWindowLayout(settings)
    }

    /**
     * 输入层接管（system_server 模块）用的现场命中复核。
     *
     * 由 binder 线程调用，因此只读快照数据（设置快照、场景状态、屏幕尺寸），不触碰 View 结构；
     * 球的位置会随停靠/自定义/键盘弹出/横屏变化，所以这里**当次现算**，
     * 不用缓存矩形——算不到就返回 false，模块据此放行，绝不吞掉无人处理的触摸。
     */
    fun canAcceptForwardedTouchAt(target: Int, x: Float, y: Float): Boolean {
        val view = displayView ?: touchHost ?: lineTouchHost ?: return false
        val state = sceneState ?: return false
        val settings = state.settingsState.value
        if (!settings.floatBallEnabled || captureSuppressed || passthroughRestorePending) return false
        val isLandscape = isLandscapeLayout()
        if (FloatBallLayout.isKeyboardTriggerDisabled(settings, isLandscape)) return false
        val metrics = view.resources.displayMetrics
        val (screenW, screenH) = FloatBallScreenMetrics.sizePx(view.context, windowManager)
        val activeSide = effectiveActiveSide(settings)
        return when (target) {
            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.TARGET_FLOAT_BALL ->
                state.ballHitRect(
                    settings = settings,
                    metrics = metrics,
                    activeSide = activeSide,
                    screenWidthPx = screenW,
                    screenHeightPx = screenH,
                    isLandscape = isLandscape,
                ).contains(x.roundToInt(), y.roundToInt())

            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.TARGET_FLOAT_LINE ->
                FloatBallLayout.shouldShowLine(settings) &&
                    state.lineHitRect(
                        settings = settings,
                        metrics = metrics,
                        inactiveSide = FloatBallSide.opposite(activeSide),
                        screenWidthPx = screenW,
                        screenHeightPx = screenH,
                        isLandscape = isLandscape,
                    ).contains(x.roundToInt(), y.roundToInt())

            else -> false
        }
    }

    /** 输入层转发来的触摸：交给对应的触摸窗，复用既有手势检测（必须在主线程调用）。 */
    fun handleForwardedTouch(target: Int, event: android.view.MotionEvent): Boolean = when (target) {
        com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.TARGET_FLOAT_BALL ->
            touchHost?.handleForwardedTouch(event) ?: false
        com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.TARGET_FLOAT_LINE ->
            lineTouchHost?.handleForwardedTouch(event) ?: false
        else -> false
    }

    /** 输入层会话结束：结束该目标的手势并收回空闲布局。 */
    fun cancelForwardedTouch(target: Int) {
        when (target) {
            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.TARGET_FLOAT_BALL -> {
                touchHost?.forceEndGestureCapture()
                collapseBallTouchHostFromFullscreen()
            }
            com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.TARGET_FLOAT_LINE -> {
                lineTouchHost?.cancelGesture()
                collapseLineTouchHostFromFullscreen()
            }
        }
    }

    private fun buildDisplayLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
    }

    private fun buildTouchLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            1,
            1,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
    }

    private fun hideGestureHintWindow() {
        currentGestureHintType = null
        gestureHintWindow.hide()
    }

    private fun updateGestureHintWindow() {
        val gestureType = currentGestureHintType
        if (gestureType == null || !isDragging) {
            gestureHintWindow.hide()
            return
        }
        val settings = settingsState?.value ?: run {
            gestureHintWindow.hide()
            return
        }
        val action = settings.floatBallGestureActions[gestureType] ?: GestureAction.None
        if (action is GestureAction.None) {
            gestureHintWindow.hide()
            return
        }
        val view = displayView ?: return
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        gestureHintWindow.update(
            action = action,
            // 跟主题强调色走（不是 themeColorArgb 那个默认紫）：与球/线条保持一致。
            accentArgb = resolveAppAccentArgb(view.context, settings),
            fingerX = dragSession.dragFingerX,
            fingerY = dragSession.dragFingerY,
            dockSide = effectiveActiveSide(settings),
            density = density
        )
    }

    private fun isOverlayLauncherAction(action: GestureAction): Boolean =
        action == GestureAction.RingLauncher || action == GestureAction.HoneycombLauncher

    private fun createFloatBallActionExecutor(
        hostContext: Context,
        deps: com.slideindex.app.di.OverlayDependencies
    ): ActionExecutor = ActionExecutor(
        context = hostContext,
        appRepository = deps.appRepository,
        onShellCommandsPersist = { commands ->
            overlayScope.launch {
                deps.settingsRepository.setShellCommands(commands)
            }
        }
    )

    private fun handleFloatBallLauncherCaptureMove(rawX: Float, rawY: Float) {
        RingLauncherOverlayWindow.updatePointer(rawX, rawY)
        HoneycombAppPickerOverlayWindow.updatePointer(rawX, rawY)
    }

    private fun handleFloatBallLauncherCaptureUp(
        rawX: Float,
        rawY: Float,
        fromLineStrip: Boolean,
        settings: AppSettings
    ) {
        val hostContext = OverlayDependencyAccess.overlayHostContext()
            ?: displayView?.context?.applicationContext
        val deps = hostContext?.let { OverlayDependencyAccess.overlayDependencies(it) }
        if (hostContext == null || deps == null) {
            RingLauncherOverlayWindow.dismiss()
            HoneycombAppPickerOverlayWindow.dismiss()
            releaseFloatBallLauncherCapture(fromLineStrip)
            return
        }
        val actionExecutor = createFloatBallActionExecutor(hostContext, deps)
        when {
            RingLauncherOverlayWindow.isShowing -> {
                RingLauncherOverlayWindow.confirmSelection(
                    rawX = rawX,
                    rawY = rawY,
                    actionExecutor = actionExecutor,
                    settings = settings
                )
            }
            HoneycombAppPickerOverlayWindow.isShowing -> {
                HoneycombAppPickerOverlayWindow.confirmSelection(
                    rawX = rawX,
                    rawY = rawY,
                    actionExecutor = actionExecutor,
                    settings = settings
                )
            }
        }
        releaseFloatBallLauncherCapture(fromLineStrip)
        if (!RingLauncherOverlayWindow.isShowing && !HoneycombAppPickerOverlayWindow.isShowing) {
            displayView?.visibility = View.VISIBLE
        }
    }

    private fun releaseFloatBallLauncherCapture(fromLineStrip: Boolean) {
        if (fromLineStrip) {
            lineTouchHost?.cancelLauncherCaptureMode()
        } else {
            touchHost?.cancelLauncherCaptureMode()
        }
        if (RingLauncherOverlayWindow.isShowing || HoneycombAppPickerOverlayWindow.isShowing) {
            suppressTouchHostsForLauncherOverlay()
        } else {
            clearLauncherAssociatedDragState()
            releaseAllTouchCaptures()
        }
    }

    private fun isLauncherCaptureActive(): Boolean =
        touchHost?.isLauncherCaptureMode() == true || lineTouchHost?.isLauncherCaptureMode() == true

    /**
     * 圆环/蜂窝展示期间：隐藏并穿透悬浮球触摸窗。
     * 避免 updateViewLayout 把全屏触摸窗抬到启动器之上（部分 OEM 上会挡住圆环）。
     */
    private fun suppressTouchHostsForLauncherOverlay() {
        if (!RingLauncherOverlayWindow.isShowing && !HoneycombAppPickerOverlayWindow.isShowing) return
        clearLauncherAssociatedDragState()
        touchHost?.forceEndGestureCapture()
        lineTouchHost?.cancelGesture()
        touchHost?.visibility = View.GONE
        setLineTouchHostEnabled(false)
        setBallTouchHostPassthrough(true)
        setLineTouchHostPassthrough(true)
        applyTouchHostOverlayPassthroughFlags()
    }

    private fun applyTouchHostOverlayPassthroughFlags() {
        val wm = windowManager ?: return
        touchHost?.let { view ->
            val params = touchLayoutParams ?: return@let
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            if (view.isAttachedToWindow) {
                runCatching { wm.updateViewLayout(view, params) }
            }
        }
        lineTouchHost?.let { view ->
            val params = lineTouchLayoutParams ?: return@let
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            if (view.isAttachedToWindow) {
                runCatching { wm.updateViewLayout(view, params) }
            }
        }
    }

    private fun clearLauncherAssociatedDragState() {
        if (!isDragging && !slopPhaseBallFollowActive) return
        clearCursorUi(restoreLayout = false)
        slopPhaseBallFollowActive = false
        dragActiveSideOverride = null
        activeSideAtDragStart = null
        dragOriginatedFromLine = false
        lineDragEndedWithGesture = false
        setDragging(false)
    }

    private fun showFloatBallLauncherOverlay(
        settings: AppSettings,
        action: GestureAction,
        anchorX: Float,
        anchorY: Float,
        externalTracking: Boolean,
        fromLineStrip: Boolean,
        actionExecutor: ActionExecutor,
        hostContext: Context,
        panelSide: PanelSide? = null,
    ) {
        cancelCursorPickPreview()
        clearSplitIdleChrome()
        hideGestureHintWindow()
        if (externalTracking) {
            displayView?.visibility = View.GONE
            if (fromLineStrip) {
                expandLineTouchCapture()
                lineTouchHost?.beginLauncherCaptureMode()
            } else {
                expandBallTouchCapture()
                touchHost?.beginLauncherCaptureMode()
            }
        }
        val onLaunch: (com.slideindex.app.launcher.QuickLauncherItem, Boolean) -> Unit = { item, longPressArmed ->
            releaseFloatBallLauncherCapture(fromLineStrip)
            displayView?.visibility = View.VISIBLE
            // 圆环里点开的条目（例如「快速启动器」槽位）要跟圆环落在同一侧，
            // 否则打开的面板会按宿主默认落到左边。
            actionExecutor.launchQuickItem(
                item,
                settings,
                longPressArmed = longPressArmed,
                panelSide = panelSide,
            )
        }
        val shown = when (action) {
            GestureAction.RingLauncher -> RingLauncherOverlayWindow.show(
                context = hostContext,
                settings = settings,
                anchorRawX = anchorX,
                anchorRawY = anchorY,
                externalTracking = externalTracking,
                onLaunch = onLaunch,
                // 长按呼出（按住跟手）时，可自动选中悬浮球正下方的第一个槽位，松手即启动。
                // 其它方式打开的圆环由手指/点击决定，不预设高亮。
                initialHighlightSlot = if (externalTracking && settings.floatBallLongPressAutoSelectRingSlot) 0 else -1,
            )
            GestureAction.HoneycombLauncher -> HoneycombAppPickerOverlayWindow.show(
                context = hostContext,
                settings = settings,
                anchorRawX = anchorX,
                anchorRawY = anchorY,
                externalTracking = externalTracking,
                onLaunch = onLaunch
            )
            else -> false
        }
        if (!shown) {
            releaseFloatBallLauncherCapture(fromLineStrip)
            displayView?.visibility = View.VISIBLE
        } else if (!externalTracking) {
            releaseFloatBallLauncherCapture(fromLineStrip)
            displayView?.visibility = View.VISIBLE
        }
    }

    private fun performFloatBallGesture(
        settings: AppSettings,
        gestureType: FloatBallGestureType,
        rawX: Float,
        rawY: Float,
        fromLineStrip: Boolean = false,
        touchDownRawX: Float? = null,
        touchDownRawY: Float? = null
    ) {
        val action = settings.floatBallGestureActions[gestureType] ?: GestureAction.None
        if (action is GestureAction.None) return
        // 返回/桌面/最近任务由系统自己给触摸反馈，我们再振一下就会变成"两下"；
        // 与触钮同一规则（Back/Home/Recents 不发确认振动）。
        val systemNavigation = action == GestureAction.Back ||
            action == GestureAction.Home ||
            action == GestureAction.Recents
        if (!systemNavigation) {
            displayView?.let { HapticHelper.floatBallGestureStart(it, settings) }
        }
        executeFloatBallAction(
            settings = settings,
            action = action,
            rawX = rawX,
            rawY = rawY,
            fromLineStrip = fromLineStrip,
            touchDownRawX = touchDownRawX,
            touchDownRawY = touchDownRawY,
            // 只有长按才把「悬浮窗启动器」锚到球心（与历史行为一致）。
            anchorAtBallCenterForLauncher = gestureType == FloatBallGestureType.LONG_PRESS
        )
    }

    /**
     * FV「自定义手势」的运行时入口（见 `docs/reverse/fv-custom-gesture/03-runtime-pipeline.md`）。
     *
     * 判定交给 `resolveCustomGesture`（纯函数，三态与 FV 的 `q1.o.H()` 一一对应）：
     *  - `Native`   ：画的是直线手势 -> 返回 false，让悬浮球的原生方向手势继续处理；
     *  - `Consumed` ：画了曲线但没匹配到 -> 返回 true（消费掉，本次什么都不做，FV 语义）；
     *  - `Matched`  ：命中 -> 执行该手势绑定的动作并返回 true。
     *
     * 库里没有任何自定义手势时 `resolveCustomGesture` 直接返回 `Native`，因此未使用该功能的
     * 用户行为零变化。
     *
     * [fromLineStrip] 必须由调用方如实传入：它是「本次从哪一侧触发的」唯一来源。
     * 双贴边 / 双线模式下线条恒在球停靠侧的**对侧**，面板侧别要靠它取「线条侧」
     * （见 [executeFloatBallAction] → `FloatBallLayout.panelSideForLineStrip`）；
     * 写死 false 会让面板按球停靠侧打开，表现就是从线条触发时面板跑到对侧。
     */
    private fun handleFloatBallCustomGesture(
        settings: AppSettings,
        path: FloatArray,
        rawX: Float,
        rawY: Float,
        touchDownRawX: Float,
        touchDownRawY: Float,
        fromLineStrip: Boolean = false
    ): Boolean = when (val outcome = resolveCustomGesture(path)) {
        FvCustomGestureOutcome.Native -> false
        FvCustomGestureOutcome.Consumed -> true
        is FvCustomGestureOutcome.Matched -> {
            Log.i(TAG, "custom gesture matched: id=${outcome.gestureId}, action=${outcome.action}")
            executeFloatBallAction(
                settings = settings,
                action = outcome.action,
                rawX = rawX,
                rawY = rawY,
                fromLineStrip = fromLineStrip,
                touchDownRawX = touchDownRawX,
                touchDownRawY = touchDownRawY,
                anchorAtBallCenterForLauncher = false
            )
            true
        }
    }

    /** 悬浮球动作的统一执行体：原生方向手势与 FV 自定义手势都走这里。 */
    private fun executeFloatBallAction(
        settings: AppSettings,
        action: GestureAction,
        rawX: Float,
        rawY: Float,
        fromLineStrip: Boolean,
        touchDownRawX: Float?,
        touchDownRawY: Float?,
        anchorAtBallCenterForLauncher: Boolean
    ) {
        if (action is GestureAction.None) return
        if (action is GestureAction.ClickPassthrough) {
            if (passthroughRestorePending) return
            // 输入层接管期间禁止注入放行：注入事件会再次进入模块的过滤器，形成回环。
            if (ModuleForwardedTouchGate.isRecent()) return
            OverlayPassthrough.run(
                hideTriggers = ::hideFloatBallOverlaysForPassthrough,
                showTriggers = ::restoreFloatBallOverlaysAfterPassthrough,
                rawX = rawX,
                rawY = rawY,
                onComplete = {},
                framesBeforeInject = FLOAT_BALL_PASSTHROUGH_FRAMES_BEFORE_INJECT,
                restoreDelayMs = FLOAT_BALL_PASSTHROUGH_RESTORE_DELAY_MS
            )
            return
        }
        val hostContext = OverlayDependencyAccess.overlayHostContext()
            ?: displayView?.context?.applicationContext
            ?: return
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext) ?: return
        val panelSide = if (fromLineStrip && FloatBallLayout.usesEdgeLines(settings)) {
            FloatBallLayout.panelSideForLineStrip(settings)
        } else {
            FloatBallLayout.panelSideFor(settings)
        }
        val actionExecutor = ActionExecutor(
            context = hostContext,
            appRepository = deps.appRepository,
            onShellCommandsPersist = { commands ->
                overlayScope.launch {
                    deps.settingsRepository.setShellCommands(commands)
                }
            }
        )
        if (anchorAtBallCenterForLauncher && isOverlayLauncherAction(action)) {
            val (anchorX, anchorY) = if (fromLineStrip) {
                rawX to rawY
            } else {
                ballCenterForRingLauncher(settings) ?: (rawX to rawY)
            }
            showFloatBallLauncherOverlay(
                settings = settings,
                action = action,
                anchorX = anchorX,
                anchorY = anchorY,
                externalTracking = true,
                fromLineStrip = fromLineStrip,
                actionExecutor = actionExecutor,
                hostContext = hostContext,
                panelSide = panelSide,
            )
            return
        }
        actionExecutor.execute(
            action = action,
            settings = settings,
            anchorRawX = rawX,
            anchorRawY = rawY,
            panelSide = panelSide,
            gestureTouchDownRawX = touchDownRawX,
            gestureTouchDownRawY = touchDownRawY
        )
    }

    private fun hideFloatBallOverlaysForPassthrough() {
        if (passthroughRestorePending) return
        passthroughRestorePending = true
        cancelPendingChromeRaise()
        cancelCursorPickPreview()
        hideGestureHintWindow()
        setBallTouchHostPassthrough(true)
        setLineTouchHostPassthrough(true)
        setFloatBallPassthroughWindowsVisible(false)
    }

    private fun restoreFloatBallOverlaysAfterPassthrough() {
        if (!passthroughRestorePending) return
        passthroughRestorePending = false
        setBallTouchHostPassthrough(false)
        setLineTouchHostPassthrough(false)
        setFloatBallPassthroughWindowsVisible(true)
        settingsState?.value?.let { updateChromeVisibility(it) }
    }

    private fun setFloatBallPassthroughWindowsVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        displayView?.visibility = visibility
        touchHost?.visibility = visibility
        if (!visible) {
            setLineTouchHostEnabled(false)
            gestureHintWindow.hide()
        }
    }

    private fun setCursorLayersVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        cursorPreviewView?.visibility = visibility
        if (visible) {
            syncCursorChromeAppearance()
        } else {
            syncCursorPreviewAppearance()
        }
    }

    private fun syncCursorChromeAppearance() {
        syncCursorPreviewAppearance()
    }

    private fun syncCursorPreviewAppearance() {
        val view = cursorPreviewView ?: return
        val settings = settingsState?.value
        val layersVisible = cursorVisibleState?.value == true
        val crossVisible = layersVisible || cursorPreviewActive
        val crossAlpha = if (cursorPreviewActive && !isDragging) {
            passivePickPreviewAlpha
        } else {
            1f
        }
        view.setChromeState(
            visible = layersVisible,
            paused = cursorPausedState?.value == true,
            selectionStart = selectionStartState?.value,
            selectionPreviewBounds = selectionPreviewBoundsState?.value,
            pickAnchor = currentPickAnchor() ?: Offset.Zero,
            regionalDragActive = regionalPickActive,
            crossVisible = crossVisible,
            crossAlpha = crossAlpha,
            crossPaused = cursorPausedState?.value == true,
            crossArmDp = settings?.floatBallPickCrossArmDp?.coerceIn(4f, 16f) ?: 7.5f,
            hintMode = resolveCursorHintMode()
        )
    }

    /** 悬停提示：区域截图→截图图标；输入框→粘贴；其余→取词 A。 */
    private fun resolveCursorHintMode(): FloatBallCursorPreviewView.HintMode {
        if (cursorVisibleState?.value != true || cursorPausedState?.value != true) {
            return FloatBallCursorPreviewView.HintMode.HIDDEN
        }
        if (regionalPickActive) {
            return FloatBallCursorPreviewView.HintMode.SCREENSHOT
        }
        val settings = settingsState?.value
        if (settings?.floatBallDragPasteEnabled == true && !dragPastePickUpgraded) {
            val anchor = currentPickAnchor()
            if (anchor != null && FloatBallPreviewBoundsCache.hitTestEditableAt(anchor.x, anchor.y) != null) {
                return FloatBallCursorPreviewView.HintMode.PASTE
            }
        }
        return FloatBallCursorPreviewView.HintMode.TEXT
    }

    private fun currentPickAnchor(): Offset? {
        if (cursorPreviewActive && !isDragging) {
            return passivePickPreviewAnchor
        }
        if (isDragging) return currentDragPickAnchor
        return cursorAnchorState?.value
    }

    private fun restorePassiveOverlayLayout(
        settings: AppSettings,
        @Suppress("UNUSED_PARAMETER") fixZOrder: Boolean = true,
        deferLineRestore: Boolean = false,
        skipBallLayout: Boolean = false
    ) {
        setBallTouchable(true)
        if (!skipBallLayout) {
            applyBallLayout(settings)
        }
        if (deferLineRestore) {
            updateChromeVisibility(settings)
            schedulePassiveLineRestore()
        } else {
            applyLineLayout(settings)
            updateChromeVisibility(settings)
        }
    }

    private fun cancelPassiveLineRestore() {
        passiveLineRestoreRunnable?.let { mainHandler.removeCallbacks(it) }
        passiveLineRestoreRunnable = null
    }

    /** Show line strip one frame after ball Compose/GIF restore — spreads release CPU spike. */
    private fun schedulePassiveLineRestore() {
        cancelPassiveLineRestore()
        val host = displayView ?: return
        val runnable = Runnable {
            passiveLineRestoreRunnable = null
            if (captureSuppressed) return@Runnable
            val settings = settingsState?.value ?: return@Runnable
            applyLineLayout(settings)
            updateChromeVisibility(settings)
        }
        passiveLineRestoreRunnable = runnable
        host.postOnAnimation(runnable)
    }

    private fun restoreAfterDragEnd(settings: AppSettings) {
        clearCursorUi(restoreLayout = false)
        cancelPassiveLineRestore()
        applyBallLayout(settings)
        setDragging(false)
        FloatBallPickResultPanel.releaseWarmUpShell()
        setBallTouchHostPassthrough(false)
        restorePassiveOverlayLayout(
            settings = settings,
            fixZOrder = false,
            deferLineRestore = true,
            skipBallLayout = true
        )
        releaseAllTouchCaptures()
        syncTouchCaptureLayouts()
        flushDeferredChromeRaiseIfNeeded()
    }

    private fun areChromeWindowsAttached(): Boolean {
        val display = displayView ?: return false
        val touch = touchHost ?: return false
        if (!display.isAttachedToWindow || !touch.isAttachedToWindow) return false
        val line = lineTouchHost ?: return true
        val settings = settingsState?.value
        val lineNeeded = settings != null && FloatBallLayout.shouldShowLine(settings) && sceneState?.lineVisible?.value == true
        return !lineNeeded || line.isAttachedToWindow
    }

    private fun bringOverlayToFront(
        view: View,
        params: WindowManager.LayoutParams,
        forceReAdd: Boolean = false
    ) {
        val wm = windowManager ?: return
        if (!view.isAttachedToWindow) {
            if (!forceReAdd) return
            runCatching {
                wm.addView(view, params)
                view.requestLayout()
                view.invalidate()
            }.onFailure { Log.w(TAG, "bringOverlayToFront re-add failed", it) }
            return
        }
        if (forceReAdd) {
            runCatching {
                wm.removeView(view)
                wm.addView(view, params)
                view.requestLayout()
                view.invalidate()
            }.onFailure { Log.w(TAG, "bringOverlayToFront forceReAdd failed", it) }
            return
        }
        runCatching {
            wm.updateViewLayout(view, params)
            view.requestLayout()
            view.invalidate()
        }.onFailure { Log.w(TAG, "bringOverlayToFront updateViewLayout failed", it) }
    }

    private fun applyAllLayouts(settings: AppSettings, relayoutChrome: Boolean = true) {
        applyBallLayout(settings)
        if (relayoutChrome) {
            applyLineLayout(settings)
        }
        updateChromeVisibility(settings)
    }

    private fun applyBallLayout(settings: AppSettings) {
        sceneState?.ballCenterPx?.value = null
        syncDisplayBallLayout(settings)
    }

    private fun syncDisplayBallLayout(
        settings: AppSettings,
        layoutOnly: Boolean = false,
        overrideCenter: Offset? = null
    ) {
        val host = displayView ?: return
        val state = sceneState ?: return
        val metrics = host.resources.displayMetrics
        val activeSide = effectiveActiveSide(settings)
        val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(host.context, windowManager)
        val isLandscape = isLandscapeLayout()
        val center = overrideCenter ?: state.ballCenterPx.value ?: state.dockBallCenter(
            settings,
            metrics,
            activeSide,
            screenWidthPx,
            screenHeightPx,
            isLandscape,
        )
        val (left, top) = state.ballWindowTopLeft(settings, metrics, activeSide, center, screenHeightPx)
        val ballSize = FloatBallLayout.ballSize(settings, metrics.density)
        host.layoutBall(left, top, ballSize.widthPx, ballSize.heightPx)
        host.setBallVisible(state.ballVisible.value)
        if (!layoutOnly) {
            ballIconView?.bind(
                settings = settings,
                activeSide = activeSide,
                styleGeneration = state.styleVisualGeneration.intValue
            )
        }
        ballIconView?.setDragging(state.ballDragging.value)
    }

    private fun applyLineLayout(settings: AppSettings) {
        if (!FloatBallLayout.shouldShowLine(settings)) {
            sceneState?.lineVisible?.value = false
            return
        }
        sceneState?.lineVisible?.value = !isDragging || dragOriginatedFromLine
    }

    private fun updateChromeVisibility(settings: AppSettings) {
        val state = sceneState ?: return
        if (captureSuppressed) {
            state.chromeVisible.value = false
            syncTouchWindowLayout(settings)
            displayView?.setBallVisible(false)
            return
        }
        if (FloatBallLayout.isKeyboardTriggerDisabled(settings, isLandscapeLayout())) {
            state.chromeVisible.value = false
            state.ballVisible.value = false
            state.lineVisible.value = false
            syncTouchWindowLayout(settings)
            displayView?.setBallVisible(false)
            return
        }
        state.chromeVisible.value = true
        if (isDragging || passiveLineRestoreRunnable != null) {
            state.ballVisible.value = true
            state.lineVisible.value = dragOriginatedFromLine && FloatBallLayout.shouldShowLine(settings)
            if (!dragOriginatedFromLine) {
                setLineTouchHostEnabled(false)
            }
            syncDisplayBallLayout(settings, layoutOnly = isDragging)
            return
        }
        state.ballVisible.value = true
        state.lineVisible.value = FloatBallLayout.shouldShowLine(settings)
        touchHost?.ballStripTouchable = true
        lineTouchHost?.stripTouchable = true
        syncTouchWindowLayout(settings)
        syncSplitIdleChrome(settings)
        syncDisplayBallLayout(settings)
    }

    private fun effectiveActiveSide(settings: AppSettings): FloatBallSide =
        dragActiveSideOverride ?: FloatBallLayout.resolvedActiveSide(settings)

    private fun prepareLineDrag(
        touchDownX: Float,
        touchDownY: Float,
        fingerX: Float,
        fingerY: Float
    ) {
        val settings = settingsState?.value ?: return
        val dockedSide = FloatBallLayout.resolvedActiveSide(settings)
        val bothEdges = FloatBallLayout.usesEdgeLines(settings)
        activeSideAtDragStart = if (bothEdges) dockedSide else null
        lineDragEndedWithGesture = false
        if (!slopPhaseBallFollowActive) {
            dragOriginatedFromLine = bothEdges
            dragActiveSideOverride = if (bothEdges) {
                FloatBallSide.opposite(dockedSide)
            } else {
                null
            }
        }
        expandLineTouchCapture()
        showCursorAtScreenTouch(
            touchDownX = touchDownX,
            touchDownY = touchDownY,
            fingerX = fingerX,
            fingerY = fingerY,
            deferBallWindowMutation = true
        )
        if (isDragging && dragOriginatedFromLine) {
            setBallTouchable(false)
        }
    }

    private fun commitLineDragSideSwap() {
        if (!dragOriginatedFromLine) return
        val fromSide = activeSideAtDragStart ?: return
        val settings = settingsState?.value ?: return
        if (!FloatBallLayout.usesEdgeLines(settings)) return
        val targetSide = FloatBallSide.opposite(fromSide)
        if (FloatBallLayout.resolvedActiveSide(settings) != targetSide) {
            applyActiveSide(targetSide)
        }
    }

    private fun revertLineDragSideSwapIfNeeded() {
        if (!dragOriginatedFromLine) return
        val revertSide = activeSideAtDragStart ?: return
        val settings = settingsState?.value ?: return
        if (!FloatBallLayout.usesEdgeLines(settings)) return
        if (FloatBallLayout.resolvedActiveSide(settings) != revertSide) {
            applyActiveSide(revertSide)
        }
    }

    private fun cancelDragWithoutPick() {
        if (!isDragging) return
        if (moveModeActive) exitMoveMode()
        cancelDragChromeLayoutFrame()
        cancelCursorCommitFrame()
        activeSideAtDragStart = null
        val settings = settingsState?.value
        if (settings != null) {
            restoreAfterDragEnd(settings)
        } else {
            clearCursorUi(restoreLayout = false)
            setDragging(false)
        }
    }

    private fun completeDragGesture() {
        if (!isDragging || finishDragRequested) return
        finishDragRequested = true
        val settings = settingsState?.value ?: run {
            finishDragRequested = false
            return
        }
        finishDrag(settings)
        finishDragRequested = false
    }

    private fun applyActiveSide(targetSide: FloatBallSide) {
        val settings = settingsState?.value ?: return
        if (!FloatBallLayout.usesEdgeLines(settings)) return
        val updated = settings.copy(floatBall = settings.floatBall.copy(floatBallActiveSide = targetSide))
        settingsState?.value = updated
        committedActiveSideUntilPersist = targetSide
        onActiveSidePersisted?.invoke(targetSide)
        applyBallLayout(updated)
        if (!isDragging) {
            applyLineLayout(updated)
            updateChromeVisibility(updated)
        }
    }

    private fun restoreDockPosition(settings: AppSettings) {
        applyAllLayouts(settings)
    }

    private fun onFingerDrag(fingerX: Float, fingerY: Float) {
        if (!isDragging) return
        dragSession.updateFingerPosition(fingerX, fingerY)
        updatePickAndBallFromFinger(moveBallWindow = true)
    }

    private fun finishDrag(settings: AppSettings) {
        if (!isDragging) return
        cancelDragChromeLayoutFrame()
        cancelCursorCommitFrame()
        flushDragChromeLayout(syncAnchorState = true)
        commitPickAnchor()
        // 圆环工具栏「移动」进入的移动模式：松手只做"选边 + 存竖直位置 + 吸附"，不取词。
        if (moveModeActive) {
            finishMoveModeRelease(settings)
            activeSideAtDragStart = null
            restoreAfterDragEnd(settingsState?.value ?: settings)
            return
        }
        val hadPauseIntent = cursorPausedState?.value == true || selectionStartState?.value != null
        if (hadPauseIntent) {
            if (!regionalPickActive) {
                ensurePreviewBoundsForPick()
            }
            handlePickOnRelease(settings)
        } else {
            commitLineDragSideSwap()
        }
        dragActiveSideOverride = null
        activeSideAtDragStart = null

        if (settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM) {
            persistBallCenterFraction()
        }

        restoreAfterDragEnd(settingsState?.value ?: settings)
    }

    private fun handlePickOnRelease(settings: AppSettings) {
        val end = currentPickAnchor() ?: return
        val start = selectionStartState?.value ?: end
        val host = appContext ?: return
        val view = displayView ?: return
        val dragRect = rectBetween(start, end)
        val isRegionalDrag = regionalPickActive
        val ocrFallbackEnabled = settings.floatBallOcrFallbackEnabled
        val ocrModelId = settings.floatBallOcrModelId

        if (isRegionalDrag) {
            val density = view.resources.displayMetrics.density
            val minSidePx = (REGIONAL_RECT_MIN_SIDE_DP * density).roundToInt()
            val pickRect = dragRect
            if (pickRect.width() < minSidePx || pickRect.height() < minSidePx) {
                return
            }
            val sessionTag = "regional_rect"
            PickPerf.beginSession(sessionTag)
            PickPerf.mark(
                "ACTION_UP",
                "regional=true ocr=$ocrFallbackEnabled"
            )
            val panelAnchorX = pickRect.centerX().toFloat()
            val panelAnchorY = pickRect.bottom.toFloat()
            FloatBallPickResultPanel.showLoading(
                host,
                panelAnchorX,
                panelAnchorY,
                PickResultTextSource.OCR
            )
            SlideIndexAccessibilityService.pickFloatBallOnRelease(
                context = host,
                startX = pickRect.left.toFloat(),
                startY = pickRect.top.toFloat(),
                endX = pickRect.right.toFloat(),
                endY = pickRect.bottom.toFloat(),
                regionalRect = true,
                ocrFallbackEnabled = ocrFallbackEnabled,
                ocrModelId = ocrModelId
            ) { result ->
                PickPerf.mark("showResultPanel_callback")
                FloatBallPickResultPanel.showResult(host, panelAnchorX, panelAnchorY, result)
                PickPerf.endSession("END", sessionTag)
            }
        } else {
            if (tryFloatBallDragPaste(settings, end)) return
            var previewBounds = selectionPreviewBoundsState?.value
            if (previewBounds == null) {
                ensurePreviewBoundsForPick()
                previewBounds = selectionPreviewBoundsState?.value
            }
            val bounds = previewBounds ?: return
            val panelAnchorX = bounds.centerX().toFloat()
            val panelAnchorY = bounds.bottom.toFloat()
            PickPerf.beginSession("preview_bounds")
            FloatBallPickResultPanel.showLoading(
                host,
                panelAnchorX,
                panelAnchorY,
                PickResultTextSource.A11Y
            )
            SlideIndexAccessibilityService.pickFloatBallTextInRect(
                context = host,
                rect = bounds,
                ocrFallbackEnabled = ocrFallbackEnabled,
                ocrModelId = ocrModelId,
                previewBoundsPick = true
            ) { result ->
                FloatBallPickResultPanel.showResult(host, panelAnchorX, panelAnchorY, result)
                PickPerf.endSession("END", "preview_bounds")
            }
        }
    }

    private fun tryFloatBallDragPaste(settings: AppSettings, anchor: Offset): Boolean {
        if (!settings.floatBallDragPasteEnabled) return false
        if (dragPastePickUpgraded) return false
        val hadPasteIntent = lockedDragPasteEditableRect != null
        val editableRect = lockedDragPasteEditableRect?.let { Rect(it) }
            ?: FloatBallPreviewBoundsCache.hitTestEditableAt(anchor.x, anchor.y)?.let { Rect(it) }
        lockedDragPasteEditableRect = null
        if (editableRect == null) return false
        val service = SlideIndexAccessibilityService.accessibilityInstance()
        val host = appContext
        val repo = ClipboardAccess.repository
        if (service == null || host == null || repo == null) {
            if (hadPasteIntent) {
                Toast.makeText(host ?: appContext, R.string.search_engine_accessibility_required, Toast.LENGTH_SHORT)
                    .show()
            }
            return hadPasteIntent
        }
        if (repo.entryCount.value <= 0) {
            Toast.makeText(host, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
            return true
        }
        val rect = editableRect
        val fvStyle = settings.clipboardPasteFvStyleEnabled
        overlayScope.launch {
            val entry = withContext(Dispatchers.IO) { repo.peekLatestEntry() }
            if (entry == null) return@launch
            ClipboardPasteCoordinator.pasteEntryAtScreenRect(
                service = service,
                context = host,
                entry = entry,
                rect = rect,
                fvStyle = fvStyle,
                onFinished = { result ->
                    if (result is PasteResult.Failure) {
                        ClipboardPasteCoordinator.toastPasteFailure(host, result.reason)
                    }
                },
            )
        }
        return true
    }

    private fun snapBallToEdge(settings: AppSettings) {
        restoreDockPosition(settings)
    }

    /** 当前球心占屏幕宽/高的比例（自由位置模式用的就是这两个分数）。 */
    private fun currentBallCenterFraction(): Pair<Float, Float>? {
        val view = displayView ?: return null
        val settings = settingsState?.value ?: return null
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val ballSize = FloatBallLayout.ballSize(settings, density)
        val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(view.context, windowManager)
        // ⚠ 拖动中必须以**手指带动的真实球心**为准：dragSession 记着"手指↔球心"的偏移并做了边界钳制。
        // 若这里去问 FloatBallLayout.ballCenterPx(settings)，自由位置模式下读到的还是**旧的自定义分数**，
        // 结果就是"拖到哪、松手又弹回哪"（自由位置模式的移动一直是坏的）。
        val center: Pair<Float, Float> = if (isDragging) {
            val clamped = dragSession.clampedBallCenter(
                ballSize = ballSize,
                marginPx = FloatBallLayout.marginPx(density),
                screenWidth = screenWidthPx,
                screenHeight = screenHeightPx
            )
            clamped.x to clamped.y
        } else {
            val activeSide = FloatBallLayout.resolvedActiveSide(settings)
            FloatBallLayout.ballCenterPx(settings, metrics, activeSide, screenWidthPx, screenHeightPx)
        }
        val customCenterXFraction = FloatBallLayout.coerceCustomCenterXFraction(center.first / screenWidthPx)
        val yFraction = FloatBallLayout.coercePositionYFraction(center.second / screenHeightPx)
        return customCenterXFraction to yFraction
    }

    private fun persistBallCenterFraction() {
        val (xFraction, yFraction) = currentBallCenterFraction() ?: return
        onPositionPersisted?.invoke(xFraction, yFraction)
    }

    private fun showCursorPickPreview(@Suppress("UNUSED_PARAMETER") screenX: Float, @Suppress("UNUSED_PARAMETER") screenY: Float) {
        if (isDragging) return
        passivePickPreviewAnchor = computePassivePickAnchor()
        cursorPreviewActive = true
        passivePickPreviewAlpha = pickPreviewAlpha(0f)
        syncCursorPreviewAppearance()
        cursorPreviewView?.visibility = View.VISIBLE
    }

    private fun prepareLineDragStateForSlop() {
        val settings = settingsState?.value ?: return
        val dockedSide = FloatBallLayout.resolvedActiveSide(settings)
        val bothEdges = FloatBallLayout.usesEdgeLines(settings)
        if (!bothEdges) return
        dragOriginatedFromLine = true
        slopPhaseFromLineStrip = true
        dragActiveSideOverride = FloatBallSide.opposite(dockedSide)
    }

    private fun revertLineDragStateForSlop() {
        if (!slopPhaseFromLineStrip) return
        slopPhaseFromLineStrip = false
        dragOriginatedFromLine = false
        dragActiveSideOverride = null
    }

    private fun resolveDragBallCenter(
        settings: AppSettings,
        metrics: android.util.DisplayMetrics,
        touchDownX: Float,
        touchDownY: Float,
        fingerX: Float,
        fingerY: Float,
        fromEdgeGesture: Boolean,
        ballHeightPx: Int,
        marginPx: Int,
        screenWidth: Float,
        screenHeight: Float,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): Pair<Float, Float> {
        val activeSide = if (fromEdgeGesture) {
            if (fingerX < screenWidth / 2f) FloatBallSide.LEFT else FloatBallSide.RIGHT
        } else {
            effectiveActiveSide(settings)
        }
        val halfHeight = ballHeightPx / 2f
        val minCenterY = marginPx + halfHeight
        val maxCenterY = screenHeight - marginPx - halfHeight
        return when {
            dragOriginatedFromLine -> {
                touchDownX to touchDownY.coerceIn(minCenterY, maxCenterY)
            }
            fromEdgeGesture -> fingerX to fingerY
            else -> FloatBallLayout.ballCenterPx(
                settings,
                metrics,
                activeSide,
                screenWidthPx,
                screenHeightPx
            )
        }
    }

    private fun armDragSessionAtFinger(
        settings: AppSettings,
        metrics: android.util.DisplayMetrics,
        touchDownX: Float,
        touchDownY: Float,
        fingerX: Float,
        fingerY: Float,
        fromEdgeGesture: Boolean
    ) {
        val density = metrics.density
        val bounds = FloatBallScreenMetrics.bounds(displayView!!.context, windowManager)
        val ballSize = FloatBallLayout.ballSize(settings, density)
        val marginPx = (EDGE_MARGIN_DP * density).roundToInt()
        val screenWidth = bounds.width
        val screenHeight = bounds.height
        val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(displayView!!.context, windowManager)
        val (ballCenterX, ballCenterY) = resolveDragBallCenter(
            settings = settings,
            metrics = metrics,
            touchDownX = touchDownX,
            touchDownY = touchDownY,
            fingerX = fingerX,
            fingerY = fingerY,
            fromEdgeGesture = fromEdgeGesture,
            ballHeightPx = ballSize.heightPx,
            marginPx = marginPx,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx
        )
        val pickDockSide = when {
            fromEdgeGesture -> if (fingerX < screenWidth / 2f) FloatBallSide.LEFT else FloatBallSide.RIGHT
            else -> effectiveActiveSide(settings)
        }
        dragSession.armAtTouch(
            settings = settings,
            touchDownX = touchDownX,
            touchDownY = touchDownY,
            fingerX = fingerX,
            fingerY = fingerY,
            ballCenterX = ballCenterX,
            ballCenterY = ballCenterY,
            ballSize = ballSize,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density,
            pickDockSide = pickDockSide
        )
        dragScreenBounds = bounds
    }

    private fun updateSlopPhaseBallFollow(
        fromLineStrip: Boolean,
        touchDownX: Float,
        touchDownY: Float,
        fingerX: Float,
        fingerY: Float
    ) {
        val view = displayView ?: return
        val settings = settingsState?.value ?: return
        if (!slopPhaseBallFollowActive) {
            if (fromLineStrip) {
                prepareLineDragStateForSlop()
            } else {
                expandBallTouchCapture()
            }
            slopPhaseBallFollowActive = true
            slopPhaseFromLineStrip = fromLineStrip
            cancelCursorPickPreview()
            setDragging(true)
            ballDraggingState?.value = true
            ballIconView?.setDragging(true)
            armDragSessionAtFinger(
                settings = settings,
                metrics = view.resources.displayMetrics,
                touchDownX = touchDownX,
                touchDownY = touchDownY,
                fingerX = fingerX,
                fingerY = fingerY,
                fromEdgeGesture = false
            )
            flushDragChromeLayout(syncAnchorState = true)
        } else {
            dragSession.updateFingerPosition(fingerX, fingerY)
        }
        updatePickAndBallFromFinger(moveBallWindow = true)
        flushDragChromeLayout()
    }

    private fun cancelSlopPhaseBallFollow() {
        if (!slopPhaseBallFollowActive) return
        slopPhaseBallFollowActive = false
        revertLineDragStateForSlop()
        dragSession.reset()
        sceneState?.ballCenterPx?.value = null
        if (isDragging) {
            setDragging(false)
            settingsState?.value?.let { restorePassiveOverlayLayout(it, fixZOrder = false, deferLineRestore = true) }
        }
    }

    private fun updateCursorPickPreviewAlpha(progress: Float) {
        if (!cursorPreviewActive || isDragging) return
        passivePickPreviewAlpha = pickPreviewAlpha(progress)
        syncCursorPreviewAppearance()
    }

    private fun cancelCursorPickPreview() {
        if (!cursorPreviewActive || isDragging) return
        cursorPreviewActive = false
        passivePickPreviewAlpha = 1f
        passivePickPreviewAnchor = null
        cursorPreviewView?.visibility = View.GONE
        syncCursorPreviewAppearance()
    }

    private fun computePassivePickAnchor(): Offset {
        val view = displayView ?: return Offset.Zero
        val settings = settingsState?.value ?: return Offset.Zero
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val bounds = FloatBallScreenMetrics.bounds(view.context, windowManager)
        val ballSize = FloatBallLayout.ballSize(settings, density)
        val (screenWidthPx, screenHeightPx) = FloatBallScreenMetrics.sizePx(view.context, windowManager)
        val activeSide = effectiveActiveSide(settings)
        val (ballCenterX, ballCenterY) = FloatBallLayout.ballCenterPx(
            settings,
            metrics,
            activeSide,
            screenWidthPx,
            screenHeightPx
        )
        return FloatBallPickAnchor.pickPointForBallCenter(
            settings = settings,
            ballCenterX = ballCenterX,
            ballCenterY = ballCenterY,
            ballHeightPx = ballSize.heightPx.toFloat(),
            screenWidth = bounds.width,
            screenHeight = bounds.height,
            density = density,
            dockSide = activeSide
        )
    }

    private fun pickPreviewAlpha(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        return PICK_PREVIEW_ALPHA_MIN + (1f - PICK_PREVIEW_ALPHA_MIN) * eased
    }

    private fun showCursorAtScreenTouch(
        touchDownX: Float,
        touchDownY: Float,
        fingerX: Float,
        fingerY: Float,
        deferBallWindowMutation: Boolean = false,
        fromEdgeGesture: Boolean = false
    ) {
        val view = displayView ?: return
        val settings = settingsState?.value ?: return
        val metrics = view.resources.displayMetrics

        val promotingFromSlop = slopPhaseBallFollowActive
        if (!promotingFromSlop) {
            armDragSessionAtFinger(
                settings = settings,
                metrics = metrics,
                touchDownX = touchDownX,
                touchDownY = touchDownY,
                fingerX = fingerX,
                fingerY = fingerY,
                fromEdgeGesture = fromEdgeGesture
            )
            setDragging(true)
        } else {
            slopPhaseBallFollowActive = false
            dragSession.updateFingerPosition(fingerX, fingerY)
        }

        if (!fromEdgeGesture && dragOriginatedFromLine && !deferBallWindowMutation) {
            setBallTouchable(false)
        }
        // 移动模式：这条拖动只用来挪球 —— 不进取词（不显示光标/放大镜/选区）。
        if (moveModeActive) {
            cancelDragChromeLayoutFrame()
            cancelCursorCommitFrame()
            pendingPickAnchor = null
            selectionStartState?.value = null
            selectionPreviewBoundsState?.value = null
            cursorVisibleState?.value = false
            cursorPausedState?.value = false
            dragScreenBounds = FloatBallScreenMetrics.bounds(view.context, windowManager)
            setCursorLayersVisible(false)
            // 跟手更新球窗口（applyDragBallLayout 用的是 dragSession 的手指坐标）
            flushDragChromeLayout()
            return
        }
        cancelDragChromeLayoutFrame()
        cancelCursorCommitFrame()
        pendingPickAnchor = null
        cancelBoundsLookupGeneration()
        cancelCacheRefresh()
        cancelInitialPreviewBoundsCache()
        lastCacheRefreshX = Float.NaN
        lastCacheRefreshY = Float.NaN
        dragScreenBounds = FloatBallScreenMetrics.bounds(view.context, windowManager)
        PickPrefetchCache.invalidate()
        FloatBallPreviewBoundsCache.invalidate()
        regionalPickActive = false
        selectionStartState?.value = null
        selectionPreviewBoundsState?.value = null
        cursorVisibleState?.value = true
        cursorPausedState?.value = false
        cursorPreviewActive = false
        passivePickPreviewAlpha = 1f
        updatePickAndBallFromFinger(moveBallWindow = true)
        flushDragChromeLayout(syncAnchorState = true)
        syncCursorPreviewAppearance()
        if (!promotingFromSlop) {
            applyImmediateDragStartVisual(deferBallWindowMutation || fromEdgeGesture)
        } else {
            setCursorLayersVisible(true)
            settingsState?.value?.let { updateChromeVisibility(it) }
        }
        if (cursorPreviewView?.visibility != View.VISIBLE) {
            setCursorLayersVisible(true)
        }
        lastPauseScheduleX = Float.NaN
        lastPauseScheduleY = Float.NaN
        schedulePauseTimer()
        val anchor = currentPickAnchor()
        if (anchor != null) {
            lastPauseScheduleX = anchor.x
            lastPauseScheduleY = anchor.y
        }
        scheduleInitialPreviewBoundsCache()
    }

    private fun setBallTouchable(touchable: Boolean) {
        touchHost?.ballStripTouchable = touchable
    }

    private fun clearCursorUi(restoreLayout: Boolean = true) {
        slopPhaseBallFollowActive = false
        slopPhaseFromLineStrip = false
        dragOriginatedFromLine = false
        lineDragEndedWithGesture = false
        dragActiveSideOverride = null
        selectionPreviewBoundsState?.value = null
        setBallTouchable(true)
        if (restoreLayout) {
            settingsState?.value?.let { restorePassiveOverlayLayout(it) }
        }
        cancelPauseTimer()
        cancelCacheRefresh()
        cancelInitialPreviewBoundsCache()
        cancelDragChromeLayoutFrame()
        cancelCursorCommitFrame()
        boundsLookupGeneration++
        FloatBallPreviewBoundsCache.invalidate()
        lastPauseScheduleX = Float.NaN
        lastPauseScheduleY = Float.NaN
        lastCacheRefreshX = Float.NaN
        lastCacheRefreshY = Float.NaN
        lastDragPasteHintActive = false
        lockedDragPasteEditableRect = null
        dragPastePickUpgraded = false
        cancelDragPastePickUpgradeTimer()
        dragScreenBounds = null
        currentDragPickAnchor = Offset.Zero
        regionalPickActive = false
        passivePickPreviewAlpha = 1f
        dragSession.reset()
        hideGestureHintWindow()
        cursorPreviewActive = false
        passivePickPreviewAlpha = 1f
        cursorVisibleState?.value = false
        cursorPausedState?.value = false
        selectionStartState?.value = null
        setCursorLayersVisible(false)
    }

    private fun hideCursor(restorePassive: Boolean = true) {
        activeSideAtDragStart = null
        if (isDragging) {
            clearCursorUi(restoreLayout = false)
            setDragging(false)
            setBallTouchHostPassthrough(false)
            releaseAllTouchCaptures()
            syncTouchCaptureLayouts()
            if (restorePassive) {
                settingsState?.value?.let {
                    restorePassiveOverlayLayout(it, fixZOrder = false, deferLineRestore = true)
                }
            }
            flushDeferredChromeRaiseIfNeeded()
            return
        }
        clearCursorUi(restoreLayout = restorePassive)
    }

    private fun onDragMoved() {
        // 移动模式：只挪球，不调度取词那一套（暂停计时、预览缓存）。
        if (moveModeActive) return
        val start = selectionStartState?.value
        if (start != null) {
            val anchor = currentPickAnchor()
            if (anchor != null) {
                updateRegionalPickModeOnMove(anchor, start)
            }
            syncCursorPreviewAppearance()
            return
        }
        if (cursorPausedState?.value == true) {
            cursorPausedState?.value = false
            syncCursorChromeAppearance()
        }
        schedulePauseTimerIfMoved()
        schedulePreviewCacheRefresh()
    }

    private fun updateRegionalPickModeOnMove(anchor: Offset, start: Offset) {
        val density = displayView?.resources?.displayMetrics?.density ?: 1f
        val cancelSlopDp = settingsState?.value?.floatBallRegionalCancelSlopDp ?: 16f
        val movePx = cancelSlopDp * density
        val distFromStart = hypot(anchor.x - start.x, anchor.y - start.y)

        if (regionalPickActive) {
            if (distFromStart < movePx) {
                restoreActiveDragFromPauseOrigin()
            }
            return
        }
        if (distFromStart >= movePx) {
            enterRegionalPickMode()
        }
    }

    private fun enterRegionalPickMode() {
        regionalPickActive = true
        boundsLookupGeneration++
        PickPrefetchCache.invalidate()
        lockedDragPasteEditableRect = null
        dragPastePickUpgraded = false
        cancelDragPastePickUpgradeTimer()
        selectionPreviewBoundsState?.value = null
        syncCursorChromeAppearance()
    }

    /** Screenshot mode: plus back at pause origin → red cross, resume normal drag. */
    private fun restoreActiveDragFromPauseOrigin() {
        regionalPickActive = false
        cursorPausedState?.value = false
        selectionStartState?.value = null
        unlockActivePickGestureFromPause()
        cancelPauseTimer()
        lockedDragPasteEditableRect = null
        dragPastePickUpgraded = false
        cancelDragPastePickUpgradeTimer()
        lastPauseScheduleX = Float.NaN
        lastPauseScheduleY = Float.NaN
        boundsLookupGeneration++
        PickPrefetchCache.invalidate()
        FloatBallPickResultPanel.releaseWarmUpShell()
        applyPreviewBoundsFromCache()
        syncCursorChromeAppearance()
    }

    /** Reset pause countdown when finger moves beyond the cancel slop distance. */
    private fun schedulePauseTimerIfMoved() {
        val anchor = currentPickAnchor() ?: return
        val density = displayView?.resources?.displayMetrics?.density ?: 1f
        val cancelSlopDp = settingsState?.value?.floatBallRegionalCancelSlopDp ?: 16f
        val movePx = cancelSlopDp * density
        if (!lastPauseScheduleX.isNaN() && !lastPauseScheduleY.isNaN()) {
            if (hypot(anchor.x - lastPauseScheduleX, anchor.y - lastPauseScheduleY) < movePx) {
                return
            }
        }
        lastPauseScheduleX = anchor.x
        lastPauseScheduleY = anchor.y
        schedulePauseTimer()
    }

    /** Yellow cross: finger held still long enough to lock regional pick start. */
    private fun schedulePauseTimer() {
        cancelPauseTimer()
        val runnable = Runnable { onCursorPaused() }
        pauseRunnable = runnable
        val pauseMs = (settingsState?.value?.floatBallHoverPauseDelayMs ?: 400).toLong()
        mainHandler.postDelayed(runnable, pauseMs)
    }

    /** FV G4: async full-tree scan into preview bounds cache. */
    private fun startPreviewBoundsCache() {
        val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return
        val includeEditable = settingsState?.value?.floatBallDragPasteEnabled == true
        FloatBallPreviewBoundsCache.refresh(
            service = service,
            includeEditable = includeEditable,
            onReady = {
                if (!isDragging || cursorVisibleState?.value != true) return@refresh
                applyPreviewBoundsFromCache()
            }
        )
    }

    /** FV G4: first cache build waits ~300ms so fast drags skip the heavy a11y walk. */
    private fun scheduleInitialPreviewBoundsCache() {
        cancelInitialPreviewBoundsCache()
        val runnable = Runnable {
            initialCacheRunnable = null
            if (!isDragging || cursorVisibleState?.value != true) return@Runnable
            val anchor = currentPickAnchor()
            if (anchor != null) {
                lastCacheRefreshX = anchor.x
                lastCacheRefreshY = anchor.y
            }
            startPreviewBoundsCache()
        }
        initialCacheRunnable = runnable
        mainHandler.postDelayed(runnable, INITIAL_CACHE_DELAY_MS)
    }

    private fun cancelInitialPreviewBoundsCache() {
        initialCacheRunnable?.let { mainHandler.removeCallbacks(it) }
        initialCacheRunnable = null
    }

    /** FV O0: rebuild cache after finger moves, without blocking MOVE. */
    private fun schedulePreviewCacheRefresh() {
        if (cursorPausedState?.value == true) return
        if (selectionStartState?.value != null) return
        val anchor = currentPickAnchor() ?: return
        val density = displayView?.resources?.displayMetrics?.density ?: 1f
        val movePx = CACHE_REFRESH_MOVE_DP * density
        if (!lastCacheRefreshX.isNaN() && !lastCacheRefreshY.isNaN()) {
            if (hypot(anchor.x - lastCacheRefreshX, anchor.y - lastCacheRefreshY) < movePx) {
                return
            }
        }
        cancelCacheRefresh()
        val runnable = Runnable {
            cacheRefreshRunnable = null
            if (!isDragging || cursorVisibleState?.value != true) return@Runnable
            if (cursorPausedState?.value == true) return@Runnable
            val latest = currentPickAnchor() ?: return@Runnable
            lastCacheRefreshX = latest.x
            lastCacheRefreshY = latest.y
            startPreviewBoundsCache()
        }
        cacheRefreshRunnable = runnable
        mainHandler.postDelayed(runnable, CACHE_REFRESH_MS)
    }

    /** FV o1.r(x,y): instant hit-test on cached rects — coalesced to one pass per animation frame. */
    private fun applyPreviewBoundsFromCache() {
        if (!isDragging || cursorVisibleState?.value != true) return
        if (regionalPickActive) return
        if (cursorPausedState?.value == true && selectionStartState?.value != null) return
        val anchor = currentPickAnchor() ?: return
        val pasteHintActive = settingsState?.value?.floatBallDragPasteEnabled == true &&
            !dragPastePickUpgraded &&
            FloatBallPreviewBoundsCache.hitTestEditableAt(anchor.x, anchor.y) != null
        if (pasteHintActive != lastDragPasteHintActive) {
            lastDragPasteHintActive = pasteHintActive
            syncCursorChromeAppearance()
        }
        val bounds = FloatBallPreviewBoundsCache.hitTestAt(anchor.x, anchor.y) ?: return
        val current = selectionPreviewBoundsState?.value
        val density = displayView?.resources?.displayMetrics?.density ?: 1f
        val slopPx = (2f * density).roundToInt()
        if (current == null || !previewBoundsStableEquals(current, bounds, slopPx)) {
            selectionPreviewBoundsState?.value = bounds
            syncCursorChromeAppearance()
        }
    }

    private fun lockActivePickGestureFromPause() {
        if (dragOriginatedFromLine) {
            lineTouchHost?.lockPickFromPause()
        } else {
            touchHost?.lockPickFromPause()
        }
    }

    /** 黄框/A 悬停：界面已暂停，松手应提交取词而非 cancel。 */
    private fun hasPickPauseIntentForCommit(): Boolean {
        if (cursorPausedState?.value == true) return true
        if (selectionStartState?.value != null) return true
        return false
    }

    private fun unlockActivePickGestureFromPause() {
        touchHost?.unlockPickFromPause()
        lineTouchHost?.unlockPickFromPause()
    }

    private fun onCursorPaused() {
        if (cursorVisibleState?.value != true) return
        if (cursorPausedState?.value == true) return
        val anchor = currentPickAnchor() ?: return
        cancelPauseTimer()
        regionalPickActive = false
        cursorAnchorState?.value = anchor
        selectionStartState?.value = anchor
        cursorPausedState?.value = true
        lockActivePickGestureFromPause()
        val settings = settingsState?.value
        val editableForPaste = if (settings?.floatBallDragPasteEnabled == true) {
            FloatBallPreviewBoundsCache.hitTestEditableAt(anchor.x, anchor.y)
        } else {
            null
        }
        if (editableForPaste != null) {
            dragPastePickUpgraded = false
            cancelDragPastePickUpgradeTimer()
            lockedDragPasteEditableRect = Rect(editableForPaste)
            // Paste mode: frame the editable rect, not the outer text container (QQ zaq).
            selectionPreviewBoundsState?.value = Rect(editableForPaste)
            scheduleDragPastePickUpgrade()
        } else {
            lockedDragPasteEditableRect = null
            dragPastePickUpgraded = false
            cancelDragPastePickUpgradeTimer()
            val bounds = FloatBallPreviewBoundsCache.hitTestAt(anchor.x, anchor.y)
            if (bounds != null) {
                selectionPreviewBoundsState?.value = bounds
                maybeStartPickPrefetch()
            } else {
                launchPreviewBoundsLookupFallback(anchor)
            }
        }
        syncCursorChromeAppearance()
    }

    /** One-shot tree lookup when cache is not ready at pause time. */
    private fun launchPreviewBoundsLookupFallback(anchor: Offset) {
        val generation = ++boundsLookupGeneration
        val x = anchor.x
        val y = anchor.y
        overlayScope.launch(Dispatchers.Default) {
            val bounds = SlideIndexAccessibilityService.findControlBoundsAt(
                rawX = x,
                rawY = y
            )
            withContext(Dispatchers.Main) {
                if (generation != boundsLookupGeneration) return@withContext
                if (!isDragging || cursorVisibleState?.value != true) return@withContext
                if (cursorPausedState?.value != true) return@withContext
                if (regionalPickActive) return@withContext
                if (bounds != null) {
                    selectionPreviewBoundsState?.value = bounds
                    maybeStartPickPrefetch()
                    syncCursorChromeAppearance()
                }
            }
        }
    }

    private fun cancelBoundsLookupGeneration() {
        boundsLookupGeneration++
    }

    private fun previewBoundsStableEquals(current: Rect, next: Rect, slopPx: Int): Boolean {
        return kotlin.math.abs(current.left - next.left) <= slopPx &&
            kotlin.math.abs(current.top - next.top) <= slopPx &&
            kotlin.math.abs(current.right - next.right) <= slopPx &&
            kotlin.math.abs(current.bottom - next.bottom) <= slopPx
    }

    private fun cancelCacheRefresh() {
        cacheRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
        cacheRefreshRunnable = null
    }

    private fun ensurePreviewBoundsForPick() {
        if (selectionPreviewBoundsState?.value != null) return
        val anchor = currentPickAnchor() ?: return
        val cached = FloatBallPreviewBoundsCache.hitTestAt(anchor.x, anchor.y)
        if (cached != null) {
            selectionPreviewBoundsState?.value = cached
            syncCursorChromeAppearance()
            return
        }
        val bounds = SlideIndexAccessibilityService.findControlBoundsAt(
            rawX = anchor.x,
            rawY = anchor.y
        )
        if (bounds != null) {
            selectionPreviewBoundsState?.value = bounds
            syncCursorChromeAppearance()
        }
    }

    private fun maybeStartPickPrefetch() {
        if (cursorPausedState?.value != true) return
        val bounds = selectionPreviewBoundsState?.value ?: return
        val service = SlideIndexAccessibilityService.accessibilityInstance() ?: return
        PickPrefetchCache.startPreviewA11yPrefetch(
            service = service,
            rect = bounds,
            generation = boundsLookupGeneration
        )
    }

    private fun rectBetween(start: Offset, end: Offset): Rect {
        val left = min(start.x, end.x).roundToInt()
        val top = min(start.y, end.y).roundToInt()
        val right = max(start.x, end.x).roundToInt()
        val bottom = max(start.y, end.y).roundToInt()
        return Rect(left, top, right, bottom)
    }

    private fun cancelPauseTimer() {
        pauseRunnable?.let { mainHandler.removeCallbacks(it) }
        pauseRunnable = null
    }

    /** FV: second dwell on editable upgrades paste hint to a11y text pick (A). */
    private fun scheduleDragPastePickUpgrade() {
        cancelDragPastePickUpgradeTimer()
        if (settingsState?.value?.floatBallDragPasteEnabled != true) return
        val runnable = Runnable { upgradeDragPasteToPick() }
        dragPasteUpgradeRunnable = runnable
        mainHandler.postDelayed(runnable, DRAG_PASTE_PICK_UPGRADE_MS)
    }

    private fun cancelDragPastePickUpgradeTimer() {
        dragPasteUpgradeRunnable?.let { mainHandler.removeCallbacks(it) }
        dragPasteUpgradeRunnable = null
    }

    private fun upgradeDragPasteToPick() {
        dragPasteUpgradeRunnable = null
        if (!isDragging || cursorVisibleState?.value != true) return
        if (cursorPausedState?.value != true) return
        if (settingsState?.value?.floatBallDragPasteEnabled != true) return
        if (regionalPickActive) return
        val anchor = currentPickAnchor() ?: return
        if (FloatBallPreviewBoundsCache.hitTestEditableAt(anchor.x, anchor.y) == null) return
        dragPastePickUpgraded = true
        lockedDragPasteEditableRect = null
        lastDragPasteHintActive = false
        val bounds = FloatBallPreviewBoundsCache.hitTestAt(anchor.x, anchor.y)
        if (bounds != null) {
            selectionPreviewBoundsState?.value = bounds
            maybeStartPickPrefetch()
        } else {
            launchPreviewBoundsLookupFallback(anchor)
        }
        syncCursorChromeAppearance()
    }

    private fun cancelDragChromeLayoutFrame() {
        dragChromeLayoutFrameScheduled = false
        pendingCursorFrameAnchor = null
    }

    private fun scheduleDragChromeLayoutOnNextFrame() {
        val view = displayView ?: cursorPreviewView ?: return
        if (dragChromeLayoutFrameScheduled) return
        dragChromeLayoutFrameScheduled = true
        view.postOnAnimation {
            dragChromeLayoutFrameScheduled = false
            if (!isDragging) return@postOnAnimation
            commitDragChromeLayoutFrame()
        }
    }

    private fun flushDragChromeLayout(syncAnchorState: Boolean = false) {
        cancelDragChromeLayoutFrame()
        if (!isDragging && !syncAnchorState) return
        commitDragChromeLayoutFrame(forceAnchorState = syncAnchorState)
    }

    /** Ball + cursor chrome in one animation tick (FV-style single-frame move). */
    private fun commitDragChromeLayoutFrame(forceAnchorState: Boolean = false) {
        val pick = pendingCursorFrameAnchor ?: currentDragPickAnchor
        pendingCursorFrameAnchor = null
        if (!moveModeActive) {
            syncCursorPreviewAppearance()
        }
        settingsState?.value?.let { applyDragBallLayout(it) }
        // 移动模式下不维护取词光标锚点/预览边界：这一趟拖动只是为了挪球。
        if (moveModeActive) return
        val needsPreviewAnchor = forceAnchorState ||
            cursorPausedState?.value == true ||
            selectionStartState?.value != null
        if (needsPreviewAnchor) {
            cursorAnchorState?.value = pick
        }
        if (isDragging && cursorVisibleState?.value == true) {
            applyPreviewBoundsFromCache()
        }
    }

    private fun cancelCursorCommitFrame() {
        cursorCommitFrameScheduled = false
        pendingPickAnchor = null
    }

    private fun scheduleCursorCommitOnNextFrame() {
        val view = displayView ?: return
        if (cursorCommitFrameScheduled) return
        cursorCommitFrameScheduled = true
        view.postOnAnimation {
            cursorCommitFrameScheduled = false
            if (!isDragging) return@postOnAnimation
            commitPickAnchor()
        }
    }

    private fun commitPickAnchor() {
        val pick = pendingPickAnchor ?: return
        pendingPickAnchor = null
        cursorAnchorState?.value = pick
    }

    private fun applyPickAnchor(pick: Offset) {
        if (isDragging) {
            currentDragPickAnchor = pick
            pendingCursorFrameAnchor = pick
            return
        }
        pendingPickAnchor = pick
        scheduleCursorCommitOnNextFrame()
    }

    private fun applyDragBallLayout(settings: AppSettings) {
        val view = displayView ?: return
        val metrics = view.resources.displayMetrics
        val ballSize = FloatBallLayout.ballSize(settings, metrics.density)
        val marginPx = FloatBallLayout.marginPx(metrics.density)
        val screenBounds = dragScreenBounds ?: FloatBallScreenMetrics.bounds(view.context, windowManager)
            .also { dragScreenBounds = it }
        val center = dragSession.clampedBallCenter(
            ballSize = ballSize,
            marginPx = marginPx,
            screenWidth = screenBounds.width.roundToInt(),
            screenHeight = screenBounds.height.roundToInt()
        )
        syncDisplayBallLayout(settings, layoutOnly = true, overrideCenter = center)
    }

    private fun updatePickAndBallFromFinger(moveBallWindow: Boolean) {
        val view = displayView ?: return
        val settings = settingsState?.value ?: return
        val metrics = view.resources.displayMetrics
        val density = metrics.density
        val bounds = dragScreenBounds ?: FloatBallScreenMetrics.bounds(view.context, windowManager)
            .also { dragScreenBounds = it }
        val ballSize = FloatBallLayout.ballSize(settings, density)
        val marginPx = (EDGE_MARGIN_DP * density).roundToInt()
        val screenWidth = bounds.width
        val screenHeight = bounds.height

        // 移动模式：这次拖动只是挪球，不计算/提交取词锚点。
        if (!moveModeActive) {
            val pick = dragSession.computePick(
                settings = settings,
                ballSize = ballSize,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                density = density,
                marginPx = marginPx
            )
            applyPickAnchor(pick)
        }

        if (!moveBallWindow) return
        flushDragChromeLayout()
    }

    private fun floatBallStyleSignature(settings: AppSettings?): String {
        if (settings == null) return ""
        return buildString {
            append(settings.floatBallStyleType.storageKey)
            append('|')
            append(settings.floatBallGifUri)
            append('|')
            append(settings.floatBallCustomImageUri)
            append('|')
            // 开关与宽高比会改变**窗口尺寸**（不只画面）：签名里少了它们，改开关后不会重新 layout，
            // 球窗口还是旧的方形，图片被挤在方框里。
            append(settings.floatBallCustomImageNoCrop)
            append('|')
            append(settings.floatBallCustomImageAspect)
            append('|')
            append(settings.floatBallSlideshowUris.joinToString(","))
        }
    }

    private fun isLandscapeLayout(): Boolean {
        val view = displayView ?: touchHost ?: lineTouchHost ?: return false
        return view.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
}
