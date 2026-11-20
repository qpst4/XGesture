package com.slideindex.app.ui.gesturepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.LaunchWindowMode
import com.slideindex.app.gesture.RingerMode
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.ui.ActionModeMenu
import com.slideindex.app.ui.actionModeOptions
import com.slideindex.app.ui.toRingerMode
import com.slideindex.app.ui.picker.FilteredShortcutCatalog
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.ui.picker.GestureActionCatalogScope
import com.slideindex.app.ui.picker.APP_PICKER_GRID_COLUMNS
import com.slideindex.app.ui.picker.appPickerGridItems
import com.slideindex.app.ui.picker.activityShortcutPickerRadioSection
import com.slideindex.app.ui.picker.shortcutFolderCardsSection
import com.slideindex.app.ui.picker.systemShortcutCatalogItems
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.ShortcutScanProgress
import com.slideindex.app.ui.gestureActionIcon
import top.yukonga.miuix.kmp.basic.Icon

/** 动作网格默认列数（竖屏）。 */
internal const val ACTION_GRID_COLUMNS = 4

/** 动作网格单元格基准推荐最小宽度，保证正方形/微长方形比例与触控体验。 */
internal val ACTION_GRID_MIN_CELL_WIDTH = 78.dp

/**
 * 根据容器实际可用宽度计算自适应列数：
 * - 竖屏（~360-430dp）：4 列
 * - 折叠屏展开态 / 小平板（~600dp）：6 列
 * - 横屏 / 大平板（~800-1000dp）：8~10 列
 */
internal fun calculateActionGridColumns(availableWidth: Dp): Int {
    val horizontalPadding = 24.dp // 左右各 12.dp 外边距
    val spacing = 8.dp
    val available = availableWidth - horizontalPadding
    val cols = ((available + spacing) / (ACTION_GRID_MIN_CELL_WIDTH + spacing)).toInt()
    return cols.coerceAtLeast(ACTION_GRID_COLUMNS)
}

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
 * 动作网格单元项：排布于分类大卡片内部，平时透明与大卡片融为一体，
 * 选中时浮现主题色微透底色与微描边，点击时有圆角水波纹。
 */
@Composable
private fun ActionPickerGridCell(
    action: GestureAction,
    selected: Boolean,
    hasParams: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** 覆盖显示名（切换类动作带模式后缀时用；null = 用动作本名）。 */
    label: String? = null,
    /** 覆盖图标用的动作实例（已绑定带模式的动作时，图标要跟着模式走）。 */
    iconAction: GestureAction = action,
    /** 定位到已选动作时的闪烁脉冲（0..1）。 */
    highlightPulse: Float = 0f,
) {
    val accent = MiuixTheme.colorScheme.primary
    val effectiveSelected = selected || highlightPulse > 0.05f
    val baseBackground = if (selected) {
        accent.copy(alpha = 0.16f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    val cellBackground = if (highlightPulse > 0.05f) {
        lerp(
            baseBackground,
            accent.copy(alpha = 0.32f),
            highlightPulse,
        )
    } else {
        baseBackground
    }

    val cornerRadius = 12.dp
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(cellBackground)
            .then(
                if (effectiveSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = accent.copy(alpha = if (selected) 0.65f else highlightPulse * 0.7f),
                        shape = shape,
                    )
                } else {
                    Modifier
                }
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            com.slideindex.app.ui.ActionIconSlot(
                action = iconAction,
                sizeDp = 28,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label ?: gestureActionLabel(action),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    accent
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
            )
        }
        if (hasParams) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 2.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
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
    /**
     * 系统当前铃声模式（`AudioManager.RINGER_MODE_*`）。
     *
     * 只影响「声音模式 · 切换」的图标：显式档位仍画动作目标，切换档画手机当前状态。
     * `null` = 调用方没接实时状态，退回静态铃铛。
     */
    liveRingerMode: Int? = null,
    /** 网格列数：自适应计算，默认 4 列（竖屏）；横屏/平板自动扩展。 */
    columns: Int = ACTION_GRID_COLUMNS,
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

        // 整个分类封装为一张标准的 MIUIX 大卡片（CardSegment，12dp 外边距），内部整齐排布动作网格
        item(key = "action-card-${section.category.name}") {
            CardSegment(
                isFirst = true,
                isLast = true,
                outerHorizontalPadding = 12.dp,
                insidePadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            ) {
                val rows = section.actions.chunked(columns)
                rows.forEachIndexed { rowIndex, rowActions ->
                    if (rowIndex > 0) {
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                    rowActions.forEach { action ->
                        val context = LocalContext.current
                        val selected = when {
                            action.type == GestureActionType.LAUNCH_APP ||
                                action.type == GestureActionType.LAUNCH_SHORTCUT -> false
                            else -> action.type == current.type
                        }
                        val cellPulse = if (selected) highlightPulse else 0f
                        val bound = current.takeIf { it.type == action.type }
                        val modeOptions = actionModeOptions(action, bound)
                        // 「声音模式 · 切换」：图标跟手机实际铃声状态走；显式档位仍画动作目标。
                        val liveIconAction = if (
                            action is GestureAction.ToggleMute &&
                            action.ringerMode == RingerMode.TOGGLE &&
                            liveRingerMode != null
                        ) {
                            GestureAction.ToggleMute(liveRingerMode.toRingerMode())
                        } else {
                            bound ?: action
                        }
                        val cellLabel = if (selected && bound != null) {
                            gestureActionLabel(bound)
                        } else {
                            gestureActionLabel(action)
                        }
                        if (modeOptions != null) {
                            // 带「操作方式」的动作：点格子先弹菜单，选完才真正选中。
                            ActionModeMenu(
                                options = modeOptions,
                                onPick = { picked ->
                                    requestPermissionForAdjustAction(context, picked)
                                    onSelect(picked)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                ActionPickerGridCell(
                                    action = action,
                                    selected = selected,
                                    hasParams = true,
                                    onClick = null,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = cellLabel,
                                    iconAction = liveIconAction,
                                    highlightPulse = cellPulse,
                                )
                            }
                        } else {
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
                                highlightPulse = cellPulse,
                            )
                        }
                    }
                    // 最后一行不足 columns 个时补空位，保证格子等宽。
                    repeat(columns - rowActions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
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
    columns: Int = APP_PICKER_GRID_COLUMNS,
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
        columns = columns,
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
    @Suppress("UNUSED_PARAMETER") columns: Int = ACTION_GRID_COLUMNS,
): Int? {
    if (current.type == GestureActionType.LAUNCH_APP ||
        current.type == GestureActionType.LAUNCH_SHORTCUT ||
        current.type == GestureActionType.NONE
    ) {
        return null
    }
    var index = 0
    for (section in GestureActionCatalog.groupIntoSections(filtered)) {
        val contains = section.actions.any { it.type == current.type }
        // 每个分类占 1 行标题 + 1 行大卡片；若找到了直接定位到该分类的大卡片
        if (contains) return index + 1
        index += 2
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
