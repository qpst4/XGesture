package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ComposeView
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.InputTapUtil
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Floating pointer with QC-inspired visuals:
 * - Display layer (NOT_TOUCHABLE): pointer + joystick artwork.
 * - Touch layer: passthrough everywhere except the joystick disc; uses raw screen coords (no jitter).
 */
@SuppressLint("StaticFieldLeak") // Overlay singleton; ActionExecutor/views cleared when window hides
object FloatingPointerOverlayWindow {
    internal val mainHandler = Handler(Looper.getMainLooper())
    internal val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    internal val windowLifecycle by lazy { FloatingPointerWindowLifecycle(this) }
    internal val settingsSync by lazy { FloatingPointerSettingsSync(this) }

    internal var windowManager: WindowManager? = null
    internal var displayView: ComposeView? = null
    internal var touchHost: FloatingPointerHostLayout? = null
    internal var displayOwner: OverlayComposeOwner? = null
    internal var visibleState: MutableState<Boolean>? = null
    internal var settingsState: MutableState<AppSettings>? = null
    internal var session: FloatingPointerSession? = null
    internal var displayLayoutParams: WindowManager.LayoutParams? = null
    internal var touchLayoutParams: WindowManager.LayoutParams? = null
    internal val screenOffDismissReceiver = ScreenOffDismissReceiver { windowLifecycle.dismiss() }
    internal var appContext: Context? = null
    internal var settingsCollectJob: Job? = null
    internal var idleHideRunnable: Runnable? = null
    internal var outsideDismissGraceRunnable: Runnable? = null
    internal var outsideDismissSuppressed = false
    internal var touchCaptureUserCollapsed = false
    internal var actionExecutor: ActionExecutor? = null
    internal var isPointerTapInFlight = false
    internal var pointerTapOutsideSuppressUntilMs = 0L
    internal val pointerTapEchoGuard = PointerTapEchoGuard()
    internal val pendingPointerTaps = ArrayDeque<PendingPointerTap>()
    internal var pendingPointerSwipeRunnable: Runnable? = null
    internal var isPointerSwipeInFlight = false
    internal var continuedGestureActive = false
    internal var pendingCleanupRunnable: Runnable? = null
    /** When true, [onPointerTapComplete] dismisses after the in-flight tap finishes. */
    internal var dismissAfterPointerTap = false
    /** Instantly hides display/touch hosts so system screenshots omit joystick/pointer chrome. */
    internal var captureSuppressed = false

    internal data class PendingPointerTap(
        val rawX: Float,
        val rawY: Float
    )

    val isShowing: Boolean get() = displayView != null
    val isVisible: Boolean get() = visibleState?.value == true

    internal fun injectEchoSlopPx(): Float {
        val pointerSession = session ?: return 48f
        val settings = settingsState?.value
        val pointerRadius = (settings?.floatingPointerPointerDiameterPx ?: 48f) / 2f
        return pointerRadius + pointerSession.density * 16f
    }

    internal fun isAbsorbingPointerTapEcho(): Boolean =
        pointerTapEchoGuard.isActive ||
            isPointerTapInFlight ||
            pendingPointerTaps.isNotEmpty()

    internal fun shouldSwallowInjectEcho(event: MotionEvent): Boolean =
        pointerTapEchoGuard.shouldSwallow(event)

