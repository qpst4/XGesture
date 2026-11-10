@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.settings.macro.ui.MacroEntryCard
import com.slideindex.app.ui.miuix.MiuixHubScaffold
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingGrid2Row
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle

@Composable
fun ExtensionHubScreen(
    settings: ExtensionHubSettings,
    gestureActive: Boolean,
    stashEntryCount: Int,
    bottomContentPadding: Dp = 0.dp,
    bottomNavReselectCount: Int = 0,
    onOpenActionCenter: () -> Unit,
    onOpenLayoutSettings: () -> Unit,
    onOpenQuickLauncher: () -> Unit,
    onOpenQuickWheel: () -> Unit,
    onOpenHoneycombLauncher: () -> Unit,
    onOpenRingLauncher: () -> Unit,
    onOpenHolographicLauncher: () -> Unit,
    onOpenActivityShortcuts: () -> Unit,
    onOpenExternalInvocations: () -> Unit,
    onOpenShellCommands: () -> Unit,
    onOpenWidgetPanel: () -> Unit,
    onOpenStashClipboard: () -> Unit,
    onOpenDragDropFlow: () -> Unit = {},
    onOpenShareSheet: () -> Unit = {},
    onOpenIconLibrary: () -> Unit = {},
    onOpenIconTheme: () -> Unit = {},
    onOpenFreezer: () -> Unit = {},
    onOpenMacro: () -> Unit = {},
    onOpenPageScroll: () -> Unit = {},
    onOpenSettingsBackup: () -> Unit,
    onOpenNativeEnginePacks: () -> Unit,
    onOpenDiagnosticLogs: () -> Unit,
    onOpenShortcutTest: () -> Unit = {},
    onOpenAbout: () -> Unit,
) {
    val listState = rememberLazyListState()
    BottomNavReselectScrollEffect(
        reselectCount = bottomNavReselectCount,
        listState = listState
    )

    val actionsTitle = stringResource(R.string.extension_section_actions)
    val toolsTitle = stringResource(R.string.extension_section_tools)
    val shortcutsTitle = stringResource(R.string.extension_section_shortcuts)
    val maintenanceTitle = stringResource(R.string.extension_section_maintenance)

    MiuixHubScaffold(
        title = stringResource(R.string.main_nav_extension),
        modifier = Modifier.fillMaxSize(),
        listState = listState,
        bottomContentPadding = bottomContentPadding
    ) {
        settingsLazySmallTitle(
            key = "actions_section",
            title = actionsTitle
        )
        groupedCardItems(
            keyPrefix = "extension_actions",
            items = buildList {
                add(
                    settingsCardScopeItem("action-center") {
                        SettingNavigationRow(
                            icon = { label -> Icon(ThinActionIcons.Gesture, contentDescription = label) },
                            title = stringResource(R.string.extension_action_center_title),
                            subtitle = stringResource(R.string.extension_action_center_subtitle),
                            onClick = onOpenActionCenter,
                        )
                    }
                )
                add(
                    settingsCardScopeItem("activity-shortcut") {
                        ActivityShortcutEntryCard(
                            shortcutCount = settings.activityShortcutCount,
                            outlinedLeadingIcons = true,
                            onClick = onOpenActivityShortcuts,
                        )
                    }
                )
            }
        )

        settingsLazySmallTitle(
            key = "tools_section",
            title = toolsTitle
        )
        groupedCardItems(
            keyPrefix = "extension_tools",
            items = buildList {
                add(
                    settingsCardScopeItem("drag-drop-flow") {
                        val flowSub = if (settings.dragDropFlowEnabled) {
                            when (settings.dragDropFlowPositionId) {
                                1 -> stringResource(R.string.drag_drop_flow_hub_status_left)
                                2 -> stringResource(R.string.drag_drop_flow_hub_status_right)
                                3 -> stringResource(R.string.drag_drop_flow_hub_status_ring)
                                else -> stringResource(R.string.drag_drop_flow_hub_status_bottom)
                            }
                        } else {
                            stringResource(R.string.drag_drop_flow_hub_status_off)
                        }
                        SettingNavigationRow(
                            icon = { label -> Icon(ThinActionIcons.Drag, contentDescription = label) },
                            title = stringResource(R.string.drag_drop_flow_title),
                            subtitle = flowSub,
                            onClick = onOpenDragDropFlow,
                        )
                    }
                )
                add(
                    settingsCardScopeItem("stash-clipboard") {
                        StashClipboardEntryCard(
                            settings = settings,
                            stashEntryCount = stashEntryCount,
                            outlinedLeadingIcons = true,
                            onClick = onOpenStashClipboard
                        )
                    }
                )
                add(
                    settingsCardScopeItem("freezer") {
                        SettingNavigationRow(
                            icon = { label -> SymbolsIcon("AcUnit", contentDescription = label) },
                            title = stringResource(R.string.extension_freezer_title),
                            subtitle = stringResource(R.string.extension_freezer_subtitle),
                            onClick = onOpenFreezer
                        )
                    }
                )
                add(
                    settingsCardScopeItem("macro") {
                        MacroEntryCard(onClick = onOpenMacro)
                    }
                )
                add(
                    settingsCardScopeItem("page-scroll") {
                        PageScrollEntryCard(onClick = onOpenPageScroll)
                    }
                )
                add(
                    settingsCardScopeItem("shell-panel") {
                        SettingNavigationRow(
                            icon = { label -> Icon(HubLeadingIcons.shellCommand(true), contentDescription = label) },
                            title = stringResource(R.string.shell_panel_entry_title),
                            subtitle = stringResource(R.string.shell_panel_entry_desc),
                            onClick = onOpenShellCommands,
                        )
                    }
                )
                add(
                    settingsCardScopeItem("layout-settings") {
                        SettingNavigationRow(
                            icon = { label -> Icon(HubLeadingIcons.layoutSettings(true), contentDescription = label) },
                            title = stringResource(R.string.extension_action_index_layout_title),
                            subtitle = stringResource(R.string.layout_settings_entry_desc),
                            onClick = onOpenLayoutSettings,
                        )
                    }
                )
            }
        )

        settingsLazySmallTitle(
            key = "shortcuts_section",
            title = shortcutsTitle
        )
        groupedCardItems(
            keyPrefix = "extension_shortcuts",
            items = buildList {
                add(
                    settingsCardScopeItem("share-sheet") {
                        SettingNavigationRow(
                            icon = { label -> SymbolsIcon("Share", contentDescription = label) },
                            title = stringResource(R.string.share_sheet_settings_title),
                            subtitle = stringResource(R.string.share_sheet_hub_subtitle),
                            onClick = onOpenShareSheet,
                        )
                    }
                )
                add(
                    settingsCardScopeItem("shortcut-test") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(
                                    ThinActionIcons.Shortcut,
                                    contentDescription = label,
                                )
                            },
                            title = stringResource(R.string.extension_shortcut_test_title),
                            subtitle = stringResource(R.string.extension_shortcut_test_subtitle),
                            onClick = onOpenShortcutTest,
                        )
                    }
                )
            }
        )

        settingsLazySmallTitle(
            key = "maintenance_section",
            title = maintenanceTitle
        )
        groupedCardItems(
            keyPrefix = "extension_maintenance",
            items = buildList {
                add(
                    settingsCardScopeItem("icon-theme") {
                        SettingNavigationRow(
                            icon = { label -> Icon(ThinActionIcons.IconSet, contentDescription = label) },
                            title = stringResource(R.string.extension_icon_theme_title),
                            subtitle = stringResource(R.string.extension_icon_theme_subtitle),
                            onClick = onOpenIconTheme
                        )
                    }
                )
                add(
                    settingsCardScopeItem("icon-library") {
                        SettingNavigationRow(
                            icon = { label -> Icon(ThinActionIcons.Favicon, contentDescription = label) },
                            title = stringResource(R.string.extension_icon_library_title),
                            subtitle = stringResource(R.string.extension_icon_library_subtitle),
                            onClick = onOpenIconLibrary
                        )
                    }
                )
                add(
                    settingsCardScopeItem("settings-backup") {
                        SettingsBackupEntryCard(
                            outlinedLeadingIcons = true,
                            onClick = onOpenSettingsBackup
                        )
                    }
                )
                add(
                    settingsCardScopeItem("native-engine-packs") {
                        NativeEnginePacksEntryCard(
                            outlinedLeadingIcons = true,
                            onClick = onOpenNativeEnginePacks
                        )
                    }
                )
                add(
                    settingsCardScopeItem("diagnostic-logs") {
                        DiagnosticLogsEntryCard(
                            outlinedLeadingIcons = true,
                            onClick = onOpenDiagnosticLogs
                        )
                    }
                )
                add(
                    settingsCardScopeItem("about") {
                        AboutEntryCard(
                            outlinedLeadingIcons = true,
                            onClick = onOpenAbout
                        )
                    }
                )
            }
        )
    }
}

