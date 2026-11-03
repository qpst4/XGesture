package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.multitouch.MultiPointerBinding
import com.slideindex.app.gesture.multitouch.PathGestureTemplate
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
        // 进入设置页时确保协调器已启动（正常由 Application 启动，这里兜底）。
        coordinator.start()
    }

    fun setEnabled(enabled: Boolean) {
        settingsStore.setEnabled(enabled)
    }

    fun setBinding(binding: MultiPointerBinding, action: GestureAction) {
        settingsStore.setBinding(binding, action)
    }

    fun addPathGesture(template: PathGestureTemplate) {
        settingsStore.addPathGesture(template)
    }

    fun removePathGesture(id: String) {
        settingsStore.removePathGesture(id)
    }

    fun setPathPriority(enabled: Boolean) {
        settingsStore.setPathPriority(enabled)
    }

    /** 动作选择页/子流程回写：`bindingKey=path` 时保存为一条新的路径手势。 */
    fun applyAction(bindingKey: String, pendingStrokes: String, action: GestureAction) {
        if (bindingKey == PATH_BINDING_KEY) {
            val strokes = decodePathStrokes(pendingStrokes)
            if (strokes.size < 2) return
            settingsStore.addPathGesture(
                PathGestureTemplate(
                    id = "path_${System.currentTimeMillis()}",
                    name = "手势 ${settingsStore.state.value.pathGestures.size + 1}",
                    strokes = strokes,
                    action = action,
                )
            )
            return
        }
        val binding = MultiPointerBinding.fromStorageKey(bindingKey) ?: return
        settingsStore.setBinding(binding, action)
    }

    /** 录制路径手势期间暂停/恢复识别，避免录制动作触发已有手势。 */
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

    companion object {
        const val PATH_BINDING_KEY = "path"
    }
}
