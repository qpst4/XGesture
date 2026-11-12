package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.HomeMainSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.util.HapticHelper
import kotlin.math.roundToInt

/**
 * 「震动」子页面：总开关 + 强度 + 逐个事件开关。
 *
 * 每个开关背后是一次具体的触感事件（越过短距、折返、悬停、长按…），由
 * [HapticHelper.play] 按事件查开关；关闭只影响该事件，不影响其它。
 */
@Composable
fun HapticSettingsScreen(
    settings: HomeMainSettings,
    onBack: () -> Unit,
    onHapticEnabledChange: (Boolean) -> Unit,
    onHapticStrengthChange: (Int) -> Unit,
    onHapticShortThresholdEnabledChange: (Boolean) -> Unit,
    onHapticCompoundTurnEnabledChange: (Boolean) -> Unit,
    onHapticReturnSwipeEnabledChange: (Boolean) -> Unit,
    onHapticHoverEnabledChange: (Boolean) -> Unit,
    onHapticLongPressEnabledChange: (Boolean) -> Unit,
    onHapticLongThresholdEnabledChange: (Boolean) -> Unit,
    onHapticSlotLetterEnabledChange: (Boolean) -> Unit,
    onHapticSlotAppEnabledChange: (Boolean) -> Unit,
    onHapticConfirmLaunchEnabledChange: (Boolean) -> Unit,
    onHapticActionConfirmEnabledChange: (Boolean) -> Unit,
) {
    val view = LocalView.current
    val lightLabel = stringResource(R.string.haptic_strength_light)
    val mediumLabel = stringResource(R.string.haptic_strength_medium)
    val strongLabel = stringResource(R.string.haptic_strength_strong)
    val strengthLabel: (Float) -> String = remember(lightLabel, mediumLabel, strongLabel) {
        { level ->
            when (level.roundToInt()) {
                0 -> lightLabel
                1 -> mediumLabel
                else -> strongLabel
            }
        }
    }

    val gestureGroupTitle = stringResource(R.string.haptic_group_gesture)
    val pickGroupTitle = stringResource(R.string.haptic_group_pick)

    SettingsScreenScaffold(
        title = stringResource(R.string.haptic_settings_title),
        subtitle = stringResource(R.string.haptic_settings_desc),
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "haptic-master",
            items = buildList {
                add(
                    settingsCardScopeItem("enabled") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.haptic_enabled),
                            checked = settings.hapticEnabled,
                            enabled = true,
                            onCheckedChange = onHapticEnabledChange,
                        ) {
                            SettingsSliderRow(
                                title = stringResource(R.string.haptic_strength),
                                value = settings.hapticStrengthLevel.toFloat(),
                                valueRange = 0f..2f,
                                steps = 1,
                                enabled = true,
                                label = strengthLabel(settings.hapticStrengthLevel.toFloat()),
                                formatLabel = strengthLabel,
                                commitOnFinish = true,
                                triggersLayoutPreview = true,
                                onLayoutPreviewValueChange = { level ->
                                    HapticHelper.preview(
                                        view,
                                        AppSettings(
                                            hapticEnabled = true,
                                            hapticStrengthLevel = level.roundToInt(),
                                        ),
                                    )
                                },
                                onValueChange = { onHapticStrengthChange(it.roundToInt()) },
                            )
                        }
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "haptic-gesture-title",
            title = gestureGroupTitle,
        )
        groupedCardItems(
            keyPrefix = "haptic-gesture",
            items = buildList {
                add(
                    settingsCardScopeItem("short") {
                        SettingSwitchRow(
                            title = stringResource(R.string.haptic_short_threshold_title),
                            subtitle = stringResource(R.string.haptic_short_threshold_desc),
                            checked = settings.hapticShortThresholdEnabled,
                            enabled = true,
                            onCheckedChange = onHapticShortThresholdEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("turn") {
                        SettingSwitchRow(
                            title = stringResource(R.string.haptic_compound_turn_title),
                            subtitle = stringResource(R.string.haptic_compound_turn_desc),
                            checked = settings.hapticCompoundTurnEnabled,
                            enabled = true,
                            onCheckedChange = onHapticCompoundTurnEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("return") {
                        SettingSwitchRow(
                            title = stringResource(R.string.haptic_return_swipe_title),
                            subtitle = stringResource(R.string.haptic_return_swipe_desc),
                            checked = settings.hapticReturnSwipeEnabled,
                            enabled = true,
                            onCheckedChange = onHapticReturnSwipeEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("hover") {
                        SettingSwitchRow(
                            title = stringResource(R.string.haptic_hover_title),
                            subtitle = stringResource(R.string.haptic_hover_desc),
                            checked = settings.hapticHoverEnabled,
                            enabled = true,
                            onCheckedChange = onHapticHoverEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("long-press") {
                        SettingSwitchRow(
                            title = stringResource(R.string.haptic_long_press_title),
                            subtitle = stringResource(R.string.haptic_long_press_desc),
                            checked = settings.hapticLongPressEnabled,
                            enabled = true,
                            onCheckedChange = onHapticLongPressEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("long-distance") {
                        SettingSwitchRow(
                            title = stringResource(R.string.haptic_long_threshold_title),
                            subtitle = stringResource(R.string.haptic_long_threshold_desc),
                            checked = settings.hapticLongThresholdEnabled,
                            enabled = true,
                            onCheckedChange = onHapticLongThresholdEnabledChange,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "haptic-pick-title",
            title = pickGroupTitle,
        )
        groupedCardItems(
            keyPrefix = "haptic-pick",
            items = buildList {
                add(
                    settingsCardScopeItem("slot-letter") {
                        SettingSwitchRow(
                            title = stringResource(R.string.haptic_slot_letter_title),
                            subtitle = stringResource(R.string.haptic_slot_letter_desc),
                            checked = settings.hapticSlotLetterEnabled,
                            enabled = true,
                            onCheckedChange = onHapticSlotLetterEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("slot-app") {
                        SettingSwitchRow(
                            title = stringResource(R.string.haptic_slot_app_title),
                            subtitle = stringResource(R.string.haptic_slot_app_desc),
                            checked = settings.hapticSlotAppEnabled,
                            enabled = true,
                            onCheckedChange = onHapticSlotAppEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("confirm-launch") {
                        SettingSwitchRow(
                            title = stringResource(R.string.haptic_confirm_launch_title),
                            subtitle = stringResource(R.string.haptic_confirm_launch_desc),
                            checked = settings.hapticConfirmLaunchEnabled,
                            enabled = true,
                            onCheckedChange = onHapticConfirmLaunchEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("confirm-action") {
                        SettingSwitchRow(
                            title = stringResource(R.string.haptic_action_confirm_title),
                            subtitle = stringResource(R.string.haptic_action_confirm_desc),
                            checked = settings.hapticActionConfirmEnabled,
                            enabled = true,
                            onCheckedChange = onHapticActionConfirmEnabledChange,
                        )
                    },
                )
            },
        )
    }
}
