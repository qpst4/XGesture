package com.slideindex.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.SelectedHintMetrics
import com.slideindex.app.overlay.SystemWallpaperBlurHelper
import com.slideindex.app.overlay.WallpaperPermissionLauncher
import com.slideindex.app.settings.HoneycombDisplaySettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_100
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoneycombDisplaySettingsScreen(
    display: HoneycombDisplaySettings,
    onBack: () -> Unit,
    onDisplayChange: (HoneycombDisplaySettings) -> Unit
) {
    val context = LocalContext.current
    var localDisplay by remember { mutableStateOf(display) }
    LaunchedEffect(display) { localDisplay = display }
    fun updateDisplay(next: HoneycombDisplaySettings) {
        localDisplay = next
        onDisplayChange(next)
    }
    fun ensureWallpaperPermission() {
        SystemWallpaperBlurHelper.requestWallpaperPermission(context)
    }

    val modeSectionTitle = stringResource(R.string.honeycomb_display_section_mode)
    val positionSectionTitle = stringResource(R.string.honeycomb_display_section_position)
    val layoutSectionTitle = stringResource(R.string.honeycomb_display_section_layout)
    val backgroundSectionTitle = stringResource(R.string.honeycomb_display_section_background)
    val animationSectionTitle = stringResource(R.string.honeycomb_display_section_animation)
    val backgroundStyles = listOf(
        HoneycombDisplaySettings.BACKGROUND_BLUR,
        HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR,
        HoneycombDisplaySettings.BACKGROUND_BLACK
    )
    val blurEnabled = localDisplay.backgroundStyle == HoneycombDisplaySettings.BACKGROUND_BLUR
        || localDisplay.backgroundStyle == HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR
    val speedLabels = listOf(
        stringResource(R.string.honeycomb_speed_very_fast),
        stringResource(R.string.honeycomb_speed_fast),
        stringResource(R.string.honeycomb_speed_normal),
        stringResource(R.string.honeycomb_speed_soft),
        stringResource(R.string.honeycomb_speed_slow)
    )
    val inertiaLabels = listOf(
        stringResource(R.string.honeycomb_inertia_low),
        stringResource(R.string.honeycomb_inertia_medium),
        stringResource(R.string.honeycomb_inertia_high)
    )

    SettingsScreenScaffold(
        title = stringResource(R.string.honeycomb_display_settings_entry),
        pageHint = stringResource(R.string.honeycomb_display_settings_entry_desc),
        onBack = onBack
    ) {
        settingsLazySmallTitle(
            key = "section-mode",
            title = modeSectionTitle
        )
        groupedCardItems(
            keyPrefix = "honeycomb-mode-radio",
            selectableGroup = true,
            items = buildList {
                add(
                    settingsCardScopeItem("mode-hold") {
                        SettingRadioRow(
                            title = stringResource(R.string.honeycomb_mode_hold),
                            subtitle = stringResource(R.string.honeycomb_mode_hold_desc),
                            selected = localDisplay.mode == HoneycombDisplaySettings.MODE_HOLD,
                            onClick = {
                                updateDisplay(localDisplay.copy(mode = HoneycombDisplaySettings.MODE_HOLD))
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("mode-browse") {
                        SettingRadioRow(
                            title = stringResource(R.string.honeycomb_mode_browse),
                            subtitle = stringResource(R.string.honeycomb_mode_browse_desc),
                            selected = localDisplay.mode == HoneycombDisplaySettings.MODE_BROWSE,
                            onClick = {
                                updateDisplay(localDisplay.copy(mode = HoneycombDisplaySettings.MODE_BROWSE))
                            }
                        )
                    }
                )
            }
        )
        groupedCardItems(
            keyPrefix = "honeycomb-mode",
            items = buildList {
                add(
                    settingsCardScopeItem("empty-tap-close") {
                        SettingToggleRow(
                            icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                            title = stringResource(R.string.honeycomb_empty_tap_close),
                            subtitle = stringResource(R.string.honeycomb_empty_tap_close_desc),
                            checked = localDisplay.emptyTapClose,
                            onCheckedChange = { updateDisplay(localDisplay.copy(emptyTapClose = it)) }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("show-selected-name") {
                        SettingToggleRow(
                            icon = { label -> Icon(Icons.Default.Tune, contentDescription = label) },
                            title = stringResource(R.string.honeycomb_show_selected_name),
                            subtitle = stringResource(R.string.honeycomb_show_selected_name_desc),
                            checked = localDisplay.showSelectedName,
                            onCheckedChange = { updateDisplay(localDisplay.copy(showSelectedName = it)) }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("selected-hint-icon-size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_selected_hint_icon_size),
                            value = localDisplay.selectedHintIconSizeDp.toFloat(),
                            valueRange = SelectedHintMetrics.MIN_ICON_SIZE_DP.toFloat()..
                                SelectedHintMetrics.MAX_ICON_SIZE_DP.toFloat(),
                            steps = SelectedHintMetrics.MAX_ICON_SIZE_DP - SelectedHintMetrics.MIN_ICON_SIZE_DP - 1,
                            enabled = localDisplay.showSelectedName,
                            label = stringResource(
                                R.string.selected_hint_icon_size_value,
                                localDisplay.selectedHintIconSizeDp
                            ),
                            onValueChange = {
                                updateDisplay(localDisplay.copy(selectedHintIconSizeDp = it.roundToInt()))
                            }
                        )
                    }
                )
            }
        )
        settingsLazySmallTitle(
            key = "section-position",
            title = positionSectionTitle
        )
        groupedCardItems(
            keyPrefix = "honeycomb-position",
            items = buildList {
                add(
                    settingsCardScopeItem("follow-finger") {
                        SettingToggleRow(
                            icon = { label -> Icon(Icons.Default.TouchApp, contentDescription = label) },
                            title = stringResource(R.string.honeycomb_follow_finger),
                            subtitle = stringResource(R.string.honeycomb_follow_finger_desc),
                            checked = localDisplay.followFinger,
                            onCheckedChange = { updateDisplay(localDisplay.copy(followFinger = it)) }
                        )
                    }
                )
                if (!localDisplay.followFinger) {
                    add(
                        settingsCardScopeItem("fixed-x") {
                            SettingsSliderRow(
                                title = stringResource(R.string.honeycomb_fixed_x),
                                value = localDisplay.fixedXPercent.toFloat(),
                                valueRange = 0f..100f,
                                enabled = true,
                                label = stringResource(
                                    R.string.floating_pointer_percent_value,
                                    localDisplay.fixedXPercent
                                ),
                                keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_100,
                                onValueChange = {
                                    updateDisplay(localDisplay.copy(fixedXPercent = it.roundToInt()))
                                }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("fixed-y") {
                            SettingsSliderRow(
                                title = stringResource(R.string.honeycomb_fixed_y),
                                value = localDisplay.fixedYPercent.toFloat(),
                                valueRange = 0f..100f,
                                enabled = true,
                                label = stringResource(
                                    R.string.floating_pointer_percent_value,
                                    localDisplay.fixedYPercent
                                ),
                                keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_100,
                                onValueChange = {
                                    updateDisplay(localDisplay.copy(fixedYPercent = it.roundToInt()))
                                }
                            )
                        }
                    )
                }
            }
        )
        settingsLazySmallTitle(
            key = "section-layout",
            title = layoutSectionTitle
        )
        groupedCardItems(
            keyPrefix = "honeycomb-layout",
            items = buildList {
                add(
                    settingsCardScopeItem("icon-size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_icon_size),
                            value = localDisplay.iconSizeDp.toFloat(),
                            valueRange = HoneycombDisplaySettings.MIN_ICON_SIZE_DP.toFloat()..
                                HoneycombDisplaySettings.MAX_ICON_SIZE_DP.toFloat(),
                            steps = 16,
                            enabled = true,
                            label = stringResource(R.string.corner_gesture_zone_dp_value, localDisplay.iconSizeDp),
                            onValueChange = { updateDisplay(localDisplay.copy(iconSizeDp = it.roundToInt())) }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("spacing") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_spacing),
                            value = localDisplay.spacingDp.toFloat(),
                            valueRange = HoneycombDisplaySettings.MIN_SPACING_DP.toFloat()..
                                HoneycombDisplaySettings.MAX_SPACING_DP.toFloat(),
                            steps = 24,
                            enabled = true,
                            label = stringResource(R.string.corner_gesture_zone_dp_value, localDisplay.spacingDp),
                            onValueChange = { updateDisplay(localDisplay.copy(spacingDp = it.roundToInt())) }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("disc-size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_disc_size),
                            value = localDisplay.discSizePercent.toFloat(),
                            valueRange = HoneycombDisplaySettings.MIN_DISC_SIZE_PERCENT.toFloat()..
                                HoneycombDisplaySettings.MAX_DISC_SIZE_PERCENT.toFloat(),
                            steps = 10,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                localDisplay.discSizePercent
                            ),
                            onValueChange = { updateDisplay(localDisplay.copy(discSizePercent = it.roundToInt())) }
                        )
                    }
                )
            }
        )
        settingsLazySmallTitle(
            key = "section-background",
            title = backgroundSectionTitle
        )
        groupedCardItems(
            keyPrefix = "honeycomb-background",
            items = buildList {
                add(
                    settingsCardScopeItem("background-style") {
                        SettingDropdownRow(
                            title = stringResource(R.string.honeycomb_display_section_background),
                            items = listOf(
                                stringResource(R.string.honeycomb_background_blur),
                                stringResource(R.string.honeycomb_background_wallpaper_blur),
                                stringResource(R.string.honeycomb_background_black)
                            ),
                            selectedIndex = backgroundStyles.indexOf(localDisplay.backgroundStyle).coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                val style = backgroundStyles[index]
                                updateDisplay(localDisplay.copy(backgroundStyle = style))
                                if (style == HoneycombDisplaySettings.BACKGROUND_WALLPAPER_BLUR &&
                                    !SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context)
                                ) {
                                    ensureWallpaperPermission()
                                }
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("blur-strength") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_blur_strength),
                            value = localDisplay.blurDp.toFloat(),
                            valueRange = HoneycombDisplaySettings.MIN_BLUR_DP.toFloat()..
                                HoneycombDisplaySettings.MAX_BLUR_DP.toFloat(),
                            steps = 16,
                            enabled = blurEnabled,
                            label = stringResource(R.string.corner_gesture_zone_dp_value, localDisplay.blurDp),
                            onValueChange = { updateDisplay(localDisplay.copy(blurDp = it.roundToInt())) }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("dim-percent") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_dim_percent),
                            value = localDisplay.dimPercent.toFloat(),
                            valueRange = HoneycombDisplaySettings.MIN_DIM_PERCENT.toFloat()..
                                HoneycombDisplaySettings.MAX_DIM_PERCENT.toFloat(),
                            steps = 12,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                localDisplay.dimPercent
                            ),
                            onValueChange = { updateDisplay(localDisplay.copy(dimPercent = it.roundToInt())) }
                        )
                    }
                )
            }
        )
        settingsLazySmallTitle(
            key = "section-animation",
            title = animationSectionTitle
        )
        groupedCardItems(
            keyPrefix = "honeycomb-animation",
            items = buildList {
                add(
                    settingsCardScopeItem("animation-speed") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_animation_speed),
                            value = localDisplay.animationSpeed.toFloat(),
                            valueRange = HoneycombDisplaySettings.MIN_ANIMATION_SPEED.toFloat()..
                                HoneycombDisplaySettings.MAX_ANIMATION_SPEED.toFloat(),
                            steps = 4,
                            enabled = true,
                            label = speedLabels[localDisplay.animationSpeed.coerceIn(0, speedLabels.lastIndex)],
                            onValueChange = { updateDisplay(localDisplay.copy(animationSpeed = it.roundToInt())) }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("inertia") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_inertia),
                            value = localDisplay.inertia.toFloat(),
                            valueRange = HoneycombDisplaySettings.MIN_INERTIA.toFloat()..
                                HoneycombDisplaySettings.MAX_INERTIA.toFloat(),
                            steps = 2,
                            enabled = true,
                            label = inertiaLabels[localDisplay.inertia.coerceIn(0, inertiaLabels.lastIndex)],
                            onValueChange = { updateDisplay(localDisplay.copy(inertia = it.roundToInt())) }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("center-scale") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_center_scale),
                            value = localDisplay.centerScale.toFloat(),
                            valueRange = HoneycombDisplaySettings.MIN_CENTER_SCALE.toFloat()..
                                HoneycombDisplaySettings.MAX_CENTER_SCALE.toFloat(),
                            steps = 11,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                localDisplay.centerScale
                            ),
                            onValueChange = { updateDisplay(localDisplay.copy(centerScale = it.roundToInt())) }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("edge-scale") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_edge_scale),
                            value = localDisplay.edgeScale.toFloat(),
                            valueRange = HoneycombDisplaySettings.MIN_EDGE_SCALE.toFloat()..
                                HoneycombDisplaySettings.MAX_EDGE_SCALE.toFloat(),
                            steps = 10,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                localDisplay.edgeScale
                            ),
                            onValueChange = { updateDisplay(localDisplay.copy(edgeScale = it.roundToInt())) }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("selection-scale") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_selection_scale),
                            value = localDisplay.selectionScale.toFloat(),
                            valueRange = HoneycombDisplaySettings.MIN_SELECTION_SCALE.toFloat()..
                                HoneycombDisplaySettings.MAX_SELECTION_SCALE.toFloat(),
                            steps = 11,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                localDisplay.selectionScale
                            ),
                            onValueChange = { updateDisplay(localDisplay.copy(selectionScale = it.roundToInt())) }
                        )
                    }
                )
            }
        )
    }
}
