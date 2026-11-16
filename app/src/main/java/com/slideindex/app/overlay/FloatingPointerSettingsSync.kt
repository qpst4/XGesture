package com.slideindex.app.overlay

import android.os.Handler
import androidx.compose.runtime.MutableState
import com.slideindex.app.di.OverlayDependencies
import com.slideindex.app.monitoring.OverlayPerformanceMonitorBinding
import com.slideindex.app.settings.AppSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class FloatingPointerSettingsSync(
    private val window: FloatingPointerOverlayWindow
) {
    private val mainHandler get() = window.mainHandler

    fun startSettingsSync(deps: OverlayDependencies, settingsHolder: MutableState<AppSettings>) {
        window.settingsCollectJob?.cancel()
        window.settingsCollectJob = window.overlayScope.launch {
            deps.settingsRepository.settings.collectLatest { latest ->
                settingsHolder.value = latest
                OverlayPerformanceMonitorBinding.syncUserPreference(latest, window.appContext)
                onSettingsUpdated(latest)
            }
        }
    }

    fun onSettingsUpdated(settings: AppSettings) {
        window.session?.applyLayoutSettings(settings)
        if (settings.floatingPointerHideWhenJoystickReleased &&
            window.session?.joystickActive?.value != true &&
            window.session?.gestureCaptureActive != true
        ) {
            window.session?.pointerVisible?.value = false
        }
        if (window.session?.joystickActive?.value == true ||
            window.session?.radialMenuActive?.value == true ||
            window.session?.gestureCaptureActive == true ||
            window.session?.realtimeGestureActive?.value == true
        ) {
            resetIdleTimer()
            return
        }
        window.session?.let {
            window.windowLifecycle.collapseTouchCapture(
                it.joystickCenterX.floatValue,
                it.joystickCenterY.floatValue,
                forceCollapse = true
            )
        }
        resetIdleTimer()
    }

    fun resetIdleTimer() {
        window.idleHideRunnable?.let { mainHandler.removeCallbacks(it) }
        window.idleHideRunnable = null
        val settings = window.settingsState?.value ?: return
        if (!settings.floatingPointerHideWhenIdle) return
        if (window.session?.joystickActive?.value == true ||
            window.session?.radialMenuActive?.value == true ||
            window.session?.gestureReplayActive?.value == true ||
            window.session?.gestureCaptureActive == true ||
            window.session?.realtimeGestureActive?.value == true
        ) {
            return
        }
        val runnable = Runnable {
            if (window.session?.joystickActive?.value == true ||
                window.session?.radialMenuActive?.value == true ||
                window.session?.gestureReplayActive?.value == true ||
                window.session?.gestureCaptureActive == true ||
                window.session?.realtimeGestureActive?.value == true
            ) {
                resetIdleTimer()
                return@Runnable
            }
            window.dismiss()
        }
        window.idleHideRunnable = runnable
        mainHandler.postDelayed(runnable, settings.floatingPointerIdleHideDelayMs.toLong())
    }

    fun cancelSettingsSync() {
        window.settingsCollectJob?.cancel()
        window.settingsCollectJob = null
    }

    fun cancelIdleTimer() {
        window.idleHideRunnable?.let { mainHandler.removeCallbacks(it) }
        window.idleHideRunnable = null
    }
}
