package com.slideindex.app.gesture.multitouch

import com.slideindex.app.gesture.GestureAction
import kotlin.math.hypot
import kotlin.math.sqrt

/** 归一化坐标系里的一个路径点（x/y 均为屏幕高度比例）。 */
data class PathPoint(
    val x: Float,
    val y: Float,
)

/** 一条轨迹（一根手指的一次划动）。 */
data class PathStroke(
    val points: List<PathPoint>,
)

/** 录制好的路径模板：多条轨迹，顺序对应手指落下的先后。 */
data class PathGestureTemplate(
    val id: String,
    val name: String,
    val strokes: List<PathStroke>,
    val action: GestureAction = GestureAction.None,
) {
    val fingerCount: Int get() = strokes.size
}

/** 一次路径手势的采集结果（所有手指抬起时产生）。 */
data class CollectedPath(
    val strokes: List<PathStroke>,
) {
    val fingerCount: Int get() = strokes.size
}

/**
 * 路径手势采集器（纯逻辑）。
 *
 * 每根手指（pointerId）单独一条轨迹：新指按下就开一条新轨迹，手指抬起时保留该轨迹，
 * 全部抬起后再统一结算。这样无论是“多指同时画”还是“逐条画”，采集结构一致。
 */
class PathGestureCollector(
    private val minStrokes: Int = 1,
    private val maxStrokes: Int = 5,
    private val sampleDistance: Float = 0.012f,
    private val minStrokeLength: Float = 0.05f,
) {
    private val strokes = LinkedHashMap<Int, MutableList<PathPoint>>()
    private var started = false

    val isCollecting: Boolean get() = started

    /** 喂一帧触点；全部抬起时返回有效路径，否则 null。 */
    fun onFrame(pointers: List<TouchPointer>): CollectedPath? {
        if (pointers.isEmpty()) return finish()
        for (pointer in pointers) {
            if (!strokes.containsKey(pointer.id)) {
                if (strokes.size >= maxStrokes) continue
                strokes[pointer.id] = mutableListOf()
                started = true
            }
            val points = strokes[pointer.id] ?: continue
            val last = points.lastOrNull()
            if (last == null ||
                hypot(pointer.x - last.x, pointer.y - last.y) >= sampleDistance
            ) {
                points += PathPoint(pointer.x, pointer.y)
            }
        }
        return null
    }

    /** 内置手势已经触发，或录制取消：丢弃这次采集。 */
    fun cancel() {
        strokes.clear()
        started = false
    }

    private fun finish(): CollectedPath? {
        if (!started) return null
        val valid = strokes.values
            .filter { points -> points.size >= 2 && strokeLength(points) >= minStrokeLength }
            .map { points -> PathStroke(points.toList()) }
            .take(maxStrokes)
        cancel()
        if (valid.size < minStrokes) return null
        return CollectedPath(valid)
    }

    private fun strokeLength(points: List<PathPoint>): Float {
        var length = 0f
        for (index in 1 until points.size) {
            length += hypot(
                points[index].x - points[index - 1].x,
                points[index].y - points[index - 1].y,
            )
        }
        return length
    }
}

/**
 * 路径模板匹配（纯逻辑）。
 *
 * 每条轨迹单独做“等距重采样 + 质心/尺度归一化”，再逐条比较平均欧氏距离；
 * 轨迹条数必须一致。位置、大小、画得快慢都不影响匹配，形状和顺序影响。
 */
object PathGestureMatcher {
    const val RESAMPLE_COUNT = 24

    /** 平均距离低于这个值算匹配。 */
    const val MATCH_THRESHOLD = 0.32f

    fun prepare(strokes: List<PathStroke>): List<List<PathPoint>> =
        strokes.map { stroke -> prepareStroke(stroke.points) }.filter { it.size >= 2 }

    fun prepareStroke(points: List<PathPoint>): List<PathPoint> {
        val resampled = resample(points, RESAMPLE_COUNT)
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

    /** 平移质心、按 RMS 半径缩放。 */
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
        rms = sqrt(rms / points.size)
        if (rms < 1e-6) return emptyList()
        return points.map { point ->
            PathPoint(
                x = ((point.x - centerX) / rms).toFloat(),
                y = ((point.y - centerY) / rms).toFloat(),
            )
        }
    }

    /** 多轨迹平均距离；条数不同返回正无穷。 */
    fun distance(first: List<List<PathPoint>>, second: List<List<PathPoint>>): Float {
        if (first.isEmpty() || first.size != second.size) return Float.POSITIVE_INFINITY
        var sum = 0f
        var counted = 0
        for (index in first.indices) {
            val a = first[index]
            val b = second[index]
            if (a.size != b.size || a.isEmpty()) return Float.POSITIVE_INFINITY
            var strokeSum = 0.0
            for (point in a.indices) {
                strokeSum += hypot(
                    (a[point].x - b[point].x).toDouble(),
                    (a[point].y - b[point].y).toDouble(),
                )
            }
            sum += (strokeSum / a.size).toFloat()
            counted++
        }
        return if (counted == 0) Float.POSITIVE_INFINITY else sum / counted
    }

    /** 返回最匹配的模板（距离小于 [MATCH_THRESHOLD] 的最小者），否则 null。 */
    fun bestMatch(
        strokes: List<PathStroke>,
        templates: List<PathGestureTemplate>,
    ): PathGestureTemplate? {
        val prepared = prepare(strokes)
        if (prepared.isEmpty()) return null
        var best: PathGestureTemplate? = null
        var bestDistance = MATCH_THRESHOLD
        for (template in templates) {
            if (template.strokes.size != prepared.size) continue
            val candidate = prepare(template.strokes)
            if (candidate.size != prepared.size) continue
            val distance = distance(prepared, candidate)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = template
            }
        }
        return best
    }
}
