package com.slideindex.app.macro.record

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.service.SlideIndexAccessibilityService
import kotlin.math.hypot
import kotlin.math.max

/**
 * 录制期间的**触摸捕获层**：一圈围着"录制控制条"的窗口，中间给控制条留一个洞。
 *
 * 为什么要挖洞（而不是一块全屏窗）：
 * 控制条必须能被原生触摸（点按钮、按住"⠿"拖动），而全屏捕获窗每次"摘窗→注入→装回"都会跑到
 * 控制条上面，把按钮点击吞掉、把拖动变成一次"录制滑动"。给控制条留洞之后，落在条上的触摸
 * 根本不会进捕获层：不记录、不注入，按钮和拖动都是系统原生行为。
 *
 * 其余约束与原实现一致：
 * 1. **注入回环**：注入的手势会走系统输入管线，若本层仍可触摸会被自己接住 → 死循环。
 *    所以注入期间把四块窗全部摘掉，注入环境与"引擎回放"一致。
 * 2. **标志生效竞态**：`updateViewLayout` 异步，摘窗后必须等 [INJECTION_APPLY_DELAY_MS] 再注入。
 * 3. 采样规则与录制器一致（相邻采样点 ≥ 24px 才记；按下 / 抬起强制记录）。
 */
class MacroRecordingTouchCapture(
    private val hostContext: Context,
    private val listener: Listener,
    /** 注入被系统拒绝 / 注入链路不可用时回调：调用方应当**立即结束录制**，别让触摸一直被吞。 */
    private val onInjectionUnavailable: () -> Unit = {},
    /** 录制控制条在屏幕上的矩形（留洞用）；null = 还没显示，先按全屏捕获。 */
    private val barRectProvider: () -> Rect? = { null },
) {

    /** 触摸事件回调（原始屏幕像素坐标 + 事件时间）。 */
    interface Listener {
        fun onDown(x: Float, y: Float, eventTimeMs: Long)
        fun onMove(x: Float, y: Float, eventTimeMs: Long)
        fun onUp(x: Float, y: Float, eventTimeMs: Long)
        fun onCancel()
    }

    private class Pane(
        val view: FrameLayout,
        val params: WindowManager.LayoutParams,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private val panes = ArrayList<Pane>()

    private val sampleXs = ArrayList<Float>()
    private val sampleYs = ArrayList<Float>()
    private var downX = 0f
    private var downY = 0f
    private var downTimeMs = 0L
    private var capturing = false
    private var injecting = false
    private var injectionFailures = 0

    /**
     * 注入期间把四块窗**全部摘掉**（不是切换触摸标志）。
     *
     * 真机验证过"引擎回放（完全没有这扇窗时）能正常滚动"，而"录制时（有窗 + 切换
     * NOT_TOUCHABLE）松手后那一下不生效"。所以注入环境和已验证可行的环境保持一致：
     * 摘窗 → 注入 → 装回。代价：这段时间里的触摸会直接给目标 App，不被记录。
     */
    private var attached = false

    /** 注入代次：兜底定时器按代次失效，避免提前打开闸门形成"记录→注入→再记录"回环。 */
    private var injectionGeneration = 0

    /**
     * 待注入的手势队列。注入会把用户"正在进行"的触摸取消掉，所以必须串行：
     * 只要还有在途触摸或正在注入，新的回放就排队。
     */
    private val pendingForwards = ArrayDeque<() -> Unit>()

    /** 注入的事件带有 `deviceId < 0`（无障碍注入的虚拟设备）。 */
    private fun isInjectedEvent(event: MotionEvent): Boolean = event.deviceId < 0

    val isShowing: Boolean get() = panes.isNotEmpty()

    fun show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show() }
            return
        }
        if (panes.isNotEmpty()) return
        val manager = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = manager

        val type = resolveWindowType()
        val rects = computePaneRects()
        var added = 0
        for (rect in rects) {
            val view = FrameLayout(hostContext).apply {
                setOnTouchListener { _, event ->
                    when {
                        isInjectedEvent(event) -> {
                            if (!injecting) {
                                Log.w(TAG, "注入事件落回本层（窗口标志生效晚了）action=${event.actionMasked}，已忽略")
                            }
                            true
                        }

                        injecting -> true

                        else -> {
                            handleTouch(event)
                            true
                        }
                    }
                }
            }
            val params = buildParams(type, rect)
            runCatching { manager.addView(view, params) }
                .onSuccess {
                    panes += Pane(view, params)
                    added++
                }
                .onFailure { Log.w(TAG, "无法添加捕获窗 $rect", it) }
        }
        if (added == 0) {
            Log.w(TAG, "无法添加录制捕获层")
            windowManager = null
            return
        }
        attached = true
        Log.i(TAG, "捕获层已添加 panes=$added type=$type hole=${barRectProvider()}")
    }

    /** 控制条移动/显示后调用：重新算四块窗的位置，保证中间那个洞一直对着控制条。 */
    fun updateHole() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateHole() }
            return
        }
        if (panes.isEmpty()) return
        val manager = windowManager ?: return
        val rects = computePaneRects()
        Log.i(TAG, "updateHole rects=$rects")
        panes.forEachIndexed { index, pane ->
            val rect = rects.getOrNull(index) ?: return@forEachIndexed
            pane.params.width = rect.width().coerceAtLeast(1)
            pane.params.height = rect.height().coerceAtLeast(1)
            pane.params.x = rect.left
            pane.params.y = rect.top
            if (attached) {
                runCatching { manager.updateViewLayout(pane.view, pane.params) }
                    .onFailure { Log.w(TAG, "更新捕获窗失败", it) }
            }
        }
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        if (panes.isEmpty()) return
        capturing = false
        injecting = false
        // 让所有还没触发的兜底定时器失效。
        injectionGeneration++
        injectionFailures = 0
        val manager = windowManager
        if (attached) {
            panes.forEach { pane -> runCatching { manager?.removeView(pane.view) } }
        }
        panes.clear()
        attached = false
        windowManager = null
        Log.i(TAG, "捕获层已移除")
    }

    /** 屏幕尺寸（与 MATCH_PARENT 全屏窗一致的整屏像素）。 */
    private fun screenSize(): Rect {
        val manager = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val bounds = manager?.currentWindowMetrics?.bounds
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) return bounds
        val metrics = hostContext.resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * 四块窗：上、下、左、右，围着控制条留洞。
     *
     * 洞的边界 = 控制条矩形（按屏幕坐标裁进屏内）；没有控制条时退化成"上面一整块"。
     */
    private fun computePaneRects(): List<Rect> {
        val screen = screenSize()
        val bar = barRectProvider()
        if (bar == null || bar.isEmpty) {
            return listOf(
                Rect(screen),
                Rect(0, 0, 1, 1),
                Rect(0, 0, 1, 1),
                Rect(0, 0, 1, 1),
            )
        }
        val hole = Rect(
            bar.left.coerceIn(screen.left, screen.right),
            bar.top.coerceIn(screen.top, screen.bottom),
            bar.right.coerceIn(screen.left, screen.right),
            bar.bottom.coerceIn(screen.top, screen.bottom),
        )
        return listOf(
            // 上方
            Rect(screen.left, screen.top, screen.right, hole.top),
            // 下方
            Rect(screen.left, hole.bottom, screen.right, screen.bottom),
            // 左侧（与控制条同高）
            Rect(screen.left, hole.top, hole.left, hole.bottom),
            // 右侧（与控制条同高）
            Rect(hole.right, hole.top, screen.right, hole.bottom),
        ).map { rect ->
            Rect(
                rect.left,
                rect.top,
                max(rect.left + 1, rect.right),
                max(rect.top + 1, rect.bottom),
            )
        }
    }

    private fun resolveWindowType(): Int {
        // 与原来一致：优先应用层悬浮窗（Flyme 会给无障碍浮层叠 80% alpha）；
        // 拿不到悬浮窗权限时回退无障碍浮层。
        val preferred = OverlayWindowTypes.fullscreenLauncherWindowType(hostContext)
        if (preferred == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) return preferred
        return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    }

    private fun buildParams(type: Int, rect: Rect): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            rect.width().coerceAtLeast(1),
            rect.height().coerceAtLeast(1),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = rect.left
            y = rect.top
        }

    private fun handleTouch(event: MotionEvent) {
        val action = event.actionMasked
        val x = event.rawX
        val y = event.rawY
        val time = event.eventTime
        if (action != MotionEvent.ACTION_MOVE) {
            Log.i(
                TAG,
                "touch action=${actionName(action)} (${x.toInt()},${y.toInt()}) device=${event.deviceId} flags=${event.flags}",
            )
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (capturing) {
                    Log.w(TAG, "上一次触摸没有 UP/CANCEL 就收到新的 DOWN，丢弃上一次")
                    listener.onCancel()
                }
                capturing = true
                downX = x
                downY = y
                downTimeMs = time
                sampleXs.clear()
                sampleYs.clear()
                sample(x, y, force = true)
                listener.onDown(x, y, time)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!capturing) return
                sample(x, y, force = false)
                listener.onMove(x, y, time)
            }

            MotionEvent.ACTION_UP -> {
                if (!capturing) return
                capturing = false
                sample(x, y, force = true)
                listener.onUp(x, y, time)
                requestForward { performForward(x, y, time) }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!capturing) return
                capturing = false
                sampleXs.clear()
                sampleYs.clear()
                listener.onCancel()
                Log.i(TAG, "触摸被系统取消（本次不记录、也不回放）")
            }
        }
        drainForwards()
    }

    private fun actionName(action: Int): String = when (action) {
        MotionEvent.ACTION_DOWN -> "DOWN"
        MotionEvent.ACTION_UP -> "UP"
        MotionEvent.ACTION_CANCEL -> "CANCEL"
        else -> action.toString()
    }

    private fun requestForward(task: () -> Unit) {
        pendingForwards.addLast(task)
        drainForwards()
    }

    private fun drainForwards() {
        if (capturing || injecting) return
        val task = pendingForwards.removeFirstOrNull() ?: return
        task()
    }

    private fun sample(x: Float, y: Float, force: Boolean) {
        if (sampleXs.isEmpty()) {
            sampleXs.add(x)
            sampleYs.add(y)
            return
        }
        val distance = hypot(x - sampleXs.last(), y - sampleYs.last())
        if (distance < MIN_DISTINCT_PX) return
        if (!force && distance < SAMPLE_MIN_DISTANCE_PX) return
        sampleXs.add(x)
        sampleYs.add(y)
    }

    /**
     * 把刚结束的手势注入回系统（否则触摸被本层吃掉，用户在目标 App 里点哪都没反应）。
     *
     * 位移 ≤ [TAP_SLOP_PX] 按"点击"处理（只有 moveTo 的单点），否则按"滑动"处理。
     */
    private fun performForward(upX: Float, upY: Float, upTimeMs: Long) {
        val service = SlideIndexAccessibilityService.accessibilityInstance()
        if (service == null) {
            Log.w(TAG, "无障碍服务不可用：无法回注，结束录制")
            onInjectionUnavailable()
            return
        }
        val pressMs = (upTimeMs - downTimeMs).coerceAtLeast(1L)
        val moved = hypot(upX - downX, upY - downY) > TAP_SLOP_PX
        val path = Path().apply { moveTo(downX, downY) }
        if (moved) {
            for (index in 1 until sampleXs.size) {
                path.lineTo(sampleXs[index], sampleYs[index])
            }
            path.lineTo(upX, upY)
        }
        val durationMs = if (moved) {
            pressMs.coerceIn(SWIPE_MIN_MS, SWIPE_MAX_MS)
        } else {
            pressMs.coerceIn(TAP_MIN_MS, TAP_MAX_MS)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()

        injecting = true
        val generation = ++injectionGeneration
        detachForInjection()

        mainHandler.postDelayed({
            if (generation != injectionGeneration) return@postDelayed
            val result = runCatching {
                service.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            Log.i(TAG, "注入完成 moved=$moved dur=${durationMs}ms points=${sampleXs.size}")
                            endInjection(generation)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            Log.w(TAG, "注入被取消 moved=$moved dur=${durationMs}ms")
                            endInjection(generation)
                        }
                    },
                    null,
                )
            }
            val accepted = result.getOrDefault(false)
            if (result.isFailure) {
                Log.w(TAG, "注入抛异常", result.exceptionOrNull())
            }
            Log.i(TAG, "注入受理 accepted=$accepted moved=$moved dur=${durationMs}ms points=${sampleXs.size}")
            if (!accepted) {
                injectionFailures++
                endInjection(generation)
                if (injectionFailures >= MAX_INJECTION_FAILURES) {
                    Log.w(TAG, "注入连续失败 $injectionFailures 次，结束录制以恢复触摸")
                    onInjectionUnavailable()
                }
            }
        }, INJECTION_APPLY_DELAY_MS)

        mainHandler.postDelayed(
            { endInjection(generation) },
            INJECTION_APPLY_DELAY_MS + durationMs + INJECTION_TIMEOUT_EXTRA_MS,
        )
    }

    private fun endInjection(generation: Int) {
        if (generation != injectionGeneration) return
        if (!injecting) return
        injecting = false
        reattachAfterInjection()
        drainForwards()
    }

    /** 注入期间把四块窗都摘掉：注入的事件不会落回我们自己。 */
    private fun detachForInjection() {
        if (!attached) return
        val manager = windowManager ?: return
        attached = false
        panes.forEach { pane -> runCatching { manager.removeView(pane.view) } }
        Log.i(TAG, "捕获层已摘除（准备注入）")
    }

    /** 注入结束后装回。四块窗都不覆盖控制条，所以装回顺序不再影响按钮点击。 */
    private fun reattachAfterInjection() {
        if (attached) return
        if (panes.isEmpty()) return
        val manager = windowManager ?: return
        attached = true
        var failed = false
        panes.forEach { pane ->
            runCatching { manager.addView(pane.view, pane.params) }
                .onFailure {
                    Log.w(TAG, "装回捕获窗失败", it)
                    failed = true
                }
        }
        if (failed) attached = false
        Log.i(TAG, "捕获层已装回")
    }

    private companion object {
        const val TAG = "MacroTouchCapture"

        const val SAMPLE_MIN_DISTANCE_PX = 24f
        const val MIN_DISTINCT_PX = 1f
        const val TAP_SLOP_PX = 40f

        const val TAP_MIN_MS = 30L
        const val TAP_MAX_MS = 2_000L
        const val SWIPE_MIN_MS = 50L
        const val SWIPE_MAX_MS = 5_000L

        const val INJECTION_APPLY_DELAY_MS = 80L
        const val INJECTION_TIMEOUT_EXTRA_MS = 800L
        const val MAX_INJECTION_FAILURES = 2
    }
}
