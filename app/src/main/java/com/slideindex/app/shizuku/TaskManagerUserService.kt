package com.slideindex.app.shizuku

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.StringRes
import com.slideindex.app.gesture.multitouch.TouchAnchor
import com.slideindex.app.gesture.multitouch.TouchDisplayTransform
import com.slideindex.app.multitouch.EvdevTouchObserver
import com.slideindex.app.multitouch.MultiTouchTriggerFilter
import com.slideindex.app.multitouch.encodePathStrokes
import com.slideindex.app.physicalkey.EvdevPhysicalKeyObserver
import com.slideindex.app.physicalkey.PhysicalKeyGesture
import java.util.concurrent.ConcurrentLinkedQueue

class TaskManagerUserService() : ITaskManagerService.Stub() {

    private var appContext: Context? = null

    /**
     * 当前屏幕方向/尺寸；app 通过 [setMultiTouchDisplay] 更新。
     *
     * 观察器每帧读取它，把面板坐标旋转成屏幕坐标（对齐 GMD 的入口旋转）。
     * 默认 IDENTITY = 不做换算，兼容还没拿到尺寸的极短窗口。
     */
    @Volatile
    private var displayTransform: TouchDisplayTransform = TouchDisplayTransform.IDENTITY

    /** 手动协议覆盖：auto / type_a / type_b。 */
    @Volatile
    private var protocolOverride: String = "auto"

    /** 指定触摸设备路径；空串 = 自动挑。 */
    @Volatile
    private var devicePathOverride: String = ""

    /** 最近一次观察诊断（设备/协议/候选），设置页读取。 */
    @Volatile
    private var multitouchDiagnostics: String = ""

    /** 最近一次枚举到的触摸设备候选，每行 `eventPath|name|protocol|width|height`。 */
    @Volatile
    private var touchDevices: Array<String> = emptyArray()

    /** 键盘可见时的策略：0 禁用 / 1 键盘上方 / 2 所有区域（全局一条）。 */
    @Volatile
    private var keyboardBehavior: Int = MultiTouchTriggerFilter.KEYBOARD_ABOVE

    private val keyboardMonitorLazy = lazy {
        KeyboardInsetsMonitor(
            dumpsysReader = { shell.shellOutput("/system/bin/dumpsys", "window") },
        )
    }

    private val keyboardMonitor: KeyboardInsetsMonitor
        get() = keyboardMonitorLazy.value

    private val shell = TaskManagerShellExecutor
    private val shellRunner = ShellCommandRunner { args -> shell.shellCommand(*args) }
    private val tasks = TaskManagerTaskOperations(shellRunner)
    private val shortcuts = TaskManagerShortcutResolver(shell)
    private val freeWindow =
        TaskManagerFreeWindowOperations(verifier = SystemRecentsAccess::freeWindowMoveStatus)

    private var touchObserver: EvdevTouchObserver? = null

    private var keyObserver: EvdevPhysicalKeyObserver? = null
    private var pollingKeyObserver: EvdevPhysicalKeyObserver? = null
    private val pollingKeyEvents = ConcurrentLinkedQueue<String>()
    @Volatile
    private var physicalKeyDiagnostics: String = ""

    @Keep
    constructor(context: Context) : this() {
        appContext = context.applicationContext
    }

    override fun destroy() {
        stopMultiTouchObservation()
        stopMultiTouchPolling()
        stopPhysicalKeyObservation()
        stopPhysicalKeyPolling()
        if (keyboardMonitorLazy.isInitialized()) {
            runCatching { keyboardMonitor.close() }
        }
        System.exit(0)
    }