    fun suppressForScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suppressForScreenshotCapture() }
            return
        }
        if (captureSuppressed || displayView == null) return
        captureSuppressed = true
        session?.screenshotCaptureInProgress?.value = true
        session?.closeRadialMenu()
        // View.INVISIBLE is still captured by some OEM system screenshots; detach instead.
        detachChromeWindows()
    }

    fun restoreAfterScreenshotCapture() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAfterScreenshotCapture() }
            return
        }
        if (!captureSuppressed) return
        captureSuppressed = false
        session?.screenshotCaptureInProgress?.value = false
        if (displayView == null) return
        reattachChromeWindows()
    }

    /** Hide chrome immediately for one-shot click-dismiss (no presence animation). */
    fun hideChromeForOneShotDismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideChromeForOneShotDismiss() }
            return
        }
        session?.closeRadialMenu()
        detachChromeWindows()
    }

    fun dismissImmediate() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissImmediate() }
            return
        }
        dismissAfterPointerTap = false
        captureSuppressed = false
        windowLifecycle.dismissImmediate()
    }

    private fun detachChromeWindows() {
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
    }

    private fun reattachChromeWindows() {
        val wm = windowManager ?: return
        val display = displayView
        val touch = touchHost
        val displayParams = displayLayoutParams
        val touchParams = touchLayoutParams
        if (display != null && displayParams != null && !display.isAttachedToWindow) {
            runCatching { wm.addView(display, displayParams) }
        }
        if (touch != null && touchParams != null && !touch.isAttachedToWindow) {
            runCatching { wm.addView(touch, touchParams) }
        }
    }

    fun show(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float? = null,
        anchorRawY: Float? = null,
        continueTouch: Boolean = false
    ) {
        windowLifecycle.show(context, settings, anchorRawX, anchorRawY, continueTouch)
    }

    fun bringToFront(forceReAdd: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { bringToFront(forceReAdd) }
            return
        }
        val wm = windowManager ?: return
        val display = displayView
        val touch = touchHost
        if (display != null && display.isAttachedToWindow) {
            val displayLp = displayLayoutParams
            if (displayLp != null) {
                runCatching {
                    wm.updateViewLayout(display, displayLp)
                    display.requestLayout()
                    display.invalidate()
                }.onFailure { Log.w("FloatingPointer", "bringToFront display failed", it) }
            }
        }
        if (touch != null && touch.isAttachedToWindow) {
            val touchLp = touchLayoutParams
            if (touchLp != null) {
                runCatching {
                    wm.updateViewLayout(touch, touchLp)
                    touch.requestLayout()
                    touch.invalidate()
                }.onFailure { Log.w("FloatingPointer", "bringToFront touch failed", it) }
            }
        }
    }


    /** True while an edge-gesture finger is still down and events are forwarded here. */
    fun isConsumingEdgeGestureTouch(): Boolean = continuedGestureActive

    fun armContinuedHandoff() {
        continuedGestureActive = true
    }

    fun launchContinuedFromEdge(
        context: Context,
        settings: AppSettings,
        rawX: Float,
        rawY: Float
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { launchContinuedFromEdge(context, settings, rawX, rawY) }
            return
        }
        Log.i(TAG, "launchContinuedFromEdge at ($rawX, $rawY)")
        continuedGestureActive = true
        if (isShowing && isVisible) {
            session?.placeAtTouch(rawX, rawY, settings)
            settingsState?.value = settings
            touchHost?.beginContinuedGesture(
                rawX,
                rawY,
                SystemClock.uptimeMillis()
            )
            Log.i(TAG, "launchContinuedFromEdge: reused visible window")
            return
        }
        windowLifecycle.show(
            context = context,
            settings = settings,
            anchorRawX = rawX,
            anchorRawY = rawY,
            continueTouch = true,
            attachDeferred = true
        )
    }

    fun forwardContinuedTouch(event: MotionEvent): Boolean {
        if (!continuedGestureActive) return false
        val host = touchHost ?: return false
        val handled = host.forwardContinuedTouch(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            continuedGestureActive = false
            EdgeContinuedOverlayHandoff.clearIfInactive()
        }
        return handled
    }

    internal fun onTouchCycleComplete() = windowLifecycle.onTouchCycleComplete()

    internal fun shouldDismissOnOutsideTouch(event: MotionEvent): Boolean =
        windowLifecycle.shouldDismissOnOutsideTouch(event)

    fun toggle(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float? = null,
        anchorRawY: Float? = null,
        continueTouch: Boolean = false
    ) {
        windowLifecycle.toggle(context, settings, anchorRawX, anchorRawY, continueTouch)
    }

    fun dismiss() {
        windowLifecycle.dismiss()
    }

    /**
     * Defers pointer swipe until the radial-menu touch cycle finishes and the touch overlay
     * stops intercepting injected gestures.
     */
    fun schedulePointerSwipe(startX: Float, startY: Float, config: PointerSwipeConfig) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { schedulePointerSwipe(startX, startY, config) }
            return
        }
        pendingPointerSwipeRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingPointerSwipeRunnable = null
            enqueuePointerSwipe(startX, startY, config)
        }
        pendingPointerSwipeRunnable = runnable
        mainHandler.postDelayed(runnable, RADIAL_ACTION_INJECT_DELAY_MS)
    }

    internal fun executeEdgeAction(action: GestureAction) {
        val settings = settingsState?.value ?: return
        val pointerSession = session ?: return
        executePointerAction(
            action = action,
            settings = settings,
            anchorRawX = pointerSession.pointerX.floatValue,
            anchorRawY = pointerSession.pointerY.floatValue
        )
    }

    internal fun executeRadialSlotAction(slotIndex: Int, fingerStillDown: Boolean) {
        val settings = settingsState?.value ?: return
        val slots = settings.floatingPointerRadialSlotActions
        val action = slots.getOrNull(slotIndex) ?: return
        if (action is GestureAction.None) return
        val pointerSession = session ?: return
        val pointerX = pointerSession.pointerX.floatValue
        val pointerY = pointerSession.pointerY.floatValue
        when (action) {
            is GestureAction.PointerGestureRecorder -> {
                if (fingerStillDown) {
                    startGestureRecorder(pointerX, pointerY)
                } else {
                    pointerSession.armPendingGestureCapture(action)
                    windowLifecycle.expandTouchCapture()
                }
            }
            is GestureAction.PointerRealtimeGesture -> {
                if (fingerStillDown) {
                    startRealtimeGesture(pointerX, pointerY)
                } else {
                    pointerSession.armPendingGestureCapture(action)
                    windowLifecycle.expandTouchCapture()
                }
            }
            is GestureAction.PointerPinchZoom -> {
                if (fingerStillDown) {
                    startPinchGestureAt(pointerX, pointerY)
                } else {
                    pointerSession.armPendingGestureCapture(action)
                    windowLifecycle.expandTouchCapture()
                }
            }
            is GestureAction.PointerRealtimeScroll -> {
                if (fingerStillDown) {
                    startScrollGestureAt(pointerX, pointerY)
                } else {
                    pointerSession.armPendingGestureCapture(action)
                    windowLifecycle.expandTouchCapture()
                }
            }
            is GestureAction.Magnifier -> {
                if (fingerStillDown) {
                    SystemMagnifierController.startFollow(pointerX, pointerY)
                } else {
                    SystemMagnifierController.toggle(pointerX, pointerY)
                    dismiss()
                }
            }
            else -> executePointerAction(action, settings, pointerX, pointerY)
        }
    }

    internal fun executeJoystickLongPressAction(action: GestureAction) {
        val settings = settingsState?.value ?: return
        val pointerSession = session ?: return
        val pointerX = pointerSession.pointerX.floatValue
        val pointerY = pointerSession.pointerY.floatValue
        when (action) {
            is GestureAction.PointerGestureRecorder -> startGestureRecorder(pointerX, pointerY)
            is GestureAction.PointerRealtimeGesture -> startRealtimeGesture(pointerX, pointerY)
            is GestureAction.PointerPinchZoom -> startPinchGestureAt(pointerX, pointerY)
            is GestureAction.PointerRealtimeScroll -> startScrollGestureAt(pointerX, pointerY)
            is GestureAction.Magnifier -> SystemMagnifierController.startFollow(pointerX, pointerY)
            is GestureAction.OpenFloatingPointerRadialMenu -> {
                // Already handled by the caller; this branch is defensive.
            }
            else -> {
                executePointerAction(action, settings, pointerX, pointerY)
            }
        }
    }

    internal fun startPendingGestureCapture(action: GestureAction) {
        when (action) {
            is GestureAction.PointerGestureRecorder -> startGestureRecorder(
                session?.pointerX?.floatValue ?: return,
                session?.pointerY?.floatValue ?: return
            )
            is GestureAction.PointerRealtimeGesture -> startRealtimeGesture(
                session?.pointerX?.floatValue ?: return,
                session?.pointerY?.floatValue ?: return
            )
            is GestureAction.PointerPinchZoom -> startPinchGestureAt(
                session?.pointerX?.floatValue ?: return,
                session?.pointerY?.floatValue ?: return
            )
            is GestureAction.PointerRealtimeScroll -> startScrollGestureAt(
                session?.pointerX?.floatValue ?: return,
                session?.pointerY?.floatValue ?: return
            )
            else -> Unit
        }
    }

    private fun startGestureRecorder(pointerX: Float, pointerY: Float) {
        val pointerSession = session ?: return
        val service = SlideIndexAccessibilityService.overlayHostContext() as? android.accessibilityservice.AccessibilityService
        if (service == null) {
            Log.e(TAG, "startGestureRecorder: accessibility service unavailable")
            return
        }
        Log.i(TAG, "startGestureRecorder at ($pointerX, $pointerY)")
        windowLifecycle.expandTouchCapture()
        pointerSession.startGestureRecorder(
            service = service,
            pointerX = pointerX,
            pointerY = pointerY,
            onReplayPrepare = { _ ->
                suppressOutsideDismissForInjectedGesture()
                pointerSession.beginGestureReplay()
                pointerSession.pointerVisible.value = false
                windowLifecycle.collapseTouchCapture(
                    pointerSession.joystickCenterX.floatValue,
                    pointerSession.joystickCenterY.floatValue,
                    forceCollapse = true
                )
                windowLifecycle.setTouchOverlayPassthrough(true)
            },
            onFinished = {
                Log.i(TAG, "gesture recorder replay finished")
                extendOutsideDismissSuppressAfterInjectedGesture()
                pointerSession.endGestureReplay()
                windowLifecycle.setTouchOverlayPassthrough(false)
                val settings = settingsState?.value
                if (settings != null && !pointerSession.gestureTrailRetreatActive.value) {
                    pointerSession.completeGestureAftermathIfReady(settings)
                }
                val (dockX, dockY) = pointerSession.takeGestureCaptureDock()
                    ?: (pointerSession.joystickCenterX.floatValue to pointerSession.joystickCenterY.floatValue)
                windowLifecycle.collapseTouchCapture(
                    dockX,
                    dockY,
                    forceCollapse = true
                )
                settingsSync.resetIdleTimer()
            }
        )
    }

    private fun startRealtimeGesture(pointerX: Float, pointerY: Float) = startPointerGesture(KIND_REALTIME, pointerX, pointerY)

    private fun startPinchGestureAt(pointerX: Float, pointerY: Float) = startPointerGesture(KIND_PINCH, pointerX, pointerY)

    private fun startScrollGestureAt(pointerX: Float, pointerY: Float) = startPointerGesture(KIND_SCROLL, pointerX, pointerY)

    private fun startPointerGesture(kind: Int, pointerX: Float, pointerY: Float) {
        val pointerSession = session ?: return
        val service = SlideIndexAccessibilityService.overlayHostContext() as? android.accessibilityservice.AccessibilityService
        if (service == null) {
            Log.e(TAG, "startRealtimeGesture: accessibility service unavailable")
            return
        }
        Log.i(TAG, "startRealtimeGesture at ($pointerX, $pointerY)")
        suppressOutsideDismissForInjectedGesture()
        if (kind != KIND_PINCH) {
            windowLifecycle.expandTouchCapture()
        }
        val endRealtimeCapture = {
            extendOutsideDismissSuppressAfterInjectedGesture()
            windowLifecycle.setTouchOverlayPassthrough(false)
            pointerSession.clearTrail()
            val settings = settingsState?.value
            pointerSession.pointerVisible.value =
                settings?.floatingPointerHideWhenJoystickReleased != true
            val (dockX, dockY) = pointerSession.takeGestureCaptureDock()
                ?: (pointerSession.joystickCenterX.floatValue to pointerSession.joystickCenterY.floatValue)
            windowLifecycle.collapseTouchCapture(
                dockX,
                dockY,
                forceCollapse = true
            )
            settingsSync.resetIdleTimer()
        }
        val onError: () -> Unit = {
            Log.w(TAG, "pointer gesture error kind=$kind")
            endRealtimeCapture()
        }
        val onFinished: () -> Unit = {
            Log.i(TAG, "pointer gesture finished kind=$kind")
            endRealtimeCapture()
        }
        when (kind) {
            KIND_PINCH -> pointerSession.startPinchGesture(service, pointerX, pointerY, onError, onFinished)
            KIND_SCROLL -> pointerSession.startScrollGesture(service, pointerX, pointerY, onError, onFinished)
            else -> pointerSession.startRealtimeGesture(service, pointerX, pointerY, onError, onFinished)
        }
    }

    private fun executePointerAction(
        action: GestureAction,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float
    ) {
        if (action is GestureAction.None) return
        onHaptic(settings)
        if (action is GestureAction.SimulatePointerSwipe) {
            schedulePointerSwipe(startX = anchorRawX, startY = anchorRawY, config = action.config)
            return
        }
        // 「放大镜」普通调用（非拖拽抓取）走开关模式
        if (action is GestureAction.Magnifier) {
            SystemMagnifierController.toggle(anchorRawX, anchorRawY)
            return
        }
        val executor = actionExecutor ?: return
        executor.execute(
            action = action,
            settings = settings,
            anchorRawX = anchorRawX,
            anchorRawY = anchorRawY
        )
    }

    internal fun runQuickSwipeDismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runQuickSwipeDismiss() }
            return
        }
        val visible = visibleState ?: return
        if (!visible.value) return
        session?.clearTrail()
        windowLifecycle.setTouchOverlayPassthrough(true)
        visible.value = false
        windowLifecycle.scheduleDismissCleanup()
    }

    internal fun runOutsideDismissPrepare() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runOutsideDismissPrepare() }
            return
        }
        val visible = visibleState ?: return
        if (!visible.value) return
        session?.clearTrail()
        windowLifecycle.setTouchOverlayPassthrough(true)
        visible.value = false
        windowLifecycle.scheduleDismissCleanup()
    }

    internal fun runPointerTap(rawX: Float, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runPointerTap(rawX, rawY) }
            return
        }
        val pointerRadius = ((settingsState?.value?.floatingPointerPointerDiameterPx ?: 48f) / 2f)
            .coerceAtLeast(18f * (session?.density ?: 1f))
        if (com.slideindex.app.link.LinkRadarOverlay.onPointerTapRequested(rawX, rawY, pointerRadius)) {
            dismiss()
            return
        }
        enqueuePointerInjection(rawX, rawY)
    }

    /** Inject a pointer tap, then dismiss once the gesture finishes (continued one-shot). */
    internal fun runPointerTapThenDismiss(rawX: Float, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runPointerTapThenDismiss(rawX, rawY) }
            return
        }
        val pointerRadius = ((settingsState?.value?.floatingPointerPointerDiameterPx ?: 48f) / 2f)
            .coerceAtLeast(18f * (session?.density ?: 1f))
        if (com.slideindex.app.link.LinkRadarOverlay.onPointerTapRequested(rawX, rawY, pointerRadius)) {
            dismissImmediate()
            return
        }
        // Detach before tap so radial/presence never flash while waiting for injection.
        hideChromeForOneShotDismiss()
        pendingPointerTaps.clear()
        if (isPointerTapInFlight) {
            dismissAfterPointerTap = true
            pendingPointerTaps.addLast(PendingPointerTap(rawX, rawY))
            return
        }
        startPointerTap(rawX, rawY)
        // Tear down immediately — tap injection uses a11y, not overlay windows.
        dismissImmediate()
    }

    private fun enqueuePointerSwipe(startX: Float, startY: Float, config: PointerSwipeConfig) {
        if (isPointerTapInFlight || isPointerSwipeInFlight) {
            mainHandler.postDelayed(
                { enqueuePointerSwipe(startX, startY, config) },
                SlideIndexAccessibilityService.POINTER_TAP_CHAIN_GAP_MS
            )
            return
        }
        startPointerSwipe(startX, startY, config)
    }

    private fun startPointerSwipe(startX: Float, startY: Float, config: PointerSwipeConfig) {
        isPointerSwipeInFlight = true
        session?.beginInjectedSwipePreview(startX, startY, config)
        windowLifecycle.setTouchOverlayPassthrough(true)
        markPointerTapOutsideSuppress()
        Log.i(TAG, "injectPointerSwipe start ($startX, $startY) config=$config")
        InputTapUtil.dispatchPointerSwipeAsync(startX, startY, config) { ok ->
            Log.i(TAG, "injectPointerSwipe finished ok=$ok at ($startX, $startY)")
            onPointerSwipeComplete()
        }
    }

    private fun onPointerSwipeComplete() {
        isPointerSwipeInFlight = false
        windowLifecycle.setTouchOverlayPassthrough(false)
        extendPointerTapOutsideSuppressAfterComplete()
    }

    private fun onHaptic(settings: AppSettings) {
        displayView?.let { HapticHelper.appTick(it, settings) }
    }

    private fun enqueuePointerInjection(rawX: Float, rawY: Float) {
        if (isPointerTapInFlight) {
            pendingPointerTaps.addLast(PendingPointerTap(rawX, rawY))
            return
        }
        startPointerTap(rawX, rawY)
    }

    private fun startPointerTap(rawX: Float, rawY: Float) {
        isPointerTapInFlight = true
        pointerTapEchoGuard.arm(rawX, rawY, injectEchoSlopPx())
        markPointerTapOutsideSuppress()
        injectPointerTap(rawX, rawY)
    }

    private fun injectPointerTap(rawX: Float, rawY: Float) {
        val insideTouchOverlay = windowLifecycle.isInjectPointInsideTouchOverlay(rawX, rawY)
        if (insideTouchOverlay) {
            windowLifecycle.setPassthroughForInjectAt(rawX, rawY)
        }
        InputTapUtil.dispatchPointerTapAsync(
            rawX = rawX,
            rawY = rawY,
            preferNodeClick = insideTouchOverlay,
            onFinished = { _ ->
                onPointerTapComplete(insideTouchOverlay)
            }
        )
    }

    private fun onPointerTapComplete(clearInjectPassthrough: Boolean = false) {
        val next = pendingPointerTaps.pollFirst()
        if (next != null) {
            markPointerTapOutsideSuppress()
            pointerTapEchoGuard.arm(next.rawX, next.rawY, injectEchoSlopPx())
            mainHandler.postDelayed(
                { injectPointerTap(next.rawX, next.rawY) },
                SlideIndexAccessibilityService.POINTER_TAP_CHAIN_GAP_MS
            )
            return
        }
        isPointerTapInFlight = false
        if (clearInjectPassthrough) {
            windowLifecycle.setTouchOverlayPassthrough(false)
        }
        windowLifecycle.onPointerTapInjectionFinished()
        extendPointerTapOutsideSuppressAfterComplete()
        if (dismissAfterPointerTap) {
            dismissAfterPointerTap = false
            dismissImmediate()
        }
    }

    private fun markPointerTapOutsideSuppress() {
        val until = SystemClock.uptimeMillis() + POINTER_TAP_OUTSIDE_SUPPRESS_MS
        if (until > pointerTapOutsideSuppressUntilMs) {
            pointerTapOutsideSuppressUntilMs = until
        }
    }

    internal fun suppressOutsideDismissForInjectedGesture() {
        markPointerTapOutsideSuppress()
    }

    internal fun extendOutsideDismissSuppressAfterInjectedGesture() {
        extendPointerTapOutsideSuppressAfterComplete()
    }

    private fun extendPointerTapOutsideSuppressAfterComplete() {
        val until = SystemClock.uptimeMillis() + POINTER_TAP_OUTSIDE_ECHO_AFTER_COMPLETE_MS
        if (until > pointerTapOutsideSuppressUntilMs) {
            pointerTapOutsideSuppressUntilMs = until
        }
    }

    private const val TAG = "FloatingPointerOverlay"
    /** 指针上的连续手势种类：0=实时手势（拖动）、1=双指缩放、2=实时滚动。 */
    private const val KIND_REALTIME = 0
    private const val KIND_PINCH = 1
    private const val KIND_SCROLL = 2
    /** Covers injected tap + in-flight ACTION_OUTSIDE echo (coords are zeroed). */
    private const val POINTER_TAP_OUTSIDE_SUPPRESS_MS = 500L
    /** ACTION_OUTSIDE can arrive after dispatchGesture/onFinished returns. */
    private const val POINTER_TAP_OUTSIDE_ECHO_AFTER_COMPLETE_MS = 350L
    private const val RADIAL_ACTION_INJECT_DELAY_MS = 120L
}
