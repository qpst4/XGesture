package com.slideindex.app.macro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.service.SlideIndexAccessibilityService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * 宏执行器。
 *
 * 行为要点：
 * 1. 先把目标 App 拉到前台（最多等 [FOREGROUND_WAIT_TIMEOUT_MS]，每 [FOREGROUND_POLL_INTERVAL_MS] 查一次），
 *    到前台后再等 [SETTLE_MS] 让界面稳定；
 * 2. 宏开头那段"等待"会被**启动耗时 + settle** 抵扣（见 [execute] 里的 budget），一旦执行过真正的点/滑就不再抵扣；
 * 3. 每一步开始前检查前台是否还是目标 App，不是就中止；
 * 4. 同一时刻只允许一个宏在跑（进程级互斥，见 [LOCK]）。
 *
 * 线程模型：整条流程跑在自己的工作线程上（里面会 sleep/等待手势），
 * 手势注入与 Toast 都 post 回主线程 —— 不阻塞主线程。
 */
class MacroEngine(private val context: Context) {

    /**
     * 启动一条宏。
     *
     * @return true 表示已受理（执行在工作线程上继续）；false 表示当场被拒（已给出提示）。
     */
    fun run(macro: Macro): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            toast(R.string.macro_run_requires_android_n)
            return false
        }
        synchronized(LOCK) {
            if (runningMacroId != null) {
                toast(R.string.macro_run_already_running)
                return false
            }
            runningMacroId = macro.id
        }
        Thread({
            // 回放时用触摸指示器把"宏正在点哪里/滑哪里"显示出来，用户才知道宏在做什么。
            val indicator = MacroTouchIndicator(context)
            try {
                execute(macro, indicator)
            } catch (error: Exception) {
                // 执行期的意外（例如 startActivity 被拒）不该把线程崩掉，也不该卡住互斥锁。
                Log.w(TAG, "宏执行中断：${macro.name}", error)
            } finally {
                indicator.hide()
                synchronized(LOCK) {
                    if (runningMacroId == macro.id) runningMacroId = null
                }
            }
        }, "macro-runner").start()
        return true
    }

    private fun execute(macro: Macro, indicator: MacroTouchIndicator) {
        val service = SlideIndexAccessibilityService.accessibilityInstance()
        if (service == null) {
            toast(R.string.macro_run_service_unavailable)
            return
        }

        // 全局宏（反馈 #394）：不绑定 App，不拉起、不做前台校验，直接在当前界面回放。
        val global = macro.isGlobal
        val targetPackage = if (global) "" else macro.targetPackage.let {
            // 目标可能是分身：`包名\u0002userId`，需要跨 user 启动；旧数据裸包名 = user 0。
            com.slideindex.app.launcher.AppTarget.parse(it).packageName
        }
        var waitedMs = 0L
        if (!global) {
            // 目标可能是分身：`包名\u0002userId`，需要跨 user 启动；旧数据裸包名 = user 0。
            val target = com.slideindex.app.launcher.AppTarget.parse(macro.targetPackage)
            if (target.isClone) {
                if (!com.slideindex.app.util.CloneAppLauncher.launch(context, target.packageName, target.cloneUserId)) {
                    toast(R.string.macro_run_target_not_found)
                    return
                }
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
                if (launchIntent == null) {
                    toast(R.string.macro_run_target_not_found)
                    return
                }
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(launchIntent) }
                    .onFailure { Log.w(TAG, "无法启动 ${target.packageName}", it) }
            }

            val startedAt = SystemClock.uptimeMillis()
            var reachedForeground = false
            while (true) {
                waitedMs = SystemClock.uptimeMillis() - startedAt
                if (waitedMs >= FOREGROUND_WAIT_TIMEOUT_MS) break
                if (currentForegroundPackage() == target.packageName) {
                    reachedForeground = true
                    break
                }
                Thread.sleep(FOREGROUND_POLL_INTERVAL_MS)
            }
            if (!reachedForeground) {
                toast(R.string.macro_run_app_timeout)
                return
            }

            Thread.sleep(SETTLE_MS)
        }

        // 回放期间挂起自家的边缘浮层/触钮：全局宏跨界面回放时，它们会盖住点击点。
        var overlaysSuspended = false
        if (global) {
            runCatching { SlideIndexAccessibilityService.suspendAllEdgeOverlays() }
            overlaysSuspended = true
        }
        try {
            runSteps(macro, global, targetPackage, service, indicator, waitedMs)
        } finally {
            if (overlaysSuspended) {
                runCatching { SlideIndexAccessibilityService.resumeAllEdgeOverlays() }
            }
        }
    }

    private fun runSteps(
        macro: Macro,
        global: Boolean,
        targetPackage: String,
        service: AccessibilityService,
        indicator: MacroTouchIndicator,
        waitedMs: Long,
    ) {

        // 等待步的实际时长：开头那段会被"启动耗时 + settle"抵扣。
        // 这段数学在 MacroPlanner 里（有单测），这里只负责睡。
        val waitDurations = MacroPlanner.plannedWaitDurations(macro.steps, waitedMs + SETTLE_MS)
        val screen = realScreenSize()
        val screenWidth = screen.x.toFloat()
        val screenHeight = screen.y.toFloat()

        macro.steps.forEachIndexed { index, step ->
            val foreground = currentForegroundPackage()
            Log.i(
                TAG,
                "step[$index/${macro.steps.size}] ${step.javaClass.simpleName} 开始 foreground=$foreground target=$targetPackage",
            )
            // 全局宏不校验前台（跨界面回放正是它的用途）；应用内宏仍然离开就中止。
            if (!global && foreground != null && foreground != targetPackage) {
                toast(R.string.macro_run_foreground_changed)
                return
            }

            val ok = when (step) {
                is MacroStep.Wait -> {
                    val sleepMs = waitDurations[index]
                    if (sleepMs > 0L) Thread.sleep(sleepMs)
                    true
                }

                is MacroStep.Tap -> {
                    val (x, y) = MacroPlanner.toPixels(step.x, step.y, screenWidth, screenHeight)
                    val path = Path().apply { moveTo(x, y) }
                    indicator.pulseAt(x, y)
                    val dispatched = dispatchPath(service, path, MacroLimits.clampTap(step.durationMs))
                    indicator.hideAfter(INDICATOR_LINGER_MS)
                    dispatched
                }

                is MacroStep.Swipe -> {
                    // 首点是"按下点"，已经由 moveTo 覆盖；其余点依次连线（末点即抬起点）。
                    val points = MacroPlanner.swipePixelPath(step, screenWidth, screenHeight)
                    val path = Path().apply {
                        points.forEachIndexed { pointIndex, point ->
                            if (pointIndex == 0) moveTo(point.first, point.second)
                            else lineTo(point.first, point.second)
                        }
                    }
                    val durationMs = MacroLimits.clampSwipe(step.durationMs)
                    // 只在手势开始前落一次提示，**绝不在手势进行中改动窗口**（会打断注入的手势）。
                    points.firstOrNull()?.let { indicator.pulseAt(it.first, it.second) }
                    val dispatched = dispatchPath(service, path, durationMs)
                    // 手势结束后再标出终点，短暂停留后淡出（不能一直停在终点）。
                    points.lastOrNull()?.let {
                        indicator.pulseAt(it.first, it.second)
                        indicator.hideAfter(INDICATOR_LINGER_MS)
                    }
                    dispatched
                }
                is MacroStep.Key -> performSystemKey(service, step.key)
            }

            if (!ok) {
                Log.w(TAG, "step[$index] 注入失败，宏停止")
                toast(R.string.macro_run_step_failed, stepLabel(step))
                return
            }
        }

        Log.i(TAG, "宏执行完成：${macro.name}")
        toast(R.string.macro_run_completed, macro.name)
    }

    /**
     * 执行一步系统按键（反馈 #4）。
     *
     * 用无障碍的 `performGlobalAction`：不需要 root / Shizuku，也不依赖输入法壳，
     * 是"回桌面 / 切最近任务 / 返回"最稳的一条路。
     */
    private fun performSystemKey(service: AccessibilityService, key: MacroStep.SystemKey): Boolean {
        val globalAction = when (key) {
            MacroStep.SystemKey.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            MacroStep.SystemKey.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            MacroStep.SystemKey.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
        }
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        mainHandler.post {
            ok.set(runCatching { service.performGlobalAction(globalAction) }.getOrDefault(false))
            latch.countDown()
        }
        latch.await(KEY_ACTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        android.util.Log.i(
            "MacroEngine",
            "system key: key=$key globalAction=$globalAction ok=${ok.get()}",
        )
        return ok.get()
    }

    /** 注入一条手势路径并等它结束；取消或超时都算失败。 */
    private fun dispatchPath(service: AccessibilityService, path: Path, durationMs: Long): Boolean {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()

        val finished = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        mainHandler.post {
            val accepted = runCatching {
                service.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            finished.set(true)
                            latch.countDown()
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            finished.set(false)
                            latch.countDown()
                        }
                    },
                    null,
                )
            }.getOrDefault(false)
            if (!accepted) latch.countDown()
        }
        latch.await(durationMs + GESTURE_WAIT_EXTRA_MS, TimeUnit.MILLISECONDS)
        return finished.get()
    }

    /** 比例 → 真实像素（用真实分辨率，与录制端同一基准）。 */
    private fun realScreenSize(): Point {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        manager?.defaultDisplay?.getRealMetrics(metrics)
        return Point(max(1, metrics.widthPixels), max(1, metrics.heightPixels))
    }

    /**
     * 当前前台包名。
     *
     * 优先用无障碍根节点的包名（同步、即时）；根节点拿不到时退回 App 自己的前台跟踪缓存，
     * 再退回上一次成功读到的值 —— 这样"切走了没"不会因为一次读不到而误判。
     */
    private fun currentForegroundPackage(): String? {
        val fromWindow = runCatching {
            SlideIndexAccessibilityService.accessibilityInstance()
                ?.rootInActiveWindow
                ?.packageName
                ?.toString()
        }.getOrNull()
        // 我们自己的浮层（指示器 / 面板）有时会成为"活动窗口"，那不是"用户切走了别的 App"。
        if (!fromWindow.isNullOrBlank() && fromWindow != context.packageName) {
            lastKnownForeground = fromWindow
            return fromWindow
        }
        val tracked = SlideIndexAccessibilityService.currentForegroundPackageName()
        if (!tracked.isNullOrBlank() && tracked != context.packageName) {
            lastKnownForeground = tracked
            return tracked
        }
        return lastKnownForeground
    }

    private fun stepLabel(step: MacroStep): String = context.getString(
        when (step) {
            is MacroStep.Wait -> R.string.macro_step_wait
            is MacroStep.Tap -> R.string.macro_step_tap
            is MacroStep.Swipe -> R.string.macro_step_swipe
            is MacroStep.Key -> R.string.macro_step_key
        },
    )

    private fun toast(resId: Int, vararg args: Any) {
        val text = context.getString(resId, *args)
        mainHandler.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val TAG = "MacroEngine"

        /** 等目标 App 到前台的轮询间隔。 */
        const val FOREGROUND_POLL_INTERVAL_MS = 100L

        /** 等目标 App 到前台的总上限。 */
        const val FOREGROUND_WAIT_TIMEOUT_MS = 4_500L

        /** 到前台后再等这么久，让首屏动画/加载稳定。 */
        const val SETTLE_MS = 250L

        /** 手势回调的额外等待余量（超时按失败处理）。 */
        const val GESTURE_WAIT_EXTRA_MS = 1_500L

        /** 触摸指示器在步骤结束后停留的时长。 */
        private const val INDICATOR_LINGER_MS = 320L

    /** 系统按键步骤的执行上限（performGlobalAction 是异步的）。 */
    private const val KEY_ACTION_TIMEOUT_MS = 800L

        private val LOCK = Any()
        private val mainHandler = Handler(Looper.getMainLooper())

        @Volatile
        private var runningMacroId: String? = null

        @Volatile
        private var lastKnownForeground: String? = null

        /** 是否有宏正在执行。 */
        val isRunning: Boolean
            get() = runningMacroId != null
    }
}
