package com.slideindex.app.physicalkey

import android.os.SystemClock
import android.util.Log
import io.github.sds100.keymapper.common.models.GrabTargetKeyCode
import io.github.sds100.keymapper.sysbridge.service.SystemBridge
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * 在 Shizuku / Root 的 shell 用户服务里读取物理按键 evdev 原始事件，识别实体按键手势。
 *
 * 为什么读 `/dev/input/event*`：
 * 1. Android 原生无障碍服务的 onKeyEvent 出于安全限制过滤掉了电源键（KEYCODE_POWER），
 *    且在息屏时系统会休眠无障碍事件分发；
 * 2. shell 用户（uid 2000）归属于 input 组，天然拥有 `/dev/input/event*` 读取权限；
 * 3. 这是底层只读监听，不破坏原有系统按键传递，同时支持电源键与黑屏盲操。
 */
class EvdevPhysicalKeyObserver(
    private val onGesture: (PhysicalKeyGesture, Long) -> Unit,
    private val onState: (Boolean, String) -> Unit = { _, _ -> },
    private val onDiagnostics: (String) -> Unit = {},
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {
    @Volatile
    private var running = false

    private val readerWorkers = CopyOnWriteArrayList<Thread>()
    private val openStreams = CopyOnWriteArrayList<FileInputStream>()

    private class PendingClick(
        val deviceId: Int,
        val keyCode: Int,
        val timeMs: Long,
    )

    private val pendingClicks = java.util.concurrent.ConcurrentHashMap<Int, PendingClick>()
    private val longPressConsumedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    var doublePressKeyCodes: Set<Int> = emptySet()

    @Volatile
    private var systemBridge: SystemBridge? = null

    @Volatile
    private var tickWorker: Thread? = null

    private val recognizer = PhysicalKeyGestureRecognizer()

    fun start(): Boolean {
        if (running) return true
        running = true

        val devices = findKeyDevices()
        if (devices.isEmpty()) {
            log("no physical key devices found via getevent -p")
            onState(false, "no-key-devices")
            // 跨进程诊断数据，随设备状态变化，不是可翻译的界面文案。
            onDiagnostics("未找到包含电源键或音量键的硬件输入设备") // i18n-ignore
            running = false
            return false
        }

        val diag = "devices=" + devices.joinToString(" | ") { "${it.eventPath}(${it.name},keys=${it.keys.joinToString()})" }
        log("key candidates: $diag")
        onDiagnostics(diag)
        onState(true, "starting:${devices.size}")

        var nativeGrabbedDeviceNames = emptySet<String>()

        // 尝试加载 KeyMapper 原生 libevdev_manager 独占拦截引擎
        val bridge = SystemBridge.getOrCreate()
        if (bridge != null) {
            log("native evdev_manager engine ready, setting up listener...")
            runCatching {
                bridge.listener = object : SystemBridge.Listener {
                    override fun onEvdevEvent(
                        deviceId: Int,
                        timeSec: Long,
                        timeUsec: Long,
                        type: Int,
                        code: Int,
                        value: Int,
                        androidCode: Int,
                    ): Boolean {
                        if (type == EV_KEY) {
                            val nowMs = SystemClock.elapsedRealtime()

                            if (value == 1) {
                                longPressConsumedKeys.remove(code)
                                pendingClicks.remove(code)?.let {
                                    log("native engine: double press in progress for keyCode=$code, suppressed single click emission")
                                }
                                val gestures = synchronized(recognizer) {
                                    recognizer.onKeyEvent(code, true, nowMs)
                                }
                                handleGestures(gestures, nowMs)
                                return true // 独占拦截按下中断，防止系统 PhoneWindowManager 提前计时关机或调音
                            } else if (value == 2) {
                                val gestures = synchronized(recognizer) {
                                    recognizer.onKeyEvent(code, true, nowMs)
                                }
                                handleGestures(gestures, nowMs)
                                return true
                            } else if (value == 0) {
                                val gestures = synchronized(recognizer) {
                                    recognizer.onKeyEvent(code, false, nowMs)
                                }
                                val isDoublePress = gestures.any {
                                    it == PhysicalKeyGesture.POWER_DOUBLE_PRESS ||
                                        it == PhysicalKeyGesture.VOLUME_UP_DOUBLE_PRESS ||
                                        it == PhysicalKeyGesture.VOLUME_DOWN_DOUBLE_PRESS
                                }
                                handleGestures(gestures, nowMs)

                                if (longPressConsumedKeys.remove(code)) {
                                    log("native engine: keyCode=$code released after long press, suppressed")
                                    return true
                                } else if (isDoublePress) {
                                    log("native engine: keyCode=$code double press triggered, suppressing uinput to avoid conflicting system actions")
                                    return true
                                } else if (code in doublePressKeyCodes) {
                                    // 仅当用户为该按键配置了双击手势时才启动消歧等待（若用户按第2下则取消回写）
                                    pendingClicks[code] = PendingClick(deviceId, code, nowMs)
                                    return true
                                } else {
                                    // 未配置双击手势：直接放行（0ms 延迟，恢复系统原生即时锁屏与调音）
                                    log("native engine: keyCode=$code has no double-press mapping, bypassing disambiguation directly")
                                    return false
                                }
                            }
                        }
                        return false
                    }
                }

                val evdevDevices = bridge.getEvdevDevicesNative()
                log("native evdev devices found: ${evdevDevices.size}")
                val targetDeviceNames = devices.associateBy { it.name }
                val grabTargets = evdevDevices.filter { dev ->
                    targetDeviceNames.containsKey(dev.name) ||
                        dev.name.contains("pwrkey", ignoreCase = true) ||
                        dev.name.contains("power", ignoreCase = true) ||
                        dev.name.contains("gpio-keys", ignoreCase = true) ||
                        dev.name.contains("resin", ignoreCase = true)
                }.map { dev ->
                    val keyCodes = targetDeviceNames[dev.name]?.keys?.toIntArray()
                        ?: intArrayOf(KEY_POWER, KEY_VOLUMEUP, KEY_VOLUMEDOWN)
                    GrabTargetKeyCode(
                        name = dev.name,
                        bus = dev.bus,
                        vendor = dev.vendor,
                        product = dev.product,
                        extraKeyCodes = keyCodes,
                    )
                }.toTypedArray()

                if (grabTargets.isNotEmpty()) {
                    val grabbed = bridge.setGrabTargetsNative(grabTargets)
                    nativeGrabbedDeviceNames = grabbed.map { it.name }.toSet()
                    log("grabbed targets via native engine: ${grabbed.size} devices grabbed successfully ($nativeGrabbedDeviceNames)")
                }

                systemBridge = bridge
            }.onFailure {
                log("failed to configure SystemBridge: ${it.message}")
            }
        }

        // 为每一个未被原生引擎成功独占的按键设备启动独立读取线程（非独占设备监听或降级只读监听）
        for (device in devices) {
            if (device.name in nativeGrabbedDeviceNames) {
                log("skip java reader for ${device.eventPath} (${device.name} handled by native engine)")
                continue
            }
            val thread = Thread({ readDeviceLoop(device) }, "evdev-key-${device.eventPath.substringAfterLast('/')}").apply {
                isDaemon = true
                start()
            }
            readerWorkers.add(thread)
        }

        // 启动长按判定的心跳定时线程
        tickWorker = Thread({ tickLoop() }, "evdev-key-tick").apply {
            isDaemon = true
            start()
        }

        onState(true, "watching:${devices.map { it.eventPath }}")
        return true
    }

    fun stop() {
        running = false
        pendingClicks.clear()
        longPressConsumedKeys.clear()

        systemBridge?.let { bridge ->
            runCatching {
                bridge.setGrabTargetsNative(emptyArray())
                bridge.listener = null
                log("SystemBridge grab released")
            }
            systemBridge = null
        }

        for (stream in openStreams) {
            runCatching { stream.close() }
        }
        openStreams.clear()

        for (worker in readerWorkers) {
            worker.interrupt()
        }
        readerWorkers.clear()

        tickWorker?.interrupt()
        tickWorker = null

        recognizer.reset()
        onState(false, "stopped")
    }

    fun isRunning(): Boolean = running && (readerWorkers.any { it.isAlive } || systemBridge != null)

    private fun handleGestures(gestures: List<PhysicalKeyGesture>, nowMs: Long) {
        for (gesture in gestures) {
            when (gesture) {
                PhysicalKeyGesture.POWER_LONG_PRESS -> longPressConsumedKeys.add(KEY_POWER)
                PhysicalKeyGesture.VOLUME_UP_LONG_PRESS -> longPressConsumedKeys.add(KEY_VOLUMEUP)
                PhysicalKeyGesture.VOLUME_DOWN_LONG_PRESS -> longPressConsumedKeys.add(KEY_VOLUMEDOWN)
                else -> {}
            }
            log("recognized physical key gesture: $gesture")
            onGesture(gesture, nowMs)
        }
    }

    private fun readDeviceLoop(device: KeyDevice) {
        val eventPath = device.eventPath
        val stream = try {
            FileInputStream(eventPath).also { openStreams.add(it) }
        } catch (e: Exception) {
            log("failed to open key device $eventPath: ${e.message}")
            return
        }

        val buffer = ByteArray(EVENT_SIZE)
        try {
            while (running) {
                if (!readFully(stream, buffer)) break

                val type = readShort(buffer, 16)
                val code = readShort(buffer, 18)
                val value = readInt(buffer, 20)

                if (type == EV_KEY) {
                    val isDown = value == 1 || value == 2
                    val nowMs = SystemClock.elapsedRealtime()

                    val gestures = synchronized(recognizer) {
                        recognizer.onKeyEvent(code, isDown, nowMs)
                    }
                    handleGestures(gestures, nowMs)
                }
            }
        } catch (e: Throwable) {
            if (running) {
                log("key reader for $eventPath stopped: ${e.message}")
            }
        } finally {
            runCatching { stream.close() }
            openStreams.remove(stream)
        }
    }

    private fun tickLoop() {
        while (running) {
            sleepQuietly(TICK_INTERVAL_MS)
            val nowMs = SystemClock.elapsedRealtime()

            // 检查待发射的单键回写（双击消歧窗口确认）
            val bridge = systemBridge
            if (bridge != null && pendingClicks.isNotEmpty()) {
                val it = pendingClicks.entries.iterator()
                while (it.hasNext()) {
                    val entry = it.next()
                    val pending = entry.value
                    if (nowMs - pending.timeMs >= DOUBLE_TAP_DISAMBIGUATION_MS) {
                        it.remove()
                        log("native engine: single click confirmed for keyCode=${pending.keyCode} after double-tap window, emitting to uinput")
                        emitUinputClick(bridge, pending.deviceId, pending.keyCode)
                    }
                }
            }

            val gestures = synchronized(recognizer) {
                recognizer.onTick(nowMs)
            }
            handleGestures(gestures, nowMs)
        }
    }

    private fun emitUinputClick(bridge: SystemBridge, deviceId: Int, keyCode: Int) {
        runCatching {
            bridge.writeEvdevEventNative(deviceId, EV_KEY, keyCode, 1)
            bridge.writeEvdevEventNative(deviceId, 0 /* EV_SYN */, 0, 0)
            SystemClock.sleep(15)
            bridge.writeEvdevEventNative(deviceId, EV_KEY, keyCode, 0)
            bridge.writeEvdevEventNative(deviceId, 0 /* EV_SYN */, 0, 0)
        }
    }

    private data class KeyDevice(
        val eventPath: String,
        val name: String,
        val keys: List<Int>,
    )

    private fun findKeyDevices(): List<KeyDevice> {
        val output = runCatching {
            val process = ProcessBuilder("/system/bin/getevent", "-p")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(3, TimeUnit.SECONDS)
            text
        }.getOrNull() ?: return emptyList()

        var currentPath: String? = null
        var currentName = ""
        val currentKeys = mutableListOf<Int>()
        var inKeySection = false

        val candidates = ArrayList<KeyDevice>()

        fun flush() {
            val path = currentPath ?: return
            val isVirtual = currentName.contains("uinput", ignoreCase = true) ||
                currentName.contains("virtual", ignoreCase = true) ||
                currentName.contains("relay", ignoreCase = true) ||
                currentName.contains("touch", ignoreCase = true) ||
                currentName.contains("qbt", ignoreCase = true)
            if (!isVirtual && currentKeys.isNotEmpty()) {
                candidates.add(
                    KeyDevice(
                        eventPath = path,
                        name = currentName,
                        keys = currentKeys.toList(),
                    )
                )
            }
        }

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("add device")) {
                flush()
                currentPath = "/dev/input/" + trimmed.substringAfterLast('/').trim()
                currentName = ""
                currentKeys.clear()
                inKeySection = false
                continue
            }
            if (currentPath == null) continue

            when {
                trimmed.startsWith("name:") -> {
                    currentName = trimmed.substringAfter("name:").trim().trim('"')
                }
                trimmed.startsWith("KEY (0001):") || trimmed.startsWith("events:") -> {
                    inKeySection = trimmed.startsWith("KEY (0001):")
                    if (inKeySection) {
                        parseKeyCodes(trimmed.substringAfter("KEY (0001):"), currentKeys)
                    }
                }
                inKeySection -> {
                    if (trimmed.startsWith("00") || trimmed.startsWith("01")) {
                        parseKeyCodes(trimmed, currentKeys)
                    } else if (trimmed.contains(':')) {
                        inKeySection = false
                    }
                }
            }
        }
        flush()

        return candidates
    }

    private fun parseKeyCodes(text: String, dest: MutableList<Int>) {
        val tokens = text.split("\\s+".toRegex())
        for (token in tokens) {
            val hex = token.trim()
            if (hex.isBlank()) continue
            val code = hex.toIntOrNull(16) ?: continue
            if (code == KEY_VOLUMEDOWN || code == KEY_VOLUMEUP || code == KEY_POWER) {
                if (!dest.contains(code)) {
                    dest.add(code)
                }
            }
        }
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

    companion object {
        private const val TAG = "EvdevKeyObserver"

        const val EV_KEY = 1

        const val KEY_VOLUMEDOWN = 114
        const val KEY_VOLUMEUP = 115
        const val KEY_POWER = 116

        /** 64 位 timeval 结构：16 字节时间戳 + 2 字节 type + 2 字节 code + 4 字节 value = 24 字节。 */
        const val EVENT_SIZE = 24

        const val TICK_INTERVAL_MS = 40L
        const val DOUBLE_TAP_DISAMBIGUATION_MS = 280L
    }
}
