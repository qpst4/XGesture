package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.multitouch.MultiTouchGestureCoordinator
import com.slideindex.app.multitouch.MultiTouchGestureSettingsStore
import com.slideindex.app.multitouch.decodePathStrokes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MultiTouchGestureViewModel @Inject constructor(
    private val settingsStore: MultiTouchGestureSettingsStore,
    private val coordinator: MultiTouchGestureCoordinator,
) : ViewModel() {

    val settings = settingsStore.state

    val status = coordinator.status

    init {
        coordinator.start()
    }

    fun setEnabled(enabled: Boolean) {
        settingsStore.setEnabled(enabled)
    }

    /** 触摸协议：auto / type_a / type_b。 */
    fun setProtocol(protocol: String) {
        settingsStore.setProtocol(protocol)
    }

    /** 指定触摸设备路径；空串 = 自动。 */
    fun setDevicePath(path: String) {
        settingsStore.setDevicePath(path)
    }

    private val _diagnostics = MutableStateFlow("")
    val diagnostics: StateFlow<String> = _diagnostics.asStateFlow()

    private val _touchDevices = MutableStateFlow<List<String>>(emptyList())
    val touchDevices: StateFlow<List<String>> = _touchDevices.asStateFlow()

    /** 从 service 读取当前设备/协议诊断。 */
    fun refreshDiagnostics() {
        viewModelScope.launch {
            _diagnostics.value = runCatching { coordinator.readDiagnostics() }.getOrDefault("")
        }
    }

    /** 从 service 读取触摸设备候选（设置页「触摸设备」选择用）。 */
    fun refreshTouchDevices() {
        viewModelScope.launch {
            _touchDevices.value = runCatching { coordinator.readTouchDevices() }
                .getOrDefault(emptyList())
        }
    }

    /** 进页面时校准一次状态（只修正开关值，不主动重启观察器）。 */
    fun refreshStatus() {
        coordinator.refreshStatus()
    }

    /** 动作选择页回写：预设类型按「类型 + 接触点数」覆盖；绘制手势总是新增（带 entryId 才编辑）。 */
    fun applyEntry(
        kind: MultiPointerGesture,
        fingerCount: Int,
        pendingStrokes: String,
        action: GestureAction,
        entryId: String = "",
    ) {
        settingsStore.applyEntry(
            kind = kind,
            fingerCount = fingerCount,
            pendingStrokes = decodePathStrokes(pendingStrokes),
            action = action,
            entryId = entryId,
        )
    }

    fun removeEntry(id: String) {
        settingsStore.removeEntry(id)
    }

    /** 重录路径手势：覆盖轨迹，动作保留。 */
    fun updatePathStrokes(entryId: String, pendingStrokes: String) {
        settingsStore.updatePathStrokes(entryId, decodePathStrokes(pendingStrokes))
    }

    /** 录制路径手势期间暂停/恢复识别。 */
    fun setRecording(recording: Boolean) {
        if (recording) {
            coordinator.pauseForRecording()
        } else {
            coordinator.resumeAfterRecording()
        }
    }

    fun retry() {
        coordinator.restart()
    }
}
