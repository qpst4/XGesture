package com.slideindex.app.multitouch

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.gesture.multitouch.PathGestureMatcher
import com.slideindex.app.gesture.multitouch.PathGestureTemplate
import com.slideindex.app.gesture.multitouch.PathPoint
import com.slideindex.app.gesture.multitouch.PathStroke
import com.slideindex.app.gesture.multitouch.displayName
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.shake.ShakeVibrationHelper
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
import rikka.shizuku.Shizuku

/** 多指手势运行状态（设置页展示用）。 */
data class MultiTouchStatus(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val detail: String = "",
    val shizukuAvailable: Boolean = false,
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

    @Volatile
    private var lastActionAtMs = 0L

    private val callback = object : IOnMultiTouchGesture.Stub() {
        override fun onGesture(gesture: Int, fingerCount: Int, eventTimeMs: Long) {
            dispatchGesture(gesture, fingerCount)
        }

        override fun onGestureBatch(
            kinds: IntArray?,
            fingerCounts: IntArray?,
            eventTimeMs: Long,
        ) {
            val kindArray = kinds ?: return
            val fingerArray = fingerCounts ?: return
            if (kindArray.size != fingerArray.size) return
            for (index in kindArray.indices) {
                if (dispatchGesture(kindArray[index], fingerArray[index])) return
            }
        }

        override fun onPathGesture(
            fingerCount: Int,
            points: FloatArray?,
            strokeLengths: IntArray?,
            eventTimeMs: Long,
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
            val candidates = settingsStore.state.value.entries
                .filter { it.isPath && it.fingerCount == fingerCount && it.pathStrokes.isNotEmpty() }
            if (candidates.isEmpty()) return
            val templates = candidates.map { entry ->
                PathGestureTemplate(
                    id = entry.id,
                    name = "${entry.fingerCount} 指路径",
                    strokes = entry.pathStrokes,
                    action = entry.action,
                )
            }
            val match = PathGestureMatcher.bestMatch(strokes, templates) ?: return
            if (match.action == GestureAction.None) return
            Log.i(TAG, "path gesture -> ${match.id}")
            mainHandler.post { performAction(match.action, MultiPointerGesture.PATH, fingerCount) }
        }

        override fun onObserverState(running: Boolean, detail: String) {
            _status.value = _status.value.copy(running = running, detail = detail)
        }

        override fun onInputConflict(devices: String?) {
            val value = devices?.takeIf { it.isNotBlank() }
            Log.i(TAG, "input conflict: $value")
            _status.value = _status.value.copy(conflictDevices = value)
        }
    }

    fun start() {
        if (started) return
        started = true
        if (!isMainProcess()) return
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        }
        applicationScope.launch(Dispatchers.IO) {
            settingsStore.state.collect { syncNow() }
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
                Log.i(TAG, "sync: shizuku unavailable")
                return
            }

            val service = runCatching { ShizukuUserServiceHost.ensure(context) }.getOrNull()
            if (service == null) {
                _status.value = _status.value.copy(running = false, detail = "service-unavailable")
                Log.i(TAG, "sync: user service unavailable")
                return
            }
            val startedObservation = runCatching {
                service.startMultiTouchObservation(callback, false)
            }.getOrDefault(false)
            Log.i(TAG, "sync: start observation=$startedObservation")
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

    private fun performAction(
        action: GestureAction,
        kind: MultiPointerGesture,
        fingerCount: Int,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAtMs < ACTION_COOLDOWN_MS) return
        lastActionAtMs = now
        runCatching { ShakeVibrationHelper.vibrate(context) }
        MultiTouchGestureFeedback.show(
            context = SlideIndexAccessibilityService.overlayHostContext() ?: context,
            text = "$fingerCount 指 · ${kind.displayName()}",
        )
        runCatching {
            val executor = ActionExecutor(
                context = SlideIndexAccessibilityService.overlayHostContext() ?: context,
                appRepository = appRepository,
            )
            executor.execute(action, settingsRepository.readSnapshot())
        }.onFailure {
            Log.w(TAG, "perform action failed: ${it.message}")
        }
    }

    private fun isMainProcess(): Boolean =
        runCatching { Application.getProcessName() == context.packageName }.getOrDefault(true)

    /** 候选按顺序匹配条目，命中第一个就执行并返回 true。 */
    private fun dispatchGesture(gesture: Int, fingerCount: Int): Boolean {
        val kind = MultiPointerGesture.entries.getOrNull(gesture) ?: return false
        val entry = settingsStore.state.value.entries.firstOrNull {
            it.kind == kind && it.fingerCount == fingerCount &&
                it.action != GestureAction.None
        } ?: run {
            Log.i(TAG, "no entry for ${kind.name} x$fingerCount")
            return false
        }
        Log.i(TAG, "gesture ${kind.name} x$fingerCount -> ${entry.action}")
        mainHandler.post { performAction(entry.action, kind, fingerCount) }
        return true
    }

    private companion object {
        const val TAG = "MultiTouchCoordinator"

        /** 同一手势可能同时命中内置条目与路径条目：这个窗口内只执行一次。 */
        const val ACTION_COOLDOWN_MS = 1_000L
    }
}