@Composable
fun SettingsCardScope.NativeEnginePacksEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    SettingNavigationRow(
        icon = { label -> SymbolsIcon("Memory", contentDescription = label) },
        title = stringResource(R.string.extension_native_engine_packs_entry_title),
        subtitle = stringResource(R.string.extension_native_engine_packs_entry_desc),
        onClick = onClick
    )
}

@Composable
fun SettingsCardScope.DiagnosticLogsEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    SettingNavigationRow(
        icon = { label -> SymbolsIcon("BugReport", contentDescription = label) },
        title = stringResource(R.string.diagnostic_report_entry_title),
        subtitle = stringResource(R.string.diagnostic_report_entry_subtitle),
        onClick = onClick
    )
}

@Composable
fun SettingsCardScope.AboutEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    SettingNavigationRow(
        icon = { label -> Icon(HubLeadingIcons.about(outlinedLeadingIcons), contentDescription = label) },
        title = stringResource(R.string.about_section_title),
        subtitle = stringResource(R.string.extension_about_subtitle),
        onClick = onClick
    )
}

@Composable
fun SettingsCardScope.PrivacyPolicyEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> SymbolsIcon("Article", contentDescription = label, mirrored = true) },
        title = stringResource(R.string.privacy_policy_entry_title),
        subtitle = stringResource(R.string.privacy_policy_entry_desc),
        onClick = onClick
    )
}

