package com.slideindex.app.gesture.multitouch

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** 一次识别结果（候选）。 */
data class RecognizedMultiPointerGesture(
    val gesture: MultiPointerGesture,
    val fingerCount: Int,
    /** 诊断信息（评分/半径差等），仅用于日志。 */
    val debug: String = "",
    /**
     * 这次触摸的**基准质心**（x、y 与 [TouchPointer] 同一坐标系：都按屏高归一化）。
     *
     * 取的是「手指数量最后一次变化那一帧」的质心，之后手怎么动都不变。用它给外部浮层
     * （快速启动器这类只能贴边画的面板）决定「从哪一侧、哪个高度出来」：
     * 若用触发帧的质心，滑动/张开过程中质心会漂过半屏，面板跟着翻边。
     *
     * 手指是先后落下的（1 指 → 3 指）时，基准会重建，所以拿到的是**多指都摆好**的位置。
     */
    val anchorX: Float = 0f,
    val anchorY: Float = 0f,
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
 * 和常见实现不同：**每种手势类型独立判定**，一次触摸可以同时产出多个候选
 * （例如“捏合”和“上滑”可能同时被判定），由上层按手势条目挑选，避免
 * “捏合被滑动抢”的单选题。
 *
 * 类型：
 * - 几何：捏合/张开（平均半径差，mm 阈值）、顺/逆时针旋转（|cross|/Σr² + 半径惩罚）；
 * - 滑动：质心位移 + 各指方向一致性，八方向；
 * - 点按：轻点/双击/三击（抬起判定）、长按（按住不动）。
 */
class MultiPointerGestureRecognizer(
    private val minFingerCount: Int = 1,
    private val maxFingerCount: Int = 5,
    private val swipeThreshold: Float = 0.09f,
    /** 点击允许的位移（经验阈值约 5px，归一化后按屏幕高度折算）。 */
    private val tapSlop: Float = 0.006f,
    private val settleMs: Long = 60L,
    private val longPressMs: Long = 550L,
    private val doubleTapMs: Long = 260L,
    private val rotateScoreThreshold: Float = 0.10f,
    private val fourFingerRotateScoreThreshold: Float = 0.08f,
    /** 手势尺寸（毫米）：默认 5，可调范围 5–10。 */
    private val gestureSizeMm: Float = 5f,
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
        /** 按下时的平均半径（用于捏合/张开）。 */
        val startRadius: Float,
        val timeMs: Long,
    )

    private var baseline: Baseline? = null
    private var downTimeMs = 0L
    private var downCenterX = 0f
    private var downCenterY = 0f
    private var maxFingers = 0
    private var moved = false

    /** 本次触摸已触发过的类型（每种类型只触发一次）。 */
    private val triggered = HashSet<MultiPointerGesture>()

    /** 上一帧位置：用于“各指方向一致性”判定。 */
    private val lastPositions = HashMap<Int, TouchPointer>()

    /** 本次触摸是否已经输出过 TOUCH（两指先后按下时补一次）。 */
    private var touchEmitted = false

    private var pendingTapAtMs = 0L
    private var pendingTapFingers = 0
    private var tapCount = 0
    private var lastTapAtMs = 0L

    /** 滑动方向历史：连续多帧方向一致才触发，避免起步抖动误判。 */
    private val swipeHistory = ArrayDeque<MultiPointerGesture>()

    @Synchronized
    fun onFrame(pointers: List<TouchPointer>, timeMs: Long): List<RecognizedMultiPointerGesture> {
        if (pointers.isEmpty()) {
            val results = ArrayList<RecognizedMultiPointerGesture>(2)
            resolveTapOnLift(results, timeMs)
            resetTouch()
            return results
        }
        if (pointers.size > maxFingerCount) {
            updateLastPositions(pointers)
            return emptyList()
        }

        val ordered = pointers.sortedBy { it.id }
        val centerX = ordered.sumOf { it.x.toDouble() }.toFloat() / ordered.size
        val centerY = ordered.sumOf { it.y.toDouble() }.toFloat() / ordered.size

        if (baseline == null) {
            if (tapCount > 0 && timeMs - lastTapAtMs > doubleTapMs) {
                tapCount = 0
                pendingTapAtMs = 0
            }
            downTimeMs = timeMs
            downCenterX = centerX
            downCenterY = centerY
            maxFingers = ordered.size
            moved = false
            triggered.clear()
            baseline = Baseline(
                ids = ordered.map { it.id },
                startPoints = ordered.associateBy { it.id },
                centerX = centerX,
                centerY = centerY,
                startRadius = averageRadius(ordered, centerX, centerY),
                timeMs = timeMs,
            )
            updateLastPositions(pointers)
            // 触摸：按下即触发，支持任意触点数（条目匹配兜底）。
            // 不写入 triggered——触摸只输出一次，不能阻止后续点击判定。
            val results = ArrayList<RecognizedMultiPointerGesture>(1)
            if (ordered.size >= 1) {
                touchEmitted = true
                results += RecognizedMultiPointerGesture(
                    MultiPointerGesture.TOUCH,
                    ordered.size,
                    "touch",
                    centerX,
                    centerY,
                )
            }
            return results
        }

        maxFingers = max(maxFingers, ordered.size)
        val dx = centerX - downCenterX
        val dy = centerY - downCenterY
        val distance = hypot(dx, dy)
        if (distance > tapSlop) moved = true

        val results = ArrayList<RecognizedMultiPointerGesture>(2)
        val current = baseline ?: return results
        // 所有候选共用「这次触摸的基准质心」当锚点（见 RecognizedMultiPointerGesture.anchorX）。
        val anchorX = current.centerX
        val anchorY = current.centerY

        // 长按（本次触摸还没触发过其它手势，且按住不动）。
        if (!moved &&
            triggered.isEmpty() &&
            timeMs - downTimeMs >= longPressMs &&
            MultiPointerGesture.LONG_PRESS !in triggered
        ) {
            triggered += MultiPointerGesture.LONG_PRESS
            results += RecognizedMultiPointerGesture(
                MultiPointerGesture.LONG_PRESS,
                maxFingers,
                "long",
                anchorX,
                anchorY,
            )
        }

        if (current.ids != ordered.map { it.id }) {
            baseline = Baseline(
                ids = ordered.map { it.id },
                startPoints = ordered.associateBy { it.id },
                centerX = centerX,
                centerY = centerY,
                startRadius = averageRadius(ordered, centerX, centerY),
                timeMs = timeMs,
            )
            // 手指数量变化 = 新基准：位移重新从零算，否则第二根手指落下时会被误判成“移动过”。
            moved = false
            // 两指先后按下：第二指加入时补一次触摸（触摸只按条目配置判定，不要求同帧按下）。
            if (!touchEmitted && ordered.size >= 1) {
                touchEmitted = true
                results += RecognizedMultiPointerGesture(
                    MultiPointerGesture.TOUCH,
                    ordered.size,
                    "touch",
                    centerX,
                    centerY,
                )
            }
            updateLastPositions(pointers)
            return results
        }
        if (timeMs - current.timeMs < settleMs) {
            updateLastPositions(pointers)
            return results
        }

        // 滑动：逐指判定方向，要求至少 2/3 手指同向。
        if (distance >= swipeThreshold) {
            val direction = consensusSwipeDirection(ordered)
            if (direction != null && direction !in triggered) {
                if (swipeHistory.lastOrNull() != direction) {
                    swipeHistory.clear()
                }
                swipeHistory.addLast(direction)
                if (swipeHistory.size > SWIPE_STABLE_FRAMES) {
                    swipeHistory.removeFirst()
                }
                if (swipeHistory.size == SWIPE_STABLE_FRAMES &&
                    swipeHistory.all { it == direction }
                ) {
                    triggered += direction
                    results += RecognizedMultiPointerGesture(
                        direction,
                        maxFingers,
                        "swipe",
                        anchorX,
                        anchorY,
                    )
                }
            }
        }

        // 几何（>=2 指）。
        if (ordered.size >= 2) {
            val four = ordered.size >= 4
            val rotation = rotationScore(ordered, centerX, centerY, current)
            if (rotation != null && MultiPointerGesture.ROTATE_CLOCKWISE !in triggered &&
                MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE !in triggered
            ) {
                val score = abs(rotation.cross) / rotation.radiusSquared
                val penalty = abs(rotation.radiusDelta) / (2f * rotation.radiusSquared)
                val threshold = if (four) fourFingerRotateScoreThreshold else rotateScoreThreshold
                val maxDrift = if (four) fourFingerRotateMaxRadiusDrift else rotateMaxRadiusDrift
                val radius = averageRadius(ordered, centerX, centerY)
                val radiusRatio = if (current.startRadius > MIN_RADIUS) {
                    radius / current.startRadius
                } else {
                    1f
                }
                if (score >= threshold &&
                    score >= penalty &&
                    abs(radiusRatio - 1f) <= maxDrift
                ) {
                    val gesture = if (rotation.cross > 0f) {
                        MultiPointerGesture.ROTATE_CLOCKWISE
                    } else {
                        MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE
                    }
                    triggered += gesture
                    results += RecognizedMultiPointerGesture(
                        gesture,
                        maxFingers,
                        "rot s=%.3f p=%.3f r=%.3f".format(score, penalty, radiusRatio),
                        anchorX,
                        anchorY,
                    )
                }
            }
            // 捏合/张开：平均半径差 >= max(1, gestureSizeMm/4) mm。
            val startRadius = current.startRadius
            val nowRadius = averageRadius(ordered, centerX, centerY)
            val delta = nowRadius - startRadius
            val threshold = max(1f, gestureSizeMm / 4f) / screenHeightMm
            if (delta <= -threshold && MultiPointerGesture.PINCH !in triggered) {
                triggered += MultiPointerGesture.PINCH
                results += RecognizedMultiPointerGesture(
                    MultiPointerGesture.PINCH,
                    maxFingers,
                    "pinch d=%.4f t=%.4f".format(delta, threshold),
                    anchorX,
                    anchorY,
                )
            } else if (delta >= threshold && MultiPointerGesture.SPREAD !in triggered) {
                triggered += MultiPointerGesture.SPREAD
                results += RecognizedMultiPointerGesture(
                    MultiPointerGesture.SPREAD,
                    maxFingers,
                    "spread d=%.4f t=%.4f".format(delta, threshold),
                    anchorX,
                    anchorY,
                )
            }
        }

        updateLastPositions(pointers)
        return results
    }

    /** 由外部定时器调用：双击窗口结束仍未等到第二次点击 → 发出「轻点」。 */
    @Synchronized
    fun onTick(timeMs: Long): RecognizedMultiPointerGesture? = null

    @Synchronized
    fun reset() {
        resetTouch()
        tapCount = 0
        pendingTapAtMs = 0
        lastTapAtMs = 0
    }

    /**
     * 抬手时产生点击候选（点击是独立事件，立即发出，不等窗口）。
     *
     * 第一次点击发 TAP；距上次点击 [doubleTapMs] 内的第二次点击再发 DOUBLE_TAP；
     * 第三次发 TRIPLE_TAP。协调器按条目类型匹配，不需要识别器“猜”。
     */
    private fun resolveTapOnLift(
        results: MutableList<RecognizedMultiPointerGesture>,
        timeMs: Long,
    ) {
        if (moved ||
            maxFingers !in minFingerCount..maxFingerCount ||
            MultiPointerGesture.LONG_PRESS in triggered
        ) {
            return
        }
        val fingers = maxFingers
        // 抬手时手指已经没了，用这次触摸最后一次建立基准的质心当锚点（即点击落点）。
        val anchorX = baseline?.centerX ?: downCenterX
        val anchorY = baseline?.centerY ?: downCenterY
        val isSecond = tapCount == 1 && timeMs - lastTapAtMs <= doubleTapMs
        val isThird = tapCount == 2 && timeMs - lastTapAtMs <= doubleTapMs
        when {
            isThird -> {
                tapCount = 0
                lastTapAtMs = timeMs
                results += RecognizedMultiPointerGesture(
                    MultiPointerGesture.TRIPLE_TAP,
                    fingers,
                    "triple",
                    anchorX,
                    anchorY,
                )
            }
            isSecond -> {
                tapCount = 2
                lastTapAtMs = timeMs
                results += RecognizedMultiPointerGesture(
                    MultiPointerGesture.DOUBLE_TAP,
                    fingers,
                    "double",
                    anchorX,
                    anchorY,
                )
            }
            else -> {
                tapCount = 1
                lastTapAtMs = timeMs
                results += RecognizedMultiPointerGesture(
                    MultiPointerGesture.TAP,
                    fingers,
                    "tap",
                    anchorX,
                    anchorY,
                )
            }
        }
    }

    private fun updateLastPositions(pointers: List<TouchPointer>) {
        val ids = pointers.map { it.id }.toSet()
        lastPositions.keys.retainAll(ids)
        for (pointer in pointers) {
            lastPositions[pointer.id] = pointer
        }
    }

    /** 各指方向分类的众数；至少 2/3 手指同向才算数。 */
    private fun consensusSwipeDirection(pointers: List<TouchPointer>): MultiPointerGesture? {
        val counts = HashMap<MultiPointerGesture, Int>()
        var counted = 0
        for (pointer in pointers) {
            val last = lastPositions[pointer.id] ?: continue
            counted++
            val direction = swipeDirection(pointer.x - last.x, pointer.y - last.y) ?: continue
            counts[direction] = (counts[direction] ?: 0) + 1
        }
        if (counted < 2) return null
        val best = counts.maxByOrNull { it.value } ?: return null
        return if (best.value * 3 >= counted * 2) best.key else null
    }

    /** 各指位移方向与质心位移方向一致的比例 >= 60% 才算滑动。 */
    private fun isSwipeConsistent(
        pointers: List<TouchPointer>,
        dx: Float,
        dy: Float,
    ): Boolean {
        if (pointers.size < 2) return true
        var consistent = 0
        var counted = 0
        for (pointer in pointers) {
            val last = lastPositions[pointer.id] ?: continue
            counted++
            val vx = pointer.x - last.x
            val vy = pointer.y - last.y
            if (vx * dx + vy * dy > 0f) consistent++
        }
        if (counted == 0) return true
        return consistent.toFloat() / counted >= 0.6f
    }

    private fun resetTouch() {
        baseline = null
        downTimeMs = 0
        maxFingers = 0
        moved = false
        triggered.clear()
        swipeHistory.clear()
        lastPositions.clear()
        touchEmitted = false
    }

    private fun rotationScore(
        pointers: List<TouchPointer>,
        centerX: Float,
        centerY: Float,
        baseline: Baseline,
    ): RotationScore? {
        var cross = 0f
        var radiusSquared = 0f
        var radiusDelta = 0f
        for (pointer in pointers) {
            val start = baseline.startPoints[pointer.id] ?: continue
            val startX = start.x - baseline.centerX
            val startY = start.y - baseline.centerY
            val currentX = pointer.x - centerX
            val currentY = pointer.y - centerY
            cross += startX * currentY - startY * currentX
            radiusSquared += currentX * currentX + currentY * currentY
            radiusDelta += currentX * currentX + currentY * currentY -
                (startX * startX + startY * startY)
        }
        if (radiusSquared <= MIN_RADIUS) return null
        return RotationScore(cross, radiusSquared, radiusDelta)
    }

    private class RotationScore(
        val cross: Float,
        val radiusSquared: Float,
        val radiusDelta: Float,
    )

    private fun averageRadius(pointers: List<TouchPointer>, cx: Float, cy: Float): Float {
        if (pointers.isEmpty()) return 0f
        return pointers.sumOf { hypot(it.x - cx, it.y - cy).toDouble() }.toFloat() / pointers.size
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

        /** 滑动方向连续一致的帧数。 */
        const val SWIPE_STABLE_FRAMES = 3
    }
}
