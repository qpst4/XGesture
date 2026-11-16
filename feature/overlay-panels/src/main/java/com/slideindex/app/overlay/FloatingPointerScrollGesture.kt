package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 悬浮指针的「实时滚动」：按住指针并移动，反复下发**单指短滑**。
 *
 * 和 Quick Cursor 的 realTimeScroll 同一套行为：
 * - 方向：跟随指针位移的主轴（死区 24dp）；
 * - 距离：光标推得越远，单次滑动越长（渐进 1/3 → 1×）；
 * - 节奏：一次滑完等 400ms 再滑下一次；松手立即停。
 *
 * 注意：这里**不是**连续拖拽——拖拽在列表里会被很多应用当成"长按拖动/选择文字"，短滑才安全。
 */
class FloatingPointerScrollGesture(
    private val service: AccessibilityService,
    startX: Float,
    startY: Float,
    private val onError: () -> Unit = {},
    private val onFinished: () -> Unit = {},
    /** 滚动状态（轴: 1 垂直 / 2 水平, 方向 ±1, 推力 px），给指针画"多次轨迹线"用。 */
    private val onState: (axis: Int, dir: Int, pushPx: Float) -> Unit = { _, _, _ -> }
) {

    private val density = service.resources.displayMetrics.density.coerceAtLeast(0.1f)
    private val screenWidth = service.resources.displayMetrics.widthPixels
    private val screenHeight = service.resources.displayMetrics.heightPixels

    private val anchor: Point
    private val marginPx = 24f * density
    private val deadzonePx = 24f * density
    private val swipeDistancePx = 200f * density
    private val gradualFromPx = 24f * density
    private val gradualSpanPx = 96f * density

    private val mainHandler = Handler(Looper.getMainLooper())
    private var active = true
    private var dispatchInFlight = false
    private var dirX = 0
    private var dirY = 0
    private var pushPx = 0f

    /**
     * 当前轴：0 未定 / 1 垂直 / 2 水平。
     *
     * 不用"一锤定音"的硬锁：Quick Cursor 只有上下左右，但用户起手会带一点斜向，硬锁很容易锁错，
     * 锁错后垂直拖动就完全不滚（看起来像"滚动断了"）。这里用回差：
     * 先按主导轴定一次，之后只有另一轴明显超过当前轴（1.6 倍）才改轴。
     */
    private var lockedAxis = 0

    private val nextSwipe = Runnable { dispatchSwipe() }

    init {
        anchor = Point(
            startX.roundToInt().coerceIn(marginPx.toInt(), (screenWidth - marginPx).toInt()),
            startY.roundToInt().coerceIn(marginPx.toInt(), (screenHeight - marginPx).toInt())
        )
    }

    /** 指针移动：更新方向/力度；还停在死区里就暂停滚动。 */
    fun updatePosition(x: Float, y: Float) {
        if (!active) return
        applyPush(x - anchor.x, y - anchor.y)
    }

    /** 手指相对按下的位移（指针冻结时用这个，不再依赖光标位置）。 */
    fun updateFingerDelta(dx: Float, dy: Float) {
        if (!active) return
        applyPush(dx, dy)
    }

    private fun applyPush(dx: Float, dy: Float) {
        pushPx = max(abs(dx), abs(dy))
        if (pushPx < deadzonePx) {
            mainHandler.removeCallbacks(nextSwipe)
            onState(lockedAxis, 0, pushPx)
            return
        }
        if (lockedAxis == 0) {
            lockedAxis = if (abs(dy) >= abs(dx)) AXIS_VERTICAL else AXIS_HORIZONTAL
        } else {
            val primary = if (lockedAxis == AXIS_VERTICAL) abs(dy) else abs(dx)
            val cross = if (lockedAxis == AXIS_VERTICAL) abs(dx) else abs(dy)
            if (cross > primary * 1.6f) {
                lockedAxis = if (lockedAxis == AXIS_VERTICAL) AXIS_HORIZONTAL else AXIS_VERTICAL
            }
        }
        if (lockedAxis == AXIS_VERTICAL) {
            dirX = 0
            dirY = if (dy > 0) 1 else -1
        } else {
            dirX = if (dx > 0) 1 else -1
            dirY = 0
        }
        onState(lockedAxis, if (lockedAxis == AXIS_VERTICAL) dirY else dirX, pushPx)
        if (!dispatchInFlight) {
            mainHandler.removeCallbacks(nextSwipe)
            mainHandler.post(nextSwipe)
        }
    }

    fun finish() {
        if (!active) return
        active = false
        mainHandler.removeCallbacks(nextSwipe)
        if (!dispatchInFlight) onFinished()
    }

    private fun dispatchSwipe() {
        if (!active || dispatchInFlight) return
        if (dirX == 0 && dirY == 0) return
        val progress = ((pushPx - gradualFromPx) / gradualSpanPx).coerceIn(0f, 1f)
        val distance = swipeDistancePx * (1f / 3f + 2f / 3f * progress)
        val endX = (anchor.x + dirX * distance).coerceIn(marginPx, screenWidth - marginPx)
        val endY = (anchor.y + dirY * distance).coerceIn(marginPx, screenHeight - marginPx)
        if (abs(endX - anchor.x) + abs(endY - anchor.y) < marginPx * 0.66f) return
        val path = Path().apply {
            moveTo(anchor.x.toFloat(), anchor.y.toFloat())
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS, false)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchInFlight = true
        val accepted = runCatching {
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    dispatchInFlight = false
                    if (!active) {
                        onFinished()
                        return
                    }
                    mainHandler.removeCallbacks(nextSwipe)
                    mainHandler.postDelayed(nextSwipe, SWIPE_DELAY_MS)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    dispatchInFlight = false
                    if (!active) {
                        onFinished()
                    } else {
                        Log.w(TAG, "swipe cancelled")
                        onError()
                    }
                }
            }, null)
        }.getOrDefault(false)
        if (!accepted) {
            dispatchInFlight = false
            Log.e(TAG, "dispatchGesture rejected")
            onError()
        }
    }

    private companion object {
        private const val TAG = "FpScroll"
        private const val AXIS_VERTICAL = 1
        private const val AXIS_HORIZONTAL = 2
        private const val SWIPE_DURATION_MS = 150L
        private const val SWIPE_DELAY_MS = 400L
    }
}
