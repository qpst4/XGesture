package com.slideindex.app.dragpress

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.slideindex.app.shizuku.ITouchInterruptionGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 客户端触摸输入源：从特权触摸流读取 evdev 帧，映射为当前屏幕方向坐标并派发。
 */
internal class TouchInputSource(
    private val context: Context,
    private val scope: CoroutineScope,
    private val event: (Int, Float, Float, Long) -> Unit,
    private val disconnected: () -> Unit,
) {
    private class Run {
        var job: Job? = null
        @Volatile var cancelled = false
        @Volatile var connection: ShizukuTouchConnection? = null
    }

    private var current: Run? = null

    suspend fun interruptSourceTouch(
        sourcePackage: String,
        x: Float,
        y: Float,
        displayId: Int,
        deadlineUptimeMs: Long,
        guard: ITouchInterruptionGuard,
    ): Boolean {
        val run = current ?: return false
        if (run.cancelled) return false
        val connection = run.connection ?: return false
        return connection.interrupt(sourcePackage, x, y, displayId, deadlineUptimeMs, guard)
    }

    fun start() {
        if (current != null) return
        val run = Run()
        current = run
        run.job = scope.launch(Dispatchers.IO) {
            while (isActive && !run.cancelled) {
                try {
                    val connection = ShizukuTouchConnection(context)
                    run.connection = connection
                    val ready = AtomicBoolean()
                    val deadline = scope.launch(Dispatchers.IO) {
                        delay(10_000)
                        if (!ready.get() && run.connection === connection) {
                            runCatching { connection.close() }
                        }
                    }
                    try {
                        if (run.cancelled) break
                        val pipe = connection.open()
                        if (run.cancelled || !isActive) {
                            pipe.close()
                            break
                        }
                        val manager = context.getSystemService(WindowManager::class.java)
                        var device: TouchInputProtocol.Device? = null
                        var eventTime = 0L
                        val pendingFrames = AtomicInteger()
                        val parser = EvdevTouchParser { action, x, y, _, _, _ ->
                            val metrics = DisplayMetrics()
                            @Suppress("DEPRECATION")
                            manager.defaultDisplay.getRealMetrics(metrics)
                            @Suppress("DEPRECATION")
                            val rotation = manager.defaultDisplay.rotation
                            val dev = device ?: return@EvdevTouchParser
                            val point = TouchInputProtocol.map(x, y, dev, metrics.widthPixels, metrics.heightPixels, rotation)
                            val frameTime = eventTime
                            if (pendingFrames.incrementAndGet() <= 64) {
                                scope.launch(Dispatchers.Main) {
                                    try {
                                        if (!run.cancelled && run.connection === connection) {
                                            event(action, point[0], point[1], frameTime)
                                        }
                                    } finally {
                                        pendingFrames.decrementAndGet()
                                    }
                                }
                            } else {
                                pendingFrames.decrementAndGet()
                            }
                        }
                        ParcelFileDescriptor.AutoCloseInputStream(pipe).use { input ->
                            ProcessOutputLines.read(input, maxLength = 512, onDiscard = {
                                Log.w("TouchInputSource", "Oversized input frame discarded")
                            }) { line ->
                                if (run.cancelled || !isActive) return@read false
                                if (!ready.get()) {
                                    val header = line.split(' ')
                                    check(header.size == 5 && (header[0] == "SLIDE_TOUCH" || header[0] == "EVO_TOUCH")) {
                                        "Touch stream header mismatch: $line"
                                    }
                                    device = TouchInputProtocol.Device(
                                        "",
                                        header[1].toInt(),
                                        header[2].toInt(),
                                        header[3].toInt(),
                                        header[4].toInt()
                                    )
                                    ready.set(true)
                                    deadline.cancel()
                                } else {
                                    val timestamp = TouchInputProtocol.eventTime(line, SystemClock.uptimeMillis())
                                    if (timestamp == null) {
                                        parser.cancel()
                                    } else {
                                        eventTime = timestamp
                                        TouchInputProtocol.event(line)?.let { values ->
                                            if (values[0] == 0 && values[1] == 3) {
                                                parser.cancel()
                                            } else {
                                                parser.consume(values[0], values[1], values[2])
                                            }
                                        }
                                    }
                                }
                                true
                            }
                        }
                    } finally {
                        deadline.cancel()
                        runCatching { connection.close() }
                        if (run.connection === connection) run.connection = null
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("TouchInputSource", "Touch stream error: ${error.message}")
                } finally {
                    withContext(Dispatchers.Main) {
                        if (!run.cancelled) {
                            disconnected()
                        }
                    }
                }
                delay(3000)
            }
        }
    }

    fun stop() {
        val run = current ?: return
        current = null
        run.cancelled = true
        run.job?.cancel()
        val connection = run.connection
        run.connection = null
        scope.launch(NonCancellable + Dispatchers.IO) {
            runCatching { connection?.close() }
        }
        disconnected()
    }
}
