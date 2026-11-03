package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.multitouch.MultiPointerBinding
import com.slideindex.app.gesture.multitouch.PathGestureTemplate
import com.slideindex.app.multitouch.MultiTouchGestureCoordinator
import com.slideindex.app.multitouch.MultiTouchGestureSettingsStore
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

    fun retry() {
        coordinator.restart()
    }
}
