package com.slideindex.app.ui.gesturepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.LaunchWindowMode
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.ui.picker.FilteredShortcutCatalog
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.ui.picker.GestureActionCatalogScope
import com.slideindex.app.ui.picker.appPickerGridItems
import com.slideindex.app.ui.picker.activityShortcutPickerRadioSection
import com.slideindex.app.ui.picker.shortcutFolderCardsSection
import com.slideindex.app.ui.picker.systemShortcutCatalogItems
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.ShortcutScanProgress
import com.slideindex.app.ui.gestureActionIcon
import top.yukonga.miuix.kmp.basic.Icon

/** 动作网格列数（列表排布与「定位到当前动作」的下标计算共用，改这里两处一起变）。 */
internal const val ACTION_GRID_COLUMNS = 4

/**
 * 这个动作"选完还有参数要配"吗？
 *
 * 有参数的在格子上打一个小角标，点它进详情页（或先弹二级选择框，如「运行宏」挑宏）；
 * 没有的直接选中退出。新增带配置页 / 二级选择器的动作类型时，把类型加进这里。
 */
internal fun actionHasDetailParams(type: GestureActionType): Boolean = when (type) {
    GestureActionType.QUICK_LAUNCHER,
    GestureActionType.QUICK_WHEEL,
    GestureActionType.WIDGET_POPUP_OVERLAY,
    GestureActionType.EXECUTE_SHELL_COMMAND,
    GestureActionType.OPEN_LINK,
    GestureActionType.SIMULATE_KEY_EVENT,
    // 「运行宏」不是详情页而是一个二级弹窗（MacroPickDialog / MacroPickOverlay），
    // 但语义一样：点下去还没完，得先挑一条宏 —— 所以同样打点，别让它看起来像"一下就选中"的动作。
    GestureActionType.RUN_MACRO,
    -> true
    else -> false
}

/**
 * 动作网格格子：图标 + 最多 2 行标题 + 右上角"有参数"小圆点，选中时描边。
 *
 * 原来的行内副标题（动作描述 / 当前配置摘要，如"钉住·仅本应用"、Shell 命令预览）在网格里没有
 * 位置，统一由动作详情页承载 —— 这也是"网格化"必须和"详情页"一起做的原因。
 */
@Composable
private fun ActionPickerGridCell(
    action: GestureAction,
    selected: Boolean,
    hasParams: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(
                        if (selected) {
                            accent.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                com.slideindex.app.ui.ActionIconSlot(
                    action = action,
                    sizeDp = 27,
                )
            }
            if (hasParams) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(accent),
                )
            }
        }
        // 标题固定两行：动作名最长到「执行 Shell 命令」「模拟按键 (KeyEvent)」这个量级，
        // 4 列时一格只有 ~74dp 文字宽（一行约 6 个汉字），一行必然打省略号。
        // minLines = 2 让短标签也占满两行高度，整行格子等高，行距不会参差不齐。
        Text(
            text = gestureActionLabel(action),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 5.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
fun rememberActionPickerFilteredActions(
    trigger: GestureTriggerType,
    searchQuery: String,
    includePointerGestureActions: Boolean,
    includeCornerInnerZoneActions: Boolean,
    pinNoneAtTop: Boolean,
    catalogPolicy: ActionPickerCatalogPolicy,
    excludeActionTypes: Set<GestureActionType> = emptySet(),
): List<GestureAction> {
    val context = LocalContext.current
    val actionOptions = remember(
        trigger,
        includePointerGestureActions,
        includeCornerInnerZoneActions,
        catalogPolicy,
        excludeActionTypes,
    ) {
        GestureActionCatalog.build(
            scope = GestureActionCatalogScope.GesturePicker,
            trigger = trigger,
            includePointerGestureActions = includePointerGestureActions,
            includeCornerInnerZoneActions = includeCornerInnerZoneActions,
            catalogPolicy = catalogPolicy,
        ).filterNot { it.type in excludeActionTypes }
    }
    return remember(
        actionOptions,
        searchQuery,
        context,
        includeCornerInnerZoneActions,
        pinNoneAtTop,
    ) {
        GestureActionCatalog.filter(
            context = context,
            actions = actionOptions,
            query = searchQuery,
            pinNoneAtTop = pinNoneAtTop,
            includeCornerInnerZoneActions = includeCornerInnerZoneActions,
        )
    }
}

@Composable
fun rememberActionPickerFilteredApps(
    apps: List<AppInfo>,
    searchQuery: String,
): List<AppInfo> {
    val query = searchQuery.trim().lowercase()
    return remember(apps, query) {
        apps.filter { app ->
            query.isEmpty() ||
                app.label.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query) ||
                app.pinyinKey.contains(query)
        }.sortedBy { it.pinyinKey }
    }
}

