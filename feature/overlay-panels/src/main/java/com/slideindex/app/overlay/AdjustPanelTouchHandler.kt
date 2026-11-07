package com.slideindex.app.overlay

import android.view.MotionEvent
import android.widget.Toast
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.util.PermissionHelper

class AdjustPanelTouchHandler(
    private val ctrl: AdjustPanelOverlayController
) {
    private val host get() = ctrl.host

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean {
        if (ctrl.adjustPanelDismissing) {
            // 退场动画期间窗口还没让出触摸：这一拍必须记下来，否则用户只会说"点哪都没反应"。
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
                com.slideindex.app.diagnostic.EdgeDiag.log(
                    "touchDrop",
                    "adjustPanelDismissing action=${MotionEvent.actionToString(action)} " +
                        "raw=(${event.rawX},${event.rawY}) → 吞掉（窗口尚未让出触摸）"
                )
            }
            return false
        }
        val state = ctrl.adjustPanelState ?: return false
        val density = host.density()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val layout = ctrl.adjustIndicatorLayout ?: run {
                    ctrl.dismissAdjustPanel()
                    return true
                }
                when (state.mode) {
                    com.slideindex.app.overlay.panels.PanelAdjustMode.VOLUME -> when (
                        AdjustLevelIndicator.hitVolumeTarget(layout, host.side(), localX, localY, density)
                    ) {
                        VolumeHitTarget.DND -> {
                            if (!host.actionHost().hasVolumeAccess(host.context)) {
                                PermissionHelper.requestNotificationPolicyAccess(host.context)
                            } else {
                                val executor = host.actionHost()
                                val changed = executor.toggleDnd() != null
                                state.interruptionFilter = executor.readInterruptionFilter()
                                if (changed) {
                                    host.hapticConfirmLaunch()
                                } else {
                                    Toast.makeText(
                                        host.context.applicationContext,
                                        R.string.gesture_action_toggle_dnd_failed,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            ctrl.scheduleAutoDismiss()
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.RINGER -> {
                            if (!host.actionHost().hasVolumeAccess(host.context)) {
                                PermissionHelper.requestNotificationPolicyAccess(host.context)
                            } else {
                                host.actionHost().cycleRingerMode()?.let { state.ringerMode = it }
                                host.hapticConfirmLaunch()
                            }
                            ctrl.scheduleAutoDismiss()
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.EXPAND -> {
                            state.volumeExpanded = !state.volumeExpanded
                            if (state.volumeExpanded) {
                                refreshVolumePanelLevels(state)
                            }
                            ctrl.updateAdjustIndicatorLayout(state.anchorRawY)
                            host.hapticConfirmLaunch()
                            ctrl.scheduleAutoDismiss()
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.MEDIA -> {
                            beginMainAdjustDrag(state, event.rawY)
                            return true
                        }
                        VolumeHitTarget.RING -> {
                            if (!state.volumeExpanded) return false
                            beginVolumeStreamDrag(state, VolumeDragTarget.RING, event.rawY)
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.NOTIFICATION -> {
                            if (!state.volumeExpanded) return false
                            beginVolumeStreamDrag(state, VolumeDragTarget.NOTIFICATION, event.rawY)
                            host.invalidate()
                            return true
                        }
                        VolumeHitTarget.NONE -> {
                            ctrl.dismissAdjustPanel()
                            return true
                        }
                    }
                    com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS -> when (
                        AdjustLevelIndicator.hitBrightnessTarget(layout, host.side(), localX, localY, density)
                    ) {
                        BrightnessHitTarget.AUTO_BRIGHTNESS -> {
                            if (!host.actionHost().hasBrightnessAccess(host.context)) {
                                PermissionHelper.requestWriteSettingsAccess(host.context)
                            } else {
                                host.actionHost().toggleAutoBrightness()?.let {
                                    state.autoBrightnessEnabled = it
                                    ctrl.syncBrightnessFractionAfterAutoToggle()
                                    host.hapticConfirmLaunch()
                                }
                            }
                            ctrl.scheduleAutoDismiss()
                            host.invalidate()
                            return true
                        }
                        BrightnessHitTarget.DARK_MODE -> {
                            if (!host.actionHost().hasDarkModeAccess(host.context)) {
                                PermissionHelper.requestWriteSettingsAccess(host.context)
                            } else {
                                host.actionHost().toggleDarkMode()?.let {
                                    state.darkModeEnabled = it
                                    host.hapticConfirmLaunch()
                                }
                            }
                            ctrl.scheduleAutoDismiss()
                            host.invalidate()
                            return true
                        }
                        BrightnessHitTarget.BRIGHTNESS -> {
                            beginMainAdjustDrag(state, event.rawY)
                            return true
                        }
                        BrightnessHitTarget.NONE -> {
                            ctrl.dismissAdjustPanel()
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (state.dragTarget == null) return false
                when (state.dragTarget) {
                    VolumeDragTarget.MEDIA -> {
                        host.actionHost().updateContinuousAdjust(state.mode, event.rawY)
                        state.fraction = host.actionHost().adjustFraction()
                    }
                    VolumeDragTarget.RING, VolumeDragTarget.NOTIFICATION -> {
                        updateVolumeStreamDrag(state, event.rawY)
                    }
                    null -> return false
                }
                host.invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (state.dragTarget == null) return false
                when (state.dragTarget) {
                    VolumeDragTarget.MEDIA -> {
                        if (state.mode == com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS) {
                            ctrl.suppressBrightnessLevelSync()
                        }
                        val committedFraction = if (state.mode == com.slideindex.app.overlay.panels.PanelAdjustMode.BRIGHTNESS) {
                            host.actionHost().adjustFraction()
                        } else {
                            null
                        }
                        host.actionHost().endContinuousAdjust()
                        state.fraction = committedFraction
                            ?: host.actionHost().readCurrentAdjustFraction(state.mode)
                    }
                    VolumeDragTarget.RING, VolumeDragTarget.NOTIFICATION -> Unit
                    null -> Unit
                }
                state.dragTarget = null
                state.dragging = false
                ctrl.scheduleAutoDismiss()
                host.invalidate()
                return true
            }
        }
        return false
    }

    fun refreshVolumePanelLevels(state: AdjustPanelState) {
        val executor = host.actionHost()
        state.fraction = executor.readCurrentAdjustFraction(com.slideindex.app.overlay.panels.PanelAdjustMode.VOLUME)
        state.ringFraction = executor.readVolumeFraction(com.slideindex.app.overlay.panels.PanelVolumeStream.RING)
        state.notificationFraction = executor.readVolumeFraction(com.slideindex.app.overlay.panels.PanelVolumeStream.NOTIFICATION)
        state.ringerMode = executor.readRingerMode()
        state.interruptionFilter = executor.readInterruptionFilter()
    }

    private fun beginMainAdjustDrag(state: AdjustPanelState, rawY: Float) {
        ctrl.cancelAutoDismiss()
        state.dragTarget = VolumeDragTarget.MEDIA
        state.dragging = true
        host.actionHost().beginContinuousAdjust(state.mode, rawY)
        host.actionHost().updateContinuousAdjust(state.mode, rawY)
        host.invalidate()
    }

    private fun beginVolumeStreamDrag(state: AdjustPanelState, target: VolumeDragTarget, rawY: Float) {
        ctrl.cancelAutoDismiss()
        state.dragTarget = target
        state.dragging = true
        ctrl.volumeDragAnchorRawY = rawY
        ctrl.volumeDragBaseline = when (target) {
            VolumeDragTarget.RING -> state.ringFraction
            VolumeDragTarget.NOTIFICATION -> state.notificationFraction
            VolumeDragTarget.MEDIA -> state.fraction
        }
    }

    private fun updateVolumeStreamDrag(state: AdjustPanelState, rawY: Float) {
        val span = host.screenHeightPx().coerceAtLeast(1) * VOLUME_DRAG_SPAN_SCREEN_FRACTION
        val fraction = ((ctrl.volumeDragBaseline + (ctrl.volumeDragAnchorRawY - rawY) / span)).coerceIn(0f, 1f)
        when (state.dragTarget) {
            VolumeDragTarget.RING -> {
                state.ringFraction = fraction
                host.actionHost().setVolumeFraction(com.slideindex.app.overlay.panels.PanelVolumeStream.RING, fraction)
            }
            VolumeDragTarget.NOTIFICATION -> {
                state.notificationFraction = fraction
                host.actionHost().setVolumeFraction(com.slideindex.app.overlay.panels.PanelVolumeStream.NOTIFICATION, fraction)
            }
            VolumeDragTarget.MEDIA, null -> Unit
        }
    }

    companion object {
        private const val VOLUME_DRAG_SPAN_SCREEN_FRACTION = 0.5f
    }
}
