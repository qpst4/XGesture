package com.slideindex.app.multitouch

import android.util.Log
import com.slideindex.app.gesture.multitouch.CollectedPath
import com.slideindex.app.gesture.multitouch.MtProtocolSniffer
import com.slideindex.app.gesture.multitouch.MtTouchFrameDecoder
import com.slideindex.app.gesture.multitouch.MultiPointerGestureRecognizer
import com.slideindex.app.gesture.multitouch.PathGestureCollector
import com.slideindex.app.gesture.multitouch.RecognizedMultiPointerGesture
import com.slideindex.app.gesture.multitouch.TouchAnchor
import com.slideindex.app.gesture.multitouch.TouchDisplayTransform
import com.slideindex.app.gesture.multitouch.TouchPointer
import com.slideindex.app.gesture.multitouch.rotatePanelPointToScreen
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
    /**
     * 当前屏幕方向/尺寸。有效时把面板坐标旋转成屏幕坐标（对齐 GMD 的入口旋转）：
     * 识别器看到的滑动方向、路径形状、锚点就都是用户眼里的屏幕方向。
     */
    private val displayTransform: () -> TouchDisplayTransform = { TouchDisplayTransform.IDENTITY },
    /** 手动协议覆盖：auto / type_a / type_b。 */
    private val protocolOverride: () -> String = { MultiTouchGestureSettings.PROTOCOL_AUTO },
    /** 手动指定触摸设备路径；空串 = 自动挑。 */
    private val devicePathOverride: () -> String = { "" },
    /** 诊断信息（设备/协议/候选），服务端会转给设置页。 */
    private val onDiagnostics: (String) -> Unit = {},
    /** 触摸设备候选（每行 `eventPath|name|protocol|width|height`），设置页「触摸设备」用。 */
    private val onDevices: (List<String>) -> Unit = {},
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
            log(
                "touch device=${device.eventPath} name=${device.name} protocol=${device.protocol} " +
                    "slot=${device.hasSlot} track=${device.hasTrackingId} btn=${device.hasBtnTouch} " +
                    "range=${device.width}x${device.height}",
            )
            onState(true, "watching:${device.eventPath}")
            try {
                readDevice(
                    eventPath = device.eventPath,
                    ranges = AbsRanges(device.width, device.height),
                    protocol = device.protocol,
                    trackingIdOptionalHint = device.trackingIdOptionalHint,
                )
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

    private fun readDevice(
        eventPath: String,
        ranges: AbsRanges,
        protocol: MtTouchFrameDecoder.Protocol,
        trackingIdOptionalHint: Boolean,
    ) {
        FileInputStream(eventPath).use { stream ->
            openStream = stream
            val buffer = ByteArray(EVENT_SIZE)
            // Type A / Type B 的差异全部收在解码器里：这里只负责读事件、喂帧、驱动识别。
            var decoder = MtTouchFrameDecoder(
                protocol = protocol,
                panelHeight = ranges.height,
                trackingIdOptional = trackingIdOptionalHint,
            )
            // 静态能力表只是初判：第一次真正摸上来时用真实事件流定案（防混合驱动判错）。
            // 手动指定协议时尊重用户选择，不再自动嗅探/回退。
            val allowSniffing = runCatching { protocolOverride() }
                .getOrDefault(MultiTouchGestureSettings.PROTOCOL_AUTO) ==
                MultiTouchGestureSettings.PROTOCOL_AUTO
            val sniffer = MtProtocolSniffer()
            var sniffing = allowSniffing
            var rawEvents = 0
            var pointerFrames = 0
            var fallbackTried = !allowSniffing
            val startedAtMs = android.os.SystemClock.elapsedRealtime()

            while (running) {
                if (!readFully(stream, buffer)) break
                val type = readShort(buffer, 16)
                val code = readShort(buffer, 18)
                val value = readInt(buffer, 20)
                rawEvents++
                if (sniffing) sniffer.onEvent(type, code, value)

                val frameReady = decoder.onEvent(type, code, value)
                if (type == MtTouchFrameDecoder.EV_SYN && code == MtTouchFrameDecoder.SYN_DROPPED) {
                    recognizer.reset()
                    pathCollector.cancel()
                }
                if (!frameReady) continue

                if (sniffing && sniffer.sawTouch) {
                    sniffing = false
                    val decided = sniffer.protocol
                    val optional = sniffer.trackingIdOptional
                    if (decided != decoder.protocol || optional != decoder.trackingIdOptional) {
                        decoder = MtTouchFrameDecoder(
                            protocol = decided,
                            panelHeight = ranges.height,
                            trackingIdOptional = optional,
                        )
                        recognizer.reset()
                        pathCollector.cancel()
                        log(
                            "protocol sniff: static=${protocol}(optional=$trackingIdOptionalHint) " +
                                "-> $decided(optional=$optional)",
                        )
                        continue
                    }
                    log("protocol confirmed $decided optional=$optional")
                }

                val transform = runCatching { displayTransform() }
                    .getOrDefault(TouchDisplayTransform.IDENTITY)
                val pointers = transformPointers(decoder.pointers, transform, ranges)
                if (pointers.isNotEmpty()) pointerFrames++
                // 锚点用与触点同一套归一化：旋转到屏幕后按屏幕宽高算 u/v，app 侧不再旋转。
                val anchorRanges = if (transform.valid) {
                    AbsRanges(transform.screenWidthPx.toFloat(), transform.screenHeightPx.toFloat())
                } else {
                    ranges
                }
                val now = android.os.SystemClock.elapsedRealtime()
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
                        panelWidth = anchorRanges.width,
                        panelHeight = anchorRanges.height,
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
                    onPathGesture(path, now, pathAnchor(path, anchorRanges))
                }

                // 兜底：有原始事件、但两类判定都没产出任何触点 —— 换另一种协议再试一次。
                if (!fallbackTried && !sniffing && pointerFrames == 0 &&
                    rawEvents >= 200 &&
                    android.os.SystemClock.elapsedRealtime() - startedAtMs >= 1_500L
                ) {
                    fallbackTried = true
                    val other = if (decoder.protocol == MtTouchFrameDecoder.Protocol.TYPE_B) {
                        MtTouchFrameDecoder.Protocol.TYPE_A
                    } else {
                        MtTouchFrameDecoder.Protocol.TYPE_B
                    }
                    decoder = MtTouchFrameDecoder(
                        protocol = other,
                        panelHeight = ranges.height,
                        trackingIdOptional = other == MtTouchFrameDecoder.Protocol.TYPE_B,
                    )
                    recognizer.reset()
                    pathCollector.cancel()
                    log("protocol fallback -> $other (no pointers after $rawEvents events)")
                }
            }
        }
    }

    /**
     * 把解码器输出的「面板坐标（按面板高度归一化）」旋转成「屏幕坐标（按屏幕高度归一化）」。
     *
     * 旋转不在 app 侧做，是因为识别和路径采集都发生在 service 里；在入口一次转完，
     * 滑动方向、路径匹配、锚点就全部天然是屏幕方向（GMD 的 parser 也是这么做的）。
     */
    private fun transformPointers(
        pointers: List<TouchPointer>,
        transform: TouchDisplayTransform,
        ranges: AbsRanges,
    ): List<TouchPointer> {
        if (!transform.valid || pointers.isEmpty()) return pointers
        val screenHeight = transform.screenHeightPx.toFloat()
        return pointers.map { pointer ->
            val (screenX, screenY) = rotatePanelPointToScreen(
                x = pointer.x * ranges.height,
                y = pointer.y * ranges.height,
                panelWidth = ranges.width,
                panelHeight = ranges.height,
                transform = transform,
            )
            TouchPointer(
                id = pointer.id,
                x = screenX / screenHeight,
                y = screenY / screenHeight,
            )
        }
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

    private data class AbsRanges(val width: Float, val height: Float)

    private data class Touchscreen(
        val eventPath: String,
        val name: String,
        val width: Float,
        val height: Float,
        val protocol: MtTouchFrameDecoder.Protocol,
        /** 静态能力表看起来像「有 slot 没 tracking id」的 B 变体。 */
        val trackingIdOptionalHint: Boolean = false,
        val hasSlot: Boolean = false,
        val hasTrackingId: Boolean = false,
        val hasBtnTouch: Boolean = false,
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
        var hasMtPositionX = false
        var hasMtPositionY = false
        var hasMtSlot = false
        var hasTrackingId = false
        var hasBtnTouch = false
        val candidates = ArrayList<Touchscreen>()
        val virtualDevices = ArrayList<String>()

        fun flush() {
            val path = currentPath ?: return
            val hasTouchRange = hasMtPositionX && hasMtPositionY && maxX > 1 && maxY > 1
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
            if (!hasTouchRange) return
            // 手动覆盖优先；auto 时先按能力表初判，第一帧真正触摸再由嗅探纠正。
            val protocolMode = runCatching { protocolOverride() }
                .getOrDefault(MultiTouchGestureSettings.PROTOCOL_AUTO)
            val protocol = when (protocolMode) {
                MultiTouchGestureSettings.PROTOCOL_TYPE_A -> MtTouchFrameDecoder.Protocol.TYPE_A
                MultiTouchGestureSettings.PROTOCOL_TYPE_B -> MtTouchFrameDecoder.Protocol.TYPE_B
                else -> if (hasMtSlot && hasTrackingId) {
                    MtTouchFrameDecoder.Protocol.TYPE_B
                } else {
                    MtTouchFrameDecoder.Protocol.TYPE_A
                }
            }
            candidates += Touchscreen(
                eventPath = path,
                name = currentName,
                width = maxX.toFloat(),
                height = maxY.toFloat(),
                protocol = protocol,
                trackingIdOptionalHint = hasMtSlot && !hasTrackingId,
                hasSlot = hasMtSlot,
                hasTrackingId = hasTrackingId,
                hasBtnTouch = hasBtnTouch,
            )
        }

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("add device")) {
                flush()
                currentPath = "/dev/input/" + trimmed.substringAfterLast('/').trim()
                currentName = ""
                maxX = 0
                maxY = 0
                hasMtPositionX = false
                hasMtPositionY = false
                hasMtSlot = false
                hasTrackingId = false
                hasBtnTouch = false
                continue
            }
            if (currentPath == null) continue
            when {
                trimmed.startsWith("name:") ->
                    currentName = trimmed.substringAfter("name:").trim().trim('"')

                // ABS_MT_SLOT（0x2f）：Type B 才有的槽位。
                trimmed.startsWith("002f") -> hasMtSlot = true

                // ABS_MT_TRACKING_ID（0x39）：Type B 的手指编号。
                trimmed.startsWith("0039") -> hasTrackingId = true

                // BTN_TOUCH（0x14a）：没有 tracking id 时用它判断整体抬起。
                trimmed.startsWith("014a") -> hasBtnTouch = true

                trimmed.startsWith("0035") -> {
                    hasMtPositionX = true
                    maxX = MAX_PATTERN.find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: maxX
                }

                trimmed.startsWith("0036") -> {
                    hasMtPositionY = true
                    maxY = MAX_PATTERN.find(trimmed)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: maxY
                }
            }
        }
        flush()
        onDevices(
            candidates.map { device ->
                "${device.eventPath}|${device.name}|${device.protocol}|" +
                    "${device.width.toInt()}|${device.height.toInt()}"
            },
        )
        val overridePath = runCatching { devicePathOverride() }.getOrDefault("")
        val pool = if (overridePath.isBlank()) {
            candidates
        } else {
            candidates.filter { it.eventPath == overridePath }.ifEmpty {
                val available = candidates.joinToString { it.eventPath }
                log("指定触摸设备 $overridePath 不在候选里：$available")
                onDiagnostics("指定设备不存在：$overridePath（候选：$available）")
                return null
            }
        }
        if (pool.isEmpty()) return null
        val best = pool.maxWithOrNull(
            compareBy<Touchscreen> { scoreOf(it) }
                .thenBy { (it.width * it.height).toLong() },
        ) ?: pool.first()
        val diagnostics = "device=${best.eventPath} name=${best.name} protocol=${best.protocol} " +
            "slot=${best.hasSlot} track=${best.hasTrackingId} btn=${best.hasBtnTouch} " +
            "range=${best.width}x${best.height} candidates=${pool.size}"
        log("touch candidates: " + pool.joinToString(" | ") { device ->
            "${device.eventPath}(${device.name}," +
                "slot=${device.hasSlot},track=${device.hasTrackingId},btn=${device.hasBtnTouch}," +
                "range=${device.width}x${device.height},score=${scoreOf(device)})"
        })
        log(diagnostics)
        onDiagnostics(diagnostics)
        return best.copy(virtualDevices = virtualDevices.toList())
    }

    /**
     * 触摸设备打分：优先现代 Type B，其次有 BTN_TOUCH 的，最后按量程大小。
     * 名字像手写笔/数字化仪的降权（但不硬排除，避免误杀名字奇怪的机型）。
     */
    private fun scoreOf(device: Touchscreen): Int {
        var score = 0
        if (device.hasSlot && device.hasTrackingId) score += 8
        else if (device.hasSlot || device.hasTrackingId) score += 4
        if (device.hasBtnTouch) score += 2
        val name = device.name.lowercase()
        if (name.contains("pen") || name.contains("stylus") || name.contains("digitizer")) {
            score -= 6
        }
        return score
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

        /** 运行期冲突检测间隔：其它触控工具可能随时创建设备。 */
        const val CONFLICT_CHECK_INTERVAL_MS = 5_000L

        /** 点按延迟判定的定时器间隔。 */
        const val TICK_INTERVAL_MS = 40L
    }
}
