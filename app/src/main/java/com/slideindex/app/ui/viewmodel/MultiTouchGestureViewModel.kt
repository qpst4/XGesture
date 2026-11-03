package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.multitouch.MultiTouchGestureCoordinator
import com.slideindex.app.multitouch.MultiTouchGestureSettingsStore
import com.slideindex.app.multitouch.decodePathStrokes
import com.slideindex.app.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class MultiTouchGestureViewModel @Inject constructor(
    private val settingsStore: MultiTouchGestureSettingsStore,
    private val coordinator: MultiTouchGestureCoordinator,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings = settingsStore.state

    val status = coordinator.status

    private var floatBallWasEnabled = false

    init {
        coordinator.start()
    }

    fun setEnabled(enabled: Boolean) {
        settingsStore.setEnabled(enabled)
    }

    /** 动作选择页回写：同「类型 + 接触点数」覆盖，否则新增条目。 */
    fun applyEntry(
        kind: MultiPointerGesture,
        fingerCount: Int,
        pendingStrokes: String,
        action: GestureAction,
    ) {
        settingsStore.applyEntry(
            kind = kind,
            fingerCount = fingerCount,
            pendingStrokes = decodePathStrokes(pendingStrokes),
            action = action,
        )
    }

    fun removeEntry(id: String) {
        settingsStore.removeEntry(id)
    }

    /** 重录路径手势：覆盖轨迹，动作保留。 */
    fun updatePathStrokes(entryId: String, pendingStrokes: String) {
        settingsStore.updatePathStrokes(entryId, decodePathStrokes(pendingStrokes))
    }

    /** 录制路径手势期间暂停/恢复识别；同时临时隐藏悬浮球，避免画到边缘被它抢触摸。 */
    fun setRecording(recording: Boolean) {
        if (recording) {
            coordinator.pauseForRecording()
            viewModelScope.launch {
                floatBallWasEnabled = runCatching {
                    settingsRepository.readSnapshot().floatBallEnabled
                }.getOrDefault(false)
                if (floatBallWasEnabled) {
                    runCatching { settingsRepository.setFloatBallEnabled(false) }
                }
            }
        } else {
            coordinator.resumeAfterRecording()
            viewModelScope.launch {
                if (floatBallWasEnabled) {
                    runCatching { settingsRepository.setFloatBallEnabled(true) }
                }
                floatBallWasEnabled = false
            }
        }
    }

    fun retry() {
        coordinator.restart()
    }
}
