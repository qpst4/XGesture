package com.slideindex.app.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatingPointerBounds
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import kotlin.math.roundToInt

fun floatBallPickAdvancedCardItems(
    settings: AppSettings,
    onPickBottomTransitionChange: (Float) -> Unit,
    onPointerSpeedChange: (Float) -> Unit,
    onPointerSpeedVerticalChange: (Float) -> Unit,
    onPickAccelZoneChange: (Float) -> Unit,
    onPointerSlopChange: (Float) -> Unit,
    onHoverPauseDelayMsChange: (Int) -> Unit,
    onRegionalCancelSlopDpChange: (Float) -> Unit,
): List<CardItem> = listOf(
    settingsCardScopeItem("pointer-speed") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_pointer_speed),
            value = settings.floatBallPointerSpeedFraction,
            valueRange = FloatingPointerBounds.PICK_POINTER_SPEED_MIN..
                FloatingPointerBounds.SENSITIVITY_MAX,
            // 0%–75%，每档 1%。
            steps = 74,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_percent_value,
                (settings.floatBallPointerSpeedFraction * 100).roundToInt(),
            ),
            onValueChange = onPointerSpeedChange,
        )
    },
    settingsCardScopeItem("pointer-speed-vertical") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_pointer_speed_vertical),
            value = settings.floatBallPointerSpeedVerticalFraction,
            valueRange = FloatingPointerBounds.PICK_POINTER_SPEED_MIN..
                FloatingPointerBounds.SENSITIVITY_MAX,
            // 0%–75%，每档 1%。
            steps = 74,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_percent_value,
                (settings.floatBallPointerSpeedVerticalFraction * 100).roundToInt(),
            ),
            onValueChange = onPointerSpeedVerticalChange,
        )
    },
    settingsCardScopeItem("pick-accel-zone") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_pick_accel_zone),
            value = settings.floatBallPickAccelZoneFraction,
            valueRange = FloatingPointerBounds.PICK_ACCEL_ZONE_MIN..
                FloatingPointerBounds.PICK_ACCEL_ZONE_MAX,
            // 0%–100%，每档 5%。
            steps = 19,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_percent_value,
                (settings.floatBallPickAccelZoneFraction * 100).roundToInt(),
            ),
            onValueChange = onPickAccelZoneChange,
        )
    },
    settingsCardScopeItem("bottom-transition") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_pick_bottom_transition),
            summary = stringResource(R.string.float_ball_pick_bottom_transition_desc),
            value = settings.floatBallPickBottomTransitionFraction,
            // 0% = 关闭：红十字允许低于手指（旧手感）；>0 时手指进入屏幕底部这段距离才放开。
            valueRange = 0f..FloatingPointerBounds.PICK_BOTTOM_TRANSITION_MAX,
            // 0%–22%，每档 1%。
            steps = (FloatingPointerBounds.PICK_BOTTOM_TRANSITION_MAX * 100f).roundToInt() - 1,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_percent_value,
                (settings.floatBallPickBottomTransitionFraction * 100).roundToInt(),
            ),
            onValueChange = onPickBottomTransitionChange,
        )
    },
    settingsCardScopeItem("pointer-slop") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_pointer_slop),
            value = settings.floatBallPointerSlopDp,
            // 0 = 手指一动就开始取词；调大 = 需要走够这段距离才启动取词（长按也在这段内不会被打断）。
            valueRange = 0f..32f,
            steps = 31,
            enabled = true,
            label = stringResource(R.string.float_ball_size_value, settings.floatBallPointerSlopDp),
            onValueChange = onPointerSlopChange,
        )
    },
    settingsCardScopeItem("hover-pause-delay") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_hover_pause_delay),
            value = settings.floatBallHoverPauseDelayMs.toFloat(),
            valueRange = 200f..1000f,
            steps = 15,
            enabled = true,
            label = stringResource(
                R.string.float_ball_pick_panel_animation_ms_value,
                settings.floatBallHoverPauseDelayMs,
            ),
            onValueChange = { onHoverPauseDelayMsChange(it.roundToInt()) },
        )
    },
    settingsCardScopeItem("regional-cancel-slop") {
        SettingsSliderRow(
            title = stringResource(R.string.float_ball_regional_cancel_slop),
            value = settings.floatBallRegionalCancelSlopDp,
            valueRange = 3f..30f,
            steps = 26,
            enabled = true,
            label = stringResource(
                R.string.float_ball_size_value,
                settings.floatBallRegionalCancelSlopDp,
            ),
            onValueChange = onRegionalCancelSlopDpChange,
        )
    },
)

fun LazyListScope.floatBallPickAdvancedSettingsGroup(
    keyPrefix: String,
    settings: AppSettings,
    onPickBottomTransitionChange: (Float) -> Unit,
    onPointerSpeedChange: (Float) -> Unit,
    onPointerSpeedVerticalChange: (Float) -> Unit,
    onPickAccelZoneChange: (Float) -> Unit,
    onPointerSlopChange: (Float) -> Unit,
    onHoverPauseDelayMsChange: (Int) -> Unit,
    onRegionalCancelSlopDpChange: (Float) -> Unit,
) {
    groupedCardItems(
        keyPrefix = keyPrefix,
        items = floatBallPickAdvancedCardItems(
            settings = settings,
            onPickBottomTransitionChange = onPickBottomTransitionChange,
            onPointerSpeedChange = onPointerSpeedChange,
            onPointerSpeedVerticalChange = onPointerSpeedVerticalChange,
            onPickAccelZoneChange = onPickAccelZoneChange,
            onPointerSlopChange = onPointerSlopChange,
            onHoverPauseDelayMsChange = onHoverPauseDelayMsChange,
            onRegionalCancelSlopDpChange = onRegionalCancelSlopDpChange,
        ),
    )
}
