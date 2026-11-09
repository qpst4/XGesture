package com.slideindex.app.overlay

import android.graphics.RectF

enum class BrightnessHitTarget {
    AUTO_BRIGHTNESS,
    BRIGHTNESS,
    DARK_MODE,
    NONE,
}

enum class AdjustPanelChrome {
    NONE,
    VOLUME,
    BRIGHTNESS,
}

enum class VolumeHitTarget {
    DND,
    MEDIA,
    RING,
    NOTIFICATION,
    RINGER,
    EXPAND,
    NONE,
}

data class BrightnessPanelVisual(
    val autoBrightnessEnabled: Boolean,
    val darkModeEnabled: Boolean
)

data class VolumePanelVisual(
    val expanded: Boolean,
    val ringFraction: Float,
    val notificationFraction: Float,
    val ringerMode: Int,
    /** 勿扰是否开启。由 [com.slideindex.app.overlay.panels.PanelActionHost.isDndFilter] 判定后传入，渲染层不再自行解释 filter。 */
    val dndEnabled: Boolean
)

data class AdjustLevelIndicatorLayout(
    val bounds: RectF,
    val track: RectF,
    val ringPill: RectF? = null,
    val ringTrack: RectF? = null,
    val notificationPill: RectF? = null,
    val notificationTrack: RectF? = null,
    val topPill: RectF? = null,
    val bottomPill: RectF? = null,
    val ringerButton: RectF? = null,
    val expandButton: RectF? = null
)
