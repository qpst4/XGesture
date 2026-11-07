package com.slideindex.app.overlay

/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.HoneycombDisplaySettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.gesture.SelectedHintMetrics

/**
 * Runtime honeycomb overlay parameters, mapped from persisted [HoneycombDisplaySettings].
 */
data class HoneycombDisplayConfig(
    val hapticEnabled: Boolean = true,
    val forceCircularIcons: Boolean = true,
    val honeycombMode: Int = HoneycombDisplaySettings.MODE_HOLD,
    val honeycombIconSizeDp: Int = HoneycombDisplaySettings.DEFAULT_ICON_SIZE_DP,
    val honeycombSpacingDp: Int = HoneycombDisplaySettings.DEFAULT_SPACING_DP,
    val honeycombAnimationSpeed: Int = HoneycombDisplaySettings.DEFAULT_ANIMATION_SPEED,
    val honeycombInertia: Int = HoneycombDisplaySettings.DEFAULT_INERTIA,
    val honeycombCenterScale: Int = HoneycombDisplaySettings.DEFAULT_CENTER_SCALE,
    val honeycombEdgeScale: Int = HoneycombDisplaySettings.DEFAULT_EDGE_SCALE,
    val honeycombSelectionScale: Int = HoneycombDisplaySettings.DEFAULT_SELECTION_SCALE,
    val honeycombEmptyTapClose: Boolean = true,
    val honeycombShowSelectedName: Boolean = true,
    val honeycombFollowFinger: Boolean = false,
    val honeycombFixedXPercent: Int = HoneycombDisplaySettings.DEFAULT_FIXED_X_PERCENT,
    val honeycombFixedYPercent: Int = HoneycombDisplaySettings.DEFAULT_FIXED_Y_PERCENT,
    val honeycombBackgroundStyle: Int = HoneycombDisplaySettings.BACKGROUND_BLUR,
    val honeycombBlurDp: Int = HoneycombDisplaySettings.DEFAULT_BLUR_DP,
    val honeycombDimPercent: Int = HoneycombDisplaySettings.DEFAULT_DIM_PERCENT,
    val honeycombDiscSizePercent: Int = HoneycombDisplaySettings.DEFAULT_DISC_SIZE_PERCENT,
    val selectedHintIconSizeDp: Int = SelectedHintMetrics.DEFAULT_ICON_SIZE_DP,
    val launchLongPressDurationMs: Int = 450,
    val launchLongPressTrackingEnabled: Boolean = false
) {
    companion object {
        const val MODE_BROWSE = HoneycombDisplaySettings.MODE_BROWSE
        const val MODE_HOLD = HoneycombDisplaySettings.MODE_HOLD
        const val BACKGROUND_BLUR = HoneycombDisplaySettings.BACKGROUND_BLUR
        const val BACKGROUND_BLACK = HoneycombDisplaySettings.BACKGROUND_BLACK
        const val BACKGROUND_WALLPAPER_BLUR = HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR
        const val MIN_DISC_SIZE_PERCENT = HoneycombDisplaySettings.MIN_DISC_SIZE_PERCENT
        const val MAX_DISC_SIZE_PERCENT = HoneycombDisplaySettings.MAX_DISC_SIZE_PERCENT
        const val MIN_DIM_PERCENT = HoneycombDisplaySettings.MIN_DIM_PERCENT
        const val MAX_DIM_PERCENT = HoneycombDisplaySettings.MAX_DIM_PERCENT

        fun from(settings: AppSettings): HoneycombDisplayConfig {
            val display = settings.honeycombDisplay
            return HoneycombDisplayConfig(
                hapticEnabled = settings.hapticEnabled,
                honeycombMode = display.mode,
                honeycombIconSizeDp = display.iconSizeDp,
                honeycombSpacingDp = display.spacingDp,
                honeycombAnimationSpeed = display.animationSpeed,
                honeycombInertia = display.inertia,
                honeycombCenterScale = display.centerScale,
                honeycombEdgeScale = display.edgeScale,
                honeycombSelectionScale = display.selectionScale,
                honeycombEmptyTapClose = display.emptyTapClose,
                honeycombShowSelectedName = display.showSelectedName,
                honeycombFollowFinger = display.followFinger,
                honeycombFixedXPercent = display.fixedXPercent,
                honeycombFixedYPercent = display.fixedYPercent,
                honeycombBackgroundStyle = display.backgroundStyle,
                honeycombBlurDp = display.blurDp,
                honeycombDimPercent = display.dimPercent,
                honeycombDiscSizePercent = display.discSizePercent,
                selectedHintIconSizeDp = display.selectedHintIconSizeDp,
                launchLongPressDurationMs = settings.effectiveLongPressDurationMs(),
                launchLongPressTrackingEnabled = settings.launchPolicyLongPressEligible()
            )
        }
    }
}
