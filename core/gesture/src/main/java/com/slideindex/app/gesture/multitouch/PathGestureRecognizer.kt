package com.slideindex.app.gesture.multitouch

import com.slideindex.app.gesture.GestureAction
import kotlin.math.hypot

/** 归一化坐标系里的一个路径点（x/y 均为屏幕比例）。 */
data class PathPoint(
    val x: Float,
    val y: Float,
)

/** 录制好的路径模板；[fingerCount] 必须与实时手指数一致才会参与匹配。 */
data class PathGestureTemplate(
    val id: String,
    val name: String,
    val fingerCount: Int,
    val points: List<PathPoint>,
    val action: GestureAction = GestureAction.None,
)

/** 一次路径手势的采集结果（抬手时产生）。 */
data class CollectedPath(
    val fingerCount: Int,
    val points: List<PathPoint>,
)

/**
 * 路径手势采集器（纯逻辑）。
 *
 * 只做三件事：
 * 1. 指针数达到 [minFingerCount] 后开始记录**质心路径**；
 * 2. 按 [sampleDistance] 丢点，避免 120Hz 采样把模板撑爆；
 * 3. 全部手指抬起时，路径够长、点数够多才交出去。
 *
 * 多指画路径时手指是逐个抬起的，所以指针数掉到阈值以下时**不立即丢弃**，
 * 而是等空帧再结算；期间如果又回到阈值以上，继续记录。
 */
class PathGestureCollector(
    private val minFingerCount: Int = 3,
    private val sampleDistance: Float = 0.012f,
    private val minPathLength: Float = 0.12f,
    private val minPoints: Int = 8,
    private val maxPoints: Int = 96,
) {
    private val points = ArrayList<PathPoint>()
    private var fingerCount = 0
    private var started = false

    val isCollecting: Boolean get() = started

    /** 喂一帧触点；抬手（空帧）时返回有效路径，否则 null。 */
    fun onFrame(pointers: List<TouchPointer>): CollectedPath? {
        if (pointers.isEmpty()) {
            return finish()
        }
        if (pointers.size < minFingerCount) {
            // 结束过程中（手指逐个抬起）：保留已有路径，等空帧结算。
            return null
        }
        started = true
        fingerCount = pointers.size
        val centerX = pointers.sumOf { it.x.toDouble() }.toFloat() / pointers.size
        val centerY = pointers.sumOf { it.y.toDouble() }.toFloat() / pointers.size
        val last = points.lastOrNull()
        if (last == null ||
            hypot(centerX - last.x, centerY - last.y) >= sampleDistance
        ) {
            if (points.size < maxPoints) {
                points += PathPoint(centerX, centerY)
            }
        }
        return null
    }

    /** 内置手势已经触发：这条路径不再参与自定义手势。 */
    fun cancel() {
        points.clear()
        fingerCount = 0
        started = false
    }

    fun reset() {
        cancel()
    }

    private fun finish(): CollectedPath? {
        val collected = if (!started || points.size < minPoints) {
            null
        } else {
            var length = 0f
            for (index in 1 until points.size) {
                length += hypot(
                    points[index].x - points[index - 1].x,
                    points[index].y - points[index - 1].y,
                )
            }
            if (length >= minPathLength) {
                CollectedPath(fingerCount, points.toList())
            } else {
                null
            }
        }
        cancel()
        return collected
    }
}

/**
 * 路径模板匹配（纯逻辑）。
 *
 * 录制与实时路径都先做同样的归一化：
 * - 等距重采样到固定点数（消除快慢/采样率差异）；
 * - 平移质心到原点、按 RMS 半径缩放（消除位置与大小差异）；
 * 然后逐点比较平均欧氏距离，越小越像。
 */
object PathGestureMatcher {
    const val RESAMPLE_COUNT = 32

    /** 平均距离低于这个值算匹配（归一化后坐标，经验值）。 */
    const val MATCH_THRESHOLD = 0.30f

    fun prepare(points: List<PathPoint>, count: Int = RESAMPLE_COUNT): List<PathPoint> {
        val resampled = resample(points, count)
        if (resampled.size < 2) return emptyList()
        return normalize(resampled)
    }

    /** 等距重采样：沿折线按固定弧长取点。 */
    fun resample(points: List<PathPoint>, count: Int): List<PathPoint> {
        if (count < 2 || points.size < 2) return emptyList()
        val lengths = DoubleArray(points.size)
        var total = 0.0
        for (index in 1 until points.size) {
            total += hypot(
                (points[index].x - points[index - 1].x).toDouble(),
                (points[index].y - points[index - 1].y).toDouble(),
            )
            lengths[index] = total
        }
        if (total < 1e-6) return emptyList()

        val result = ArrayList<PathPoint>(count)
        var segment = 1
        for (step in 0 until count) {
            val target = total * step / (count - 1)
            while (segment < points.size - 1 && lengths[segment] < target) {
                segment++
            }
            val previous = points[segment - 1]
            val next = points[segment]
            val segmentStart = lengths[segment - 1]
            val segmentLength = lengths[segment] - segmentStart
            val fraction = if (segmentLength <= 1e-9) {
                0.0
            } else {
                ((target - segmentStart) / segmentLength).coerceIn(0.0, 1.0)
            }
            result += PathPoint(
                x = (previous.x + (next.x - previous.x) * fraction).toFloat(),
                y = (previous.y + (next.y - previous.y) * fraction).toFloat(),
            )
        }
        return result
    }

    /** 平移质心、按 RMS 半径缩放，使位置和大小不影响匹配。 */
    fun normalize(points: List<PathPoint>): List<PathPoint> {
        if (points.isEmpty()) return emptyList()
        val centerX = points.sumOf { it.x.toDouble() }.toFloat() / points.size
        val centerY = points.sumOf { it.y.toDouble() }.toFloat() / points.size
        var rms = 0.0
        for (point in points) {
            val dx = (point.x - centerX).toDouble()
            val dy = (point.y - centerY).toDouble()
            rms += dx * dx + dy * dy
        }
        rms = kotlin.math.sqrt(rms / points.size)
        if (rms < 1e-6) return emptyList()
        return points.map { point ->
            PathPoint(
                x = ((point.x - centerX) / rms).toFloat(),
                y = ((point.y - centerY) / rms).toFloat(),
            )
        }
    }

    /** 两条已 prepare 的路径的平均逐点距离；长度不同时返回正无穷。 */
    fun distance(first: List<PathPoint>, second: List<PathPoint>): Float {
        if (first.isEmpty() || first.size != second.size) return Float.POSITIVE_INFINITY
        var sum = 0.0
        for (index in first.indices) {
            sum += hypot(
                (first[index].x - second[index].x).toDouble(),
                (first[index].y - second[index].y).toDouble(),
            )
        }
        return (sum / first.size).toFloat()
    }

    /** 返回最匹配的模板（距离小于 [MATCH_THRESHOLD] 的最小者），否则 null。 */
    fun bestMatch(
        path: List<PathPoint>,
        templates: List<PathGestureTemplate>,
    ): PathGestureTemplate? {
        val prepared = prepare(path)
        if (prepared.isEmpty()) return null
        var best: PathGestureTemplate? = null
        var bestDistance = MATCH_THRESHOLD
        for (template in templates) {
            val candidate = prepare(template.points)
            if (candidate.isEmpty()) continue
            val distance = distance(prepared, candidate)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = template
            }
        }
        return best
    }
}
