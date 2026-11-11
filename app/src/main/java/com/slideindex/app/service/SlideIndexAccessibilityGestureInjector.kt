package com.slideindex.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.gesture.PointerSwipeDirection
import com.slideindex.app.overlay.AccessibilityLink
import com.slideindex.app.overlay.FloatBallStashPanel
import com.slideindex.app.overlay.GlobalOverlayDismissHelper
import com.slideindex.app.overlay.WidgetPickerOverlayWindow
import com.slideindex.app.overlay.WidgetPopupOverlayWindow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object SlideIndexAccessibilityGestureInjector {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingScrollRunnable: Runnable? = null

    fun perform(action: GestureAction, serviceProvider: () -> SlideIndexAccessibilityService?): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = CountDownLatch(1)
            mainHandler.post {
                result = performOnMain(action, serviceProvider)
                latch.countDown()
            }
            runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
            return result
        }
        return performOnMain(action, serviceProvider)
    }

    private fun performOnMain(
        action: GestureAction,
        serviceProvider: () -> SlideIndexAccessibilityService?
    ): Boolean {
        val service = serviceProvider()
        if (service == null) {
            Log.w(TAG, "perform($action): accessibility service not connected")
            return false
        }
        val result = when (action) {
            GestureAction.Back -> {
                // §0.16.22 / §0.16.24：**先看收纳面板**（闪念 / 剪贴板侧栏）。
                //
                // 为什么必须由这里插一手：面板是独立 overlay 窗，它自己的按键返回处理在真机上
                // **收不到事件**（实测：面板开着按系统 BACK，窗口焦点不变、面板纹丝不动；
                // 而"点面板外遮罩"能关，说明面板的 dismiss 本身是好的）。手势返回（悬浮球 / 触钮）
                // **都**汇到本漏斗，所以这里给面板一条明确入口，不必再指望"系统返回能落到面板窗上"。
                //
                // ⚠️ **只调入口、不判定**：`requestBackIfShowing()` 把这次返回交给面板**自己的返回链**，
                // 那条链里的层级判定**全工程只有一份**（注册表见 `HistoryPanelScreen` 的 KDoc）：
                //   ① 输入法弹着 → 只收键盘（面板/弹窗/展开态都不动）
                //   ② 否则面板里的子层（提醒选择器 / 标签管理 / 编辑条 / 记一条弹窗 / 展开的卡片）
                //      → 只关最上面那一层
                //   ③ 否则窗口输入态 → 交回浏览态
                //   ④ 否则面板本身 → 收起
                // 面板没显示 → 返回 false，落到下面的分支（`GLOBAL_ACTION_BACK`，不吞别人的返回）。
                //
                // ⛔ **不要在这里自己判 IME、判子层、也不要自己 `FloatBallStashPanel.dismiss()`**：
                // 那等于把层级再抄一份，抄漏了就会出现"面板里弹着键盘/卡片展开着，返回却把整个面板关了"
                // （§0.16.22 的真机回归）。
                when {
                    // §0.16.25：小组件面板浮窗 + 「添加小组件」浮窗页面也走手势返回链路。
                    // 它们过去不在这里，靠"系统注入 KEYCODE_BACK"来关；而那个注入在预测性返回开着的机器上
                    // 会被 SelfInjectedBackGuard 当回声消费掉（见该类 KDoc），于是"手势返回没反应"。
                    // 顺序：添加页在最上层，先问它；再问小组件面板本体。
                    // ⚠️ 这里**只做路由**、不复制键盘优先/子层判定：各窗自己的返回链
                    // （OverlayViewBackHandler / dismissFromBack）仍然是唯一漏斗。
                    WidgetPickerOverlayWindow.isShowing -> {
                        // 走它自己的返回漏斗（键盘优先在内），不要在这里直接关页面。
                        WidgetPickerOverlayWindow.requestBack()
                    }
                    WidgetPopupOverlayWindow.requestBackIfVisible() -> true
                    FloatBallStashPanel.requestBackIfShowing() -> true
                    ClipboardFloatService.isExpandedShowing() && !ClipboardFloatService.isPinned() ->
                        ClipboardFloatService.closeFromBack()
                    else -> {
                        // §0.16.24 诊断：面板那一整套层级（含第 0 档键盘优先）**没有任何一档认领**这一次
                        // 返回 → 交回系统。这是唯一打 `consumed=false` 的地方：真机上看到它，就说明这次
                        // 返回漏给了下层 App（面板当时没显示），不是被面板吞掉了。
                        Log.i(BACK_DECISION_TAG, "back decision consumed=false branch=fallthrough")
                        // §0.16.25：这里**故意不登记"自注入返回"**。
                        // 曾经登记过（SelfInjectedBackGuard），想拦"系统把这次返回又注回来"的回环；但真机日志与
                        // 崩溃栈证明那个回环发生在系统内部（`ViewRootImpl` 的兼容回调自己递归注入），
                        // **根本没有跑回 app 的代码**，所以闸门拦不住它；反而把"系统注入键送达到浮窗"
                        // 这条正常路吃掉了，导致小组件面板/添加页等浮窗"手势返回没反应"。
                        // 拦不住的敌人、却误伤自己人 —— 因此撤掉闸门，改为把这些浮窗**直接接进手势返回链路**
                        // （见上面 when 里的路由），让返回不再依赖系统回声。
                        performGlobalActionTracked(
                            service,
                            AccessibilityService.GLOBAL_ACTION_BACK,
                            "Back",
                        )
                    }
                }
            }
            GestureAction.Home, GestureAction.Recents -> {
                // 面板都是独立 overlay 窗，Home / Recents 不会顺带关掉它们：桌面会被面板盖住，
                // 而且面板还占着焦点（现状是"还得再点一次返回"）。
                // 所有自有触发源（悬浮球 / 触钮 / 轮盘 / 摇一摇 / 屏幕控制条 / 外部调用）都汇到本漏斗，
                // 所以在这里先清场、再执行系统动作，等于一次覆盖全部入口。
                GlobalOverlayDismissHelper.dismissContentPanelsForSystemNavigation()
                performGlobalActionTracked(
                    service,
                    if (action == GestureAction.Home) {
                        AccessibilityService.GLOBAL_ACTION_HOME
                    } else {
                        AccessibilityService.GLOBAL_ACTION_RECENTS
                    },
                    if (action == GestureAction.Home) "Home" else "Recents",
                )
            }
            GestureAction.OpenNotifications ->
                performGlobalActionTracked(
                    service,
                    AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
                    "OpenNotifications",
                )
            GestureAction.OpenQuickSettings ->
                performGlobalActionTracked(
                    service,
                    AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
                    "OpenQuickSettings",
                )
            GestureAction.LockScreen ->
                performGlobalActionTracked(
                    service,
                    AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
                    "LockScreen",
                )
            GestureAction.Screenshot -> {
                service.takeScreenshotDelayed()
                true
            }
            GestureAction.PowerMenu ->
                performGlobalActionTracked(
                    service,
                    AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
                    "PowerMenu",
                )
            GestureAction.PreviousApp -> service.launchPreviousApp()
            GestureAction.KeepScreenOn -> service.toggleKeepScreenOn()
            GestureAction.ScrollToTop -> scheduleFastVerticalScroll(service, toTop = true)
            GestureAction.ScrollToBottom -> scheduleFastVerticalScroll(service, toTop = false)
            else -> false
        }
        if (result) {
            Log.i(TAG, "perform($action): ok")
        } else {
            Log.w(TAG, "perform($action): returned false")
        }
        return result
    }

    /**
     * 全局动作的统一出口。
     *
     * 成功一律记成“链路可用”的正面证据。失败只记**弱证据**（[AccessibilityLink.noteSuspect]）：
     * `false` 只说明“这次动作没做成”，原因可能是旧机型根本没这个动作（截图 / 电源菜单 /
     * 快捷设置）、全屏视频里被系统拒绝、锁屏期间不可用 —— 把一次 false 当成“连接已断”，
     * 会让看门狗拆掉整批浮层，而重绑又需要 WRITE_SECURE_SETTINGS，用户只能手动开关一次。
     */
    private fun performGlobalActionTracked(
        service: SlideIndexAccessibilityService,
        action: Int,
        actionName: String,
    ): Boolean {
        val ok = service.performGlobalAction(action)
        if (ok) {
            AccessibilityLink.noteAlive("performGlobalAction($actionName)")
        } else {
            AccessibilityLink.noteSuspect("performGlobalAction($actionName)")
        }
        return ok
    }

    private fun scheduleFastVerticalScroll(service: SlideIndexAccessibilityService, toTop: Boolean): Boolean {
        pendingScrollRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingScrollRunnable = null
            service.fastVerticalScroll(toTop)
        }
        pendingScrollRunnable = runnable
        mainHandler.postDelayed(runnable, SCROLL_GESTURE_DELAY_MS)
        return true
    }

    fun dispatchPointerTap(
        service: AccessibilityService?,
        rawX: Float,
        rawY: Float,
        onFinished: (Boolean) -> Unit,
        preferNodeClick: Boolean = false
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dispatchPointerTap(service, rawX, rawY, onFinished, preferNodeClick) }
            return
        }
        if (service == null) {
            Log.w(TAG, "dispatchPointerTap($rawX, $rawY): service instance is null")
            onFinished(false)
            return
        }
        if (preferNodeClick) {
            val nodeOk = clickNodeAt(service, rawX, rawY)
            if (nodeOk) {
                Log.i(TAG, "dispatchPointerTap($rawX, $rawY): node click (overlay bypass)")
                onFinished(true)
                return
            }
            Log.i(TAG, "dispatchPointerTap($rawX, $rawY): node click failed, trying gesture")
        }
        val path = Path().apply {
            moveTo(rawX, rawY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, POINTER_TAP_DURATION_MS))
            .build()
        // QC-style: fire-and-forget when accepted — no wait for onCompleted.
        val accepted = service.dispatchGesture(gesture, null, null)
        if (accepted) {
            onFinished(true)
            return
        }
        Log.w(TAG, "dispatchPointerTap gesture rejected at ($rawX, $rawY)")
        onFinished(clickNodeAt(service, rawX, rawY))
    }

    fun dispatchTap(
        service: AccessibilityService?,
        rawX: Float,
        rawY: Float,
        onFinished: (Boolean) -> Unit,
        durationMs: Long = TAP_DURATION_MS
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dispatchTap(service, rawX, rawY, onFinished, durationMs) }
            return
        }
        if (service == null) {
            Log.w(TAG, "dispatchTap($rawX, $rawY): service instance is null")
            onFinished(false)
            return
        }
        Log.i(TAG, "dispatchTap start ($rawX, $rawY) duration=${durationMs}ms")
        dispatchGestureTap(service, rawX, rawY, durationMs) { gestureOk ->
            if (gestureOk) {
                Log.i(TAG, "dispatchTap($rawX, $rawY): gesture completed")
                onFinished(true)
                return@dispatchGestureTap
            }
            val nodeOk = clickNodeAt(service, rawX, rawY)
            if (nodeOk) {
                Log.i(TAG, "dispatchTap($rawX, $rawY): node ACTION_CLICK succeeded")
            } else {
                Log.w(TAG, "dispatchTap($rawX, $rawY): all paths failed")
            }
            onFinished(nodeOk)
        }
    }

    fun dispatchPointerSwipe(
        service: AccessibilityService?,
        startX: Float,
        startY: Float,
        config: PointerSwipeConfig,
        onFinished: (Boolean) -> Unit = {}
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dispatchPointerSwipe(service, startX, startY, config, onFinished) }
            return
        }
        if (service == null) {
            Log.w(TAG, "dispatchPointerSwipe: service instance is null")
            onFinished(false)
            return
        }
        val density = service.resources.displayMetrics.density
        val metrics = service.resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenHeight = metrics.heightPixels.toFloat().coerceAtLeast(1f)
        val (dx, dy) = config.delta(density)
        val endX = startX + dx
        val endY = startY + dy
        val durationMs = config.durationMs.toLong().coerceIn(20L, 800L)
        val pointerCount = config.pointerCount.coerceIn(1, 5)
        val sanitized = sanitizeSwipeEndpoints(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            minDistancePx = 24f * density
        )
        if (sanitized == null) {
            Log.w(TAG, "dispatchPointerSwipe: invalid path at ($startX, $startY)")
            val scrolled = scrollNodeAt(service, startX, startY, config.direction)
            onFinished(scrolled)
            return
        }
        val (safeStartX, safeStartY, safeEndX, safeEndY) = sanitized
        val builder = GestureDescription.Builder()
        for (index in 0 until pointerCount) {
            val spread = (index - (pointerCount - 1) / 2f) * 28f
            val offsetX = if (kotlin.math.abs(safeEndY - safeStartY) >= kotlin.math.abs(safeEndX - safeStartX)) {
                spread
            } else {
                0f
            }
            val offsetY = if (kotlin.math.abs(safeEndX - safeStartX) > kotlin.math.abs(safeEndY - safeStartY)) {
                spread
            } else {
                0f
            }
            val strokeEndpoints = sanitizeSwipeEndpoints(
                startX = safeStartX + offsetX,
                startY = safeStartY + offsetY,
                endX = safeEndX + offsetX,
                endY = safeEndY + offsetY,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                minDistancePx = 8f * density
            ) ?: continue
            val path = buildSwipePath(
                startX = strokeEndpoints.startX,
                startY = strokeEndpoints.startY,
                endX = strokeEndpoints.endX,
                endY = strokeEndpoints.endY
            )
            val stroke = runCatching {
                GestureDescription.StrokeDescription(path, 0, durationMs)
            }.getOrElse { error ->
                Log.w(TAG, "dispatchPointerSwipe: invalid stroke at ($safeStartX, $safeStartY)", error)
                null
            } ?: continue
            builder.addStroke(stroke)
        }
        val gesture = builder.build()
        if (gesture.strokeCount == 0) {
            Log.w(TAG, "dispatchPointerSwipe: no valid strokes at ($startX, $startY)")
            val scrolled = scrollNodeAt(service, startX, startY, config.direction)
            onFinished(scrolled)
            return
        }
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "dispatchPointerSwipe completed at ($startX, $startY)")
                    onFinished(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "dispatchPointerSwipe cancelled at ($startX, $startY)")
                    val scrolled = scrollNodeAt(service, startX, startY, config.direction)
                    onFinished(scrolled)
                }
            },
            null
        )
        if (!accepted) {
            Log.w(TAG, "dispatchPointerSwipe rejected at ($startX, $startY)")
            val scrolled = scrollNodeAt(service, startX, startY, config.direction)
            onFinished(scrolled)
        }
    }

    fun dispatchPointerHold(
        service: AccessibilityService?,
        rawX: Float,
        rawY: Float,
        durationMs: Long,
        onFinished: (Boolean) -> Unit = {}
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                dispatchPointerHold(service, rawX, rawY, durationMs, onFinished)
            }
            return
        }
        if (service == null) {
            Log.w(TAG, "dispatchPointerHold: service instance is null")
            onFinished(false)
            return
        }
        val safeDuration = durationMs.coerceIn(20L, MAX_HOLD_DURATION_MS)
        Log.i(TAG, "dispatchPointerHold at ($rawX, $rawY) duration=${safeDuration}ms")
        dispatchGestureTap(service, rawX, rawY, safeDuration, onFinished)
    }

    fun dispatchPointerSwipePath(
        service: AccessibilityService?,
        startX: Float,
        startY: Float,
        path: Path,
        durationMs: Long,
        maxDurationMs: Long = DEFAULT_SWIPE_MAX_DURATION_MS,
        onFinished: (Boolean) -> Unit = {}
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                dispatchPointerSwipePath(
                    service,
                    startX,
                    startY,
                    path,
                    durationMs,
                    maxDurationMs,
                    onFinished
                )
            }
            return
        }
        if (service == null) {
            Log.w(TAG, "dispatchPointerSwipePath: service instance is null")
            onFinished(false)
            return
        }
        val metrics = service.resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenHeight = metrics.heightPixels.toFloat().coerceAtLeast(1f)
        val clampedPath = buildClampedPath(path, screenWidth, screenHeight)
        if (clampedPath == null) {
            Log.w(TAG, "dispatchPointerSwipePath: empty path at ($startX, $startY)")
            onFinished(false)
            return
        }
        val safeDuration = durationMs.coerceIn(20L, maxDurationMs)
        val stroke = GestureDescription.StrokeDescription(clampedPath, 0, safeDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "dispatchPointerSwipePath completed at ($startX, $startY)")
                    onFinished(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "dispatchPointerSwipePath cancelled at ($startX, $startY)")
                    onFinished(false)
                }
            },
            null
        )
        if (!accepted) {
            Log.w(TAG, "dispatchPointerSwipePath rejected at ($startX, $startY)")
            onFinished(false)
        }
    }

    /**
     * 屏搜无惯性平滑拖动：滑动到终点后利用 continueStroke 在原地驻留 [holdDurationMs]，
     * 使抬手时的初速度降为 0，防止触发系统的 Fling 惯性过冲。
     */
    fun dispatchPointerDragNoFling(
        service: AccessibilityService?,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        dragDurationMs: Long = 420L,
        holdDurationMs: Long = 140L,
        onFinished: (Boolean) -> Unit = {},
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                dispatchPointerDragNoFling(
                    service,
                    startX,
                    startY,
                    endX,
                    endY,
                    dragDurationMs,
                    holdDurationMs,
                    onFinished,
                )
            }
            return
        }
        if (service == null) {
            Log.w(TAG, "dispatchPointerDragNoFling: service instance is null")
            onFinished(false)
            return
        }
        val metrics = service.resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenHeight = metrics.heightPixels.toFloat().coerceAtLeast(1f)
        val sx = startX.coerceIn(0f, screenWidth)
        val sy = startY.coerceIn(0f, screenHeight)
        val ex = endX.coerceIn(0f, screenWidth)
        val ey = endY.coerceIn(0f, screenHeight)
        val swipePath = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        val holdPath = Path().apply {
            moveTo(ex, ey)
            lineTo(ex, ey)
        }
        val stroke1 = GestureDescription.StrokeDescription(swipePath, 0L, dragDurationMs, true)
        val gesture1 = GestureDescription.Builder().addStroke(stroke1).build()
        val accepted = service.dispatchGesture(
            gesture1,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    try {
                        val stroke2 = stroke1.continueStroke(holdPath, 0L, holdDurationMs, false)
                        val gesture2 = GestureDescription.Builder().addStroke(stroke2).build()
                        val contAccepted = service.dispatchGesture(
                            gesture2,
                            object : AccessibilityService.GestureResultCallback() {
                                override fun onCompleted(gestureDescription: GestureDescription?) {
                                    Log.i(TAG, "dispatchPointerDragNoFling completed at ($ex, $ey)")
                                    onFinished(true)
                                }

                                override fun onCancelled(gestureDescription: GestureDescription?) {
                                    Log.w(TAG, "dispatchPointerDragNoFling hold cancelled")
                                    onFinished(true)
                                }
                            },
                            mainHandler,
                        )
                        if (!contAccepted) {
                            onFinished(true)
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "dispatchPointerDragNoFling continueStroke failed", e)
                        onFinished(true)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "dispatchPointerDragNoFling swipe cancelled")
                    onFinished(false)
                }
            },
            mainHandler,
        )
        if (!accepted) {
            Log.w(TAG, "dispatchPointerDragNoFling rejected at ($sx, $sy)")
            onFinished(false)
        }
    }

    private fun buildClampedPath(path: Path, screenWidth: Float, screenHeight: Float): Path? {
        val pathMeasure = android.graphics.PathMeasure(path, false)
        val length = pathMeasure.length
        if (length <= 0f) return null
        val result = Path()
        val pos = FloatArray(2)
        val samples = 24
        val step = length / samples
        for (index in 0..samples) {
            val distance = (index * step).coerceAtMost(length)
            if (!pathMeasure.getPosTan(distance, pos, null)) continue
            val x = pos[0].coerceIn(0f, screenWidth)
            val y = pos[1].coerceIn(0f, screenHeight)
            if (index == 0) {
                result.moveTo(x, y)
            } else {
                result.lineTo(x, y)
            }
        }
        return result
    }

    internal fun buildSwipePath(startX: Float, startY: Float, endX: Float, endY: Float): Path {
        val steps = 8
        return Path().apply {
            moveTo(startX, startY)
            for (step in 1..steps) {
                val t = step / steps.toFloat()
                lineTo(
                    startX + (endX - startX) * t,
                    startY + (endY - startY) * t
                )
            }
        }
    }

    internal data class SwipeEndpoints(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float
    )

    internal fun sanitizeSwipeEndpoints(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        screenWidth: Float,
        screenHeight: Float,
        minDistancePx: Float
    ): SwipeEndpoints? {
        if (!startX.isFinite() || !startY.isFinite() || !endX.isFinite() || !endY.isFinite()) {
            return null
        }
        val maxX = screenWidth.coerceAtLeast(1f)
        val maxY = screenHeight.coerceAtLeast(1f)
        var sx = startX.coerceIn(0f, maxX)
        var sy = startY.coerceIn(0f, maxY)
        var ex = endX.coerceIn(0f, maxX)
        var ey = endY.coerceIn(0f, maxY)
        var dx = ex - sx
        var dy = ey - sy
        var distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance < minDistancePx) {
            val rawDx = endX - startX
            val rawDy = endY - startY
            val rawLen = kotlin.math.hypot(rawDx.toDouble(), rawDy.toDouble()).toFloat()
            if (rawLen < 0.1f) return null
            val unitX = rawDx / rawLen
            val unitY = rawDy / rawLen
            ex = (sx + unitX * minDistancePx).coerceIn(0f, maxX)
            ey = (sy + unitY * minDistancePx).coerceIn(0f, maxY)
            dx = ex - sx
            dy = ey - sy
            distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        }
        if (distance < 1f) return null
        return SwipeEndpoints(sx, sy, ex, ey)
    }

    /**
     * 屏搜等场景：对坐标处可滚动容器执行 [ACTION_SCROLL_FORWARD]/[BACKWARD]，
     * 单步滚动、无 fling 惯性（对齐 GestureEVO 观感）。
     */
    fun performScrollableNodeAction(
        service: AccessibilityService?,
        rawX: Float,
        rawY: Float,
        direction: PointerSwipeDirection,
    ): Boolean {
        if (service == null) return false
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = CountDownLatch(1)
            mainHandler.post {
                result = scrollNodeAt(service, rawX, rawY, direction)
                latch.countDown()
            }
            latch.await(GESTURE_TIMEOUT_MS + 200, TimeUnit.MILLISECONDS)
            return result
        }
        return scrollNodeAt(service, rawX, rawY, direction)
    }

    private fun scrollNodeAt(
        service: AccessibilityService,
        rawX: Float,
        rawY: Float,
        direction: PointerSwipeDirection
    ): Boolean {
        val scrollAction = when (direction) {
            PointerSwipeDirection.UP -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            PointerSwipeDirection.DOWN -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            PointerSwipeDirection.LEFT,
            PointerSwipeDirection.RIGHT,
            -> return false
        }
        val root = rootForTap(service, rawX, rawY) ?: return false
        try {
            val target = findDeepestNodeAt(root, rawX, rawY) ?: return false
            try {
                var node: AccessibilityNodeInfo? = target
                while (node != null) {
                    if (node.isScrollable && node.isEnabled && node.isVisibleToUser &&
                        node.actionList.any { it.id == scrollAction }
                    ) {
                        if (node.performAction(scrollAction)) {
                            Log.i(TAG, "scrollNodeAt fallback succeeded direction=$direction")
                            return true
                        }
                    }
                    node = node.parent
                }
                return false
            } finally {
                releaseNode(target)
            }
        } finally {
            releaseNode(root)
        }
    }

    fun dispatchTapSync(service: AccessibilityService?, rawX: Float, rawY: Float): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e(TAG, "dispatchTapSync must not run on main thread (deadlock risk)")
            return false
        }
        var result = false
        val latch = CountDownLatch(1)
        dispatchTap(service, rawX, rawY, onFinished = { ok ->
            result = ok
            latch.countDown()
        })
        latch.await(GESTURE_TIMEOUT_MS + 200, TimeUnit.MILLISECONDS)
        return result
    }

    private fun dispatchGestureTap(
        service: AccessibilityService,
        rawX: Float,
        rawY: Float,
        durationMs: Long,
        onFinished: (Boolean) -> Unit
    ) {
        val path = Path().apply {
            moveTo(rawX, rawY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onFinished(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "dispatchGesture cancelled at ($rawX, $rawY)")
                    onFinished(false)
                }
            },
            null
        )
        if (!accepted) {
            Log.w(TAG, "dispatchGesture rejected at ($rawX, $rawY)")
            onFinished(false)
        }
    }

    private fun clickNodeAt(service: AccessibilityService, rawX: Float, rawY: Float): Boolean {
        val root = rootForTap(service, rawX, rawY) ?: return false
        try {
            val target = findDeepestNodeAt(root, rawX, rawY) ?: return false
            try {
                var node: AccessibilityNodeInfo? = target
                while (node != null) {
                    if (node.isEnabled && node.isVisibleToUser &&
                        (node.isClickable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
                    ) {
                        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            return true
                        }
                    }
                    node = node.parent
                }
                return false
            } finally {
                releaseNode(target)
            }
        } finally {
            releaseNode(root)
        }
    }

    private fun rootForTap(service: AccessibilityService, rawX: Float, rawY: Float): AccessibilityNodeInfo? {
        val px = rawX.toInt()
        val py = rawY.toInt()
        val selfPkg = service.packageName
        var fallback: AccessibilityNodeInfo? = null
        for (window in service.windows) {
            when (window.type) {
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
                AccessibilityWindowInfo.TYPE_INPUT_METHOD,
                -> continue
            }
            val root = window.root ?: continue
            val bounds = Rect()
            root.getBoundsInScreen(bounds)
            if (!bounds.contains(px, py)) continue
            val pkg = root.packageName?.toString()
            if (pkg == selfPkg) continue
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                return copyNode(root)
            }
            if (fallback == null) {
                fallback = copyNode(root)
            }
        }
        if (fallback != null) return fallback
        val active = service.rootInActiveWindow ?: return null
        if (active.packageName?.toString() == selfPkg) {
            releaseNode(active)
            return null
        }
        return active
    }

    private fun findDeepestNodeAt(
        root: AccessibilityNodeInfo,
        rawX: Float,
        rawY: Float
    ): AccessibilityNodeInfo? {
        val rect = Rect()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        val x = rawX.toInt()
        val y = rawY.toInt()
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val ownedChild = node !== root
            try {
                node.getBoundsInScreen(rect)
                if (rect.contains(x, y)) {
                    val area = rect.width() * rect.height()
                    if (area < bestArea) {
                        releaseNode(best)
                        best = copyNode(node)
                        bestArea = area
                    }
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { stack.add(it) }
                    }
                }
            } finally {
                if (ownedChild) releaseNode(node)
            }
        }
        return best
    }

    private fun copyNode(source: AccessibilityNodeInfo): AccessibilityNodeInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityNodeInfo(source)
        } else {
            @Suppress("DEPRECATION")
            AccessibilityNodeInfo.obtain(source)
        }

    private fun releaseNode(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        @Suppress("DEPRECATION")
        node.recycle()
    }

    const val TAP_DURATION_MS = 35L
    const val POINTER_TAP_DURATION_MS = 5L
    const val POINTER_TAP_CHAIN_GAP_MS = 12L
    const val DEFAULT_SWIPE_MAX_DURATION_MS = 800L
    const val MAX_HOLD_DURATION_MS = 5_000L
    const val MAX_RECORDED_GESTURE_DURATION_MS = 5_000L
    private const val TAG = "SlideIndexA11y"
    /**
     * §0.16.24：返回决策日志的 tag —— 沿用 `OverlayViewBackHandler` 那个 `OverlayBack`（**不新建 tag**），
     * 字符串与面板链其余档位逐字一致，`adb logcat -s OverlayBack` 才能把一次返回读成一行。
     *
     * 本文件只负责打**最后一档** `fallthrough`（`consumed=false`：走完整套层级都没命中、交回系统）；
     * 其余分支名见 `OverlaySidePanelHost` 里的常量注释。
     */
    private const val BACK_DECISION_TAG = "OverlayBack"
    private const val GESTURE_TIMEOUT_MS = 600L
    private const val SCROLL_GESTURE_DELAY_MS = 180L
}
