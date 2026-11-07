package com.slideindex.app.perf

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 主线程「同步跨进程调用」计时探针。
 *
 * 背景：Perfetto 能测出主线程卡在 `binder transaction` 上（60 秒里 6.56 秒、单次最长 79.9ms），
 * 但 trace 里拿不到对端服务名，只有 node id。因此改为在**我们自己的调用点**上计时，
 * 直接给出「哪个 API × 调了多少次 × 总耗时 × 最慢单次」。
 *
 * 约束：
 * - release 构建下 [enabled] 为 false，[probe] 直接执行原 lambda，零额外开销；
 * - 只做计时与聚合，不写盘、不上报、不采参数；
 * - 聚合用 [ConcurrentHashMap]，可从任意线程调用（当前只用主线程）。
 */
object PerfProbe {

    private const val TAG = "PerfProbe"

    /** debug 构建启用。与 EdgeDiag 同一套思路：正式包不要这个开销。 */
    val enabled: Boolean = PerfBuildFlags.debugBuild

    /** 单次超过这个阈值就单独打一行，便于对着拖动时间轴看。 */
    private const val SLOW_MS = 8L

    private class Stat {
        val count = AtomicLong()
        val totalNanos = AtomicLong()
        val maxNanos = AtomicLong()
        val slowCount = AtomicLong()
    }

    private val stats = ConcurrentHashMap<String, Stat>()

    private fun statOf(name: String): Stat = stats.getOrPut(name) { Stat() }

    /**
     * 给一次同步调用计时。
     *
     * @param name 探针名，建议 `类.方法` 形式
     * @param block 被测调用
     */
    inline fun <T> probe(name: String, block: () -> T): T {
        if (!enabled) return block()
        val start = SystemClock.elapsedRealtimeNanos()
        try {
            return block()
        } finally {
            record(name, SystemClock.elapsedRealtimeNanos() - start)
        }
    }

    /** [probe] 的 suspend 版本，供协程体内使用。 */
    suspend inline fun <T> probeSuspend(name: String, block: suspend () -> T): T {
        if (!enabled) return block()
        val start = SystemClock.elapsedRealtimeNanos()
        try {
            return block()
        } finally {
            record(name, SystemClock.elapsedRealtimeNanos() - start)
        }
    }

    /** 供 inline 的 [probe] 调用；非 inline 以减小调用点体积。 */
    @JvmStatic
    fun record(name: String, elapsedNanos: Long) {
        val s = statOf(name)
        s.count.incrementAndGet()
        s.totalNanos.addAndGet(elapsedNanos)
        // 记录峰值：CAS 循环
        var prev = s.maxNanos.get()
        while (elapsedNanos > prev) {
            if (s.maxNanos.compareAndSet(prev, elapsedNanos)) break
            prev = s.maxNanos.get()
        }
        val ms = elapsedNanos / 1_000_000
        if (ms >= SLOW_MS) {
            s.slowCount.incrementAndGet()
            Log.i(TAG, "SLOW $name ${elapsedNanos / 1000}us")
        }
        ensureAutoDump()
    }

    /** 导出聚合结果并清零；由外部（例如某个面板或诊断分享）调用。 */
    fun dumpAndReset(prefix: String = "DUMP"): String {
        if (!enabled) return ""
        val sb = StringBuilder()
        val snapshot = stats.entries.sortedByDescending { it.value.totalNanos.get() }
        for ((name, s) in snapshot) {
            val c = s.count.get()
            if (c == 0L) continue
            val totalMs = s.totalNanos.get() / 1_000_000.0
            val maxMs = s.maxNanos.get() / 1_000_000.0
            val avgMs = totalMs / c
            val line = "$prefix $name | calls=$c total=${"%.1f".format(totalMs)}ms " +
                "avg=${"%.3f".format(avgMs)}ms max=${"%.2f".format(maxMs)}ms slow=${
                    s.slowCount.get()
                }"
            sb.appendLine(line)
            Log.i(TAG, line)
        }
        stats.clear()
        return sb.toString()
    }

    /** 清零，便于"开拖前清、拖完再 dump"。 */
    fun reset() {
        if (!enabled) return
        stats.clear()
    }

    /** 当前是否有任何样本（供调用方判断要不要 dump）。 */
    fun hasSamples(): Boolean = enabled && stats.isNotEmpty()

    // ---- 自动上报：只要探针被触发过，就每 5 秒打一次聚合，避免依赖手动触发 ----

    private const val AUTO_DUMP_INTERVAL_MS = 5_000L

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoDumpScheduled = false

    private val autoDumpRunnable = object : Runnable {
        override fun run() {
            autoDumpScheduled = false
            if (!enabled) return
            if (stats.isNotEmpty()) {
                dumpAndReset("AUTO")
                // 还有活动就继续排，直到没有新样本为止
                scheduleAutoDump()
            }
        }
    }

    private fun scheduleAutoDump() {
        if (autoDumpScheduled) return
        autoDumpScheduled = true
        mainHandler.postDelayed(autoDumpRunnable, AUTO_DUMP_INTERVAL_MS)
    }

    /** [record] 里顺带启动自动上报（只在第一次触发时排一次）。 */
    private fun ensureAutoDump() {
        if (!autoDumpScheduled) scheduleAutoDump()
    }
}
