package com.slideindex.app.multitouch

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import com.slideindex.app.R
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.capturesScreen
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.gesture.multitouch.MultiTouchGestureEntry
import com.slideindex.app.gesture.multitouch.MultiTouchTriggerArea
import com.slideindex.app.gesture.multitouch.PathGestureMatcher
import com.slideindex.app.gesture.multitouch.PathGestureTemplate
import com.slideindex.app.gesture.multitouch.PathPoint
import com.slideindex.app.gesture.multitouch.PathStroke
import com.slideindex.app.gesture.multitouch.ScreenAnchor
import com.slideindex.app.gesture.multitouch.TouchAnchor
import com.slideindex.app.gesture.multitouch.forDisplayRotation
import com.slideindex.app.gesture.multitouch.mapTouchAnchorToScreen
import com.slideindex.app.overlay.OverlayScreenMetrics
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shake.ShakeVibrationHelper
import com.slideindex.app.shizuku.IOnMultiTouchGesture
import com.slideindex.app.shizuku.ITaskManagerService
import com.slideindex.app.shizuku.ShizukuUserServiceHost
import com.slideindex.app.privilege.root.RootTaskManagerServiceHost
import com.slideindex.app.privilege.root.TaskManagerRpcClient
import com.slideindex.app.ui.gestureKindLabelRes
import com.slideindex.app.util.OverlaySuppression
import com.slideindex.app.util.OverlaySuppressionScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

/** 多指手势运行状态（设置页展示用）。 */
data class MultiTouchStatus(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val detail: String = "",
    val shizukuAvailable: Boolean = false,
    /** 当前观察通道："root" / "shizuku"；未运行时为空。 */
    val channel: String = "",
    /** 触摸设备路径（如 `/dev/input/event7`）；root 模式可能为空。 */
    val device: String = "",
    /** 其它触控工具创建的虚拟触摸设备名；非空表示存在冲突风险。 */
    val conflictDevices: String? = null,
)

/**
 * 多指手势总协调器（app 主进程）。
 *
 * 识别在 Shizuku 用户服务里完成，这里只做“条目匹配 + 动作执行 + 反馈”。
 * 同一手势可能同时命中内置条目与路径条目，用 [ACTION_COOLDOWN_MS] 去重。
 */
