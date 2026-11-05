package com.slideindex.app.multitouch

import android.util.Log
import com.slideindex.app.gesture.multitouch.CollectedPath
import com.slideindex.app.gesture.multitouch.MultiPointerGestureRecognizer
import com.slideindex.app.gesture.multitouch.PathGestureCollector
import com.slideindex.app.gesture.multitouch.RecognizedMultiPointerGesture
import com.slideindex.app.gesture.multitouch.TouchAnchor
import com.slideindex.app.gesture.multitouch.TouchPointer
import com.slideindex.app.gesture.multitouch.touchAnchorOf
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
    private val onGesture: (List<RecognizedMultiPointerGesture>, Long, TouchAnchor) -> Unit,
    private val onPathGesture: (CollectedPath, Long, TouchAnchor) -> Unit = { _, _, _ -> },
    private val onInputConflict: (String?) -> Unit = {},
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

    private val pathCollector = PathGestureCollector()

    /** 上一次上报的虚拟设备列表，避免每次枚举都重复回调。 */
    @Volatile
    private var lastReportedConflict: String? = null

    /** 本次启动是否已经上报过（首次强制上报，避免 app 保留旧状态）。 */
    @Volatile
    private var conflictReported = false

    /** 独立冲突检测线程：不依赖触摸事件。 */
    @Volatile
    private var conflictWorker: Thread? = null

    /** 点按延迟判定：没有触摸帧时也要驱动识别器。 */
    @Volatile
    private var tickWorker: Thread? = null

    fun start(): Boolean {
        if (running) return true
        running = true
        conflictReported = false
        worker = Thread({ runLoop() }, "multi-touch-observer").apply {
            isDaemon = true
            start()
        }
        conflictWorker = Thread({ conflictLoop() }, "multi-touch-conflict").apply {
            isDaemon = true
            start()
        }
        tickWorker = Thread({ tickLoop() }, "multi-touch-tick").apply {
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
        conflictWorker?.interrupt()
        conflictWorker = null
        tickWorker?.interrupt()
        tickWorker = null
    }

    fun isRunning(): Boolean = running && worker?.isAlive == true

    private fun runLoop() {
        onState(true, "starting")
        while (running) {
            val device = findTouchscreen() ?: run {
                reportConflict(emptyList())
                onState(false, "no-touchscreen")
                sleepQuietly(1_500L)
                continue
            }
            reportConflict(device.virtualDevices)
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
                            val gestures = recognizer.onFrame(pointers, now)
                            if (gestures.isNotEmpty()) {
                                for (gesture in gestures) {
                                    log(
                                        "recognized ${gesture.gesture} " +
                                            "x${gesture.fingerCount} ${gesture.debug}"
                                    )
                                }
                                // 同一次触摸的候选共用基准质心，取第一条即可。
                                val anchor = touchAnchorOf(
                                    anchorX = gestures.first().anchorX,
                                    anchorY = gestures.first().anchorY,
                                    panelWidth = ranges.width,
                                    panelHeight = ranges.height,
                                )
                                onGesture(gestures, now, anchor)
                            }
                            // 路径轨迹与几何识别并行采集：最终由 app 侧按条目去重。
                            val path = pathCollector.onFrame(pointers)
                            if (path != null) {
                                log(
                                    "path x${path.fingerCount} " +
                                        "points=${path.strokes.sumOf { it.points.size }}"
                                )
                                onPathGesture(path, now, pathAnchor(path, ranges))
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
        // 统一按屏幕高度归一化：x、y 同一尺度，旋转/捏合/滑动阈值在横竖方向一致。
        for ((index, slot) in slots) {
            if (slot.trackingId < 0 || slot.x.isNaN() || slot.y.isNaN()) continue
            val nx = slot.x / ranges.height
            val ny = slot.y / ranges.height
            result += TouchPointer(id = index, x = nx, y = ny)
        }
        return result
    }

    /**
     * 路径手势的锚点：所有轨迹点的质心。
     *
     * 绘制手势可能横跨半个屏幕，没有「一个落点」可用；质心最能代表用户的手落在哪一区域，
     * 也和内置手势用质心决定的规则一致。
     */
    private fun pathAnchor(path: CollectedPath, ranges: AbsRanges): TouchAnchor {
        var sumX = 0.0
        var sumY = 0.0
        var count = 0
        for (stroke in path.strokes) {
            for (point in stroke.points) {
                sumX += point.x
                sumY += point.y
                count++
            }
        }
        if (count == 0) return TouchAnchor.CENTER
        return touchAnchorOf(
            anchorX = (sumX / count).toFloat(),
            anchorY = (sumY / count).toFloat(),
            panelWidth = ranges.width,
            panelHeight = ranges.height,
        )
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
        val virtualDevices: List<String> = emptyList(),
    )

    private fun conflictLoop() {
        while (running) {
            runCatching {
                val device = findTouchscreen()
                Log.i(TAG, "conflict scan: device=${device?.eventPath} virtual=${device?.virtualDevices}")
                reportConflict(device?.virtualDevices ?: emptyList())
            }.onFailure {
                Log.w(TAG, "conflict scan failed: ${it.message}")
            }
            sleepQuietly(CONFLICT_CHECK_INTERVAL_MS)
        }
    }

    /** 定时驱动识别器：把双击窗口结束后待定的「轻点」发出去。 */
    private fun tickLoop() {
        while (running) {
            val now = android.os.SystemClock.elapsedRealtime()
            val gesture = runCatching { recognizer.onTick(now) }.getOrNull()
            if (gesture != null) {
                log(
                    "recognized ${gesture.gesture} x${gesture.fingerCount} " +
                        "${gesture.debug} (tick)"
                )
                // onTick 目前只会产出「抬手后延迟结算」的候选（现在恒为 null）；
                // 这条路上手指坐标已经收不到，锚点退回屏幕中心，不要拿 gesture 里默认的 0 当左上角。
                onGesture(listOf(gesture), now, TouchAnchor.CENTER)
            }
            sleepQuietly(TICK_INTERVAL_MS)
        }
    }

    private fun reportConflict(devices: Collection<String>) {
        val joined = devices.distinct().joinToString(" | ").ifBlank { null }
        // 首次上报必须发：否则 app 会保留上一次运行时的旧冲突状态。
        if (conflictReported && joined == lastReportedConflict) return
        conflictReported = true
        lastReportedConflict = joined
        Log.i(TAG, "report conflict: $joined")
        onInputConflict(joined)
    }

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
        val virtualDevices = ArrayList<String>()

        fun flush() {
            val path = currentPath ?: return
            val hasTouchRange = maxX > 1 && maxY > 1
            // 虚拟/中继设备：记录名字用于冲突提示，但绝不选作触摸屏。
            val isVirtual = currentName.contains("uinput", ignoreCase = true) ||
                currentName.contains("virtual", ignoreCase = true) ||
                currentName.contains("relay", ignoreCase = true)
            if (isVirtual) {
                if (hasTouchRange && currentName.isNotBlank()) {
                    virtualDevices += currentName
                }
                return
            }
            if (found != null || !hasTouchRange) return
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
        return found?.copy(virtualDevices = virtualDevices.toList())
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

        /** 运行期冲突检测间隔：其它触控工具可能随时创建设备。 */
        const val CONFLICT_CHECK_INTERVAL_MS = 5_000L

        /** 点按延迟判定的定时器间隔。 */
        const val TICK_INTERVAL_MS = 40L

        const val SYN_REPORT = 0
        const val SYN_DROPPED = 3

        const val ABS_MT_SLOT = 47
        const val ABS_MT_POSITION_X = 53
        const val ABS_MT_POSITION_Y = 54
        const val ABS_MT_TRACKING_ID = 57
        const val BTN_TOUCH = 330
    }
}
