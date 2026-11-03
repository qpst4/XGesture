package com.slideindex.app.gesture.multitouch

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 多指手势类型（与指数量一起构成绑定键）。
 *
 * 旋转以**屏幕坐标系**为准（y 向下时，视觉上顺时针 = 角度增大）。
 */
enum class MultiPointerGesture {
    PINCH,
    SPREAD,
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    ROTATE_CLOCKWISE,
    ROTATE_COUNTER_CLOCKWISE,
    ;

    companion object {
        fun fromName(raw: String?): MultiPointerGesture? =
            entries.firstOrNull { it.name == raw }
    }
}

/** 归一化到屏幕坐标系的一个触点（x 已按长宽比校正，见 EvdevTouchObserver）。 */
data class TouchPointer(
    val id: Int,
    val x: Float,
    val y: Float,
)

/** 识别结果；[fingerCount] 是触发时的实际触点数（3 指 / 4 指）。 */
data class RecognizedMultiPointerGesture(
    val gesture: MultiPointerGesture,
    val fingerCount: Int,
)

/**
 * 多指手势识别器（纯逻辑，无 Android 依赖，便于单测）。
 *
 * 输入是 evdev 帧解析后的归一化触点列表；输出一次手势或 null。
 *
 * 判定思路：
 * 1. 以「触点数稳定的那一帧」为基准，记录各指起点与质心；
 * 2. 基准稳定 [settleMs] 后才判定，避免按下瞬间抖动；
 * 3. 旋转用「相对各自质心的叉积和 / 半径和」判定，质心漂移不会抵消旋转量；
 * 4. 捏合/张开用起始、当前两组 RMS 半径之差，不要求整体不动；
 * 5. 四指（及以上）不参与滑动判定，避免转动手腕时被位移抢走；
 * 6. 触发后 latch，直到所有手指抬起才重新装填。
 *
 * 阈值全部基于归一化坐标，和屏幕分辨率/密度无关。
 */
