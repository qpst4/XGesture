package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.slideindex.app.ui.Md3PickerAppEntryLeading
import com.slideindex.app.ui.picker.appPickerGridItems
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.AppPackageEntry
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.gesturepicker.gestureActionDescription
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.ui.picker.FilteredShortcutCatalog
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.ui.picker.GestureActionCatalogScope
import com.slideindex.app.ui.picker.activityShortcutPickerToggleSection
import com.slideindex.app.ui.picker.shortcutFolderCardsSection
import com.slideindex.app.ui.picker.systemShortcutCatalogItems
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import com.slideindex.app.util.PinyinHelper
import com.slideindex.app.util.ShortcutScanProgress

fun LazyListScope.quickLauncherAddPickerActionItems(
    filtered: List<GestureAction>,
    configuredActionKeys: Set<String>,
    onToggleItem: (QuickLauncherItem, Boolean) -> Unit,
    onOpenExecuteShellCommand: () -> Unit,
    onOpenOpenLink: () -> Unit = {},
    onOpenSimulateKeyEvent: () -> Unit = {},
    /**
     * 「手势宏」必须带 macroId 才有意义：这里把"挑一条宏"的动作交回给调用方（由它挂选择框），
     * 拿到结果后再落库。缺省空实现时保持旧行为（不推荐，等于存了个空动作）。
     */
    onRequestRunMacro: ((GestureAction) -> Unit) -> Unit = {},
    singleSelect: Boolean = false,
    pinNoneAtTop: Boolean = false,
    noneSelected: Boolean = false,
    onSelectNone: () -> Unit = {},
    searchQuery: String = "",
) {
    if (pinNoneAtTop) {
        item(key = "ql-action-none") {
            val noneLabel = stringResource(R.string.gesture_action_none)
            val showNone = searchQuery.isBlank() || noneLabel.contains(searchQuery, ignoreCase = true)
            if (showNone) {
                com.slideindex.app.ui.Md3PickerListRow(
                    segmentIndex = 0,
                    segmentCount = 1,
                    title = noneLabel,
                    subtitle = null,
                    selected = noneSelected,
                    onClick = onSelectNone,
                    leadingContent = {
                        com.slideindex.app.ui.Md3PickerIconLeading(
                            icon = gestureActionIcon(GestureAction.None, outlined = true),
                            selected = noneSelected,
                        )
                    },
                    trailingMode = com.slideindex.app.ui.PickerTrailingMode.Radio,
                )
            }
        }
    }

    if (filtered.isEmpty()) {
        if (!pinNoneAtTop || !searchQuery.isBlank()) {
            item(key = "actions_empty") {
                Text(
                    text = stringResource(R.string.search_no_actions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                )
            }
        }
        return
    }

    val sections = GestureActionCatalog.groupIntoSections(filtered)
    sections.forEach { section ->
        item(key = "ql-action-cat-${section.category.name}") {
            com.slideindex.app.ui.Md3PickerSectionHeader(stringResource(section.category.titleRes))
        }
        val rows = section.actions.chunked(PICKER_GRID_COLUMNS)
        items(rows.size, key = { "ql-action-row-${section.category.name}-$it" }) { rowIndex ->
            val rowActions = rows[rowIndex]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowActions.forEach { action ->
                    val context = LocalContext.current
                    val added = QuickLauncherItemCodec.actionKey(action) in configuredActionKeys
                    val cellLabel = if (action.type == GestureActionType.RUN_MACRO) {
                        stringResource(R.string.macro_action_run_macro)
                    } else {
                        // 不能在这里用可组合版 gestureActionLabel()：这块添加区被浮层面板复用
                        //（圆环槽位编辑），浮层合成里没有 LocalAppDependencies，调用会直接抛异常。
                        gestureActionLabelText(context, action)
                    }
                    PickerGridCell(
                        label = cellLabel,
                        selected = !singleSelect && added,
                        onClick = {
                            when (action.type) {
                                GestureActionType.OPEN_LINK -> onOpenOpenLink()
                                GestureActionType.EXECUTE_SHELL_COMMAND -> {
                                    requestPermissionForAdjustAction(context, action)
                                    onOpenExecuteShellCommand()
                                }
                                GestureActionType.SIMULATE_KEY_EVENT -> {
                                    requestPermissionForAdjustAction(context, action)
                                    onOpenSimulateKeyEvent()
                                }
                                GestureActionType.RUN_MACRO -> onRequestRunMacro { picked ->
                                    if (!added) requestPermissionForAdjustAction(context, picked)
                                    onToggleItem(
                                        QuickLauncherItem.action(
                                            picked,
                                            gestureActionLabelText(context, picked),
                                        ),
                                        false,
                                    )
                                }
                                else -> {
                                    if (!added) requestPermissionForAdjustAction(context, action)
                                    onToggleItem(
                                        QuickLauncherItem.action(
                                            action,
                                            gestureActionLabelText(context, action),
                                        ),
                                        if (singleSelect) false else added,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        com.slideindex.app.ui.ActionIconSlot(
                            action = action,
                            sizeDp = 27,
                        )
                    }
                }
                repeat(PICKER_GRID_COLUMNS - rowActions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

fun LazyListScope.quickLauncherAddPickerAppItems(
    filtered: List<AppInfo>,
    configuredAppPackages: Set<String>,
    onToggle: (AppInfo, Boolean) -> Unit,
    singleSelect: Boolean = false,
) {
    appPickerGridItems(
        apps = filtered,
        selectedKeys = if (singleSelect) emptySet() else configuredAppPackages,
        onAppClick = { app ->
            val added = com.slideindex.app.launcher.AppTarget.encode(app.packageName, app.cloneUserId) in
                configuredAppPackages
            onToggle(app, if (singleSelect) false else added)
        },
    )
}
fun LazyListScope.quickLauncherAddPickerShortcutItems(
    searchQuery: String,
    activityShortcuts: List<ActivityShortcut>,
    configuredShortcutKeys: Set<String>,
    filtered: FilteredShortcutCatalog,
    appsByPackage: Map<String, AppInfo>,
    loading: Boolean,
    scanProgress: ShortcutScanProgress?,
    onCreateHostClick: (AppShortcutLoader.CreateShortcutHost) -> Unit,
    onToggle: (AppInfo, TaskSwitcherMenuItem, Boolean) -> Unit,
    onToggleActivityShortcut: (QuickLauncherItem, Boolean) -> Unit,
    onBrowseActivityShortcut: () -> Unit,
    onOpenMyShortcuts: () -> Unit = {},
    onOpenPresetShortcuts: () -> Unit = {},
    singleSelect: Boolean = false,
) {
    if (searchQuery.isBlank()) {
        shortcutFolderCardsSection(
            activityShortcutsCount = activityShortcuts.size,
            onOpenMyShortcuts = onOpenMyShortcuts,
            onOpenPresetShortcuts = onOpenPresetShortcuts,
        )
    } else if (activityShortcuts.isNotEmpty()) {
        activityShortcutPickerToggleSection(
            activityShortcuts = activityShortcuts,
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = onToggleActivityShortcut,
            onBrowse = onBrowseActivityShortcut,
            searchQuery = searchQuery,
            singleSelect = singleSelect,
        )
    }
    systemShortcutCatalogItems(
        filtered = filtered,
        appsByPackage = appsByPackage,
        loading = loading,
        scanProgress = scanProgress,
        searchQuery = searchQuery,
        onCreateHostClick = onCreateHostClick,
        shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
            val item = shortcut.toQuickLauncherItem(group.app.packageName)
            val added = QuickLauncherItemCodec.shortcutItemKey(item) in configuredShortcutKeys
            ShortcutCatalogRow(
                shortcut = shortcut,
                segmentIndex = segmentIndex,
                segmentCount = segmentCount,
                added = added,
                onToggle = { onToggle(group.app, shortcut, if (singleSelect) false else added) },
            )
        },
    )
}

@Composable
fun rememberQuickLauncherFilteredActions(searchQuery: String): List<GestureAction> {
    val context = LocalContext.current
    val actionOptions = remember {
        GestureActionCatalog.build(
            scope = GestureActionCatalogScope.QuickLauncher,
            catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.OverlayTap),
        )
    }
    return remember(actionOptions, searchQuery, context) {
        GestureActionCatalog.filter(context, actionOptions, searchQuery)
    }
}

@Composable
fun rememberQuickLauncherFilteredApps(
    apps: List<AppInfo>,
    searchQuery: String,
    enabled: Boolean = true,
): List<AppInfo> {
    val query = searchQuery.trim().lowercase()
    return remember(apps, query, enabled) {
        if (!enabled || apps.isEmpty()) return@remember emptyList()
        val filtered = apps.filter { app ->
            query.isEmpty() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query) ||
                app.pinyinKey.contains(query)
        }
        filtered.sortedBy { it.pinyinKey }
    }
}

/** 添加页网格列数（应用 / 动作 / 快捷方式共用）。 */
private const val PICKER_GRID_COLUMNS = 4

/**
 * 添加页网格中的一个格子：图标 + 名称 + 选中勾。
 *
 * 图标由调用方给（应用图标组件 / 动作矢量图标），这样三种内容共用同一套格子。
 */
@Composable
private fun PickerGridCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.Check,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
        )
    }
}
