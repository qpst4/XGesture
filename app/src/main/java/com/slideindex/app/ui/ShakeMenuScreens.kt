package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.shake.ShakeMenu
import com.slideindex.app.shake.ShakeMenuLimits
import com.slideindex.app.shake.ShakeMenuRuntime
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToLong

/** 菜单行图标用的固定绿色：与「晃动菜单」动作图标保持一致，便于在列表里认出。 */
private val ShakeMenuIconBackground = Color(0xFF43A047)

private const val ShakeMenuNameMaxLength = 24

/**
 * 「晃动菜单」管理页：菜单级参数 + 已建菜单列表。
 *
 * 这里只管菜单本身；把菜单挂到某个晃动方向是在晃动首页（基础动作 → 选「晃动菜单」）。
 */
@Composable
fun ShakeMenuSettingsScreen(
    settings: ShakeGestureSettings,
    onBack: () -> Unit,
    onAddMenu: () -> Unit,
    onOpenMenu: (String) -> Unit,
    onRemoveMenu: (String) -> Unit,
    onGuardMsChange: (Long) -> Unit,
    onDisplayDelayMsChange: (Long) -> Unit,
    onShowOverlayChange: (Boolean) -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<ShakeMenu?>(null) }
    val menus = settings.menus
    // LazyListScope 的 content 不是 @Composable，字符串必须在进入前取好。
    val settingsSectionTitle = stringResource(R.string.shake_menu_section_settings)
    val addedSectionTitle = stringResource(R.string.shake_menu_added_section)
    val emptyDesc = stringResource(R.string.shake_menu_empty_desc)

    pendingRemoval?.let { menu ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.shake_menu_delete)) },
            text = { Text(stringResource(R.string.shake_menu_delete_confirm, menu.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoval = null
                        onRemoveMenu(menu.id)
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.shake_menu_title),
        pageHint = stringResource(R.string.shake_menu_desc),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "shake-menu-settings",
            title = settingsSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "shake-menu-settings",
            items = listOf(
                settingsCardScopeItem("shake-menu-guard") {
                    SettingsSliderRow(
                        title = stringResource(R.string.shake_menu_guard_duration),
                        value = settings.menuGuardMs.toFloat(),
                        valueRange = ShakeMenuRuntime.GUARD_MS_MIN.toFloat()..
                            ShakeMenuRuntime.GUARD_MS_MAX.toFloat(),
                        steps = 8,
                        enabled = true,
                        label = stringResource(
                            R.string.shake_menu_guard_value,
                            settings.menuGuardMs,
                        ),
                        summary = stringResource(R.string.shake_menu_guard_duration_desc),
                        onValueChange = { onGuardMsChange(it.roundToLong()) },
                    )
                },
                settingsCardScopeItem("shake-menu-display-delay") {
                    SettingsSliderRow(
                        title = stringResource(R.string.shake_menu_display_delay),
                        value = settings.menuDisplayDelayMs.toFloat(),
                        valueRange = 0f..ShakeMenuRuntime.DISPLAY_DELAY_MS_MAX.toFloat(),
                        steps = 29,
                        enabled = true,
                        label = if (settings.menuDisplayDelayMs <= 0L) {
                            stringResource(R.string.shake_menu_display_delay_immediate)
                        } else {
                            stringResource(
                                R.string.shake_menu_guard_value,
                                settings.menuDisplayDelayMs,
                            )
                        },
                        summary = stringResource(R.string.shake_menu_display_delay_desc),
                        onValueChange = { onDisplayDelayMsChange(it.roundToLong()) },
                    )
                },
                settingsCardScopeItem("shake-menu-show-overlay") {
                    SettingSwitchRow(
                        title = stringResource(R.string.shake_menu_show_overlay),
                        subtitle = stringResource(R.string.shake_menu_show_overlay_desc),
                        checked = settings.showMenuOverlay,
                        enabled = true,
                        onCheckedChange = onShowOverlayChange,
                    )
                },
            ),
        )

        settingsLazySmallTitle(
            key = "shake-menu-added",
            title = addedSectionTitle,
        )
        if (menus.isEmpty()) {
            settingsLazyHint(
                key = "shake-menu-empty",
                text = emptyDesc,
            )
        } else {
            groupedCardItems(
                keyPrefix = "shake-menu-list",
                items = menus.mapIndexed { index, menu ->
                    settingsCardScopeItem("shake-menu-${menu.id}") {
                        SettingNavigationRow(
                            icon = { label ->
                                ShakeGestureColoredIcon(
                                    icon = Icons.Default.Menu,
                                    background = ShakeMenuIconBackground,
                                    contentDescription = label,
                                )
                            },
                            title = ShakeMenuLimits.displayName(menu, index),
                            subtitle = stringResource(
                                R.string.shake_menu_direction_count,
                                menu.configuredDirections.size,
                            ),
                            onClick = { onOpenMenu(menu.id) },
                            trailingContent = {
                                IconButton(onClick = { pendingRemoval = menu }) {
                                    Icon(
                                        imageVector = Icons.Default.RemoveCircleOutline,
                                        contentDescription = stringResource(R.string.shake_menu_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }
        groupedCardItems(
            keyPrefix = "shake-menu-add",
            items = listOf(
                settingsCardScopeItem("shake-menu-add") {
                    SettingNavigationRow(
                        icon = { label ->
                            ShakeGestureColoredIcon(
                                icon = Icons.Default.Add,
                                background = MaterialTheme.colorScheme.primary,
                                contentDescription = label,
                            )
                        },
                        title = stringResource(R.string.shake_menu_add),
                        subtitle = stringResource(R.string.shake_menu_add_desc),
                        enabled = menus.size < ShakeMenuLimits.MAX_MENUS,
                        onClick = onAddMenu,
                    )
                },
            ),
        )
    }
}

/**
 * 单个菜单的编辑页：名称 + 6 个方向的第二套动作。
 *
 * 与晃动首页的「基础晃动动作」同构，只是数据落在菜单上；进入动作选择时用
 * [com.slideindex.app.ui.navigation.ShakeActionPickTarget.MENU] 指回本页。
 */
@Composable
fun ShakeMenuEditScreen(
    menu: ShakeMenu?,
    appSettings: AppSettings? = null,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onOpenActionPick: (ShakeGestureType) -> Unit,
    menuIndex: Int = 0,
) {
    if (menu == null) {
        // 刚添加菜单时设置流还没推下来，这里先渲染空页而不是直接返回，
        // 避免「新建菜单 → 立刻进编辑页」被当成无效菜单弹回去。
        SettingsScreenScaffold(
            title = stringResource(R.string.shake_menu_edit_title),
            onBack = onBack,
        ) {}
        return
    }
    var nameDialogVisible by remember { mutableStateOf(false) }
    // LazyListScope 的 content 不是 @Composable，字符串必须在进入前取好。
    val directionsSectionTitle = stringResource(R.string.shake_menu_directions_section)

    if (nameDialogVisible) {
        ShakeMenuNameDialog(
            initial = menu.name,
            onDismiss = { nameDialogVisible = false },
            onConfirm = { name ->
                nameDialogVisible = false
                onRename(name)
            },
        )
    }

    SettingsScreenScaffold(
        title = ShakeMenuLimits.displayName(menu, menuIndex),
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "shake-menu-name",
            items = listOf(
                settingsCardScopeItem("shake-menu-name") {
                    SettingNavigationRow(
                        icon = { label ->
                            ShakeGestureColoredIcon(
                                icon = Icons.Default.Menu,
                                background = ShakeMenuIconBackground,
                                contentDescription = label,
                            )
                        },
                        title = stringResource(R.string.shake_menu_name),
                        subtitle = ShakeMenuLimits.displayName(menu, menuIndex),
                        onClick = { nameDialogVisible = true },
                    )
                },
            ),
        )
        settingsLazySmallTitle(
            key = "shake-menu-directions",
            title = directionsSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "shake-menu-directions",
            items = ShakeGestureType.entries.map { type ->
                settingsCardScopeItem("shake-menu-direction-${type.name}") {
                    ShakeGestureActionRow(
                        type = type,
                        action = menu.actionFor(type),
                        activityShortcuts = appSettings?.activityShortcuts.orEmpty(),
                        shellCommands = appSettings?.shellCommands.orEmpty(),
                        onClick = { onOpenActionPick(type) },
                    )
                }
            },
        )
    }
}

@Composable
private fun ShakeMenuNameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shake_menu_name)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(ShakeMenuNameMaxLength) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.shake_menu_name_hint)) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${text.length}/$ShakeMenuNameMaxLength",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
