package com.slideindex.app.gesture.multitouch

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/** 一次识别结果。 */
data class RecognizedMultiPointerGesture(
    val gesture: MultiPointerGesture,
    val fingerCount: Int,
)

/** 绑定键（旧设置页过渡用；新模型见 [MultiTouchGestureEntry]）。 */
data class MultiPointerBinding(
    val fingerCount: Int,
    val gesture: MultiPointerGesture,
) {
    val storageKey: String get() = "${fingerCount}:${gesture.name}"

    companion object {
        fun fromStorageKey(raw: String?): MultiPointerBinding? {
            if (raw.isNullOrBlank()) return null
            val separator = raw.indexOf(':')
            if (separator <= 0) return null
            val count = raw.substring(0, separator).toIntOrNull() ?: return null
            val gesture = MultiPointerGesture.fromName(raw.substring(separator + 1)) ?: return null
            return MultiPointerBinding(count, gesture)
        }
    }
}

/**
 * 多指手势识别器（纯逻辑，无 Android 依赖）。
 *
 * 支持 1–5 指：
 * - 几何：捏合/张开/旋转/八方向滑动（相对按下时的基线）；
 * - 点按：轻点/双击/三击（抬起时判定，轻点延迟到双击窗口结束）；
 * - 长按：按住不动超过 [longPressMs] 实时触发；
 * - 滑动：按主方向夹角分八向（22.5° 边界）。
 *
 * [onTick] 由外部定时器驱动，用于在“没有任何触摸帧”时把待定的轻点发出去。
 */
