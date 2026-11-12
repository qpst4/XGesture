package com.slideindex.app.ui.ringlauncher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.miuix.MiuixContentScaffold
import com.slideindex.app.ui.miuix.MiuixExpandableSearchFieldStrip
import com.slideindex.app.ui.miuix.MiuixExpandableSearchIconAction
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.miuix.consumeExpandableSearchBack
import com.slideindex.app.ui.pickerListSegmentedGap
import com.slideindex.app.ui.quicklauncher.QuickLauncherEmbedParentConfirm
import com.slideindex.app.ui.quicklauncher.QUICK_LAUNCHER_SHEET_ENTER_MS
import com.slideindex.app.ui.quicklauncher.QUICK_LAUNCHER_SHEET_EXIT_MS
import com.slideindex.app.ui.quicklauncher.QuickLauncherAddOverlaySheetBody
import com.slideindex.app.ui.gesturepicker.MacroPickOverlay
import com.slideindex.app.ui.quicklauncher.QuickLauncherAddSubScreen
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.AppShortcutLoader.CreatedShortcut
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.MiuixIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingLauncherSlotConfigSheet(
    slotIndex: Int,
    currentItem: QuickLauncherItem?,
    apps: List<AppInfo>,
    activityShortcuts: List<ActivityShortcut> = emptyList(),
    shellCommands: List<ShellCommand> = emptyList(),
    quickLauncherPanels: List<QuickLauncherPanel> = emptyList(),
    onDismiss: () -> Unit,
    onSelectItem: (QuickLauncherItem?) -> Unit,
    onOpenCustomIconEditor: () -> Unit = {},
    launchCreateShortcut: (
        AppShortcutLoader.CreateShortcutHost,
        (CreatedShortcut?) -> Unit,
    ) -> Unit = { _, _ -> },
    /**
     * 宿主是 Activity 时用：把内部「返回一步」（先退子页、再收起）交给宿主注册系统返回键。
     * 浮层宿主走 `OverlayComposeDialogHost` 的 onBackPressed，不需要这个。
     */
    onBackHandlerReady: ((() -> Unit) -> Unit)? = null,
    /**
     * true = 整页宿主（透明 Activity）用：去掉遮罩 / 82% 高度 / 底部圆角 / 点外部关闭，
     * 铺满窗口并让出状态栏；浮层兜底仍用默认的 sheet 形态。
     */
    pageMode: Boolean = false,
) {
    var visible by remember { mutableStateOf(pageMode) }
    var subScreen by remember { mutableStateOf<QuickLauncherAddSubScreen>(QuickLauncherAddSubScreen.Main) }
    var panelPickVisible by remember { mutableStateOf(false) }
    // 「手势宏」要先挑一条宏再落库（本面板是悬浮窗，选择层必须画在界面里，不能用平台 Dialog）。
    var pendingMacroPick by remember { mutableStateOf<((GestureAction) -> Unit)?>(null) }
    var embedParentConfirm by remember { mutableStateOf<QuickLauncherEmbedParentConfirm?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val slotBound = currentItem != null && currentItem.payload.isNotBlank()
    val slotEmpty = !slotBound
    // 当前绑定项在三个 tab 里的 key：用于打开时定位 + 选中高亮（与边角轮盘行为一致）。
    val focusActionKey = currentItem
        ?.takeIf { it.type == QuickLauncherItemType.ACTION }
        ?.let { QuickLauncherItemCodec.parseActionPayload(it.payload) }
        ?.let(QuickLauncherItemCodec::actionKey)
    val focusAppKey = currentItem
        ?.takeIf { it.type == QuickLauncherItemType.APP }
        ?.payload
        ?.takeIf { it.isNotBlank() }
    val focusShortcutKey = currentItem
        ?.takeIf { it.type == QuickLauncherItemType.SHORTCUT }
        ?.let { QuickLauncherItemCodec.shortcutItemKey(it) }
    val focusItemKey = focusActionKey ?: focusAppKey ?: focusShortcutKey
    var focusTabApplied by remember { mutableStateOf(false) }
    LaunchedEffect(currentItem) {
        if (!focusTabApplied && currentItem != null) {
            selectedTab = when (currentItem.type) {
                QuickLauncherItemType.APP -> 1
                QuickLauncherItemType.SHORTCUT -> 2
                else -> 0
            }
            focusTabApplied = true
        }
    }
    val customIconTitle = stringResource(R.string.animation_style_custom_icon)
    val quickLauncherActionLabel = stringResource(R.string.gesture_action_quick_launcher)
    val panels = remember(quickLauncherPanels) { QuickLauncherPanelDefaults.effectivePanels(quickLauncherPanels) }
    val boundQuickLauncherPanelId = currentItem?.quickLauncherPanelId()
    val selectedPanelId = boundQuickLauncherPanelId?.let { QuickLauncherPanelDefaults.resolvePanelId(panels, it) }

    // 整页模式不走「隐藏 → 等动画 → 回调」，直接交给宿主关闭（少一帧空白）。
    val requestDismiss: () -> Unit = if (pageMode) {
        { onDismiss() }
    } else {
        { visible = false }
    }

    val handleBack: () -> Unit = {
        when {
            panelPickVisible -> panelPickVisible = false
            subScreen == QuickLauncherAddSubScreen.Main -> {
                if (
                    !consumeExpandableSearchBack(
                        expanded = searchExpanded,
                        query = searchQuery,
                        onExpandedChange = { searchExpanded = it },
                        onQueryChange = { searchQuery = it },
                    )
                ) {
                    requestDismiss()
                }
            }
            subScreen == QuickLauncherAddSubScreen.PickApp -> subScreen = QuickLauncherAddSubScreen.Main
            subScreen is QuickLauncherAddSubScreen.PickActivity -> subScreen = QuickLauncherAddSubScreen.PickApp
            subScreen is QuickLauncherAddSubScreen.ShellCommandConfig -> subScreen = QuickLauncherAddSubScreen.Main
            subScreen is QuickLauncherAddSubScreen.OpenLinkConfig -> subScreen = QuickLauncherAddSubScreen.Main
            subScreen is QuickLauncherAddSubScreen.SimulateKeyEventConfig -> subScreen = QuickLauncherAddSubScreen.Main
            subScreen == QuickLauncherAddSubScreen.CreateFolder -> subScreen = QuickLauncherAddSubScreen.Main
            subScreen == QuickLauncherAddSubScreen.MyShortcuts -> subScreen = QuickLauncherAddSubScreen.Main
            subScreen == QuickLauncherAddSubScreen.PresetShortcuts -> subScreen = QuickLauncherAddSubScreen.Main
        }
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(onBackHandlerReady) {
        onBackHandlerReady?.invoke(handleBack)
    }

    LaunchedEffect(visible) {
        if (!visible) {
            if (!pageMode) delay(QUICK_LAUNCHER_SHEET_EXIT_MS.toLong())
            onDismiss()
        }
    }

    val searchHintResId = when (selectedTab) {
        0 -> R.string.search_actions_hint
        else -> R.string.search_hint
    }

    val isFolderSubScreen =
        subScreen is QuickLauncherAddSubScreen.MyShortcuts ||
            subScreen is QuickLauncherAddSubScreen.PresetShortcuts
    val showPickerChrome =
        !panelPickVisible &&
            (subScreen == QuickLauncherAddSubScreen.Main || isFolderSubScreen)
    val panelDisplayNames = buildList {
        panels.forEachIndexed { index, panel ->
            add(
                panel.name.ifBlank {
                    stringResource(R.string.quick_launcher_panel_default_name, index + 1)
                },
            )
        }
    }
    val title = when {
        panelPickVisible -> stringResource(R.string.quick_launcher_panel_pick_title)
        subScreen == QuickLauncherAddSubScreen.Main -> stringResource(
            R.string.ring_launcher_slot_title,
            slotIndex + 1,
        )
        subScreen == QuickLauncherAddSubScreen.MyShortcuts ->
            stringResource(R.string.quick_launcher_my_shortcuts)
        subScreen == QuickLauncherAddSubScreen.PresetShortcuts ->
            stringResource(R.string.quick_launcher_preset_shortcuts)
        subScreen == QuickLauncherAddSubScreen.PickApp ->
            stringResource(R.string.activity_shortcut_pick_app_title)
        subScreen is QuickLauncherAddSubScreen.PickActivity ->
            stringResource(R.string.search_engine_pick_activity_title)
        subScreen is QuickLauncherAddSubScreen.ShellCommandConfig ->
            stringResource(R.string.gesture_shell_command_config_title)
        subScreen is QuickLauncherAddSubScreen.OpenLinkConfig ->
            stringResource(R.string.gesture_action_open_link)
        subScreen is QuickLauncherAddSubScreen.SimulateKeyEventConfig ->
            stringResource(R.string.gesture_action_simulate_key_event)
        subScreen == QuickLauncherAddSubScreen.CreateFolder ->
            stringResource(R.string.quick_launcher_create_folder)
        else -> stringResource(R.string.ring_launcher_slot_title, slotIndex + 1)
    }
    val statusText = if (slotBound) {
        val boundLabel = if (currentItem.quickLauncherPanelId() != null) {
            val panelIndex = panels.indexOfFirst { it.id == selectedPanelId }.coerceAtLeast(0)
            stringResource(
                R.string.gesture_action_quick_launcher_named,
                panelDisplayNames.getOrElse(panelIndex) { "" },
            )
        } else {
            currentItem.label.ifBlank { currentItem.payload }
        }
        stringResource(R.string.ring_launcher_slot_bound, boundLabel)
    } else {
        stringResource(R.string.ring_launcher_slot_unconfigured)
    }

    val headerActions: @Composable RowScope.() -> Unit = {
        if (slotBound && !panelPickVisible && subScreen == QuickLauncherAddSubScreen.Main) {
            IconButton(onClick = onOpenCustomIconEditor) {
                Icon(
                    imageVector = MiuixIcons.Medium.Image,
                    contentDescription = customIconTitle,
                )
            }
        }
        if (showPickerChrome) {
            MiuixExpandableSearchIconAction(
                expanded = searchExpanded,
                query = searchQuery,
                onExpandedChange = { searchExpanded = it },
                onQueryChange = { searchQuery = it },
            )
        }
        embedParentConfirm?.let { confirm ->
            IconButton(onClick = confirm.onConfirm, enabled = confirm.enabled) {
                Icon(
                    imageVector = MiuixIcons.Medium.Ok,
                    contentDescription = stringResource(R.string.confirm),
                )
            }
        }
    }

    val headerBottom: @Composable () -> Unit = {
        if (showPickerChrome) {
            MiuixExpandableSearchFieldStrip(
                expanded = searchExpanded,
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                focusRequester = searchFocusRequester,
                hintResId = searchHintResId,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        if (!panelPickVisible && subScreen == QuickLauncherAddSubScreen.Main) {
            MiuixTabRowWithContour(
                tabs = listOf(
                    stringResource(R.string.action_picker_tab_actions),
                    stringResource(R.string.action_picker_tab_apps),
                    stringResource(R.string.action_picker_tab_shortcuts),
                ),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                contourHost = MiuixTabRowContourHost.SurfaceContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }

    val bodyContent: @Composable (Modifier) -> Unit = { bodyModifier ->
        Box(modifier = bodyModifier) {
            if (panelPickVisible) {
                QuickLauncherPanelPickList(
                    panels = panels,
                    panelDisplayNames = panelDisplayNames,
                    selectedPanelId = selectedPanelId,
                    onSelect = { panel ->
                        onSelectItem(
                            QuickLauncherItem.action(
                                GestureAction.QuickLauncher(panel.id),
                                quickLauncherActionLabel,
                            ),
                        )
                        requestDismiss()
                    },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    QuickLauncherAddOverlaySheetBody(
                        modifier = Modifier.fillMaxSize(),
                        padding = PaddingValues(0.dp),
                        nestedScrollConnection = null,
                        searchQuery = searchQuery,
                        apps = apps,
                        addedAppPackages = focusAppKey?.let { setOf(it) }.orEmpty(),
                        addedShortcutKeys = focusShortcutKey?.let { setOf(it) }.orEmpty(),
                        addedActionKeys = focusActionKey?.let { setOf(it) }.orEmpty(),
                        activityShortcuts = activityShortcuts,
                        shellCommands = shellCommands,
                        onToggle = { item, _ ->
                            if (item.quickLauncherPanelId() != null && panels.isNotEmpty()) {
                                // 圆环槽位不放「没挑面板」的快速启动器：先让用户选面板再落库。
                                panelPickVisible = true
                            } else {
                                onSelectItem(item)
                                requestDismiss()
                            }
                        },
                        launchCreateShortcut = launchCreateShortcut,
                        subScreen = subScreen,
                        onSubScreenChange = { subScreen = it },
                        selectedTab = selectedTab,
                        singleSelect = true,
                        focusItemKey = focusItemKey,
                        pinNoneAtTop = true,
                        slotEmpty = slotEmpty,
                        onClearSlot = {
                            onSelectItem(null)
                            requestDismiss()
                        },
                        onReportEmbedParentConfirm = { embedParentConfirm = it },
                        onRequestRunMacro = { onPicked -> pendingMacroPick = onPicked },
                    )

                    val macroPick = pendingMacroPick
                    if (macroPick != null) {
                        MacroPickOverlay(
                            onDismissRequest = { pendingMacroPick = null },
                            onPick = { macro ->
                                pendingMacroPick = null
                                macroPick(GestureAction.RunMacro(macro.id))
                            },
                        )
                    }
                }
            }
        }
    }

    if (pageMode) {
        MiuixContentScaffold(
            title = title,
            onBack = handleBack,
            actions = headerActions,
            bottomContent = headerBottom,
        ) {
            bodyContent(Modifier.fillMaxSize())
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = requestDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(QUICK_LAUNCHER_SHEET_ENTER_MS)),
                exit = fadeOut(tween(QUICK_LAUNCHER_SHEET_EXIT_MS)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                )
            }

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight / 2 },
                    animationSpec = tween(QUICK_LAUNCHER_SHEET_ENTER_MS),
                ) + fadeIn(tween(QUICK_LAUNCHER_SHEET_ENTER_MS)),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight / 2 },
                    animationSpec = tween(QUICK_LAUNCHER_SHEET_EXIT_MS),
                ) + fadeOut(tween(QUICK_LAUNCHER_SHEET_EXIT_MS)),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.82f)
                        .imePadding()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 16.dp,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (panelPickVisible || subScreen != QuickLauncherAddSubScreen.Main) {
                                IconButton(onClick = handleBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_navigate_back),
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (!panelPickVisible && subScreen == QuickLauncherAddSubScreen.Main) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            headerActions()
                        }

                        headerBottom()

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        bodyContent(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}


/**
 * 圆环槽位里「打开快速启动器」的面板选择列表。
 *
 * 侧边手势槽位早就有这一步，圆环这边缺了它，才会出现「选了快速启动器但没选面板」的槽位。
 */
@Composable
private fun QuickLauncherPanelPickList(
    panels: List<QuickLauncherPanel>,
    panelDisplayNames: List<String>,
    selectedPanelId: String?,
    onSelect: (QuickLauncherPanel) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(pickerListSegmentedGap()),
    ) {
        itemsIndexed(panels, key = { _, panel -> panel.id }) { index, panel ->
            val selected = panel.id == selectedPanelId
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
                selected = selected,
                onClick = { onSelect(panel) },
                leadingContent = {
                    Md3PickerIconLeading(
                        icon = gestureActionIcon(GestureAction.QuickLauncher(panel.id), outlined = true),
                        selected = selected,
                    )
                },
                trailingMode = PickerTrailingMode.Radio,
            )
        }
    }
}

/** 条目若是「打开快速启动器」动作，返回其面板 id（可能是空串，表示旧的未指定状态）。 */
private fun QuickLauncherItem.quickLauncherPanelId(): String? {
    if (type != QuickLauncherItemType.ACTION) return null
    val action = QuickLauncherItemCodec.parseActionPayload(payload)
    return (action as? GestureAction.QuickLauncher)?.panelId
}