    override fun setMultiTouchDisplay(rotation: Int, screenWidthPx: Int, screenHeightPx: Int) {
        displayTransform = TouchDisplayTransform(
            rotation = rotation,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
    }

    override fun setMultiTouchConfig(protocol: String?, devicePath: String?) {
        protocolOverride = protocol ?: "auto"
        devicePathOverride = devicePath ?: ""
    }

    /**
     * 键盘策略（全局一条）。
     *
     * 区域判定已下沉到 app（每个手势自带生效区域），所以 area / borderPx 只保留签名兼容，
     * 不再参与判定。
     */
    override fun setMultiTouchTrigger(area: Int, keyboardBehavior: Int, borderPx: Int) {
        this.keyboardBehavior = keyboardBehavior.coerceIn(
            MultiTouchTriggerFilter.KEYBOARD_BLOCKED,
            MultiTouchTriggerFilter.KEYBOARD_EVERYWHERE,
        )
    }

    /**
     * 键盘门控：键盘可见且策略不是"所有区域"时，按起点是否压在 IME 矩形内决定放不放行。
     *
     * 区域是每个手势自己的属性，由 app 按条目判定，这里不处理。
     */
    private fun acceptsKeyboard(start: TouchAnchor?): Boolean {
        val anchor = start ?: return true
        val keyboard = runCatching { keyboardMonitor.snapshot() }.getOrNull() ?: return true
        return MultiTouchTriggerFilter.keyboardAllows(
            behavior = keyboardBehavior,
            startU = anchor.u,
            startV = anchor.v,
            visible = keyboard.visible,
            hasBounds = keyboard.hasBounds(),
            left = keyboard.bounds.left,
            top = keyboard.bounds.top,
            right = keyboard.bounds.right,
            bottom = keyboard.bounds.bottom,
            displayWidth = keyboard.displayBounds.width(),
            displayHeight = keyboard.displayBounds.height(),
        )
    }

    override fun getMultiTouchDiagnostics(): String = multitouchDiagnostics

    override fun getTouchDevices(): Array<String> = touchDevices

    override fun startMultiTouchObservation(
        callback: IOnMultiTouchGesture,
        pathPriority: Boolean,
    ): Boolean {
        stopMultiTouchObservation()
        val observer = EvdevTouchObserver(
            displayTransform = { displayTransform },
            protocolOverride = { protocolOverride },
            devicePathOverride = { devicePathOverride },
            onDiagnostics = { multitouchDiagnostics = it },
            onDevices = { touchDevices = it.toTypedArray() },
            onGesture = { gestures, eventTimeMs, anchor, startAnchor ->
                if (acceptsKeyboard(startAnchor)) {
                    runCatching {
                        Log.i(
                            "MultiTouchBridge",
                            "batch: " + gestures.joinToString { "${it.gesture}x${it.fingerCount}" } +
                                " anchor=(${anchor.u},${anchor.v}) " +
                                "start=(${startAnchor?.u},${startAnchor?.v})",
                        )
                        callback.onGestureBatch(
                            gestures.map { it.gesture.ordinal }.toIntArray(),
                            gestures.map { it.fingerCount }.toIntArray(),
                            eventTimeMs,
                            anchor.u,
                            anchor.v,
                            startAnchor?.u ?: Float.NaN,
                            startAnchor?.v ?: Float.NaN,
                        )
                    }
                }
            },
            onPathGesture = { path, eventTimeMs, anchor, startAnchor ->
                if (acceptsKeyboard(startAnchor)) {
                    val coordinates = ArrayList<Float>()
                    val strokeLengths = IntArray(path.strokes.size)
                    path.strokes.forEachIndexed { index, stroke ->
                        strokeLengths[index] = stroke.points.size
                        for (point in stroke.points) {
                            coordinates += point.x
                            coordinates += point.y
                        }
                    }
                    runCatching {
                        callback.onPathGesture(
                            path.fingerCount,
                            coordinates.toFloatArray(),
                            strokeLengths,
                            eventTimeMs,
                            anchor.u,
                            anchor.v,
                            startAnchor?.u ?: Float.NaN,
                            startAnchor?.v ?: Float.NaN,
                        )
                    }
                }
            },
            onInputConflict = { devices ->
                android.util.Log.i("MultiTouchBridge", "notify conflict: $devices")
                runCatching { callback.onInputConflict(devices) }
            },
            onState = { running, detail ->
                // 「stopped」是 app 主动停止触发的：此时正在切换观察器，
                // 转发这条 false 会把新会话刚设好的 running=true 覆盖掉（页面显示"未运行"但手势正常）。
                // 真正的异常状态（no-touchscreen / reader-error）照常转发。
                if (running || detail != "stopped") {
                    runCatching { callback.onObserverState(running, detail) }
                }
            },
        )
        touchObserver = observer
        return observer.start()
    }

    override fun stopMultiTouchObservation() {
        touchObserver?.stop()
        touchObserver = null
    }

    override fun isMultiTouchObservationRunning(): Boolean =
        touchObserver?.isRunning() == true

    // ---- root 模式轮询（RPC 无法传 binder callback）----

    private val pollingEvents = ConcurrentLinkedQueue<String>()

    private var pollingObserver: EvdevTouchObserver? = null

    /**
     * root 模式：启动观察器，事件进队列，由 app 轮询取走。
     *
     * 事件格式（`u`/`v` 是面板自然坐标 0..1，见 [IOnMultiTouchGesture]）：
     * - `g:<手势 ordinal>:<触点数>:<u>:<v>:<起点u>:<起点v>`
     * - `p:<触点数>:<u>:<v>:<起点u>:<起点v>:<轨迹>`（轨迹里没有冒号，可以直接按 limit 切）
     */
    override fun startMultiTouchPolling(pathPriority: Boolean): Boolean {
        stopMultiTouchPolling()
        val observer = EvdevTouchObserver(
            displayTransform = { displayTransform },
            protocolOverride = { protocolOverride },
            devicePathOverride = { devicePathOverride },
            onDiagnostics = { multitouchDiagnostics = it },
            onDevices = { touchDevices = it.toTypedArray() },
            onGesture = { gestures, _, anchor, startAnchor ->
                if (acceptsKeyboard(startAnchor)) {
                    val startU = startAnchor?.u ?: Float.NaN
                    val startV = startAnchor?.v ?: Float.NaN
                    for (gesture in gestures) {
                        pollingEvents += "g:${gesture.gesture.ordinal}:${gesture.fingerCount}:" +
                            "${anchor.u}:${anchor.v}:$startU:$startV"
                    }
                }
            },
            onPathGesture = { path, _, anchor, startAnchor ->
                if (acceptsKeyboard(startAnchor)) {
                    val startU = startAnchor?.u ?: Float.NaN
                    val startV = startAnchor?.v ?: Float.NaN
                    pollingEvents += "p:${path.fingerCount}:${anchor.u}:${anchor.v}:" +
                        "$startU:$startV:" + encodePathStrokes(path.strokes)
                }
            },
            onState = { _, detail ->
                // 设备路径只取短名（event7），状态行不用完整的 /dev/input/...。
                if (detail.startsWith("watching:")) {
                    val device = detail.removePrefix("watching:").substringAfterLast('/')
                    pollingEvents += "d:$device"
                }
            },
            onInputConflict = { devices ->
                pollingEvents += "c:${devices?.takeIf { it.isNotBlank() } ?: ""}"
            },
        )
        val started = observer.start()
        if (!started) return false
        pollingObserver = observer
        return true
    }

    override fun pollMultiTouchEvents(): Array<String> {
        val events = ArrayList<String>()
        while (true) {
            events += pollingEvents.poll() ?: break
        }
        return events.toTypedArray()
    }

    override fun stopMultiTouchPolling() {
        pollingObserver?.stop()
        pollingObserver = null
        pollingEvents.clear()
    }

    override fun startPhysicalKeyObservation(callback: IOnPhysicalKeyGesture?): Boolean {
        callback ?: return false
        stopPhysicalKeyObservation()
        val observer = EvdevPhysicalKeyObserver(
            onGesture = { gesture, eventTimeMs ->
                runCatching {
                    callback.onKeyGesture(gesture.ordinal, eventTimeMs)
                }
            },
            onState = { running, detail ->
                if (running || detail != "stopped") {
                    runCatching { callback.onObserverState(running, detail) }
                }
            },
            onDiagnostics = { physicalKeyDiagnostics = it },
        )
        observer.updateTargets(configuredMappedKeyCodes, configuredDoublePressKeyCodes, configuredSinglePressKeyCodes)
        keyObserver = observer
        return observer.start()
    }

    override fun stopPhysicalKeyObservation() {
        keyObserver?.stop()
        keyObserver = null
    }

    override fun isPhysicalKeyObservationRunning(): Boolean =
        keyObserver?.isRunning() == true || pollingKeyObserver?.isRunning() == true

    override fun startPhysicalKeyPolling(): Boolean {
        stopPhysicalKeyPolling()
        val observer = EvdevPhysicalKeyObserver(
            onGesture = { gesture, eventTimeMs ->
                pollingKeyEvents += "k:${gesture.ordinal}:$eventTimeMs"
            },
            onState = { running, detail ->
                pollingKeyEvents += "s:${if (running) 1 else 0}:$detail"
            },
            onDiagnostics = { physicalKeyDiagnostics = it },
        )
        observer.updateTargets(configuredMappedKeyCodes, configuredDoublePressKeyCodes, configuredSinglePressKeyCodes)
        val started = observer.start()
        if (!started) return false
        pollingKeyObserver = observer
        return true
    }

    override fun pollPhysicalKeyEvents(): Array<String> {
        val events = ArrayList<String>()
        while (true) {
            events += pollingKeyEvents.poll() ?: break
        }
        return events.toTypedArray()
    }

    override fun stopPhysicalKeyPolling() {
        pollingKeyObserver?.stop()
        pollingKeyObserver = null
        pollingKeyEvents.clear()
    }

    override fun getPhysicalKeyDiagnostics(): String = physicalKeyDiagnostics

    @Volatile
    private var configuredMappedKeyCodes: Set<Int> = emptySet()

    @Volatile
    private var configuredDoublePressKeyCodes: Set<Int> = emptySet()

    @Volatile
    private var configuredSinglePressKeyCodes: Set<Int> = emptySet()

    override fun updatePhysicalKeyTargets(mappedKeyCodes: IntArray?, doublePressKeyCodes: IntArray?) {
        updatePhysicalKeyCustomTargets(mappedKeyCodes, doublePressKeyCodes, intArrayOf())
    }

    override fun updatePhysicalKeyCustomTargets(
        mappedKeyCodes: IntArray?,
        doublePressKeyCodes: IntArray?,
        singlePressKeyCodes: IntArray?,
    ) {
        val mapped = mappedKeyCodes?.toSet() ?: emptySet()
        val doublePress = doublePressKeyCodes?.toSet() ?: emptySet()
        val singlePress = singlePressKeyCodes?.toSet() ?: emptySet()
        configuredMappedKeyCodes = mapped
        configuredDoublePressKeyCodes = doublePress
        configuredSinglePressKeyCodes = singlePress

        keyObserver?.updateTargets(mapped, doublePress, singlePress)
        pollingKeyObserver?.updateTargets(mapped, doublePress, singlePress)
    }

    override fun removeTaskById(taskIdStr: String?): Boolean =
        tasks.removeTaskById(taskIdStr)

    override fun getFrontTaskId(): String =
        tasks.getFrontTaskId()

    override fun getFrontTaskPackage(): String =
        tasks.getFrontTaskPackage()

    override fun getApiVersion(): Int = API_VERSION

    override fun getTaskIdsForPackage(packageName: String?): Array<String> =
        tasks.getTaskIdsForPackage(packageName)

    override fun getRecentTaskPackages(): Array<String> =
        tasks.getRecentTaskPackages()

    override fun getRecentTasks(): Array<String> =
        tasks.getRecentTasks()

    override fun switchToTask(
        taskIdStr: String?,
        identifier: String?,
        topComponentStr: String?
    ): Boolean = tasks.switchToTask(taskIdStr, identifier, topComponentStr)

    override fun showVoiceAssistant(): Boolean =
        tasks.showVoiceAssistant()

    override fun runShellCommand(cmd: Array<out String>?): Boolean {
        if (cmd.isNullOrEmpty()) return false
        runViaSession(cmd.joinToString(" ") { shell.shellQuote(it) })?.let { return it.exitCode == 0 }
        return shell.shellCommand(*cmd)
    }

    override fun runShellCommandOutput(cmd: Array<out String>?): String {
        if (cmd.isNullOrEmpty()) return shell.formatShellOutput(-1, "Empty command")
        runViaSession(cmd.joinToString(" ") { shell.shellQuote(it) })?.let {
            return shell.formatShellOutput(it.exitCode, it.output)
        }
        val result = shell.shellCommandWithOutput(*cmd)
        return shell.formatShellOutput(result.exitCode, result.output)
    }

    override fun runShellCommandLine(command: String?, useRoot: Boolean, forceAdb: Boolean): String {
        val trimmed = command?.trim().orEmpty()
        if (trimmed.isEmpty()) return shell.formatShellOutput(-1, "Empty command")
        val wantRoot = useRoot && !forceAdb
        val result = when {
            wantRoot -> shell.runAsRootUser(trimmed)
            // shell 身份下走常驻会话：单次从 ~100ms 降到几毫秒（亮度/音量的连续调节靠它）。
            else -> runViaSession(trimmed) ?: shell.runAsShellUser(trimmed, appContext)
        }
        return shell.formatShellOutput(result.exitCode, result.output)
    }

    /**
     * 走常驻 sh 会话；root 身份（或会话不可用）时返回 null，由调用方回退到一次性执行。
     */
    private fun runViaSession(line: String): TaskManagerShellExecutor.ShellExecResult? {
        if (android.os.Process.myUid() == 0) return null
        return PrivilegedShellSession.run(line, SESSION_TIMEOUT_MS)
    }

    override fun probeRootAvailable(): Boolean =
        shell.probeRootAvailable()

    override fun getTetheringState(): Int = TetheringOps.getState()

    override fun setTetheringEnabled(enable: Boolean): Boolean = TetheringOps.setEnabled(enable)

    override fun forceStopPackage(packageName: String?): Boolean =
        tasks.forceStopPackage(packageName)

    override fun getPublishedShortcuts(packageName: String?): Array<String> =
        shortcuts.getPublishedShortcuts(packageName)

    override fun getAllPublishedShortcuts(): Array<String> =
        shortcuts.getAllPublishedShortcuts()

    override fun startPublishedShortcut(packageName: String?, shortcutId: String?): Boolean =
        tasks.startPublishedShortcut(packageName, shortcutId)

    override fun getShortcutIconBytes(
        packageName: String?,
        shortcutId: String?,
        userId: Int,
    ): ByteArray? {
        val context = appContext ?: return null
        if (packageName.isNullOrBlank() || shortcutId.isNullOrBlank()) return null
        return ShortcutIconBitmapLoader.loadPngBytes(packageName, shortcutId, userId)
    }

    override fun moveTaskToFreeWindow(
        taskIdStr: String?,
        windowingMode: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Boolean = freeWindow.moveTaskToFreeWindow(taskIdStr, windowingMode, left, top, right, bottom)

    companion object {
        /** 单条会话命令的超时：远大于正常 `settings put`（几毫秒），只兜底卡死。 */
        private const val SESSION_TIMEOUT_MS = 30_000L

        const val API_VERSION = ShizukuUserServiceHost.SERVICE_BUILD
        @StringRes
        val SHELL_DOWNGRADE_HINT_RES: Int = TaskManagerShellExecutor.SHELL_DOWNGRADE_HINT_RES
    }
}
