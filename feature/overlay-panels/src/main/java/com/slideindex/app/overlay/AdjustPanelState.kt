package com.slideindex.app.overlay

import android.media.AudioManager

data class AdjustPanelState(
    val mode: com.slideindex.app.overlay.panels.PanelAdjustMode,
    var fraction: Float,
    var anchorRawY: Float,
    var dragging: Boolean = false,
    var volumeExpanded: Boolean = false,
    var ringFraction: Float = 0f,
    var notificationFraction: Float = 0f,
    var ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
    var interruptionFilter: Int = android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
    var autoBrightnessEnabled: Boolean = false,
    var darkModeEnabled: Boolean = false,
    var dragTarget: VolumeDragTarget? = null
) {
    fun isDraggingVolume(): Boolean = dragTarget != null
}
