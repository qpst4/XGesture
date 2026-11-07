package com.slideindex.app.overlay

import androidx.compose.animation.core.Easing

data class OverlayScreenBounds(
    val width: Float,
    val height: Float
)

const val FLOATING_POINTER_PRESENCE_ANIMATION_MS = 280L
const val GESTURE_RECORDER_POINTER_ANIMATION_MS = 400
/** QC default `gestureRecorderColor` (#b2fd746c). */
const val DefaultGestureRecorderColorArgb = 0xB2FD746C.toInt()
const val FLOATING_POINTER_RADIAL_MENU_ANIMATION_MS = 220L
const val FLOATING_POINTER_EDGE_PREVIEW_ANIMATION_MS = 300L

/** Matches QC DecelerateInterpolator used for the click ring shrink. */
val QcPointerClickEasing = Easing { fraction ->
    val t = fraction.coerceIn(0f, 1f)
    1f - (1f - t) * (1f - t)
}
