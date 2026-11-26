package com.slideindex.app.ui

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerSlotSubMenuConfig
import com.slideindex.app.ui.gesturepicker.launchShortcutDisplayLabel
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Delete

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CornerGestureSlotSettingsScreen(
    slotIndex: Int,
    cornerTitle: String,
    currentAction: GestureAction,
    subMenuConfig: CornerSlotSubMenuConfig,
    appSettings: AppSettings,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onPickMainAction: () -> Unit,
    onSubMenuEnabledChange: (Boolean) -> Unit,
    onRemoveSubMenuItem: (Int) -> Unit,
    onAddSubMenuAction: () -> Unit,
    onImportFromApp: () -> Unit,
    canImportFromHost: Boolean
) {
    val slotTitle = stringResource(R.string.corner_gesture_slot_title, slotIndex + 1)
    val mainActionTitle = stringResource(R.string.corner_gesture_slot_main_action)
    val subMenuSectionTitle = stringResource(R.string.corner_gesture_slot_submenu_section)
    val subMenuHint = stringResource(R.string.corner_gesture_slot_submenu_hint)
    val subMenuEnabledTitle = stringResource(R.string.corner_gesture_slot_submenu_enabled)
    val subMenuEnabledDesc = stringResource(R.string.corner_gesture_slot_submenu_enabled_desc)
    val subMenuAddActionTitle = stringResource(R.string.corner_gesture_slot_submenu_add_action)
    val subMenuAddActionDesc = stringResource(R.string.corner_gesture_slot_submenu_add_action_desc)
    val subMenuImportTitle = stringResource(R.string.corner_gesture_slot_submenu_import)
    val subMenuImportDesc = stringResource(R.string.corner_gesture_slot_submenu_import_desc)
    val subMenuRemoveItem = stringResource(R.string.corner_gesture_slot_submenu_remove_item)
    val mainActionLabel = gestureActionLabel(currentAction, appSettings)

    SettingsScreenScaffold(
        title = slotTitle,
        pageHint = stringResource(R.string.corner_gesture_slots_entry_desc),
        onBack = onBack,
        actions = {
            MiuixTopBarIconButton(onClick = onSave) {
                Icon(
                    imageVector = MiuixIcons.Medium.Ok,
                    contentDescription = stringResource(R.string.fv_ring_launcher_done),
                )
            }
        },
    ) {
        settingsLazyHint(
            key = "corner-slot-corner-hint",
            text = cornerTitle
        )
        groupedCardItems(
            keyPrefix = "corner-slot-main-action",
            items = buildList {
                add(
                    settingsCardScopeItem("main-action") {
                        SettingNavigationRow(
                            icon = { label ->
                                GestureSlotActionIcon(
                                    action = currentAction,
                                    settings = appSettings,
                                    contentDescription = label,
                                )
                            },
                            title = mainActionTitle,
                            subtitle = mainActionLabel,
                            onClick = onPickMainAction
                        )
                    }
                )
            }
        )

        settingsLazySmallTitle(
            key = "corner-slot-submenu-section",
            title = subMenuSectionTitle
        )
        settingsLazyTipCard(key = "corner-slot-submenu-hint",
            text = subMenuHint
        )
        groupedCardItems(
            keyPrefix = "corner-slot-submenu-enabled",
            items = buildList {
                add(
                    settingsCardScopeItem("enabled") {
                        SettingSwitchRow(
                            title = subMenuEnabledTitle,
                            subtitle = subMenuEnabledDesc,
                            checked = subMenuConfig.enabled,
                            enabled = true,
                            onCheckedChange = onSubMenuEnabledChange
                        )
                    }
                )
            }
        )

        if (subMenuConfig.enabled) {
            subMenuConfig.items.forEachIndexed { index, action ->
                groupedCardItems(
                    keyPrefix = "corner-slot-submenu-item-$index",
                    items = buildList {
                        add(
                            settingsCardScopeItem("item") {
                                SettingNavigationRow(
                                    icon = { label ->
                                        if (action is GestureAction.LaunchShortcut) {
                                            Md3PickerLaunchShortcutLeading(
                                                action = action,
                                                activityShortcuts = appSettings.activityShortcuts
                                            )
                                        } else {
                                            GestureSlotActionIcon(
                                                action = action,
                                                settings = appSettings,
                                                contentDescription = label,
                                            )
                                        }
                                    },
                                    title = if (action is GestureAction.LaunchShortcut) {
                                        launchShortcutDisplayLabel(action).ifBlank {
                                            gestureActionLabel(action, appSettings)
                                        }
                                    } else {
                                        gestureActionLabel(action, appSettings)
                                    },
                                    subtitle = "",
                                    onClick = {},
                                    trailingContent = {
                                        IconButton(onClick = { onRemoveSubMenuItem(index) }) {
                                            Icon(
                                                imageVector = MiuixIcons.Glass.Delete,
                                                contentDescription = subMenuRemoveItem,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                )
            }
            groupedCardItems(
                keyPrefix = "corner-slot-submenu-add",
                items = buildList {
                    // 反馈 #11：只留一个「添加项」入口 —— 快捷方式本来就在「选择操作类型」页里，
                    // 两个入口是重复的。
                    add(
                        settingsCardScopeItem("add-action") {
                            SettingNavigationRow(
                                icon = { label -> SymbolsIcon("Add", contentDescription = label) },
                                title = subMenuAddActionTitle,
                                subtitle = subMenuAddActionDesc,
                                onClick = onAddSubMenuAction
                            )
                        }
                    )
                    if (canImportFromHost) {
                        add(
                            settingsCardScopeItem("import") {
                                SettingNavigationRow(
                                    icon = { label -> SymbolsIcon("FileDownload", contentDescription = label) },
                                    title = subMenuImportTitle,
                                    subtitle = subMenuImportDesc,
                                    onClick = onImportFromApp
                                )
                            }
                        )
                    }
                }
            )
        }
    }
}

internal fun taskSwitcherItemToLaunchShortcut(
    shortcut: TaskSwitcherMenuItem,
    packageName: String
): GestureAction.LaunchShortcut {
    val serviceId = shortcut.shortcutId?.takeIf { shortcut.isServiceShortcut && it.isNotBlank() }
    if (serviceId != null) {
        return GestureAction.LaunchShortcut.dynamic(packageName, serviceId, shortcut.label)
    }
    val uris = shortcut.intentUris
    if (!uris.isNullOrEmpty()) {
        return if (uris.size == 1) {
            GestureAction.LaunchShortcut.intent(uris[0], shortcut.label)
        } else {
            GestureAction.LaunchShortcut.intents(uris, shortcut.label)
        }
    }
    val intent = shortcut.shortcutIntent
    if (intent != null) {
        return GestureAction.LaunchShortcut.intent(
            intent.toUri(android.content.Intent.URI_INTENT_SCHEME),
            shortcut.label
        )
    }
    val component = shortcut.targetComponent?.takeIf { it.isNotBlank() }
    if (component != null) {
        return GestureAction.LaunchShortcut.component(component, shortcut.label)
    }
    val shortcutId = shortcut.shortcutId?.takeIf { it.isNotBlank() } ?: shortcut.label
    return GestureAction.LaunchShortcut.dynamic(packageName, shortcutId, shortcut.label)
}
