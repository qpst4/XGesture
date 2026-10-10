package com.slideindex.app.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.slideindex.app.BuildConfig
import kotlin.math.roundToInt

/**
 * 输入法顶缘探测：**只允许纯读**。
 *
 * ## 为什么删掉了 `InputMethodManager.isAcceptingText()` 兜底（2026-10-10 真机定位）
 *
 * 它看着像"读"，实际是"写"：AOSP 里
 * `isAcceptingText() { checkFocus(); return mServedInputConnection != null; }`，
 * 而 `checkFocus()` → `startInputOnWindowFocusGainInternal(StartInputReason.CHECK_FOCUS, …)`
 * 会主动向 IMMS 申请一次输入会话；同时它读的是**本进程**是否持有输入连接，
 * 而"通知栏内联回复 + 键盘"这个场景里 IME 焦点属于 SystemUI，本进程本来就不该持有。
 *
 * 后果是一次自激振荡（logcat 实测：魅族 21 / Android 16 / Flyme 12.6.0.0A，系统通知栏回复框）：
 * 相邻两次窗口事件里本方法 null / 非 null 交替 → 协调器 `HIDE` 与 `SHOW_IME` 交替（6 秒各 18 次）
 * → 剪贴板浮窗"增删"本身又产生新的 `TYPE_WINDOWS_CHANGED` → 进入下一轮。
 *
 * 因此本文件**永久禁止**再引入会碰输入法/焦点状态的 API
 * （`isAcceptingText` / `isActive` / `showSoftInput` / `toggleSoftInput` …）。
 * 取不到就返回 null，由调用方按"信号不稳就不动作"处理（见 [ImeSignalStabilizer]）。
 *
 * 诊断期例外：`ImeDiagnostics` 会额外记录 `isAcceptingText()` 的返回值用于对照，
 * 那是**只在诊断构建（`-PimeDiag=true`）里发生**的额外扰动，正式包里不执行。
 */
object ImeBoundsDetector {

    private const val MIN_IME_HEIGHT_DP = 48

    /** 一次探测里"IME 顶缘是从哪来的"。诊断日志靠它区分"窗口表可用"与"只能靠兜底读数"。 */
    internal enum class Source { ACCESSIBILITY_WINDOW, WINDOW_METRICS, NONE }

    /**
     * 一次探测的完整结果。
     *
     * @param bounds IME 顶缘；null = 判断为"键盘不可见 / 取不到"。
     * @param applicationWindowPresent 屏幕上是否存在 `TYPE_APPLICATION` 窗口（诊断用：
     *   通知栏/锁屏里通常没有，应用里通常有）。
     * @param source 见 [Source]。
     * @param rawImeInsetBottom [Source.WINDOW_METRICS] 分支读到的原始 IME inset 高度（px）；
     *   未走该分支时为 -1。真机实测这条读数**会在键盘已经收起后仍然活着**（配合 [Source] 可判定）。
     */
    internal data class ImeProbe(
        val bounds: Rect?,
        val applicationWindowPresent: Boolean,
        val source: Source,
        val rawImeInsetBottom: Int,
    )

    fun detectImeBounds(service: AccessibilityService): Rect? = detectImeProbe(service).bounds

    /** 单次遍历无障碍窗口表，同时拿到 IME 顶缘与"有没有应用窗口"。 */
    internal fun detectImeProbe(service: AccessibilityService): ImeProbe =
        com.slideindex.app.perf.PerfProbe.probe("IME.detectImeBounds") {
            val minHeightPx =
                (MIN_IME_HEIGHT_DP * service.resources.displayMetrics.density).roundToInt()
            var best: Rect? = null
            var bestHeight = 0
            var applicationWindowPresent = false
            for (window in service.windows) {
                when (window.type) {
                    AccessibilityWindowInfo.TYPE_APPLICATION -> applicationWindowPresent = true
                    AccessibilityWindowInfo.TYPE_INPUT_METHOD -> {
                        val bounds = Rect()
                        window.getBoundsInScreen(bounds)
                        if (bounds.isEmpty) continue
                        if (bounds.height() < minHeightPx) continue
                        if (bounds.height() > bestHeight) {
                            best = bounds
                            bestHeight = bounds.height()
                        }
                    }
                }
            }
            if (best != null) {
                return@probe ImeProbe(
                    bounds = best,
                    applicationWindowPresent = applicationWindowPresent,
                    source = Source.ACCESSIBILITY_WINDOW,
                    rawImeInsetBottom = -1,
                )
            }
            val fallback = detectFromWindowMetrics(service)
            ImeProbe(
                bounds = fallback.bounds,
                applicationWindowPresent = applicationWindowPresent,
                source = if (fallback.bounds != null) Source.WINDOW_METRICS else Source.NONE,
                rawImeInsetBottom = fallback.rawInsetBottom,
            )
        }

