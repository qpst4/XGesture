@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import top.yukonga.miuix.kmp.basic.SmallTitle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.shake.ShakeSensitivityScale
import com.slideindex.app.shake.ShakeWorkMode
import com.slideindex.app.shake.ShakeWorkModes
import com.slideindex.app.shake.includesScreenOff
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow
import com.slideindex.app.ui.miuix.MiuixHubScaffold
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingCheckboxRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsCardScopeContent
import com.slideindex.app.ui.settings.components.settingsCardItem
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShakeGesturesScreen(
    settings: ShakeGestureSettings,
    bottomContentPadding: Dp = 0.dp,
    bottomNavReselectCount: Int = 0,
    onEnabledChange: (Boolean) -> Unit,
    onWorkModesChange: (Set<ShakeWorkMode>) -> Unit,
    onIndependentAppShakeEnabledChange: (Boolean) -> Unit,
    onGlobalSensitivityChange: (Float) -> Unit,
    onIndependentSensitivityEnabledChange: (Boolean) -> Unit,
    onOpenIndependentSensitivity: () -> Unit = {},
    onAnimationFeedbackEnabledChange: (Boolean) -> Unit,
    onVibrationFeedbackEnabledChange: (Boolean) -> Unit,
    onAnimationColorChange: (Int) -> Unit,
    onDisableInLandscapeChange: (Boolean) -> Unit,
    onOpenLockScreenShakeSettings: () -> Unit = {},
    onOpenIndependentAppShakeSettings: () -> Unit = {},
    onOpenAppBlacklist: () -> Unit = {},
    onOpenShakeMenuSettings: () -> Unit = {},
    onOpenBasicActionPick: (ShakeGestureType) -> Unit = {},
    onOpenFaceDownSettings: () -> Unit = {},
    onOpenBackTapSettings: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    BottomNavReselectScrollEffect(
        reselectCount = bottomNavReselectCount,
        listState = listState,
    )
    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        AnimationStyleColorPickerDialog(
            initialColor = settings.animationColorArgb,
            onDismissRequest = { showColorPicker = false },
            onColorPicked = { color ->
                onAnimationColorChange(color)
                showColorPicker = false
            },
        )
    }

    val basicSectionTitle = stringResource(R.string.shake_gestures_section_basic)
    val serviceSectionTitle = stringResource(R.string.settings_section_general)
    val workModeSectionTitle = stringResource(R.string.shake_work_mode_section)
    val workModeBatteryHint = stringResource(R.string.shake_work_mode_battery_hint)
    val workModeLockScreenHint = stringResource(R.string.shake_work_mode_lock_screen_actions_hint)
    val actionScopeSectionTitle = stringResource(R.string.shake_gestures_section_action_scope)
    val otherGesturesSectionTitle = stringResource(R.string.shake_gestures_section_other)
    val otherGesturesHint = stringResource(R.string.shake_gestures_other_hint)
    val sensitivitySectionTitle = stringResource(R.string.shake_gestures_section_sensitivity)
    val sensitivityHint = stringResource(R.string.shake_gestures_sensitivity_hint)
    val feedbackSectionTitle = stringResource(R.string.shake_gestures_section_feedback)
    val advancedFeaturesSectionTitle = stringResource(R.string.shake_gestures_section_advanced_features)

    MiuixHubScaffold(
        title = stringResource(R.string.shake_gestures_title),
        modifier = Modifier.fillMaxSize(),
        listState = listState,
        bottomContentPadding = bottomContentPadding,
    ) {
        settingsLazySmallTitle(key = "shake-service", title = serviceSectionTitle)
        groupedCardItems(
            keyPrefix = "shake-enabled",
            items = listOf(
                settingsCardItem("shake-enabled") {
                    SettingsCardScopeContent {
                        SettingSwitchRow(
                            title = stringResource(R.string.shake_gestures_title),
                            subtitle = null,
                            icon = { label -> Icon(Icons.Default.ScreenRotation, contentDescription = label) },
                            checked = settings.enabled,
                            enabled = true,
                            onCheckedChange = onEnabledChange,
                        )
                    }
                },
            ),
        )
        settingsLazySmallTitle(key = "shake-basic", title = basicSectionTitle)
        groupedCardItems(
            keyPrefix = "shake-basic-actions",
            items = buildList {
                ShakeGestureType.entries.forEach { type ->
                    add(
                        settingsCardScopeItem("shake-basic-action-${type.name}") {
                            ShakeActionRow(
                                icon = shakeGestureIcon(type),
                                iconTint = shakeGestureIconTint(type),
                                title = shakeGestureLabel(type),
                                action = settings.actionFor(type),
                                enabled = true,
                                onClick = { onOpenBasicActionPick(type) },
                            )
                        },
                    )
                }
            },
        )
        settingsLazySmallTitle(key = "shake-work-mode", title = workModeSectionTitle)
        val workModes = settings.workModes
        groupedCardItems(
            keyPrefix = "shake-work-mode",
            items = buildList {
                add(
                    settingsCardScopeItem("shake-work-mode-always") {
                        SettingCheckboxRow(
                            title = stringResource(R.string.shake_work_mode_always),
                            subtitle = stringResource(R.string.shake_work_mode_always_desc),
                            checked = workModes.containsAll(ShakeWorkModes.ALL),
                            enabled = settings.enabled,
                            onCheckedChange = { checked ->
                                onWorkModesChange(if (checked) ShakeWorkModes.ALL else ShakeWorkModes.DEFAULT)
                            },
                        )
                    },
                )
                ShakeWorkMode.entries.forEach { mode ->
                    add(
                        settingsCardScopeItem("shake-work-mode-${workModeItemKey(mode)}") {
                            SettingCheckboxRow(
                                title = stringResource(workModeTitleRes(mode)),
                                subtitle = stringResource(workModeDescRes(mode)),
                                checked = mode in workModes,
                                enabled = settings.enabled,
                                onCheckedChange = { checked ->
                                    onWorkModesChange(if (checked) workModes + mode else workModes - mode)
                                },
                            )
                        },
                    )
                }
            },
        )
        if (ShakeWorkMode.LOCKED_SCREEN_ON in workModes &&
            settings.lockScreenActions.values.none { it != GestureAction.None }
        ) {
            settingsLazyHint(key = "shake-work-mode-lock-screen-hint", text = workModeLockScreenHint)
        }
        if (workModes.includesScreenOff) {
            settingsLazyHint(key = "shake-work-mode-battery-hint", text = workModeBatteryHint)
        }
        settingsLazySmallTitle(key = "shake-action-scope", title = actionScopeSectionTitle)
        groupedCardItems(
            keyPrefix = "shake-action-scope",
            items = buildList {
                add(
                    settingsCardScopeItem("shake-lock-screen") {
                        SettingNavigationRow(
                            title = stringResource(R.string.shake_gestures_lock_screen),
                            subtitle = stringResource(R.string.shake_gestures_lock_screen_desc),
                            icon = { label ->
                                ColoredSettingIcon(
                                    icon = Icons.Default.Lock,
                                    background = Color(0xFF5C6BC0),
                                    contentDescription = label,
                                )
                            },
                            enabled = settings.enabled,
                            onClick = onOpenLockScreenShakeSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("shake-independent-app") {
                        SettingSwitchNavigationRow(
                            title = stringResource(R.string.shake_gestures_independent_app),
                            subtitle = stringResource(R.string.shake_gestures_independent_app_desc),
                            icon = { label ->
                                ColoredSettingIcon(
                                    icon = Icons.Default.Apps,
                                    background = Color(0xFFEF5350),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.independentAppShakeEnabled,
                            enabled = settings.enabled,
                            onCheckedChange = onIndependentAppShakeEnabledChange,
                            onNavigate = onOpenIndependentAppShakeSettings,
                        )
                    },
                )
            },
        )
        settingsLazySmallTitle(key = "shake-sensitivity", title = sensitivitySectionTitle)
        groupedCardItems(
            keyPrefix = "shake-sensitivity",
            items = buildList {
                add(
                    settingsCardScopeItem("shake-global-sensitivity") {
                        SettingsSliderRow(
                            title = stringResource(R.string.shake_gestures_global_sensitivity),
                            value = settings.globalSensitivity,
                            valueRange = ShakeSensitivityScale.UI_MIN..ShakeSensitivityScale.UI_MAX,
                            enabled = settings.enabled && !settings.independentSensitivityEnabled,
                            label = String.format(java.util.Locale.US, "%.0f", settings.globalSensitivity),
                            formatLabel = { String.format(java.util.Locale.US, "%.0f", it) },
                            startLabel = stringResource(R.string.shake_gestures_sensitivity_hard),
                            endLabel = stringResource(R.string.shake_gestures_sensitivity_easy),
                            keyPoints = ShakeSensitivityScale.UI_KEY_POINTS,
                            onValueChange = onGlobalSensitivityChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("shake-independent-sensitivity") {
                        SettingSwitchNavigationRow(
                            title = stringResource(R.string.shake_gestures_independent_sensitivity),
                            subtitle = stringResource(R.string.shake_gestures_independent_sensitivity_desc),
                            icon = { label ->
                                ColoredSettingIcon(
                                    icon = Icons.Default.ScreenRotation,
                                    background = Color(0xFF26A69A),
                                    contentDescription = label,
                                )
                            },
                            checked = settings.independentSensitivityEnabled,
                            enabled = settings.enabled,
                            onCheckedChange = onIndependentSensitivityEnabledChange,
                            onNavigate = onOpenIndependentSensitivity,
                        )
                    },
                )
            },
        )
        settingsLazyHint(key = "shake-sensitivity-hint", text = sensitivityHint)
        settingsLazySmallTitle(key = "shake-feedback", title = feedbackSectionTitle)
        groupedCardItems(
            keyPrefix = "shake-feedback",
            items = buildList {
                add(
                    settingsCardScopeItem("shake-vibration-feedback") {
                        SettingSwitchRow(
                            title = stringResource(R.string.shake_gestures_vibration_feedback),
                            checked = settings.vibrationFeedbackEnabled,
                            enabled = settings.enabled,
                            onCheckedChange = onVibrationFeedbackEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("shake-animation-feedback") {
                        SettingSwitchRow(
                            title = stringResource(R.string.shake_gestures_animation_feedback),
                            checked = settings.animationFeedbackEnabled,
                            enabled = settings.enabled,
                            onCheckedChange = onAnimationFeedbackEnabledChange,
                        )
                    },
                )
                if (settings.animationFeedbackEnabled) {
                    add(
                        settingsCardScopeItem("shake-animation-color") {
                            AnimationStyleColorRow(
                                title = stringResource(R.string.shake_gestures_animation_color),
                                color = settings.animationColorArgb,
                                enabled = settings.enabled,
                                onClick = { showColorPicker = true },
                            )
                        },
                    )
                }
            },
        )
        settingsLazySmallTitle(key = "shake-advanced-features", title = advancedFeaturesSectionTitle)
        groupedCardItems(
            keyPrefix = "shake-advanced-features",
            items = buildList {
                add(
                    settingsCardScopeItem("shake-menu-settings") {
                        SettingNavigationRow(
                            icon = { label ->
                                ColoredSettingIcon(
                                    icon = Icons.Default.Menu,
                                    background = Color(0xFF43A047),
                                    contentDescription = label,
                                )
                            },
                            title = stringResource(R.string.shake_menu_title),
                            subtitle = stringResource(R.string.shake_menu_desc),
                            enabled = settings.enabled,
                            onClick = onOpenShakeMenuSettings,
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (settings.menus.isNotEmpty()) {
                                        Text(
                                            text = settings.menus.size.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = stringResource(R.string.cd_navigate_forward),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("shake-disable-landscape") {
                        SettingSwitchRow(
                            title = stringResource(R.string.shake_gestures_disable_landscape),
                            checked = settings.disableInLandscape,
                            enabled = settings.enabled,
                            onCheckedChange = onDisableInLandscapeChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("shake-app-blacklist") {
                        SettingNavigationRow(
                            icon = { label ->
                                ColoredSettingIcon(
                                    icon = Icons.Default.Block,
                                    background = Color(0xFFFF9800),
                                    contentDescription = label,
                                )
                            },
                            title = stringResource(R.string.shake_gestures_app_blacklist),
                            subtitle = stringResource(R.string.shake_gestures_app_blacklist_desc),
                            enabled = settings.enabled,
                            onClick = onOpenAppBlacklist,
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (settings.blacklistedPackages.isNotEmpty()) {
                                        Text(
                                            text = settings.blacklistedPackages.size.toString(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = stringResource(R.string.cd_navigate_forward),
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
        settingsLazySmallTitle(key = "shake-other-gestures", title = otherGesturesSectionTitle)
        settingsLazyHint(key = "shake-other-gestures-hint", text = otherGesturesHint)
        groupedCardItems(
            keyPrefix = "shake-other-gestures",
            items = listOf(
                settingsCardScopeItem("face-down-settings") {
                    SettingNavigationRow(
                        icon = { label ->
                            ColoredSettingIcon(
                                icon = Icons.Default.PhoneAndroid,
                                background = Color(0xFF78909C),
                                contentDescription = label,
                            )
                        },
                        title = stringResource(R.string.face_down_gestures_title),
                        subtitle = stringResource(R.string.face_down_gestures_subtitle),
                        onClick = onOpenFaceDownSettings,
                    )
                },
                settingsCardScopeItem("back-tap-settings") {
                    SettingNavigationRow(
                        icon = { label ->
                            ColoredSettingIcon(
                                icon = Icons.Default.TouchApp,
                                background = Color(0xFF26A69A),
                                contentDescription = label,
                            )
                        },
                        title = stringResource(R.string.extension_back_tap_title),
                        subtitle = stringResource(R.string.extension_back_tap_subtitle),
                        onClick = onOpenBackTapSettings,
                    )
                },
            ),
        )
    }
}

@Composable
private fun SettingsCardScope.ShakeActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    action: GestureAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            ColoredSettingIcon(icon = icon, background = iconTint, contentDescription = label)
        },
        title = title,
        subtitle = gestureActionSettingSubtitle(action),
        enabled = enabled,
        onClick = onClick,
        trailingContent = {
            GestureActionSettingTrailing(
                action = action,
                enabled = enabled,
                onClick = onClick,
            )
        },
    )
}

@Composable
private fun ColoredSettingIcon(
    icon: ImageVector,
    background: Color,
    contentColor: Color = Color.White,
    contentDescription: String,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = background,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun workModeItemKey(mode: ShakeWorkMode): String = when (mode) {
    ShakeWorkMode.UNLOCKED -> "unlocked"
    ShakeWorkMode.LOCKED_SCREEN_ON -> "locked-screen-on"
    ShakeWorkMode.SCREEN_OFF -> "screen-off"
}

private fun workModeTitleRes(mode: ShakeWorkMode): Int = when (mode) {
    ShakeWorkMode.UNLOCKED -> R.string.shake_work_mode_unlocked
    ShakeWorkMode.LOCKED_SCREEN_ON -> R.string.shake_work_mode_locked_screen_on
    ShakeWorkMode.SCREEN_OFF -> R.string.shake_work_mode_screen_off
}

private fun workModeDescRes(mode: ShakeWorkMode): Int = when (mode) {
    ShakeWorkMode.UNLOCKED -> R.string.shake_work_mode_unlocked_desc
    ShakeWorkMode.LOCKED_SCREEN_ON -> R.string.shake_work_mode_locked_screen_on_desc
    ShakeWorkMode.SCREEN_OFF -> R.string.shake_work_mode_screen_off_desc
}
