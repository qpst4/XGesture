package com.slideindex.app.settings

/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.gesture.SelectedHintMetrics
import com.slideindex.app.overlay.layout.PanelIconShape

data class HoneycombDisplaySettings(
    val mode: Int = MODE_HOLD,
    val iconSizeDp: Int = DEFAULT_ICON_SIZE_DP,
    val iconShape: PanelIconShape = PanelIconShape.FOLLOW_GLOBAL,
    val spacingDp: Int = DEFAULT_SPACING_DP,
    val animationSpeed: Int = DEFAULT_ANIMATION_SPEED,
    val inertia: Int = DEFAULT_INERTIA,
    val centerScale: Int = DEFAULT_CENTER_SCALE,
    val edgeScale: Int = DEFAULT_EDGE_SCALE,
    val selectionScale: Int = DEFAULT_SELECTION_SCALE,
    val emptyTapClose: Boolean = true,
    val showSelectedName: Boolean = true,
    val selectedHintIconSizeDp: Int = SelectedHintMetrics.DEFAULT_ICON_SIZE_DP,
    val followFinger: Boolean = false,
    val fixedXPercent: Int = DEFAULT_FIXED_X_PERCENT,
    val fixedYPercent: Int = DEFAULT_FIXED_Y_PERCENT,
    val backgroundStyle: Int = BACKGROUND_BLUR,
    val blurDp: Int = DEFAULT_BLUR_DP,
    val dimPercent: Int = DEFAULT_DIM_PERCENT,
    val discSizePercent: Int = DEFAULT_DISC_SIZE_PERCENT,
) {
    companion object {
        const val MODE_BROWSE = 0
        const val MODE_HOLD = 1
        const val BACKGROUND_BLUR = 0
        const val BACKGROUND_BLACK = 1
        /** System wallpaper decoded + Gaussian blur (not live behind-window blur). */
        const val BACKGROUND_WALLPAPER_BLUR = 2

        const val MIN_ICON_SIZE_DP = 20
        const val MAX_ICON_SIZE_DP = 100
        const val DEFAULT_ICON_SIZE_DP = 48

        const val MIN_SPACING_DP = 24
        const val MAX_SPACING_DP = 120
        const val DEFAULT_SPACING_DP = 59

        const val MIN_ANIMATION_SPEED = 0
        const val MAX_ANIMATION_SPEED = 4
        const val DEFAULT_ANIMATION_SPEED = 2

        const val MIN_INERTIA = 0
        const val MAX_INERTIA = 2
        const val DEFAULT_INERTIA = 1

        const val MIN_CENTER_SCALE = 105
        const val MAX_CENTER_SCALE = 160
        const val DEFAULT_CENTER_SCALE = 124

        const val MIN_EDGE_SCALE = 40
        const val MAX_EDGE_SCALE = 90
        const val DEFAULT_EDGE_SCALE = 90

        const val MIN_SELECTION_SCALE = 105
        const val MAX_SELECTION_SCALE = 160
        const val DEFAULT_SELECTION_SCALE = 130

        const val DEFAULT_FIXED_X_PERCENT = 50
        const val DEFAULT_FIXED_Y_PERCENT = 60

        const val DEFAULT_BLUR_DP = 36
        const val MIN_BLUR_DP = 0
        const val MAX_BLUR_DP = 72

        const val DEFAULT_DIM_PERCENT = 22
        const val MIN_DIM_PERCENT = 0
        const val MAX_DIM_PERCENT = 60

        const val MIN_DISC_SIZE_PERCENT = 50
        const val MAX_DISC_SIZE_PERCENT = 100
        const val DEFAULT_DISC_SIZE_PERCENT = 60

        fun fromPreferences(prefs: Preferences): HoneycombDisplaySettings =
            HoneycombDisplaySettings(
                mode = prefs[SettingsPreferenceKeys.HONEYCOMB_MODE] ?: MODE_HOLD,
                iconSizeDp = prefs[SettingsPreferenceKeys.HONEYCOMB_ICON_SIZE_DP] ?: DEFAULT_ICON_SIZE_DP,
                iconShape = PanelIconShape.fromName(prefs[SettingsPreferenceKeys.HONEYCOMB_ICON_SHAPE]),
                spacingDp = prefs[SettingsPreferenceKeys.HONEYCOMB_SPACING_DP] ?: DEFAULT_SPACING_DP,
                animationSpeed = prefs[SettingsPreferenceKeys.HONEYCOMB_ANIMATION_SPEED] ?: DEFAULT_ANIMATION_SPEED,
                inertia = prefs[SettingsPreferenceKeys.HONEYCOMB_INERTIA] ?: DEFAULT_INERTIA,
                centerScale = prefs[SettingsPreferenceKeys.HONEYCOMB_CENTER_SCALE] ?: DEFAULT_CENTER_SCALE,
                edgeScale = prefs[SettingsPreferenceKeys.HONEYCOMB_EDGE_SCALE] ?: DEFAULT_EDGE_SCALE,
                selectionScale = prefs[SettingsPreferenceKeys.HONEYCOMB_SELECTION_SCALE] ?: DEFAULT_SELECTION_SCALE,
                emptyTapClose = prefs[SettingsPreferenceKeys.HONEYCOMB_EMPTY_TAP_CLOSE] ?: true,
                showSelectedName = prefs[SettingsPreferenceKeys.HONEYCOMB_SHOW_SELECTED_NAME] ?: true,
                selectedHintIconSizeDp = SelectedHintMetrics.clampIconSizeDp(
                    prefs[SettingsPreferenceKeys.HONEYCOMB_SELECTED_HINT_ICON_SIZE_DP]
                        ?: prefs[SettingsPreferenceKeys.SELECTED_HINT_ICON_SIZE_DP]
                        ?: SelectedHintMetrics.DEFAULT_ICON_SIZE_DP,
                ),
                followFinger = prefs[SettingsPreferenceKeys.HONEYCOMB_FOLLOW_FINGER] ?: false,
                fixedXPercent = prefs[SettingsPreferenceKeys.HONEYCOMB_FIXED_X_PERCENT] ?: DEFAULT_FIXED_X_PERCENT,
                fixedYPercent = prefs[SettingsPreferenceKeys.HONEYCOMB_FIXED_Y_PERCENT] ?: DEFAULT_FIXED_Y_PERCENT,
                backgroundStyle = prefs[SettingsPreferenceKeys.HONEYCOMB_BACKGROUND_STYLE] ?: BACKGROUND_BLUR,
                blurDp = prefs[SettingsPreferenceKeys.HONEYCOMB_BLUR_DP] ?: DEFAULT_BLUR_DP,
                dimPercent = prefs[SettingsPreferenceKeys.HONEYCOMB_DIM_PERCENT] ?: DEFAULT_DIM_PERCENT,
                discSizePercent = prefs[SettingsPreferenceKeys.HONEYCOMB_DISC_SIZE_PERCENT] ?: DEFAULT_DISC_SIZE_PERCENT,
            )
    }
}
