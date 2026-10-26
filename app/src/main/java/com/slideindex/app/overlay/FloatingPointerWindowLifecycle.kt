package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.monitoring.OverlayPerformanceMonitorBinding
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.PermissionHelper
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal class FloatingPointerWindowLifecycle(
    private val window: FloatingPointerOverlayWindow
) {
    private val mainHandler get() = window.mainHandler

    fun show(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float?,
        anchorRawY: Float?,
        continueTouch: Boolean,
        attachDeferred: Boolean = false
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                show(context, settings, anchorRawX, anchorRawY, continueTouch, attachDeferred)
            }
            return
        }
        val shouldContinueTouch = continueTouch && anchorRawX != null && anchorRawY != null
        if (window.isShowing) {
            if (window.isVisible) {
                window.settingsState?.value = settings
                OverlayPerformanceMonitorBinding.syncUserPreference(settings, window.appContext)
                window.settingsSync.onSettingsUpdated(settings)
                if (shouldContinueTouch) {
                    val x = anchorRawX
                    val y = anchorRawY
                    Log.i(TAG, "show: continue on visible pointer at ($x, $y)")
                    window.session?.placeAtTouch(x, y, settings)
                    window.continuedGestureActive = true
                    EdgeContinuedOverlayHandoff.begin()
                    window.touchHost?.beginContinuedGesture(
                        x,
                        y,
                        SystemClock.uptimeMillis()
                    )
                }
                return
            }
            cleanup()
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            window.continuedGestureActive = false
            EdgeContinuedOverlayLaunchCoordinator.clearAfterHandoffEnd()
            return
        }

        val hostContext = OverlayDependencyAccess.overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                window.continuedGestureActive = false
                EdgeContinuedOverlayLaunchCoordinator.clearAfterHandoffEnd()
                return
            }

        // Coordinator already posts off the edge touch stack; only defer once for direct callers.
        if (shouldContinueTouch && !attachDeferred) {
            mainHandler.post {
                show(context, settings, anchorRawX, anchorRawY, continueTouch, attachDeferred = true)
            }
            return
        }

        val overlayContext = OverlayCompose.themedContext(hostContext)
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val dm = hostContext.resources.displayMetrics
        val screenBounds = overlayScreenBounds(wm, dm)
        val visible = mutableStateOf(false)
        val settingsHolder = mutableStateOf(settings)
        val pointerSession = FloatingPointerSession(
            density = dm.density,
            screenWidth = screenBounds.width,
            screenHeight = screenBounds.height,
            settingsSource = { settingsHolder.value }
        )
        if (anchorRawX != null && anchorRawY != null && !shouldContinueTouch) {
            pointerSession.placeAtTouch(anchorRawX, anchorRawY, settings)
        } else if (anchorRawX == null || anchorRawY == null) {
            pointerSession.awaitingPlacement = true
        }

        val displayDialogOwner = OverlayComposeOwner()
        val displayCompose = OverlayCompose.createComposeView(overlayContext, displayDialogOwner).apply {
            setContent {
                val currentSettings by settingsHolder
                val isVisible by visible
                FloatingPointerDisplay(
                    session = pointerSession,
                    settings = currentSettings,
                    visible = isVisible
                )
            }
        }

        val touchLayout = FloatingPointerHostLayout(
            context = overlayContext,
            session = pointerSession,
            settingsProvider = { settingsHolder.value },
            fingerTrackingMove = { fingerRawX, fingerRawY, fingerLocalX, fingerLocalY ->
                if (window.session?.gestureCaptureActive != true &&
                    window.session?.gestureReplayActive?.value != true
                ) {
                    followFingerTouchCapture(fingerRawX, fingerRawY, fingerLocalX, fingerLocalY)
                }
            },
            pointerPositionChanged = { pointerX, pointerY ->
                syncPassthroughForPointerPosition(pointerX, pointerY)
            },
            gestureEnd = { centerX, centerY, _ ->
                window.touchCaptureUserCollapsed = true
                if (window.session?.gestureCaptureActive != true &&
                    window.session?.gestureReplayActive?.value != true
                ) {
                    collapseTouchCapture(centerX, centerY, forceCollapse = true)
                }
            },
            pointerClick = { rawX, rawY -> window.runPointerTap(rawX, rawY) },
            pointerClickAndDismiss = { rawX, rawY -> window.runPointerTapThenDismiss(rawX, rawY) },
            outsideDismissPrepare = { window.runOutsideDismissPrepare() },
            quickSwipeDismiss = { window.runQuickSwipeDismiss() },
            dismiss = { window.dismiss() },
            radialMenuOpened = { expandTouchCapture() },
            radialMenuClosed = {
                window.session?.let {
                    if (it.gestureCaptureActive) return@let
                    collapseTouchCapture(it.joystickCenterX.floatValue, it.joystickCenterY.floatValue, forceCollapse = true)
                }
            },
            radialMenuAction = { slotIndex, fingerStillDown ->
                window.executeRadialSlotAction(slotIndex, fingerStillDown)
            },
            onExpandTouchCapture = { expandTouchCapture() },
            joystickLongPressAction = { action ->
                window.executeJoystickLongPressAction(action)
            },
            startPendingGestureCapture = { action ->
                window.startPendingGestureCapture(action)
            },
            activity = { window.settingsSync.resetIdleTimer() },
            haptic = { displayCompose.let { HapticHelper.appTick(it, settingsHolder.value) } },
            dismissOnOutsideTouch = window::shouldDismissOnOutsideTouch,
            touchCycleComplete = { window.onTouchCycleComplete() },
            swallowInjectEcho = window::shouldSwallowInjectEcho,
            onEnsureTouchOverlayInteractive = { setTouchOverlayPassthrough(false) },
            resolveFingerLocalInTouchOverlay = { rawX, rawY -> fingerLocalInTouchOverlay(rawX, rawY) },
            onFinishEdgeHandoffTouchCapture = { rawX, rawY -> finishEdgeHandoffTouchCapture(rawX, rawY) },
            pointerTapInjectionActive = { window.isPointerTapInFlight }
        )

        val displayParams = buildDisplayParams(hostContext)
        val touchParams = if (shouldContinueTouch) {
            buildEdgeHandoffTouchParams(hostContext)
        } else if (pointerSession.awaitingPlacement) {
            buildExpandedTouchParams(hostContext)
        } else {
            buildCollapsedTouchParams(hostContext, pointerSession, settings)
        }

        val displayAdded = runCatching { wm.addView(displayCompose, displayParams) }
            .onFailure { Log.e(TAG, "display addView failed", it) }
            .isSuccess
        if (!displayAdded) {
            displayDialogOwner.destroy()
            return
        }

        val touchAdded = runCatching { wm.addView(touchLayout, touchParams) }
            .onFailure { Log.e(TAG, "touch addView failed", it) }
            .isSuccess
        if (!touchAdded) {
            runCatching { wm.removeView(displayCompose) }
            displayDialogOwner.destroy()
            return
        }

        window.windowManager = wm
        window.displayView = displayCompose
        window.touchHost = touchLayout
        window.displayOwner = displayDialogOwner
        window.visibleState = visible
        window.settingsState = settingsHolder
        window.session = pointerSession
        pointerSession.onEdgeActionTriggered = { action ->
            val currentSettings = settingsHolder.value
            displayCompose.let { HapticHelper.appTick(it, currentSettings) }
            window.executeEdgeAction(action)
        }
        window.displayLayoutParams = displayParams
        window.touchLayoutParams = touchParams
        window.appContext = hostContext
        val deps = OverlayDependencyAccess.overlayDependencies(hostContext)
            ?: run {
                Log.w(TAG, "show: accessibility service deps unavailable")
                runCatching { wm.removeView(displayCompose) }
                displayDialogOwner.destroy()
                return
            }
        window.actionExecutor = ActionExecutor(
            context = hostContext,
            appRepository = deps.appRepository,
            clickPassthroughHandler = null,
            overlayBrightness = null,
            side = PanelSide.LEFT,
            onShellCommandsPersist = { commands ->
                window.overlayScope.launch {
                    deps.settingsRepository.setShellCommands(commands)
                }
            }
        )
        registerScreenOffReceiver(hostContext)
        OverlayPerformanceMonitorBinding.onOverlayShown(settings, hostContext)
        window.settingsSync.startSettingsSync(deps, settingsHolder)
        window.settingsSync.resetIdleTimer()
        beginOutsideDismissGrace()

        window.continuedGestureActive = shouldContinueTouch
        if (shouldContinueTouch) {
            EdgeContinuedOverlayHandoff.begin()
            touchLayout.beginContinuedGesture(
                anchorRawX,
                anchorRawY,
                SystemClock.uptimeMillis()
            )
            Log.i(TAG, "show: edge handoff ready at ($anchorRawX, $anchorRawY)")
        }
        displayCompose.post {
            visible.value = true
            setTouchOverlayPassthrough(false)
        }
    }

    fun toggle(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float?,
        anchorRawY: Float?,
        continueTouch: Boolean
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { window.toggle(context, settings, anchorRawX, anchorRawY, continueTouch) }
            return
        }
        if (window.isShowing && window.isVisible) {
            if (anchorRawX != null && anchorRawY != null) {
                resummonAtAnchor(context, settings, anchorRawX, anchorRawY, continueTouch)
            } else {
                dismiss()
            }
            return
        }
        show(context, settings, anchorRawX, anchorRawY, continueTouch)
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { window.dismiss() }
            return
        }
        val visible = window.visibleState ?: return
        if (!visible.value) {
            cleanup()
            return
        }
        window.session?.clearTrail()
        window.session?.let {
            collapseTouchCapture(
                it.joystickCenterX.floatValue,
                it.joystickCenterY.floatValue,
                forceCollapse = true
            )
        }
        visible.value = false
        scheduleCleanup()
    }

    /** Tear down without presence animation (one-shot click-dismiss / already-detached chrome). */
    fun dismissImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissImmediate() }
            return
        }
        cancelPendingCleanup()
        window.session?.continuedEdgeSessionActive?.value = false
        window.session?.clearTrail()
        window.visibleState?.value = false
        cleanup()
    }

    fun cleanup() {
        OverlayPerformanceMonitorBinding.onOverlayHidden(window.appContext)
        cancelPendingCleanup()
        window.settingsSync.cancelSettingsSync()
        window.settingsSync.cancelIdleTimer()
        window.outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
        window.outsideDismissGraceRunnable = null
        window.outsideDismissSuppressed = false
        val wm = window.windowManager
        window.displayView?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        window.touchHost?.let { view -> wm?.let { runCatching { it.removeView(view) } } }
        window.screenOffDismissReceiver.unregister()
        val displayView = window.displayView
        val displayOwner = window.displayOwner
        OverlayCompose.teardownOverlayCompose(displayView, displayOwner)
        window.displayOwner = null
        window.displayView = null
        window.touchHost = null
        window.windowManager = null
        window.visibleState = null
        window.settingsState = null
        window.session = null
        window.displayLayoutParams = null
        window.touchLayoutParams = null
        window.appContext = null
        window.actionExecutor = null
        window.touchCaptureUserCollapsed = false
        window.isPointerTapInFlight = false
        window.pointerTapEchoGuard.reset()
        window.dismissAfterPointerTap = false
        window.captureSuppressed = false
        window.pointerTapOutsideSuppressUntilMs = 0L
        window.pendingPointerTaps.clear()
        window.pendingPointerSwipeRunnable?.let { mainHandler.removeCallbacks(it) }
        window.pendingPointerSwipeRunnable = null
        window.isPointerSwipeInFlight = false
        window.continuedGestureActive = false
        EdgeContinuedOverlayHandoff.clearIfInactive()
    }

    fun collapseTouchCapture(
        centerX: Float,
        centerY: Float,
        @Suppress("UNUSED_PARAMETER") forceCollapse: Boolean = false
    ) {
        val pointerSession = window.session ?: return
        if (pointerSession.radialMenuActive.value) return
        val view = window.touchHost ?: return
        val wm = window.windowManager ?: return
        val params = window.touchLayoutParams ?: return
        val settings = window.settingsState?.value ?: return
        val size = pointerSession.touchCaptureDiameterPx(settings).roundToInt()
        val radius = pointerSession.touchCaptureRadiusPx(settings)
        val maxX = (pointerSession.screenWidth - size).roundToInt().coerceAtLeast(0)
        val maxY = (pointerSession.screenHeight - size).roundToInt().coerceAtLeast(0)
        val targetX = (centerX - radius).roundToInt().coerceIn(0, maxX)
        val targetY = (centerY - radius).roundToInt().coerceIn(0, maxY)
        if (params.width == size &&
            params.height == size &&
            params.x == targetX &&
            params.y == targetY &&
            pointerSession.joystickCenterX.floatValue == centerX &&
            pointerSession.joystickCenterY.floatValue == centerY
        ) {
            return
        }
        pointerSession.joystickCenterX.floatValue = centerX
        pointerSession.joystickCenterY.floatValue = centerY
        params.width = size
        params.height = size
        params.x = targetX
        params.y = targetY
        applyCollapsedTouchFlags(params)
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "collapseTouchCapture failed", it) }
    }

    /**
     * Leaves edge-handoff full-screen NOT_TOUCHABLE mode and activates the QC-style tracker.
     */
    fun finishEdgeHandoffTouchCapture(fingerRawX: Float, fingerRawY: Float) {
        val pointerSession = window.session ?: return
        val view = window.touchHost ?: return
        val wm = window.windowManager ?: return
        val params = window.touchLayoutParams ?: return
        val settings = window.settingsState?.value ?: return
        val size = pointerSession.touchCaptureDiameterPx(settings).roundToInt()
        val radius = pointerSession.touchCaptureRadiusPx(settings)
        val maxX = (pointerSession.screenWidth - size).roundToInt().coerceAtLeast(0)
        val maxY = (pointerSession.screenHeight - size).roundToInt().coerceAtLeast(0)
        val targetX = (fingerRawX - radius).roundToInt().coerceIn(0, maxX)
        val targetY = (fingerRawY - radius).roundToInt().coerceIn(0, maxY)
        pointerSession.joystickCenterX.floatValue = fingerRawX
        pointerSession.joystickCenterY.floatValue = fingerRawY
        params.width = size
        params.height = size
        params.x = targetX
        params.y = targetY
        applyCollapsedTouchFlags(params)
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "finishEdgeHandoffTouchCapture failed", it) }
    }

    /**
     * QC `m81` MOVE: tracker window follows the finger while preserving the down-point offset.
     */
    fun followFingerTouchCapture(
        fingerRawX: Float,
        fingerRawY: Float,
        fingerLocalX: Float,
        fingerLocalY: Float
    ) {
        val pointerSession = window.session ?: return
        if (pointerSession.radialMenuActive.value) return
        if (pointerSession.gestureReplayActive.value) return
        val view = window.touchHost ?: return
        val wm = window.windowManager ?: return
        val params = window.touchLayoutParams ?: return
        val settings = window.settingsState?.value ?: return
        if (params.width == WindowManager.LayoutParams.MATCH_PARENT &&
            params.height == WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        val size = pointerSession.touchCaptureDiameterPx(settings).roundToInt()
        val maxX = (pointerSession.screenWidth - size).roundToInt().coerceAtLeast(0)
        val maxY = (pointerSession.screenHeight - size).roundToInt().coerceAtLeast(0)
        val targetX = (fingerRawX - fingerLocalX).roundToInt().coerceIn(0, maxX)
        val targetY = (fingerRawY - fingerLocalY).roundToInt().coerceIn(0, maxY)
        val sizeChanged = params.width != size || params.height != size
        if (!sizeChanged && params.x == targetX && params.y == targetY) {
            return
        }
        params.width = size
        params.height = size
        params.x = targetX
        params.y = targetY
        applyCollapsedTouchFlags(params)
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "followFingerTouchCapture failed", it) }
    }

    fun fingerLocalInTouchOverlay(fingerRawX: Float, fingerRawY: Float): Pair<Float, Float> {
        val params = window.touchLayoutParams
        return if (params != null) {
            (fingerRawX - params.x) to (fingerRawY - params.y)
        } else {
            fingerRawX to fingerRawY
        }
    }

    /**
     * Edge-handoff windows start full-screen with [FLAG_NOT_TOUCHABLE] and no
     * [FLAG_WATCH_OUTSIDE_TOUCH]. Restores collapsed interaction flags so outside taps
     * deliver [MotionEvent.ACTION_OUTSIDE] for click-to-hide.
     */
    private fun applyCollapsedTouchFlags(params: WindowManager.LayoutParams) {
        val passthrough = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (passthrough) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
    }

    fun expandTouchCapture() {
        val view = window.touchHost ?: return
        val wm = window.windowManager ?: return
        val params = window.touchLayoutParams ?: return
        if (params.width == WindowManager.LayoutParams.MATCH_PARENT &&
            params.height == WindowManager.LayoutParams.MATCH_PARENT
        ) {
            return
        }
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.x = 0
        params.y = 0
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "expandTouchCapture failed", it) }
    }

    fun setTouchOverlayPassthrough(passthrough: Boolean) {
        val view = window.touchHost ?: return
        val wm = window.windowManager ?: return
        val params = window.touchLayoutParams ?: return
        if (passthrough) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "setTouchOverlayPassthrough failed", it) }
    }

    /** QC `m81.c`: inject coords inside our touch window would otherwise consume the gesture. */
    fun isPointInsideTouchOverlay(rawX: Float, rawY: Float): Boolean {
        val params = window.touchLayoutParams ?: return false
        val session = window.session ?: return false
        val left = params.x.toFloat()
        val top = params.y.toFloat()
        val right = if (params.width == WindowManager.LayoutParams.MATCH_PARENT) {
            session.screenWidth
        } else {
            left + params.width
        }
        val bottom = if (params.height == WindowManager.LayoutParams.MATCH_PARENT) {
            session.screenHeight
        } else {
            top + params.height
        }
        return rawX in left..right && rawY in top..bottom
    }

    fun isInjectPointInsideTouchOverlay(rawX: Float, rawY: Float): Boolean =
        isPointInsideTouchOverlay(rawX, rawY)

    /** QC `m81.c/d`: swipe inject, drag+pointer-in-window, or idle touchable. */
    fun syncPassthroughForPointerPosition(pointerX: Float, pointerY: Float) {
        if (window.isPointerSwipeInFlight) {
            setTouchOverlayPassthrough(true)
            return
        }
        if (window.isPointerTapInFlight) {
            return
        }
        val session = window.session
        if (session != null &&
            session.joystickActive.value &&
            session.activeTouchPhase.isDragging() &&
            isPointInsideTouchOverlay(pointerX, pointerY)
        ) {
            setTouchOverlayPassthrough(true)
            return
        }
        setTouchOverlayPassthrough(false)
    }

    /** QC `m81.c` at inject instant — idle proactive c passes finger taps to the app below. */
    fun setPassthroughForInjectAt(injectX: Float, injectY: Float) {
        if (isPointInsideTouchOverlay(injectX, injectY)) {
            setTouchOverlayPassthrough(true)
        }
    }

    fun beginOutsideDismissGrace() {
        window.outsideDismissSuppressed = true
        window.outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            window.outsideDismissGraceRunnable = null
            window.outsideDismissSuppressed = false
        }
        window.outsideDismissGraceRunnable = runnable
        mainHandler.postDelayed(runnable, OUTSIDE_DISMISS_GRACE_MS)
    }

    fun shouldDismissOnOutsideTouch(event: MotionEvent): Boolean {
        if (window.outsideDismissSuppressed) return false
        if (window.isAbsorbingPointerTapEcho()) return false
        if (window.shouldSwallowInjectEcho(event)) return false
        if (window.isPointerSwipeInFlight) return false
        if (SystemClock.uptimeMillis() < window.pointerTapOutsideSuppressUntilMs) return false
        val pointerSession = window.session ?: return true
        if (pointerSession.gestureReplayActive.value) return false
        if (pointerSession.gestureCaptureActive) return false
        if (hasReliableOutsideTouchCoordinates(event)) {
            val settings = window.settingsState?.value
            if (settings != null &&
                pointerSession.isFingerInsideTouchCapture(event.rawX, event.rawY, settings)
            ) {
                return false
            }
            if (pointerSession.isNearPointer(event.rawX, event.rawY)) {
                return false
            }
        }
        return true
    }

    fun onTouchCycleComplete() {
        if (window.outsideDismissSuppressed) {
            window.outsideDismissSuppressed = false
            window.outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
            window.outsideDismissGraceRunnable = null
        }
        window.session?.let { session ->
            if (!session.gestureCaptureActive &&
                !session.gestureReplayActive.value &&
                !session.radialMenuActive.value &&
                !session.radialMenuIdle.value &&
                !window.isAbsorbingPointerTapEcho()
            ) {
                collapseTouchCapture(
                    session.joystickCenterX.floatValue,
                    session.joystickCenterY.floatValue,
                    forceCollapse = true
                )
            }
        }
        syncTouchOverlayPassthroughForPointer()
        window.settingsSync.resetIdleTimer()
    }

    /** Passthrough during swipe inject; otherwise keep tracker touchable (QC d). */
    fun syncTouchOverlayPassthroughForPointer() {
        syncPassthroughForPointerPosition(0f, 0f)
    }

    fun onPointerTapInjectionFinished() {
        window.session?.let { session ->
            if (!session.gestureCaptureActive &&
                !session.gestureReplayActive.value &&
                !session.radialMenuActive.value &&
                !session.radialMenuIdle.value
            ) {
                collapseTouchCapture(
                    session.joystickCenterX.floatValue,
                    session.joystickCenterY.floatValue,
                    forceCollapse = true
                )
            }
        }
        syncTouchOverlayPassthroughForPointer()
    }

    private fun resummonAtAnchor(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float,
        continueTouch: Boolean
    ) {
        cancelPendingCleanup()
        window.settingsSync.cancelIdleTimer()
        window.continuedGestureActive = false
        EdgeContinuedOverlayHandoff.clearIfInactive()
        window.outsideDismissSuppressed = false
        window.outsideDismissGraceRunnable?.let { mainHandler.removeCallbacks(it) }
        window.outsideDismissGraceRunnable = null
        window.visibleState?.value = false
        cleanup()
        show(context, settings, anchorRawX, anchorRawY, continueTouch)
    }

    fun scheduleDismissCleanup() {
        scheduleCleanup()
    }

    private fun scheduleCleanup() {
        cancelPendingCleanup()
        val runnable = Runnable {
            window.pendingCleanupRunnable = null
            cleanup()
        }
        window.pendingCleanupRunnable = runnable
        mainHandler.postDelayed(runnable, FLOATING_POINTER_PRESENCE_ANIMATION_MS)
    }

    private fun cancelPendingCleanup() {
        window.pendingCleanupRunnable?.let { mainHandler.removeCallbacks(it) }
        window.pendingCleanupRunnable = null
    }

    private fun registerScreenOffReceiver(context: Context) {
        window.screenOffDismissReceiver.register(context)
    }

    private fun buildDisplayParams(context: Context): WindowManager.LayoutParams {
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
            applyCutoutMode()
        }
    }

    private fun buildEdgeHandoffTouchParams(context: Context): WindowManager.LayoutParams {
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
            x = 0
            y = 0
            applyCutoutMode()
        }
    }

    private fun buildExpandedTouchParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            applyCutoutMode()
        }
    }

    private fun buildCollapsedTouchParams(
        context: Context,
        pointerSession: FloatingPointerSession,
        settings: AppSettings
    ): WindowManager.LayoutParams {
        val size = pointerSession.touchCaptureDiameterPx(settings).roundToInt()
        val radius = pointerSession.touchCaptureRadiusPx(settings)
        val maxX = (pointerSession.screenWidth - size).roundToInt().coerceAtLeast(0)
        val maxY = (pointerSession.screenHeight - size).roundToInt().coerceAtLeast(0)
        return WindowManager.LayoutParams(
            size,
            size,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (pointerSession.joystickCenterX.floatValue - radius)
                .roundToInt()
                .coerceIn(0, maxX)
            y = (pointerSession.joystickCenterY.floatValue - radius)
                .roundToInt()
                .coerceIn(0, maxY)
            applyCutoutMode()
        }
    }

    private fun WindowManager.LayoutParams.applyCutoutMode() {
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun hasReliableOutsideTouchCoordinates(event: MotionEvent): Boolean {
        return event.rawX != 0f || event.rawY != 0f
    }

    private fun overlayScreenBounds(wm: WindowManager, fallback: DisplayMetrics): OverlayScreenBounds {
        val bounds = wm.currentWindowMetrics.bounds
        return OverlayScreenBounds(
            width = bounds.width().toFloat(),
            height = bounds.height().toFloat()
        )
    }

    companion object {
        private const val TAG = "FloatingPointerOverlay"
        private const val OUTSIDE_DISMISS_GRACE_MS = 450L
    }
}
