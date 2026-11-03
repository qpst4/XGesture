package com.slideindex.app.multitouch

import android.util.Log
import com.slideindex.app.gesture.multitouch.MultiPointerGestureRecognizer
import com.slideindex.app.gesture.multitouch.RecognizedMultiPointerGesture
import com.slideindex.app.gesture.multitouch.TouchPointer
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 在 Shizuku 的 shell 用户服务里读取触摸屏 evdev 原始事件，识别多指手势。
 *
 * 为什么读 `/dev/input/event*`：普通 app 拿不到全屏多点触控；shell（uid 2000）属于 input 组，
 * 可以直接读触摸设备。这是**只读观察**，不会拦截事件，应用仍会收到同一手势。
 *
 * 设备枚举走 `getevent -p`（`/proc/bus/input/devices` 在部分 ROM 上对 shell 也拒读；
 * Linux input 的 ioctl 在纯 Java 里拿不到，直接解析 shell 输出最省事），同时拿到坐标范围。
 */
class EvdevTouchObserver(
    private val onGesture: (RecognizedMultiPointerGesture, Long) -> Unit,
    private val onState: (Boolean, String) -> Unit = { _, _ -> },
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {
    @Volatile
    private var running = false

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var openStream: FileInputStream? = null

    private val recognizer = MultiPointerGestureRecognizer()

    fun start(): Boolean {
        if (running) return true
        running = true
        worker = Thread({ runLoop() }, "multi-touch-observer").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun stop() {
        running = false
        runCatching { openStream?.close() }
        openStream = null
        worker?.interrupt()
        worker = null
    }

    fun isRunning(): Boolean = running && worker?.isAlive == true

    private fun runLoop() {
        onState(true, "starting")
        while (running) {
            val device = findTouchscreen() ?: run {
                onState(false, "no-touchscreen")
                sleepQuietly(1_500L)
                continue
            }
            recognizer.reset()
            onState(true, "watching:${device.eventPath}")
            try {
                readDevice(device.eventPath, AbsRanges(device.width, device.height))
            } catch (error: Throwable) {
                if (running) {
                    log("touch reader stopped: ${error.message}")
                    onState(false, "reader-error")
                }
            } finally {
                runCatching { openStream?.close() }
                openStream = null
            }
            if (running) sleepQuietly(600L)
        }
        onState(false, "stopped")
    }

    private fun readDevice(eventPath: String, ranges: AbsRanges) {
        FileInputStream(eventPath).use { stream ->
            openStream = stream
            val buffer = ByteArray(EVENT_SIZE)
            val slots = HashMap<Int, Slot>()
            var currentSlot = 0
            var touchDown = false

            while (running) {
                if (!readFully(stream, buffer)) break
                val type = readShort(buffer, 16)
                val code = readShort(buffer, 18)
                val value = readInt(buffer, 20)
                when (type) {
                    EV_ABS -> when (code) {
                        ABS_MT_SLOT -> currentSlot = value
                        ABS_MT_TRACKING_ID -> {
                            val slot = slots.getOrPut(currentSlot) { Slot() }
                            slot.trackingId = value
                            if (value < 0) {
                                slot.x = Float.NaN
                                slot.y = Float.NaN
                            }
                        }
                        ABS_MT_POSITION_X -> {
                            slots.getOrPut(currentSlot) { Slot() }.x = value.toFloat()
                        }
                        ABS_MT_POSITION_Y -> {
                            slots.getOrPut(currentSlot) { Slot() }.y = value.toFloat()
                        }
                    }

                    EV_KEY -> if (code == BTN_TOUCH) {
                        touchDown = value != 0
                    }

                    EV_SYN -> when (code) {
                        SYN_REPORT -> {
                            val now = android.os.SystemClock.elapsedRealtime()
                            val pointers = collectPointers(slots, ranges)
                            if (pointers.isEmpty() && !touchDown) {
                                recognizer.onFrame(emptyList(), now)
                            } else {
                                val gesture = recognizer.onFrame(pointers, now)
                                if (gesture != null) {
                                    log("recognized ${gesture.gesture} x${gesture.fingerCount}")
                                    onGesture(gesture, now)
                                }
                            }
                        }

                        SYN_DROPPED -> {
                            slots.clear()
                            currentSlot = 0
                            touchDown = false
                            recognizer.reset()
                        }
                    }
                }
            }
        }
    }

    private fun collectPointers(slots: Map<Int, Slot>, ranges: AbsRanges): List<TouchPointer> {
        val result = ArrayList<TouchPointer>(slots.size)
        if (ranges.width <= 0f || ranges.height <= 0f) return result
        // 触摸屏原始坐标是 10800x23400 这类非等比范围。识别器内部按 0..1 工作，
        // 若直接 x/width、y/height，横向阈值会比纵向"短"一倍多（96px vs 210px），
        // 旋转半径也会算成椭圆。这里把 x 拉成和 y 同尺度（都按屏幕高度的比例）。
        val aspect = ranges.height / ranges.width
        for ((index, slot) in slots) {
            if (slot.trackingId < 0 || slot.x.isNaN() || slot.y.isNaN()) continue
            val nx = (slot.x / ranges.width) * aspect
            val ny = slot.y / ranges.height
            result += TouchPointer(id = index, x = nx, y = ny)
        }
        return result
    }

    private data class Slot(
        var trackingId: Int = -1,
        var x: Float = Float.NaN,
        var y: Float = Float.NaN,
    )

    private data class AbsRanges(val width: Float, val height: Float)

    private data class Touchscreen(
        val eventPath: String,
        val name: String,
        val width: Float,
        val height: Float,
    )

    /**
     * 用 `getevent -p` 枚举触摸屏并同时拿到坐标范围。
     *
     * 不能用 `/proc/bus/input/devices`：部分 ROM（实测魅族 21 / Android 16）对 shell 也
     * 返回 Permission denied；`getevent` 是 shell 自带工具，权限与格式都稳定。
     *
     * 输出形态：
     * ```
     * add device 2: /dev/input/event7
     *   name:     "main_touch"
     *     ABS (0003): 0035  : value 0, min 0, max 10800, ...
     *                 0036  : value 0, min 0, max 23400, ...
     * ```
     */
    private fun findTouchscreen(): Touchscreen? {
        val output = runCatching {
            val process = ProcessBuilder("/system/bin/getevent", "-p")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(3, TimeUnit.SECONDS)
            text
        }.getOrNull() ?: return null

        var currentPath: String? = null
        var currentName = ""
        var maxX = 0
        var maxY = 0
        var found: Touchscreen? = null

        fun flush() {
            if (found != null) return
            val path = currentPath ?: return
            if (currentName.contains("uinput", ignoreCase = true) ||
                currentName.contains("virtual", ignoreCase = true)
            ) {
                return
            }
            if (maxX <= 1 || maxY <= 1) return
            found = Touchscreen(path, currentName, maxX.toFloat(), maxY.toFloat())
        }

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("add device")) {
                flush()
                currentPath = "/dev/input/" + trimmed.substringAfterLast('/').trim()
                currentName = ""
                maxX = 0
                maxY = 0
                continue
            }
            if (currentPath == null) continue
            when {
                trimmed.startsWith("name:") ->
                    currentName = trimmed.substringAfter("name:").trim().trim('"')

                trimmed.startsWith("0035") ->
                    maxX = MAX_PATTERN.find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: maxX

                trimmed.startsWith("0036") ->
                    maxY = MAX_PATTERN.find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: maxY
            }
        }
        flush()
        return found
    }

    private fun readFully(stream: FileInputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = try {
                stream.read(buffer, offset, buffer.size - offset)
            } catch (error: IOException) {
                return false
            }
            if (read < 0) return false
            if (read == 0) {
                sleepQuietly(5L)
                continue
            }
            offset += read
        }
        return true
    }

    private fun readShort(buffer: ByteArray, offset: Int): Int =
        (buffer[offset].toInt() and 0xFF) or ((buffer[offset + 1].toInt() and 0xFF) shl 8)

    private fun readInt(buffer: ByteArray, offset: Int): Int =
        (buffer[offset].toInt() and 0xFF) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 3].toInt() and 0xFF) shl 24)

    private fun sleepQuietly(millis: Long) {
        runCatching { Thread.sleep(millis) }
    }

    private companion object {
        const val TAG = "EvdevTouchObserver"

        val MAX_PATTERN = Regex("max\\s+(\\d+)")

        /** 64 位 timeval：16 + type 2 + code 2 + value 4。 */
        const val EVENT_SIZE = 24

        const val EV_SYN = 0
        const val EV_KEY = 1
        const val EV_ABS = 3

        const val SYN_REPORT = 0
        const val SYN_DROPPED = 3

        const val ABS_MT_SLOT = 47
        const val ABS_MT_POSITION_X = 53
        const val ABS_MT_POSITION_Y = 54
        const val ABS_MT_TRACKING_ID = 57
        const val BTN_TOUCH = 330
    }
}
