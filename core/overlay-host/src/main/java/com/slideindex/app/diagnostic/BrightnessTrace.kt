package com.slideindex.app.diagnostic

import android.content.Context
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 亮度诊断追踪（跨进程落文件）。
 *
 * 为什么落文件：亮度调节跑在 `:overlay`，而"扩展 → 日志"页的分享导出在主进程。
 * 进程内环形缓冲读不到对方的记录，所以统一追加到 `files/diagnostics/brightness-trace.log`，
 * 导出时直接读文件尾部。
 *
 * 记三类：
 * 1. 我们每次写（fraction / int / float / 量程 / 通道 / 成功与否）；
 * 2. 系统四个键的每次变化（区分"我们的写"和"外部改的"）——回弹是谁改的，看这个；
 * 3. 写完 2.5 秒内按 8Hz 采样实际屏幕亮度（DisplayManager）——"键没变但屏幕变了"也能抓到。
 *
 * 内容只有数字与时间，无任何通知/剪贴板数据。
 */
object BrightnessTrace {

    private const val MAX_BYTES = 512 * 1024
    private const val SAMPLE_INTERVAL_MS = 120L
    private const val SAMPLE_WINDOW_MS = 2_500L
    private const val SELF_MATCH_WINDOW_MS = 800L
    private const val READBACK_DELAY_MS = 300L

    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "brightness-trace").apply { isDaemon = true }
    }
    private val clock = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val mainHandler = Handler(Looper.getMainLooper())

    private data class SelfWrite(val key: String, val value: String, val atUptime: Long)

    private val selfWrites = ArrayDeque<SelfWrite>()

    @Volatile
    private var observing = false
    @Volatile
    private var sampling = false
    private var observer: ContentObserver? = null
    private var sampleRunnable: Runnable? = null
    private var lastWriteAt = 0L

    private val keys = listOf(
        Settings.System.SCREEN_BRIGHTNESS,
        "screen_brightness_float",
        "screen_auto_brightness_adj",
        Settings.System.SCREEN_BRIGHTNESS_MODE,
    )

    private fun file(context: Context): File =
        File(context.filesDir, "diagnostics/brightness-trace.log").apply { parentFile?.mkdirs() }

    fun record(context: Context, event: String) {
        val line = "${clock.format(Date())} ${event}\n"
        io.execute {
            runCatching {
                val target = file(context)
                if (target.length() > MAX_BYTES) {
                    val rotated = File(target.parentFile, "brightness-trace.1.log")
                    rotated.delete()
                    target.renameTo(rotated)
                }
                target.appendText(line)
            }
        }
    }

    /** 记一次我们发起的写；同时开启/续期"写完观察窗口"。 */
    fun recordWrite(
        context: Context,
        fraction: Float,
        targetInt: Int,
        range: String,
        path: String,
        intOk: Boolean,
        shellOk: Boolean,
    ) {
        markSelf(Settings.System.SCREEN_BRIGHTNESS, targetInt.toString())
        record(
            context,
            "WRITE f=$fraction int=$targetInt range=$range path=$path " +
                "intOk=$intOk shellOk=$shellOk display=${readDisplay(context)}",
        )
        startSampling(context)
        scheduleReadback(context, targetInt)
    }

    /**
     * 写完 300ms 回读一次 int：用来判断"写入成功但被系统静默改掉"。
     *
     * 只记录，不改变任何行为；下一份用户日志能直接看出量程对不对、有没有被静默忽略。
     */
    private fun scheduleReadback(context: Context, expected: Int) {
        val appContext = context.applicationContext
        mainHandler.postDelayed({
            val resolver = appContext.contentResolver
            val actual = runCatching {
                Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1)
            }.getOrDefault(-1)
            if (actual != expected) {
                record(appContext, "READBACK expected=$expected actual=$actual")
            }
        }, READBACK_DELAY_MS)
    }

    fun ensureObserving(context: Context) {
        if (observing) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { ensureObserving(context) }
            return
        }
        observing = true
        val appContext = context.applicationContext
        val contentObserver = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                record(appContext, snapshotChanges(appContext))
            }
        }
        observer = contentObserver
        val resolver = appContext.contentResolver
        keys.forEach { key ->
            runCatching { resolver.registerContentObserver(Settings.System.getUriFor(key), false, contentObserver) }
        }
        record(appContext, "OBSERVER attached")
    }

    private fun snapshotChanges(context: Context): String {
        val resolver = context.contentResolver
        val int = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1) }.getOrDefault(-1)
        val float = runCatching { Settings.System.getFloat(resolver, "screen_brightness_float") }.getOrNull()
        val adj = runCatching { Settings.System.getFloat(resolver, "screen_auto_brightness_adj") }.getOrNull()
        val mode = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1) }.getOrDefault(-1)
        val intTag = tagFor(Settings.System.SCREEN_BRIGHTNESS, int.toString())
        val floatTag = tagFor("screen_brightness_float", float?.toString().orEmpty())
        return "CHANGE int=${int}$intTag float=$float$floatTag adj=$adj mode=$mode display=${readDisplay(context)}"
    }

    private fun markSelf(key: String, value: String) {
        val now = SystemClock.uptimeMillis()
        synchronized(selfWrites) {
            selfWrites.addLast(SelfWrite(key, value, now))
            while (selfWrites.size > 16) selfWrites.removeFirst()
        }
    }

    private fun tagFor(key: String, value: String): String {
        val now = SystemClock.uptimeMillis()
        synchronized(selfWrites) {
            selfWrites.removeAll { now - it.atUptime > SELF_MATCH_WINDOW_MS * 4 }
            val hit = selfWrites.any {
                it.key == key && it.value == value && now - it.atUptime <= SELF_MATCH_WINDOW_MS
            }
            return if (hit) "(self)" else "(external)"
        }
    }

    private fun startSampling(context: Context) {
        lastWriteAt = SystemClock.uptimeMillis()
        if (sampling) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startSampling(context) }
            return
        }
        sampling = true
        val appContext = context.applicationContext
        val runnable = object : Runnable {
            override fun run() {
                record(appContext, "SAMPLE display=${readDisplay(appContext)}")
                val idle = SystemClock.uptimeMillis() - lastWriteAt
                if (idle >= SAMPLE_WINDOW_MS) {
                    sampling = false
                    sampleRunnable = null
                    return
                }
                mainHandler.postDelayed(this, SAMPLE_INTERVAL_MS)
            }
        }
        sampleRunnable = runnable
        mainHandler.postDelayed(runnable, SAMPLE_INTERVAL_MS)
    }

    /**
     * 读屏幕"实际亮度"。
     *
     * 不同 ROM / compileSdk 上读取入口不一样（`DisplayManager.getBrightness(int)`、
     * 带 BrightnessType 的旧/新重载、`Display.getBrightness()`），依次尝试；
     * 全失败才返回 err，避免像 ColorOS 上那样一直是 err 看不到屏幕真实值。
     */
    fun readDisplayBrightness(context: Context): String {
        val manager = runCatching {
            context.getSystemService(DisplayManager::class.java)
        }.getOrNull() ?: return "n/a"
        runCatching {
            manager.javaClass.getMethod("getBrightness", Int::class.javaPrimitiveType)
                .invoke(manager, Display.DEFAULT_DISPLAY) as? Float
        }.getOrNull()?.let { return it.toString() }
        runCatching {
            manager.javaClass.getMethod(
                "getBrightness",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).invoke(manager, Display.DEFAULT_DISPLAY, 0) as? Float
        }.getOrNull()?.let { return it.toString() }
        val display = runCatching { manager.getDisplay(Display.DEFAULT_DISPLAY) }.getOrNull()
            ?: return "n/a"
        return runCatching {
            display.javaClass.getMethod("getBrightness").invoke(display) as? Float
        }.getOrNull()?.toString() ?: "n/a"
    }

    private fun readDisplay(context: Context): String = readDisplayBrightness(context)

    fun readTail(context: Context, maxBytes: Int = 200 * 1024): String = runCatching {
        val target = file(context)
        if (!target.exists()) return@runCatching ""
        val bytes = target.readBytes()
        val start = (bytes.size - maxBytes).coerceAtLeast(0)
        String(bytes, start, bytes.size - start, Charsets.UTF_8)
    }.getOrDefault("")
}
