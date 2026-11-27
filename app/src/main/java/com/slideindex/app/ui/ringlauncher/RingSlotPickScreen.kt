package com.slideindex.app.ui.ringlauncher

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.AppTarget
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.gesture.KeyEventPresets
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureOpenLinkScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.displayLabelForExecuteShellCommand
import com.slideindex.app.ui.gestureActionLabel
import com.slideindex.app.ui.gesturepicker.MacroPickDialog
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.miuix.syncCollapseAfterProgrammaticScroll
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.AppPickerTabRow
import com.slideindex.app.ui.picker.GestureActionCatalog
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.pickerListSegmentedGap
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.gesturepicker.calculateActionGridColumns
import com.slideindex.app.ui.quicklauncher.QuickLauncherEditorAddTab
import com.slideindex.app.ui.quicklauncher.quickLauncherAddPickerActionItems
import com.slideindex.app.ui.quicklauncher.quickLauncherAddPickerAppItems
import com.slideindex.app.ui.quicklauncher.quickLauncherAddPickerShortcutItems
import com.slideindex.app.ui.quicklauncher.rememberQuickLauncherFilteredActions
import com.slideindex.app.ui.quicklauncher.rememberQuickLauncherFilteredApps
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.toQuickLauncherItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Image
import kotlinx.coroutines.launch

/**
 * 圆环启动器 · 槽位选择页（标准列表页，单选）。
 *
 * 结构与「边角轮盘槽位」一致：`SettingsLazyScreenScaffold`（列表从屏幕顶开始画，内容滚动时从
 * 顶栏下穿过 → 栏才采样得到模糊；脚手架同时提供 `LocalMiuixContentUnderBar`，tab 行是玻璃的）。
 * 点一项即写回槽位并返回（单选）；需要二次配置的动作（打开链接 / Shell / 模拟按键 / 宏）
 * 走子页面，选完再写回。
 */
