package com.slideindex.app.gesture

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import kotlin.math.abs
import kotlin.math.hypot

data class SwipeClassification(
    val trigger: GestureTriggerType,
    val inwardDelta: Float,
    val verticalDelta: Float,
)

class SwipePathRecognizer(
    private val side: PanelSide,
    private val density: Float,
) {
    data class ClassifyOptions(
        val tapSlopMultiplier: Float = 1f,
        val tapMaxMs: Long = TAP_MAX_MS,
        val preferSingleTap: Boolean = false,
        val isTriggerConfigured: ((GestureTriggerType) -> Boolean)? = null,
    ) {
        companion object {
            val DEFAULT = ClassifyOptions()
            val LENIENT_SINGLE_TAP = ClassifyOptions(
                tapSlopMultiplier = TAP_LENIENT_SLOP_DP / TAP_SLOP_DP,
                tapMaxMs = TAP_LENIENT_MAX_MS,
                preferSingleTap = true,
            )
        }
    }

    private var startRawX = 0f
    private var startRawY = 0f
    private var startTime = 0L
    private var tracking = false
    private var longPressTriggered = false
    private var movedBeyondLongPressSlop = false
    private var peakInward = 0f
    private var peakSwipeDistance = 0f
    private var peakDy = 0f
    private var shortDistanceDp = DEFAULT_SHORT_DISTANCE_DP
    private var longDistanceDp = DEFAULT_LONG_DISTANCE_DP
    private var gestureAngle = GestureAngle.DEFAULT_LEFT
    private var stripBounds = RectF()
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var inwardReachedShortThreshold = false
    /** 首段家族与其越过短距阈值时的锚点（内滑 / 沿边上 / 沿边下）。 */
    private var firstSegmentDirection: SwipeDirection? = null
    private var firstAnchorX = 0f
    private var firstAnchorY = 0f
    private var peakAlongUpProgress = 0f
    private var peakAlongDownProgress = 0f
    private var slotHoverDirection: SwipeDirection? = null
    private var slotHoverAnchorX = 0f
    private var slotHoverAnchorY = 0f
    private var slotHoverSatisfiedAnchorX = 0f
    private var slotHoverSatisfiedAnchorY = 0f
    private var slotHoverHoldStartMs = 0L
    private var slotHoverTracking = false
    private var slotHoverSatisfied = false
    private var slotHoverCancelled = false
    private var slotHoverJustSatisfied = false
    private var compoundModeArmed = false
    /** 本次手势内组合已被解除（越过长距阈值），不再重新就绪。 */
    private var compoundDisarmed = false
    /** 本段清零信号（撤销/回到起手位置时置位），供上层重置提示用的峰值与去重状态。 */
    private var segmentResetPending = false
    private var hoverDurationMs = DEFAULT_HOVER_DURATION_MS
    private var lCornerHoverGateRequired = false
    private var returnSwipeHoverGateRequired = false
    private var alongCornerGateRequired = false
    private var alongReturnGateRequired = false
    private var slotHoverConfigured: (SwipeDirection) -> Boolean = { false }

    fun applyDistances(shortDp: Float, longDp: Float) {
        shortDistanceDp = shortDp.coerceIn(0f, MAX_DISTANCE_DP)
        val longMin = if (shortDistanceDp <= 0f) {
            MIN_DISTANCE_GAP_DP
        } else {
            shortDistanceDp + MIN_DISTANCE_GAP_DP
        }
        longDistanceDp = longDp.coerceIn(longMin, MAX_LONG_DISTANCE_DP)
    }

    fun applyAngles(angles: GestureAngles) {
        gestureAngle = angles.forSide(side)
    }

    /** [durationMs] 为「短滑后悬停」的静止时长；0 表示滑过短距即视为悬停成立。 */
    fun applyHoverSettings(durationMs: Long) {
        hoverDurationMs = durationMs.coerceIn(HOVER_DURATION_MIN_MS, HOVER_DURATION_MAX_MS)
    }

    fun applyCompoundGestureGate(options: ClassifyOptions) {
        lCornerHoverGateRequired = isLCornerConfigured(options)
        returnSwipeHoverGateRequired = isReturnSwipeConfigured(options)
        alongCornerGateRequired = isAlongCornerConfigured(options)
        alongReturnGateRequired = isAlongReturnConfigured(options)
        val filter = options.isTriggerConfigured
        slotHoverConfigured = fun(direction: SwipeDirection): Boolean {
            val hoverTrigger = direction.toHoverTrigger() ?: return false
            return filter == null || filter(hoverTrigger)
        }
    }

    fun onTouchDown(rawX: Float, rawY: Float, bounds: RectF) {
        stripBounds = RectF(bounds)
        startRawX = rawX
        startRawY = rawY
        lastRawX = rawX
        lastRawY = rawY
        startTime = System.currentTimeMillis()
        tracking = true
        longPressTriggered = false
        movedBeyondLongPressSlop = false
        peakInward = 0f
        peakSwipeDistance = 0f
        peakDy = 0f
        resetHoverState()
    }

    private fun resetHoverState() {
        inwardReachedShortThreshold = false
        firstSegmentDirection = null
        firstAnchorX = 0f
        firstAnchorY = 0f
        peakAlongUpProgress = 0f
        peakAlongDownProgress = 0f
        resetSlotHoverState()
        compoundModeArmed = false
        compoundDisarmed = false
        segmentResetPending = false
        lCornerHoverGateRequired = false
        returnSwipeHoverGateRequired = false
        alongCornerGateRequired = false
        alongReturnGateRequired = false
        slotHoverConfigured = { false }
    }

    private fun resetSlotHoverState() {
        slotHoverDirection = null
        slotHoverAnchorX = 0f
        slotHoverAnchorY = 0f
        slotHoverSatisfiedAnchorX = 0f
        slotHoverSatisfiedAnchorY = 0f
        slotHoverHoldStartMs = 0L
        slotHoverTracking = false
        slotHoverSatisfied = false
        slotHoverCancelled = false
        slotHoverJustSatisfied = false
    }

    private fun cancelSlotHover() {
        slotHoverCancelled = true
        slotHoverTracking = false
        slotHoverSatisfied = false
    }

    /** 越过长距阈值后本次手势不再判定组合，避免长滑被第二段抢走。 */
    private fun disarmCompoundMode() {
        if (compoundDisarmed) return
        compoundModeArmed = false
        compoundDisarmed = true
    }

    private fun passedLongInwardThreshold(rawX: Float, rawY: Float): Boolean {
        val directionDistance = measureDistanceForDirection(rawX, rawY, SwipeDirection.IN)
        return directionDistance >= longDistanceDp * density
    }

    /** 沿边首段在指定方向上的进展（px，恒为正）。 */
    private fun alongProgressPx(rawX: Float, rawY: Float, direction: SwipeDirection?): Float {
        if (direction != SwipeDirection.UP && direction != SwipeDirection.DOWN) return 0f
        val along = alongDelta(rawX - startRawX, rawY - startRawY)
        return if (direction == SwipeDirection.UP) -along else along
    }

    /** 首段进展越过长距阈值即解除组合（长滑优先），按首段家族各自计算。 */
    private fun firstSegmentPassedLong(rawX: Float, rawY: Float): Boolean {
        val longPx = longDistanceDp * density
        return when (firstSegmentDirection) {
            SwipeDirection.IN -> passedLongInwardThreshold(rawX, rawY)
            SwipeDirection.UP, SwipeDirection.DOWN ->
                alongProgressPx(rawX, rawY, firstSegmentDirection) >= longPx
            else -> false
        }
    }

    /** 起手位置距屏幕边缘的内距（px）。 */
    private fun startInwardPx(): Float =
        SwipePathGeometry.measureTriggerDistance(
            side = side,
            direction = SwipeDirection.IN,
            startX = startRawX,
            startY = startRawY,
            fingerX = startRawX,
            fingerY = startRawY,
            stripBounds = stripBounds,
        )

    /** 折返撤销区：手指回到起手位置附近（含滑到屏幕边缘）时不再判折返。 */
    private fun returnCancelInwardPx(): Float = startInwardPx() + RETURN_CANCEL_INWARD_DP * density

    fun gestureStartRawX(): Float = startRawX

    fun gestureStartRawY(): Float = startRawY

    fun gestureStartUptimeMs(): Long = if (tracking) startTime else 0L

    fun gestureElapsedMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (!tracking) return 0L
        return (nowMs - startTime).coerceAtLeast(0L)
    }

    fun seedExternalAnchor(rawX: Float, rawY: Float) {
        startRawX = rawX
        startRawY = rawY
        lastRawX = rawX
        lastRawY = rawY
        startTime = System.currentTimeMillis()
        tracking = false
        longPressTriggered = false
        movedBeyondLongPressSlop = false
        peakInward = 0f
        peakSwipeDistance = 0f
        peakDy = 0f
        resetHoverState()
    }

    fun gestureDistance(rawX: Float, rawY: Float): Float {
        if (!tracking) return 0f
        return hypot(rawX - startRawX, rawY - startRawY)
    }

    fun isWithinLenientTapSlop(rawX: Float, rawY: Float): Boolean =
        gestureDistance(rawX, rawY) < TAP_LENIENT_SLOP_DP * density

    fun isLongPressArmed(): Boolean = tracking && longPressTriggered

    fun onTouchMove(rawX: Float, rawY: Float) {
        if (!tracking) return
        lastRawX = rawX
        lastRawY = rawY
        recordMovement(rawX, rawY)
        refreshLongPress(rawX, rawY)
    }

    fun refreshLongPress(rawX: Float, rawY: Float) {
        if (!tracking || longPressTriggered || movedBeyondLongPressSlop) return
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed >= LONG_PRESS_MS) {
            val dist = hypot(rawX - startRawX, rawY - startRawY)
            if (dist < TAP_SLOP_DP * density) {
                longPressTriggered = true
            }
        }
    }

    private fun recordMovement(rawX: Float, rawY: Float) {
        if (!tracking) return
        val dx = rawX - startRawX
        val dy = rawY - startRawY
        val inward = inwardDelta(dx, dy)
        val resolvedDir = resolveDirectionAt(rawX, rawY)
        val shortPx = shortDistanceDp * density

        // 回到起手位置附近 = 撤销：本次手势重新开始（峰值清零、短距达成标记复位、折返清零），
        // 之后必须重新滑过短距阈值才算重新开始，避免"旧峰值算出的回缩量"让折返立刻又成立。
        if (isBackAtGestureStart(rawX, rawY)) {
            resetSegment()
        }

        if (resolvedDir == SwipeDirection.IN && inward >= shortPx) {
            inwardReachedShortThreshold = true
        }
        if (firstSegmentDirection == null) {
            when {
                resolvedDir == SwipeDirection.IN && inward >= shortPx -> {
                    firstSegmentDirection = SwipeDirection.IN
                    firstAnchorX = rawX
                    firstAnchorY = rawY
                }
                (resolvedDir == SwipeDirection.UP || resolvedDir == SwipeDirection.DOWN) &&
                    measureDistanceForDirection(rawX, rawY, resolvedDir) >= shortPx -> {
                    firstSegmentDirection = resolvedDir
                    firstAnchorX = rawX
                    firstAnchorY = rawY
                }
            }
        }
        val alongFromStart = alongDelta(dx, dy)
        if (alongFromStart < 0f) {
            peakAlongUpProgress = maxOf(peakAlongUpProgress, -alongFromStart)
        } else {
            peakAlongDownProgress = maxOf(peakAlongDownProgress, alongFromStart)
        }
        val swipeDist = resolvedDir?.let { direction ->
            measureDistanceForDirection(rawX, rawY, direction)
        } ?: hypot(inward.toDouble(), dy.toDouble()).toFloat()
        if (swipeDist > peakSwipeDistance) {
            peakSwipeDistance = swipeDist
            peakInward = inward
            peakDy = dy
        } else {
            peakInward = maxOf(peakInward, inward)
        }
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (!movedBeyondLongPressSlop && dist >= TAP_SLOP_DP * density) {
            movedBeyondLongPressSlop = true
        }
        updateSlotHoverState(rawX, rawY, resolvedDir, swipeDist)
        updateCompoundGateState(rawX, rawY)
    }

    /** 手指是否回到起手位置附近（撤销区）。 */
    private fun isBackAtGestureStart(rawX: Float, rawY: Float): Boolean {
        val cancelPx = RETURN_CANCEL_INWARD_DP * density
        val direction = firstSegmentDirection
        return if (direction == SwipeDirection.UP || direction == SwipeDirection.DOWN) {
            alongProgressPx(rawX, rawY, direction) <= cancelPx
        } else {
            // ⚠️ 这里只能比**相对位移**。[inwardDelta] 吃的是 (raw - startRaw)，
            // 再加 [startInwardPx]（起手点距屏幕边缘的绝对深度）会把撤销区从 12dp 撑开到
            // 「起手深度 + 12dp」：顶部触钮的触摸窗口贴边但有厚度（实测 181px，按下点距顶边
            // 就有 ~150px），于是整段前 183px 的内滑都被反复判成撤销、每帧清零本段状态，
            // 上层震动器随之每帧重播一次「越过短滑阈值」的震动（真机表现：滑动时一直震）。
            // 起手点本来就在屏幕边缘时 [startInwardPx] 恒为 0，去掉它不改变左右方向的行为。
            if (inwardDelta(rawX - startRawX, rawY - startRawY) > cancelPx) return false
            // 撤销区（相对起手点）和短滑阈值（相对屏幕边缘，见 measureTriggerDistance）用的是
            // 两套原点。起手点本身已经在阈值之外时（厚触钮里按在 y≈150，离顶边就已经 150px），
            // 这两段区间会重叠：同一帧既判定"撤销、清零本段"、又判定"越过短滑阈值"，上层
            // [GestureSessionThresholdTracker] 每清零一次就重播一次 GESTURE_START —— 真机表现
            // 就是顶部/底部触钮滑动时一直震。真正的撤销必然先退回阈值以内，所以"仍在阈值之外"
            // 时不算撤销。（左右触钮起手点贴边，距离 ≤ 12dp < 阈值，行为不变。）
            measureDistanceForDirection(rawX, rawY, SwipeDirection.IN) < shortThresholdPx()
        }
    }

    /** 撤销：把本次手势"清零重来"（保留峰值用于点击判定的部分，不动 tap 去抖）。 */
    private fun resetSegment() {
        peakInward = inwardDelta(lastRawX - startRawX, lastRawY - startRawY).coerceAtLeast(0f)
        peakAlongUpProgress = alongProgressPx(lastRawX, lastRawY, SwipeDirection.UP).coerceAtLeast(0f)
        peakAlongDownProgress = alongProgressPx(lastRawX, lastRawY, SwipeDirection.DOWN).coerceAtLeast(0f)
        inwardReachedShortThreshold = false
        firstSegmentDirection = null
        firstAnchorX = 0f
        firstAnchorY = 0f
        compoundModeArmed = false
        compoundDisarmed = false
        segmentResetPending = true
    }

    /** 消费"本段清零"信号（撤销/回到起手位置）。 */
    fun consumeSegmentReset(): Boolean {
        if (!segmentResetPending) return false
        segmentResetPending = false
        return true
    }

    private fun updateSlotHoverState(
        rawX: Float,
        rawY: Float,
        resolvedDir: SwipeDirection?,
        swipeDist: Float,
    ) {
        val shortPx = shortDistanceDp * density
        val longPx = longDistanceDp * density

        if (resolvedDir != null && swipeDist >= shortPx && slotHoverDirection == null) {
            if (slotHoverConfigured(resolvedDir)) {
                slotHoverDirection = resolvedDir
                slotHoverAnchorX = rawX
                slotHoverAnchorY = rawY
                slotHoverHoldStartMs = 0L
                slotHoverTracking = true
            }
        }

        if (slotHoverCancelled || !slotHoverTracking || slotHoverDirection == null) return

        val directionDistance = measureDistanceForDirection(rawX, rawY, slotHoverDirection)
        if (directionDistance >= longPx) {
            cancelSlotHover()
            return
        }

        if (slotHoverSatisfied) return

        // 悬停锚点固定不动：手指离开锚点达到静止容差即视为"仍在移动"，
        // 锚点前移并重新计时。这样任何持续滑动都会不断重启计时，只有真的停住
        //（整段时长内累计位移 < HOVER_SLOP）才会攒够时长，避免"边滑边攒计时"误判悬停。
        val anchorDist = hypot(
            (rawX - slotHoverAnchorX).toDouble(),
            (rawY - slotHoverAnchorY).toDouble(),
        ).toFloat()
        val now = System.currentTimeMillis()
        if (anchorDist >= HOVER_SLOP_DP * density) {
            slotHoverAnchorX = rawX
            slotHoverAnchorY = rawY
            slotHoverHoldStartMs = now
            return
        }

        if (slotHoverHoldStartMs == 0L) {
            slotHoverHoldStartMs = now
        }
        if (now - slotHoverHoldStartMs < hoverDurationMs) return

        slotHoverSatisfied = true
        slotHoverJustSatisfied = true
        slotHoverSatisfiedAnchorX = rawX
        slotHoverSatisfiedAnchorY = rawY
    }

    /**
     * 组合手势不再要求"短滑后悬停"：第一段越过短距阈值即就绪（内滑或沿边均可），
     * 由第二段自己证明是转向（内滑家族转向沿边 / 沿边家族转向内滑）或折返。
     * 未配置对应组合槽位时始终不就绪，方向判定照旧走直滑。
     */
    private fun updateCompoundGateState(rawX: Float, rawY: Float) {
        if (firstSegmentPassedLong(rawX, rawY)) {
            disarmCompoundMode()
            return
        }
        if (compoundModeArmed || compoundDisarmed) return
        val gateRequired = when (firstSegmentDirection) {
            SwipeDirection.IN -> lCornerHoverGateRequired || returnSwipeHoverGateRequired
            SwipeDirection.UP, SwipeDirection.DOWN ->
                alongCornerGateRequired || alongReturnGateRequired
            else -> false
        }
        if (!gateRequired) return
        compoundModeArmed = true
    }

    fun consumeHoverJustSatisfied(): Boolean {
        if (!slotHoverJustSatisfied) return false
        slotHoverJustSatisfied = false
        return true
    }

    fun activeHoverTrigger(): GestureTriggerType? =
        slotHoverDirection?.toHoverTrigger()

    fun isHoverSatisfied(): Boolean = slotHoverSatisfied

    fun hoverHoldRemainingMs(): Long? {
        if (slotHoverCancelled || !slotHoverTracking || slotHoverSatisfied) return null
        if (slotHoverDirection == null || slotHoverHoldStartMs == 0L) return null
        val elapsed = System.currentTimeMillis() - slotHoverHoldStartMs
        return (hoverDurationMs - elapsed).coerceAtLeast(0L)
    }

    /**
     * 组合第二段当前解析出的转向触发（走够 [TURN_SLOP_DP] 且方向族切换）。
     * 注意：这里只保证几何成立，**不代表该槽位已配置动作**——调用方（震动分档）必须自己过滤，
     * 否则会出现"给没配置的组合手势响提示震动、但图标与松手结果都不是它"的幽灵震动。
     */
    fun compoundSecondSegmentTrigger(rawX: Float, rawY: Float): GestureTriggerType? =
        resolveCompoundSecondSegment(rawX, rawY)

    /** 组合已就绪时的第二段判定；锚点取第一段越过短距阈值的位置。 */
    private fun resolveCompoundSecondSegment(rawX: Float, rawY: Float): GestureTriggerType? {
        if (!compoundModeArmed) return null
        val firstDirection = firstSegmentDirection ?: return null
        return when (firstDirection) {
            SwipeDirection.IN -> SwipePathGeometry.resolveCornerSwipeTrigger(
                side = side,
                stripBounds = stripBounds,
                inwardReachedThreshold = true,
                currentInward = inwardDelta(rawX - startRawX, rawY - startRawY),
                shortThresholdPx = shortDistanceDp * density,
                longThresholdPx = longDistanceDp * density,
                gestureStartX = startRawX,
                gestureStartY = startRawY,
                anchorX = firstAnchorX,
                anchorY = firstAnchorY,
                fingerX = rawX,
                fingerY = rawY,
                turnThresholdPx = TURN_SLOP_DP * density,
                angle = gestureAngle,
                longFromSecondSegmentOnly = true,
            )
            SwipeDirection.UP, SwipeDirection.DOWN -> SwipePathGeometry.resolveAlongToInwardTrigger(
                side = side,
                firstDirection = firstDirection,
                anchorX = firstAnchorX,
                anchorY = firstAnchorY,
                fingerX = rawX,
                fingerY = rawY,
                turnThresholdPx = TURN_SLOP_DP * density,
                longThresholdPx = longDistanceDp * density,
            )
            else -> null
        }
    }

    private fun movedFromSlotHoverAnchor(rawX: Float, rawY: Float): Boolean {
        return hypot(
            (rawX - slotHoverSatisfiedAnchorX).toDouble(),
            (rawY - slotHoverSatisfiedAnchorY).toDouble(),
        ) >= HOVER_SLOP_DP * density
    }

    fun swipeDistance(rawX: Float, rawY: Float): Float {
        if (!tracking) return 0f
        return measureDistanceForDirection(
            fingerX = rawX,
            fingerY = rawY,
            direction = resolveDirectionAt(rawX, rawY),
        )
    }

    fun effectiveSwipeDistance(rawX: Float, rawY: Float): Float =
        maxOf(swipeDistance(rawX, rawY), peakSwipeDistance)

    fun currentSwipeDistancePx(): Float = swipeDistance(lastRawX, lastRawY)

    fun currentInwardPx(): Float {
        if (!tracking) return 0f
        val dx = lastRawX - startRawX
        val dy = lastRawY - startRawY
        return inwardDelta(dx, dy).coerceAtLeast(0f)
    }

    fun currentEdgeOffsetPx(): Float {
        if (!tracking) return 0f
        return when (side) {
            PanelSide.LEFT, PanelSide.RIGHT -> lastRawY - startRawY
            PanelSide.BOTTOM, PanelSide.TOP -> lastRawX - startRawX
        }
    }

    fun movementPxFromStart(): Float {
        if (!tracking) return 0f
        return hypot(
            (lastRawX - startRawX).toDouble(),
            (lastRawY - startRawY).toDouble(),
        ).toFloat()
    }

    /**
     * 手势提示动画开始跟手的位移门槛（约 4dp）。
     *
     * ⚠️ 故意**不**复用 [TAP_SLOP_DP]（12dp）：那个是"单击 vs 滑动"的判定阈值，必须够宽，
     * 否则人手点一下的漂移就被判成滑动、单击/长按全废。动画只是视觉效果，可以更早出现，
     * 所以单独用一个更小的常量。真正决定"会不会出动画"的还有方向分量（向内 / 沿边 / 滑动距离），
     * 纯抖动不会触发。
     */
    fun gestureHintStartThresholdPx(): Float = HINT_START_SLOP_DP * density

    /** 与 classifyOnUp 一致：低于此位移仍可能判为单击。 */
    fun tapDisqualifyMovementPx(options: ClassifyOptions = ClassifyOptions.DEFAULT): Float =
        if (options.preferSingleTap) {
            TAP_LENIENT_SLOP_DP * density
        } else {
            TAP_SLOP_DP * density * options.tapSlopMultiplier
        }

    fun lastRawX(): Float = lastRawX

    fun lastRawY(): Float = lastRawY

    fun shortThresholdPx(): Float = shortDistanceDp * density

    fun stripOriginX(): Float = when (side) {
        PanelSide.LEFT -> stripBounds.left
        PanelSide.RIGHT -> stripBounds.right
        PanelSide.BOTTOM, PanelSide.TOP -> startRawX
    }

    fun longThresholdPx(): Float = longDistanceDp * density

    fun disqualifyLongPress() {
        movedBeyondLongPressSlop = true
    }

    fun longPressEligible(): Boolean = tracking && !movedBeyondLongPressSlop

    fun classifyPartial(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions = ClassifyOptions.DEFAULT,
    ): SwipeClassification? {
        if (!tracking) return null
        return computeClassification(rawX, rawY, options, partial = true)
    }

    fun classifyOnUp(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions = ClassifyOptions.DEFAULT,
    ): SwipeClassification? {
        if (!tracking) return null
        val classification = computeClassification(rawX, rawY, options, partial = false)
        reset()
        return classification
    }

    fun hasMetThreshold(
        trigger: GestureTriggerType,
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions = ClassifyOptions.DEFAULT,
    ): Boolean {
        if (!tracking) return false
        if (trigger.isHoverSwipe) return isHoverReady(rawX, rawY)
        if (shouldDeferBaseSwipeForHover(options)) {
            val baseTrigger = slotHoverDirection?.toBaseShortTrigger()
            if (trigger == baseTrigger && !slotHoverSatisfied) return false
        }
        if (trigger.isCornerSwipe && compoundModeArmed) {
            val distance = compoundSecondSegmentDistance(rawX, rawY)
            return if (trigger.isLongDistance) {
                distance >= longDistanceDp * density
            } else {
                distance >= shortDistanceDp * density
            }
        }
        val distance = swipeDistance(rawX, rawY)
        return when {
            trigger.isLongPress -> longPressTriggered
            trigger.isSingleTap -> false
            trigger.isLongDistance -> distance >= longDistanceDp * density
            else -> distance >= shortDistanceDp * density
        }
    }

    fun isVerticalDominant(rawX: Float, rawY: Float): Boolean {
        if (!tracking) return false
        val dx = rawX - startRawX
        val dy = rawY - startRawY
        val inward = inwardDelta(dx, dy)
        if (hypot(inward.toDouble(), dy.toDouble()) < INDEX_ENTER_DP * density) return false
        return abs(dy) > abs(inward) * VERTICAL_DOMINANCE_RATIO
    }

    fun verticalDirection(rawY: Float): GestureTriggerType? {
        if (!tracking) return null
        val dy = rawY - startRawY
        if (abs(dy) < INDEX_ENTER_DP * density) return null
        return if (dy < 0) GestureTriggerType.SHORT_SWIPE_UP else GestureTriggerType.SHORT_SWIPE_DOWN
    }

    fun reset() {
        tracking = false
        longPressTriggered = false
        movedBeyondLongPressSlop = false
        peakInward = 0f
        peakSwipeDistance = 0f
        peakDy = 0f
        resetHoverState()
    }

    private fun isHoverReady(rawX: Float, rawY: Float): Boolean {
        if (!slotHoverSatisfied || slotHoverCancelled || slotHoverDirection == null) return false
        return !movedFromSlotHoverAnchor(rawX, rawY)
    }

    private fun computeClassification(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions,
        partial: Boolean,
    ): SwipeClassification? {
        applyCompoundGestureGate(options)
        recordMovement(rawX, rawY)
        refreshLongPress(rawX, rawY)
        val dx = rawX - startRawX
        val dy = rawY - startRawY
        val inward = inwardDelta(dx, dy)
        val direction = resolveDirectionAt(rawX, rawY)
        val distance = measureDistanceForDirection(rawX, rawY, direction)
        val elapsed = System.currentTimeMillis() - startTime
        val tapSlop = TAP_SLOP_DP * density * options.tapSlopMultiplier
        val tapDisqualifyPx = if (options.preferSingleTap) {
            TAP_LENIENT_SLOP_DP * density
        } else {
            TAP_SLOP_DP * density
        }
        val movementPx = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val movedBeyondTap = peakSwipeDistance >= tapDisqualifyPx || movementPx >= tapDisqualifyPx
        val trigger = when {
            longPressTriggered && distance < tapSlop * 2 -> {
                if (distance >= longDistanceDp * density) GestureTriggerType.LONG_LONG_PRESS
                else GestureTriggerType.SHORT_LONG_PRESS
            }
            !partial && options.preferSingleTap && !longPressTriggered &&
                !movedBeyondTap && movementPx < tapSlop && elapsed < options.tapMaxMs -> {
                GestureTriggerType.SHORT_SINGLE_TAP
            }
            !partial && !movedBeyondTap && distance < TAP_SLOP_DP * density -> {
                if (elapsed < TAP_MAX_MS) GestureTriggerType.SHORT_SINGLE_TAP
                else null
            }
            partial && options.preferSingleTap && distance < tapSlop -> null
            partial && distance < shortDistanceDp * density && !longPressTriggered -> {
                if (isReturnSwipeActive(rawX, rawY, options)) {
                    directionTrigger(rawX, rawY, distance, options, partial)
                } else {
                    null
                }
            }
            else -> directionTrigger(rawX, rawY, distance, options, partial)
        }
        return trigger?.let { SwipeClassification(it, inward, dy) }
    }

    private fun inwardDelta(dx: Float, dy: Float = 0f): Float =
        SwipePathGeometry.inwardDelta(dx, dy, side)

    private fun alongDelta(dx: Float, dy: Float): Float =
        SwipePathGeometry.alongDelta(dx, dy, side)

    fun isReturnSwipeActive(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions = ClassifyOptions.DEFAULT,
    ): Boolean {
        if (!tracking) return false
        return isReturnGeometryActive(rawX, rawY, RETURN_SLOP_DP * density)
    }

    /** 用给定回缩门槛做一次折返几何判定（含首段家族、闸门与守卫等全部前提）。 */
    private fun isReturnGeometryActive(rawX: Float, rawY: Float, returnThresholdPx: Float): Boolean {
        val direction = firstSegmentDirection ?: return false
        if (direction == SwipeDirection.UP || direction == SwipeDirection.DOWN) {
            if (!compoundModeArmed && !alongReturnGateRequired) return false
            val peak = if (direction == SwipeDirection.UP) peakAlongUpProgress else peakAlongDownProgress
            return SwipePathGeometry.resolveAlongReturnTrigger(
                side = side,
                firstDirection = direction,
                startX = startRawX,
                startY = startRawY,
                peakProgress = peak,
                fingerX = rawX,
                fingerY = rawY,
                returnThresholdPx = returnThresholdPx,
                cancelProgressPx = RETURN_CANCEL_INWARD_DP * density,
                turnThresholdPx = TURN_SLOP_DP * density,
            ) != null
        }
        if (direction != SwipeDirection.IN) return false
        if (returnSwipeHoverGateRequired && !compoundModeArmed) return false
        return SwipePathGeometry.resolveReturnSwipeTrigger(
            side = side,
            inwardReachedThreshold = inwardReachedShortThreshold,
            peakInward = peakInward,
            currentInward = inwardDelta(rawX - startRawX, rawY - startRawY),
            shortThresholdPx = shortDistanceDp * density,
            startX = startRawX,
            startY = startRawY,
            fingerX = rawX,
            fingerY = rawY,
            returnThresholdPx = returnThresholdPx,
            cancelInwardPx = returnCancelInwardPx(),
        ) != null
    }

    /**
     * 当前首段方向对应的折返触发类型；首段方向还没确定时返回 null。
     * 供震动等"必须与识别同口径"的判断使用：识别器只在对应槽位配了动作时才认折返
     * （[directionTrigger] 里的 `options.isTriggerConfigured` 过滤），震动也应按同一个槽位判断。
     */
    fun activeReturnSwipeTrigger(): GestureTriggerType? = returnSwipeTriggerType()

    /** 当前首段家族对应的折返触发类型。 */
    private fun returnSwipeTriggerType(): GestureTriggerType? = when (firstSegmentDirection) {
        SwipeDirection.UP -> GestureTriggerType.SHORT_SWIPE_UP_AND_BACK
        SwipeDirection.DOWN -> GestureTriggerType.SHORT_SWIPE_DOWN_AND_BACK
        SwipeDirection.IN -> GestureTriggerType.SHORT_SWIPE_IN_AND_BACK
        else -> null
    }

    private fun directionTrigger(
        rawX: Float,
        rawY: Float,
        distance: Float,
        options: ClassifyOptions,
        partial: Boolean,
    ): GestureTriggerType? {
        // 折返：与震动共用同一个粘滞状态，保证"响了就是它、图标与松手也一定是它"。
        if (isReturnSwipeActive(rawX, rawY, options)) {
            val returnTrigger = returnSwipeTriggerType()
            if (returnTrigger != null) {
                val filter = options.isTriggerConfigured
                if (filter == null || filter(returnTrigger)) return returnTrigger
            }
        }

        resolveCompoundSecondSegment(rawX, rawY)?.let { compoundCorner ->
            if (isCornerConfigured(compoundCorner, options)) return compoundCorner
        }

        resolveHoverTrigger(rawX, rawY, options, partial)?.let { return it }

        if (partial && shouldDeferBaseSwipeForHover(options)) {
            return null
        }

        return SwipePathGeometry.classifySwipeTrigger(
            side = side,
            stripBounds = stripBounds,
            startX = startRawX,
            startY = startRawY,
            fingerX = rawX,
            fingerY = rawY,
            shortThresholdPx = shortDistanceDp * density,
            longThresholdPx = longDistanceDp * density,
            angle = gestureAngle,
        )
    }

    private fun isLCornerConfigured(options: ClassifyOptions): Boolean {
        val filter = options.isTriggerConfigured ?: return true
        return filter(GestureTriggerType.SHORT_SWIPE_IN_UP) ||
            filter(GestureTriggerType.LONG_SWIPE_IN_UP) ||
            filter(GestureTriggerType.SHORT_SWIPE_IN_DOWN) ||
            filter(GestureTriggerType.LONG_SWIPE_IN_DOWN)
    }

    private fun isReturnSwipeConfigured(options: ClassifyOptions): Boolean {
        val filter = options.isTriggerConfigured ?: return true
        return filter(GestureTriggerType.SHORT_SWIPE_IN_AND_BACK)
    }

    private fun isAlongCornerConfigured(options: ClassifyOptions): Boolean {
        val filter = options.isTriggerConfigured ?: return true
        return filter(GestureTriggerType.SHORT_SWIPE_UP_IN) ||
            filter(GestureTriggerType.LONG_SWIPE_UP_IN) ||
            filter(GestureTriggerType.SHORT_SWIPE_DOWN_IN) ||
            filter(GestureTriggerType.LONG_SWIPE_DOWN_IN)
    }

    private fun isAlongReturnConfigured(options: ClassifyOptions): Boolean {
        val filter = options.isTriggerConfigured ?: return true
        return filter(GestureTriggerType.SHORT_SWIPE_UP_AND_BACK) ||
            filter(GestureTriggerType.SHORT_SWIPE_DOWN_AND_BACK)
    }

    fun compoundSecondSegmentTurnThresholdPx(): Float = TURN_SLOP_DP * density

    fun compoundSecondSegmentDistance(rawX: Float, rawY: Float): Float {
        if (!compoundModeArmed) return 0f
        if (firstSegmentDirection == SwipeDirection.UP || firstSegmentDirection == SwipeDirection.DOWN) {
            // 沿边首段的第二段是向内侧滑，直接量内距。
            return inwardDelta(rawX - firstAnchorX, rawY - firstAnchorY).coerceAtLeast(0f)
        }
        val direction = SwipePathGeometry.resolveSwipeDirection(
            side = side,
            stripBounds = stripBounds,
            startX = firstAnchorX,
            startY = firstAnchorY,
            fingerX = rawX,
            fingerY = rawY,
            angle = gestureAngle,
        ) ?: return hypot(
            (rawX - firstAnchorX).toDouble(),
            (rawY - firstAnchorY).toDouble(),
        ).toFloat()
        return SwipePathGeometry.measureTriggerDistance(
            side = side,
            direction = direction,
            startX = firstAnchorX,
            startY = firstAnchorY,
            fingerX = rawX,
            fingerY = rawY,
            stripBounds = stripBounds,
            anchorRelativeInward = true,
        )
    }

    private fun isCornerConfigured(
        corner: GestureTriggerType,
        options: ClassifyOptions,
    ): Boolean {
        val filter = options.isTriggerConfigured ?: return true
        val counterpart = when (corner) {
            GestureTriggerType.SHORT_SWIPE_IN_UP -> GestureTriggerType.LONG_SWIPE_IN_UP
            GestureTriggerType.LONG_SWIPE_IN_UP -> GestureTriggerType.SHORT_SWIPE_IN_UP
            GestureTriggerType.SHORT_SWIPE_IN_DOWN -> GestureTriggerType.LONG_SWIPE_IN_DOWN
            GestureTriggerType.LONG_SWIPE_IN_DOWN -> GestureTriggerType.SHORT_SWIPE_IN_DOWN
            GestureTriggerType.SHORT_SWIPE_UP_IN -> GestureTriggerType.LONG_SWIPE_UP_IN
            GestureTriggerType.LONG_SWIPE_UP_IN -> GestureTriggerType.SHORT_SWIPE_UP_IN
            GestureTriggerType.SHORT_SWIPE_DOWN_IN -> GestureTriggerType.LONG_SWIPE_DOWN_IN
            GestureTriggerType.LONG_SWIPE_DOWN_IN -> GestureTriggerType.SHORT_SWIPE_DOWN_IN
            else -> null
        }
        return filter(corner) || (counterpart?.let { filter(it) } ?: false)
    }

    private fun isHoverSlotConfigured(options: ClassifyOptions): Boolean {
        val hoverDirection = slotHoverDirection ?: return false
        val hoverTrigger = hoverDirection.toHoverTrigger() ?: return false
        val filter = options.isTriggerConfigured ?: return true
        return filter(hoverTrigger)
    }

    private fun isSlotHoverTrackingActive(): Boolean =
        slotHoverTracking && !slotHoverCancelled && slotHoverDirection != null

    /** 悬停槽位计时期间先压住基础短滑，避免手指还没停稳就把内滑动作派发出去。 */
    private fun shouldDeferBaseSwipeForHover(options: ClassifyOptions): Boolean =
        isSlotHoverTrackingActive() && isHoverSlotConfigured(options)

    private fun resolveHoverTrigger(
        rawX: Float,
        rawY: Float,
        options: ClassifyOptions,
        partial: Boolean,
    ): GestureTriggerType? {
        if (!isHoverReady(rawX, rawY)) return null
        val hoverTrigger = slotHoverDirection?.toHoverTrigger() ?: return null
        val filter = options.isTriggerConfigured
        if (filter != null && !filter(hoverTrigger)) return null
        return hoverTrigger
    }

    fun currentSwipeDirection(): SwipeDirection? {
        if (!tracking) return null
        return resolveDirectionAt(lastRawX, lastRawY)
    }

    private fun resolveDirectionAt(fingerX: Float, fingerY: Float): SwipeDirection? =
        SwipePathGeometry.resolveSwipeDirection(
            side = side,
            stripBounds = stripBounds,
            startX = startRawX,
            startY = startRawY,
            fingerX = fingerX,
            fingerY = fingerY,
            angle = gestureAngle,
        )

    private fun measureDistanceForDirection(
        fingerX: Float,
        fingerY: Float,
        direction: SwipeDirection?,
    ): Float {
        val resolved = direction ?: return hypot(
            inwardDelta(fingerX - startRawX, fingerY - startRawY).toDouble(),
            0.0,
        ).toFloat()
        return SwipePathGeometry.measureTriggerDistance(
            side = side,
            direction = resolved,
            startX = startRawX,
            startY = startRawY,
            fingerX = fingerX,
            fingerY = fingerY,
            stripBounds = stripBounds,
        )
    }

    private fun directionVector(inward: Float, dy: Float): Pair<Float, Float> {
        if (peakInward > 0f && peakSwipeDistance > 0f) {
            return peakInward to peakDy
        }
        return inward to dy
    }

    private fun resolveDirection(inward: Float, dy: Float): SwipeDirection? {
        val (inw, d) = directionVector(inward, dy)
        return SwipePathGeometry.resolveSwipeDirection(
            side = side,
            stripBounds = stripBounds,
            startX = startRawX,
            startY = startRawY,
            fingerX = startRawX + inw,
            fingerY = startRawY + d,
            angle = gestureAngle,
        )
    }

    companion object {
        private const val MIN_DISTANCE_DP = 24f
        /** 短距硬上限（UI 的软上限见 [SHORT_DISTANCE_MAX_DP]）。 */
        private const val MAX_DISTANCE_DP = 240f
        /** 长距硬上限：与短距解耦，允许把"短滑宽容带"拉到 320dp 以上（issue #52）。 */
        private const val MAX_LONG_DISTANCE_DP = 320f
        private const val MIN_DISTANCE_GAP_DP = 16f

        const val DEFAULT_SHORT_DISTANCE_DP = 60f
        const val DEFAULT_LONG_DISTANCE_DP = 120f
        const val SHORT_DISTANCE_MIN_DP = 0f
        const val SHORT_DISTANCE_MAX_DP = 160f
        const val LONG_DISTANCE_MIN_DP = MIN_DISTANCE_GAP_DP
        const val LONG_DISTANCE_MAX_DP = MAX_LONG_DISTANCE_DP
        const val LONG_PRESS_MS = 450L
        private const val TAP_SLOP_DP = 12f
        private const val TAP_LENIENT_SLOP_DP = 36f
        /** 手势提示动画的启动门槛（dp）：只影响"动画何时出现"，不参与任何手势判定。 */
        // B：提示动画启动阈值。原为 3dp，但首帧 MOVE 常被系统合并/稀疏投递，
    // 等到第一个"超过 3dp 的帧"时往往已滑出几十像素；降到 0.5dp 让第一帧就生效。
    private const val HINT_START_SLOP_DP = 0.5f
        const val TURN_SLOP_DP = 32f
        /** 折返手势的回缩门槛；无悬停时更易误触，对齐悬浮球的 28dp。 */
        const val RETURN_SLOP_DP = 28f
        /** 折返撤销区：内距回到「起手内距 + 该值」以内即视为撤销。 */
        const val RETURN_CANCEL_INWARD_DP = 12f
        /** 悬停的静止容差：偏移超过该值即取消悬停 / 松手不再算悬停。 */
        const val HOVER_SLOP_DP = 6f
        /** 悬停时长：0 = 滑过短距即视为悬停成立。 */
        const val DEFAULT_HOVER_DURATION_MS = 500L
        const val HOVER_DURATION_MIN_MS = 0L
        const val HOVER_DURATION_MAX_MS = 1000L
        private const val INDEX_ENTER_DP = 24f
        private const val TAP_MAX_MS = 220L
        private const val TAP_LENIENT_MAX_MS = 450L
        private const val VERTICAL_DOMINANCE_RATIO = 1.2f
    }
}
