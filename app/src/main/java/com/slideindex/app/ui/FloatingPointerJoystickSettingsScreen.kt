package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatingPointerJoystickPreview
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingSpinnerRow
import com.slideindex.app.ui.settings.components.SettingsCardRow
import com.slideindex.app.ui.settings.components.SettingsHintText
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsGroupedRowBackground
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private enum class JoystickColorTarget {
    Inner,
    Outer,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerJoystickSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onJoystickDiameterChange: (Float) -> Unit,
    onInnerColorChange: (Int) -> Unit,
    onOuterColorChange: (Int) -> Unit,
    onGradientRadiusChange: (Float) -> Unit,
    onHideOnOutsideClickChange: (Boolean) -> Unit,
    onHideOnQuickSwipeChange: (Boolean) -> Unit,
    onHideWhenIdleChange: (Boolean) -> Unit,
    onIdleDelayChange: (Int) -> Unit,
    onReleaseClickModeChange: (com.slideindex.app.settings.FloatingPointerReleaseClickMode) -> Unit,
    onHoverEnterSelectChange: (Boolean) -> Unit,
    onClickDistanceThresholdChange: (Float) -> Unit,
    onResetVisualDefaults: () -> Unit,
    onResetBehaviorDefaults: () -> Unit
) {
    var colorTarget by remember { mutableStateOf<JoystickColorTarget?>(null) }
    var pickerInitialColor by remember { mutableIntStateOf(0) }
    var joystickPreviewDragging by remember { mutableStateOf(false) }
    var previewJoystickDiameterPx by remember {
        mutableFloatStateOf(settings.floatingPointerJoystickDiameterPx)
    }
    var previewGradientRadiusFraction by remember {
        mutableFloatStateOf(settings.floatingPointerJoystickGradientRadiusFraction)
    }
    var previewClickDistanceThresholdDp by remember {
        mutableFloatStateOf(settings.floatingPointerClickDistanceThresholdDp)
    }
    var previewIdleHideDelayMs by remember {
        mutableIntStateOf(settings.floatingPointerIdleHideDelayMs)
    }
    val density = LocalDensity.current.density

    LaunchedEffect(
        settings.floatingPointerJoystickDiameterPx,
        settings.floatingPointerJoystickGradientRadiusFraction,
        settings.floatingPointerClickDistanceThresholdDp,
        settings.floatingPointerIdleHideDelayMs
    ) {
        if (!joystickPreviewDragging) {
            previewJoystickDiameterPx = settings.floatingPointerJoystickDiameterPx
            previewGradientRadiusFraction = settings.floatingPointerJoystickGradientRadiusFraction
            previewClickDistanceThresholdDp = settings.floatingPointerClickDistanceThresholdDp
            previewIdleHideDelayMs = settings.floatingPointerIdleHideDelayMs
        }
    }

    val previewSettings = settings.copy(
        floatingPointer = settings.floatingPointer.copy(
            floatingPointerJoystickDiameterPx = previewJoystickDiameterPx,
            floatingPointerJoystickGradientRadiusFraction = previewGradientRadiusFraction,
            floatingPointerClickDistanceThresholdDp = previewClickDistanceThresholdDp,
            floatingPointerIdleHideDelayMs = previewIdleHideDelayMs
        )
    )

    if (colorTarget != null) {
        AnimationStyleColorPickerDialog(
            initialColor = pickerInitialColor,
            onDismissRequest = { colorTarget = null },
            onColorPicked = { color ->
                when (colorTarget) {
                    JoystickColorTarget.Inner -> onInnerColorChange(color)
                    JoystickColorTarget.Outer -> onOuterColorChange(color)
                    null -> Unit
                }
                colorTarget = null
            }
        )
    }

    val previewSectionTitle = stringResource(R.string.floating_pointer_preview_section)
    val visualSectionTitle = stringResource(R.string.floating_pointer_joystick_visual_section)
    val behaviorSectionTitle = stringResource(R.string.floating_pointer_joystick_behavior_section)
    val clickDistanceDesc = stringResource(R.string.floating_pointer_click_distance_threshold_desc)

    SettingsScreenScaffold(
        title = stringResource(R.string.floating_pointer_joystick_settings_title),
        onBack = onBack
    ) {
        settingsLazySmallTitle(key = "fp-joystick-preview-section", title = previewSectionTitle)
        item(key = "floating-pointer-joystick-preview") {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                FloatingPointerJoystickPreview(settings = previewSettings)
            }
        }

        settingsLazySmallTitle(key = "fp-joystick-visual-section", title = visualSectionTitle)
        groupedCardItems(
            keyPrefix = "fp-joystick-visual",
            items = buildList {
                add(
                    settingsCardScopeItem("size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.floating_pointer_joystick_size),
                            value = settings.floatingPointerJoystickDiameterPx,
                            valueRange = 180f..360f,
                            steps = 17,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_size_px_value,
                                settings.floatingPointerJoystickDiameterPx.roundToInt()
                            ),
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = { joystickPreviewDragging = true },
                            onLayoutPreviewStop = { joystickPreviewDragging = false },
                            onLayoutPreviewValueChange = { previewJoystickDiameterPx = it },
                            onValueChange = onJoystickDiameterChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("inner-color") {
                        AnimationStyleColorRow(
                            title = stringResource(R.string.floating_pointer_joystick_inner_color),
                            color = settings.floatingPointerJoystickInnerColorArgb,
                            enabled = true,
                            onClick = {
                                pickerInitialColor = settings.floatingPointerJoystickInnerColorArgb
                                colorTarget = JoystickColorTarget.Inner
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("outer-color") {
                        AnimationStyleColorRow(
                            title = stringResource(R.string.floating_pointer_joystick_outer_color),
                            color = settings.floatingPointerJoystickOuterColorArgb,
                            enabled = true,
                            onClick = {
                                pickerInitialColor = settings.floatingPointerJoystickOuterColorArgb
                                colorTarget = JoystickColorTarget.Outer
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("gradient-radius") {
                        SettingsSliderRow(
                            title = stringResource(R.string.floating_pointer_joystick_gradient_radius),
                            value = settings.floatingPointerJoystickGradientRadiusFraction,
                            valueRange = 0.5f..1f,
                            steps = 9,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                (settings.floatingPointerJoystickGradientRadiusFraction * 100).roundToInt()
                            ),
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = { joystickPreviewDragging = true },
                            onLayoutPreviewStop = { joystickPreviewDragging = false },
                            onLayoutPreviewValueChange = { previewGradientRadiusFraction = it },
                            onValueChange = onGradientRadiusChange
                        )
                    }
                )
            }
        )
        groupedCardItems(
            keyPrefix = "fp-joystick-reset-visual",
            items = buildList {
                add(
                    settingsCardScopeItem("reset") {
                        SettingLinkRow(
                            title = stringResource(R.string.floating_pointer_reset_joystick_visual),
                            onClick = onResetVisualDefaults
                        )
                    }
                )
            }
        )

        settingsLazySmallTitle(key = "fp-joystick-behavior-section", title = behaviorSectionTitle)
        groupedCardItems(
            keyPrefix = "fp-joystick-behavior",
            items = buildList {
                add(
                    settingsCardScopeItem("click-distance") {
                        SettingsSliderRow(
                            title = stringResource(R.string.floating_pointer_click_distance_threshold),
                            value = settings.floatingPointerClickDistanceThresholdDp,
                            valueRange = 1f..30f,
                            steps = 28,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_size_px_dp_value,
                                (settings.floatingPointerClickDistanceThresholdDp * density).roundToInt(),
                                settings.floatingPointerClickDistanceThresholdDp
                            ),
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = { joystickPreviewDragging = true },
                            onLayoutPreviewStop = { joystickPreviewDragging = false },
                            onLayoutPreviewValueChange = { previewClickDistanceThresholdDp = it },
                            onValueChange = onClickDistanceThresholdChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("click-distance-hint") {
                        SettingsHintText(clickDistanceDesc)
                    }
                )
                add(
                    settingsCardScopeItem("release-click") {
                        val modes =
                            com.slideindex.app.settings.FloatingPointerReleaseClickMode.entries
                        val labels = modes.map { mode ->
                            stringResource(
                                when (mode) {
                                    com.slideindex.app.settings.FloatingPointerReleaseClickMode.OFF ->
                                        R.string.floating_pointer_release_click_off

                                    com.slideindex.app.settings.FloatingPointerReleaseClickMode.CLICK_AND_DISMISS ->
                                        R.string.floating_pointer_release_click_and_dismiss

                                    com.slideindex.app.settings.FloatingPointerReleaseClickMode.CLICK_KEEP ->
                                        R.string.floating_pointer_release_click_keep_joystick
                                },
                            )
                        }
                        val selectedIndex = modes
                            .indexOf(settings.floatingPointerReleaseClickMode)
                            .coerceAtLeast(0)
                        SettingSpinnerRow(
                            title = stringResource(R.string.floating_pointer_release_click_mode),
                            subtitle = labels[selectedIndex],
                            dialogButtonText = stringResource(android.R.string.cancel),
                            items = labels.map { top.yukonga.miuix.kmp.basic.DropdownItem(text = it) },
                            selectedIndex = selectedIndex,
                            enabled = true,
                            onSelectedIndexChange = { index ->
                                modes.getOrNull(index)?.let(onReleaseClickModeChange)
                            },
                        )
                    }
                )
                add(
                    settingsCardScopeItem("hover-enter") {
                        SettingSwitchRow(
                            title = stringResource(R.string.floating_pointer_hover_enter_select),
                            subtitle = stringResource(R.string.floating_pointer_hover_enter_select_desc),
                            checked = settings.floatingPointerHoverEnterSelect,
                            enabled = true,
                            onCheckedChange = onHoverEnterSelectChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("hide-outside") {
                        SettingSwitchRow(
                            title = stringResource(R.string.floating_pointer_hide_outside_click),
                            subtitle = stringResource(R.string.floating_pointer_hide_outside_click_desc),
                            checked = settings.floatingPointerHideOnOutsideClick,
                            enabled = true,
                            onCheckedChange = onHideOnOutsideClickChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("hide-swipe") {
                        SettingSwitchRow(
                            title = stringResource(R.string.floating_pointer_hide_quick_swipe),
                            subtitle = stringResource(R.string.floating_pointer_hide_quick_swipe_desc),
                            checked = settings.floatingPointerHideOnQuickSwipe,
                            enabled = true,
                            onCheckedChange = onHideOnQuickSwipeChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("hide-idle") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.floating_pointer_hide_idle),
                            subtitle = stringResource(R.string.floating_pointer_hide_idle_desc),
                            checked = settings.floatingPointerHideWhenIdle,
                            enabled = true,
                            onCheckedChange = onHideWhenIdleChange
                        ) {
                            SettingsSliderRow(
                                title = stringResource(R.string.floating_pointer_hide_idle_delay),
                                value = settings.floatingPointerIdleHideDelayMs.toFloat(),
                                valueRange = 1000f..10000f,
                                steps = 8,
                                enabled = true,
                                label = stringResource(
                                    R.string.floating_pointer_hide_idle_delay_value,
                                    settings.floatingPointerIdleHideDelayMs / 1000
                                ),
                                triggersLayoutPreview = true,
                                onLayoutPreviewStart = { joystickPreviewDragging = true },
                                onLayoutPreviewStop = { joystickPreviewDragging = false },
                                onLayoutPreviewValueChange = { previewIdleHideDelayMs = it.roundToInt() },
                                onValueChange = { onIdleDelayChange(it.roundToInt()) }
                            )
                        }
                    }
                )
            }
        )
        groupedCardItems(
            keyPrefix = "fp-joystick-reset-behavior",
            items = buildList {
                add(
                    settingsCardScopeItem("reset") {
                        SettingLinkRow(
                            title = stringResource(R.string.floating_pointer_reset_joystick_behavior),
                            onClick = onResetBehaviorDefaults
                        )
                    }
                )
            }
        )
    }
}