@Composable
fun RingSlotPickScreen(
    slotIndex: Int,
    currentItem: QuickLauncherItem?,
    apps: List<AppInfo>,
    settings: AppSettings,
    onBack: () -> Unit,
    onSelectItem: (QuickLauncherItem?) -> Unit,
    onSelectNoneMode: (RingSlotNoneMode) -> Unit,
    onOpenCustomIconEditor: () -> Unit,
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (AppShortcutLoader.CreatedShortcut?) -> Unit,
    ) -> Unit,
    onBackHandlerReady: ((() -> Unit) -> Unit)? = null,
) {
    val tabs = remember { QuickLauncherEditorAddTab.entries }
    var selectedTab by remember { mutableIntStateOf(initTabFor(currentItem)) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // 顶栏折叠态只由 nestedScroll 写；本页是程序化定位，所以要把它注入脚手架再手动同步一次。
    val listScrollBehavior = MiuixScrollBehavior()
    var page by remember { mutableStateOf<RingSlotPage>(RingSlotPage.Main) }
    var pendingMacroPick by remember { mutableStateOf<((GestureAction) -> Unit)?>(null) }
    val requestComposite = com.slideindex.app.ui.composite.rememberCompositePickRequester()
    val requestOpenLink = com.slideindex.app.ui.composite.rememberOpenLinkRequester()
    var pendingPanelPick by remember { mutableStateOf<QuickLauncherItem?>(null) }
    var pendingCreateHost by remember {
        mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null)
    }

    val localContext = LocalContext.current
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val host = pendingCreateHost
        pendingCreateHost = null
        if (result.resultCode != android.app.Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
        val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data, localContext)
            ?: return@rememberLauncherForActivityResult
        onSelectItem(created.toQuickLauncherItem())
    }

    val isAppsTabActive = tabs[selectedTab] == QuickLauncherEditorAddTab.APPS
    val isShortcutsTabActive = tabs[selectedTab] == QuickLauncherEditorAddTab.SHORTCUTS
    // 槽位可以「无」：快捷启动器目录不含 None（多选添加页不需要），在这里补进动作列表，
    // 与边角轮盘/指尖环等槽位页一致。
    val filteredActions = rememberQuickLauncherFilteredActions(searchQuery, pinNoneAtTop = true)
    val filteredApps = rememberQuickLauncherFilteredApps(
        apps = apps,
        searchQuery = searchQuery,
        enabled = isAppsTabActive || searchQuery.isNotBlank(),
    )
    val loadedCatalog = rememberLoadedShortcutCatalog(
        apps = apps,
        enabled = isShortcutsTabActive || searchQuery.isNotBlank(),
    )
    val filteredShortcuts = remember(loadedCatalog.catalog, searchQuery) {
        filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
    }
    val appsByPackage = remember(apps) {
        apps.groupBy { it.packageName }.mapValues { (_, list) ->
            list.firstOrNull { it.cloneUserId == 0 } ?: list.first()
        }
    }
    val panels = remember(settings.quickLauncherPanels) {
        QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels)
    }
    val panelDisplayNames = buildList {
        panels.forEachIndexed { index, panel ->
            add(panel.name.ifBlank { "" }.ifEmpty { "#${index + 1}" })
        }
    }
    val boundPanelId = currentItem?.panelIdOf()
        ?.let { QuickLauncherPanelDefaults.resolvePanelId(panels, it) }
    val quickLauncherActionLabel = stringResource(R.string.gesture_action_quick_launcher)

    // 当前绑定项的"已选"标记（单选高亮）。
    val configuredActionKeys = remember(currentItem) {
        if (currentItem?.type == QuickLauncherItemType.ACTION) {
            QuickLauncherItemCodec.parseActionPayload(currentItem.payload)
                ?.let { setOf(QuickLauncherItemCodec.actionKey(it)) }
                .orEmpty()
        } else {
            emptySet()
        }
    }
    val configuredAppPackages = remember(currentItem) {
        if (currentItem?.type == QuickLauncherItemType.APP) setOf(currentItem.payload) else emptySet()
    }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val gridColumns = remember(configuration.screenWidthDp) {
        calculateActionGridColumns(configuration.screenWidthDp.dp)
    }
    val configuredShortcutKeys = remember(currentItem) {
        if (currentItem?.type == QuickLauncherItemType.SHORTCUT) {
            QuickLauncherItemCodec.shortcutItemKey(currentItem)?.let { setOf(it) }.orEmpty()
        } else {
            emptySet()
        }
    }

    // 打开时定位到当前绑定的那一项（和旧浮层版一致）。
    //
    // ⚠️ `scrollToItem` 要的是 **LazyColumn 的 item 下标**，不是"网格行号"：
    // - ACTIONS 每个分类只有「标题 item + 整节卡片 item」两个 item（卡片内部才按列排网格），
    //   以前按 `chunked(4)` 的行号当 item 下标，行号远大于真实 item 数 → 被夹到列表末尾，
    //   真机表现就是"打开就停在底部"；
    // - APPS 是每行一个 item（`appPickerGridItems`），行号 = 下标 / 列数（列数与列表一致）；
    // - 两种情况都要加上列表开头由脚手架插入的 header item（见 [RingSlotListHeaderItems]）。
    var revealApplied by remember(selectedTab, currentItem) { mutableStateOf(false) }
    LaunchedEffect(selectedTab, currentItem, filteredActions, filteredApps, gridColumns) {
        if (revealApplied) return@LaunchedEffect
        val item = currentItem ?: return@LaunchedEffect
        val target = when (tabs[selectedTab]) {
            QuickLauncherEditorAddTab.ACTIONS -> {
                val key = QuickLauncherItemCodec.parseActionPayload(item.payload)
                    ?.let(QuickLauncherItemCodec::actionKey)
                    ?: return@LaunchedEffect
                ringActionScrollItemIndex(filteredActions, key)
            }
            QuickLauncherEditorAddTab.APPS -> {
                val index = if (item.type == QuickLauncherItemType.APP) {
                    filteredApps.indexOfFirst { app ->
                        AppTarget.encode(app.packageName, app.cloneUserId) == item.payload
                    }
                } else {
                    -1
                }
                if (index < 0) -1 else RingSlotListHeaderItems + index / gridColumns
            }
            QuickLauncherEditorAddTab.SHORTCUTS -> -1
        }
        if (target < 0) return@LaunchedEffect
        listState.scrollToItem(target)
        revealApplied = true
        // 程序化定位不经过 nestedScroll：不补这一步，顶栏会停在
        // "内容已经在中间、材质 alpha 仍是 0" 的透明态（和选择操作页同一个病根）。
        launch {
            listScrollBehavior.syncCollapseAfterProgrammaticScroll(
                canCollapse = { listState.canScrollBackward },
            )
        }
    }

    val navigateBack: () -> Unit = { page = RingSlotPage.Main }
    val handleBack: () -> Unit = {
        when {
            searchExpanded || searchQuery.isNotEmpty() -> {
                if (
                    !consumeExpandableSearchBack(
                        expanded = searchExpanded,
                        query = searchQuery,
                        onExpandedChange = { searchExpanded = it },
                        onQueryChange = { searchQuery = it },
                    )
                ) {
                    onBack()
                }
            }
            page != RingSlotPage.Main -> page = RingSlotPage.Main
            else -> onBack()
        }
    }
    LaunchedEffect(onBackHandlerReady, handleBack) { onBackHandlerReady?.invoke(handleBack) }
    BackHandler(onBack = handleBack)

    val searchHintResId = when (tabs[selectedTab]) {
        QuickLauncherEditorAddTab.ACTIONS -> R.string.search_actions_hint
        else -> R.string.search_hint
    }
    val slotTitle = stringResource(R.string.ring_launcher_slot_title, slotIndex + 1)
    val boundLabel = currentItem?.label?.ifBlank { currentItem.payload }.orEmpty()
    val subtitle = when (currentItem.ringSlotNoneMode()) {
        RingSlotNoneMode.TRANSPARENT -> stringResource(R.string.ring_slot_none_transparent)
        RingSlotNoneMode.AUTO_FILL -> stringResource(R.string.ring_slot_none_auto_fill)
        null -> stringResource(R.string.ring_launcher_slot_bound, boundLabel)
    }

    val currentPage = page
    when (currentPage) {
        RingSlotPage.Main -> {
            SettingsLazyScreenScaffold(
                title = slotTitle,
                subtitle = subtitle,
                modifier = Modifier.fillMaxSize(),
                enableBackHandler = false,
                listState = listState,
                scrollBehavior = listScrollBehavior,
                onBack = handleBack,
                actions = {
                    MiuixExpandableSearchIconAction(
                        expanded = searchExpanded,
                        query = searchQuery,
                        onExpandedChange = { searchExpanded = it },
                        onQueryChange = { searchQuery = it },
                    )
                },
                bottomContent = {
                    MiuixScaffoldSearchTabBottomContent(
                        searchExpanded = searchExpanded,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        focusRequester = searchFocusRequester,
                        hintResId = searchHintResId,
                        tabContent = {
                            AppPickerTabRow(
                                tabs = tabs.map { tab ->
                                    stringResource(
                                        when (tab) {
                                            QuickLauncherEditorAddTab.ACTIONS -> R.string.action_picker_tab_actions
                                            QuickLauncherEditorAddTab.APPS -> R.string.action_picker_tab_apps
                                            QuickLauncherEditorAddTab.SHORTCUTS -> R.string.action_picker_tab_shortcuts
                                        },
                                    )
                                },
                                selectedTabIndex = selectedTab,
                                onTabSelected = { selectedTab = it },
                            )
                        },
                    )
                },
            ) {
                when (tabs[selectedTab]) {
                    QuickLauncherEditorAddTab.ACTIONS -> {
                        quickLauncherAddPickerActionItems(
                            filtered = filteredActions,
                            configuredActionKeys = configuredActionKeys,
                            onToggleItem = { item, _ ->
                                if (item.panelIdOf() != null && panels.isNotEmpty()) {
                                    pendingPanelPick = item
                                    page = RingSlotPage.PanelPick
                                } else {
                                    onSelectItem(item)
                                }
                            },
                            onOpenExecuteShellCommand = { page = RingSlotPage.ShellCommand("") },
                            onOpenOpenLink = { page = RingSlotPage.OpenLink },
                            onOpenSimulateKeyEvent = { page = RingSlotPage.SimulateKeyEvent },
                            onRequestRunMacro = { onPicked -> pendingMacroPick = onPicked },
                            onRequestComposite = requestComposite,
                            onRequestOpenLink = requestOpenLink,
                            singleSelect = true,
                            noneSelected = currentItem == null || currentItem.payload.isBlank(),
                            onSelectNone = { onSelectItem(null) },
                            noneMode = currentItem.ringSlotNoneMode(),
                            onSelectNoneMode = onSelectNoneMode,
                            searchQuery = searchQuery,
                            columns = gridColumns,
                        )
                    }
                    QuickLauncherEditorAddTab.APPS -> {
                        quickLauncherAddPickerAppItems(
                            filtered = filteredApps,
                            configuredAppPackages = configuredAppPackages,
                            onToggle = { app, _ ->
                                onSelectItem(
                                    QuickLauncherItem.app(app.packageName, app.label, app.cloneUserId),
                                )
                            },
                            singleSelect = true,
                            columns = gridColumns,
                        )
                    }
                    QuickLauncherEditorAddTab.SHORTCUTS -> {
                        quickLauncherAddPickerShortcutItems(
                            searchQuery = searchQuery,
                            activityShortcuts = settings.activityShortcuts,
                            configuredShortcutKeys = configuredShortcutKeys,
                            filtered = filteredShortcuts,
                            appsByPackage = appsByPackage,
                            loading = loadedCatalog.loading,
                            scanProgress = loadedCatalog.scanProgress,
                            onCreateHostClick = { host ->
                                pendingCreateHost = host
                                runCatching { createLauncher.launch(host.createIntent()) }
                                    .onFailure { pendingCreateHost = null }
                            },
                            onToggle = { app, shortcut, _ ->
                                onSelectItem(shortcut.toQuickLauncherItem(app.packageName))
                            },
                            onToggleActivityShortcut = { item, _ -> onSelectItem(item) },
                            onBrowseActivityShortcut = { page = RingSlotPage.PickApp },
                            onOpenMyShortcuts = { page = RingSlotPage.MyShortcuts },
                            onOpenPresetShortcuts = { page = RingSlotPage.PresetShortcuts },
                            singleSelect = true,
                        )
                    }
                }
            }
        }

        RingSlotPage.PickApp -> ActivityShortcutPickAppScreen(
            onBack = navigateBack,
            onSelectApp = { app -> page = RingSlotPage.PickActivity(app.packageName) },
        )

        is RingSlotPage.PickActivity -> ActivityShortcutPickActivityScreen(
            packageName = currentPage.packageName,
            onBack = navigateBack,
            onSelectActivity = { activity ->
                onSelectItem(
                    QuickLauncherItem.shortcut(
                        componentFlat = "${activity.packageName}/${activity.className}",
                        label = activity.label,
                    ),
                )
            },
        )

        RingSlotPage.MyShortcuts -> MyShortcutsFolderScreen(
            activityShortcuts = settings.activityShortcuts,
            onBack = navigateBack,
            onBrowseNewShortcut = { page = RingSlotPage.PickApp },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, _ -> onSelectItem(item) },
        )

        RingSlotPage.PresetShortcuts -> PresetShortcutsFolderScreen(
            onBack = navigateBack,
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, _ -> onSelectItem(item) },
        )

        is RingSlotPage.ShellCommand -> GestureExecuteShellCommandScreen(
            initialCommand = currentPage.initialCommand,
            shellCommands = settings.shellCommands,
            onBack = navigateBack,
            onConfirm = { command ->
                onSelectItem(
                    QuickLauncherItem.action(
                        GestureAction.ExecuteShellCommand(command),
                        displayLabelForExecuteShellCommand(command, settings.shellCommands),
                    ),
                )
            },
        )

        RingSlotPage.OpenLink -> GestureOpenLinkScreen(
            initialUrl = "",
            onBack = navigateBack,
            onConfirm = { url, label ->
                val trimmedUrl = url.trim()
                val trimmedLabel = label.trim()
                onSelectItem(
                    QuickLauncherItem.action(
                        GestureAction.OpenLink(url = trimmedUrl, label = trimmedLabel),
                        trimmedLabel.ifBlank { trimmedUrl },
                    ),
                )
            },
        )

        RingSlotPage.SimulateKeyEvent -> GestureSimulateKeyEventScreen(
            initialAction = null,
            onBack = navigateBack,
            onConfirm = { keyEvent ->
                onSelectItem(
                    QuickLauncherItem.action(
                        keyEvent,
                        keyEvent.keyName.ifBlank {
                            KeyEventPresets.getDisplayName(localContext, keyEvent.keyCode)
                        },
                    ),
                )
            },
        )

        RingSlotPage.PanelPick -> {
            val title = stringResource(R.string.gesture_action_quick_launcher)
            SettingsLazyScreenScaffold(
                title = title,
                modifier = Modifier.fillMaxSize(),
                enableBackHandler = false,
                onBack = navigateBack,
            ) {
                itemsIndexed(panels, key = { _, panel -> panel.id }) { index, panel ->
                    Md3PickerListRow(
                        segmentIndex = index,
                        segmentCount = panels.size,
                        title = panelDisplayNames.getOrElse(index) { "" },
                        subtitle = pluralStringResource(
                            R.plurals.quick_launcher_panel_pick_summary,
                            panel.items.size,
                            panel.columnsPerPage,
                            panel.rowsPerPage,
                            panel.items.size,
                        ),
                        selected = panel.id == boundPanelId,
                        onClick = {
                            onSelectItem(
                                QuickLauncherItem.action(
                                    GestureAction.QuickLauncher(panel.id),
                                    quickLauncherActionLabel,
                                ),
                            )
                        },
                        leadingContent = {
                            Md3PickerIconLeading(
                                icon = gestureActionIcon(
                                    GestureAction.QuickLauncher(panel.id),
                                    outlined = true,
                                ),
                                selected = panel.id == boundPanelId,
                            )
                        },
                        trailingMode = PickerTrailingMode.Radio,
                    )
                }
            }
        }
    }

    val macroPick = pendingMacroPick
    MacroPickDialog(
        show = macroPick != null,
        onDismissRequest = { pendingMacroPick = null },
        onPick = { macro ->
            pendingMacroPick = null
            val action = GestureAction.RunMacro(macro.id)
            macroPick?.invoke(action)
            onSelectItem(QuickLauncherItem.action(action, macro.name))
        },
    )
}

