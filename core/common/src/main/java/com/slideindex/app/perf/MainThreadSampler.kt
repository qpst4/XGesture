package com.slideindex.app.perf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 主线程栈采样器（仅 debug）。
 *
 * 背景：`dumpsys SurfaceFlinger --timestats` 在 Flyme 上认不出无标题的悬浮窗图层
 * （全部归成 `none`），指不出是哪个窗口在持续重绘。这里改从进程内部反向采样：
 * 后台线程每 [INTERVAL_MS] 取一次主线程栈，按「栈顶帧」和「首个应用帧」两个口径
 * 统计占比，采 [DURATION_MS] 后打日志。
 *
 * 用法（debug 包）：
 * ```
 * adb shell am broadcast -a com.slideindex.app.DEBUG_SAMPLE
 * adb logcat -s MainThreadSampler
 * ```
 *
 * 约束：
 * - release 构建下 [install] 与 [start] 都是空操作；
 * - 只读栈，不改任何行为、不写盘、不上报；
 * - 同一时刻只允许一次采样，重复广播会被忽略。
 */
object MainThreadSampler {

    private const val TAG = "MainThreadSampler"
    private const val ACTION = "com.slideindex.app.DEBUG_SAMPLE"
    private const val INTERVAL_MS = 10L
    private const val DURATION_MS = 15_000L
    private const val TOP_N = 12

    private val running = AtomicBoolean(false)
    private val installed = AtomicBoolean(false)

    /** 在常驻进程里装一次广播接收器，之后靠 adb 广播按需触发。 */
    fun install(context: Context) {
        if (!PerfBuildFlags.debugBuild) return
        if (!installed.compareAndSet(false, true)) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                start("broadcast")
                OverlayDrawProbe.startDump(DURATION_MS)
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                IntentFilter(ACTION),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.onFailure { Log.w(TAG, "install failed", it) }
    }

    /** 采一段主线程栈，异步执行；已在采样时直接返回。 */
    fun start(reason: String) {
        if (!PerfBuildFlags.debugBuild) return
        if (!running.compareAndSet(false, true)) return
        Thread({ sample(reason) }, "MainThreadSampler").apply {
            isDaemon = true
            start()
        }
    }

    private fun sample(reason: String) {
        try {
            val main = Looper.getMainLooper()?.thread ?: return
            val topFrames = HashMap<String, Int>()
            val appFrames = HashMap<String, Int>()
            var samples = 0
            val deadline = SystemClock.uptimeMillis() + DURATION_MS
            while (SystemClock.uptimeMillis() < deadline) {
                val stack = runCatching { main.stackTrace }.getOrNull()
                if (!stack.isNullOrEmpty()) {
                    samples++
                    count(topFrames, key(stack[0]))
                    val app = stack.firstOrNull { it.className.startsWith("com.slideindex.") }
                    if (app != null) count(appFrames, key(app))
                }
                Thread.sleep(INTERVAL_MS)
            }
            Log.i(TAG, "reason=$reason samples=$samples duration=${DURATION_MS}ms")
            dump("top", topFrames, samples)
            dump("app", appFrames, samples)
        } catch (_: InterruptedException) {
            // 采样被中断，直接结束
        } finally {
            running.set(false)
        }
    }

    private fun key(frame: StackTraceElement): String =
        "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"

    private fun count(map: HashMap<String, Int>, key: String) {
        map[key] = (map[key] ?: 0) + 1
    }

    private fun dump(label: String, map: Map<String, Int>, total: Int) {
        if (total <= 0) return
        map.entries
            .sortedByDescending { it.value }
            .take(TOP_N)
            .forEach { (key, hits) ->
                val pct = String.format(Locale.US, "%5.1f", hits * 100f / total)
                Log.i(TAG, "  [$label] $pct% ($hits/$total) $key")
            }
    }
}
