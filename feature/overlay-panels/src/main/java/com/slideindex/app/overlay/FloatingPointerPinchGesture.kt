package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 悬浮指针的「实时双指缩放」：两根手指以光标位置为中心对称张合，跟手实时缩放。
 *
 * 完整对齐 Quick Cursor 逆向算法（[n11.java]）：
 * 1. 方向向量自适应：依据光标到屏幕四周边缘的距离（minX / minY）按比例分配张合方向，
 *    光标靠边时自动将开合方向偏向纵向，防止手指硬撞屏幕边缘；
 * 2. 边缘安全保护：预留 [EDGE_PADDING_DP] 安全边距，绝不触碰屏幕 0 像素系统侧滑手势区；
 * 3. 静止心跳保持：用户手指按住暂未移动时，以 100ms 周期派发原地保持段，防止系统无障碍底层
 *    （MotionEventInjector）因等待未完结的 continueStroke 超过 2000ms 而超时取消；
 * 4. 坐标防重：两指位置未发生实质变化时不上报，避免系统事件队列拥塞。
 */
class FloatingPointerPinchGesture(
    private val service: AccessibilityService,
    startX: Float,
    startY: Float,
    private val onError: () -> Unit = {},
    private val onFinished: () -> Unit = {},
    /** 两根虚拟手指的实时位置（x1,y1,x2,y2），用于画 QC 同款的缩放视觉。 */
    private val onVisualize: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> }
) {

    private val density = service.resources.displayMetrics.density.coerceAtLeast(0.1f)
    private val screenWidth = service.resources.displayMetrics.widthPixels
    private val screenHeight = service.resources.displayMetrics.heightPixels

    private val anchorX = startX
    private val anchorY = startY

    // QC 逆向算法：按光标到屏幕边缘的距离比重计算张合轴向，防止靠边时撞墙
    private val ratioX: Double
    private val ratioY: Double

    private val strokes = arrayOfNulls<GestureDescription.StrokeDescription>(2)
    private val points = arrayOf(Point(), Point())
    private val previousPoints = arrayOf(Point(), Point())

    private val defaultHalf = 60f * density
    private val minHalf = 5f * density
    private val edgePaddingPx = (16f * density).roundToInt()
    private val startThresholdPx = 12f * density

    private var fingerDy = 0f
    private var dispatchInFlight = false
    private var finishing = false
    private var finished = false
    private var started = false
    private var pendingUpdate = false
    private var dispatchSeq = 0
    private var completeCount = 0
    private var lastDispatchAtMs = 0L
    private var dispatchRetries = 0
    private var rebuildCount = 0
    private val startedAtMs = android.os.SystemClock.uptimeMillis()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val callback = PinchCallback()
    /**
     * 独立心跳：**不再依赖 `dispatchInFlight`**。
     *
     * 以前的写法是"回调回来才续 100ms 心跳"，一旦某次回调丢失，`dispatchInFlight` 永远为 true，
     * 心跳再也不会发，系统 2s 后取消手势 —— 表现就是"偶尔断一下"。
     */
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            if (finished || finishing) return
            val now = android.os.SystemClock.uptimeMillis()
            if (dispatchInFlight && now - lastDispatchAtMs > DISPATCH_TIMEOUT_MS) {
                Log.w(TAG, "dispatch callback timeout; recover")
                dispatchInFlight = false
            }
            if (!started) return
            if (dispatchInFlight) {
                mainHandler.postDelayed(this, KEEP_ALIVE_INTERVAL_MS)
            } else {
                dispatchContinuation(continueStroke = true)
            }
        }
    }

    init {
        val minX = min(anchorX, screenWidth - anchorX).toDouble().coerceAtLeast(0.0)
        val minY = min(anchorY, screenHeight - anchorY).toDouble().coerceAtLeast(0.0)
        val h = hypot(minX, minY)
        if (h <= 0.0) {
            ratioX = 0.0
            ratioY = 1.0
        } else {
            ratioX = minX / h
            ratioY = minY / h
        }
        // 按下就先把两根虚拟手指画出来：不等 12dp 位移，也不要任何"圆环"过渡。
        computeTargetPoints()
        publishVisual()
    }

    private fun publishVisual() {
        onVisualize(
            points[0].x.toFloat(),
            points[0].y.toFloat(),
            points[1].x.toFloat(),
            points[1].y.toFloat(),
        )
    }

    /**
     * 手指相对按下点的累计位移（QC 的 frozen-cursor 语义）。
     *
     * 超过 [startThresholdPx] 才真正开始缩放；之后按竖直位移（dy）驱动两指张合。
     */
    fun updateFingerDelta(dx: Float, dy: Float) {
        if (finishing || finished) return
        mainHandler.removeCallbacks(keepAliveRunnable)
        fingerDy = dy
        // 每一帧都先刷新视觉，位移不够也照样显示两指（只是不注入）。
        computeTargetPoints()
        publishVisual()

        if (!started) {
            if (hypot(dx, dy) < startThresholdPx) return
            started = true

            previousPoints[0].set(points[0].x, points[0].y)
            previousPoints[1].set(points[1].x, points[1].y)
            strokes[0] = newStroke(points[0], points[0], continueStroke = true)
            strokes[1] = newStroke(points[1], points[1], continueStroke = true)
            Log.i(TAG, "pinch started dx=$dx dy=$dy p0=${points[0]} p1=${points[1]}")
            dispatch()
            return
        }

        // 坐标无实质位移时跳过下发，等后续位移或心跳
        if (points[0].x == previousPoints[0].x && points[0].y == previousPoints[0].y &&
            points[1].x == previousPoints[1].x && points[1].y == previousPoints[1].y
        ) {
            return
        }

        if (dispatchInFlight) {
            pendingUpdate = true
            return
        }
        dispatchContinuation(continueStroke = true)
    }

    fun finish() {
        if (finished || finishing) return
        Log.i(TAG, "finish: started=$started inFlight=$dispatchInFlight seq=$dispatchSeq completed=$completeCount")
        finishing = true
        mainHandler.removeCallbacks(keepAliveRunnable)
        if (!started) {
            finishNow()
            return
        }
        if (dispatchInFlight) {
            mainHandler.postDelayed({ finish() }, FINISH_RETRY_MS)
            return
        }
        dispatchContinuation(continueStroke = false)
    }

    /**
     * 计算目标双指坐标：
     * [sign] = -1 为左/上手指，+1 为右/下手指。
     * 往上推（dy < 0）= 张开，往下推（dy > 0）= 收拢。
     * 并应用安全边距 [edgePaddingPx]，绝不触碰屏幕 0 像素。
     */
    private fun computeTargetPoints() {
        val dist = max(minHalf, defaultHalf - fingerDy)
        for (i in 0..1) {
            val sign = if (i == 0) -1 else 1
            val rawX = (anchorX + sign * ratioX * dist).roundToInt()
            val rawY = (anchorY + sign * ratioY * dist).roundToInt()
            val safeX = rawX.coerceIn(edgePaddingPx, screenWidth - 1 - edgePaddingPx)
            val safeY = rawY.coerceIn(edgePaddingPx, screenHeight - 1 - edgePaddingPx)
            points[i].set(safeX, safeY)
        }
    }

    private fun newStroke(from: Point, to: Point, continueStroke: Boolean): GestureDescription.StrokeDescription {
        val path = Path().apply {
            moveTo(from.x.toFloat(), from.y.toFloat())
            lineTo(to.x.toFloat(), to.y.toFloat())
        }
        return GestureDescription.StrokeDescription(path, 0L, SEGMENT_MS, continueStroke)
    }

    /** 续写两条手指（从上一拍位置到当前目标位置）。 */
    private fun dispatchContinuation(continueStroke: Boolean) {
        val first = strokes[0]
        val second = strokes[1]
        if (first == null || second == null) {
            finishNow()
            return
        }
        val path0 = Path().apply {
            moveTo(previousPoints[0].x.toFloat(), previousPoints[0].y.toFloat())
            lineTo(points[0].x.toFloat(), points[0].y.toFloat())
        }
        val path1 = Path().apply {
            moveTo(previousPoints[1].x.toFloat(), previousPoints[1].y.toFloat())
            lineTo(points[1].x.toFloat(), points[1].y.toFloat())
        }
        val next0 = runCatching {
            first.continueStroke(path0, 0L, SEGMENT_MS, continueStroke)
        }.getOrNull()
        val next1 = runCatching {
            second.continueStroke(path1, 0L, SEGMENT_MS, continueStroke)
        }.getOrNull()
        if (next0 == null || next1 == null) {
            // 系统拒绝了某一段续写（常见于别的注入刚结束）：重建一对新 stroke，不要整段手势结束。
            Log.w(TAG, "continueStroke failed; rebuild pair")
            rebuildStrokes()
            return
        }
        strokes[0] = next0
        strokes[1] = next1
        dispatch()
    }

    private fun rebuildStrokes() {
        strokes[0] = newStroke(points[0], points[0], continueStroke = true)
        strokes[1] = newStroke(points[1], points[1], continueStroke = true)
        previousPoints[0].set(points[0].x, points[0].y)
        previousPoints[1].set(points[1].x, points[1].y)
        dispatch()
    }

    private fun dispatch() {
        val first = strokes[0] ?: return
        val second = strokes[1] ?: return
        previousPoints[0].set(points[0].x, points[0].y)
        previousPoints[1].set(points[1].x, points[1].y)
        dispatchInFlight = true
        lastDispatchAtMs = android.os.SystemClock.uptimeMillis()
        dispatchSeq++
        val gesture = GestureDescription.Builder()
            .addStroke(first)
            .addStroke(second)
            .build()
        val accepted = runCatching { service.dispatchGesture(gesture, callback, null) }
            .getOrDefault(false)
        if (!accepted) {
            dispatchInFlight = false
            dispatchRetries++
            Log.w(TAG, "dispatchGesture rejected; retry=$dispatchRetries")
            if (dispatchRetries >= MAX_DISPATCH_RETRIES) {
                finishNow()
                onError()
            } else {
                mainHandler.postDelayed({ if (!finished && !finishing) dispatch() }, RETRY_DELAY_MS)
            }
            return
        }
        dispatchRetries = 0
    }

    private fun finishNow() {
        mainHandler.removeCallbacks(keepAliveRunnable)
        if (finished) return
        finished = true
        onFinished()
    }

    private fun onSegmentCompleted() {
        dispatchInFlight = false
        completeCount++
        if (finished) return
        if (finishing) {
            finishNow()
            return
        }
        mainHandler.removeCallbacks(keepAliveRunnable)
        if (pendingUpdate) {
            pendingUpdate = false
            dispatchContinuation(continueStroke = true)
        } else {
            // 用户手指按住暂未移动：心跳保持续写原地 Stroke，防止系统底层因超时取消手势
            mainHandler.postDelayed(keepAliveRunnable, KEEP_ALIVE_INTERVAL_MS)
        }
    }

    private inner class PinchCallback : AccessibilityService.GestureResultCallback() {
        override fun onCancelled(gestureDescription: GestureDescription?) {
            dispatchInFlight = false
            mainHandler.removeCallbacks(keepAliveRunnable)
            Log.w(TAG, "gesture cancelled: seq=$dispatchSeq completed=$completeCount rebuild=$rebuildCount")
            if (finishing) {
                finishNow()
                return
            }
            if (!started) {
                finishNow()
                return
            }
            // 被系统取消（别的注入抢占/目标 App 吃掉第一个触点）时重建，而不是整段结束。
            rebuildCount++
            if (rebuildCount > MAX_REBUILDS) {
                onError()
                return
            }
            mainHandler.postDelayed({ if (!finished && !finishing) rebuildStrokes() }, RETRY_DELAY_MS)
        }

        override fun onCompleted(gestureDescription: GestureDescription?) {
            onSegmentCompleted()
        }
    }

    private companion object {
        private const val TAG = "FpPinch"
        private const val SEGMENT_MS = 1L
        private const val FINISH_RETRY_MS = 1L
        private const val KEEP_ALIVE_INTERVAL_MS = 100L
        private const val DISPATCH_TIMEOUT_MS = 1_500L
        private const val RETRY_DELAY_MS = 80L
        private const val MAX_DISPATCH_RETRIES = 3
        private const val MAX_REBUILDS = 4
    }
}
