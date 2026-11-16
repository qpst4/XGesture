package com.slideindex.app.overlay

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.service.SlideIndexAccessibilityGestureInjector
import com.slideindex.app.settings.AppSettings
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.hypot

internal class FloatingPointerSession(
    val density: Float,
    val screenWidth: Float,
    val screenHeight: Float,
    private val settingsSource: () -> AppSettings
) {
    val tapSlopPx: Float
        get() = clickDistanceThresholdPx()

    fun hoverPauseDelayMs(settings: AppSettings = settingsSource()): Long =
        settings.floatBallHoverPauseDelayMs.coerceIn(200, 1000).toLong()

    fun regionalCancelSlopPx(settings: AppSettings = settingsSource()): Float =
        settings.floatBallRegionalCancelSlopDp.coerceIn(3f, 30f) * density

    fun clickDistanceThresholdPx(settings: AppSettings = settingsSource()): Float =
        settings.floatingPointerClickDistanceThresholdDp.coerceIn(1f, 30f) * density

    fun joystickDiameterPx(): Float = settingsSource().floatingPointerJoystickDiameterPx
    fun joystickRadiusPx(): Float = joystickDiameterPx() / 2f

    fun touchCaptureDiameterPx(settings: AppSettings = settingsSource()): Float {
        val joystick = joystickDiameterPx()
        if (!settings.floatingPointerRadialAlwaysVisible) return joystick
        return maxOf(joystick, settings.floatingPointerRadialOuterDiameterPx)
    }

    fun touchCaptureRadiusPx(settings: AppSettings = settingsSource()): Float =
        touchCaptureDiameterPx(settings) / 2f

    /** True when the pointer sits inside the expanded touch-capture disc (QC `m81.c`). */
    fun isPointerInsideTouchCapture(settings: AppSettings = settingsSource()): Boolean {
        val radius = touchCaptureRadiusPx(settings)
        val dx = pointerX.floatValue - joystickCenterX.floatValue
        val dy = pointerY.floatValue - joystickCenterY.floatValue
        return kotlin.math.hypot(dx.toDouble(), dy.toDouble()) <= radius
    }

    /** True when a screen touch falls inside the joystick / radial touch-capture disc. */
    fun isFingerInsideTouchCapture(
        fingerX: Float,
        fingerY: Float,
        settings: AppSettings = settingsSource()
    ): Boolean {
        val radius = touchCaptureRadiusPx(settings)
        val dx = fingerX - joystickCenterX.floatValue
        val dy = fingerY - joystickCenterY.floatValue
        return kotlin.math.hypot(dx.toDouble(), dy.toDouble()) <= radius
    }

    fun radialMenuCenterForInput(): Pair<Float, Float> {
        val usePinnedRadialCenter = radialMenuActive.value || radialMenuIdle.value
        return if (usePinnedRadialCenter) {
            radialMenuCenterX to radialMenuCenterY
        } else {
            joystickCenterX.floatValue to joystickCenterY.floatValue
        }
    }

    val pointerX = mutableFloatStateOf(screenWidth / 2f)
    val pointerY = mutableFloatStateOf(screenHeight / 2f)
    /** Unclamped pointer used for edge-action overscroll detection (QC raw tracker position). */
    val rawPointerX = mutableFloatStateOf(screenWidth / 2f)
    val rawPointerY = mutableFloatStateOf(screenHeight / 2f)
    val edgeActionSegments = mutableStateListOf<FloatingPointerEdgeActionSegment>()
    val edgePreviewVisible = mutableStateOf(false)
    internal var onEdgeActionTriggered: ((GestureAction) -> Unit)? = null
    val joystickCenterX = mutableFloatStateOf(0f)
    val joystickCenterY = mutableFloatStateOf(0f)
    val joystickActive = mutableStateOf(false)
    /** QC `m81.s` mirror for lifecycle passthrough (`c/d`) decisions. */
    var activeTouchPhase = FloatingPointerTouchPhase.PendingTapOrDrag
    /** When hide-on-release is enabled, stays true after tap; false only after drag release. */
    val pointerVisible = mutableStateOf(true)
    val trailPoints = mutableStateListOf<FloatingPointerTrailPoint>()
    /**
     * QC `j60` live samples while recording.
     * Replay injection uses a private copy; retreat drawing uses [gestureRecorderTrailDrawPoints].
     */
    internal val gestureRecorderTrailPoints = CopyOnWriteArrayList<GestureRecorderTrailPoint>()
    private var gestureRecorderTrailDrawPoints = CopyOnWriteArrayList<GestureRecorderTrailPoint>()
    val gestureRecorderTrailRevision = mutableIntStateOf(0)
    /** Non-null value temporarily overrides [AppSettings.floatingPointerTrailDurationMs]. */
    var trailLifespanOverrideMs: Long? = null

    /** Radial / edge injected swipe preview (screen space, not tied to pointer presence). */
    val injectedSwipePreview = mutableStateOf<FloatingPointerInjectedSwipePreview?>(null)
    val rippleActive = mutableStateOf(false)
    val rippleCenterX = mutableFloatStateOf(0f)
    val rippleCenterY = mutableFloatStateOf(0f)
    val rippleStartTimeMs = mutableLongStateOf(0L)
    val rippleGeneration = mutableIntStateOf(0)
    val pointerClickGeneration = mutableIntStateOf(0)
    val radialMenuActive = mutableStateOf(false)
    val radialMenuIdle = mutableStateOf(false)
    val radialHighlightedSlot = mutableIntStateOf(-1)
    var radialMenuCenterX = 0f
    var radialMenuCenterY = 0f

    /** Active QC-style gesture recorder, if any. */
    internal var gestureRecorder: FloatingPointerGestureRecorder? = null
        private set

    /** Compose-visible flag for QC-style recorder pointer shrink animation. */
    val gestureRecordingActive = mutableStateOf(false)

    /** Real-time gesture capture: same pointer shrink as recording, without recorder state. */
    val realtimeGestureActive = mutableStateOf(false)

    /** 实时手势类型："zoom" / "scroll"；仅用于指针动作提示。 */
    val realtimeGestureKind = mutableStateOf<String?>(null)

    /** After finger-up: trail retreats on a wall-clock timeline until consumed. */
    val gestureTrailRetreatActive = mutableStateOf(false)

    /** Bumped when the pointer should immediately reappear after gesture aftermath. */
    val pointerRestoreGeneration = mutableIntStateOf(0)

    private var gesturePointerRestoreX: Float? = null
    private var gesturePointerRestoreY: Float? = null
    private var gestureReplayStartedAtMs: Long? = null
    private var gestureReplayTotalMs: Long? = null

    /** Active QC-style real-time gesture, if any. */
    internal var realtimeGesture: FloatingPointerRealtimeGesture? = null
        private set

    /** 实时双指缩放（指针专属）。 */
    internal var pinchGesture: FloatingPointerPinchGesture? = null
        private set

    /** 实时滚动（指针专属）。 */
    internal var scrollGesture: FloatingPointerScrollGesture? = null
        private set

    /**
     * Set when a radial-menu slot with a gesture-capture action is confirmed on finger lift.
     * The next touch down starts capture immediately.
     */
    internal var pendingGestureCaptureAction: GestureAction? = null
        private set

    /** Joystick dock position saved on finger lift during gesture capture (QC-style). */
    private var gestureCaptureDockX: Float? = null
    private var gestureCaptureDockY: Float? = null
    /** Finger-to-joystick-center offset armed when gesture capture starts on a radial slot. */
    private var gestureCaptureCenterOffsetX = 0f
    private var gestureCaptureCenterOffsetY = 0f

    /** True while a recorded gesture is being injected (overlay must not track touches). */
    val gestureReplayActive = mutableStateOf(false)
    private var edgeOverscrollActive = false
    private var edgeSettingsFingerprint = 0

    fun beginGestureReplay() {
        gestureReplayActive.value = true
        gestureRecordingActive.value = false
    }

    fun beginGestureReplayPrepare(
        replayDurationMs: Long,
        replayStartDelayMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ) {
        gestureRecorderTrailDrawPoints = CopyOnWriteArrayList(
            gestureRecorderTrailPoints.map { it.copy() }
        )
        gestureRecorderTrailDrawPoints.lastOrNull()?.let { last ->
            gesturePointerRestoreX = last.x.toFloat()
            gesturePointerRestoreY = last.y.toFloat()
        }
        gestureReplayTotalMs = replayDurationMs.coerceAtLeast(1L)
        gestureReplayStartedAtMs = nowMs + replayStartDelayMs.coerceAtLeast(0L)
        gestureTrailRetreatActive.value = true
        trailLifespanOverrideMs = null
        notifyGestureRecorderTrailChanged()
    }

    fun gestureReplayTotalDurationMs(): Long =
        gestureReplayTotalMs?.coerceAtLeast(1L) ?: 1L

    fun gestureRecorderConsumedDurationMs(nowMs: Long = System.currentTimeMillis()): Long {
        val startedAt = gestureReplayStartedAtMs ?: return 0L
        if (nowMs < startedAt) return 0L
        return (nowMs - startedAt).coerceAtMost(gestureReplayTotalDurationMs())
    }

    fun isGestureRecorderTrailRetreatConsumed(nowMs: Long = System.currentTimeMillis()): Boolean =
        gestureRecorderConsumedDurationMs(nowMs) >= gestureReplayTotalDurationMs()

    /**
     * Recording: live list. Retreat: frozen snapshot clipped by replay wall-clock
     * (works for both chained ko and single-path fallback).
     */
    fun gestureRecorderTrailPointsForDraw(nowMs: Long = System.currentTimeMillis()): List<GestureRecorderTrailPoint> {
        if (gestureRecordingActive.value) {
            return gestureRecorderTrailPoints.toList()
        }
        if (!gestureTrailRetreatActive.value) return emptyList()
        return gestureRecorderTrailSuffixPoints(
            points = gestureRecorderTrailDrawPoints,
            consumedDurationMs = gestureRecorderConsumedDurationMs(nowMs)
        )
    }

    fun finishGestureTrailRetreatIfConsumed(settings: AppSettings): Boolean {
        if (!gestureTrailRetreatActive.value) return false
        if (!isGestureRecorderTrailRetreatConsumed()) return false
        gestureTrailRetreatActive.value = false
        gestureReplayStartedAtMs = null
        gestureReplayTotalMs = null
        gesturePointerRestoreX?.let { pointerX.floatValue = it }
        gesturePointerRestoreY?.let { pointerY.floatValue = it }
        gesturePointerRestoreX = null
        gesturePointerRestoreY = null
        clearGestureRecorderTrail()
        trailLifespanOverrideMs = null
        pointerVisible.value = !settings.floatingPointerHideWhenJoystickReleased
        pointerRestoreGeneration.intValue++
        clearGestureCaptureJoystickOffset()
        return true
    }

    fun endGestureReplay() {
        gestureReplayActive.value = false
    }

    /** True while gesture capture is sampling, replaying, or armed for the next touch. */
    val gestureCaptureActive: Boolean
        get() = gestureRecorder != null || realtimeGesture != null || pinchGesture != null ||
            scrollGesture != null || pendingGestureCaptureAction != null

    fun armPendingGestureCapture(action: GestureAction) {
        pendingGestureCaptureAction = action
    }

    fun clearPendingGestureCapture() {
        pendingGestureCaptureAction = null
    }

    fun dockJoystickAfterGestureCapture(fingerX: Float, fingerY: Float) {
        val (centerX, centerY) = gestureCaptureJoystickCenterForFinger(fingerX, fingerY)
        gestureCaptureDockX = centerX
        gestureCaptureDockY = centerY
        joystickCenterX.floatValue = centerX
        joystickCenterY.floatValue = centerY
        clearGestureCaptureJoystickOffset()
    }

    /**
     * Keep the radial-menu slot (or joystick) fixed under the finger while gesture capture moves.
     * Offset is center - finger at the moment capture arms.
     */
    fun armGestureCaptureJoystickOffset(fingerX: Float, fingerY: Float) {
        gestureCaptureCenterOffsetX = joystickCenterX.floatValue - fingerX
        gestureCaptureCenterOffsetY = joystickCenterY.floatValue - fingerY
    }

    fun gestureCaptureJoystickCenterForFinger(fingerX: Float, fingerY: Float): Pair<Float, Float> =
        (fingerX + gestureCaptureCenterOffsetX) to (fingerY + gestureCaptureCenterOffsetY)

    fun clearGestureCaptureJoystickOffset() {
        gestureCaptureCenterOffsetX = 0f
        gestureCaptureCenterOffsetY = 0f
    }

    fun takeGestureCaptureDock(): Pair<Float, Float>? {
        val x = gestureCaptureDockX
        val y = gestureCaptureDockY
        gestureCaptureDockX = null
        gestureCaptureDockY = null
        return if (x != null && y != null) x to y else null
    }

    fun releaseGestureRecorder() {
        gestureRecorder = null
        gestureRecordingActive.value = false
        clearGestureCaptureJoystickOffset()
    }

    fun prepareGesturePointerRestore(x: Float, y: Float) {
        gesturePointerRestoreX = x
        gesturePointerRestoreY = y
    }

    fun notifyGestureRecorderTrailChanged() {
        gestureRecorderTrailRevision.intValue++
    }

    /**
     * 手势收尾（回填指针位置 + 清理轨迹）是否还没做完。
     *
     * 这些字段不是 Compose 状态，只能靠逐帧回调兜底；[FloatingPointerDisplay] 的
     * 逐帧循环用它决定是否继续吃 vsync。
     */
    internal val hasPendingGestureAftermath: Boolean
        get() = gesturePointerRestoreX != null ||
            gesturePointerRestoreY != null ||
            gestureRecorderTrailPoints.isNotEmpty() ||
            trailLifespanOverrideMs != null

    /** QC `o21.m`: clear trail, restore pointer after replay + retreat end. */
    fun completeGestureAftermathIfReady(settings: AppSettings): Boolean {
        if (gestureReplayActive.value) return false
        if (gestureRecordingActive.value || gestureRecorder != null) return false
        if (gestureTrailRetreatActive.value) return false
        val aftermathPending = gesturePointerRestoreX != null ||
            gesturePointerRestoreY != null ||
            gestureRecorderTrailPoints.isNotEmpty() ||
            trailLifespanOverrideMs != null
        if (!aftermathPending) return false
        gesturePointerRestoreX?.let { pointerX.floatValue = it }
        gesturePointerRestoreY?.let { pointerY.floatValue = it }
        gesturePointerRestoreX = null
        gesturePointerRestoreY = null
        clearTrail()
        clearGestureRecorderTrail()
        trailLifespanOverrideMs = null
        pointerVisible.value = !settings.floatingPointerHideWhenJoystickReleased
        pointerRestoreGeneration.intValue++
        clearGestureCaptureJoystickOffset()
        return true
    }

    fun releaseRealtimeGesture() {
        realtimeGesture = null
        pinchGesture = null
        scrollGesture = null
        realtimeGestureActive.value = false
        realtimeGestureKind.value = null
    }

    /** 指针长按/功能环触发的实时双指缩放。 */
    fun startPinchGesture(
        service: android.accessibilityservice.AccessibilityService,
        pointerX: Float,
        pointerY: Float,
        onError: () -> Unit,
        onFinished: () -> Unit,
    ) {
        clearPendingGestureCapture()
        realtimeGestureActive.value = true
        realtimeGestureKind.value = "zoom"
        pinchGesture = FloatingPointerPinchGesture(
            service = service,
            startX = pointerX,
            startY = pointerY,
            onError = {
                releaseRealtimeGesture()
                onError()
            },
            onFinished = {
                releaseRealtimeGesture()
                onFinished()
            },
        )
    }

    /** 指针长按/功能环触发的实时滚动。 */
    fun startScrollGesture(
        service: android.accessibilityservice.AccessibilityService,
        pointerX: Float,
        pointerY: Float,
        onError: () -> Unit,
        onFinished: () -> Unit
    ) {
        clearPendingGestureCapture()
        realtimeGestureActive.value = true
        realtimeGestureKind.value = "scroll"
        scrollGesture = FloatingPointerScrollGesture(
            service = service,
            startX = pointerX,
            startY = pointerY,
            onError = {
                releaseRealtimeGesture()
                onError()
            },
            onFinished = {
                releaseRealtimeGesture()
                onFinished()
            }
        )
    }

    fun openRadialMenu(centerX: Float, centerY: Float) {
        radialMenuCenterX = centerX
        radialMenuCenterY = centerY
        joystickCenterX.floatValue = centerX
        joystickCenterY.floatValue = centerY
        radialMenuActive.value = true
        radialMenuIdle.value = false
        radialHighlightedSlot.intValue = -1
    }

    fun closeRadialMenu() {
        radialMenuActive.value = false
        radialMenuIdle.value = false
        radialHighlightedSlot.intValue = -1
    }

    fun updateRadialHighlight(fingerX: Float, fingerY: Float, settings: AppSettings): Boolean {
        if (!radialMenuActive.value && !radialMenuIdle.value) return false
        val inner = settings.floatingPointerRadialInnerDiameterPx / 2f
        val outer = settings.floatingPointerRadialOuterDiameterPx / 2f
        val previous = radialHighlightedSlot.intValue
        val newSlot = FloatingPointerRadialMenu.sectorIndexAt(
            centerX = radialMenuCenterX,
            centerY = radialMenuCenterY,
            fingerX = fingerX,
            fingerY = fingerY,
            innerRadius = inner,
            outerRadius = outer
        ) ?: -1
        radialHighlightedSlot.intValue = newSlot
        return newSlot >= 0 && newSlot != previous
    }

    /** True until the first touch places joystick and pointer near the finger. */
    var awaitingPlacement = false

    /**
     * True while an edge continuous handoff finger is still driving this session.
     * Used to hide always-visible radial when release-click-and-dismiss is enabled.
     */
    val continuedEdgeSessionActive = mutableStateOf(false)

    /** Hides radial chrome while a system screenshot capture is in flight. */
    val screenshotCaptureInProgress = mutableStateOf(false)

    /** Continued-edge hover-select chrome for [FloatBallCursorPreviewView]. */
    val hoverSelectChrome = mutableStateOf(FloatingPointerHoverSelectChrome())

    private var pointerTravelWidth = 0f
    private var pointerTravelHeight = 0f
    private var dragFingerAnchorX = 0f
    private var dragFingerAnchorY = 0f
    private var dragPointerAnchorX = 0f
    private var dragPointerAnchorY = 0f
    /** Joystick center minus finger at touch-down; keeps grab point fixed while dragging. */
    private var dragJoystickOffsetX = 0f
    private var dragJoystickOffsetY = 0f

    /**
     * Re-anchors drag when gesture capture arms mid-gesture (e.g. sliding into a radial
     * "gesture record" sector). Radial-menu drags skip [applyPointerFromTouch]; reset anchors
     * at the current finger without moving the pointer so the next MOVE does not jump.
     */
    fun syncPointerForGestureCapture(fingerX: Float, fingerY: Float) {
        clearTrail()
        armDrag(fingerX, fingerY)
    }

    /** Starts a QC-style gesture recorder at the given pointer position. */
    fun startGestureRecorder(
        service: android.accessibilityservice.AccessibilityService,
        pointerX: Float,
        pointerY: Float,
        onReplayPrepare: (replayDurationMs: Long) -> Unit,
        onFinished: () -> Unit
    ) {
        clearPendingGestureCapture()
        gestureTrailRetreatActive.value = false
        gestureReplayActive.value = false
        gestureReplayStartedAtMs = null
        gestureReplayTotalMs = null
        gesturePointerRestoreX = null
        gesturePointerRestoreY = null
        clearTrail()
        clearGestureRecorderTrail()
        trailLifespanOverrideMs = GESTURE_CAPTURE_TRAIL_MS
        gestureRecordingActive.value = true
        gestureRecorder = FloatingPointerGestureRecorder(
            service = service,
            startX = pointerX,
            startY = pointerY,
            points = gestureRecorderTrailPoints,
            onPointsChanged = { notifyGestureRecorderTrailChanged() },
            onReplayPrepare = { replayDurationMs ->
                beginGestureReplayPrepare(
                    replayDurationMs = replayDurationMs,
                    replayStartDelayMs = FloatingPointerGestureRecorder.REPLAY_START_DELAY_MS
                )
                onReplayPrepare(replayDurationMs)
            },
            onFinished = {
                releaseGestureRecorder()
                onFinished()
            }
        )
    }

    /** Updates the active gesture recorder with the current pointer position. */
    fun updateGestureRecorder() {
        gestureRecorder?.updatePosition(pointerX.floatValue, pointerY.floatValue)
    }

    /** QC `b60`: draw whenever the remaining path still has a visible segment. */
    fun shouldDrawGestureRecorderTrail(nowMs: Long = System.currentTimeMillis()): Boolean =
        gestureRecorderTrailPointsForDraw(nowMs).size >= 2

    fun hasActiveGestureRecorderTrail(nowMs: Long = System.currentTimeMillis()): Boolean =
        shouldDrawGestureRecorderTrail(nowMs)

    /** Stops the gesture recorder and begins replay. */
    fun finishGestureRecorder() {
        gestureRecorder?.finish()
    }

    /** Starts a QC-style real-time gesture at the given pointer position. */
    fun startRealtimeGesture(
        service: android.accessibilityservice.AccessibilityService,
        pointerX: Float,
        pointerY: Float,
        onError: () -> Unit,
        onFinished: () -> Unit
    ) {
        clearPendingGestureCapture()
        realtimeGestureActive.value = true
        realtimeGesture = FloatingPointerRealtimeGesture(
            service = service,
            startX = pointerX,
            startY = pointerY,
            onError = {
                releaseRealtimeGesture()
                onError()
            },
            onFinished = {
                releaseRealtimeGesture()
                onFinished()
            }
        )
    }

    /** 实时缩放由手指位移驱动（指针冻结），见 [updatePinchByFinger]。 */
    fun updatePinchByFinger(dx: Float, dy: Float) {
        pinchGesture?.updateFingerDelta(dx, dy)
    }

    /** Updates the active real-time gesture with the current pointer position. */
    fun updateRealtimeGesture() {
        realtimeGesture?.updatePosition(pointerX.floatValue, pointerY.floatValue)
        scrollGesture?.updatePosition(pointerX.floatValue, pointerY.floatValue)
    }

    /** Ends the active real-time gesture. */
    fun finishRealtimeGesture() {
        realtimeGesture?.finish()
        pinchGesture?.finish()
        scrollGesture?.finish()
    }

    fun triggerRipple(x: Float, y: Float) {
        rippleCenterX.floatValue = x
        rippleCenterY.floatValue = y
        rippleStartTimeMs.longValue = System.currentTimeMillis()
        rippleActive.value = true
        rippleGeneration.intValue++
    }

    fun triggerPointerClick() {
        pointerClickGeneration.intValue++
    }

    fun clearRippleIfExpired(nowMs: Long, durationMs: Long = FLOATING_POINTER_RIPPLE_DURATION_MS) {
        if (!rippleActive.value) return
        if (nowMs - rippleStartTimeMs.longValue >= durationMs) {
            rippleActive.value = false
        }
    }

    fun placeJoystickDefault() {
        val margin = 32f * density
        val radius = joystickRadiusPx()
        joystickCenterX.floatValue = margin + radius
        joystickCenterY.floatValue = screenHeight - margin - radius
    }

    fun placeAtTouch(rawX: Float, rawY: Float, settings: AppSettings) {
        val center = clampJoystickCenter(rawX, rawY, settings)
        joystickCenterX.floatValue = center.x
        joystickCenterY.floatValue = center.y
        establishPointerTravel(settings)
        awaitingPlacement = false
        armDrag(rawX, rawY)
        updateEdgeActions(settings)
    }

    /**
     * Arms edge-continued control without lifting the finger. Joystick stays under the finger;
     * pointer snaps to the nearest screen edge at the trigger height/position.
     */
    fun prepareContinuedEdgeGesture(rawX: Float, rawY: Float, settings: AppSettings) {
        establishPointerTravel(settings)
        joystickCenterX.floatValue = rawX
        joystickCenterY.floatValue = rawY
        applyEdgePointerStart(rawX, rawY)
        awaitingPlacement = false
        armDrag(rawX, rawY)
        updateEdgeActions(settings)
    }

    private fun applyEdgePointerStart(rawX: Float, rawY: Float) {
        val edgeThresholdPx = 48f * density
        val start = FloatingPointerBounds.pointerStartForEdgeTrigger(
            rawX = rawX,
            rawY = rawY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeThresholdPx = edgeThresholdPx
        )
        pointerX.floatValue = start.x
        pointerY.floatValue = start.y
        rawPointerX.floatValue = start.x
        rawPointerY.floatValue = start.y
    }

    /** Arms a new drag without moving the pointer or re-anchoring the joystick area. */
    fun beginGesture(rawX: Float, rawY: Float, settings: AppSettings) {
        if (awaitingPlacement || !hasEstablishedPointerTravel()) {
            placeAtTouch(rawX, rawY, settings)
        } else {
            armDrag(rawX, rawY)
        }
    }

    private fun armDrag(fingerX: Float, fingerY: Float) {
        dragFingerAnchorX = fingerX
        dragFingerAnchorY = fingerY
        dragPointerAnchorX = pointerX.floatValue
        dragPointerAnchorY = pointerY.floatValue
        dragJoystickOffsetX = joystickCenterX.floatValue - fingerX
        dragJoystickOffsetY = joystickCenterY.floatValue - fingerY
    }

    fun joystickCenterForFinger(fingerX: Float, fingerY: Float): Pair<Float, Float> =
        (fingerX + dragJoystickOffsetX) to (fingerY + dragJoystickOffsetY)

    /** Re-anchors drag after joystick center is adjusted externally (e.g. edge continue-gesture). */
    fun rearmJoystickDrag(fingerX: Float, fingerY: Float) {
        armDrag(fingerX, fingerY)
    }

    fun hasEstablishedPointerTravel(): Boolean =
        pointerTravelWidth > 0f && pointerTravelHeight > 0f

    private fun establishPointerTravel(settings: AppSettings) {
        val (travelWidth, travelHeight) = FloatingPointerBounds.effectivePointerTravel(
            settings = settings,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        pointerTravelWidth = travelWidth
        pointerTravelHeight = travelHeight
    }

    fun refreshPointerTravel(settings: AppSettings) {
        if (!hasEstablishedPointerTravel()) return
        establishPointerTravel(settings)
    }

    private fun clampJoystickCenter(rawX: Float, rawY: Float, settings: AppSettings): Offset {
        val (travelWidth, travelHeight) = FloatingPointerBounds.effectivePointerTravel(
            settings = settings,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        return FloatingPointerBounds.clampJoystickCenter(
            rawX = rawX,
            rawY = rawY,
            joystickRadiusPx = joystickRadiusPx(),
            travelWidth = travelWidth,
            travelHeight = travelHeight,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            density = density
        )
    }

    fun applyLayoutSettings(settings: AppSettings) {
        val next = FloatingPointerBounds.clamp(
            position = Offset(pointerX.floatValue, pointerY.floatValue),
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        pointerX.floatValue = next.x
        pointerY.floatValue = next.y
        refreshPointerTravel(settings)
    }

    fun clearTrail() {
        trailPoints.clear()
        trailLifespanOverrideMs = null
        injectedSwipePreview.value = null
    }

    /**
     * Screen-space swipe preview for radial slots / edge [PointerSwipeConfig] injects.
     * Endpoints match [SlideIndexAccessibilityGestureInjector.dispatchPointerSwipe].
     */
    fun beginInjectedSwipePreview(startX: Float, startY: Float, config: PointerSwipeConfig) {
        val (dx, dy) = config.delta(density)
        val swipeDurationMs = config.durationMs.toLong().coerceIn(20L, 800L)
        val sanitized = SlideIndexAccessibilityGestureInjector.sanitizeSwipeEndpoints(
            startX = startX,
            startY = startY,
            endX = startX + dx,
            endY = startY + dy,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            minDistancePx = 24f * density
        ) ?: return
        val fadeDurationMs = settingsSource()
            .floatingPointerTrailDurationMs
            .coerceAtLeast(50)
            .toLong()
        injectedSwipePreview.value = FloatingPointerInjectedSwipePreview(
            startX = sanitized.startX,
            startY = sanitized.startY,
            endX = sanitized.endX,
            endY = sanitized.endY,
            startedAtMs = System.currentTimeMillis(),
            swipeDurationMs = swipeDurationMs,
            fadeDurationMs = fadeDurationMs
        )
    }

    fun clearInjectedSwipePreviewIfExpired(nowMs: Long = System.currentTimeMillis()) {
        val preview = injectedSwipePreview.value ?: return
        if (preview.isExpired(nowMs)) {
            injectedSwipePreview.value = null
        }
    }

    fun hasActiveInjectedSwipePreview(nowMs: Long = System.currentTimeMillis()): Boolean {
        val preview = injectedSwipePreview.value ?: return false
        return !preview.isExpired(nowMs)
    }

    fun clearGestureRecorderTrail() {
        gestureRecorderTrailPoints.clear()
        gestureRecorderTrailDrawPoints.clear()
        notifyGestureRecorderTrailChanged()
    }

    fun effectiveTrailNowMs(nowMs: Long = System.currentTimeMillis()): Long = nowMs

    fun pruneExpiredTrailPoints(nowMs: Long = System.currentTimeMillis()) {
        if (gestureRecordingActive.value || gestureReplayActive.value || gestureTrailRetreatActive.value) return
        val settings = settingsSource()
        val maxAge = trailLifespanOverrideMs
            ?: settings.floatingPointerTrailDurationMs.coerceAtLeast(50).toLong()
        val cutoff = nowMs - maxAge
        while (trailPoints.isNotEmpty() && trailPoints.first().timeMs < cutoff) {
            if (trailPoints.size <= 2) break
            trailPoints.removeAt(0)
        }
    }

    fun hasActiveTrail(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (gestureRecordingActive.value || gestureReplayActive.value || gestureTrailRetreatActive.value) {
            return false
        }
        if (trailPoints.size < 2) return false
        val maxAge = trailLifespanOverrideMs
            ?: settingsSource().floatingPointerTrailDurationMs.coerceAtLeast(50).toLong()
        val cutoff = nowMs - maxAge
        return trailPoints.last().timeMs >= cutoff
    }

    fun isNearPointer(rawX: Float, rawY: Float): Boolean {
        val hitRadius = settingsSource().floatingPointerPointerDiameterPx / 2f + tapSlopPx
        return kotlin.math.hypot(
            (rawX - pointerX.floatValue).toDouble(),
            (rawY - pointerY.floatValue).toDouble()
        ) <= hitRadius
    }

    private fun recordTrail(x: Float, y: Float) {
        if (gestureRecordingActive.value) return
        val settings = settingsSource()
        var trailType = com.slideindex.app.settings.FloatingPointerTrailType.fromId(
            settings.floatingPointerTrailTypeId
        )
        if (gestureRecordingActive.value && trailType == com.slideindex.app.settings.FloatingPointerTrailType.OFF) {
            trailType = com.slideindex.app.settings.FloatingPointerTrailType.SIMPLE
        }
        FloatingPointerTrailSampler.recordPoint(
            points = trailPoints,
            x = x,
            y = y,
            nowMs = System.currentTimeMillis(),
            type = trailType,
            density = density
        )
        // Expiration is handled by pruneExpiredTrailPoints in the display frame loop so that
        // temporary lifespan overrides (e.g. during gesture recording) are respected.
    }

    fun applyPointerFromTouch(rawX: Float, rawY: Float, settings: AppSettings) {
        if (gestureReplayActive.value) return
        if (pointerTravelWidth <= 0f || pointerTravelHeight <= 0f) return
        val normDeltaX = if (pointerTravelWidth > 0f) (rawX - dragFingerAnchorX) / pointerTravelWidth else 0f
        val normDeltaY = if (pointerTravelHeight > 0f) (rawY - dragFingerAnchorY) / pointerTravelHeight else 0f
        val unclamped = Offset(
            x = dragPointerAnchorX + normDeltaX * screenWidth,
            y = dragPointerAnchorY + normDeltaY * screenHeight
        )
        rawPointerX.floatValue = unclamped.x
        rawPointerY.floatValue = unclamped.y
        val next = Offset(
            x = unclamped.x.coerceIn(0f, screenWidth),
            y = unclamped.y.coerceIn(0f, screenHeight)
        )
        pointerX.floatValue = next.x
        pointerY.floatValue = next.y
        if (!gestureRecordingActive.value) {
            recordTrail(next.x, next.y)
        }
        updateGestureRecorder()
        updateRealtimeGesture()
        updateEdgeActions(settings)
    }

    private fun updateEdgeActions(settings: AppSettings) {
        if (gestureCaptureActive || gestureReplayActive.value) {
            edgePreviewVisible.value = false
            edgeActionSegments.forEach { it.proximity = 0f }
            return
        }
        val fingerprint = edgeSettingsFingerprint(settings)
        if (fingerprint != edgeSettingsFingerprint || edgeActionSegments.isEmpty()) {
            edgeSettingsFingerprint = fingerprint
            edgeActionSegments.clear()
            edgeActionSegments.addAll(
                FloatingPointerEdgeActionsEngine.buildSegments(
                    settings = settings,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    density = density
                )
            )
        }
        val previewRadius = FloatingPointerEdgeActionsEngine.previewRadiusPx(settings, density)
        FloatingPointerEdgeActionsEngine.updateProximity(
            segments = edgeActionSegments,
            rawX = rawPointerX.floatValue,
            rawY = rawPointerY.floatValue,
            previewRadiusPx = previewRadius
        )
        edgePreviewVisible.value = FloatingPointerEdgeActionsEngine.shouldShowPreview(
            settings = settings,
            segments = edgeActionSegments,
            gestureCaptureActive = gestureCaptureActive,
            gestureReplayActive = gestureReplayActive.value,
            armedAction = null
        )
        val triggered = FloatingPointerEdgeActionsEngine.resolveTriggeredAction(
            settings = settings,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            clampedX = pointerX.floatValue,
            clampedY = pointerY.floatValue,
            rawX = rawPointerX.floatValue,
            rawY = rawPointerY.floatValue,
            density = density
        )
        if (triggered != null) {
            if (!edgeOverscrollActive) {
                edgeOverscrollActive = true
                onEdgeActionTriggered?.invoke(triggered)
            }
        } else {
            edgeOverscrollActive = false
        }
    }

    private fun edgeSettingsFingerprint(settings: AppSettings): Int {
        var hash = settings.floatingPointerEdgeThresholdDp.hashCode()
        hash = 31 * hash + settings.floatingPointerEdgePreviewSensitivity
        hash = 31 * hash + settings.floatingPointerEdgePreviewGlowSize
        hash = 31 * hash + settings.floatingPointerEdgeVisualSizeDp.hashCode()
        hash = 31 * hash + settings.floatingPointerEdgeVisualColorArgb
        hash = 31 * hash + settings.floatingPointerEdgeActionsConfig.hashCode()
        hash = 31 * hash + screenWidth.hashCode()
        hash = 31 * hash + screenHeight.hashCode()
        return hash
    }

    private companion object {
        private const val GESTURE_CAPTURE_TRAIL_MS = 4_000L

        private fun gestureRecorderTrailSuffixPoints(
            points: List<GestureRecorderTrailPoint>,
            consumedDurationMs: Long
        ): List<GestureRecorderTrailPoint> {
            if (points.size < 2) return emptyList()
            val totalDuration = points.drop(1).sumOf { it.durationMs.coerceAtLeast(0L) }.coerceAtLeast(1L)
            if (consumedDurationMs >= totalDuration) return emptyList()
            if (consumedDurationMs <= 0L) return points

            var walkedMs = 0L
            for (index in 0 until points.lastIndex) {
                val segmentMs = points[index + 1].durationMs.coerceAtLeast(1L)
                if (walkedMs + segmentMs > consumedDurationMs) {
                    val from = points[index]
                    val to = points[index + 1]
                    val fraction = ((consumedDurationMs - walkedMs).toFloat() / segmentMs.toFloat())
                        .coerceIn(0f, 1f)
                    val head = GestureRecorderTrailPoint(
                        x = (from.x + (to.x - from.x) * fraction).toInt(),
                        y = (from.y + (to.y - from.y) * fraction).toInt(),
                        durationMs = 0L
                    )
                    return listOf(head) + points.subList(index + 1, points.size)
                }
                walkedMs += segmentMs
            }
            return emptyList()
        }
    }
}
