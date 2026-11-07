package com.slideindex.app.overlay.backpanel

import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** Ported from AOSP SystemUI `BackPanelController` visual state machine (Apache-2.0). */
class BackPanelController(
    private val panel: BackPanel,
    private val viewConfiguration: ViewConfiguration = ViewConfiguration.get(panel.context),
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    var params: EdgePanelParams = EdgePanelParams(panel.resources)
        internal set
    var currentState: GestureState = GestureState.GONE
        private set
    private var previousState: GestureState = GestureState.GONE

    private val layoutParams = FrameLayout.LayoutParams(0, 0)
    private val displaySize = Point()

    /**
     * 顶/底边复用同一份「水平箭头」：把整块 view 旋转 90°（顶边 +90、底边 -90）。
     *
     * 0° 时保持原来左右边的行为。旋转后条带的厚度变成 [EdgePanelParams.panelWidth]，
     * 需要在 [applyLayout] 里补一个纵向平移贴住所在边（见 [edgeBandCenterY]）。
     */
    private var edgeRotationDegrees = 0f
    private var edgeScreenHeightPx = 0

    var extraFingerOffsetPx: Float = 0f
    var onSettled: (() -> Unit)? = null
    private var suppressSettled = false

    private var previousXTranslationOnActiveOffset = 0f
    private var previousXTranslation = 0f
    private var totalTouchDeltaActive = 0f
    private var totalTouchDeltaInactive = 0f
    private var touchDeltaStartX = 0f
    private var velocityTracker: VelocityTracker? = null
        set(value) {
            if (field != value) field?.recycle()
            field = value
        }
        get() {
            if (field == null) field = VelocityTracker.obtain()
            return field
        }

    private var startX = 0f
    private var fingerDownX = 0f
    private var startY = 0f
    private var downTime = 0L
    private var gestureEntryTime = 0L
    private var gestureInactiveTime = 0L
    private val elapsedTimeSinceInactive
        get() = SystemClock.uptimeMillis() - gestureInactiveTime
    private val elapsedTimeSinceEntry
        get() = SystemClock.uptimeMillis() - gestureEntryTime
    private var pastThresholdWhileEntryOrInactiveTime = 0L
    private var entryToActiveDelay = 0F
    private val entryToActiveDelayCalculation = {
        if (activationThresholdOverridePx != null) {
            0f
        } else {
            convertVelocityToAnimationFactor(
                valueOnFastVelocity = MIN_DURATION_ENTRY_BEFORE_ACTIVE_ANIMATION,
                valueOnSlowVelocity = MAX_DURATION_ENTRY_BEFORE_ACTIVE_ANIMATION,
            )
        }
    }
    private var hasPassedDragSlop = false
    private var entryShowSlopPx = 0f
    private var activationThresholdOverridePx: Float? = null
    private var minFlingDistance = 0
    private var fullyStretchedThreshold = 0f

    private val failsafeRunnable = Runnable { onFailsafe() }
    private val flungAfterInactiveRunnable = Runnable { updateArrowState(GestureState.FLUNG) }
    private val committedAfterInactiveRunnable = Runnable { updateArrowState(GestureState.COMMITTED) }
    private val popOnFlingRunnable = Runnable { panel.popScale(POP_ON_FLING_VELOCITY) }

    enum class GestureState {
        GONE,
        ENTRY,
        ACTIVE,
        INACTIVE,
        FLUNG,
        COMMITTED,
        CANCELLED,
    }

    inner class DelayedOnAnimationEndListener(
        private val handler: Handler,
        private val runnableDelay: Long,
        val runnable: Runnable,
    ) : DynamicAnimation.OnAnimationEndListener {
        override fun onAnimationEnd(
            animation: DynamicAnimation<*>,
            canceled: Boolean,
            value: Float,
            velocity: Float,
        ) {
            animation.removeEndListener(this)
            if (!canceled) {
                val delay = max(0, runnableDelay - elapsedTimeSinceEntry)
                handler.postDelayed(runnable, delay)
            }
        }

        fun run() = runnable.run()
    }

    private val onEndSetCommittedStateListener =
        DelayedOnAnimationEndListener(mainHandler, 0L) { updateArrowState(GestureState.COMMITTED) }

    private val onEndSetGoneStateListener =
        DelayedOnAnimationEndListener(mainHandler, runnableDelay = 0L) {
            cancelFailsafe()
            updateArrowState(GestureState.GONE)
        }

    private val onAlphaEndSetGoneStateListener =
        DelayedOnAnimationEndListener(mainHandler, 0L) {
            updateRestingArrowDimens()
            if (!panel.addAnimationEndListener(panel.backgroundAlpha, onEndSetGoneStateListener)) {
                scheduleFailsafe()
            }
        }

    val isShowing: Boolean
        get() = currentState != GestureState.GONE

    fun attach(parent: ViewGroup) {
        updateConfiguration()
        if (panel.parent !== parent) {
            (panel.parent as? ViewGroup)?.removeView(panel)
            parent.addView(panel, layoutParams)
        }
        panel.z = GESTURE_ANIMATION_Z_INDEX
        updateArrowDirection()
        updateArrowState(GestureState.GONE, force = true)
        updateRestingArrowDimens()
        applyEdgeRotation()
    }

    fun detach() {
        cancelAllPendingAnimations()
        (panel.parent as? ViewGroup)?.removeView(panel)
        velocityTracker = null
        updateArrowState(GestureState.GONE, force = true)
    }

    fun setIsLeftPanel(isLeftPanel: Boolean) {
        panel.isLeftPanel = isLeftPanel
        layoutParams.gravity = if (isLeftPanel) {
            Gravity.LEFT or Gravity.TOP
        } else {
            Gravity.RIGHT or Gravity.TOP
        }
        applyLayout()
    }

    fun setDisplaySize(width: Int, height: Int) {
        displaySize.set(width, height)
        fullyStretchedThreshold = min(width.toFloat(), params.swipeProgressThreshold)
    }

    /**
     * 上/下边把系统箭头旋转 90° 复用（0° = 左右边，保持原行为）。
     *
     * 旋转后 view 的局部 X（原来"向内长"的方向）指向屏幕的 Y，局部 Y（原来"沿边跟随手指"）
     * 指向屏幕的 X；[applyLayout] 会再补一个纵向平移，让旋转后的条带贴住所在一侧。
     *
     * @param degrees 顶边 +90（局部 +X → 屏幕向下）、底边 -90（局部 +X → 屏幕向上）
     * @param screenHeightPx 屏幕高度，用来把条带贴到屏幕底边
     */
    fun setEdgeRotation(degrees: Float, screenHeightPx: Int) {
        if (screenHeightPx > 0) edgeScreenHeightPx = screenHeightPx
        if (edgeRotationDegrees == degrees && panel.rotation == degrees) {
            applyLayout()
            return
        }
        edgeRotationDegrees = degrees
        applyEdgeRotation()
    }

    private fun applyEdgeRotation() {
        panel.rotation = edgeRotationDegrees
        applyLayout()
    }

    /**
     * 旋转后条带（厚度 = panelWidth）贴住所在一侧所需的视觉中心 Y；null = 不旋转。
     */
    private fun edgeBandCenterY(): Float? = when {
        edgeRotationDegrees == 0f -> null
        edgeRotationDegrees > 0f -> params.panelWidth / 2f
        edgeScreenHeightPx <= 0 -> null
        else -> edgeScreenHeightPx - params.panelWidth / 2f
    }

    /** Bind ENTRY→ACTIVE to XGesture's short-swipe distance instead of AOSP's 16dp. */
    fun setActivationThresholdPx(px: Float) {
        activationThresholdOverridePx = px.takeIf { it > 0f }
        applyActivationThresholdOverride()
    }

    fun onDown(x: Float, y: Float, translationOriginX: Float = x) {
        cancelAllPendingAnimations()
        suppressSettled = true
        obtainTrackerEvent(MotionEvent.ACTION_DOWN, x, y)?.let { event ->
            velocityTracker!!.addMovement(event)
            event.recycle()
        }
        startX = translationOriginX
        fingerDownX = x
        startY = y
        downTime = SystemClock.uptimeMillis()
        previousXTranslation = 0f
        previousXTranslationOnActiveOffset = 0f
        totalTouchDeltaActive = 0f
        totalTouchDeltaInactive = 0f
        pastThresholdWhileEntryOrInactiveTime = 0L
        updateArrowState(GestureState.GONE)
        suppressSettled = false
        updateYStartPosition(startY)
        hasPassedDragSlop = false
        panel.resetStretch()
    }

    fun onMove(x: Float, y: Float) {
        obtainTrackerEvent(MotionEvent.ACTION_MOVE, x, y)?.let { event ->
            velocityTracker!!.addMovement(event)
            event.recycle()
        }
        if (dragSlopExceeded(x, y)) {
            handleMoveEvent(x, y)
        }
    }

    fun onUp(x: Float, y: Float) {
        obtainTrackerEvent(MotionEvent.ACTION_UP, x, y)?.let { event ->
            velocityTracker!!.addMovement(event)
            event.recycle()
        }
        when (currentState) {
            GestureState.ENTRY -> {
                if (isFlungAwayFromEdge(endX = x) || previousXTranslation > params.staticTriggerThreshold) {
                    updateArrowState(GestureState.FLUNG)
                } else {
                    updateArrowState(GestureState.CANCELLED)
                }
            }
            GestureState.INACTIVE -> {
                if (isFlungAwayFromEdge(endX = x)) {
                    mainHandler.postDelayed(
                        flungAfterInactiveRunnable,
                        MIN_DURATION_INACTIVE_BEFORE_FLUNG_ANIMATION,
                    )
                } else {
                    updateArrowState(GestureState.CANCELLED)
                }
            }
            GestureState.ACTIVE -> {
                if (
                    previousState == GestureState.ENTRY &&
                    elapsedTimeSinceEntry < MIN_DURATION_ENTRY_TO_ACTIVE_CONSIDERED_AS_FLING
                ) {
                    updateArrowState(GestureState.FLUNG)
                } else if (
                    previousState == GestureState.INACTIVE &&
                    elapsedTimeSinceInactive < MIN_DURATION_INACTIVE_TO_ACTIVE_CONSIDERED_AS_FLING
                ) {
                    mainHandler.postDelayed(
                        committedAfterInactiveRunnable,
                        MIN_DURATION_ACTIVE_AFTER_INACTIVE_ANIMATION,
                    )
                } else {
                    updateArrowState(GestureState.COMMITTED)
                }
            }
            GestureState.GONE,
            GestureState.FLUNG,
            GestureState.COMMITTED,
            GestureState.CANCELLED,
            -> updateArrowState(GestureState.CANCELLED)
        }
        velocityTracker = null
    }

    fun onCancel() {
        updateArrowState(GestureState.GONE)
        velocityTracker = null
    }

    fun hideImmediately() {
        cancelAllPendingAnimations()
        panel.cancelAnimations()
        updateArrowState(GestureState.GONE, force = true)
        velocityTracker = null
    }

    private fun obtainTrackerEvent(action: Int, x: Float, y: Float): MotionEvent? {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(downTime, now, action, x, y, 0)
    }

    private fun updateConfiguration() {
        params.update(panel.resources)
        panel.updateArrowPaint(params.arrowThickness)
        minFlingDistance = viewConfiguration.scaledTouchSlop * 3
        entryShowSlopPx = ENTRY_SHOW_SLOP_DP * panel.resources.displayMetrics.density
        applyActivationThresholdOverride()
        layoutParams.width = params.panelWidth
        layoutParams.height = params.panelHeight
        applyLayout()
    }

    private fun applyActivationThresholdOverride() {
        val override = activationThresholdOverridePx ?: return
        params.setStaticTriggerThreshold(max(override, entryShowSlopPx + 1f))
    }

    private fun updateArrowDirection() {
        panel.arrowsPointLeft =
            panel.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    private fun cancelAllPendingAnimations() {
        cancelFailsafe()
        panel.cancelAnimations()
        mainHandler.removeCallbacks(onEndSetCommittedStateListener.runnable)
        mainHandler.removeCallbacks(onEndSetGoneStateListener.runnable)
        mainHandler.removeCallbacks(onAlphaEndSetGoneStateListener.runnable)
        mainHandler.removeCallbacks(flungAfterInactiveRunnable)
        mainHandler.removeCallbacks(committedAfterInactiveRunnable)
        mainHandler.removeCallbacks(popOnFlingRunnable)
    }

    /**
     * Show ENTRY after a tiny move so the 16dp activation window is actually visible.
     * Activation / springs still use AOSP [EdgePanelParams.staticTriggerThreshold].
     */
    private fun dragSlopExceeded(curX: Float, curY: Float): Boolean {
        if (hasPassedDragSlop) return true
        if (abs(curX - fingerDownX) > entryShowSlopPx || abs(curY - startY) > entryShowSlopPx) {
            updateArrowState(GestureState.ENTRY)
            applyLayout()
            hasPassedDragSlop = true
        }
        return hasPassedDragSlop
    }

    private fun updateArrowStateOnMove(yTranslation: Float, xTranslation: Float) {
        val isWithinYActivationThreshold = xTranslation * 2 >= yTranslation
        val isPastStaticThreshold = xTranslation >= params.staticTriggerThreshold
        when (currentState) {
            GestureState.ENTRY -> {
                if (
                    isPastThresholdToActive(
                        isPastThreshold = isPastStaticThreshold,
                        dynamicDelay = entryToActiveDelayCalculation,
                    )
                ) {
                    updateArrowState(GestureState.ACTIVE)
                }
            }
            GestureState.INACTIVE -> {
                val isPastDynamicReactivationThreshold =
                    totalTouchDeltaInactive >= params.reactivationTriggerThreshold
                if (
                    isPastThresholdToActive(
                        isPastThreshold = isPastStaticThreshold &&
                            isPastDynamicReactivationThreshold &&
                            isWithinYActivationThreshold,
                        delay = MIN_DURATION_INACTIVE_BEFORE_ACTIVE_ANIMATION,
                    )
                ) {
                    updateArrowState(GestureState.ACTIVE)
                }
            }
            GestureState.ACTIVE -> {
                val isPastDynamicDeactivationThreshold =
                    totalTouchDeltaActive <= params.deactivationTriggerThreshold
                val isMinDurationElapsed =
                    elapsedTimeSinceEntry > MIN_DURATION_ACTIVE_BEFORE_INACTIVE_ANIMATION
                val isPastAllThresholds =
                    !isWithinYActivationThreshold || isPastDynamicDeactivationThreshold
                if (isPastAllThresholds && isMinDurationElapsed) {
                    updateArrowState(GestureState.INACTIVE)
                }
            }
            else -> {}
        }
    }

    private fun handleMoveEvent(x: Float, y: Float) {
        val yOffset = y - startY
        val yTranslation = abs(yOffset)
        val xTranslation = max(0f, if (panel.isLeftPanel) x - startX else startX - x)
        val xDelta = xTranslation - previousXTranslation
        previousXTranslation = xTranslation

        if (abs(xDelta) > 0) {
            val isInSameDirection = sign(xDelta) == sign(totalTouchDeltaActive)
            val isInDynamicRange = totalTouchDeltaActive in params.dynamicTriggerThresholdRange
            val isTouchInContinuousDirection = isInSameDirection || isInDynamicRange
            if (isTouchInContinuousDirection) {
                totalTouchDeltaActive += xDelta
            } else {
                totalTouchDeltaActive = xDelta
                touchDeltaStartX = x
            }
            val minimumDelta = -viewConfiguration.scaledTouchSlop.toFloat()
            totalTouchDeltaInactive =
                totalTouchDeltaInactive.plus(xDelta).coerceAtLeast(minimumDelta)
        }

        updateArrowStateOnMove(yTranslation, xTranslation)
        val gestureProgress = when (currentState) {
            GestureState.ACTIVE -> fullScreenProgress(xTranslation)
            GestureState.ENTRY -> staticThresholdProgress(xTranslation)
            GestureState.INACTIVE -> reactivationThresholdProgress(totalTouchDeltaInactive)
            else -> null
        }
        gestureProgress?.let {
            when (currentState) {
                GestureState.ACTIVE -> stretchActiveBackIndicator(gestureProgress)
                GestureState.ENTRY -> stretchEntryBackIndicator(gestureProgress)
                GestureState.INACTIVE -> stretchInactiveBackIndicator(gestureProgress)
                else -> {}
            }
        }
        setArrowStrokeAlpha(gestureProgress)
        setVerticalTranslation(yOffset)
    }

    private fun setArrowStrokeAlpha(gestureProgress: Float?) {
        val strokeAlphaProgress = when (currentState) {
            GestureState.ENTRY, GestureState.INACTIVE -> gestureProgress
            GestureState.ACTIVE, GestureState.FLUNG, GestureState.COMMITTED -> 1f
            GestureState.CANCELLED, GestureState.GONE -> 0f
        }
        val indicator = when (currentState) {
            GestureState.ENTRY -> params.entryIndicator
            GestureState.INACTIVE -> params.preThresholdIndicator
            GestureState.ACTIVE -> params.activeIndicator
            else -> params.preThresholdIndicator
        }
        strokeAlphaProgress?.let { progress ->
            indicator.arrowDimens.alphaSpring
                ?.get(progress)
                ?.takeIf { it.isNewState }
                ?.let { panel.popArrowAlpha(0f, it.value) }
        }
    }

    private fun setVerticalTranslation(yOffset: Float) {
        val yTranslation = abs(yOffset)
        val maxYOffset = (panel.height - params.entryIndicator.backgroundDimens.height) / 2f
        val rubberbandAmount = 15f
        val yProgress = saturate(yTranslation / (maxYOffset * rubberbandAmount))
        val yPosition =
            params.verticalTranslationInterpolator.getInterpolation(yProgress) *
                maxYOffset *
                sign(yOffset)
        panel.animateVertically(yPosition)
    }

    private fun fullScreenProgress(xTranslation: Float): Float {
        val progress = (xTranslation - previousXTranslationOnActiveOffset) / fullyStretchedThreshold
        return saturate(progress)
    }

    private fun staticThresholdProgress(xTranslation: Float): Float =
        saturate(xTranslation / params.staticTriggerThreshold)

    private fun reactivationThresholdProgress(totalTouchDelta: Float): Float =
        saturate(totalTouchDelta / params.reactivationTriggerThreshold)

    private fun stretchActiveBackIndicator(progress: Float) {
        panel.setStretch(
            horizontalTranslationStretchAmount =
                params.horizontalTranslationInterpolator.getInterpolation(progress),
            arrowStretchAmount = params.arrowAngleInterpolator.getInterpolation(progress),
            backgroundWidthStretchAmount =
                params.activeWidthInterpolator.getInterpolation(progress),
            backgroundAlphaStretchAmount = 1f,
            backgroundHeightStretchAmount = 1f,
            arrowAlphaStretchAmount = 1f,
            edgeCornerStretchAmount = 1f,
            farCornerStretchAmount = 1f,
            fullyStretchedDimens = params.fullyStretchedIndicator,
        )
    }

    private fun stretchEntryBackIndicator(progress: Float) {
        panel.setStretch(
            horizontalTranslationStretchAmount = 0f,
            arrowStretchAmount = params.arrowAngleInterpolator.getInterpolation(progress),
            backgroundWidthStretchAmount = params.entryWidthInterpolator.getInterpolation(progress),
            backgroundHeightStretchAmount = params.heightInterpolator.getInterpolation(progress),
            backgroundAlphaStretchAmount = 1f,
            arrowAlphaStretchAmount =
                params.entryIndicator.arrowDimens.alphaInterpolator?.get(progress)?.value ?: 0f,
            edgeCornerStretchAmount = params.edgeCornerInterpolator.getInterpolation(progress),
            farCornerStretchAmount = params.farCornerInterpolator.getInterpolation(progress),
            fullyStretchedDimens = params.preThresholdIndicator,
        )
    }

    private var previousPreThresholdWidthInterpolator: android.view.animation.Interpolator? = null

    private fun preThresholdWidthStretchAmount(progress: Float): Float {
        val interpolator = run {
            val isPastSlop = totalTouchDeltaInactive > viewConfiguration.scaledTouchSlop
            if (isPastSlop) {
                if (totalTouchDeltaInactive > 0) {
                    params.entryWidthInterpolator
                } else {
                    params.entryWidthTowardsEdgeInterpolator
                }
            } else {
                previousPreThresholdWidthInterpolator ?: params.entryWidthInterpolator
            }.also { previousPreThresholdWidthInterpolator = it }
        }
        return interpolator.getInterpolation(progress).coerceAtLeast(0f)
    }

    private fun stretchInactiveBackIndicator(progress: Float) {
        panel.setStretch(
            horizontalTranslationStretchAmount = 0f,
            arrowStretchAmount = params.arrowAngleInterpolator.getInterpolation(progress),
            backgroundWidthStretchAmount = preThresholdWidthStretchAmount(progress),
            backgroundHeightStretchAmount = params.heightInterpolator.getInterpolation(progress),
            backgroundAlphaStretchAmount = 1f,
            arrowAlphaStretchAmount =
                params.preThresholdIndicator.arrowDimens.alphaInterpolator?.get(progress)?.value
                    ?: 0f,
            edgeCornerStretchAmount = params.edgeCornerInterpolator.getInterpolation(progress),
            farCornerStretchAmount = params.farCornerInterpolator.getInterpolation(progress),
            fullyStretchedDimens = params.preThresholdIndicator,
        )
    }

    private fun isFlungAwayFromEdge(endX: Float, startX: Float = touchDeltaStartX): Boolean {
        val flingDistance = if (panel.isLeftPanel) endX - startX else startX - endX
        val flingVelocity = velocityTracker?.run {
            computeCurrentVelocity(PX_PER_SEC)
            xVelocity.takeIf { panel.isLeftPanel } ?: (xVelocity * -1)
        } ?: 0f
        val isPastFlingVelocityThreshold =
            flingVelocity > viewConfiguration.scaledMinimumFlingVelocity
        return flingDistance > minFlingDistance && isPastFlingVelocityThreshold
    }

    private fun isPastThresholdToActive(
        isPastThreshold: Boolean,
        delay: Float? = null,
        dynamicDelay: () -> Float = { delay ?: 0F },
    ): Boolean {
        val resetValue = 0L
        val isPastThresholdForFirstTime = pastThresholdWhileEntryOrInactiveTime == resetValue
        if (!isPastThreshold) {
            pastThresholdWhileEntryOrInactiveTime = resetValue
            return false
        }
        if (isPastThresholdForFirstTime) {
            pastThresholdWhileEntryOrInactiveTime = SystemClock.uptimeMillis()
            entryToActiveDelay = dynamicDelay()
            if (entryToActiveDelay <= 0f) return true
        }
        val timePastThreshold = SystemClock.uptimeMillis() - pastThresholdWhileEntryOrInactiveTime
        return timePastThreshold > entryToActiveDelay
    }

    private fun playWithBackgroundWidthAnimation(
        onEnd: DelayedOnAnimationEndListener,
        delay: Long = 0L,
    ) {
        if (delay == 0L) {
            updateRestingArrowDimens()
            if (!panel.addAnimationEndListener(panel.backgroundWidth, onEnd)) {
                scheduleFailsafe()
            }
        } else {
            mainHandler.postDelayed({ playWithBackgroundWidthAnimation(onEnd, delay = 0L) }, delay)
        }
    }

    private fun updateYStartPosition(touchY: Float) {
        var yPosition = touchY - params.fingerOffset - extraFingerOffsetPx
        yPosition = max(yPosition, params.minArrowYPosition.toFloat())
        yPosition -= layoutParams.height / 2.0f
        layoutParams.topMargin = yPosition.toInt().coerceIn(0, displaySize.y)
        applyLayout()
    }

    private fun applyLayout() {
        if (panel.parent != null) {
            panel.layoutParams = layoutParams
            // 顶/底边：旋转后条带沿屏幕 Y 的落点由 rotation 中心决定，这里用 translationY 把它贴边；
            // 局部 Y（topMargin，跟着手指沿边走）不参与修正。
            val bandCenterY = edgeBandCenterY()
            panel.translationY = if (bandCenterY == null) {
                0f
            } else {
                bandCenterY - (layoutParams.topMargin + layoutParams.height / 2f)
            }
        }
    }

    private fun updateRestingArrowDimens() {
        when (currentState) {
            GestureState.GONE, GestureState.ENTRY -> {
                panel.setSpring(
                    arrowLength = params.entryIndicator.arrowDimens.lengthSpring,
                    arrowHeight = params.entryIndicator.arrowDimens.heightSpring,
                    scale = params.entryIndicator.scaleSpring,
                    verticalTranslation = params.entryIndicator.verticalTranslationSpring,
                    horizontalTranslation = params.entryIndicator.horizontalTranslationSpring,
                    backgroundAlpha = params.entryIndicator.backgroundDimens.alphaSpring,
                    backgroundWidth = params.entryIndicator.backgroundDimens.widthSpring,
                    backgroundHeight = params.entryIndicator.backgroundDimens.heightSpring,
                    backgroundEdgeCornerRadius =
                        params.entryIndicator.backgroundDimens.edgeCornerRadiusSpring,
                    backgroundFarCornerRadius =
                        params.entryIndicator.backgroundDimens.farCornerRadiusSpring,
                )
            }
            GestureState.INACTIVE -> {
                panel.setSpring(
                    arrowLength = params.preThresholdIndicator.arrowDimens.lengthSpring,
                    arrowHeight = params.preThresholdIndicator.arrowDimens.heightSpring,
                    horizontalTranslation = params.preThresholdIndicator.horizontalTranslationSpring,
                    scale = params.preThresholdIndicator.scaleSpring,
                    backgroundWidth = params.preThresholdIndicator.backgroundDimens.widthSpring,
                    backgroundHeight = params.preThresholdIndicator.backgroundDimens.heightSpring,
                    backgroundEdgeCornerRadius =
                        params.preThresholdIndicator.backgroundDimens.edgeCornerRadiusSpring,
                    backgroundFarCornerRadius =
                        params.preThresholdIndicator.backgroundDimens.farCornerRadiusSpring,
                )
            }
            GestureState.ACTIVE -> {
                panel.setSpring(
                    arrowLength = params.activeIndicator.arrowDimens.lengthSpring,
                    arrowHeight = params.activeIndicator.arrowDimens.heightSpring,
                    scale = params.activeIndicator.scaleSpring,
                    horizontalTranslation = params.activeIndicator.horizontalTranslationSpring,
                    backgroundWidth = params.activeIndicator.backgroundDimens.widthSpring,
                    backgroundHeight = params.activeIndicator.backgroundDimens.heightSpring,
                    backgroundEdgeCornerRadius =
                        params.activeIndicator.backgroundDimens.edgeCornerRadiusSpring,
                    backgroundFarCornerRadius =
                        params.activeIndicator.backgroundDimens.farCornerRadiusSpring,
                )
            }
            GestureState.FLUNG -> {
                panel.setSpring(
                    arrowLength = params.flungIndicator.arrowDimens.lengthSpring,
                    arrowHeight = params.flungIndicator.arrowDimens.heightSpring,
                    backgroundWidth = params.flungIndicator.backgroundDimens.widthSpring,
                    backgroundHeight = params.flungIndicator.backgroundDimens.heightSpring,
                    backgroundEdgeCornerRadius =
                        params.flungIndicator.backgroundDimens.edgeCornerRadiusSpring,
                    backgroundFarCornerRadius =
                        params.flungIndicator.backgroundDimens.farCornerRadiusSpring,
                )
            }
            GestureState.COMMITTED -> {
                panel.setSpring(
                    arrowLength = params.committedIndicator.arrowDimens.lengthSpring,
                    arrowHeight = params.committedIndicator.arrowDimens.heightSpring,
                    scale = params.committedIndicator.scaleSpring,
                    backgroundAlpha = params.committedIndicator.backgroundDimens.alphaSpring,
                    backgroundWidth = params.committedIndicator.backgroundDimens.widthSpring,
                    backgroundHeight = params.committedIndicator.backgroundDimens.heightSpring,
                    backgroundEdgeCornerRadius =
                        params.committedIndicator.backgroundDimens.edgeCornerRadiusSpring,
                    backgroundFarCornerRadius =
                        params.committedIndicator.backgroundDimens.farCornerRadiusSpring,
                )
            }
            GestureState.CANCELLED -> {
                panel.setSpring(
                    backgroundAlpha = params.cancelledIndicator.backgroundDimens.alphaSpring,
                )
            }
        }

        panel.setRestingDimens(
            animate = !(currentState == GestureState.FLUNG || currentState == GestureState.COMMITTED),
            restingParams = EdgePanelParams.BackIndicatorDimens(
                scale = when (currentState) {
                    GestureState.ACTIVE, GestureState.FLUNG -> params.activeIndicator.scale
                    GestureState.COMMITTED -> params.committedIndicator.scale
                    else -> params.preThresholdIndicator.scale
                },
                scalePivotX = when (currentState) {
                    GestureState.GONE,
                    GestureState.ENTRY,
                    GestureState.INACTIVE,
                    GestureState.CANCELLED,
                    -> params.preThresholdIndicator.scalePivotX
                    GestureState.ACTIVE -> params.activeIndicator.scalePivotX
                    GestureState.FLUNG, GestureState.COMMITTED -> params.committedIndicator.scalePivotX
                },
                horizontalTranslation = when (currentState) {
                    GestureState.GONE -> params.activeIndicator.backgroundDimens.width?.times(-1)
                    GestureState.ENTRY, GestureState.INACTIVE ->
                        params.entryIndicator.horizontalTranslation
                    GestureState.FLUNG, GestureState.ACTIVE ->
                        params.activeIndicator.horizontalTranslation
                    GestureState.CANCELLED -> params.cancelledIndicator.horizontalTranslation
                    else -> null
                },
                arrowDimens = when (currentState) {
                    GestureState.GONE, GestureState.ENTRY, GestureState.INACTIVE ->
                        params.entryIndicator.arrowDimens
                    GestureState.ACTIVE -> params.activeIndicator.arrowDimens
                    GestureState.FLUNG -> params.flungIndicator.arrowDimens
                    GestureState.COMMITTED -> params.committedIndicator.arrowDimens
                    GestureState.CANCELLED -> params.cancelledIndicator.arrowDimens
                },
                backgroundDimens = when (currentState) {
                    GestureState.GONE, GestureState.ENTRY, GestureState.INACTIVE ->
                        params.entryIndicator.backgroundDimens
                    GestureState.ACTIVE, GestureState.FLUNG -> params.activeIndicator.backgroundDimens
                    GestureState.COMMITTED -> params.committedIndicator.backgroundDimens
                    GestureState.CANCELLED -> params.cancelledIndicator.backgroundDimens
                },
            ),
        )
    }

    private fun updateArrowState(newState: GestureState, force: Boolean = false) {
        if (!force && currentState == newState) return
        val wasShowing = isShowing
        previousState = currentState
        currentState = newState
        when (currentState) {
            GestureState.GONE -> {
                updateRestingArrowDimens()
                panel.visibility = View.GONE
                if (wasShowing && !suppressSettled) onSettled?.invoke()
            }
            GestureState.ENTRY -> {
                panel.visibility = View.VISIBLE
                updateRestingArrowDimens()
                gestureEntryTime = SystemClock.uptimeMillis()
            }
            GestureState.ACTIVE -> {
                previousXTranslationOnActiveOffset = previousXTranslation
                updateRestingArrowDimens()
                if (previousState != GestureState.ENTRY || activationThresholdOverridePx == null) {
                    performHaptic(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                }
                val popVelocity = if (previousState == GestureState.INACTIVE) {
                    POP_ON_INACTIVE_TO_ACTIVE_VELOCITY
                } else {
                    POP_ON_ENTRY_TO_ACTIVE_VELOCITY
                }
                panel.popOffEdge(popVelocity)
            }
            GestureState.INACTIVE -> {
                gestureInactiveTime = SystemClock.uptimeMillis()
                totalTouchDeltaInactive = params.deactivationTriggerThreshold
                panel.popOffEdge(POP_ON_INACTIVE_VELOCITY)
                performHaptic(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE)
                updateRestingArrowDimens()
            }
            GestureState.FLUNG -> {
                if (previousState != GestureState.ACTIVE) {
                    performHaptic(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
                }
                mainHandler.postDelayed(popOnFlingRunnable, POP_ON_FLING_DELAY)
                mainHandler.postDelayed(
                    onEndSetCommittedStateListener.runnable,
                    MIN_DURATION_FLING_ANIMATION,
                )
                updateRestingArrowDimens()
            }
            GestureState.COMMITTED -> {
                if (previousState == GestureState.FLUNG) {
                    updateRestingArrowDimens()
                    mainHandler.postDelayed(
                        onEndSetGoneStateListener.runnable,
                        MIN_DURATION_COMMITTED_AFTER_FLING_ANIMATION,
                    )
                } else {
                    panel.popScale(POP_ON_COMMITTED_VELOCITY)
                    mainHandler.postDelayed(
                        onAlphaEndSetGoneStateListener.runnable,
                        MIN_DURATION_COMMITTED_ANIMATION,
                    )
                }
            }
            GestureState.CANCELLED -> {
                val delay = max(0, MIN_DURATION_CANCELLED_ANIMATION - elapsedTimeSinceEntry)
                playWithBackgroundWidthAnimation(onEndSetGoneStateListener, delay)
                val springForceOnCancelled =
                    params.cancelledIndicator.arrowDimens.alphaSpring?.get(0f)?.value
                panel.popArrowAlpha(0f, springForceOnCancelled)
            }
        }
    }

    // 触感常量：GESTURE_THRESHOLD_ACTIVATE 是 API 34 新增的编译期常量（内联，运行时不访问字段）。
    @android.annotation.SuppressLint("NewApi")
    private fun performHaptic(constant: Int) {
        panel.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    private fun convertVelocityToAnimationFactor(
        valueOnFastVelocity: Float,
        valueOnSlowVelocity: Float,
        fastVelocityBound: Float = 1f,
        slowVelocityBound: Float = 0.5f,
    ): Float {
        val factor = velocityTracker?.run {
            computeCurrentVelocity(PX_PER_MS)
            smoothStep(slowVelocityBound, fastVelocityBound, abs(xVelocity))
        } ?: valueOnFastVelocity
        return lerp(valueOnFastVelocity, valueOnSlowVelocity, 1 - factor)
    }

    private fun scheduleFailsafe() {
        cancelFailsafe()
        mainHandler.postDelayed(failsafeRunnable, FAILSAFE_DELAY_MS)
    }

    private fun cancelFailsafe() {
        mainHandler.removeCallbacks(failsafeRunnable)
    }

    private fun onFailsafe() {
        updateArrowState(GestureState.GONE, force = true)
    }

    private companion object {
        const val FAILSAFE_DELAY_MS = 350L
        const val PX_PER_SEC = 1000
        const val PX_PER_MS = 1
        const val MIN_DURATION_ACTIVE_BEFORE_INACTIVE_ANIMATION = 300L
        const val MIN_DURATION_ACTIVE_AFTER_INACTIVE_ANIMATION = 130L
        const val MIN_DURATION_CANCELLED_ANIMATION = 200L
        const val MIN_DURATION_COMMITTED_ANIMATION = 80L
        const val MIN_DURATION_COMMITTED_AFTER_FLING_ANIMATION = 120L
        const val MIN_DURATION_INACTIVE_BEFORE_FLUNG_ANIMATION = 50L
        const val MIN_DURATION_INACTIVE_BEFORE_ACTIVE_ANIMATION = 160F
        const val MIN_DURATION_ENTRY_BEFORE_ACTIVE_ANIMATION = 10F
        const val MAX_DURATION_ENTRY_BEFORE_ACTIVE_ANIMATION = 100F
        const val MIN_DURATION_FLING_ANIMATION = 160L
        const val MIN_DURATION_ENTRY_TO_ACTIVE_CONSIDERED_AS_FLING = 100L
        const val MIN_DURATION_INACTIVE_TO_ACTIVE_CONSIDERED_AS_FLING = 400L
        const val POP_ON_FLING_DELAY = 60L
        const val POP_ON_FLING_VELOCITY = 2f
        const val POP_ON_COMMITTED_VELOCITY = 3f
        const val POP_ON_ENTRY_TO_ACTIVE_VELOCITY = 4.5f
        const val POP_ON_INACTIVE_TO_ACTIVE_VELOCITY = 4.7f
        const val POP_ON_INACTIVE_VELOCITY = -1.5f
        const val GESTURE_ANIMATION_Z_INDEX = 40f
        const val ENTRY_SHOW_SLOP_DP = 2f
    }
}

private fun saturate(value: Float): Float = value.coerceIn(0f, 1f)

private fun lerp(start: Float, stop: Float, amount: Float): Float =
    start + (stop - start) * amount

/** Matches `android.util.MathUtils.smoothStep` (Hermite). */
private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    val t = saturate((x - edge0) / (edge1 - edge0))
    return t * t * (3f - 2f * t)
}
