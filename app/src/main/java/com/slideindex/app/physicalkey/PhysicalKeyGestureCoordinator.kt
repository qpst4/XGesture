package com.slideindex.app.physicalkey

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.privilege.root.RootTaskManagerServiceHost
import com.slideindex.app.privilege.root.TaskManagerRpcClient
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shake.ShakeVibrationHelper
import com.slideindex.app.shizuku.IOnPhysicalKeyGesture
import com.slideindex.app.shizuku.ITaskManagerService
import com.slideindex.app.shizuku.ShizukuUserServiceHost
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rikka.shizuku.Shizuku

data class PhysicalKeyStatus(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val detail: String = "idle",
    val channel: String = "",
    val shizukuAvailable: Boolean = false,
)

@Singleton
class PhysicalKeyGestureCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: PhysicalKeySettingsStore,
    private val settingsRepository: SettingsRepository,
    private val appRepository: AppRepository,
    private val applicationScope: CoroutineScope,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val syncMutex = Mutex()

    private val _status = MutableStateFlow(PhysicalKeyStatus())
    val status: StateFlow<PhysicalKeyStatus> = _status.asStateFlow()

    @Volatile
    private var started = false

    @Volatile
    private var observingService: ITaskManagerService? = null

    @Volatile
    private var rootService: ITaskManagerService? = null

    private var rootPollJob: Job? = null

    @Volatile
    private var lastActionAtMs = 0L

    private var reconnectJob: Job? = null

    private var screenOffWakeLock: PowerManager.WakeLock? = null

    private val serviceDeathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "observingService binder died, scheduling reconnect")
        observingService = null
        _status.value = _status.value.copy(running = false, detail = "binder-died")
        scheduleAutoReconnect(1000L)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    releaseScreenOffWakeLock()
                    checkChannelHealthAndRecover()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    updateScreenOffWakeLock()
                }
            }
        }
    }

    private fun updateScreenOffWakeLock() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        val state = settingsStore.state.value
        val hasActiveGestures = state.entries.values.any { it.enabled && it.action !is GestureAction.None }
        val shouldHold = state.enabled && state.screenOffTriggerEnabled && !pm.isInteractive && hasActiveGestures

        if (shouldHold) {
            if (screenOffWakeLock == null) {
                screenOffWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XGesture:PhysicalKeyScreenOff").apply {
                    setReferenceCounted(false)
                }
            }
            if (screenOffWakeLock?.isHeld == false) {
                runCatching {
                    screenOffWakeLock?.acquire(8 * 3600 * 1000L) // 8 小时超时保护
                    Log.i(TAG, "acquired screen-off partial wake lock for physical key gestures")
                }
            }
        } else {
            releaseScreenOffWakeLock()
        }
    }

    private fun releaseScreenOffWakeLock() {
        if (screenOffWakeLock?.isHeld == true) {
            runCatching {
                screenOffWakeLock?.release()
                Log.i(TAG, "released screen-off partial wake lock")
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
            (r != null && rootPollJob?.isActive == true)
        if (!isAlive) {
            Log.i(TAG, "screen wakeup check: physical-key channel inactive, auto recovering...")
            scheduleAutoReconnect(300L)
        }
    }

    private fun scheduleAutoReconnect(delayMs: Long) {
        reconnectJob?.cancel()
        reconnectJob = applicationScope.launch(Dispatchers.IO) {
            delay(delayMs)
            if (settingsStore.state.value.enabled) {
                Log.i(TAG, "auto reconnecting physical key observation...")
                syncNow()
            }
        }
    }

    private val callback = object : IOnPhysicalKeyGesture.Stub() {
        override fun onKeyGesture(gestureOrdinal: Int, eventTimeMs: Long) {
            val gesture = PhysicalKeyGesture.fromOrdinal(gestureOrdinal) ?: return
            dispatchGesture(gesture)
        }

        override fun onObserverState(running: Boolean, detail: String) {
            _status.value = _status.value.copy(
                running = running,
                detail = detail,
                channel = if (running) "shizuku" else "",
            )
        }
    }

    fun start() {
        if (started) return
        started = true
        PhysicalKeyFilterBridge.keyEventFilter = { event -> shouldInterceptKeyEvent(event) }
        SlideIndexAccessibilityService.ensureKeyFilterEnabled()
        Log.i(TAG, "PhysicalKeyGestureCoordinator.start() initialized")

        if (isMainProcess()) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            context.registerReceiver(screenReceiver, filter)
        }

        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        }

        applicationScope.launch(Dispatchers.IO) {
            combine(
                settingsStore.state,
                settingsRepository.settings,
            ) { keySettings, globalSettings ->
                Pair(keySettings.enabled, Pair(globalSettings.privilegeMode, globalSettings.serviceEnabled))
            }
                .distinctUntilChanged()
                .collect { (_, global) ->
                    val (privilegeMode, serviceEnabled) = global
                    syncNow(
                        targetPrivilegeMode = privilegeMode,
                        serviceEnabledOverride = serviceEnabled,
                    )
                }
        }

        applicationScope.launch(Dispatchers.IO) {
            settingsStore.state.collect {
                updateScreenOffWakeLock()
                val service = observingService ?: rootService
                if (service != null) {
                    updateDoublePressTargets(service)
                }
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

    suspend fun readDiagnostics(): String {
        val service = observingService ?: rootService
        if (service == null) {
            val statusVal = _status.value
            return "特权服务未连接 (enabled=${statusVal.enabled}, detail=${statusVal.detail}, channel=${statusVal.channel.ifBlank { "none" }})"
        }
        val diag = runCatching { service.getPhysicalKeyDiagnostics() }.getOrDefault("")
        return diag.ifBlank { "特权服务已就绪，正在扫描硬件按键设备..." }
    }

    fun refreshStatus() {
        applicationScope.launch(Dispatchers.IO) {
            val settings = settingsStore.state.value
            _status.value = _status.value.copy(enabled = settings.enabled)
            val service = observingService ?: rootService
            if (service != null) {
                updateDoublePressTargets(service)
            } else if (settings.enabled) {
                syncNow()
            }
        }
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
                startRootObservation()
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

            // 监听器启动前优先下发最新的按键映射目标表，确保 observer 启动即生效
            updateDoublePressTargets(service)

            val startedObservation = runCatching {
                service.startPhysicalKeyObservation(callback)
            }.getOrDefault(false)

            Log.i(TAG, "sync: start physical key observation=$startedObservation")
            if (startedObservation) {
                runCatching { service.asBinder().linkToDeath(serviceDeathRecipient, 0) }
                observingService = service
                _status.value = _status.value.copy(running = true, detail = "watching:shizuku", channel = "shizuku")
                updateDoublePressTargets(service)
                applyPowerKeySuppression(true)
            } else {
                _status.value = _status.value.copy(running = false, detail = "device-unavailable")
                scheduleAutoReconnect(3000L)
            }
        }
    }

    private fun stopObservation() {
        releaseScreenOffWakeLock()
        applyPowerKeySuppression(false)
        val service = observingService
        observingService = null
        if (service != null) {
            runCatching { service.asBinder().unlinkToDeath(serviceDeathRecipient, 0) }
            runCatching { service.stopPhysicalKeyObservation() }
        }
        rootPollJob?.cancel()
        rootPollJob = null
        val root = rootService
        rootService = null
        if (root != null) {
            runCatching { root.stopPhysicalKeyPolling() }
        }
        _status.value = _status.value.copy(channel = "")
    }

    private fun startRootObservation() {
        var root = runCatching {
            RootTaskManagerServiceHost.ensure(context, minApi = ShizukuUserServiceHost.SERVICE_BUILD)
        }.getOrNull()

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

        updateDoublePressTargets(root)
        var started = runCatching { root.startPhysicalKeyPolling() }.getOrDefault(false)
        if (!started) {
            Log.w(TAG, "root start physical key polling failed, restarting root service...")
            RootTaskManagerServiceHost.restart(context)
            root = runCatching {
                RootTaskManagerServiceHost.ensure(context, minApi = ShizukuUserServiceHost.SERVICE_BUILD)
            }.getOrNull()
            started = root != null && runCatching { root.startPhysicalKeyPolling() }.getOrDefault(false)
        }

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
        updateDoublePressTargets(root)
        applyPowerKeySuppression(true)

        rootPollJob = applicationScope.launch(Dispatchers.IO) {
            var consecutiveErrors = 0
            while (isActive) {
                val result = runCatching { root.pollPhysicalKeyEvents() }
                if (result.isFailure) {
                    consecutiveErrors++
                    if (consecutiveErrors >= 5) {
                        Log.w(TAG, "root physical key polling consecutive failures, scheduling reconnect")
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
                    val events = result.getOrNull()
                    if (!events.isNullOrEmpty()) {
                        for (event in events) {
                            if (event.startsWith("k:")) {
                                val parts = event.split(':')
                                val ordinal = parts.getOrNull(1)?.toIntOrNull() ?: continue
                                val gesture = PhysicalKeyGesture.fromOrdinal(ordinal) ?: continue
                                dispatchGesture(gesture)
                            }
                        }
                    }
                }
                delay(ROOT_POLL_INTERVAL_MS)
            }
        }
    }

    private fun dispatchGesture(gesture: PhysicalKeyGesture) {
        val entry = settingsStore.state.value.entries[gesture] ?: return
        if (entry.action == GestureAction.None) {
            return
        }

        // 若是电源键长按手势且映射了有效动作，通过特权广播 CLOSE_SYSTEM_DIALOGS 压制关机弹窗
        if (gesture == PhysicalKeyGesture.POWER_LONG_PRESS) {
            suppressSystemPowerDialogIfNeeded()
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAtMs < ACTION_COOLDOWN_MS) return
        lastActionAtMs = now

        // 检查音乐播放切歌优先逻辑
        val targetAction = if (settingsStore.state.value.mediaPlaybackPrioritized && isMediaPlaying()) {
            when (gesture) {
                PhysicalKeyGesture.VOLUME_UP_LONG_PRESS -> GestureAction.MediaNext
                PhysicalKeyGesture.VOLUME_DOWN_LONG_PRESS -> GestureAction.MediaPrevious
                else -> entry.action
            }
        } else {
            entry.action
        }

        Log.i(TAG, "dispatch physical key gesture $gesture -> $targetAction")
        mainHandler.post {
            performAction(targetAction)
        }
    }

    private fun applyPowerKeySuppression(suppress: Boolean) {
        val service = observingService ?: rootService ?: return
        val state = settingsStore.state.value
        val hasPowerMapping = state.entries[PhysicalKeyGesture.POWER_LONG_PRESS]?.action != GestureAction.None ||
            state.entries[PhysicalKeyGesture.POWER_DOUBLE_PRESS]?.action != GestureAction.None ||
            state.entries[PhysicalKeyGesture.POWER_SINGLE_PRESS]?.action != GestureAction.None
        val shouldSuppress = suppress && state.enabled && state.interceptNativeEvents && hasPowerMapping

        runCatching {
            if (shouldSuppress) {
                // 通过系统全局设置直接关闭系统 PhoneWindowManager 对长按电源键的响应（AOSP 原生 + 厂商深度定制）
                service.runShellCommand(arrayOf("settings", "put", "global", "power_button_long_press", "0"))
                service.runShellCommand(arrayOf("settings", "put", "global", "intercpt_powerkey", "1"))
                service.runShellCommand(arrayOf("settings", "put", "system", "intercpt_powerkey", "1"))
            } else {
                // 恢复系统默认行为
                service.runShellCommand(arrayOf("settings", "put", "global", "power_button_long_press", "1"))
                service.runShellCommand(arrayOf("settings", "put", "global", "intercpt_powerkey", "0"))
                service.runShellCommand(arrayOf("settings", "put", "system", "intercpt_powerkey", "0"))
            }
        }
    }

    private fun updateDoublePressTargets(service: ITaskManagerService) {
        val state = settingsStore.state.value
        val mappedKeys = mutableSetOf<Int>()
        val doublePressKeys = mutableSetOf<Int>()
        val singlePressKeys = mutableSetOf<Int>()

        // 电源键相关手势
        val powerLong = state.entries[PhysicalKeyGesture.POWER_LONG_PRESS]
        if (powerLong != null && powerLong.enabled && powerLong.action !is GestureAction.None) {
            mappedKeys.add(EvdevPhysicalKeyObserver.KEY_POWER)
        }

        val powerSingle = state.entries[PhysicalKeyGesture.POWER_SINGLE_PRESS]
        if (powerSingle != null && powerSingle.enabled && powerSingle.action !is GestureAction.None) {
            mappedKeys.add(EvdevPhysicalKeyObserver.KEY_POWER)
            singlePressKeys.add(EvdevPhysicalKeyObserver.KEY_POWER)
        }

        val powerDouble = state.entries[PhysicalKeyGesture.POWER_DOUBLE_PRESS]
        if (powerDouble != null && powerDouble.enabled && powerDouble.action !is GestureAction.None) {
            mappedKeys.add(EvdevPhysicalKeyObserver.KEY_POWER)
            doublePressKeys.add(EvdevPhysicalKeyObserver.KEY_POWER)
        }

        // 音量键手势注册：无论是否独占拦截驱动，都必须注册给底层识别器以侦测对应的时序
        val volUpSingle = state.entries[PhysicalKeyGesture.VOLUME_UP_SINGLE_PRESS]
        val hasVolUpSingle = volUpSingle != null && volUpSingle.enabled && volUpSingle.action !is GestureAction.None
        if (hasVolUpSingle) {
            singlePressKeys.add(EvdevPhysicalKeyObserver.KEY_VOLUMEUP)
        }

        val volDownSingle = state.entries[PhysicalKeyGesture.VOLUME_DOWN_SINGLE_PRESS]
        val hasVolDownSingle = volDownSingle != null && volDownSingle.enabled && volDownSingle.action !is GestureAction.None
        if (hasVolDownSingle) {
            singlePressKeys.add(EvdevPhysicalKeyObserver.KEY_VOLUMEDOWN)
        }

        val volUpDouble = state.entries[PhysicalKeyGesture.VOLUME_UP_DOUBLE_PRESS]
        val hasVolUpDouble = volUpDouble != null && volUpDouble.enabled && volUpDouble.action !is GestureAction.None
        if (hasVolUpDouble) {
            doublePressKeys.add(EvdevPhysicalKeyObserver.KEY_VOLUMEUP)
        }

        val volDownDouble = state.entries[PhysicalKeyGesture.VOLUME_DOWN_DOUBLE_PRESS]
        val hasVolDownDouble = volDownDouble != null && volDownDouble.enabled && volDownDouble.action !is GestureAction.None
        if (hasVolDownDouble) {
            doublePressKeys.add(EvdevPhysicalKeyObserver.KEY_VOLUMEDOWN)
        }

        val volUpLong = state.entries[PhysicalKeyGesture.VOLUME_UP_LONG_PRESS]
        val hasVolUpLong = volUpLong != null && volUpLong.enabled && volUpLong.action !is GestureAction.None

        val volDownLong = state.entries[PhysicalKeyGesture.VOLUME_DOWN_LONG_PRESS]
        val hasVolDownLong = volDownLong != null && volDownLong.enabled && volDownLong.action !is GestureAction.None

        val bothPress = state.entries[PhysicalKeyGesture.VOLUME_BOTH_PRESS]
        val hasBothPress = bothPress != null && bothPress.enabled && bothPress.action !is GestureAction.None

        // 音量键是否独占拦截驱动（加入 mappedKeys）：
        // 彻底取决于用户是否开启了“拦截原生按键响应”（interceptNativeEvents）。
        // 若关闭“拦截原生按键响应”（默认）：音量键事件完全直通原生系统（长按连续调音、短按即刻调音），
        // 与此同时底层手势识别器依然正常并行识别单击、双击、长按与并击手势（一边调音一边触发手势）。
        // 若开启“拦截原生按键响应”：配置了手势的音量键被底层独占拦截，彻底阻止原生调音和音量条弹出。
        val shouldInterceptVolume = state.interceptNativeEvents
        if (shouldInterceptVolume) {
            if (hasVolUpSingle || hasVolUpDouble || hasVolUpLong || hasBothPress) {
                mappedKeys.add(EvdevPhysicalKeyObserver.KEY_VOLUMEUP)
            }
            if (hasVolDownSingle || hasVolDownDouble || hasVolDownLong || hasBothPress) {
                mappedKeys.add(EvdevPhysicalKeyObserver.KEY_VOLUMEDOWN)
            }
        }

        runCatching {
            service.updatePhysicalKeyCustomTargets(
                mappedKeys.toIntArray(),
                doublePressKeys.toIntArray(),
                singlePressKeys.toIntArray(),
            )
            Log.i(TAG, "updated physical key targets: mapped=$mappedKeys, doublePress=$doublePressKeys, singlePress=$singlePressKeys")
        }.onFailure {
            runCatching {
                service.updatePhysicalKeyTargets(mappedKeys.toIntArray(), doublePressKeys.toIntArray())
            }
        }
    }

    private fun suppressSystemPowerDialogIfNeeded() {
        // 特权服务广播 CLOSE_SYSTEM_DIALOGS（root / shell 具备该权限，严禁盲目发送全局返回键）
        runCatching {
            val service = observingService ?: rootService
            service?.runShellCommand(arrayOf("am", "broadcast", "-a", "android.intent.action.CLOSE_SYSTEM_DIALOGS"))
            // 确保底层设置也被强化压制
            service?.runShellCommand(arrayOf("settings", "put", "global", "power_button_long_press", "0"))
            service?.runShellCommand(arrayOf("settings", "put", "global", "intercpt_powerkey", "1"))
            service?.runShellCommand(arrayOf("settings", "put", "system", "intercpt_powerkey", "1"))
        }
    }

    fun shouldInterceptKeyEvent(event: KeyEvent): Boolean {
        val state = settingsStore.state.value
        if (!state.enabled || !state.interceptNativeEvents) {
            return false
        }

        val isVolumeUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolumeDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (!isVolumeUp && !isVolumeDown) {
            return false
        }

        if (state.mediaPlaybackPrioritized && isMediaPlaying()) {
            return true
        }

        val hasMappedGesture = state.entries.any { (gesture, entry) ->
            entry.action != GestureAction.None && when (gesture) {
                PhysicalKeyGesture.VOLUME_UP_LONG_PRESS,
                PhysicalKeyGesture.VOLUME_UP_DOUBLE_PRESS,
                PhysicalKeyGesture.VOLUME_UP_SINGLE_PRESS -> isVolumeUp
                PhysicalKeyGesture.VOLUME_DOWN_LONG_PRESS,
                PhysicalKeyGesture.VOLUME_DOWN_DOUBLE_PRESS,
                PhysicalKeyGesture.VOLUME_DOWN_SINGLE_PRESS -> isVolumeDown
                PhysicalKeyGesture.VOLUME_BOTH_PRESS -> true
                else -> false
            }
        }

        if (!hasMappedGesture) {
            return false
        }

        // 对齐 KeyMapper 规范：匹配到已启用的按键映射且开启了原生拦截时，消费掉所有按键事件（返回 true），
        // 彻底阻止系统原生调音与音量条弹出；不进行任何松开按键时的异步调音补偿。
        return true
    }

    private fun isMediaPlaying(): Boolean = runCatching {
        val audioManager = context.getSystemService(AudioManager::class.java)
        audioManager?.isMusicActive == true
    }.getOrDefault(false)

    private fun performAction(action: GestureAction) {
        val hostContext = SlideIndexAccessibilityService.overlayHostContext() ?: context
        val appSettings = runCatching { settingsRepository.readSnapshot() }.getOrNull()
        if (appSettings != null && OverlaySuppression.shouldSuppress(
                settings = appSettings,
                context = hostContext,
                foregroundPackage = null,
                scope = OverlaySuppressionScope.PHYSICAL_KEY,
            )) {
            Log.i(TAG, "physical key suppressed by foreground app")
            return
        }

        runCatching { ShakeVibrationHelper.vibrate(context) }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == false && requiresScreenOn(action)) {
            wakeScreenUp()
        }

        runCatching {
            val executor = ActionExecutor(
                context = SlideIndexAccessibilityService.overlayHostContext() ?: context,
                appRepository = appRepository,
            )
            executor.execute(
                action = action,
                settings = settingsRepository.readSnapshot(),
            )
        }.onFailure {
            Log.w(TAG, "perform physical key action failed: ${it.message}")
        }
    }

    private fun requiresScreenOn(action: GestureAction): Boolean = when (action) {
        is GestureAction.Flashlight,
        is GestureAction.MediaNext,
        is GestureAction.MediaPrevious,
        is GestureAction.MediaPlayPause -> false
        else -> true
    }

    private fun wakeScreenUp() {
        runCatching {
            val service = observingService ?: rootService
            service?.runShellCommand(arrayOf("input", "keyevent", "KEYCODE_WAKEUP"))
        }
    }

    fun retry() {
        applicationScope.launch(Dispatchers.IO) {
            checkChannelHealthAndRecover()
        }
    }

    private fun isMainProcess(): Boolean =
        runCatching { Application.getProcessName() == context.packageName }.getOrDefault(true)

    companion object {
        private const val TAG = "PhysicalKeyCoordinator"
        private const val ACTION_COOLDOWN_MS = 350L
        private const val ROOT_POLL_INTERVAL_MS = 60L
    }
}
