package com.slideindex.app.ui

import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CornerGestureSettingsScreen(
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onLeftEnabledChange: (Boolean) -> Unit,
    onRightEnabledChange: (Boolean) -> Unit,
    onOpenInteractionAppearance: () -> Unit,
    onOpenSlots: () -> Unit
) {
    val corner = settings.cornerGestureSettings
    val subSettingsEnabled = serviceEnabled && corner.enabled
    val pageHint = buildString {
        append(stringResource(R.string.corner_gesture_settings_desc))
        append('\n')
        append(stringResource(R.string.corner_gesture_settings_hint))
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.corner_gesture_settings_title),
        pageHint = pageHint,
        onBack = onBack
    ) {
        groupedCardItems(
            keyPrefix = "corner-enabled",
            items = buildList {
                add(
                    settingsCardScopeItem("corner-enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_enabled),
                            subtitle = stringResource(R.string.corner_gesture_enabled_desc),
                            icon = { label -> Icon(HomeLeadingIcons.cornerWheel(true), contentDescription = label) },
                            checked = corner.enabled,
                            enabled = serviceEnabled,
                            onCheckedChange = onEnabledChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("corner-left-enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_left_enabled),
                            subtitle = stringResource(R.string.corner_gesture_left_enabled_desc),
                            checked = corner.leftEnabled,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onLeftEnabledChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("corner-right-enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_right_enabled),
                            subtitle = stringResource(R.string.corner_gesture_right_enabled_desc),
                            checked = corner.rightEnabled,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onRightEnabledChange
                        )
                    }
                )
            }
        )
        groupedCardItems(
            keyPrefix = "corner-navigation",
            items = buildList {
                add(
                    settingsCardScopeItem("interaction-appearance") {
                        SettingNavigationRow(
                            icon = { label -> SymbolsIcon("Tune", contentDescription = label) },
                            title = stringResource(R.string.corner_gesture_interaction_appearance_title),
                            subtitle = stringResource(R.string.corner_gesture_interaction_appearance_desc),
                            enabled = subSettingsEnabled,
                            onClick = onOpenInteractionAppearance
                        )
                    }
                )
                add(
                    settingsCardScopeItem("slots") {
                        SettingNavigationRow(
                            icon = { label -> Icon(HomeLeadingIcons.cornerWheel(true), contentDescription = label) },
                            title = stringResource(R.string.corner_gesture_slots_section),
                            subtitle = stringResource(R.string.corner_gesture_slots_entry_desc),
                            enabled = subSettingsEnabled,
                            onClick = onOpenSlots
                        )
                    }
                )
            }
        )
    }
}