@Singleton
class MultiTouchGestureCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: MultiTouchGestureSettingsStore,
    private val settingsRepository: SettingsRepository,
    private val appRepository: AppRepository,
    private val applicationScope: CoroutineScope,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val syncMutex = Mutex()

    private val _status = MutableStateFlow(MultiTouchStatus())
    val status: StateFlow<MultiTouchStatus> = _status.asStateFlow()

    @Volatile
    private var started = false

    @Volatile
    private var observingService: ITaskManagerService? = null

    /**
     * service 是否支持「入口旋转」。
     *
     * true：触点、路径、锚点都已经是屏幕坐标，app 侧不再做方向翻译；
     * false：旧 service / 旧 root 守护进程，回退到 app 侧的 [forDisplayRotation] 重映射。
     */
    @Volatile
    private var serviceHandlesRotation = false

    private var displayListener: DisplayManager.DisplayListener? = null

    @Volatile
    private var rootService: TaskManagerRpcClient? = null

    private var rootPollJob: Job? = null

    @Volatile
    private var lastActionAtMs = 0L

    private var reconnectJob: Job? = null

    private val serviceDeathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "observingService binder died, scheduling reconnect")
        observingService = null
        _status.value = _status.value.copy(running = false, detail = "binder-died")
        scheduleAutoReconnect(1000L)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_USER_PRESENT) {
                checkChannelHealthAndRecover()
            }
        }
    }

    private fun checkChannelHealthAndRecover() {
        if (!settingsStore.state.value.enabled) return
        val globalSettings = runCatching { settingsRepository.readSnapshot() }.getOrNull()
        if (globalSettings?.serviceEnabled == false) return

        val s = observingService
        val r = rootService
        val isAlive = (s != null && runCatching { s.asBinder().pingBinder() }.getOrDefault(false)) ||
            (r != null && runCatching { r.apiVersion }.getOrNull() != null)
        if (!isAlive) {
            Log.i(TAG, "screen wakeup check: multi-touch channel is inactive, auto recovering...")
            scheduleAutoReconnect(300L)
        }
    }

    private fun scheduleAutoReconnect(delayMs: Long) {
        reconnectJob?.cancel()
        reconnectJob = applicationScope.launch(Dispatchers.IO) {
            delay(delayMs)
            if (settingsStore.state.value.enabled) {
                Log.i(TAG, "auto reconnecting multi-touch observation...")
                syncNow()
            }
        }
    }

    private val callback = object : IOnMultiTouchGesture.Stub() {
        override fun onGesture(gesture: Int, fingerCount: Int, eventTimeMs: Long) {
            // 旧接口（服务端已改用 onGestureBatch，这里保留兼容）：没有锚点与起点。
            dispatchGesture(gesture, fingerCount, anchor = null, start = null)
        }

        override fun onGestureBatch(
            kinds: IntArray?,
            fingerCounts: IntArray?,
            eventTimeMs: Long,
            anchorU: Float,
            anchorV: Float,
            startU: Float,
            startV: Float,
        ) {
            val kindArray = kinds ?: return
            val fingerArray = fingerCounts ?: return
            if (kindArray.size != fingerArray.size) return
            val anchor = TouchAnchor(anchorU.coerceIn(0f, 1f), anchorV.coerceIn(0f, 1f))
            val start = startAnchorOrNull(startU, startV)
            for (index in kindArray.indices) {
                if (dispatchGesture(kindArray[index], fingerArray[index], anchor, start)) return
            }
        }

        override fun onPathGesture(
            fingerCount: Int,
            points: FloatArray?,
            strokeLengths: IntArray?,
            eventTimeMs: Long,
            anchorU: Float,
            anchorV: Float,
            startU: Float,
            startV: Float,
        ) {
            val coordinates = points ?: return
            val lengths = strokeLengths ?: return
            if (lengths.isEmpty() || lengths.any { it < 2 }) return
            if (coordinates.size != lengths.sum() * 2) return
            val strokes = ArrayList<PathStroke>(lengths.size)
            var offset = 0
            for (length in lengths) {
                val strokePoints = ArrayList<PathPoint>(length)
                repeat(length) {
                    strokePoints += PathPoint(coordinates[offset], coordinates[offset + 1])
                    offset += 2
                }
                strokes += PathStroke(strokePoints)
            }
            handlePathStrokes(
                fingerCount = fingerCount,
                strokes = strokes,
                anchor = TouchAnchor(anchorU.coerceIn(0f, 1f), anchorV.coerceIn(0f, 1f)),
                start = startAnchorOrNull(startU, startV),
            )
        }

        override fun onObserverState(running: Boolean, detail: String) {
            // 旧版 service 仍会转发「stopped」：主动停止时新会话可能已经开始，不能再把它当成当前状态。
            if (!running && detail == "stopped") return
            val device = if (detail.startsWith("watching:")) {
                detail.removePrefix("watching:")
            } else {
                _status.value.device
            }
            _status.value = _status.value.copy(
                running = running,
                detail = detail,
                channel = if (running) "shizuku" else "",
                device = device,
            )
        }

        override fun onInputConflict(devices: String?) {
            val value = devices?.takeIf { it.isNotBlank() }
            Log.i(TAG, "input conflict: $value")
            _status.value = _status.value.copy(conflictDevices = value)
        }
    }

    /** 路径轨迹与条目匹配（Shizuku 回调和 root 轮询共用）。 */
    private fun handlePathStrokes(
        fingerCount: Int,
        strokes: List<PathStroke>,
        anchor: TouchAnchor? = null,
        start: TouchAnchor? = null,
    ) {
        if (strokes.isEmpty()) return
        val candidates = settingsStore.state.value.entries
            .filter {
                it.isPath && it.enabled && it.fingerCount == fingerCount &&
                    it.pathStrokes.isNotEmpty() &&
                    areaAllowsPath(it, strokes, start)
            }
        if (candidates.isEmpty()) return
        val templates = candidates.map { entry ->
            PathGestureTemplate(
                id = entry.id,
                name = entry.id,
                strokes = entry.pathStrokes,
                action = entry.action,
            )
        }
        val match = PathGestureMatcher.bestMatch(strokes, templates) ?: return
        if (match.action == GestureAction.None) return
        val matchedEntry = candidates.firstOrNull { it.id == match.id }
        Log.i(TAG, "path gesture -> ${match.id}")
        mainHandler.post {
            performAction(
                match.action,
                MultiPointerGesture.PATH,
                fingerCount,
                anchor,
                name = matchedEntry?.name.orEmpty(),
                showHint = matchedEntry?.showHint ?: true,
            )
        }
    }

    /** 服务端没给起点时（旧协议 / 延迟结算）用 NaN 表示；这里统一转成 null。 */
    private fun startAnchorOrNull(u: Float, v: Float): TouchAnchor? {
        if (!u.isFinite() || !v.isFinite()) return null
        return TouchAnchor(u.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    }

    /** 条目区域判定（几何手势）：起点落在条目区域里才允许。 */
    private fun areaAllowsEntry(entry: MultiTouchGestureEntry, start: TouchAnchor?): Boolean {
        if (entry.triggerArea == MultiTouchTriggerArea.ANYWHERE) return true
        if (start == null) return true
        val metrics = runCatching { OverlayScreenMetrics.snapshot(context) }.getOrNull()
            ?: return true
        if (metrics.widthPx <= 0 || metrics.heightPx <= 0) return true
        val point = resolveTriggerAnchor(start)
        return MultiTouchTriggerFilter.areaAllows(
            area = MultiTouchTriggerFilter.areaId(entry.triggerArea),
            startU = point.x / metrics.widthPx,
            startV = point.y / metrics.heightPx,
            widthPx = metrics.widthPx,
            heightPx = metrics.heightPx,
        )
    }

    /**
     * 条目区域判定（绘制手势）：整条轨迹任意点命中即可（GMD 语义）。
     *
     * 新服务已把轨迹点旋转到屏幕坐标（按屏高归一化），换算成 u/v 后逐点判；
     * 旧服务（未做入口旋转）没有可靠的点坐标，退回按起点判。
     */
    private fun areaAllowsPath(
        entry: MultiTouchGestureEntry,
        strokes: List<PathStroke>,
        start: TouchAnchor?,
    ): Boolean {
        if (entry.triggerArea == MultiTouchTriggerArea.ANYWHERE) return true
        val metrics = runCatching { OverlayScreenMetrics.snapshot(context) }.getOrNull()
        if (!serviceHandlesRotation || metrics == null ||
            metrics.widthPx <= 0 || metrics.heightPx <= 0
        ) {
            return areaAllowsEntry(entry, start)
        }
        val scale = metrics.heightPx.toFloat() / metrics.widthPx
        val points = ArrayList<TouchAnchor>(strokes.size * 8)
        for (stroke in strokes) {
            for (point in stroke.points) {
                points += TouchAnchor(
                    u = (point.x * scale).coerceIn(0f, 1f),
                    v = point.y.coerceIn(0f, 1f),
                )
            }
        }
        if (points.isEmpty()) return areaAllowsEntry(entry, start)
        return MultiTouchTriggerFilter.areaAllowsAny(
            area = MultiTouchTriggerFilter.areaId(entry.triggerArea),
            points = points,
            widthPx = metrics.widthPx,
            heightPx = metrics.heightPx,
        )
    }

    fun start() {
        if (started) return
        started = true
        if (!com.slideindex.app.util.AppProcess.isMain) return
        Log.i(TAG, "MultiTouchCoordinator.start() initialized")
        runCatching { registerDisplayListener() }
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        }
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                screenReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        ShizukuUserServiceHost.onDisconnectedListener = {
            Log.w(TAG, "ShizukuUserServiceHost disconnected, scheduling reconnect")
            observingService = null
            _status.value = _status.value.copy(running = false, detail = "service-disconnected")
            scheduleAutoReconnect(1000L)
        }
        applicationScope.launch(Dispatchers.IO) {
            // 先按磁盘最新鲜快照跑一次初始同步，确保冷启动立刻恢复手势观察
            syncNow()
            // 组合手势配置与全局特权/服务设置，监听后续变更
            combine(
                settingsStore.state.map { Triple(it.enabled, it.protocol, it.devicePath) },
                settingsRepository.settings.map { Pair(it.privilegeMode, it.serviceEnabled) },
            ) { gestureSettings, globalSettings ->
                Pair(gestureSettings, globalSettings)
            }
                .distinctUntilChanged()
                .collect { (_, globalSettings) ->
                    val (privilegeMode, serviceEnabled) = globalSettings
                    Log.i(TAG, "settings flow changed: privilegeMode=$privilegeMode serviceEnabled=$serviceEnabled")
                    syncNow(
                        targetPrivilegeMode = privilegeMode,
                        serviceEnabledOverride = serviceEnabled,
                    )
                }
        }
        // 键盘策略是全局一条：只推给正在观察的 service，不重启观察器（改设置不该打断手势）。
        applicationScope.launch(Dispatchers.IO) {
            settingsStore.state
                .map { it.keyboardBehavior }
                .distinctUntilChanged()
                .collect {
                    val service = observingService ?: rootService ?: return@collect
                    pushMultiTouchTrigger(service)
                }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        applicationScope.launch(Dispatchers.IO) { syncNow() }
    }

    fun restart() {
        applicationScope.launch(Dispatchers.IO) {
            runCatching { ShizukuUserServiceHost.drop(context) }
            syncNow()
        }
    }

    /** 诊断：service 里观察器的当前设备/协议状态，设置页展示用。 */
    suspend fun readDiagnostics(): String {
        val service = observingService ?: rootService ?: return ""
        return runCatching { service.getMultiTouchDiagnostics() }.getOrDefault("")
    }

    /** 触摸设备候选（每行 `eventPath|name|protocol|width|height`），设置页选择用。 */
    suspend fun readTouchDevices(): List<String> {
        val service = observingService ?: rootService ?: return emptyList()
        return runCatching { service.touchDevices.toList() }.getOrDefault(emptyList())
    }

    /**
     * 设置页打开时刷新一次状态。
     *
     * 只校准「开关值」这个真实来源；如果确实没有任何观察通道，再补一次完整同步。
     * 不在有通道时重启观察器，避免开页面就打断手势。
     */
    fun refreshStatus() {
        applicationScope.launch(Dispatchers.IO) {
            val settings = settingsStore.state.value
            _status.value = _status.value.copy(enabled = settings.enabled)
            if (settings.enabled && observingService == null && rootService == null) {
                syncNow()
            }
        }
    }

    /** 录制路径手势期间暂停识别。 */
    fun pauseForRecording() {
        applicationScope.launch(Dispatchers.IO) {
            syncMutex.withLock {
                stopObservation()
                _status.value = _status.value.copy(running = false, detail = "recording")
            }
        }
    }

    fun resumeAfterRecording() {
        applicationScope.launch(Dispatchers.IO) { syncNow() }
    }

    private suspend fun syncNow(
        targetPrivilegeMode: PrivilegeMode? = null,
        serviceEnabledOverride: Boolean? = null,
    ) {
        syncMutex.withLock {
            val settings = settingsStore.state.value
            val shizukuAvailable = runCatching {
                ShizukuUserServiceHost.hasShizukuPermission(context)
            }.getOrDefault(false)
            _status.value = _status.value.copy(
                enabled = settings.enabled,
                shizukuAvailable = shizukuAvailable,
            )

            stopObservation()

            val freshSettings = if (targetPrivilegeMode != null && serviceEnabledOverride != null) {
                null
            } else {
                runCatching { settingsRepository.readFreshSnapshot() }.getOrNull()
            }
            val serviceEnabled = serviceEnabledOverride
                ?: freshSettings?.serviceEnabled
                ?: runCatching { settingsRepository.readSnapshot().serviceEnabled }.getOrDefault(true)

            if (!settings.enabled || !serviceEnabled) {
                _status.value = _status.value.copy(
                    running = false,
                    detail = if (!serviceEnabled) "service-disabled" else "disabled"
                )
                return
            }

            val privilegeMode = targetPrivilegeMode
                ?: freshSettings?.privilegeMode
                ?: runCatching { settingsRepository.readSnapshot().privilegeMode }.getOrDefault(PrivilegeMode.SHIZUKU)
            if (privilegeMode == PrivilegeMode.ROOT) {
                startRootObservation(false)
                return
            }

            if (!shizukuAvailable) {
                _status.value = _status.value.copy(running = false, detail = "shizuku-unavailable")
                Log.i(TAG, "sync: shizuku unavailable")
                return
            }

            val service = runCatching { ShizukuUserServiceHost.ensure(context) }.getOrNull()
            if (service == null) {
                _status.value = _status.value.copy(running = false, detail = "service-unavailable")
                Log.i(TAG, "sync: user service unavailable")
                scheduleAutoReconnect(3000L)
                return
            }
            // 先把当前协议/设备/显示状态推给 service：观察器从第一帧起就按用户配置工作。
            pushMultiTouchConfig(service)
            val startedObservation = runCatching {
                service.startMultiTouchObservation(callback, false)
            }.getOrDefault(false)
            Log.i(TAG, "sync: start observation=$startedObservation")
            if (startedObservation) {
                runCatching { service.asBinder().linkToDeath(serviceDeathRecipient, 0) }
                observingService = service
                _status.value = _status.value.copy(running = true, detail = "watching")
            } else {
                _status.value = _status.value.copy(running = false, detail = "device-unavailable")
                scheduleAutoReconnect(3000L)
            }
        }
    }

    private fun stopObservation() {
        val service = observingService
        observingService = null
        if (service != null) {
            runCatching { service.asBinder().unlinkToDeath(serviceDeathRecipient, 0) }
            runCatching { service.stopMultiTouchObservation() }
        }
        rootPollJob?.cancel()
        rootPollJob = null
        val root = rootService
        rootService = null
        if (root != null) {
            runCatching { root.stopMultiTouchPolling() }
        }
        serviceHandlesRotation = false
        _status.value = _status.value.copy(channel = "", device = "")
    }

    /** root 模式：root 守护进程里的观察器入队事件，这里定时取走并分发。 */
    private fun startRootObservation(pathPriority: Boolean) {
        var root = runCatching {
            RootTaskManagerServiceHost.ensure(context) as? TaskManagerRpcClient
        }.getOrNull()
        // API 64 引入了多指手势轮询（startMultiTouchPolling/pollMultiTouchEvents）；
        // 之后的每个 SERVICE_BUILD 都改过观察器/判定逻辑（73 = 触发区域 + 键盘条件），
        // 常驻的旧守护进程必须淘汰重拉，否则新代码不生效。
        val rootApi = runCatching { root?.apiVersion }.getOrNull() ?: 0
        if (root == null || rootApi < 64 || rootApi < ShizukuUserServiceHost.SERVICE_BUILD) {
            Log.i(
                TAG,
                "root daemon is missing or outdated (api=$rootApi < " +
                    "${ShizukuUserServiceHost.SERVICE_BUILD}), restarting root service...",
            )
            RootTaskManagerServiceHost.restart(context)
            root = runCatching {
                RootTaskManagerServiceHost.ensure(context) as? TaskManagerRpcClient
            }.getOrNull()
        }
        if (root != null) {
            val api = runCatching { root.apiVersion }.getOrNull() ?: 0
            if (api < ShizukuUserServiceHost.SERVICE_BUILD) {
                Log.i(
                    TAG,
                    "root 守护进程 api=$api < ${ShizukuUserServiceHost.SERVICE_BUILD}：" +
                        "它回传的手势不带锚点（面板落屏幕中间），重启特权服务后生效",
                )
            }
        }
        if (root == null) {
            _status.value = _status.value.copy(
                running = false,
                detail = "root-unavailable",
                channel = "",
            )
            Log.i(TAG, "sync: root daemon unavailable")
            scheduleAutoReconnect(3000L)
            return
        }
        pushMultiTouchConfig(root)
        var started = runCatching { root.startMultiTouchPolling(pathPriority) }.getOrDefault(false)
        if (!started) {
            Log.w(TAG, "root start polling failed on api=${root.apiVersion}, attempting restart once...")
            RootTaskManagerServiceHost.restart(context)
            root = runCatching {
                RootTaskManagerServiceHost.ensure(context) as? TaskManagerRpcClient
            }.getOrNull()
            started = root != null && runCatching { root.startMultiTouchPolling(pathPriority) }.getOrDefault(false)
        }
        Log.i(TAG, "sync: root start polling=$started")
        if (!started || root == null) {
            _status.value = _status.value.copy(
                running = false,
                detail = "root-start-failed",
                channel = "",
            )
            scheduleAutoReconnect(3000L)
            return
        }
        rootService = root
        _status.value = _status.value.copy(
            running = true,
            detail = "watching:root",
            channel = "root",
        )
        rootPollJob = applicationScope.launch(Dispatchers.IO) {
            var consecutiveErrors = 0
            while (isActive) {
                val result = runCatching { root.pollMultiTouchEvents() }
                if (result.isFailure) {
                    consecutiveErrors++
                    if (consecutiveErrors >= 5) {
                        Log.w(TAG, "root polling consecutive failures, scheduling reconnect")
                        rootService = null
                        _status.value = _status.value.copy(
                            running = false,
                            detail = "root-poll-failed",
                            channel = "",
                        )
                        scheduleAutoReconnect(2000L)
                        break
                    }
                } else {
                    consecutiveErrors = 0
                    val events = result.getOrNull() ?: emptyArray()
                    for (event in events) {
                        handleRootEvent(event)
                    }
                }
                delay(ROOT_POLL_INTERVAL_MS)
            }
        }
    }

    private fun handleRootEvent(event: String) {
        if (event.startsWith("g:")) {
            // 新：g:<kind>:<fingers>:<u>:<v>:<startU>:<startV>；旧：无起点 / 无锚点。
            val parts = event.split(':', limit = 7)
            val kind = parts.getOrNull(1)?.toIntOrNull() ?: return
            val fingers = parts.getOrNull(2)?.toIntOrNull() ?: return
            val anchor = parseAnchor(parts.getOrNull(3), parts.getOrNull(4))
            val start = if (parts.size >= 7) {
                parseAnchor(parts.getOrNull(5), parts.getOrNull(6))
            } else {
                null
            }
            dispatchGesture(kind, fingers, anchor, start)
            return
        }
        if (event.startsWith("p:")) {
            // 新：p:<fingers>:<u>:<v>:<startU>:<startV>:<strokes>；
            // 旧：p:<fingers>:<u>:<v>:<strokes> / p:<fingers>:<strokes>。
            val parts = event.split(':', limit = 7)
            val fingers = parts.getOrNull(1)?.toIntOrNull() ?: return
            val anchor: TouchAnchor?
            val start: TouchAnchor?
            val rawStrokes: String
            when {
                parts.size >= 7 -> {
                    anchor = parseAnchor(parts.getOrNull(2), parts.getOrNull(3))
                    start = parseAnchor(parts.getOrNull(4), parts.getOrNull(5))
                    rawStrokes = parts.getOrNull(6) ?: return
                }

                parts.size >= 5 -> {
                    anchor = parseAnchor(parts.getOrNull(2), parts.getOrNull(3))
                    start = null
                    rawStrokes = parts.getOrNull(4) ?: return
                }

                else -> {
                    anchor = null
                    start = null
                    rawStrokes = parts.getOrNull(2) ?: return
                }
            }
            val strokes = decodePathStrokes(rawStrokes)
            if (strokes.isEmpty()) return
            handlePathStrokes(fingers, strokes, anchor, start)
            return
        }
        if (event.startsWith("d:")) {
            val device = event.removePrefix("d:").takeIf { it.isNotBlank() } ?: return
            _status.value = _status.value.copy(device = device, channel = "root")
            return
        }
        if (event.startsWith("c:")) {
            val value = event.removePrefix("c:").takeIf { it.isNotBlank() }
            Log.i(TAG, "root input conflict: $value")
            _status.value = _status.value.copy(conflictDevices = value)
        }
    }

    private fun parseAnchor(u: String?, v: String?): TouchAnchor? {
        val horizontal = u?.toFloatOrNull() ?: return null
        val vertical = v?.toFloatOrNull() ?: return null
        return TouchAnchor(horizontal.coerceIn(0f, 1f), vertical.coerceIn(0f, 1f))
    }

    private fun performAction(
        action: GestureAction,
        kind: MultiPointerGesture,
        fingerCount: Int,
        anchor: TouchAnchor?,
        name: String = "",
        showHint: Boolean = true,
    ) {
        val hostContext = SlideIndexAccessibilityService.overlayHostContext() ?: context
        val appSettings = runCatching { settingsRepository.readSnapshot() }.getOrNull()
        if (appSettings != null && OverlaySuppression.shouldSuppress(
                settings = appSettings,
                context = hostContext,
                foregroundPackage = null,
                scope = OverlaySuppressionScope.MULTI_TOUCH,
            )) {
            Log.i(TAG, "multi-touch suppressed by foreground app")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAtMs < ACTION_COOLDOWN_MS) return
        lastActionAtMs = now
        runCatching { ShakeVibrationHelper.vibrate(context) }
        // 抓屏类动作不弹提示胶囊：它是 ACCESSIBILITY_OVERLAY，会被自己的截图拍进去（issue #60）。
        // 每个手势还能单独关掉提示（反馈 #397）。
        if (showHint && !action.capturesScreen) {
            MultiTouchGestureFeedback.show(
                context = SlideIndexAccessibilityService.overlayHostContext() ?: context,
                // 用户给手势起了名字就显示名字，否则沿用「3 指 · 上滑」（反馈 #396）。
                text = name.takeIf { it.isNotBlank() } ?: context.getString(
                    R.string.multitouch_entry_label,
                    fingerCount,
                    context.getString(gestureKindLabelRes(kind)),
                ),
            )
        }
        // 手指落点 → 屏幕坐标 + 面板侧：快速启动器这类「只能贴边画」的浮层靠它决定从哪出来；
        // 没有锚点（旧服务进程）时保持历史行为（屏幕中心 + 谁的画布在用谁）。
        val trigger = anchor?.let { resolveTriggerAnchor(it) }
        runCatching {
            val executor = ActionExecutor(
                context = SlideIndexAccessibilityService.overlayHostContext() ?: context,
                appRepository = appRepository,
            )
            executor.execute(
                action = action,
                settings = settingsRepository.readSnapshot(),
                anchorRawX = trigger?.x,
                anchorRawY = trigger?.y,
                panelSide = trigger?.side,
            )
        }.onFailure {
            Log.w(TAG, "perform action failed: ${it.message}")
        }
    }

    /**
     * 把面板自然坐标换算成当前屏幕坐标，并决定面板从哪一侧出来。
     *
     * 侧的选择就是「手指落在屏幕中线哪边」，不做死区：多指手势（张开/旋转）的质心本来就可能
     * 压在中线上，加死区反而要再引入一个「默认侧」的武断值；落在哪边就选哪边最可解释。
     */
    private fun resolveTriggerAnchor(anchor: TouchAnchor): TriggerAnchor {
        val metrics = OverlayScreenMetrics.snapshot(context)
        val point = if (serviceHandlesRotation) {
            // service 已把触点旋转到屏幕坐标：u/v 就是屏幕宽高比例，直接还原像素。
            ScreenAnchor(
                x = anchor.u * metrics.widthPx,
                y = anchor.v * metrics.heightPx,
            )
        } else {
            mapTouchAnchorToScreen(
                anchor = anchor,
                widthPx = metrics.widthPx,
                heightPx = metrics.heightPx,
                rotation = metrics.rotation,
            )
        }
        val side = if (point.x < metrics.widthPx / 2f) PanelSide.LEFT else PanelSide.RIGHT
        Log.i(
            TAG,
            "anchor u=${anchor.u} v=${anchor.v} rotation=${metrics.rotation} " +
                "screen=${metrics.widthPx}x${metrics.heightPx} " +
                "mode=${if (serviceHandlesRotation) "screen-space" else "legacy-remap"} -> " +
                "(${point.x},${point.y}) side=$side",
        )
        return TriggerAnchor(side = side, x = point.x, y = point.y)
    }

    private data class TriggerAnchor(val side: PanelSide, val x: Float, val y: Float)

    private fun isMainProcess(): Boolean =
        runCatching { Application.getProcessName() == context.packageName }.getOrDefault(true)

    /** 当前屏幕方向（`Surface.ROTATION_*`）；取不到就按竖屏处理。 */
    private fun currentDisplayRotation(): Int =
        runCatching { OverlayScreenMetrics.displayRotation(context) }.getOrDefault(0)

    /** 候选按顺序匹配条目，命中第一个（且其生效区域包含起点）就执行并返回 true。 */
    private fun dispatchGesture(
        gesture: Int,
        fingerCount: Int,
        anchor: TouchAnchor?,
        start: TouchAnchor? = null,
    ): Boolean {
        val panelKind = MultiPointerGesture.entries.getOrNull(gesture) ?: return false
        // service 支持入口旋转时，识别结果已经是屏幕方向；否则回退到 app 侧方向翻译。
        val rotation = currentDisplayRotation()
        val kind = if (serviceHandlesRotation) panelKind else panelKind.forDisplayRotation(rotation)
        val entry = settingsStore.state.value.entries.firstOrNull {
            it.kind == kind && it.fingerCount == fingerCount &&
                it.enabled &&
                it.action != GestureAction.None &&
                areaAllowsEntry(it, start)
        } ?: run {
            Log.i(
                TAG,
                "no entry for ${kind.name} x$fingerCount（panel=${panelKind.name} rot=$rotation" +
                    " start=${start?.u},${start?.v}）",
            )
            return false
        }
        Log.i(
            TAG,
            "gesture ${kind.name} x$fingerCount（panel=${panelKind.name} rot=$rotation " +
                "mode=${if (serviceHandlesRotation) "screen-space" else "legacy-remap"}）-> ${entry.action}",
        )
        mainHandler.post {
            performAction(
                entry.action,
                kind,
                fingerCount,
                anchor,
                name = entry.name,
                showHint = entry.showHint,
            )
        }
        return true
    }

    /**
     * 协议/设备覆盖 + 显示状态：一次推给 service。
     *
     * 手动选协议后观察器不再自动嗅探/回退；没推成功（旧 service）就保持默认 auto。
     */
    private fun pushMultiTouchConfig(service: ITaskManagerService) {
        val settings = settingsStore.state.value
        val configOk = runCatching {
            service.setMultiTouchConfig(settings.protocol, settings.devicePath)
        }.isSuccess
        if (!configOk) {
            Log.i(TAG, "service 不支持 setMultiTouchConfig：协议/设备覆盖不可用")
        }
        pushMultiTouchTrigger(service)
        pushDisplayTransform(service)
    }

    /**
     * 把全局键盘策略推给 service。
     *
     * 区域判定已经下沉到 app（每个手势自带生效区域），所以 area / borderPx 统一传 0，
     * service 只消费 keyboardBehavior；旧版 service / 旧 root 守护进程没有这个方法，
     * 调用失败就保持默认策略，不影响手势本身。
     */
    private fun pushMultiTouchTrigger(service: ITaskManagerService) {
        val settings = settingsStore.state.value
        val ok = runCatching {
            service.setMultiTouchTrigger(
                0,
                MultiTouchTriggerFilter.keyboardBehaviorId(settings.keyboardBehavior),
                0,
            )
        }.isSuccess
        if (!ok) {
            Log.i(
                TAG,
                "service 不支持 setMultiTouchTrigger：键盘条件暂不生效（重启特权服务后生效）",
            )
        }
    }

    /**
     * 告诉 service 当前屏幕方向/尺寸（GMD 式入口旋转）。
     *
     * 旧版 service / 旧 root 守护进程没有 [ITaskManagerService.setMultiTouchDisplay]，
     * 调用失败就回退到 app 侧方向重映射，保证升级顺序不会把功能弄坏。
     */
    private fun pushDisplayTransform(service: ITaskManagerService) {
        val metrics = runCatching { OverlayScreenMetrics.snapshot(context) }.getOrNull() ?: return
        val ok = runCatching {
            service.setMultiTouchDisplay(metrics.rotation, metrics.widthPx, metrics.heightPx)
        }.isSuccess
        serviceHandlesRotation = ok
        if (!ok) {
            Log.i(
                TAG,
                "service 不支持 setMultiTouchDisplay：回退 app 侧方向重映射（重启特权服务后生效）",
            )
        }
    }

    /** 屏幕旋转变化：把新的方向/尺寸推给正在观察的 service，不需要重启观察器。 */
    private fun pushDisplayTransformToObservers() {
        val service = observingService
        if (service != null) {
            pushDisplayTransform(service)
            return
        }
        val root = rootService
        if (root != null) {
            pushDisplayTransform(root)
        }
    }

    private fun registerDisplayListener() {
        if (displayListener != null) return
        val manager = context.getSystemService(DisplayManager::class.java) ?: return
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                if (displayId != Display.DEFAULT_DISPLAY) return
                applicationScope.launch(Dispatchers.IO) { pushDisplayTransformToObservers() }
            }
        }
        manager.registerDisplayListener(listener, mainHandler)
        displayListener = listener
    }

    private companion object {
        const val TAG = "MultiTouchCoordinator"

        /**
         * 同一次触摸的多个候选（旋转 + 捏合等）在几帧内产生，这个窗口内只执行一次；
         * 400ms 足够去重，又不会吃掉连续两次手势（人手间隔通常 > 300ms）。
         */
        const val ACTION_COOLDOWN_MS = 400L

        /** root 模式轮询间隔（本地 socket，开销很小）。 */
        const val ROOT_POLL_INTERVAL_MS = 100L
    }
}
