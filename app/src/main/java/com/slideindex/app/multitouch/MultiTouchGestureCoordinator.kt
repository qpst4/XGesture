package com.slideindex.app.multitouch

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.multitouch.MultiPointerBinding
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shizuku.IOnMultiTouchGesture
import com.slideindex.app.shizuku.ITaskManagerService
import com.slideindex.app.shizuku.ShizukuUserServiceHost
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 多指手势运行状态（设置页展示用）。 */
data class MultiTouchStatus(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val detail: String = "",
    val shizukuAvailable: Boolean = false,
)

/**
 * 多指手势总协调器（app 主进程）。
 *
 * 职责：
 * 1. 按设置启动/停止 Shizuku 用户服务里的 evdev 观察器；
 * 2. 接收观察器回调（一次手势一条），查绑定表并执行动作；
 * 3. 维护可观察的运行状态，供设置页展示。
 *
 * 只在主进程启动：Shizuku 观察器是进程内单例，若在 `:overlay` 等子进程也启动，
 * 同一个手势会被多个进程各执行一次。
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

    private val callback = object : IOnMultiTouchGesture.Stub() {
        override fun onGesture(gesture: Int, fingerCount: Int, eventTimeMs: Long) {
            val type = MultiPointerGesture.entries.getOrNull(gesture)
            if (type == null) {
                Log.w(TAG, "unknown gesture ordinal=$gesture")
                return
            }
            val binding = MultiPointerBinding(fingerCount, type)
            val action = settingsStore.state.value.bindings[binding]
            Log.i(TAG, "onGesture binding=$binding action=$action")
            if (action == null || action == GestureAction.None) return
            mainHandler.post { performAction(action) }
        }

        override fun onObserverState(running: Boolean, detail: String) {
            _status.value = _status.value.copy(running = running, detail = detail)
        }
    }

    /** 幂等启动；由 Application 与设置页共同调用。 */
    fun start() {
        if (started) return
        started = true
        if (!isMainProcess()) return
        applicationScope.launch(Dispatchers.IO) {
            settingsStore.state.collect { syncNow() }
        }
    }

    /** 设置页「重试」：丢掉旧进程，按当前设置重新绑定。 */
    fun restart() {
        applicationScope.launch(Dispatchers.IO) {
            runCatching { ShizukuUserServiceHost.drop(context) }
            syncNow()
        }
    }

    private suspend fun syncNow() {
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

            if (!settings.enabled) {
                _status.value = _status.value.copy(running = false, detail = "disabled")
                return
            }
            if (!shizukuAvailable) {
                _status.value = _status.value.copy(running = false, detail = "shizuku-unavailable")
                return
            }

            val service = runCatching { ShizukuUserServiceHost.ensure(context) }.getOrNull()
            if (service == null) {
                _status.value = _status.value.copy(running = false, detail = "service-unavailable")
                return
            }
            val startedObservation = runCatching {
                service.startMultiTouchObservation(callback)
            }.getOrDefault(false)
            if (startedObservation) {
                observingService = service
                _status.value = _status.value.copy(running = true, detail = "watching")
            } else {
                _status.value = _status.value.copy(running = false, detail = "device-unavailable")
            }
        }
    }

    private fun stopObservation() {
        val service = observingService
        observingService = null
        if (service != null) {
            runCatching { service.stopMultiTouchObservation() }
        }
    }

    private fun performAction(action: GestureAction) {
        val result = runCatching {
            val executor = ActionExecutor(
                context = SlideIndexAccessibilityService.overlayHostContext() ?: context,
                appRepository = appRepository,
            )
            executor.execute(action, settingsRepository.readSnapshot())
        }
        Log.i(TAG, "performAction action=$action result=${result.getOrNull()} err=${result.exceptionOrNull()?.message}")
    }

    private fun isMainProcess(): Boolean =
        runCatching { Application.getProcessName() == context.packageName }.getOrDefault(true)

    private companion object {
        const val TAG = "MultiTouchCoordinator"
    }
}
