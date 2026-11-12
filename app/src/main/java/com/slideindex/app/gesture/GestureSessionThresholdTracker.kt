package com.slideindex.app.gesture

import com.slideindex.app.BuildConfig

internal class GestureSessionThresholdTracker(
    private val pathRecognizer: SwipePathRecognizer,
    private val callbacks: GestureSession.Callbacks,
    private val cancelLongPressCheck: () -> Unit,
    private val isTriggerConfigured: (GestureTriggerType) -> Boolean = { true }
) {
    private var wasAboveShortThreshold = false
    private var wasAboveLongThreshold = false
    private var wasAboveCompoundShortThreshold = false
    private var wasAboveCompoundLongThreshold = false
    private var longPressHapticFired = false
    private var returnHapticFired = false
    private var hoverHapticFired = false
    /** 本段峰值距离：只增不减，避免回退时"测量口径变化"导致距离回升而重复提示。 */
    private var peakPrimaryDistance = 0f
    private var peakCompoundDistance = 0f

    fun trackDistanceHaptics(rawX: Float, rawY: Float) {
        // 撤销（回到起手位置）＝本段清零：峰值与"已提示"状态一起复位，重新滑过才会再响。
        if (pathRecognizer.consumeSegmentReset()) {
            peakPrimaryDistance = 0f
            peakCompoundDistance = 0f
            wasAboveShortThreshold = false
            wasAboveLongThreshold = false
            wasAboveCompoundShortThreshold = false
            wasAboveCompoundLongThreshold = false
        }
        // 只有真的转向才切到第二段分档：仅"离锚点多远"会把普通内滑也当成第二段，
        // 门槛从短滑距离掉到 TURN_SLOP 后立刻多补一声震动（只配折返时还会丢掉长距那声）。
        // 同时必须"该组合槽位真的配置了动作"，否则会给一个不会被执行的组合手势响震动（幽灵震动）。
        // 且必须遵守与识别器一致的优先级：折返有效时折返优先，不能切到组合分档（否则震动与图标不一致）。
        val returnActive = isReturnSwipeHapticEnabled() &&
            pathRecognizer.isReturnSwipeActive(rawX, rawY)
        val secondSegment = pathRecognizer.compoundSecondSegmentTrigger(rawX, rawY)
        if (!returnActive && secondSegment != null && isCompoundCornerHapticEnabled(secondSegment)) {
            trackCompoundSecondSegmentHaptics(rawX, rawY)
        } else {
            trackPrimarySegmentHaptics(rawX, rawY)
        }
        trackReturnHaptics(rawX, rawY)
        trackHoverHaptics()
    }

    private fun trackPrimarySegmentHaptics(rawX: Float, rawY: Float) {
        val distance = pathRecognizer.swipeDistance(rawX, rawY)
        peakPrimaryDistance = maxOf(peakPrimaryDistance, distance)
        val aboveShort = peakPrimaryDistance >= pathRecognizer.shortThresholdPx()
        val aboveLong = peakPrimaryDistance >= pathRecognizer.longThresholdPx()
        if (aboveShort && !wasAboveShortThreshold) {
            cancelLongPressCheck()
            pathRecognizer.disqualifyLongPress()
            logHaptic("primary-short", distance, pathRecognizer.shortThresholdPx(), rawX, rawY)
            callbacks.hapticGestureStart()
        }
        if (aboveLong && !wasAboveLongThreshold) {
            logHaptic("primary-long", distance, pathRecognizer.longThresholdPx(), rawX, rawY)
            callbacks.hapticLongThreshold()
        }
        wasAboveShortThreshold = aboveShort
        wasAboveLongThreshold = aboveLong
    }

    private fun trackCompoundSecondSegmentHaptics(rawX: Float, rawY: Float) {
        val distance = pathRecognizer.compoundSecondSegmentDistance(rawX, rawY)
        peakCompoundDistance = maxOf(peakCompoundDistance, distance)
        val aboveShort = peakCompoundDistance >= pathRecognizer.compoundSecondSegmentTurnThresholdPx()
        val aboveLong = peakCompoundDistance >= pathRecognizer.longThresholdPx()
        if (aboveShort && !wasAboveCompoundShortThreshold) {
            logHaptic("compound-short", distance, pathRecognizer.compoundSecondSegmentTurnThresholdPx(), rawX, rawY)
            callbacks.hapticCompoundTurn()
        }
        if (aboveLong && !wasAboveCompoundLongThreshold) {
            logHaptic("compound-long", distance, pathRecognizer.longThresholdPx(), rawX, rawY)
            callbacks.hapticLongThreshold()
        }
        wasAboveCompoundShortThreshold = aboveShort
        wasAboveCompoundLongThreshold = aboveLong
    }

    private fun trackReturnHaptics(rawX: Float, rawY: Float) {
        if (isReturnSwipeHapticEnabled() &&
            pathRecognizer.isReturnSwipeActive(rawX, rawY)
        ) {
            if (!returnHapticFired) {
                returnHapticFired = true
                logHaptic("return", 0f, 0f, rawX, rawY)
                callbacks.hapticReturnSwipe()
            }
        } else {
            returnHapticFired = false
        }
    }

    /** 临时诊断日志：定位"同一次滑动响两次"来自哪一个来源（仅 debug 生效）。 */
    private fun logHaptic(source: String, distance: Float, threshold: Float, rawX: Float, rawY: Float) {
        if (!BuildConfig.DEBUG) return
        android.util.Log.i(
            "GestureHaptic",
            "src=$source distance=${distance.toInt()} threshold=${threshold.toInt()} " +
                "x=${rawX.toInt()} y=${rawY.toInt()} t=${System.currentTimeMillis()}",
        )
    }

    private fun trackHoverHaptics() {
        if (pathRecognizer.consumeHoverJustSatisfied()) {
            val hoverTrigger = pathRecognizer.activeHoverTrigger()
            if (hoverTrigger == null || isTriggerConfigured(hoverTrigger)) {
                if (!hoverHapticFired) {
                    hoverHapticFired = true
                    callbacks.hapticHover()
                }
            }
        }
    }

    /** 与识别器的 isCornerConfigured 同口径：短/长任一档配置了动作才算该组合可用。 */
    private fun isCompoundCornerHapticEnabled(trigger: GestureTriggerType): Boolean {
        val counterpart = when (trigger) {
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
        return isTriggerConfigured(trigger) || (counterpart?.let { isTriggerConfigured(it) } == true)
    }

    /**
     * 折返震动开关：只认「当前首段方向对应的那个折返槽位」是否配了动作。
     *
     * 早先是三个折返槽位「任一配了动作」就整体打开，于是把"向内折返"设成「无」之后，
     * 只要"沿边折返"还配着动作，向内折返时照样补一声震动；而识别器那边会用
     * `isTriggerConfigured` 过滤掉未配置的折返（[SwipePathRecognizer.directionTrigger]），
     * 松手执行的仍是基础内滑 —— 表现就是"白响一声"的幽灵震动。
     * 改成与识别器同一口径后，未配置的折返不再有震动。
     */
    private fun isReturnSwipeHapticEnabled(): Boolean {
        val trigger = pathRecognizer.activeReturnSwipeTrigger() ?: return false
        return isTriggerConfigured(trigger)
    }

    fun maybeHapticLongPress(rawX: Float, rawY: Float) {
        if (longPressHapticFired) return
        pathRecognizer.refreshLongPress(rawX, rawY)
        if (pathRecognizer.isLongPressArmed()) {
            longPressHapticFired = true
            callbacks.hapticLongPress()
        }
    }

    fun reset() {
        // 峰值必须跟"已提示"标记一起归零。它记的是"本次手势滑到过的最远处"，
        // 跨手势留着会让下一次手指刚按下去（当次距离远低于阈值）就同时越过短/长两档，
        // 在同一帧连发两声震动（实测：上次滑到 501，下次只滑 91px 也双响）。
        peakPrimaryDistance = 0f
        peakCompoundDistance = 0f
        wasAboveShortThreshold = false
        wasAboveLongThreshold = false
        wasAboveCompoundShortThreshold = false
        wasAboveCompoundLongThreshold = false
        longPressHapticFired = false
        returnHapticFired = false
        hoverHapticFired = false
    }
}