    private class MetricsProbe(val bounds: Rect?, val rawInsetBottom: Int)

    /**
     * 窗口度量里的 IME inset：纯读，不碰焦点。多数 ROM 下无障碍窗口表**根本不含 IME 窗口**
     * （实测 Flyme 通知栏：13 个无障碍窗口 = 12 个我们自己的 ACCESSIBILITY_OVERLAY + 1 个通知栏），
     * 这条是主来源。与 `ClipboardOverlayWindow` 读 `host.currentWindowMetrics.windowInsets` 同一口径。
     *
     * ⚠️ 只信 inset，**不要**拿 `currentWindowMetrics.bounds` 当屏幕尺寸：
     * 在 WindowContext 上它于部分 ROM（实测 Flyme）给的是窗口/最小应用边界而不是真实屏幕
     * （见 `EdgeGestureLayoutCoordinator` 的注释）。这里底部基准一律取 `displayMetrics.heightPixels`。
     */
    private fun detectFromWindowMetrics(context: Context): MetricsProbe {
        val wm = context.getSystemService(WindowManager::class.java)
            ?: return MetricsProbe(null, -1)
        val insets = runCatching { wm.currentWindowMetrics.windowInsets }.getOrNull()
            ?: return MetricsProbe(null, -1)
        val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
        val metrics = context.resources.displayMetrics
        val minHeightPx = (MIN_IME_HEIGHT_DP * metrics.density).roundToInt()
        if (imeBottom < minHeightPx) return MetricsProbe(null, imeBottom)
        return MetricsProbe(
            bounds = Rect(0, metrics.heightPixels - imeBottom, metrics.widthPixels, metrics.heightPixels),
            rawInsetBottom = imeBottom,
        )
    }
}

/**
 * 输入法相关诊断日志，**默认关闭**；诊断包用 `-PimeDiag=true` 打开（见 `IME_DIAG`）。
 *
 * 目的：让"我们的判断"和"输入法真实状态"能落在同一条时间线上。应用侧拿不到
 * `imeLayeringTarget`（没有 API），所以配对方式是：
 *
 * - 本日志给出：两个探测来源的原始读数、我们提交的可见性、我们 show/hide 的决定、焦点节点是否可编辑；
 * - 现场用 `adb shell dumpsys window | grep imeLayeringTarget` 与
 *   `dumpsys input_method | grep -E "mInputShown|mImeWindowVis"`（或一份错误报告）给出系统真值。
 *
 * 工程约束：**只在内容变化或满 [REPEAT_MS] 时输出**——每次事件都打会改变时序与复现率，
 * 测出来的东西就不算数了。
 */
internal object ImeDiagnostics {

    private const val TAG = "ImeDiag"
    private const val REPEAT_MS = 1000L

    /** 诊断包（`-PimeDiag=true`）为 true；正式包 false，等于零开销。 */
    val enabled: Boolean = BuildConfig.IME_DIAG

    private var lastLine: String? = null
    private var lastAtMs = 0L

    fun log(source: String, line: String, nowMs: Long = SystemClock.uptimeMillis()) {
        if (!enabled) return
        if (line == lastLine && nowMs - lastAtMs < REPEAT_MS) return
        lastLine = line
        lastAtMs = nowMs
        Log.i(TAG, "[$source] $line")
    }

    /**
     * 焦点节点是否"应用里的可编辑输入框"——比"键盘可见"更语义化的候选信号。
     * 当前只观测、不参与决策（先拿到数据再改行为）。
     */
    fun focusedNodeDescription(service: AccessibilityService?): String {
        if (!enabled || service == null) return "focus=?"
        val node = runCatching {
            service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }.getOrNull() ?: return "focus=null"
        return try {
            "focus=${node.packageName}/${node.className} editable=${node.isEditable}"
        } finally {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }
    }

    /**
     * 旧口径 `InputMethodManager.isAcceptingText()` 的读数——**只诊断用**。
     *
     * ⚠️ 它带副作用（内部 `checkFocus()` 会向 IMMS 申请一次输入会话），正式包里**绝不调用**；
     * 诊断包里调它是为了和历史现象做对照（这条读数曾在通知栏场景里真/假交替，正是自激振荡的来源）。
     */
    fun acceptingTextProbe(service: AccessibilityService?): String {
        if (!enabled || service == null) return "acceptingText=?"
        val imm = service.getSystemService(InputMethodManager::class.java)
            ?: return "acceptingText=?"
        return "acceptingText=" + runCatching { imm.isAcceptingText() }.getOrDefault(false)
    }
}
