package com.slideindex.app.ui

import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerEdgeActionSlot
import com.slideindex.app.settings.FloatingPointerEdgeActionsCodec
import com.slideindex.app.settings.FloatingPointerEdgeSide
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.MiuixIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerEdgeSideSettingsScreen(
    side: FloatingPointerEdgeSide,
    settings: AppSettings,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onOpenActionPick: (Int) -> Unit,
    onOpenShellCommand: (Int, String) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (Int) -> Unit
) {
    val bar = settings.floatingPointerEdgeActionsConfig.bar(side)
    val slots = bar.layoutSlots()
    val zonesSectionTitle = stringResource(
        R.string.floating_pointer_edge_section_zones_count,
        slots.size
    )
    val canRemoveZone = slots.size > 1

    SettingsScreenScaffold(
        title = edgeSideTitle(side),
        onBack = onBack
    ) {
        groupedCardItems(
            keyPrefix = "edge-enabled-${side.name}",
            items = buildList {
                add(
                    settingsCardScopeItem("enabled") {
                        SettingSwitchRow(
                            title = edgeSideTitle(side),
                            subtitle = edgeSideSummary(slots.size, bar.enabled),
                            icon = { label -> Icon(edgeSideIcon(side), contentDescription = label) },
                            checked = bar.enabled,
                            enabled = true,
                            onCheckedChange = onEnabledChange
                        )
                    }
                )
            }
        )

        settingsLazySmallTitle(
            key = "edge-zones-section-${side.name}",
            title = zonesSectionTitle
        )
        slots.forEachIndexed { index, slot ->
            groupedCardItems(
                keyPrefix = "edge-zone-${side.name}-$index",
                items = edgeZoneCardItems(
                    index = index,
                    slot = slot,
                    canRemove = canRemoveZone,
                    onPickAction = { onOpenActionPick(index) },
                    onOpenShellCommand = { onOpenShellCommand(index, it) },
                    onRemove = { onRemoveSlot(index) }
                )
            )
        }
        if (slots.size < FloatingPointerEdgeActionsCodec.MAX_SLOTS_PER_EDGE) {
            groupedCardItems(
                keyPrefix = "edge-add-zone-${side.name}",
                items = buildList {
                    add(
                        settingsCardScopeItem("add") {
                            SettingNavigationRow(
                                icon = { label -> SymbolsIcon("Add", contentDescription = label) },
                                title = stringResource(R.string.floating_pointer_edge_add_zone),
                                subtitle = stringResource(R.string.floating_pointer_edge_add_zone_desc),
                                onClick = onAddSlot
                            )
                        }
                    )
                }
            )
        }
    }
}

private fun edgeZoneCardItems(
    index: Int,
    slot: FloatingPointerEdgeActionSlot,
    canRemove: Boolean,
    onPickAction: () -> Unit,
    onOpenShellCommand: (String) -> Unit,
    onRemove: () -> Unit
) = buildList {
    add(
        settingsCardScopeItem("action") {
            SettingNavigationRow(
                icon = { label -> Icon(gestureActionIcon(slot.action), contentDescription = label) },
                title = stringResource(R.string.floating_pointer_edge_zone_title, index + 1),
                subtitle = gestureActionLabel(slot.action),
                onClick = onPickAction,
                trailingContent = if (canRemove) {
                    {
                        IconButton(onClick = onRemove) {
                            Icon(
                                imageVector = MiuixIcons.Medium.Delete,
                                contentDescription = stringResource(R.string.floating_pointer_edge_remove_zone),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    null
                }
            )
        }
    )
    if (slot.action is GestureAction.ExecuteShellCommand) {
        val shellAction = slot.action as GestureAction.ExecuteShellCommand
        add(
            settingsCardScopeItem("shell") {
                SettingNavigationRow(
                    icon = { label -> Icon(gestureActionIcon(shellAction), contentDescription = label) },
                    title = gestureExecuteShellCommandPreview(shellAction.command),
                    subtitle = stringResource(R.string.gesture_shell_command_config_title),
                    onClick = { onOpenShellCommand(shellAction.command) }
                )
            }
        )
    }
}
