package com.slideindex.app.shizuku

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.StringRes
import com.slideindex.app.multitouch.EvdevTouchObserver
import com.slideindex.app.multitouch.encodePathStrokes
import java.util.concurrent.ConcurrentLinkedQueue

class TaskManagerUserService() : ITaskManagerService.Stub() {

    private var appContext: Context? = null

    private val shell = TaskManagerShellExecutor
    private val shellRunner = ShellCommandRunner { args -> shell.shellCommand(*args) }
    private val tasks = TaskManagerTaskOperations(shellRunner)
    private val shortcuts = TaskManagerShortcutResolver(shell)
    private val freeWindow =
        TaskManagerFreeWindowOperations(verifier = SystemRecentsAccess::freeWindowMoveStatus)

    private var touchObserver: EvdevTouchObserver? = null

    @Keep
    constructor(context: Context) : this() {
        appContext = context.applicationContext
    }

    override fun destroy() {
        stopMultiTouchObservation()
        System.exit(0)
    }

    override fun startMultiTouchObservation(
        callback: IOnMultiTouchGesture,
        pathPriority: Boolean,
    ): Boolean {
        stopMultiTouchObservation()
        val observer = EvdevTouchObserver(
            onGesture = { gestures, eventTimeMs, anchor ->
                runCatching {
                    Log.i(
                        "MultiTouchBridge",
                        "batch: " + gestures.joinToString { "${it.gesture}x${it.fingerCount}" } +
                            " anchor=(${anchor.u},${anchor.v})",
                    )
                    callback.onGestureBatch(
                        gestures.map { it.gesture.ordinal }.toIntArray(),
                        gestures.map { it.fingerCount }.toIntArray(),
                        eventTimeMs,
                        anchor.u,
                        anchor.v,
                    )
                }
            },
            onPathGesture = { path, eventTimeMs, anchor ->
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
                    )
                }
            },
            onInputConflict = { devices ->
                android.util.Log.i("MultiTouchBridge", "notify conflict: $devices")
                runCatching { callback.onInputConflict(devices) }
            },
            onState = { running, detail ->
                runCatching { callback.onObserverState(running, detail) }
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
     * - `g:<手势 ordinal>:<触点数>:<u>:<v>`
     * - `p:<触点数>:<u>:<v>:<轨迹>`（轨迹里没有冒号，可以直接按 limit 切）
     */
    override fun startMultiTouchPolling(pathPriority: Boolean): Boolean {
        stopMultiTouchPolling()
        val observer = EvdevTouchObserver(
            onGesture = { gestures, _, anchor ->
                for (gesture in gestures) {
                    pollingEvents += "g:${gesture.gesture.ordinal}:${gesture.fingerCount}:" +
                        "${anchor.u}:${anchor.v}"
                }
            },
            onPathGesture = { path, _, anchor ->
                pollingEvents += "p:${path.fingerCount}:${anchor.u}:${anchor.v}:" +
                    encodePathStrokes(path.strokes)
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