class MultiPointerGestureRecognizer(
    private val minFingerCount: Int = 3,
    /** 质心位移阈值（归一化，约等于屏幕短边 9%）。 */
    private val swipeThreshold: Float = 0.09f,
    /** 旋转判定阈值（叉积和 / 半径和）；三指基准。 */
    private val rotateScoreThreshold: Float = 0.024f,
    /** 四指旋转阈值：四指动作幅度更小，单独放宽。 */
    private val fourFingerRotateScoreThreshold: Float = 0.020f,
    /** 捏合/张开的半径变化比例（相对起始 RMS 半径）。 */
    private val radiusChangeFraction: Float = 0.18f,
    /** 捏合/张开的最小半径变化（归一化下限，避免小半径抖动触发）。 */
    private val minRadiusChange: Float = 0.012f,
    /** 基准建立后至少经过多久才判定，避免 evdev 首帧抖动。 */
    private val settleMs: Long = 60L,
) {
    private data class Baseline(
        val ids: List<Int>,
        val startPoints: Map<Int, TouchPointer>,
        val centerX: Float,
        val centerY: Float,
        val timeMs: Long,
    )

    private var baseline: Baseline? = null
    private var latched = false
    private var latchedAtMs = 0L

    val isLatched: Boolean get() = latched

    /**
     * 喂一帧触点。返回识别到的手势（每帧最多一个），未识别返回 null。
     *
     * [timeMs] 用事件时间（单调即可）。
     */
    fun onFrame(pointers: List<TouchPointer>, timeMs: Long): RecognizedMultiPointerGesture? {
        // 全部抬起：解锁，准备下一次。
        if (pointers.isEmpty()) {
            baseline = null
            latched = false
            return null
        }
        if (pointers.size < minFingerCount) {
            baseline = null
            return null
        }
        // latch 期间只等手指抬起（或数量变化），不重复触发。
        if (latched) {
            if (timeMs - latchedAtMs < LATCH_TIMEOUT_MS) return null
            // 超时保护：手指可能因丢帧一直“按着”，避免永久锁死。
            latched = false
            baseline = null
        }

        val ordered = pointers.sortedBy { it.id }
        val ids = ordered.map { it.id }
        val current = baseline
        if (current == null || current.ids != ids) {
            baseline = buildBaseline(ordered, timeMs)
            return null
        }
        if (timeMs - current.timeMs < settleMs) return null

        val centerX = ordered.sumOf { it.x.toDouble() }.toFloat() / ordered.size
        val centerY = ordered.sumOf { it.y.toDouble() }.toFloat() / ordered.size
        val dx = centerX - current.centerX
        val dy = centerY - current.centerY
        val distance = hypot(dx, dy)
        val fingerCount = ordered.size
        val fourFinger = fingerCount >= 4

        // 1) 旋转：叉积和 / 半径和；半径变化作为惩罚项。
        val rotate = rotationScore(ordered, centerX, centerY, current)
        if (rotate != null) {
            val threshold = if (fourFinger) {
                fourFingerRotateScoreThreshold
            } else {
                rotateScoreThreshold
            }
            if (abs(rotate.cross) / rotate.radiusSum >= threshold &&
                abs(rotate.cross) / rotate.radiusSum > abs(rotate.radiusDelta) / (2f * rotate.radiusSum)
            ) {
                val gesture = if (rotate.cross > 0f) {
                    MultiPointerGesture.ROTATE_CLOCKWISE
                } else {
                    MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE
                }
                return latch(gesture, fingerCount, timeMs)
            }
        }

        // 2) 捏合 / 张开：比较起始与当前 RMS 半径，整体移动不影响判定。
        val startRms = rmsRadius(current.startPoints.values, current.centerX, current.centerY)
        val currentRms = rmsRadius(ordered, centerX, centerY)
        val radiusChangeThreshold = max(minRadiusChange, startRms * radiusChangeFraction)
        if (startRms - currentRms >= radiusChangeThreshold) {
            return latch(MultiPointerGesture.PINCH, fingerCount, timeMs)
        }
        if (currentRms - startRms >= radiusChangeThreshold) {
            return latch(MultiPointerGesture.SPREAD, fingerCount, timeMs)
        }

        // 3) 滑动：四指及以上不参与，避免转动手腕时的质心漂移被当成滑动。
        if (!fourFinger && distance >= swipeThreshold) {
            val gesture = swipeDirection(dx, dy)
            if (gesture != null) return latch(gesture, fingerCount, timeMs)
        }
        return null
    }

    fun reset() {
        baseline = null
        latched = false
    }

    private class RotationScore(
        /** 各指起始向量与当前向量的叉积之和（正 = 顺时针）。 */
        val cross: Float,
        /** 各指起始向量的曼哈顿半径之和。 */
        val radiusSum: Float,
        /** 各指半径平方变化之和。 */
        val radiusDelta: Float,
    )

    /**
     * 计算相对各自质心的旋转量。
     *
     * 起点、当前点都用**各自帧的质心**归一化：质心在转手腕时怎么漂移都不会抵消旋转；
     * 半径平方变化用于把「捏合」从旋转里区分开。
     */
    private fun rotationScore(
        pointers: List<TouchPointer>,
        centerX: Float,
        centerY: Float,
        baseline: Baseline,
    ): RotationScore? {
        var cross = 0f
        var radiusSum = 0f
        var radiusDelta = 0f
        for (pointer in pointers) {
            val start = baseline.startPoints[pointer.id] ?: continue
            val startX = start.x - baseline.centerX
            val startY = start.y - baseline.centerY
            val currentX = pointer.x - centerX
            val currentY = pointer.y - centerY
            cross += startX * currentY - startY * currentX
            radiusSum += abs(startX) + abs(startY)
            radiusDelta += currentX * currentX + currentY * currentY -
                (startX * startX + startY * startY)
        }
        if (radiusSum <= MIN_RADIUS_SUM) return null
        return RotationScore(cross, radiusSum, radiusDelta)
    }

    private fun latch(
        gesture: MultiPointerGesture,
        fingerCount: Int,
        timeMs: Long,
    ): RecognizedMultiPointerGesture {
        latched = true
        latchedAtMs = timeMs
        baseline = null
        return RecognizedMultiPointerGesture(gesture, fingerCount)
    }

    private fun buildBaseline(ordered: List<TouchPointer>, timeMs: Long): Baseline {
        val centerX = ordered.sumOf { it.x.toDouble() }.toFloat() / ordered.size
        val centerY = ordered.sumOf { it.y.toDouble() }.toFloat() / ordered.size
        return Baseline(
            ids = ordered.map { it.id },
            startPoints = ordered.associateBy { it.id },
            centerX = centerX,
            centerY = centerY,
            timeMs = timeMs,
        )
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
        return if (absX >= absY * 1.2f) {
            if (dx > 0f) MultiPointerGesture.SWIPE_RIGHT else MultiPointerGesture.SWIPE_LEFT
        } else if (absY >= absX * 1.2f) {
            if (dy > 0f) MultiPointerGesture.SWIPE_DOWN else MultiPointerGesture.SWIPE_UP
        } else {
            null
        }
    }

    private companion object {
        const val MIN_RADIUS_SUM = 1e-4f
        const val LATCH_TIMEOUT_MS = 1_500L
    }
}

/** 绑定键：指数量 + 手势类型。 */
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