class MultiPointerGestureRecognizer(
    private val minFingerCount: Int = 1,
    private val maxFingerCount: Int = 5,
    /** 质心位移阈值（归一化，约屏幕高度 9%）。 */
    private val swipeThreshold: Float = 0.09f,
    /** 点按允许的抖动距离。 */
    private val tapSlop: Float = 0.02f,
    private val settleMs: Long = 60L,
    private val longPressMs: Long = 550L,
    private val doubleTapMs: Long = 260L,
    private val rotateScoreThreshold: Float = 0.10f,
    private val fourFingerRotateScoreThreshold: Float = 0.08f,
    /** 手势尺寸（mm）：GMD 的 `gestureSizeMm`，1–10。 */
    private val gestureSizeMm: Float = 4f,
    /** 屏幕高度（mm）：把归一化半径差换算成 mm。 */
    private val screenHeightMm: Float = 150f,
    private val rotateMaxRadiusDrift: Float = 0.32f,
    private val fourFingerRotateMaxRadiusDrift: Float = 0.40f,
) {
    private data class Baseline(
        val ids: List<Int>,
        val startPoints: Map<Int, TouchPointer>,
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val timeMs: Long,
    )

    private class RotationScore(
        val cross: Float,
        /** 半径平方和 Σ(dx² + dy²)。 */
        val radiusSquared: Float,
    )

    private var baseline: Baseline? = null
    private var latched = false
    private var downTimeMs = 0L
    private var downCenterX = 0f
    private var downCenterY = 0f
    private var maxFingers = 0
    private var moved = false

    private var pendingTapAtMs = 0L
    private var pendingTapFingers = 0
    private var tapCount = 0
    private var lastTapAtMs = 0L

    @Synchronized
    fun onFrame(pointers: List<TouchPointer>, timeMs: Long): RecognizedMultiPointerGesture? {
        if (pointers.isEmpty()) {
            val result = resolveTapOnLift(timeMs)
            resetTouch()
            return result
        }
        if (pointers.size > maxFingerCount) return null

        val ordered = pointers.sortedBy { it.id }
        val centerX = ordered.sumOf { it.x.toDouble() }.toFloat() / ordered.size
        val centerY = ordered.sumOf { it.y.toDouble() }.toFloat() / ordered.size

        if (baseline == null) {
            // 新的一次触摸序列开始：上一串轻点如果超时，先作废。
            if (tapCount > 0 && timeMs - lastTapAtMs > doubleTapMs) {
                tapCount = 0
                pendingTapAtMs = 0
            }
            downTimeMs = timeMs
            downCenterX = centerX
            downCenterY = centerY
            maxFingers = ordered.size
            moved = false
            latched = false
            baseline = buildBaseline(ordered, timeMs)
            return null
        }

        maxFingers = max(maxFingers, ordered.size)
        val dx = centerX - downCenterX
        val dy = centerY - downCenterY
        val distance = hypot(dx, dy)
        if (distance > tapSlop) moved = true
        if (latched) return null

        // 长按：按住且没怎么动。
        if (!moved && timeMs - downTimeMs >= longPressMs) {
            latched = true
            return RecognizedMultiPointerGesture(MultiPointerGesture.LONG_PRESS, maxFingers)
        }

        val ids = ordered.map { it.id }
        val current = baseline ?: return null
        if (current.ids != ids) {
            baseline = buildBaseline(ordered, timeMs)
            return null
        }
        if (timeMs - current.timeMs < settleMs) return null

        // 滑动（任意指数量）。
        if (distance >= swipeThreshold) {
            val direction = swipeDirection(dx, dy)
            if (direction != null) return latch(direction, timeMs)
        }

        // 几何手势（>=2 指）。
        if (ordered.size >= 2) {
            val radius = averageRadius(ordered, centerX, centerY)
            val radiusRatio = if (current.radius > MIN_RADIUS) radius / current.radius else 1f
            val four = ordered.size >= 4
            val rotation = rotationScore(ordered, centerX, centerY, current)
            if (rotation != null) {
                // GMD：|cross| / r²，阈值约 1（归一化后按经验校准）。
                val score = abs(rotation.cross) / rotation.radiusSquared
                val threshold = if (four) fourFingerRotateScoreThreshold else rotateScoreThreshold
                val maxDrift = if (four) fourFingerRotateMaxRadiusDrift else rotateMaxRadiusDrift
                if (score >= threshold && abs(radiusRatio - 1f) <= maxDrift) {
                    val gesture = if (rotation.cross > 0f) {
                        MultiPointerGesture.ROTATE_CLOCKWISE
                    } else {
                        MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE
                    }
                    return latch(gesture, timeMs)
                }
            }
            val startRms = rmsRadius(current.startPoints.values, current.centerX, current.centerY)
            val currentRms = rmsRadius(ordered, centerX, centerY)
            // GMD：|起始半径 - 当前半径| >= max(1, gestureSizeMm/4) mm。
            val changeThreshold = max(1f, gestureSizeMm / 4f) / screenHeightMm
            if (startRms - currentRms >= changeThreshold) {
                return latch(MultiPointerGesture.PINCH, timeMs)
            }
            if (currentRms - startRms >= changeThreshold) {
                return latch(MultiPointerGesture.SPREAD, timeMs)
            }
        }
        return null
    }

    /** 由外部定时器调用：双击窗口结束仍未等到第二次点击 → 发出「轻点」。 */
    @Synchronized
    fun onTick(timeMs: Long): RecognizedMultiPointerGesture? {
        if (tapCount == 1 && pendingTapAtMs > 0 && timeMs - pendingTapAtMs >= doubleTapMs) {
            val fingers = pendingTapFingers
            tapCount = 0
            pendingTapAtMs = 0
            return RecognizedMultiPointerGesture(MultiPointerGesture.TAP, fingers)
        }
        return null
    }

    @Synchronized
    fun reset() {
        resetTouch()
        tapCount = 0
        pendingTapAtMs = 0
        lastTapAtMs = 0
    }

    private fun resolveTapOnLift(timeMs: Long): RecognizedMultiPointerGesture? {
        if (latched || moved || maxFingers !in minFingerCount..maxFingerCount) return null
        return when {
            tapCount == 1 && timeMs - lastTapAtMs <= doubleTapMs -> {
                tapCount = 2
                pendingTapAtMs = 0
                lastTapAtMs = timeMs
                RecognizedMultiPointerGesture(MultiPointerGesture.DOUBLE_TAP, maxFingers)
            }
            tapCount == 2 && timeMs - lastTapAtMs <= doubleTapMs -> {
                tapCount = 0
                pendingTapAtMs = 0
                lastTapAtMs = timeMs
                RecognizedMultiPointerGesture(MultiPointerGesture.TRIPLE_TAP, maxFingers)
            }
            else -> {
                tapCount = 1
                pendingTapAtMs = timeMs
                pendingTapFingers = maxFingers
                lastTapAtMs = timeMs
                null
            }
        }
    }

    private fun latch(gesture: MultiPointerGesture, timeMs: Long): RecognizedMultiPointerGesture {
        latched = true
        return RecognizedMultiPointerGesture(gesture, maxFingers)
    }

    private fun resetTouch() {
        baseline = null
        latched = false
        downTimeMs = 0
        maxFingers = 0
        moved = false
    }

    private fun buildBaseline(ordered: List<TouchPointer>, timeMs: Long): Baseline {
        val centerX = ordered.sumOf { it.x.toDouble() }.toFloat() / ordered.size
        val centerY = ordered.sumOf { it.y.toDouble() }.toFloat() / ordered.size
        return Baseline(
            ids = ordered.map { it.id },
            startPoints = ordered.associateBy { it.id },
            centerX = centerX,
            centerY = centerY,
            radius = averageRadius(ordered, centerX, centerY),
            timeMs = timeMs,
        )
    }

    private fun rotationScore(
        pointers: List<TouchPointer>,
        centerX: Float,
        centerY: Float,
        baseline: Baseline,
    ): RotationScore? {
        var cross = 0f
        var radiusSquared = 0f
        for (pointer in pointers) {
            val start = baseline.startPoints[pointer.id] ?: continue
            val startX = start.x - baseline.centerX
            val startY = start.y - baseline.centerY
            val currentX = pointer.x - centerX
            val currentY = pointer.y - centerY
            cross += startX * currentY - startY * currentX
            radiusSquared += currentX * currentX + currentY * currentY
        }
        if (radiusSquared <= MIN_RADIUS) return null
        return RotationScore(cross, radiusSquared)
    }

    private fun averageRadius(pointers: List<TouchPointer>, cx: Float, cy: Float): Float {
        if (pointers.isEmpty()) return 0f
        return pointers.sumOf { hypot(it.x - cx, it.y - cy).toDouble() }.toFloat() / pointers.size
    }

    private fun rmsRadius(pointers: Collection<TouchPointer>, cx: Float, cy: Float): Float {
        if (pointers.isEmpty()) return 0f
        var sum = 0.0
        for (pointer in pointers) {
            val dx = (pointer.x - cx).toDouble()
            val dy = (pointer.y - cy).toDouble()
            sum += dx * dx + dy * dy
        }
        return sqrt(sum / pointers.size).toFloat()
    }

    private fun swipeDirection(dx: Float, dy: Float): MultiPointerGesture? {
        val absX = abs(dx)
        val absY = abs(dy)
        if (absX < 1e-6f && absY < 1e-6f) return null
        return if (absX >= absY * DIAGONAL_RATIO) {
            if (dx > 0f) MultiPointerGesture.SWIPE_RIGHT else MultiPointerGesture.SWIPE_LEFT
        } else if (absY >= absX * DIAGONAL_RATIO) {
            if (dy > 0f) MultiPointerGesture.SWIPE_DOWN else MultiPointerGesture.SWIPE_UP
        } else {
            when {
                dx > 0f && dy < 0f -> MultiPointerGesture.SWIPE_UP_RIGHT
                dx > 0f && dy > 0f -> MultiPointerGesture.SWIPE_DOWN_RIGHT
                dx < 0f && dy < 0f -> MultiPointerGesture.SWIPE_UP_LEFT
                else -> MultiPointerGesture.SWIPE_DOWN_LEFT
            }
        }
    }

    private companion object {
        const val MIN_RADIUS = 1e-4f

        /** tan(67.5°)：主方向超过这个比例算水平/垂直，否则斜向。 */
        const val DIAGONAL_RATIO = 2.414f
    }
}