@Composable
fun SettingsCardScope.OpenSourceLicenseEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> SymbolsIcon("Gavel", contentDescription = label) },
        title = stringResource(R.string.about_open_source_license_title),
        subtitle = stringResource(R.string.about_open_source_license_desc),
        onClick = onClick
    )
}

@Composable
fun SettingsCardScope.ThirdPartyNoticesEntryCard(onClick: () -> Unit) {
    SettingNavigationRow(
        icon = { label -> SymbolsIcon("Favorite", contentDescription = label) },
        title = stringResource(R.string.about_third_party_notices_title),
        subtitle = stringResource(R.string.about_third_party_notices_subtitle),
        onClick = onClick
    )
}

@Composable
fun SettingsCardScope.SettingsBackupEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    SettingNavigationRow(
        icon = { label -> Icon(HubLeadingIcons.settingsBackup(outlinedLeadingIcons), contentDescription = label) },
        title = stringResource(R.string.settings_backup_entry_title),
        subtitle = stringResource(R.string.settings_backup_entry_desc),
        onClick = onClick
    )
}

@Composable
fun SettingsCardScope.QuickWheelEntryCard(
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(
                gestureActionTypeOutlinedIcon(GestureActionType.QUICK_WHEEL),
                contentDescription = label,
            )
        },
        title = stringResource(R.string.extension_quick_wheel_entry_title),
        subtitle = stringResource(R.string.extension_quick_wheel_entry_desc),
        onClick = onClick
    )
}

@Composable
fun SettingsCardScope.PageScrollEntryCard(
    onClick: () -> Unit
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(
                gestureActionTypeOutlinedIcon(GestureActionType.PAGE_SCROLL),
                contentDescription = label,
            )
        },
        title = stringResource(R.string.page_scroll_prefs_title),
        subtitle = stringResource(R.string.page_scroll_action_description),
        onClick = onClick
    )
}
