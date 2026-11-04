package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.settings.HomeMainSettings
import com.slideindex.app.settings.KeyboardTriggerBehavior
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.ui.animationstyle.GestureAnimationSettingsRows
import com.slideindex.app.ui.miuix.MiuixHubScaffold
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_100
import com.slideindex.app.ui.settings.components.SettingExpandableDropdownRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsCardScopeContent
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(
    settings: HomeMainSettings,
    cornerGestureSettings: CornerGestureSettings,
    privilegeMode: PrivilegeMode,
    privilegedAccessGranted: Boolean,
    rootAccessGranted: Boolean,
    notificationGranted: Boolean,
    shizukuGranted: Boolean,
    accessibilityGranted: Boolean,
    overlayGranted: Boolean,
    batteryOptimizationExempt: Boolean,
    onRequestNotification: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestRootAccess: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestBatteryOptimization: () -> Unit = {},
    onGestureEnabledChange: (Boolean) -> Unit,
    onOpenPrivilegeModeSettings: () -> Unit,
    onOpenAppKeepAliveSettings: () -> Unit,
    onOpenFloatBallSettings: () -> Unit,
    onOpenPickSettings: () -> Unit,
    pickOcrFallbackEnabled: Boolean,
    extensionHubSettings: ExtensionHubSettings,
    onOpenSearchPanel: () -> Unit,
    onOpenFloatingPointer: () -> Unit,
    onOpenFreeWindowSettings: () -> Unit,
    onOpenExcludedAppsSettings: () -> Unit,
    onOpenPreviousAppBlacklist: () -> Unit,
    onOpenInteractionAppearanceSettings: () -> Unit,
    onOpenTriggerCollection: () -> Unit,
    onOpenGestureAppProfiles: () -> Unit = {},
    gestureAppProfileCount: Int = 0,
    onOpenCornerWheel: () -> Unit,
    onOpenMultiTouchGesture: () -> Unit,
    onOpenGestureAngle: () -> Unit,
    onOpenSystemBackGestureWidth: () -> Unit,
    onOpenAnimationStyleSelect: () -> Unit,
    onGestureHintEnabledChange: (Boolean) -> Unit,
    onHideTriggerInLandscapeChange: (Boolean) -> Unit,
    onHideTriggerOnLockScreenChange: (Boolean) -> Unit,
    onHideTriggerOnLauncherChange: (Boolean) -> Unit,
    keyboardTriggerBehavior: KeyboardTriggerBehavior = KeyboardTriggerBehavior.NARROW,
    keyboardTriggerNarrowPercent: Int = 30,
    onKeyboardTriggerBehaviorChange: (KeyboardTriggerBehavior) -> Unit = {},
    onKeyboardTriggerNarrowPercentChange: (Int) -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    bottomNavReselectCount: Int = 0,
) {
    val gestureActive = settings.serviceEnabled && accessibilityGranted && notificationGranted
    val gestureSwitchChecked = gestureActive
    val listState = rememberLazyListState()
    BottomNavReselectScrollEffect(
        reselectCount = bottomNavReselectCount,
        listState = listState,
    )

    val pendingPermissions = buildList {
        if (!accessibilityGranted) {
            add(
                PendingPermissionItem(
                    title = stringResource(R.string.permission_accessibility_title),
                    description = stringResource(R.string.permission_accessibility_desc),
                    grantLabel = stringResource(R.string.permission_accessibility_grant),
                    onGrant = onRequestAccessibility,
                ),
            )
        }
        if (!overlayGranted) {
            add(
                PendingPermissionItem(
                    title = stringResource(R.string.permission_overlay_title),
                    description = stringResource(R.string.onboarding_permission_overlay_short),
                    grantLabel = stringResource(R.string.onboarding_grant_overlay),
                    onGrant = onRequestOverlay,
                ),
            )
        }
        if (!notificationGranted) {
            add(
                PendingPermissionItem(
                    title = stringResource(R.string.permission_notification_title),
                    description = stringResource(R.string.permission_notification_desc),
                    grantLabel = stringResource(R.string.grant_permission),
                    onGrant = onRequestNotification,
                ),
            )
        }
        if (privilegeMode == PrivilegeMode.SHIZUKU && !shizukuGranted) {
            add(
                PendingPermissionItem(
                    title = stringResource(R.string.permission_shizuku_title),
                    description = stringResource(R.string.permission_shizuku_desc),
                    grantLabel = stringResource(R.string.permission_shizuku_grant),
                    onGrant = onRequestShizuku,
                ),
            )
        }
        if (privilegeMode == PrivilegeMode.ROOT && !rootAccessGranted) {
            add(
                PendingPermissionItem(
                    title = stringResource(R.string.permission_root_title),
                    description = stringResource(R.string.permission_root_desc),
                    grantLabel = stringResource(R.string.permission_root_grant),
                    onGrant = onRequestRootAccess,
                ),
            )
        }
        if (!batteryOptimizationExempt) {
            add(
                PendingPermissionItem(
                    title = stringResource(R.string.battery_optimization_title),
                    description = stringResource(R.string.battery_optimization_desc),
                    grantLabel = stringResource(R.string.permission_battery_optimization_grant),
                    onGrant = onRequestBatteryOptimization,
                ),
            )
        }
    }

    val serviceSectionTitle = stringResource(R.string.settings_section_service)
    val featuresSectionTitle = stringResource(R.string.settings_section_features)
    val gesturesSectionTitle = stringResource(R.string.settings_section_gestures)
    val appsSectionTitle = stringResource(R.string.settings_section_apps)
    val feedbackSectionTitle = stringResource(R.string.settings_section_feedback_appearance)
    val hideTriggerItems = hideTriggerSettingsCardItems(
        hideTriggerInLandscape = settings.hideTriggerInLandscape,
        hideTriggerOnLockScreen = settings.hideTriggerOnLockScreen,
        hideTriggerOnLauncher = settings.hideTriggerOnLauncher,
        enabled = gestureActive,
        outlinedLeadingIcons = true,
        onHideInLandscapeChange = onHideTriggerInLandscapeChange,
        onHideOnLockScreenChange = onHideTriggerOnLockScreenChange,
        onHideOnLauncherChange = onHideTriggerOnLauncherChange,
    )

    MiuixHubScaffold(
        title = stringResource(R.string.app_name),
        modifier = Modifier.fillMaxSize(),
        listState = listState,
        bottomContentPadding = bottomContentPadding,
    ) {
        LazySettingsItem(key = "pending-permissions") {
            if (pendingPermissions.isNotEmpty()) {
                PendingPermissionsCardContent(items = pendingPermissions)
            }
        }

        settingsLazySmallTitle(
            key = "service_section",
            title = serviceSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "main_service",
            items = buildList {
                add(
                    settingsCardItem("gesture-enabled") {
                        SettingsCardScopeContent {
                            SettingSwitchRow(
                            title = stringResource(R.string.gesture_switch),
                            subtitle = stringResource(R.string.gesture_switch_hint),
                            icon = { label ->
                                Icon(HomeLeadingIcons.gesture(true), contentDescription = label)
                            },
                            checked = gestureSwitchChecked,
                            enabled = true,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    when {
                                        !accessibilityGranted -> onRequestAccessibility()
                                        !notificationGranted -> onRequestNotification()
                                        else -> onGestureEnabledChange(true)
                                    }
                                } else {
                                    onGestureEnabledChange(false)
                                }
                            },
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("privilege-mode") {
                        SettingsCardScopeContent {
                            PrivilegeModeEntryCard(
                                privilegeMode = privilegeMode,
                                privilegedAccessGranted = privilegedAccessGranted,
                                outlinedLeadingIcons = true,
                                onClick = onOpenPrivilegeModeSettings,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("app-keep-alive") {
                        SettingsCardScopeContent {
                            AppKeepAliveEntryCard(
                                batteryOptimizationExempt = batteryOptimizationExempt,
                                hideFromRecents = settings.hideFromRecents,
                                accessibilityKeepAliveEnabled = settings.accessibilityKeepAliveEnabled,
                                outlinedLeadingIcons = true,
                                onClick = onOpenAppKeepAliveSettings,
                            )
                        }
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "gestures_section", title = gesturesSectionTitle)
        groupedCardItems(
            keyPrefix = "main_gestures",
            items = buildList {
                add(
                    settingsCardItem("trigger-collection") {
                        SettingsCardScopeContent {
                            SettingNavigationRow(
                            icon = { label ->
                                Icon(HomeLeadingIcons.triggerCollection(true), contentDescription = label)
                            },
                            title = stringResource(R.string.trigger_collection_title),
                            subtitle = stringResource(R.string.trigger_collection_desc),
                            onClick = onOpenTriggerCollection,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("gesture-app-profiles") {
                        SettingsCardScopeContent {
                            SettingNavigationRow(
                                icon = { label ->
                                    Icon(HomeLeadingIcons.gestureAppProfiles(true), contentDescription = label)
                                },
                                title = stringResource(R.string.gesture_app_profiles_title),
                                subtitle = if (gestureAppProfileCount > 0) {
                                    stringResource(
                                        R.string.gesture_app_profiles_entry_count,
                                        gestureAppProfileCount,
                                    )
                                } else {
                                    stringResource(R.string.gesture_app_profiles_entry_desc)
                                },
                                onClick = onOpenGestureAppProfiles,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("gesture-angle") {
                        SettingsCardScopeContent {
                            GestureAngleEntryCard(
                                enabled = gestureActive,
                                outlinedLeadingIcons = true,
                                onClick = onOpenGestureAngle,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("gesture-animation") {
                        SettingsCardScopeContent {
                            GestureAnimationSettingsRows(
                                settings = settings,
                                enabled = gestureActive,
                                outlinedLeadingIcons = true,
                                onGestureHintEnabledChange = onGestureHintEnabledChange,
                                onOpenAnimationStyleSelect = onOpenAnimationStyleSelect,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("system-back-gesture-width") {
                        SettingsCardScopeContent {
                            SystemBackGestureWidthEntryCard(
                                outlinedLeadingIcons = true,
                                onClick = onOpenSystemBackGestureWidth,
                            )
                        }
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "features_section", title = featuresSectionTitle)
        groupedCardItems(
            keyPrefix = "main_features",
            items = buildList {
                add(
                    settingsCardItem("float-ball") {
                        SettingsCardScopeContent {
                            FloatBallEntryCard(
                                floatBallEnabled = settings.floatBallEnabled,
                                floatBallSizeDp = settings.floatBallSizeDp,
                                floatBallOpacity = settings.floatBallOpacity,
                                enabled = accessibilityGranted,
                                outlinedLeadingIcons = true,
                                onClick = onOpenFloatBallSettings,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("pick-settings") {
                        SettingsCardScopeContent {
                            PickSettingsEntryCard(
                                accessibilityGranted = accessibilityGranted,
                                ocrFallbackEnabled = pickOcrFallbackEnabled,
                                outlinedLeadingIcons = true,
                                onClick = onOpenPickSettings,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("search-panel") {
                        SettingsCardScopeContent {
                            SearchPanelEntryCard(
                                outlinedLeadingIcons = true,
                                onClick = onOpenSearchPanel,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("floating-pointer") {
                        SettingsCardScopeContent {
                            FloatingPointerEntryCard(
                                settings = extensionHubSettings,
                                enabled = accessibilityGranted,
                                outlinedLeadingIcons = true,
                                onClick = onOpenFloatingPointer,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("corner-wheel") {
                        SettingsCardScopeContent {
                            SettingNavigationRow(
                            icon = { label ->
                                Icon(HomeLeadingIcons.cornerWheel(true), contentDescription = label)
                            },
                            title = stringResource(R.string.corner_wheel_home_title),
                            subtitle = cornerWheelHomeSubtitle(cornerGestureSettings),
                            enabled = accessibilityGranted,
                            onClick = onOpenCornerWheel,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("multi-touch") {
                        SettingsCardScopeContent {
                            SettingNavigationRow(
                                icon = { label ->
                                    Icon(
                                        HomeLeadingIcons.gesture(true),
                                        contentDescription = label,
                                    )
                                },
                                title = "全局触控",
                                subtitle = "屏幕任意位置，单指/多指触控触发操作（需 root 或 Shizuku）",
                                onClick = onOpenMultiTouchGesture,
                            )
                        }
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "apps_section", title = appsSectionTitle)
        groupedCardItems(
            keyPrefix = "main_apps",
            items = buildList {
                add(
                    settingsCardItem("excluded-apps") {
                        SettingsCardScopeContent {
                            ExcludedAppsEntryCard(
                                excludedCount = settings.excludedAppScopes.size,
                                outlinedLeadingIcons = true,
                                onClick = onOpenExcludedAppsSettings,
                            )
                        }
                    },
                )
                add(
                    settingsCardItem("previous-app-blacklist") {
                        SettingsCardScopeContent {
                            PreviousAppBlacklistEntryCard(
                                excludedCount = settings.previousAppExcludedPackages.size,
                                outlinedLeadingIcons = true,
                                onClick = onOpenPreviousAppBlacklist,
                            )
                        }
                    },
                )
                addAll(hideTriggerItems)
                add(
                    settingsCardItem("keyboard-trigger-behavior") {
                        SettingsCardScopeContent {
                            val keyboardBehaviorEntries = KeyboardTriggerBehavior.selectable
                            val keyboardBehaviorIndex = keyboardBehaviorEntries.indexOf(keyboardTriggerBehavior).coerceAtLeast(0)
                            SettingExpandableDropdownRow(
                                title = stringResource(R.string.keyboard_trigger_behavior_title),
                                subtitle = keyboardTriggerBehaviorLabel(keyboardTriggerBehavior),
                                icon = { label ->
                                    Icon(HomeLeadingIcons.keyboardTrigger(true), contentDescription = label)
                                },
                                items = keyboardBehaviorEntries.map { keyboardTriggerBehaviorLabel(it) },
                                selectedIndex = keyboardBehaviorIndex,
                                expanded = keyboardTriggerBehavior == KeyboardTriggerBehavior.NARROW,
                                enabled = gestureActive,
                                onSelectedIndexChange = {
                                    onKeyboardTriggerBehaviorChange(keyboardBehaviorEntries[it])
                                },
                            ) {
                                SettingsSliderRow(
                                    title = stringResource(R.string.keyboard_trigger_narrow_percent_title),
                                    value = keyboardTriggerNarrowPercent.toFloat(),
                                    valueRange = 1f..99f,
                                    enabled = gestureActive,
                                    label = "${keyboardTriggerNarrowPercent}%",
                                    formatLabel = { "${it.roundToInt()}%" },
                                    keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_100,
                                    onValueChange = { onKeyboardTriggerNarrowPercentChange(it.roundToInt()) },
                                )
                            }
                        }
                    },
                )
                add(
                    settingsCardItem("free-window") {
                        SettingsCardScopeContent {
                            FreeWindowEntryCard(
                                outlinedLeadingIcons = true,
                                onClick = onOpenFreeWindowSettings,
                            )
                        }
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "feedback_section", title = feedbackSectionTitle)
        groupedCardItems(
            keyPrefix = "main_feedback",
            items = listOf(
                settingsCardItem("interaction-appearance") {
                    InteractionAppearanceEntryCard(onClick = onOpenInteractionAppearanceSettings)
                },
            ),
        )
    }
}

@Composable
private fun cornerWheelHomeSubtitle(corner: CornerGestureSettings): String {
    if (!corner.enabled) {
        return stringResource(R.string.corner_wheel_home_desc)
    }
    val sides = buildList {
        if (corner.leftEnabled) add(stringResource(R.string.corner_gesture_left_enabled))
        if (corner.rightEnabled) add(stringResource(R.string.corner_gesture_right_enabled))
    }
    return if (sides.isEmpty()) {
        stringResource(R.string.corner_wheel_home_desc)
    } else {
        sides.joinToString(" · ")
    }
}

@Composable
fun SettingsCardScope.PreviousAppBlacklistEntryCard(
    excludedCount: Int,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    val subtitle = if (excludedCount > 0) {
        stringResource(R.string.previous_app_blacklist_entry_count, excludedCount)
    } else {
        stringResource(R.string.previous_app_blacklist_entry_desc)
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(HomeLeadingIcons.previousAppBlacklist(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.previous_app_blacklist_entry_title),
        subtitle = subtitle,
        onClick = onClick,
    )
}
