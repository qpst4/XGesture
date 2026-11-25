package com.slideindex.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.physicalkey.PhysicalKeyGesture
import com.slideindex.app.physicalkey.PhysicalKeyGestureCoordinator
import com.slideindex.app.physicalkey.PhysicalKeySettings
import com.slideindex.app.physicalkey.PhysicalKeySettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PhysicalKeyViewModel @Inject constructor(
    private val settingsStore: PhysicalKeySettingsStore,
    private val coordinator: PhysicalKeyGestureCoordinator,
) : ViewModel() {

    val settings: StateFlow<PhysicalKeySettings> = settingsStore.state
    val status = coordinator.status

    private val _diagnostics = MutableStateFlow("")
    val diagnostics: StateFlow<String> = _diagnostics.asStateFlow()

    init {
        coordinator.start()
    }

    fun setEnabled(enabled: Boolean) {
        settingsStore.setEnabled(enabled)
    }

    /**
     * 写某个按键手势的动作。
     *
     * @param screenOff true = 写「熄屏时动作」，false = 写「亮屏动作」（反馈 #395）。
     */
    fun setGestureAction(
        gesture: PhysicalKeyGesture,
        action: GestureAction,
        screenOff: Boolean = false,
    ) {
        val enabled = action !is GestureAction.None
        settingsStore.updateEntry(gesture, enabled = enabled, action = action, screenOff = screenOff)
    }

    fun setMediaPlaybackPrioritized(prioritized: Boolean) {
        settingsStore.setMediaPlaybackPrioritized(prioritized)
    }

    fun setInterceptNativeEvents(intercept: Boolean) {
        settingsStore.setInterceptNativeEvents(intercept)
    }

    fun setScreenOffTriggerEnabled(enabled: Boolean) {
        settingsStore.setScreenOffTriggerEnabled(enabled)
    }

    fun setLongPressTimeout(ms: Long) {
        settingsStore.setLongPressTimeoutMs(ms)
    }

    fun setDoubleClickTimeout(ms: Long) {
        settingsStore.setDoubleClickTimeoutMs(ms)
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            _diagnostics.value = coordinator.readDiagnostics()
        }
    }

    fun retry() {
        coordinator.retry()
    }
}
