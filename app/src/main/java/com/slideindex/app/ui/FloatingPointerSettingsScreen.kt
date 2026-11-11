package com.slideindex.app.ui

import androidx.compose.material.icons.outlined.KeyboardArrowUp
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.ui.gestureActionTypeOutlinedIcon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatingPointerBounds
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerSettingsScreen(
    settings: AppSettings,
    areaPreviewEnabled: Boolean,
    previewAccessibilityGranted: Boolean,
    onAreaPreviewEnabledChange: (Boolean) -> Unit,
    onSensitivityPreviewStart: () -> Unit = {},
    onSensitivityPreviewChange: (Float) -> Unit = {},
    onSensitivityPreviewStop: () -> Unit = {},
    onBack: () -> Unit,
    onOpenPointerSettings: () -> Unit,
    onOpenJoystickSettings: () -> Unit,
    onOpenRadialMenuSettings: () -> Unit,
    onOpenEdgeActionsSettings: () -> Unit,
    onPointerSensitivityChange: (Float) -> Unit
) {
    val joystickAreaSectionTitle = stringResource(R.string.floating_pointer_joystick_area_section)
    val previewDragHint = stringResource(R.string.floating_pointer_preview_drag_hint)
    val appearanceSectionTitle = stringResource(R.string.floating_pointer_settings_section_appearance)
    val bindTriggerTip = stringResource(R.string.floating_pointer_bind_trigger_tip)

    SettingsScreenScaffold(
        title = stringResource(R.string.floating_pointer_settings_title),
        pageHint = stringResource(R.string.floating_pointer_entry_desc),
        onBack = onBack
    ) {
        settingsLazyTipCard(
            key = "floating-pointer-bind-trigger-tip",
            text = bindTriggerTip,
        )
        settingsLazySmallTitle(
            key = "section-joystick-area",
            title = joystickAreaSectionTitle
        )
        groupedCardItems(
            keyPrefix = "floating-pointer-preview",
            items = buildList {
                add(
                    settingsCardScopeItem("area-preview") {
                        SettingSwitchRow(
                            title = stringResource(R.string.floating_pointer_area_preview_title),
                            subtitle = if (previewAccessibilityGranted) {
                                stringResource(R.string.floating_pointer_area_preview_desc)
                            } else {
                                stringResource(R.string.gesture_action_floating_pointer_permission)
                            },
                            checked = areaPreviewEnabled,
                            enabled = previewAccessibilityGranted,
                            onCheckedChange = onAreaPreviewEnabledChange
                        )
                    }
                )
            }
        )
        if (areaPreviewEnabled) {
            settingsLazyHint(
                key = "floating-pointer-preview-hint",
                text = previewDragHint
            )
        }
        groupedCardItems(
            keyPrefix = "floating-pointer-sensitivity",
            items = buildList {
                add(
                    settingsCardScopeItem("sensitivity") {
                        SettingsSliderRow(
                            title = stringResource(R.string.floating_pointer_sensitivity),
                            value = settings.floatingPointerSensitivityFraction,
                            valueRange = FloatingPointerBounds.SENSITIVITY_MIN..FloatingPointerBounds.SENSITIVITY_MAX,
                            steps = 10,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                (settings.floatingPointerSensitivityFraction * 100).roundToInt()
                            ),
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = onSensitivityPreviewStart,
                            onLayoutPreviewValueChange = onSensitivityPreviewChange,
                            onLayoutPreviewStop = onSensitivityPreviewStop,
                            onValueChange = onPointerSensitivityChange
                        )
                    }
                )
            }
        )
        settingsLazySmallTitle(
            key = "section-appearance",
            title = appearanceSectionTitle
        )
        groupedCardItems(
            keyPrefix = "floating-pointer-appearance",
            items = buildList {
                add(
                    settingsCardScopeItem("pointer-settings") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(
                                    HubLeadingIcons.floatingPointer(true),
                                    contentDescription = label
                                )
                            },
                            title = stringResource(R.string.floating_pointer_pointer_settings_title),
                            subtitle = stringResource(
                                R.string.floating_pointer_pointer_settings_summary,
                                settings.floatingPointerRingThicknessPx.roundToInt(),
                                settings.floatingPointerDotDiameterPx.roundToInt()
                            ),
                            onClick = onOpenPointerSettings
                        )
                    }
                )
                add(
                    settingsCardScopeItem("joystick-settings") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(
                                    imageVector = ThinActionIcons.Joystick,
                                    contentDescription = label
                                )
                            },
                            title = stringResource(R.string.floating_pointer_joystick_settings_title),
                            subtitle = stringResource(
                                R.string.floating_pointer_joystick_settings_summary,
                                settings.floatingPointerJoystickDiameterPx.roundToInt()
                            ),
                            onClick = onOpenJoystickSettings
                        )
                    }
                )
                add(
                    settingsCardScopeItem("radial-settings") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(
                                    gestureActionTypeOutlinedIcon(GestureActionType.OPEN_FLOATING_POINTER_RADIAL_MENU),
                                    contentDescription = label
                                )
                            },
                            title = stringResource(R.string.floating_pointer_radial_settings_title),
                            subtitle = gestureActionLabel(settings.floatingPointerJoystickLongPressAction),
                            onClick = onOpenRadialMenuSettings
                        )
                    }
                )
                add(
                    settingsCardScopeItem("edge-settings") {
                        SettingNavigationRow(
                            icon = { label -> SymbolsIcon("KeyboardArrowUp", contentDescription = label) },
                            title = stringResource(R.string.floating_pointer_edge_settings_title),
                            subtitle = stringResource(R.string.floating_pointer_edge_settings_summary),
                            onClick = onOpenEdgeActionsSettings
                        )
                    }
                )
            }
        )
    }
}

@Composable
fun SettingsCardScope.FloatingPointerEntryCard(
    settings: ExtensionHubSettings,
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    val subtitle = if (enabled) {
        stringResource(
            R.string.floating_pointer_entry_summary,
            settings.floatingPointerJoystickDiameterPx.roundToInt(),
            settings.floatingPointerPointerDiameterPx.roundToInt(),
            (settings.floatingPointerSensitivityFraction * 100).roundToInt()
        )
    } else {
        stringResource(R.string.floating_pointer_entry_desc)
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.floatingPointer(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.floating_pointer_settings_title),
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick
    )
}
