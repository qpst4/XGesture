package com.slideindex.app.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.settings.AppSettings
import kotlin.math.hypot
import kotlin.math.max

internal class FloatingPointerInputHandler(
    private val session: FloatingPointerSession,
    private val settingsProvider: () -> AppSettings,
    private val host: Host
) {
    interface Host {
        fun captureAllPointers()
        fun releaseAllPointers()
        fun onFingerTrackingMove(
            fingerRawX: Float,
            fingerRawY: Float,
            fingerLocalX: Float,
            fingerLocalY: Float
        )
        fun onPointerPositionChanged(pointerX: Float, pointerY: Float)
        fun onGestureEnd(centerX: Float, centerY: Float, isTap: Boolean)
        fun onPointerClick(rawX: Float, rawY: Float)
        fun onPointerClickAndDismiss(rawX: Float, rawY: Float)
        fun onOutsideDismissPrepare()
        fun onQuickSwipeDismiss()
        fun onDismiss()
        fun onRadialMenuOpened()
        fun onRadialMenuClosed()
        fun onRadialMenuAction(slotIndex: Int, fingerStillDown: Boolean)
        fun expandTouchCapture()
        fun onJoystickLongPressAction(action: GestureAction)
        fun onStartPendingGestureCapture(action: GestureAction)
        fun onActivity()
        fun onHaptic()
        fun shouldDismissOnOutsideTouch(event: MotionEvent): Boolean
        fun onTouchCycleComplete()
        fun hostContext(): Context
        fun shouldSwallowInjectEcho(event: MotionEvent): Boolean
        fun isPointerTapInjectionActive(): Boolean
        fun fingerLocalInTouchOverlay(fingerRawX: Float, fingerRawY: Float): Pair<Float, Float>
        fun finishEdgeHandoffTouchCapture(fingerRawX: Float, fingerRawY: Float)
        fun ensureTouchOverlayInteractive()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hoverSelectController = FloatingPointerHoverSelectController(session, mainHandler)

    /** 「松手即点、摇杆保留」模式下的空闲收起定时器。 */
    private var keepJoystickIdleRunnable: Runnable? = null

    /** 本次触摸是否真的驱动过实时缩放/滚动（用于区分"选中动作的抬手"和"拖完的抬手"）。 */
    private var realtimeGestureMoved = false

    private fun cancelKeepJoystickIdleDismiss() {
        keepJoystickIdleRunnable?.let { mainHandler.removeCallbacks(it) }
        keepJoystickIdleRunnable = null
    }

    private fun scheduleKeepJoystickIdleDismiss() {
        cancelKeepJoystickIdleDismiss()
        val runnable = Runnable {
            keepJoystickIdleRunnable = null
            session.joystickActive.value = false
            session.pointerVisible.value = false
            session.clearTrail()
            host.onDismiss()
        }
        keepJoystickIdleRunnable = runnable
        mainHandler.postDelayed(runnable, KEEP_JOYSTICK_IDLE_DISMISS_MS)
    }
    internal var capturing = false
    /** QC `m81.s` — drives tap vs drag vs ring-slot routing for the current finger. */
    private var touchPhase = FloatingPointerTouchPhase.PendingTapOrDrag
    /** True while an edge continuous handoff finger is still driving this session. */
    private var fromContinuedEdge = false
    private var longPressTriggered = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var restJoystickX = 0f
    private var restJoystickY = 0f
    private var downLocalX = 0f
    private var downLocalY = 0f
    private var downTimeMs = 0L
    private var longPressRunnable: Runnable? = null
    /** Prevents re-firing gesture capture when the finger leaves and re-enters the same slot. */
    private var radialGestureCaptureTriggered = false

    private fun resetTouchPhase() {
        touchPhase = FloatingPointerTouchPhase.PendingTapOrDrag
        session.activeTouchPhase = touchPhase
    }

    private fun setTouchPhase(phase: FloatingPointerTouchPhase) {
        touchPhase = phase
        session.activeTouchPhase = phase
    }

    private fun movedBeyondTap(): Boolean = touchPhase.isDragging()

    private fun isRadialMenuEngaged(): Boolean =
        session.radialMenuActive.value || session.radialMenuIdle.value

    /**
     * Starts joystick control from an in-flight edge gesture without waiting for a new
     * [MotionEvent.ACTION_DOWN] on this overlay.
     */
    fun beginContinuedGesture(rawX: Float, rawY: Float, downTimeMs: Long) {
        host.onActivity()
        capturing = true
        fromContinuedEdge = true
        setTouchPhase(FloatingPointerTouchPhase.Dragging)
        longPressTriggered = false
        downRawX = rawX
        downRawY = rawY
        this.downTimeMs = downTimeMs
        lastRawX = rawX
        lastRawY = rawY
        restJoystickX = session.joystickCenterX.floatValue
        restJoystickY = session.joystickCenterY.floatValue

        session.joystickActive.value = true
        session.pointerVisible.value = true
        session.continuedEdgeSessionActive.value = true
        val settings = settingsProvider()
        if (settings.floatingPointerReleaseClickAndDismiss ||
            settings.floatingPointerReleaseClickKeepJoystick
        ) {
            session.closeRadialMenu()
        }
        session.prepareContinuedEdgeGesture(rawX, rawY, settings)
        host.finishEdgeHandoffTouchCapture(rawX, rawY)
        val captureRadius = session.touchCaptureRadiusPx(settings)
        downLocalX = captureRadius
        downLocalY = captureRadius
        host.onFingerTrackingMove(rawX, rawY, downLocalX, downLocalY)
        host.captureAllPointers()
        if (settings.floatingPointerHoverEnterSelect) {
            hoverSelectController.begin()
        } else {
            hoverSelectController.cancel()
        }
    }

    /** Forwards MOVE/UP from the edge capture window while the trigger finger stays down. */
    fun forwardContinuedTouch(event: MotionEvent): Boolean {
        val isRelease = event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        if (!capturing) {
            if (isRelease && session.continuedEdgeSessionActive.value && fromContinuedEdge) {
                val handled = handleTouch(event)
                capturing = false
                return handled
            }
            return false
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> handleTouch(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = handleTouch(event)
                capturing = false
                handled
            }
            else -> false
        }
    }

    fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 无障碍注入的事件会带回显（deviceId < 0）：绝不能当成用户手指，否则会重新锚定摇杆，
        // 表现为"一选功能环上的滚动/缩放，摇杆就跑到别处"。
        if (event.deviceId < 0) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                android.util.Log.i("PinchEcho", "injected DOWN at (,)")
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (host.shouldSwallowInjectEcho(event)) {
                return true
            }
            val settings = settingsProvider()
            if (settings.floatingPointerHideOnOutsideClick && host.shouldDismissOnOutsideTouch(event)) {
                host.onOutsideDismissPrepare()
            }
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                host.ensureTouchOverlayInteractive()
                realtimeGestureMoved = false
                val handled = handleTouch(event)
                capturing = handled
                return handled
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!capturing &&
                    !(session.continuedEdgeSessionActive.value && fromContinuedEdge)
                ) {
                    return false
                }
                val handled = handleTouch(event)
                capturing = false
                return handled
            }
            else -> {
                if (!capturing) return false
                // 实时缩放 / 滚动期间：指针与摇杆都冻结，手指位移只驱动手势本身。
                if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val fingerDx = event.rawX - downRawX
                    val fingerDy = event.rawY - downRawY
                    if (session.pinchGesture != null) {
                        realtimeGestureMoved = true
                        session.updatePinchByFinger(fingerDx, fingerDy)
                        return true
                    }
                    // 滚动仍然由光标移动驱动（applyPointerFromTouch → updateRealtimeGesture），
                    // 冻结光标等于把滚动唯一的驱动源掐掉——这是之前"滚动断了"的真因。
                }
                return handleTouch(event)
            }
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (host.shouldSwallowInjectEcho(event)) {
            return true
        }
        if (host.isPointerTapInjectionActive()) {
            return true
        }
        if (session.gestureReplayActive.value) return true
        if (touchPhase == FloatingPointerTouchPhase.AlwaysVisibleSlotTap) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                resetTouchPhase()
                cancelLongPressJob()
                host.releaseAllPointers()
            }
            return true
        }
        if (touchPhase == FloatingPointerTouchPhase.RadialDismissOnly) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                resetTouchPhase()
                cancelLongPressJob()
                host.releaseAllPointers()
                host.onTouchCycleComplete()
            }
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                host.onActivity()
                cancelKeepJoystickIdleDismiss()
                val settings = settingsProvider()
                val pendingCapture = session.pendingGestureCaptureAction
                if (pendingCapture != null) {
                    session.clearPendingGestureCapture()
                    setTouchPhase(FloatingPointerTouchPhase.Dragging)
                    longPressTriggered = false
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downLocalX = event.x
                    downLocalY = event.y
                    downTimeMs = System.currentTimeMillis()
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    session.joystickActive.value = true
                    session.pointerVisible.value = true
                    session.beginGesture(event.rawX, event.rawY, settingsProvider())
                    restJoystickX = session.joystickCenterX.floatValue
                    restJoystickY = session.joystickCenterY.floatValue
                    session.armGestureCaptureJoystickOffset(event.rawX, event.rawY)
                    host.onFingerTrackingMove(event.rawX, event.rawY, downLocalX, downLocalY)
                    host.captureAllPointers()
                    host.onStartPendingGestureCapture(pendingCapture)
                    return true
                }
                if (settings.floatingPointerRadialAlwaysVisible &&
                    !session.radialMenuActive.value &&
                    !session.radialMenuIdle.value
                ) {
                    val slot = slotIndexAt(event.rawX, event.rawY)
                    if (slot >= 0) {
                        val action = settings.floatingPointerRadialSlotActions.getOrNull(slot)
                        if (action != null && isGestureCaptureLongPressAction(action)) {
                            setTouchPhase(FloatingPointerTouchPhase.AlwaysVisibleRingSlot)
                            longPressTriggered = false
                            downRawX = event.rawX
                            downRawY = event.rawY
                            downLocalX = event.x
                            downLocalY = event.y
                            downTimeMs = System.currentTimeMillis()
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            session.joystickActive.value = true
                            session.pointerVisible.value = true
                            session.beginGesture(event.rawX, event.rawY, settings)
                            restJoystickX = session.joystickCenterX.floatValue
                            restJoystickY = session.joystickCenterY.floatValue
                            session.armGestureCaptureJoystickOffset(event.rawX, event.rawY)
                            host.onFingerTrackingMove(event.rawX, event.rawY, downLocalX, downLocalY)
                            host.captureAllPointers()
                            host.onHaptic()
                            host.onRadialMenuAction(slot, fingerStillDown = true)
                            return true
                        }
                        setTouchPhase(FloatingPointerTouchPhase.AlwaysVisibleSlotTap)
                        longPressTriggered = false
                        if (action != null && action !is GestureAction.None) {
                            host.onHaptic()
                            host.onRadialMenuAction(slot, fingerStillDown = true)
                        }
                        host.onTouchCycleComplete()
                        return true
                    }
                }
                if (isRadialMenuEngaged()) {
                    val slot = slotIndexAt(event.rawX, event.rawY)
                    if (slot >= 0) {
                        val action = settingsProvider().floatingPointerRadialSlotActions.getOrNull(slot)
                        val isCapture = action != null && isGestureCaptureLongPressAction(action)
                        session.closeRadialMenu()
                        host.onRadialMenuClosed()
                        if (isCapture) {
                            setTouchPhase(FloatingPointerTouchPhase.Dragging)
                            longPressTriggered = false
                            downRawX = event.rawX
                            downRawY = event.rawY
                            downLocalX = event.x
                            downLocalY = event.y
                            downTimeMs = System.currentTimeMillis()
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            session.joystickActive.value = true
                            session.pointerVisible.value = true
                            session.syncPointerForGestureCapture(event.rawX, event.rawY)
                            session.armGestureCaptureJoystickOffset(event.rawX, event.rawY)
                            host.onFingerTrackingMove(event.rawX, event.rawY, downLocalX, downLocalY)
                            host.captureAllPointers()
                            host.onHaptic()
                            host.onRadialMenuAction(slot, fingerStillDown = true)
                            return true
                        } else {
                            session.joystickActive.value = false
                            host.onGestureEnd(restJoystickX, restJoystickY, false)
                            if (action != null && action !is GestureAction.None) {
                                host.onRadialMenuAction(slot, fingerStillDown = false)
                            }
                            host.onTouchCycleComplete()
                            return true
                        }
                    } else {
                        setTouchPhase(FloatingPointerTouchPhase.RadialDismissOnly)
                        session.closeRadialMenu()
                        host.onRadialMenuClosed()
                        session.joystickActive.value = false
                        host.onGestureEnd(restJoystickX, restJoystickY, false)
                        host.onTouchCycleComplete()
                        return true
                    }
                }

                radialGestureCaptureTriggered = false
                setTouchPhase(FloatingPointerTouchPhase.PendingTapOrDrag)
                longPressTriggered = false
                downRawX = event.rawX
                downRawY = event.rawY
                downLocalX = event.x
                downLocalY = event.y
                downTimeMs = System.currentTimeMillis()
                lastRawX = event.rawX
                lastRawY = event.rawY
                session.joystickActive.value = true
                session.pointerVisible.value = true
                session.beginGesture(event.rawX, event.rawY, settingsProvider())
                restJoystickX = session.joystickCenterX.floatValue
                restJoystickY = session.joystickCenterY.floatValue
                host.onFingerTrackingMove(event.rawX, event.rawY, downLocalX, downLocalY)
                host.captureAllPointers()
                scheduleLongPress()
            }
            MotionEvent.ACTION_MOVE -> {
                host.onActivity()
                if (isRadialMenuEngaged()) {
                    val settings = settingsProvider()
                    val highlightChanged = session.updateRadialHighlight(
                        event.rawX,
                        event.rawY,
                        settings
                    )
                    if (highlightChanged) {
                        host.onHaptic()
                    }
                    if (session.radialMenuActive.value &&
                        highlightChanged &&
                        !radialGestureCaptureTriggered &&
                        !session.gestureCaptureActive
                    ) {
                        val slot = session.radialHighlightedSlot.intValue
                        val action = settings.floatingPointerRadialSlotActions.getOrNull(slot)
                        if (action != null && isGestureCaptureLongPressAction(action)) {
                            radialGestureCaptureTriggered = true
                    setTouchPhase(FloatingPointerTouchPhase.Dragging)
                            session.closeRadialMenu()
                            host.onRadialMenuClosed()
                            session.syncPointerForGestureCapture(event.rawX, event.rawY)
                            session.armGestureCaptureJoystickOffset(event.rawX, event.rawY)
                            host.onRadialMenuAction(slot, fingerStillDown = true)
                        }
                    }
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    return true
                }
                val settings = settingsProvider()
                val clickDistancePx = session.clickDistanceThresholdPx(settings)
                val totalDx = event.rawX - downRawX
                val totalDy = event.rawY - downRawY
                if (!movedBeyondTap() &&
                    !session.gestureCaptureActive &&
                    hypot(totalDx.toDouble(), totalDy.toDouble()) > clickDistancePx
                ) {
                    cancelLongPressJob()
                    setTouchPhase(FloatingPointerTouchPhase.Dragging)
                }

                val (joystickX, joystickY) = if (session.gestureCaptureActive) {
                    session.gestureCaptureJoystickCenterForFinger(event.rawX, event.rawY)
                } else {
                    session.joystickCenterForFinger(event.rawX, event.rawY)
                }
                session.joystickCenterX.floatValue = joystickX
                session.joystickCenterY.floatValue = joystickY
                host.onFingerTrackingMove(event.rawX, event.rawY, downLocalX, downLocalY)
                session.applyPointerFromTouch(event.rawX, event.rawY, settings)
                host.onPointerPositionChanged(session.pointerX.floatValue, session.pointerY.floatValue)
                if (fromContinuedEdge && settings.floatingPointerHoverEnterSelect) {
                    hoverSelectController.onPointerMoved(
                        pointerX = session.pointerX.floatValue,
                        pointerY = session.pointerY.floatValue,
                        density = session.density
                    )
                }
                lastRawX = event.rawX
                lastRawY = event.rawY
            }
            MotionEvent.ACTION_UP -> {
                host.onActivity()
                cancelLongPressJob()
                host.releaseAllPointers()
                if (session.pinchGesture != null) {
                    if (!realtimeGestureMoved) {
                        // 只是"选中动作"的那次抬手：不能把刚建好的缩放手势结束掉。
                        resetTouchPhase()
                        host.onTouchCycleComplete()
                        return true
                    }
                    // 真正拖动过：抬手结束手势，不 dock 摇杆、不移动光标（QC 语义：位置不变）。
                    fromContinuedEdge = false
                    session.finishRealtimeGesture()
                    resetTouchPhase()
                    host.onTouchCycleComplete()
                    return true
                }
                if (session.gestureCaptureActive) {
                    fromContinuedEdge = false
                    session.continuedEdgeSessionActive.value = false
                    hoverSelectController.cancel()
                    session.dockJoystickAfterGestureCapture(event.rawX, event.rawY)
                    session.joystickActive.value = false
                    session.finishGestureRecorder()
                    session.finishRealtimeGesture()
                    if (SystemMagnifierController.isFollowingCursor()) {
                        SystemMagnifierController.stop()
                    }
                    radialGestureCaptureTriggered = false
                    resetTouchPhase()
                    host.onTouchCycleComplete()
                    return true
                }
                if (session.radialMenuActive.value) {
                    fromContinuedEdge = false
                    session.continuedEdgeSessionActive.value = false
                    hoverSelectController.cancel()
                    val slot = session.radialHighlightedSlot.intValue
                    if (slot < 0) {
                        session.closeRadialMenu()
                        host.onRadialMenuClosed()
                        session.joystickActive.value = false
                        host.onGestureEnd(restJoystickX, restJoystickY, false)
                        resetTouchPhase()
                        host.onTouchCycleComplete()
                        return true
                    }
                    session.closeRadialMenu()
                    host.onRadialMenuClosed()
                    session.joystickActive.value = false
                    host.onGestureEnd(restJoystickX, restJoystickY, false)
                    if (!radialGestureCaptureTriggered) {
                        host.onRadialMenuAction(slot, fingerStillDown = false)
                    }
                    radialGestureCaptureTriggered = false
                    resetTouchPhase()
                    host.onTouchCycleComplete()
                    return true
                }
                val settings = settingsProvider()
                if (fromContinuedEdge) {
                    handleContinuedEdgeUp(settings)
                    return true
                }
                val isTap = touchPhase.allowsCenterTapOnUp() && !longPressTriggered
                if (settings.floatingPointerReleaseClickKeepJoystick) {
                    // 「松手即点、摇杆保留」：在指针处点一下，摇杆与指针留在原地；
                    // 下次按下可以继续拖，松手再点，循环。空闲一段时间后自动收起。
                    performPointerClick()
                    session.joystickActive.value = true
                    session.pointerVisible.value = true
                    resetTouchPhase()
                    scheduleKeepJoystickIdleDismiss()
                    host.onTouchCycleComplete()
                    return true
                }
                if (settings.floatingPointerHideWhenJoystickReleased) {
                    session.pointerVisible.value = false
                    if (!session.gestureCaptureActive && movedBeyondTap()) {
                        session.clearTrail()
                    }
                }
                if (SystemMagnifierController.isFollowingCursor()) {
                    SystemMagnifierController.stop()
                }
                session.joystickActive.value = false
                val endX = if (movedBeyondTap()) {
                    session.joystickCenterX.floatValue
                } else {
                    restJoystickX
                }
                val endY = if (movedBeyondTap()) {
                    session.joystickCenterY.floatValue
                } else {
                    restJoystickY
                }
                if (!movedBeyondTap()) {
                    session.joystickCenterX.floatValue = restJoystickX
                    session.joystickCenterY.floatValue = restJoystickY
                }

                val elapsed = System.currentTimeMillis() - downTimeMs
                val fingerDistance = hypot(
                    (event.rawX - downRawX).toDouble(),
                    (event.rawY - downRawY).toDouble()
                ).toFloat()
                val joystickDistance = if (movedBeyondTap()) {
                    hypot(
                        (endX - restJoystickX).toDouble(),
                        (endY - restJoystickY).toDouble()
                    ).toFloat()
                } else {
                    0f
                }
                val swipeDistance = maxOf(fingerDistance, joystickDistance)
                if (settings.floatingPointerHideOnQuickSwipe &&
                    movedBeyondTap() &&
                    swipeDistance >= QUICK_SWIPE_MIN_DISTANCE_PX &&
                    elapsed <= QUICK_SWIPE_MAX_DURATION_MS
                ) {
                    session.clearTrail()
                    host.onQuickSwipeDismiss()
                    resetTouchPhase()
                    host.onTouchCycleComplete()
                    return true
                }

                host.onGestureEnd(endX, endY, isTap)

                if (isTap) {
                    performPointerClick()
                }
                resetTouchPhase()
                host.onTouchCycleComplete()
            }
            MotionEvent.ACTION_CANCEL -> {
                host.onActivity()
                cancelLongPressJob()
                host.releaseAllPointers()
                if (fromContinuedEdge) {
                    fromContinuedEdge = false
                    session.continuedEdgeSessionActive.value = false
                    hoverSelectController.cancel()
                    session.joystickActive.value = false
                    host.onGestureEnd(
                        session.joystickCenterX.floatValue,
                        session.joystickCenterY.floatValue,
                        false
                    )
                    if (settingsProvider().floatingPointerReleaseClickAndDismiss ||
                        settingsProvider().floatingPointerHoverEnterSelect
                    ) {
                        host.onDismiss()
                    }
                    resetTouchPhase()
                    host.onTouchCycleComplete()
                    return true
                }
                if (session.gestureCaptureActive) {
                    session.joystickActive.value = false
                    session.joystickCenterX.floatValue = restJoystickX
                    session.joystickCenterY.floatValue = restJoystickY
                    session.finishGestureRecorder()
                    session.finishRealtimeGesture()
                    if (SystemMagnifierController.isFollowingCursor()) {
                        SystemMagnifierController.stop()
                    }
                    resetTouchPhase()
                    host.onTouchCycleComplete()
                    return true
                }
                if (isRadialMenuEngaged()) {
                    session.closeRadialMenu()
                    host.onRadialMenuClosed()
                }
                if (settingsProvider().floatingPointerHideWhenJoystickReleased) {
                    session.pointerVisible.value = false
                    if (movedBeyondTap()) {
                        session.clearTrail()
                    }
                }
                if (SystemMagnifierController.isFollowingCursor()) {
                    SystemMagnifierController.stop()
                }
                session.joystickActive.value = false
                session.joystickCenterX.floatValue = restJoystickX
                session.joystickCenterY.floatValue = restJoystickY
                host.onGestureEnd(restJoystickX, restJoystickY, false)
                resetTouchPhase()
                host.onTouchCycleComplete()
            }
        }
        return true
    }

    private fun handleContinuedEdgeUp(settings: AppSettings) {
        fromContinuedEdge = false
        session.joystickActive.value = false
        val endX = session.joystickCenterX.floatValue
        val endY = session.joystickCenterY.floatValue
        host.onGestureEnd(endX, endY, false)
        resetTouchPhase()

        if (settings.floatingPointerHoverEnterSelect && hoverSelectController.hasPickIntent) {
            session.continuedEdgeSessionActive.value = false
            hoverSelectController.finishAndSubmit(host.hostContext(), settings)
            host.onDismiss()
            host.onTouchCycleComplete()
            return
        }
        hoverSelectController.cancel()

        if (settings.floatingPointerReleaseClickKeepJoystick) {
            // 边缘交接路径同样：点一下、保留摇杆，下一次按下继续拖。
            performPointerClick()
            session.continuedEdgeSessionActive.value = true
            session.joystickActive.value = true
            session.pointerVisible.value = true
            host.onTouchCycleComplete()
            scheduleKeepJoystickIdleDismiss()
            return
        }

        if (settings.floatingPointerReleaseClickAndDismiss) {
            // Keep continuedEdgeSessionActive until dismissImmediate clears it — prevents
            // always-visible radial from animating back in while the tap is in flight.
            performPointerClickAndDismiss()
            host.onTouchCycleComplete()
            return
        }

        session.continuedEdgeSessionActive.value = false
        if (settings.floatingPointerHideWhenJoystickReleased) {
            session.pointerVisible.value = false
            session.clearTrail()
        }
        host.onTouchCycleComplete()
    }

    private fun performPointerClick() {
        cancelLongPressJob()
        val clickX = session.pointerX.floatValue
        val clickY = session.pointerY.floatValue
        val settings = settingsProvider()
        if (settings.floatingPointerClickHapticEnabled) {
            host.onHaptic()
        }
        if (settings.floatingPointerClickVisualFeedbackEnabled) {
            session.triggerRipple(clickX, clickY)
        }
        session.triggerPointerClick()
        // Inject synchronously so touch-overlay passthrough stays enabled through onTouchCycleComplete.
        host.onPointerClick(clickX, clickY)
    }

    private fun performPointerClickAndDismiss() {
        cancelLongPressJob()
        val clickX = session.pointerX.floatValue
        val clickY = session.pointerY.floatValue
        val settings = settingsProvider()
        // Skip ripple / pointer-click anim — chrome is detached immediately for one-shot.
        if (settings.floatingPointerClickHapticEnabled) {
            host.onHaptic()
        }
        host.onPointerClickAndDismiss(clickX, clickY)
    }

    private fun scheduleLongPress() {
        cancelLongPressJob()
        val settings = settingsProvider()
        val longPressAction = settings.floatingPointerJoystickLongPressAction
        if (longPressAction is GestureAction.OpenFloatingPointerRadialMenu) {
            val delayMs = settings.floatingPointerRadialLongPressMs.coerceIn(200, 2000).toLong()
            val runnable = Runnable {
                if (!movedBeyondTap() && !session.radialMenuActive.value && !session.radialMenuIdle.value) {
                    longPressTriggered = true
                    host.onHaptic()
                    session.openRadialMenu(restJoystickX, restJoystickY)
                    setTouchPhase(FloatingPointerTouchPhase.RadialMenu)
                    host.onRadialMenuOpened()
                    session.updateRadialHighlight(lastRawX, lastRawY, settingsProvider())
                }
            }
            longPressRunnable = runnable
            mainHandler.postDelayed(runnable, delayMs)
            return
        }
        val runnable = Runnable {
            val isGestureCapture = isGestureCaptureLongPressAction(longPressAction)
            // QC: long-tap must complete without crossing the click slop first.
                if (!movedBeyondTap()) {
                    longPressTriggered = true
                    if (!isGestureCapture) {
                        session.joystickActive.value = false
                    }
                    if (longPressAction is GestureAction.None) {
                        host.onDismiss()
                    } else {
                        host.onHaptic()
                        if (isGestureCapture) {
                            session.armGestureCaptureJoystickOffset(lastRawX, lastRawY)
                        }
                        host.onJoystickLongPressAction(longPressAction)
                    }
                }
        }
        longPressRunnable = runnable
        val delayMs = if (isGestureCaptureLongPressAction(longPressAction)) {
            settings.floatingPointerRadialLongPressMs.coerceIn(200, 2000).toLong()
        } else {
            LONG_PRESS_DISMISS_MS
        }
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun slotIndexAt(fingerX: Float, fingerY: Float): Int {
        val settings = settingsProvider()
        val inner = settings.floatingPointerRadialInnerDiameterPx / 2f
        val outer = settings.floatingPointerRadialOuterDiameterPx / 2f
        val (centerX, centerY) = session.radialMenuCenterForInput()
        return FloatingPointerRadialMenu.sectorIndexAt(
            centerX = centerX,
            centerY = centerY,
            fingerX = fingerX,
            fingerY = fingerY,
            innerRadius = inner,
            outerRadius = outer
        ) ?: -1
    }

    private fun cancelLongPressJob() {
        longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun isGestureCaptureLongPressAction(action: GestureAction): Boolean =
        action is GestureAction.PointerGestureRecorder ||
            action is GestureAction.PointerRealtimeGesture ||
            action is GestureAction.PointerPinchZoom ||
            action is GestureAction.PointerRealtimeScroll ||
            action is GestureAction.Magnifier

    companion object {
        private const val LONG_PRESS_DISMISS_MS = 900L
        private const val QUICK_SWIPE_MIN_DISTANCE_PX = 180f
        private const val QUICK_SWIPE_MAX_DURATION_MS = 450L

        /** 「松手即点、摇杆保留」模式：这么久没再按下就自动收起，避免摇杆一直挂着。 */
        private const val KEEP_JOYSTICK_IDLE_DISMISS_MS = 8_000L
    }
}
