package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.multitouch.MultiTouchGestureCoordinator
import com.slideindex.app.multitouch.MultiTouchGestureSettingsStore
import com.slideindex.app.multitouch.decodePathStrokes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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
