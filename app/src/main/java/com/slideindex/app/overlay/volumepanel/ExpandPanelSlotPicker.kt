package com.slideindex.app.overlay.volumepanel

/**
 * Portions derived from EdgeGesture (https://github.com/evilgodxu/EdgeGesture)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.subtitleDetail
import com.slideindex.app.activity.toLaunchShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.LaunchWindowMode
import com.slideindex.app.gesture.QuickWheelAnchorMode
import com.slideindex.app.gesture.QuickWheelLaunchShape
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.ui.GestureOpenLinkScreen
import com.slideindex.app.ui.GestureQuickLauncherActionScreen
import com.slideindex.app.ui.GestureQuickWheelActionScreen
import com.slideindex.app.ui.Md3PickerAppShortcutLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.shortcutKindChip
import com.slideindex.app.ui.Md3PickerManagedShortcutLeading
import com.slideindex.app.ui.Md3PickerSectionHeader
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.gesturepicker.MacroPickOverlay
import com.slideindex.app.ui.gesturepicker.actionPickerActionItems
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredActions
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredApps
import com.slideindex.app.ui.miuix.MiuixSearchField
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.picker.AppPickerTabRow
import com.slideindex.app.ui.picker.appPickerGridItems
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.quicklauncher.QuickLauncherEmbedParentConfirm
import com.slideindex.app.ui.taskSwitcherItemToLaunchShortcut
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class ExpandPanelPickerTab {
    ACTIONS,
    APPS,
    SHORTCUTS,
}

private sealed class ExpandPanelShortcutRow {
    data class Managed(val shortcut: ActivityShortcut) : ExpandPanelShortcutRow()
    data class System(val app: AppInfo, val shortcut: TaskSwitcherMenuItem) : ExpandPanelShortcutRow()
}

/** 与列表行 [com.slideindex.app.ui.Md3PickerIconLeading] 一致的槽位选择器可视高度。 */
internal val ExpandPanelPickerListHeight = 440.dp

/**
 * 选择器当前停在哪一页。
 *
 * 「打开链接 / 快捷轮盘 / 打开快捷启动器 / 运行宏」这些动作点下去还没完：
 * 面板是悬浮窗（window type 2038），既跳不了设置页、也弹不了平台 Dialog，
 * 所以参数页要么就地换上整块内容（[LINK] / [WHEEL] / [LAUNCHER]），
 * 要么在本页上盖一层（宏，见 `MacroPickOverlay`）。
 */
private enum class ExpandPanelPickerSubScreen {
    LIST,
    LINK,
    WHEEL,
    LAUNCHER,
}