private sealed interface RingSlotPage {
    data object Main : RingSlotPage
    data object PickApp : RingSlotPage
    data class PickActivity(val packageName: String) : RingSlotPage
    data object MyShortcuts : RingSlotPage
    data object PresetShortcuts : RingSlotPage
    data class ShellCommand(val initialCommand: String) : RingSlotPage
    data object OpenLink : RingSlotPage
    data object SimulateKeyEvent : RingSlotPage
    data object PanelPick : RingSlotPage
}

private fun QuickLauncherItem.panelIdOf(): String? {
    if (type != QuickLauncherItemType.ACTION) return null
    val action = QuickLauncherItemCodec.parseActionPayload(payload)
    return (action as? GestureAction.QuickLauncher)?.panelId
}

private fun initTabFor(item: QuickLauncherItem?): Int = when {
    item == null || item.payload.isBlank() -> 0
    item.type == QuickLauncherItemType.APP -> 1
    item.type == QuickLauncherItemType.SHORTCUT -> 2
    else -> 0
}

/**
 * 列表最前面由 `MiuixListScaffold(pageHint = null, subtitle = 非空)` 插入的 header item 数。
 * 对应 `settingsPageHeaderItems`：`pageHint ?: subtitle` 非空 → 1 个 page-hint item，
 * `pageHint != null && subtitle != null` 的第二个 item 本页不成立。
 */
private const val RingSlotListHeaderItems = 1

/**
 * 当前绑定动作在 ACTIONS 列表里的 **item 下标**（0 起，含列表开头的 header item）。
 *
 * 列表结构：每个分类 = 1 个标题 item + 1 张整节卡片 item
 * （见 `quickLauncherAddPickerActionItems`），卡片内部才按列排网格，
 * 所以定位目标是"目标分类那张卡片"，与网格列数无关。
 *
 * @return -1 = 当前列表里没有这个动作（被搜索过滤掉，或该动作已不存在）
 */
internal fun ringActionScrollItemIndex(
    actions: List<GestureAction>,
    key: String,
    headerItems: Int = RingSlotListHeaderItems,
): Int {
    if (actions.isEmpty()) return -1
    val sections = GestureActionCatalog.groupIntoSections(actions)
    sections.forEachIndexed { sectionIndex, section ->
        if (section.actions.any { QuickLauncherItemCodec.actionKey(it) == key }) {
            return headerItems + sectionIndex * 2 + 1
        }
    }
    return -1
}
