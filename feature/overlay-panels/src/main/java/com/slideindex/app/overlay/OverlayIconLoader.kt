package com.slideindex.app.overlay

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 浮层图标的后台解析缓存。
 *
 * 为什么必须存在：Flyme/MIUI 等 OEM 的
 * [android.content.pm.PackageManager.getApplicationIcon] 会走主题图标重建
 * （`FlymeThemeHelper.makeThemeIcon` → 逐像素 `Bitmap.getPixel`），单次调用可耗时数秒。
 * 任何在 `onDraw` 里同步调用它的路径都会把主线程卡死到 ANR
 * （实测：2026-09-27 ~ 09-28 连续 3 次 `Input dispatching timed out`，
 * 主线程栈停在 `Bitmap.getPixel` ← `FlymeThemeHelper.cropTransparentSpace`）。
 *
 * 契约：
 * - 绘制路径只允许 [peek]（纯内存，绝不阻塞）；
 * - 未命中时用 [request] 提交后台解析，完成后在主线程回调重绘，期间绘制方自己画占位；
 * - 同一个 key 不会重复提交（in-flight 去重）。
 */
object OverlayIconLoader {

    /** 图标内存上限；单个 128×128 ARGB 图标约 64KB，8MB 约可放 120 个。 */
    private const val MAX_CACHE_KB = 8 * 1024

    /** 失败记忆上限，只用于避免每帧重复提交；超了整体丢掉重来即可。 */
    private const val MISSING_LIMIT = 512

    private fun newExecutor(threads: Int, name: String): ExecutorService =
        Executors.newFixedThreadPool(threads) { runnable ->
            Thread(runnable, name).apply { isDaemon = true }
        }

    /**
     * 两档队列：绘制当场要用的（翻页到的那一页）走 [urgentExecutor]，
     * 打开面板时批量预取走 [bulkExecutor] —— 免得按需请求排在一长串预取任务后面
     * （这台机器单次 getApplicationIcon 可能要 1~2s，排在后面就是十几秒的白块）。
     */
    private val urgentExecutor = newExecutor(2, "overlay-icon-urgent")
    private val bulkExecutor = newExecutor(1, "overlay-icon-bulk")

    private val mainHandler = Handler(Looper.getMainLooper())

    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** 解析结果为空（应用已卸载、快捷方式失效等）：记下来，避免每帧重复提交。 */
    private val missing = ConcurrentHashMap.newKeySet<String>()

    // LruCache 内部读写自带同步，可以跨线程使用。
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    /** 纯内存查询，任何线程都可调用（绘制路径专用）。 */
    fun peek(key: String): Bitmap? = cache.get(key)

    /**
     * 已缓存或已在解析中则直接返回；否则排到后台线程解析。
     * [onReady] 一定在主线程回调（成功、失败都会回调，用于补一次重绘）。
     */
    fun request(
        key: String,
        urgent: Boolean = false,
        load: () -> Bitmap?,
        onReady: () -> Unit = {}
    ) {
        if (cache.get(key) != null) return
        if (missing.contains(key)) return
        if (!inFlight.add(key)) return
        val target = if (urgent) urgentExecutor else bulkExecutor
        target.execute {
            val bitmap = runCatching { load() }.getOrNull()
            if (bitmap != null) {
                cache.put(key, bitmap)
            } else {
                missing.add(key)
                if (missing.size > MISSING_LIMIT) missing.clear()
            }
            inFlight.remove(key)
            mainHandler.post(onReady)
        }
    }

    /** 与该 key 无关的杂活（快捷方式预热、矢量图标预渲染等）也走这两个线程，别另开裸线程。 */
    fun submit(block: () -> Unit) {
        bulkExecutor.execute { runCatching { block() } }
    }

    fun clear() {
        cache.evictAll()
        missing.clear()
    }
}