@Composable
fun ExpandPanelSlotPicker(
    allApps: List<AppInfo>,
    activityShortcuts: List<ActivityShortcut>,
    current: GestureAction?,
    onSelect: (GestureAction) -> Unit,
    onCancel: () -> Unit,
    editingSubtitle: String? = null,
    onClearSlot: (() -> Unit)? = null,
    /** 「快捷轮盘」参数页的数据源。 */
    wheels: List<QuickWheel> = emptyList(),
    /** 「打开快捷启动器」参数页的数据源。 */
    panels: List<QuickLauncherPanel> = emptyList(),
    /** overlay 窗里的键盘高度：列表按它让位，否则键盘会盖住下半截（见 rememberOverlayImeBottomHeight）。 */
    imeBottom: Dp = 0.dp,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    // 「运行宏」点下去还没完：得先挑一条宏。悬浮面板里不能弹平台 Dialog，
    // 所以用同一界面上的覆盖层（MacroPickOverlay）。
    // 少了这一步就会把 macroId 为空的 RunMacro 直接回填成"运行一条不存在的宏"。
    var macroPickVisible by remember { mutableStateOf(false) }
    var subScreen by remember { mutableStateOf(ExpandPanelPickerSubScreen.LIST) }
    val currentAction = current ?: GestureAction.None
    // 键盘弹起时面板整体被抬起（见 VolumePanelOverlayRoot），内容也要按同一高度收短，
    // 否则抬起来之后上半截会被顶出屏幕。
    val listHeight = (ExpandPanelPickerListHeight - imeBottom).coerceAtLeast(200.dp)

    val filteredActions = rememberActionPickerFilteredActions(
        trigger = GestureTriggerType.SHORT_SINGLE_TAP,
        searchQuery = searchQuery,
        includePointerGestureActions = false,
        includeCornerInnerZoneActions = false,
        pinNoneAtTop = false,
        catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.OverlayTap),
    )
    val filteredApps = rememberActionPickerFilteredApps(allApps, searchQuery)
    val loadedCatalog = rememberLoadedShortcutCatalog(
        apps = allApps,
        enabled = selectedTab == ExpandPanelPickerTab.SHORTCUTS.ordinal,
    )
    val filteredShortcuts = remember(loadedCatalog.catalog, searchQuery) {
        filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
    }
    val managedShortcuts = remember(activityShortcuts, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            activityShortcuts
        } else {
            activityShortcuts.filter { shortcut ->
                shortcut.label.lowercase().contains(query) ||
                    shortcut.packageName.lowercase().contains(query)
            }
        }
    }
    val systemShortcutRows = remember(filteredShortcuts) {
        buildList {
            filteredShortcuts.groups.forEach { group ->
                group.shortcuts.forEach { shortcut ->
                    add(ExpandPanelShortcutRow.System(group.app, shortcut))
                }
            }
        }
    }
    val flatShortcutRows = remember(managedShortcuts, systemShortcutRows) {
        buildList {
            managedShortcuts.forEach { add(ExpandPanelShortcutRow.Managed(it)) }
            addAll(systemShortcutRows)
        }
    }

    // 带参数的动作：整块换上调参页，确认后才回填槽位。
    // （以前这里直接把"空模板"存进槽位：链接点了没反应、轮盘绑到空轮盘、启动器靠归一化兜底。）
    when (subScreen) {
        ExpandPanelPickerSubScreen.LINK -> {
            ExpandPanelLinkConfig(
                initialUrl = (currentAction as? GestureAction.OpenLink)?.url.orEmpty(),
                initialLabel = (currentAction as? GestureAction.OpenLink)?.label.orEmpty(),
                listHeight = listHeight,
                onBack = { subScreen = ExpandPanelPickerSubScreen.LIST },
                onConfirm = { url, label ->
                    subScreen = ExpandPanelPickerSubScreen.LIST
                    onSelect(GestureAction.OpenLink(url = url, label = label))
                },
            )
            return
        }
        ExpandPanelPickerSubScreen.WHEEL -> {
            val wheelAction = currentAction as? GestureAction.QuickWheel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(listHeight),
            ) {
                GestureQuickWheelActionScreen(
                    wheels = wheels,
                    initialWheelId = wheelAction?.wheelId.orEmpty(),
                    initialShape = wheelAction?.shape ?: QuickWheelLaunchShape.DEFAULT,
                    initialSectorMask = wheelAction?.manualSectorMask,
                    initialAnchorMode = wheelAction?.anchorMode ?: QuickWheelAnchorMode.FOLLOW_FINGER,
                    onBack = { subScreen = ExpandPanelPickerSubScreen.LIST },
                    onConfirm = { wheelId, shape, manualSectorMask, anchorMode ->
                        subScreen = ExpandPanelPickerSubScreen.LIST
                        onSelect(
                            GestureAction.QuickWheel(
                                wheelId = wheelId,
                                shape = shape,
                                manualSectorMask = manualSectorMask,
                                anchorMode = anchorMode,
                            ),
                        )
                    },
                    overlayMode = true,
                )
            }
            return
        }
        ExpandPanelPickerSubScreen.LAUNCHER -> {
            val launcherAction = currentAction as? GestureAction.QuickLauncher
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(listHeight),
            ) {
                GestureQuickLauncherActionScreen(
                    panels = panels,
                    initialPanelId = launcherAction?.panelId.orEmpty(),
                    initialPinned = launcherAction?.pinned == true,
                    initialPinCurrentApp = launcherAction?.pinCurrentApp == true,
                    onBack = { subScreen = ExpandPanelPickerSubScreen.LIST },
                    onConfirm = { panelId, pinned, pinCurrentApp ->
                        subScreen = ExpandPanelPickerSubScreen.LIST
                        onSelect(
                            GestureAction.QuickLauncher(
                                panelId = panelId,
                                pinned = pinned,
                                pinCurrentApp = pinCurrentApp,
                            ),
                        )
                    },
                    overlayMode = true,
                )
            }
            return
        }
        ExpandPanelPickerSubScreen.LIST -> Unit
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.expand_panel_shortcut_pick),
                style = MiuixTheme.textStyles.title4,
            )
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onCancel,
            )
        }
        if (!editingSubtitle.isNullOrBlank()) {
            Text(
                text = editingSubtitle,
                style = MiuixTheme.textStyles.footnote2,
                color = expandPanelSecondaryTextColor(),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
        MiuixSearchField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            hintResId = R.string.search_hint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        AppPickerTabRow(
            tabs = listOf(
                stringResource(R.string.action_picker_tab_actions),
                stringResource(R.string.action_picker_tab_apps),
                stringResource(R.string.action_picker_tab_shortcuts),
            ),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            contourHost = MiuixTabRowContourHost.SurfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        // 列表区高度固定：宏选择层（MacroPickOverlay）就盖在这块区域上。
        // 悬浮面板是 window type 2038，里面不能弹平台 Dialog（见 MacroPickOverlay 的注释）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(listHeight)
                .padding(top = 4.dp),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                when (ExpandPanelPickerTab.entries[selectedTab]) {
                    ExpandPanelPickerTab.ACTIONS -> {
                        actionPickerActionItems(
                            filtered = filteredActions,
                            current = currentAction,
                            onSelect = { picked ->
                                // 这几格都只带"空模板"：先把参数问出来再回填，
                                // 别把空 id / 默认值直接存进槽位。
                                when {
                                    picked is GestureAction.RunMacro && picked.macroId.isBlank() ->
                                        macroPickVisible = true
                                    picked.type == GestureActionType.QUICK_WHEEL ->
                                        subScreen = ExpandPanelPickerSubScreen.WHEEL
                                    picked.type == GestureActionType.QUICK_LAUNCHER ->
                                        subScreen = ExpandPanelPickerSubScreen.LAUNCHER
                                    else -> onSelect(picked)
                                }
                            },
                            onOpenExecuteShellCommand = { onSelect(GestureAction.ExecuteShellCommand()) },
                            onOpenSimulateKeyEvent = { onSelect(GestureAction.SimulateKeyEvent()) },
                            onOpenOpenLink = { subScreen = ExpandPanelPickerSubScreen.LINK },
                        )
                    }
                    ExpandPanelPickerTab.APPS -> {
                        if (filteredApps.isEmpty()) {
                            item(key = "apps-empty") {
                                Text(
                                    text = stringResource(R.string.search_no_actions),
                                    style = MiuixTheme.textStyles.body2,
                                    color = expandPanelSecondaryTextColor(),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                                )
                            }
                        } else {
                            val selectedKeys = (currentAction as? GestureAction.LaunchApp)?.let { action ->
                                setOf(
                                    com.slideindex.app.launcher.AppTarget.encode(
                                        action.packageName,
                                        action.cloneUserId,
                                    ),
                                )
                            } ?: emptySet()
                            appPickerGridItems(
                                apps = filteredApps,
                                selectedKeys = selectedKeys,
                                onAppClick = { app ->
                                    onSelect(
                                        GestureAction.LaunchApp(
                                            app.packageName,
                                            cloneUserId = app.cloneUserId,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    ExpandPanelPickerTab.SHORTCUTS -> {
                        val showSections = searchQuery.isBlank()
                        if (flatShortcutRows.isEmpty()) {
                            item(key = "shortcuts-empty") {
                                Text(
                                    text = stringResource(R.string.search_no_actions),
                                    style = MiuixTheme.textStyles.body2,
                                    color = expandPanelSecondaryTextColor(),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                                )
                            }
                        } else if (showSections) {
                            if (managedShortcuts.isNotEmpty()) {
                                item(key = "shortcuts-managed-header") {
                                    Md3PickerSectionHeader(stringResource(R.string.activity_shortcut_picker_section))
                                }
                                items(managedShortcuts.size, key = { managedShortcuts[it].id }) { index ->
                                    val shortcut = managedShortcuts[index]
                                    val action = shortcut.toLaunchShortcut()
                                    val selected = currentAction is GestureAction.LaunchShortcut &&
                                        currentAction.payloadKey == action.payloadKey
                                    Md3PickerListRow(
                                        segmentIndex = index,
                                        segmentCount = managedShortcuts.size,
                                        title = shortcut.label,
                                        subtitle = shortcut.subtitleDetail(),
                                        selected = selected,
                                        onClick = { onSelect(action) },
                                        leadingContent = {
                                            Md3PickerManagedShortcutLeading(
                                                shortcut = shortcut,
                                                selected = selected,
                                            )
                                        },
                                        trailingMode = PickerTrailingMode.Radio,
                                    )
                                }
                            }
                            if (systemShortcutRows.isNotEmpty()) {
                                item(key = "shortcuts-system-header") {
                                    Md3PickerSectionHeader(stringResource(R.string.launch_shortcut))
                                }
                                items(systemShortcutRows.size, key = { index ->
                                    val row = systemShortcutRows[index]
                                    "system-${row.app.packageName}-${row.shortcut.label}-${row.shortcut.targetComponent}"
                                }) { index ->
                                    val row = systemShortcutRows[index]
                                    val action = taskSwitcherItemToLaunchShortcut(row.shortcut, row.app.packageName)
                                    val selected = currentAction is GestureAction.LaunchShortcut &&
                                        currentAction.payloadKey == action.payloadKey
                                    Md3PickerListRow(
                                        segmentIndex = index,
                                        segmentCount = systemShortcutRows.size,
                                        title = row.shortcut.label,
                                        subtitle = row.app.label,
                                        selected = selected,
                                        onClick = { onSelect(action) },
                                        leadingContent = {
                                            Md3PickerAppShortcutLeading(
                                                packageName = row.app.packageName,
                                                contentDescription = row.shortcut.label,
                                                selected = selected,
                                            )
                                        },
                                        trailingMode = PickerTrailingMode.Radio,
                                        trailingChip = shortcutKindChip(row.shortcut.kind),
                                    )
                                }
                            }
                        } else {
                            items(flatShortcutRows.size, key = { index ->
                                when (val row = flatShortcutRows[index]) {
                                    is ExpandPanelShortcutRow.Managed -> "managed-${row.shortcut.id}"
                                    is ExpandPanelShortcutRow.System ->
                                        "system-${row.app.packageName}-${row.shortcut.label}-${row.shortcut.targetComponent}"
                                }
                            }) { index ->
                                when (val row = flatShortcutRows[index]) {
                                    is ExpandPanelShortcutRow.Managed -> {
                                        val shortcut = row.shortcut
                                        val action = shortcut.toLaunchShortcut()
                                        val selected = currentAction is GestureAction.LaunchShortcut &&
                                            currentAction.payloadKey == action.payloadKey
                                        Md3PickerListRow(
                                            segmentIndex = index,
                                            segmentCount = flatShortcutRows.size,
                                            title = shortcut.label,
                                            subtitle = shortcut.subtitleDetail(),
                                            selected = selected,
                                            onClick = { onSelect(action) },
                                            leadingContent = {
                                                Md3PickerManagedShortcutLeading(
                                                    shortcut = shortcut,
                                                    selected = selected,
                                                )
                                            },
                                            trailingMode = PickerTrailingMode.Radio,
                                        )
                                    }
                                    is ExpandPanelShortcutRow.System -> {
                                        val action = taskSwitcherItemToLaunchShortcut(row.shortcut, row.app.packageName)
                                        val selected = currentAction is GestureAction.LaunchShortcut &&
                                            currentAction.payloadKey == action.payloadKey
                                        Md3PickerListRow(
                                            segmentIndex = index,
                                            segmentCount = flatShortcutRows.size,
                                            title = row.shortcut.label,
                                            subtitle = row.app.label,
                                            selected = selected,
                                            onClick = { onSelect(action) },
                                            leadingContent = {
                                                Md3PickerAppShortcutLeading(
                                                    packageName = row.app.packageName,
                                                    contentDescription = row.shortcut.label,
                                                    selected = selected,
                                                )
                                            },
                                            trailingMode = PickerTrailingMode.Radio,
                                            trailingChip = shortcutKindChip(row.shortcut.kind),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (macroPickVisible) {
                MacroPickOverlay(
                    onDismissRequest = { macroPickVisible = false },
                    onPick = { macro ->
                        macroPickVisible = false
                        onSelect(GestureAction.RunMacro(macro.id))
                    },
                    selectedMacroId = (currentAction as? GestureAction.RunMacro)?.macroId,
                )
            }
        }
        if (onClearSlot != null) {
            TextButton(
                text = stringResource(R.string.expand_panel_clear_slot),
                onClick = onClearSlot,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

/**
 * 扩展面板里的「打开链接」表单。
 *
 * 复用设置页那一份 [GestureOpenLinkScreen]（`embedInParentChrome`），
 * 只是把它的「确定」搬到本卡片的标题行 —— 面板不是 Activity 页面，没有自己的顶栏。
 */
@Composable
private fun ExpandPanelLinkConfig(
    initialUrl: String,
    initialLabel: String,
    listHeight: Dp,
    onBack: () -> Unit,
    onConfirm: (url: String, label: String) -> Unit,
) {
    var embedConfirm by remember { mutableStateOf<QuickLauncherEmbedParentConfirm?>(null) }
    // 记住这个 lambda：ReportQuickLauncherEmbedParentConfirm 用它的身份做 DisposableEffect 的键，
    // 每次组合都换一个新实例会把 "已上报" 反复清成 null（甚至抖成无限重组）。
    val reportConfirm: (QuickLauncherEmbedParentConfirm?) -> Unit = remember { { embedConfirm = it } }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.gesture_action_open_link),
                style = MiuixTheme.textStyles.title4,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                embedConfirm?.let { confirm ->
                    IconButton(
                        onClick = confirm.onConfirm,
                        enabled = confirm.enabled,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Medium.Ok,
                            contentDescription = stringResource(R.string.confirm),
                        )
                    }
                }
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onBack,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(listHeight)
                .padding(top = 4.dp),
        ) {
            GestureOpenLinkScreen(
                initialUrl = initialUrl,
                initialLabel = initialLabel,
                embedInParentChrome = true,
                overlayMode = true,
                enableBackHandler = false,
                onRegisterEmbedParentConfirm = reportConfirm,
                onBack = onBack,
                onConfirm = onConfirm,
            )
        }
    }
}
