package com.slideindex.app.perf

import android.os.SystemClock
import android.util.Log
import android.view.View
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 悬浮窗「谁在每帧重画」探针（仅 debug）。
 *
 * 背景：`dumpsys gfxinfo` 只能给出进程总帧率，`dumpsys SurfaceFlinger --timestats`
 * 在 Flyme 上又把无标题的悬浮窗图层归成 `none`，两者都指不出具体窗口。
 *
 * 做法：在每个悬浮窗的根 View 上挂 [android.view.ViewTreeObserver.OnDrawListener]，
 * 每绘制一次计一笔，按窗口标签聚合，定时打日志。配合
 * `adb shell am broadcast -a com.slideindex.app.DEBUG_SAMPLE` 触发采样。
 *
 * 约束：
 * - release 构建下 [attach] 与 [startDump] 都是空操作；
 * - 只计数，不改布局、不改绘制；
 * - 同一 View 只挂一次（弱引用集合，窗口销毁后自动回收）。
 */
object OverlayDrawProbe {

    private const val TAG = "OverlayDrawProbe"
    private const val INTERVAL_MS = 3_000L

    private val attached: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val dumping = AtomicBoolean(false)

    /** 给一个窗口根 View 打标签；重复调用无副作用。 */
    fun attach(view: View, label: String) {
        if (!PerfBuildFlags.debugBuild) return
        synchronized(attached) {
            if (!attached.add(view)) return
        }
        runCatching {
            view.viewTreeObserver.addOnDrawListener {
                counts.getOrPut(label) { AtomicInteger() }.incrementAndGet()
            }
        }.onFailure { Log.w(TAG, "attach failed: $label", it) }
    }

    /** 每 [INTERVAL_MS] 打一次各窗口出帧数，持续 [durationMs]。 */
    fun startDump(durationMs: Long) {
        if (!PerfBuildFlags.debugBuild) return
        if (!dumping.compareAndSet(false, true)) return
        Thread({
            try {
                val end = SystemClock.uptimeMillis() + durationMs
                while (SystemClock.uptimeMillis() < end) {
                    Thread.sleep(INTERVAL_MS)
                    dumpOnce()
                }
            } catch (_: InterruptedException) {
                // 结束采样
            } finally {
                dumping.set(false)
            }
        }, "OverlayDrawProbe").apply { isDaemon = true }.start()
    }

    private fun dumpOnce() {
        refreshWindows()
        if (counts.isEmpty()) {
            Log.i(TAG, "draws: (no window attached)")
            return
        }
        val snapshot = counts.mapValues { it.value.getAndSet(0) }
        val line = snapshot.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .joinToString(" ") { "${it.key}=${it.value}" }
        Log.i(TAG, "draws/${INTERVAL_MS / 1000}s: ${line.ifEmpty { "(idle)" }}")
    }

    /**
     * debug 专用：把进程里**所有**已挂到 WindowManager 的根 View 补上埋点。
     *
     * 只读 `WindowManagerGlobal.mViews`（debuggable 包允许反射 hidden API），
     * 失败静默——悬浮窗数量多、创建路径分散，逐个回调点埋点既啰嗦又容易漏。
     */
    private fun refreshWindows() {
        runCatching {
            val clazz = Class.forName("android.view.WindowManagerGlobal")
            val instance = clazz.getMethod("getInstance").invoke(null)
            val field = clazz.getDeclaredField("mViews").apply { isAccessible = true }
            val views = field.get(instance) as? List<*> ?: return@runCatching
            views.forEach { view ->
                if (view !is View) return@forEach
                val lp = view.layoutParams as? android.view.WindowManager.LayoutParams
                val label = buildString {
                    append(view.javaClass.simpleName)
                    if (lp != null) {
                        append('/').append(lp.width).append('x').append(lp.height)
                        if (!lp.title.isNullOrEmpty()) append(':').append(lp.title)
                    }
                    append('@').append(System.identityHashCode(view) % 1000)
                }
                attach(view, label)
            }
        }
    }
}
