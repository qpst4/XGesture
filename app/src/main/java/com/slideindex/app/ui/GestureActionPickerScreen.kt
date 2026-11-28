package com.slideindex.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import com.slideindex.app.ui.miuix.MiuixAdaptiveDialog
import com.slideindex.app.ui.miuix.MiuixGlassSurfaceScope
import com.slideindex.app.ui.miuix.MiuixDialogActionsRow
import com.slideindex.app.ui.miuix.MiuixDialogAction
import com.slideindex.app.ui.miuix.LocalMiuixPageBackdrop
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.window.WindowDialog
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.freezer.FreezerOperations
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.LaunchWindowMode
import com.slideindex.app.gesture.QuickWheelAnchorMode
import com.slideindex.app.gesture.QuickWheelLaunchShape
import com.slideindex.app.gesture.launchShortcutFromCreated
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.gesturepicker.ActionPickerTab
import com.slideindex.app.ui.gesturepicker.actionPickerActionItems
import com.slideindex.app.ui.gesturepicker.actionPickerAppItems
import com.slideindex.app.ui.gesturepicker.actionPickerIndexOfCurrent
import com.slideindex.app.ui.gesturepicker.actionPickerShortcutIndexOfCurrent
import com.slideindex.app.ui.gesturepicker.calculateActionGridColumns
import com.slideindex.app.ui.gesturepicker.pickerTab
import com.slideindex.app.ui.gesturepicker.ActionPickerShortcutRow
import com.slideindex.app.ui.gesturepicker.actionPickerShortcutItems
import com.slideindex.app.ui.gesturepicker.GestureLaunchWindowModeDialog
import com.slideindex.app.ui.gesturepicker.MacroPickDialog
import com.slideindex.app.ui.gesturepicker.ShakeMenuPickDialog
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredActions
import com.slideindex.app.ui.gesturepicker.rememberActionPickerFilteredApps
import com.slideindex.app.ui.picker.AppPickerTabRow
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixScaffoldSearchTabBottomContent
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.miuix.syncCollapseAfterProgrammaticScroll
import com.slideindex.app.ui.picker.filterShortcutCatalog
import com.slideindex.app.ui.picker.rememberLoadedShortcutCatalog
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.widget.WidgetPanelDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** 定位到位后目标行"亮"一下的时长（毫秒）。 */
private const val HighlightPulseOnMs = 130

