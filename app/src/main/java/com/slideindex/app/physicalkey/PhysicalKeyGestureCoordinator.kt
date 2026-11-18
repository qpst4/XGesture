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
import android.os.SystemClock
import android.util.Log
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
        Log.i(TAG, "PhysicalKeyGestureCoordinator.start() initialized")

        if (isMainProcess()) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
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
        val service = observingService ?: rootService ?: return ""
        return runCatching { service.getPhysicalKeyDiagnostics() }.getOrDefault("")
    }

    fun refreshStatus() {
        applicationScope.launch(Dispatchers.IO) {
            val settings = settingsStore.state.value
            _status.value = _status.value.copy(enabled = settings.enabled)
            if (settings.enabled && observingService == null && rootService == null) {
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

            val startedObservation = runCatching {
                service.startPhysicalKeyObservation(callback)
            }.getOrDefault(false)

            Log.i(TAG, "sync: start physical key observation=$startedObservation")
            if (startedObservation) {
                runCatching { service.asBinder().linkToDeath(serviceDeathRecipient, 0) }
                observingService = service
                _status.value = _status.value.copy(running = true, detail = "watching:shizuku", channel = "shizuku")
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
            RootTaskManagerServiceHost.ensure(context) as? TaskManagerRpcClient
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

        var started = runCatching { root.startPhysicalKeyPolling() }.getOrDefault(false)
        if (!started) {
            Log.w(TAG, "root start physical key polling failed, restarting root service...")
            RootTaskManagerServiceHost.restart(context)
            root = runCatching {
                RootTaskManagerServiceHost.ensure(context) as? TaskManagerRpcClient
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
        if (!entry.enabled || entry.action == GestureAction.None) {
            return
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