fun LazyListScope.actionPickerActionItems(
    filtered: List<GestureAction>,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
    onOpenExecuteShellCommand: () -> Unit,
    onOpenOpenLink: () -> Unit = {},
    onOpenSimulateKeyEvent: (() -> Unit)? = null,
    onOpenQuickWheel: (() -> Unit)? = null,
    onOpenQuickLauncher: (() -> Unit)? = null,
    /** 「小组件面板」：有多个页时先选页；只有一个页时直接选中（调用方决定）。 */
    onOpenWidgetPanel: (() -> Unit)? = null,
    /** 定位到"当前已绑定动作"之后闪一下的强度（0..1），只作用在当前那一行上。 */
    highlightPulse: Float = 0f,
) {
    if (filtered.isEmpty()) {
        item(key = "actions-empty") {
            Text(
                text = stringResource(R.string.search_no_actions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            )
        }
        return
    }

    val sections = GestureActionCatalog.groupIntoSections(filtered)
    sections.forEach { section ->
        item(key = "action-cat-${section.category.name}") {
            com.slideindex.app.ui.Md3PickerSectionHeader(stringResource(section.category.titleRes))
        }
        // 网格：每行 4 个。动作类型有近百个，纵向列表要滚很久；网格一屏能看 12~16 个。
        //
        // 格子里只留「图标 + 标题 + 状态角标」—— 原来行内的副标题（描述 / 当前配置摘要）
        // 全部交给动作详情页承载，否则网格一定会挤成两行文字参差不齐。
        // 点格子 = 进详情页（有参数的动作）或直接选中（返回/桌面/截屏这类无参数动作）。
        section.actions.chunked(ACTION_GRID_COLUMNS).forEachIndexed { rowIndex, rowActions ->
            item(key = "action-row-${section.category.name}-$rowIndex") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowActions.forEach { action ->
                        val context = LocalContext.current
                        val selected = when {
                            action.type == GestureActionType.LAUNCH_APP ||
                                action.type == GestureActionType.LAUNCH_SHORTCUT -> false
                            else -> action.type == current.type
                        }
                        ActionPickerGridCell(
                            action = action,
                            selected = selected,
                            hasParams = actionHasDetailParams(action.type),
                            onClick = {
                                requestPermissionForAdjustAction(context, action)
                                when {
                                    action.type == GestureActionType.OPEN_LINK -> onOpenOpenLink()
                                    action.type == GestureActionType.EXECUTE_SHELL_COMMAND ->
                                        onOpenExecuteShellCommand()
                                    action.type == GestureActionType.SIMULATE_KEY_EVENT &&
                                        onOpenSimulateKeyEvent != null -> onOpenSimulateKeyEvent()
                                    action.type == GestureActionType.QUICK_WHEEL &&
                                        onOpenQuickWheel != null -> onOpenQuickWheel()
                                    action.type == GestureActionType.QUICK_LAUNCHER &&
                                        onOpenQuickLauncher != null -> onOpenQuickLauncher()
                                    action.type == GestureActionType.WIDGET_POPUP_OVERLAY &&
                                        onOpenWidgetPanel != null -> onOpenWidgetPanel()
                                    else -> onSelect(action)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 最后一行不足 4 个时补空位，保证格子等宽。
                    repeat(ACTION_GRID_COLUMNS - rowActions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

fun LazyListScope.actionPickerAppItems(
    filtered: List<AppInfo>,
    current: GestureAction,
    onSelect: (AppInfo) -> Unit,
    /** 定位到"当前已绑定应用"之后闪一下的强度（0..1），只作用在当前那一行上。 */
    highlightPulse: Float = 0f,
) {
    val currentApp = current as? GestureAction.LaunchApp
    appPickerGridItems(
        apps = filtered,
        selectedKeys = if (currentApp == null) {
            emptySet()
        } else {
            setOf(
                com.slideindex.app.launcher.AppTarget.encode(
                    currentApp.packageName,
                    currentApp.cloneUserId,
                ),
            )
        },
        onAppClick = onSelect,
    )
}

fun LazyListScope.actionPickerShortcutItems(
    appsByPackage: Map<String, AppInfo>,
    searchQuery: String,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
    activityShortcuts: List<ActivityShortcut>,
    onOpenMyShortcuts: () -> Unit,
    onOpenPresetShortcuts: () -> Unit,
    onBrowseActivityShortcut: () -> Unit,
    filtered: FilteredShortcutCatalog,
    loading: Boolean,
    scanProgress: ShortcutScanProgress?,
    onCreateHostClick: (AppShortcutLoader.CreateShortcutHost) -> Unit,
    shortcutRowContent: @Composable (
        group: AppShortcutLoader.AppShortcutGroup,
        shortcut: com.slideindex.app.overlay.TaskSwitcherMenuItem,
        segmentIndex: Int,
        segmentCount: Int,
    ) -> Unit,
) {
    if (searchQuery.isBlank()) {
        shortcutFolderCardsSection(
            activityShortcutsCount = activityShortcuts.size,
            onOpenMyShortcuts = onOpenMyShortcuts,
            onOpenPresetShortcuts = onOpenPresetShortcuts,
        )
    } else if (activityShortcuts.isNotEmpty()) {
        activityShortcutPickerRadioSection(
            activityShortcuts = activityShortcuts,
            current = current,
            onSelect = onSelect,
            onBrowse = onBrowseActivityShortcut,
            searchQuery = searchQuery,
        )
    }
    systemShortcutCatalogItems(
        filtered = filtered,
        appsByPackage = appsByPackage,
        loading = loading,
        scanProgress = scanProgress,
        searchQuery = searchQuery,
        onCreateHostClick = onCreateHostClick,
        shortcutRowContent = shortcutRowContent,
    )
}

/**
 * 当前已绑定的动作属于哪一页 —— 打开选择器时据此预选标签页，
 * 不要让人每次都从第 0 页开始翻。
 */
internal fun GestureAction.pickerTab(): ActionPickerTab = when (this) {
    is GestureAction.LaunchApp -> ActionPickerTab.APPS
    is GestureAction.LaunchShortcut -> ActionPickerTab.SHORTCUTS
    else -> ActionPickerTab.ACTIONS
}

/**
 * 当前已绑定的动作在「动作」页里的**扁平下标**（把每个分组的标题行也算进去）。
 *
 * ⚠️ 必须与 [actionPickerActionItems] 的排布逐行对齐：每个分组 = 1 行标题 + N 行动作。
 * 找不到就返回 null（比如「打开应用 / 启动快捷方式」根本不在这一页、或该项已下架），
 * 调用方回落到列表顶部。
 */
internal fun actionPickerIndexOfCurrent(
    filtered: List<GestureAction>,
    current: GestureAction,
): Int? {
    if (current.type == GestureActionType.LAUNCH_APP ||
        current.type == GestureActionType.LAUNCH_SHORTCUT ||
        current.type == GestureActionType.NONE
    ) {
        return null
    }
    var index = 0
    for (section in GestureActionCatalog.groupIntoSections(filtered)) {
        val offset = section.actions.indexOfFirst { it.type == current.type }
        // 网格里一个分组占 1 行标题 + ceil(N / 列数) 行格子。
        if (offset >= 0) return index + 1 + offset / ACTION_GRID_COLUMNS
        index += 1 + (section.actions.size + ACTION_GRID_COLUMNS - 1) / ACTION_GRID_COLUMNS
    }
    return null
}

/**
 * 当前已绑定的快捷方式在「快捷方式」页里的**扁平下标**（把标题行 / 应用分组行 / 分组间占位都算进去）。
 *
 * ⚠️ 必须与这三段的实际排布逐行对齐（改了任何一段都要同步改这里）：
 * 1. [shortcutFolderCardsSection]（搜索为空时）：固定两张卡 = 2 行；
 * 2. [activityShortcutPickerRadioSection]（搜索非空且有自定义快捷方式时）：1 行标题 + n 行 + 1 行「浏览」；
 * 3. [systemShortcutCatalogItems]：可选「创建快捷方式」标题 + n 行 + 占位，然后「启动快捷方式」标题 +
 *    每个应用（1 行分组 + m 行 + 1 行占位）。
 *
 * 加载中 / 该项已不在目录里时返回 null，调用方回落到顶部。
 */
internal fun actionPickerShortcutIndexOfCurrent(
    filtered: FilteredShortcutCatalog,
    activityShortcuts: List<ActivityShortcut>,
    searchQuery: String,
    loading: Boolean,
    current: GestureAction,
): Int? {
    if (loading) return null
    val launch = current as? GestureAction.LaunchShortcut ?: return null
    var index = 0
    if (searchQuery.isBlank()) {
        index += 2
    } else if (activityShortcuts.isNotEmpty()) {
        index += activityShortcuts.size + 2
    }
    if (filtered.createHosts.isNotEmpty()) {
        index += 1 // 可折叠的「创建快捷方式」段
    }
    if (filtered.groups.isEmpty()) return null
    index += 1 // 「启动快捷方式」标题
    for (group in filtered.groups) {
        index += 1 // 该应用的分组行
        for (shortcut in group.shortcuts) {
            if (shortcutToLaunchShortcut(shortcut, group.app.packageName).payloadKey == launch.payloadKey) {
                return index
            }
            index += 1
        }
        index += 1 // 分组间占位
    }
    return null
}