/** 两次闪烁之间的间隔时长（毫秒）。 */
private const val HighlightPulseOffMs = 110

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureActionPickerScreen(
    trigger: GestureTriggerType,
    current: GestureAction,
    onDismiss: () -> Unit,
    onSelect: (GestureAction) -> Unit,
    onOpenMyShortcuts: () -> Unit,
    onOpenPresetShortcuts: () -> Unit,
    onOpenPickApp: () -> Unit,
    onOpenExecuteShellCommand: (String) -> Unit,
    onOpenSimulateKeyEvent: (GestureAction.SimulateKeyEvent) -> Unit = {},
    includePointerGestureActions: Boolean = false,
    includeCornerInnerZoneActions: Boolean = false,
    pinNoneAtTop: Boolean = true,
    catalogPolicy: ActionPickerCatalogPolicy,
    /** 不进候选列表的动作类型；「晃动菜单」编辑页用它挡掉再套一层菜单造成的递归。 */
    excludeActionTypes: Set<GestureActionType> = emptySet(),
) {
    // 打开时先落在「当前已绑定动作」所属的那一页，而不是永远从第 0 页开始。
    var selectedTab by remember { mutableIntStateOf(current.pickerTab().ordinal) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
    val appSettings by extensionViewModel.settings.collectAsStateWithLifecycle()
    val activityShortcuts = appSettings.activityShortcuts
    val appRepository = rememberAppRepository()
    val context = LocalContext.current
    // 「声音模式 · 切换」的图标要跟着手机实际铃声状态走。
    val liveRingerMode = rememberSystemRingerMode()
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    val handleBack: () -> Unit = {
        if (
            !consumeExpandableSearchBack(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = { searchQuery = it }
            )
        ) {
            onDismiss()
        }
    }

    val shellConfigInitialCommand = remember(current) {
        (current as? GestureAction.ExecuteShellCommand)?.command.orEmpty()
    }
    val openLinkInitial = remember(current) {
        val link = current as? GestureAction.OpenLink
        link?.url.orEmpty() to link?.label.orEmpty()
    }
    var openLinkConfigVisible by remember { mutableStateOf(false) }
    val quickWheelInitial = remember(current) {
        val wheelAction = current as? GestureAction.QuickWheel
        Triple(
            wheelAction?.wheelId.orEmpty(),
            wheelAction?.shape ?: QuickWheelLaunchShape.DEFAULT,
            wheelAction?.manualSectorMask,
        )
    }
    var quickWheelConfigVisible by remember { mutableStateOf(false) }
    // 「组合动作」编辑弹窗：按顺序勾选要执行的动作。
    var compositePickVisible by remember { mutableStateOf(false) }
    val quickLauncherInitial = remember(current) {
        val ql = current as? GestureAction.QuickLauncher
        Triple(ql?.panelId.orEmpty(), ql?.pinned == true, ql?.pinCurrentApp == true)
    }
    var quickLauncherConfigVisible by remember { mutableStateOf(false) }
    // 「小组件面板」动作可以指定打开哪一页（有多个页时先弹选择）。
    val widgetPanelPages = remember(appSettings.widgetPanelPages) {
        WidgetPanelDefaults.effectivePages(appSettings.widgetPanelPages)
    }
    var widgetPanelPickVisible by remember { mutableStateOf(false) }
    val quickWheelInitialAnchorMode = remember(current) {
        (current as? GestureAction.QuickWheel)?.anchorMode ?: QuickWheelAnchorMode.TOUCH_DOWN
    }
    var pendingLaunchApp by remember { mutableStateOf<AppInfo?>(null) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        allApps = withContext(Dispatchers.IO) {
            val loaded = com.slideindex.app.overlay.CloneAppsIndex.enrich(appRepository.loadApps(force = true))
            FreezerOperations.applyFreezerStates(context, loaded, appSettings.freezerAppPackages)
        }
    }

    val filteredActions = rememberActionPickerFilteredActions(
        trigger = trigger,
        searchQuery = searchQuery,
        includePointerGestureActions = includePointerGestureActions,
        includeCornerInnerZoneActions = includeCornerInnerZoneActions,
        pinNoneAtTop = pinNoneAtTop,
        catalogPolicy = catalogPolicy,
        excludeActionTypes = excludeActionTypes,
    )
    val filteredApps = rememberActionPickerFilteredApps(allApps, searchQuery)
    val loadedCatalog = rememberLoadedShortcutCatalog(
        apps = allApps,
        enabled = selectedTab == ActionPickerTab.SHORTCUTS.ordinal,
    )
    val filteredShortcuts = remember(loadedCatalog.catalog, searchQuery) {
        filterShortcutCatalog(loadedCatalog.catalog, searchQuery)
    }
    // 分身与主实例同包名：动作/图标解析按包名查表时保留主实例，避免主应用显示成「· 分身」。
    val appsByPackage = remember(allApps) {
        allApps.groupBy { it.packageName }.mapValues { (_, apps) ->
            apps.firstOrNull { it.cloneUserId == 0 } ?: apps.first()
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val gridColumns = remember(configuration.screenWidthDp) {
        calculateActionGridColumns(configuration.screenWidthDp.dp)
    }

    // 「重新点进来选动作」时直接停在已绑定的那一项上，省掉从顶部一路滑。
    //
    // ⚠️ 本页没有 pageHint / subtitle，`MiuixListScaffold` 不会往列表前面插任何行
    // （见 `settingsPageHeaderItems`），所以下标可以直接用；将来若给本页加了 pageHint，
    // 这里的下标要跟着加上那几行。
    val pickerTargetItemIndex = remember(
        current,
        filteredActions,
        filteredApps,
        filteredShortcuts,
        loadedCatalog.loading,
        activityShortcuts,
        searchQuery,
        gridColumns,
    ) {
        when (current.pickerTab()) {
            ActionPickerTab.ACTIONS -> actionPickerIndexOfCurrent(filteredActions, current, gridColumns)
            ActionPickerTab.APPS -> (current as? GestureAction.LaunchApp)
                ?.let { app ->
                    val indexInList = filteredApps.indexOfFirst { it.packageName == app.packageName }
                    if (indexInList >= 0) indexInList / gridColumns else null
                }
            ActionPickerTab.SHORTCUTS -> actionPickerShortcutIndexOfCurrent(
                filtered = filteredShortcuts,
                activityShortcuts = activityShortcuts,
                searchQuery = searchQuery,
                loading = loadedCatalog.loading,
                current = current,
            )
        }
    }
    // 往上多留一行（自己的分组标题 / 上一项），别让目标贴着顶栏。
    // `rememberLazyListState` 只在首次组合读这个初值，之后用户怎么滚都不会被拽回去。
    val pickerListState = rememberLazyListState(
        initialFirstVisibleItemIndex = (pickerTargetItemIndex?.minus(1) ?: 0).coerceAtLeast(0),
    )
    // 顶栏折叠态（`TopAppBarState.heightOffset`）只由 nestedScroll 写；本页是程序化定位，
    // 所以要把 behavior 注入脚手架，定位完成后再同步一次折叠态（见下面的 LaunchedEffect）。
    val pickerScrollBehavior = MiuixScrollBehavior()
    // 应用 / 快捷方式目录是**异步到货**的：首帧 `filteredApps` / `filteredShortcuts` 还是空的，
    // 上面那个初值只能是 0，等列表到货后 `rememberLazyListState` 也不会再读初值 ——
    // 所以下标第一次变得可用时补一次定位。只在用户还没拖过列表时补，且只补一次。
    var pickerRevealApplied by remember { mutableStateOf(false) }
    var pickerUserScrolled by remember { mutableStateOf(false) }
    // ⚠️ 判"用户滚过"必须看**拖拽交互**，不能看 `firstVisibleItemIndex != 0`：
    // 动作页的目录是同步的，首帧 `initialFirstVisibleItemIndex` 就把它定位好了，
    // 用下标判断会把这种情况误当成"用户滚过了"，于是那一页永远不会闪。
    // 程序化的 `scrollToItem` 不会发 DragInteraction，所以这里也不会误判自己。
    LaunchedEffect(pickerListState) {
        pickerListState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) pickerUserScrolled = true
        }
    }
    // 定位到位后让目标那一行的卡片闪几下，帮眼睛找到它（只闪 3 下，不循环）。
    val pickerHighlightPulse = remember { Animatable(0f) }
    LaunchedEffect(pickerTargetItemIndex) {
        val target = pickerTargetItemIndex ?: return@LaunchedEffect
        if (pickerRevealApplied || pickerUserScrolled) return@LaunchedEffect
        pickerRevealApplied = true
        // 同步就绪的 tab 首帧已经在这个位置了，再滚一次是幂等的（不会抖）。
        val targetIndex = (target - 1).coerceAtLeast(0)
        pickerListState.scrollToItem(targetIndex)
        // 程序化滚动不经过 nestedScroll：`TopAppBarState.heightOffset` 仍是 0，而玻璃顶栏
        // 材质带（band + 渐进模糊 + scrim）的整层 alpha = collapsedFraction = heightOffset / limit，
        // 于是顶栏会停在「展开 + 没有底色」——看起来透明，要等用户真的滑一下才恢复。
        // 所以定位完成后在这里把折叠态同步一次（miuix 示例跳到搜索结果后也是手动同步的）。
        //
        // 两个坑：
        // ① `heightOffsetLimit` 由首帧 layout 从大标题高度写回，初值是 `-Float.MAX_VALUE` 哨兵，
        //    必须等它离开哨兵再写；否则 heightOffset 会落在荒谬值上，之后「滚到顶就展开」都回不来。
        // ② 宽屏（`expandable = false`）时 limit 恒为 0，这里直接跳过，宽屏行为零变化。
        launch {
            if (targetIndex <= 0) return@launch
            pickerScrollBehavior.syncCollapseAfterProgrammaticScroll(
                canCollapse = { !pickerUserScrolled },
            )
        }
        // 先滚到位再闪（瞬时滚 + 闪），时序确定：不会出现"还在滚动时已经闪完了"。
        // 动画时长遵循系统的"动画时长缩放"，关掉动画时自动跳过；闪 2 次节奏干脆，不拖沓。
        repeat(2) {
            pickerHighlightPulse.animateTo(1f, tween(HighlightPulseOnMs))
            pickerHighlightPulse.animateTo(0f, tween(HighlightPulseOffMs))
        }
        pickerHighlightPulse.snapTo(0f)
    }
    var pendingCreateHost by remember { mutableStateOf<AppShortcutLoader.CreateShortcutHost?>(null) }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val host = pendingCreateHost
        pendingCreateHost = null
        if (result.resultCode != Activity.RESULT_OK || host == null) return@rememberLauncherForActivityResult
        val created = AppShortcutLoader.parseCreateShortcutResult(host.packageName, result.data, context)
            ?: return@rememberLauncherForActivityResult
        val iconPath = com.slideindex.app.activity.ShortcutIconCapture.captureFromIntent(
            context,
            created.iconProviderIntent ?: created.shortcutIntent,
        )
        val createdAction = launchShortcutFromCreated(created)
        // 图标跟着这条快捷方式走：按 payloadKey 存覆盖表（不写应用内直达目录）。
        com.slideindex.app.activity.ShortcutIconOverrideStore.put(
            context,
            createdAction.payloadKey,
            iconPath,
        )
        onSelect(createdAction)
    }

    var hasSelected by remember { mutableStateOf(false) }
    // 「运行宏」要先挑一条宏：这里只为它开一个二级选择弹窗，选中后再真正回调出去。
    var macroPickVisible by remember { mutableStateOf(false) }
    // 「晃动菜单」同理：先选一个已建好的菜单，再回填成带 menuId 的动作。
    var shakeMenuPickVisible by remember { mutableStateOf(false) }
    val safeSelect: (GestureAction) -> Unit = { action ->
        if (action is GestureAction.RunMacro && action.macroId.isBlank()) {
            macroPickVisible = true
        } else if (action is GestureAction.OpenShakeMenu && action.menuId.isBlank()) {
            // 只在「还没选菜单」时开弹窗；选完回填的这份 action 必须落到下面的 onSelect，
            // 否则会弹窗重开、永远绑不上。
            shakeMenuPickVisible = true
        } else if (!hasSelected) {
            hasSelected = true
            onSelect(action)
        }
    }

    val searchHintResId = when (ActionPickerTab.entries[selectedTab]) {
        ActionPickerTab.ACTIONS -> R.string.search_actions_hint
        ActionPickerTab.APPS, ActionPickerTab.SHORTCUTS -> R.string.search_hint
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        SettingsLazyScreenScaffold(
            title = stringResource(R.string.slot_pick_action),
            onBack = handleBack,
            modifier = Modifier.fillMaxSize(),
            listState = pickerListState,
            scrollBehavior = pickerScrollBehavior,
            actions = {
                MiuixExpandableSearchIconAction(
                    expanded = searchExpanded,
                    query = searchQuery,
                    onExpandedChange = { searchExpanded = it },
                    onQueryChange = { searchQuery = it }
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
                            tabs = listOf(
                                stringResource(R.string.action_picker_tab_actions),
                                stringResource(R.string.action_picker_tab_apps),
                                stringResource(R.string.action_picker_tab_shortcuts)
                            ),
                            selectedTabIndex = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )
                    }
                )
            }
        ) {
            when (ActionPickerTab.entries[selectedTab]) {
                ActionPickerTab.ACTIONS -> {
                    actionPickerActionItems(
                        filtered = filteredActions,
                        current = current,
                        onSelect = { picked ->
                            if (picked is GestureAction.Composite) {
                                compositePickVisible = true
                            } else {
                                safeSelect(picked)
                            }
                        },
                        onOpenExecuteShellCommand = {
                            onOpenExecuteShellCommand(shellConfigInitialCommand)
                        },
                        onOpenOpenLink = { openLinkConfigVisible = true },
                        onOpenSimulateKeyEvent = {
                            onOpenSimulateKeyEvent(
                                current as? GestureAction.SimulateKeyEvent ?: GestureAction.SimulateKeyEvent()
                            )
                        },
                        onOpenQuickWheel = { quickWheelConfigVisible = true },
                        onOpenQuickLauncher = { quickLauncherConfigVisible = true },
                        onOpenWidgetPanel = {
                            val only = widgetPanelPages.singleOrNull()
                            if (only != null) {
                                // 只有一页时不必多问一步，保持「点一下就好」。
                                safeSelect(GestureAction.WidgetPopupOverlay(only.id.toString()))
                            } else {
                                widgetPanelPickVisible = true
                            }
                        },
                        highlightPulse = pickerHighlightPulse.value,
                        liveRingerMode = liveRingerMode,
                        columns = gridColumns,
                    )
                }
                ActionPickerTab.APPS -> {
                    actionPickerAppItems(
                        filtered = filteredApps,
                        current = current,
                        onSelect = { app -> pendingLaunchApp = app },
                        highlightPulse = pickerHighlightPulse.value,
                        columns = gridColumns,
                    )
                }
                ActionPickerTab.SHORTCUTS -> {
                    actionPickerShortcutItems(
                        appsByPackage = appsByPackage,
                        searchQuery = searchQuery,
                        current = current,
                        onSelect = safeSelect,
                        activityShortcuts = activityShortcuts,
                        onOpenMyShortcuts = onOpenMyShortcuts,
                        onOpenPresetShortcuts = onOpenPresetShortcuts,
                        onBrowseActivityShortcut = onOpenPickApp,
                        filtered = filteredShortcuts,
                        loading = loadedCatalog.loading,
                        scanProgress = loadedCatalog.scanProgress,
                        onCreateHostClick = { host ->
                            pendingCreateHost = host
                            runCatching { createLauncher.launch(host.createIntent()) }
                                .onFailure { pendingCreateHost = null }
                        },
                        shortcutRowContent = { group, shortcut, segmentIndex, segmentCount ->
                            ActionPickerShortcutRow(
                                shortcut = shortcut,
                                packageName = group.app.packageName,
                                segmentIndex = segmentIndex,
                                segmentCount = segmentCount,
                                current = current,
                                onSelect = safeSelect,
                                highlightPulse = pickerHighlightPulse.value,
                            )
                        }
                    )
                }
            }
        }

        if (openLinkConfigVisible) {
            OpenLinkMiuixDialog(
                initialUrl = openLinkInitial.first,
                initialLabel = openLinkInitial.second,
                onDismissRequest = { openLinkConfigVisible = false },
                onConfirm = { url, label ->
                    openLinkConfigVisible = false
                    safeSelect(GestureAction.OpenLink(url = url, label = label))
                },
            )
        }

        if (compositePickVisible) {
            val pickContext = LocalContext.current
            val presets = remember(compositePickVisible) {
                com.slideindex.app.ui.composite.CompositeActionPresetStore.load(pickContext)
            }

            val dialogTitle = stringResource(R.string.gesture_action_composite)
            val body: @Composable () -> Unit = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (presets.isEmpty()) {
                        Text(
                            text = stringResource(R.string.composite_pick_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            presets.forEach { preset ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            compositePickVisible = false
                                            safeSelect(GestureAction.CompositeRef(preset.id))
                                        }
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = preset.name.ifBlank {
                                            stringResource(R.string.composite_library_untitled)
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.composite_library_action_count,
                                            preset.actions.size,
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    MiuixDialogActionsRow(
                        actions = listOf(
                            MiuixDialogAction(
                                text = stringResource(android.R.string.cancel),
                                onClick = { compositePickVisible = false },
                            ),
                        ),
                    )
                }
            }
            MiuixAdaptiveDialog(
                onDismissRequest = { compositePickVisible = false },
                title = dialogTitle,
                content = body,
            )
        }

    if (quickWheelConfigVisible) {
            Dialog(
                onDismissRequest = { quickWheelConfigVisible = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                GestureQuickWheelActionScreen(
                    wheels = appSettings.launcher.quickWheels,
                    initialWheelId = quickWheelInitial.first,
                    initialShape = quickWheelInitial.second,
                    initialSectorMask = quickWheelInitial.third,
                    initialAnchorMode = quickWheelInitialAnchorMode,
                    onBack = { quickWheelConfigVisible = false },
                    onConfirm = { wheelId, shape, manualSectorMask, anchorMode ->
                        quickWheelConfigVisible = false
                        safeSelect(
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
        }

        if (quickLauncherConfigVisible) {
            Dialog(
                onDismissRequest = { quickLauncherConfigVisible = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                GestureQuickLauncherActionScreen(
                    panels = appSettings.quickLauncherPanels,
                    initialPanelId = quickLauncherInitial.first,
                    initialPinned = quickLauncherInitial.second,
                    initialPinCurrentApp = quickLauncherInitial.third,
                    onBack = { quickLauncherConfigVisible = false },
                    onConfirm = { panelId, pinned, pinCurrentApp ->
                        quickLauncherConfigVisible = false
                        safeSelect(
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
        }

        // 对话框常驻组合，只由 show 驱动，关闭时才有退出动画。
        com.slideindex.app.ui.gesturepicker.WidgetPanelPagePickDialog(
            show = widgetPanelPickVisible,
            pages = widgetPanelPages,
            selectedPageId = (current as? GestureAction.WidgetPopupOverlay)?.panelId,
            onDismissRequest = { widgetPanelPickVisible = false },
            onPick = { page ->
                widgetPanelPickVisible = false
                safeSelect(GestureAction.WidgetPopupOverlay(page.id.toString()))
            },
        )

        pendingLaunchApp?.let { app ->
            val currentLaunchApp = current as? GestureAction.LaunchApp
            GestureLaunchWindowModeDialog(
                app = app,
                initialMode = currentLaunchApp
                    ?.takeIf { it.packageName == app.packageName && it.cloneUserId == app.cloneUserId }
                    ?.windowMode
                    ?: LaunchWindowMode.FOLLOW_GLOBAL,
                onDismiss = { pendingLaunchApp = null },
                onConfirm = { mode ->
                    pendingLaunchApp = null
                    safeSelect(GestureAction.LaunchApp(app.packageName, mode, app.cloneUserId))
                },
            )
        }

        MacroPickDialog(
            show = macroPickVisible,
            onDismissRequest = { macroPickVisible = false },
            onPick = { macro ->
                macroPickVisible = false
                safeSelect(GestureAction.RunMacro(macro.id))
            },
            // 已经绑着某条宏时，把它标出来（换宏时不用自己在列表里找）。
            selectedMacroId = (current as? GestureAction.RunMacro)?.macroId,
        )

        // 对话框常驻组合，只由 show 驱动，关闭时才有退出动画。
        ShakeMenuPickDialog(
            show = shakeMenuPickVisible,
            menus = appSettings.shakeGestureSettings.menus,
            selectedMenuId = (current as? GestureAction.OpenShakeMenu)?.menuId,
            onDismissRequest = { shakeMenuPickVisible = false },
            onPick = { menu ->
                shakeMenuPickVisible = false
                safeSelect(GestureAction.OpenShakeMenu(menu.id))
            },
        )
    }
}


/** 「打开链接」的 Miuix 弹窗：链接 + 名称两个输入框。 */
@Composable
private fun OpenLinkMiuixDialog(
    initialUrl: String,
    initialLabel: String,
    onDismissRequest: () -> Unit,
    onConfirm: (url: String, label: String) -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var label by remember(initialLabel) { mutableStateOf(initialLabel) }
    val canSave = url.trim().isNotBlank()
    val dialogTitle = stringResource(R.string.gesture_action_open_link)

    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            MiuixLabeledTextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.gesture_open_link_url_field),
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            MiuixLabeledTextField(
                value = label,
                onValueChange = { label = it },
                label = stringResource(R.string.gesture_open_link_label_field),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixDialogActionsRow(
                    actions = listOf(
                        MiuixDialogAction(
                            text = stringResource(android.R.string.cancel),
                            onClick = onDismissRequest,
                        ),
                        MiuixDialogAction(
                            text = stringResource(R.string.composite_library_save),
                            onClick = { onConfirm(url.trim(), label.trim()) },
                            enabled = canSave,
                            primary = true,
                        ),
                    ),
                )
            }
        }
    }

    MiuixAdaptiveDialog(
        onDismissRequest = onDismissRequest,
        title = dialogTitle,
        content = body,
    )
}
