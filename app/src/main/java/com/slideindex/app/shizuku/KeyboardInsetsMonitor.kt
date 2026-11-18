package com.slideindex.app.shizuku

import android.graphics.Rect
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import java.lang.reflect.Method
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer

/**
 * 键盘（IME）的可见性与位置，**全部在 shell / root 进程里读取**（对齐 GMD）。
 *
 * GMD 的路径：反射 `IWindowManager.getWindowInsets` 拿 `InsetsState`，在 sources 里找
 * `type & ITYPE_IME(8)` 的那一项，读 `isVisible()` 与 `getFrame()`；窗口变化靠
 * `WindowInfosListenerForTest` 通知。这里照做，另外补一条 `dumpsys window` 文本兜底
 * （只在反射读不到时用，而且跑在后台线程，不会卡住手势线程）。
 *
 * 读不到时 [snapshot] 返回 null，调用方按「键盘不可见」处理：不因为拿不到状态就把手势弄坏。
 */
internal class KeyboardInsetsMonitor(
    private val dumpsysReader: (() -> String)? = null,
) {

    data class State(
        val visible: Boolean,
        val bounds: Rect,
        val displayBounds: Rect,
    ) {
        fun hasBounds(): Boolean =
            displayBounds.width() > 0 && displayBounds.height() > 0 &&
                (!visible || (bounds.width() > 0 && bounds.height() > 0))
    }

    private val fallbackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KeyboardInsets").apply { isDaemon = true }
    }
    private val fallbackRunning = AtomicBoolean(false)

    @Volatile
    private var cached: State? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    @Volatile
    private var cacheValid = false

    @Volatile
    private var lastFallbackAtMs = 0L

    private var listenerHost: Any? = null
    private var listenerCallback: BiConsumer<Any, Any>? = null

    init {
        startWindowInfosListener()
    }

    /**
     * 当前键盘状态。缓存只在窗口变化时作废，所以读一次就够一次手势判定用；
     * 反射不可用时退回上一份缓存（没有就返回 null）。
     */
    fun snapshot(): State? {
        val now = SystemClock.elapsedRealtime()
        val current = cached
        if (cacheValid && current != null && now - cachedAtMs <= CACHE_TTL_MS) return current
        val direct = runCatching { WindowInsetsReader.readDisplay(DEFAULT_DISPLAY) }.getOrNull()
        if (direct != null) {
            cached = direct
            cachedAtMs = now
            cacheValid = true
            return direct
        }
        scheduleDumpsysFallback(now)
        return current
    }

    fun close() {
        val host = listenerHost
        val callback = listenerCallback
        listenerHost = null
        listenerCallback = null
        if (host != null && callback != null) {
            runCatching {
                host.javaClass
                    .getMethod("removeWindowInfosListener", BiConsumer::class.java)
                    .invoke(host, callback)
            }
        }
        runCatching { fallbackExecutor.shutdownNow() }
    }

    private fun scheduleDumpsysFallback(now: Long) {
        val reader = dumpsysReader ?: return
        if (now - lastFallbackAtMs < FALLBACK_INTERVAL_MS) return
        lastFallbackAtMs = now
        if (!fallbackRunning.compareAndSet(false, true)) return
        runCatching {
            fallbackExecutor.execute {
                try {
                    val state = runCatching { parseDumpsys(reader()) }.getOrNull()
                    if (state != null) {
                        cached = state
                        cachedAtMs = SystemClock.elapsedRealtime()
                        cacheValid = true
                    }
                } catch (_: Throwable) {
                    // 兜底失败就保持现状：没有键盘状态 = 不按键盘拦手势。
                } finally {
                    fallbackRunning.set(false)
                }
            }
        }.onFailure { fallbackRunning.set(false) }
    }

    private fun startWindowInfosListener() {
        runCatching {
            val clazz = Class.forName("android.window.WindowInfosListenerForTest")
            val host = clazz.getConstructor().newInstance()
            val add = clazz.getMethod("addWindowInfosListener", BiConsumer::class.java)
            val callback = BiConsumer<Any, Any> { _, _ ->
                // 只作废缓存，不当场反射：这个回调可能在 binder 线程上。
                cacheValid = false
            }
            add.invoke(host, callback)
            listenerHost = host
            listenerCallback = callback
        }.onFailure {
            Log.i(
                TAG,
                "WindowInfosListenerForTest 不可用（${it.javaClass.simpleName}），键盘状态改为按需读取",
            )
        }
    }

    private fun parseDumpsys(raw: String): State? {
        if (raw.isBlank()) return null
        val display = DISPLAY_FRAME_RECT.find(raw)?.let { match ->
            Rect(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
                match.groupValues[4].toInt(),
            )
        } ?: DISPLAY_FRAMES.find(raw)?.let { match ->
            Rect(0, 0, match.groupValues[1].toInt(), match.groupValues[2].toInt())
        } ?: return null
        if (display.width() <= 0 || display.height() <= 0) return null
        val ime = IME_INSETS.find(raw) ?: return State(false, Rect(), display)
        return State(
            visible = ime.groupValues[5].toBoolean(),
            bounds = Rect(
                ime.groupValues[1].toInt(),
                ime.groupValues[2].toInt(),
                ime.groupValues[3].toInt(),
                ime.groupValues[4].toInt(),
            ),
            displayBounds = display,
        )
    }

    /** 反射 `IWindowManager.getWindowInsets`，只读主显示（display 0）。 */
    private object WindowInsetsReader {
        private var resolved = false
        private var windowManager: Any? = null
        private var getWindowInsets: Method? = null
        private var insetsStateClass: Class<*>? = null

        fun readDisplay(displayId: Int): State? {
            if (!ensureReady()) return null
            val method = getWindowInsets ?: return null
            val stateClass = insetsStateClass ?: return null
            val state = runCatching { stateClass.getConstructor().newInstance() }.getOrNull()
                ?: return null
            val types = method.parameterTypes
            val invoked = when {
                // 旧签名：getWindowInsets(int displayId, LayoutParams, InsetsState)
                types[0] == Int::class.javaPrimitiveType ->
                    runCatching { method.invoke(windowManager, displayId, null, state) }.isSuccess

                // 新签名：getWindowInsets(LayoutParams, int displayId, InsetsState)
                types[1] == Int::class.javaPrimitiveType -> {
                    val params = android.view.WindowManager.LayoutParams().apply {
                        // 等价于原来的裸 0x10100：写成命名 flag 后 lint 的 WrongConstant 才能识别
                        flags = android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                    }
                    runCatching { method.invoke(windowManager, params, displayId, state) }.isSuccess
                }

                else -> false
            }
            if (!invoked) return null
            val displayFrame = invokeRect(state, "getDisplayFrame") ?: return null
            if (displayFrame.width() <= 0 || displayFrame.height() <= 0) return null
            val source = findImeSource(state) ?: return State(false, Rect(), displayFrame)
            val visible = runCatching {
                source.javaClass.getMethod("isVisible").invoke(source) as Boolean
            }.getOrDefault(false)
            val frame = invokeRect(source, "getFrame") ?: Rect()
            return State(visible, frame, displayFrame)
        }

        private fun ensureReady(): Boolean {
            if (resolved) return windowManager != null && getWindowInsets != null
            resolved = true
            runCatching {
                val binder = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String::class.java)
                    .invoke(null, "window") as? IBinder ?: return false
                val manager = Class.forName("android.view.IWindowManager\$Stub")
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, binder) ?: return false
                val method = manager.javaClass.methods.firstOrNull {
                    it.name == "getWindowInsets" && it.parameterTypes.size == 3
                } ?: return false
                windowManager = manager
                getWindowInsets = method
                insetsStateClass = Class.forName("android.view.InsetsState")
                true
            }.onFailure {
                Log.i(TAG, "getWindowInsets 不可用（${it.javaClass.simpleName}）：${it.message}")
            }
            return windowManager != null && getWindowInsets != null
        }

        /** `InsetsState` 里 type 带 IME 位（8）的 source。 */
        private fun findImeSource(state: Any): Any? {
            runCatching {
                val size = state.javaClass.getMethod("sourceSize").invoke(state) as Int
                val at = state.javaClass.getMethod("sourceAt", Int::class.javaPrimitiveType)
                for (index in 0 until size) {
                    val source = at.invoke(state, index) ?: continue
                    val type = source.javaClass.getMethod("getType").invoke(source) as Int
                    if (type and IME_TYPE != 0) return source
                }
            }
            // 老版本没有 sourceSize/sourceAt：用常量字段 + peekSource/getSource。
            runCatching {
                val field = state.javaClass.getDeclaredField("ITYPE_IME").apply {
                    isAccessible = true
                }
                val imeType = field.getInt(null)
                val method = runCatching {
                    state.javaClass.getMethod("peekSource", Int::class.javaPrimitiveType)
                }.getOrNull() ?: state.javaClass.getMethod(
                    "getSource",
                    Int::class.javaPrimitiveType,
                )
                return method.invoke(state, imeType)
            }
            return null
        }

        private fun invokeRect(target: Any, name: String): Rect? = runCatching {
            target.javaClass.getMethod(name).invoke(target) as? Rect
        }.getOrNull()
    }

    private companion object {
        const val TAG = "KeyboardInsets"
        const val DEFAULT_DISPLAY = 0
        const val IME_TYPE = 8
        const val CACHE_TTL_MS = 250L
        const val FALLBACK_INTERVAL_MS = 2_000L

        val DISPLAY_FRAMES = Regex("""DisplayFrames\s+w=(\d+)\s+h=(\d+)""")
        val DISPLAY_FRAME_RECT =
            Regex("""mDisplayFrame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]""")
        val IME_INSETS = Regex(
            """(?:type|mType)=(?:ime|ITYPE_IME).*?""" +
                """(?:frame|mFrame)=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]""" +
                """.*?(?:visible|mVisible)=(true|false)""",
        )
    }
}
