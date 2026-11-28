package com.slideindex.app.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.launcher.resolveFolderItems
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureOpenLinkScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.GlobalIconStore
import com.slideindex.app.ui.QuickLauncherEditorScreen
import com.slideindex.app.ui.displayLabelForExecuteShellCommand
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.quicklauncher.QuickLauncherAddPickerScreen
import com.slideindex.app.ui.quicklauncher.QuickLauncherCreateFolderScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.QuickLauncherEditorViewModel

/**
 * 刚从列表页新建、还没被改过的面板（返回列表时若仍未改动就回收）。
 *
 * 新建是先落盘再进编辑器（编辑器 / 添加页都按 id 读面板），所以只能事后判断：
 * 名称、条目、行列都和创建时一模一样，且没设过自定义图标，就当作没建过。
 */
private var provisionalNewPanel: QuickLauncherPanel? = null

private fun discardProvisionalPanelIfUntouched(
    currentPanels: List<QuickLauncherPanel>,
    panelId: String,
    writer: ExtensionSettingsViewModel,
    /** 只设过自定义图标也算“动过”：图标在 GlobalIconStore，不在面板数据里。 */
    hasCustomIcon: Boolean,
) {
    val snapshot = provisionalNewPanel ?: return
    if (snapshot.id != panelId) return
    provisionalNewPanel = null
    val current = currentPanels.firstOrNull { it.id == panelId }
    if (current == snapshot && !hasCustomIcon) {
        writer.setQuickLauncherPanels(currentPanels.filterNot { it.id == panelId })
    }
}

fun NavEntryBuilder.quickLauncherNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.QuickLauncher> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val panels = QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels)
        // 系统返回 / 侧滑可能绕过编辑页的 onBack：回到列表时再兜一次底。
        LaunchedEffect(Unit) {
            val snapshot = provisionalNewPanel ?: return@LaunchedEffect
            if (ctx.backStack.currentAppNavKey() != AppNavKey.QuickLauncher) return@LaunchedEffect
            discardProvisionalPanelIfUntouched(
                currentPanels = panels,
                panelId = snapshot.id,
                writer = viewModel,
                hasCustomIcon = GlobalIconStore.panelIcon(ctx.activity, snapshot.id) != null,
            )
        }
        com.slideindex.app.ui.quicklauncher.QuickLauncherListScreen(
            panels = panels,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onCreate = {
                val updated = com.slideindex.app.launcher.QuickLauncherPanelMutator.addPanel(
                    panels = panels,
                    defaultColumns = settings.quickLauncherColumnsPerPage,
                    defaultRows = settings.quickLauncherRowsPerPage,
                )
                val newPanel = updated?.lastOrNull()
                if (newPanel != null) {
                    // 记下新建时的内容：原样返回就回收，改过（条目/名称/行列）才留下。
                    provisionalNewPanel = newPanel
                    viewModel.setQuickLauncherPanels(updated)
                    ctx.navigate(AppNavKey.QuickLauncherEdit(newPanel.id))
                }
            },
            onOpen = { panelId ->
                provisionalNewPanel = null
                ctx.navigate(AppNavKey.QuickLauncherEdit(panelId))
            },
            // 拖动排序：松手一次写回整份顺序。
            onReorder = { updated -> viewModel.setQuickLauncherPanels(updated) },
            onDuplicate = { panelId ->
                com.slideindex.app.launcher.QuickLauncherPanelMutator.duplicatePanel(panels, panelId)
                    ?.let(viewModel::setQuickLauncherPanels)
            },
            onDelete = { panelId ->
                com.slideindex.app.launcher.QuickLauncherPanelMutator.removePanel(panels, panelId)
                    ?.let(viewModel::setQuickLauncherPanels)
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherEdit> { key ->
        val viewModel: QuickLauncherEditorViewModel = hiltViewModel()
        // 回收写盘用 activity 级 VM：编辑页 entry 的 VM 会随出栈销毁，写盘可能被取消。
        val listViewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)

        fun discardIfUntouched() {
            // 从 activity 级设置读当前面板：编辑页 VM 可能已经/即将被销毁。
            discardProvisionalPanelIfUntouched(
                currentPanels = listViewModel.settings.value.quickLauncherPanels,
                panelId = key.panelId,
                writer = listViewModel,
                hasCustomIcon = GlobalIconStore.panelIcon(ctx.activity, key.panelId) != null,
            )
        }

        DisposableEffect(key.panelId) {
            onDispose {
                // 只有在编辑页真的出栈时才回收：前进到添加页时它还在栈里，不能误删。
                val stillInStack = ctx.backStack.any {
                    it == AppNavKey.QuickLauncherEdit(key.panelId)
                }
                if (!stillInStack) discardIfUntouched()
            }
        }

        QuickLauncherEditorScreen(
            viewModel = viewModel,
            panelId = key.panelId,
            onBack = {
                // 新建后没做任何修改就返回：别在列表里留下一个没动过的空面板。
                discardIfUntouched()
                ctx.backStack.removeLastOrNull()
            },
            onAdd = { panelId, folderIndex ->
                ctx.navigate(AppNavKey.QuickLauncherAdd(panelId, folderIndex))
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherAdd> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        QuickLauncherAddPickerScreen(
            panelId = key.panelId,
            folderIndex = key.folderIndex,
            settings = settings,
            onBack = { ctx.backStack.removeLastOrNull() },
            onToggleItem = { item, added ->
                viewModel.toggleQuickLauncherPanelItem(key.panelId, item, added, key.folderIndex)
            },
            onAddItem = { item -> viewModel.addQuickLauncherPanelItem(key.panelId, item, key.folderIndex) },
            onPickApp = { ctx.navigate(AppNavKey.QuickLauncherPickApp(key.panelId, folderIndex = key.folderIndex)) },
            onMyShortcuts = { ctx.navigate(AppNavKey.QuickLauncherMyShortcuts(key.panelId, folderIndex = key.folderIndex)) },
            onPresetShortcuts = { ctx.navigate(AppNavKey.QuickLauncherPresetShortcuts(key.panelId, folderIndex = key.folderIndex)) },
            onOpenExecuteShellCommand = { cmd ->
                ctx.navigate(
                    AppNavKey.QuickLauncherShellCommand(
                        panelId = key.panelId,
                        initialCommand = cmd,
                        folderIndex = key.folderIndex,
                    ),
                )
            },
            onOpenCreateFolder = { ctx.navigate(AppNavKey.QuickLauncherCreateFolder(key.panelId)) },
            onOpenOpenLink = { ctx.navigate(AppNavKey.QuickLauncherOpenLink(key.panelId, folderIndex = key.folderIndex)) },
            onOpenSimulateKeyEvent = { ctx.navigate(AppNavKey.QuickLauncherSimulateKeyEvent(key.panelId, folderIndex = key.folderIndex)) },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherPickApp> { key ->
        ActivityShortcutPickAppScreen(
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectApp = { app ->
                ctx.navigate(
                    AppNavKey.QuickLauncherPickActivity(
                        panelId = key.panelId,
                        packageName = app.packageName,
                        fromCreateFolder = key.fromCreateFolder,
                        folderIndex = key.folderIndex,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherPickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val item = QuickLauncherItem.shortcut(
                    "${activity.packageName}/${activity.className}",
                    activity.label,
                )
                if (key.fromCreateFolder) {
                    viewModel.addFolderDraftItem(item)
                } else {
                    viewModel.addQuickLauncherPanelItem(key.panelId, item, key.folderIndex)
                }
                ctx.backStack.removeLastOrNull()
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherMyShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val folderDraft by viewModel.folderDraft.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val panel = remember(settings.quickLauncherPanels, key.panelId) {
            QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels).find { it.id == key.panelId }
                ?: QuickLauncherPanelDefaults.defaultPanel()
        }
        val sourceItems = if (key.fromCreateFolder) {
            folderDraft?.items.orEmpty()
        } else {
            panel.items.resolveFolderItems(key.folderIndex)
        }
        val configuredShortcutKeys = remember(sourceItems) {
            sourceItems.filter { it.type == QuickLauncherItemType.SHORTCUT }.mapNotNull { item ->
                QuickLauncherItemCodec.shortcutItemKey(item)
            }.toSet()
        }
        MyShortcutsFolderScreen(
            activityShortcuts = settings.activityShortcuts,
            onBack = { ctx.backStack.removeLastOrNull() },
            onBrowseNewShortcut = {
                ctx.navigate(
                    AppNavKey.QuickLauncherPickApp(
                        panelId = key.panelId,
                        fromCreateFolder = key.fromCreateFolder,
                        folderIndex = key.folderIndex,
                    ),
                )
            },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added ->
                if (key.fromCreateFolder) {
                    viewModel.toggleFolderDraftItem(item, added)
                } else {
                    viewModel.toggleQuickLauncherPanelItem(key.panelId, item, added, key.folderIndex)
                }
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherPresetShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val folderDraft by viewModel.folderDraft.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val panel = remember(settings.quickLauncherPanels, key.panelId) {
            QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels).find { it.id == key.panelId }
                ?: QuickLauncherPanelDefaults.defaultPanel()
        }
        val sourceItems = if (key.fromCreateFolder) {
            folderDraft?.items.orEmpty()
        } else {
            panel.items.resolveFolderItems(key.folderIndex)
        }
        val configuredShortcutKeys = remember(sourceItems) {
            sourceItems.filter { it.type == QuickLauncherItemType.SHORTCUT }.mapNotNull { item ->
                QuickLauncherItemCodec.shortcutItemKey(item)
            }.toSet()
        }
        PresetShortcutsFolderScreen(
            onBack = { ctx.backStack.removeLastOrNull() },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added ->
                if (key.fromCreateFolder) {
                    viewModel.toggleFolderDraftItem(item, added)
                } else {
                    viewModel.toggleQuickLauncherPanelItem(key.panelId, item, added, key.folderIndex)
                }
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = settings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                val label = displayLabelForExecuteShellCommand(command, settings.shellCommands)
                val item = QuickLauncherItem.action(
                    GestureAction.ExecuteShellCommand(command),
                    label,
                )
                if (key.fromCreateFolder) {
                    viewModel.addFolderDraftItem(item)
                } else {
                    viewModel.addQuickLauncherPanelItem(key.panelId, item, key.folderIndex)
                }
                ctx.backStack.removeLastOrNull()
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherSimulateKeyEvent> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { keyEventAction ->
                val label = keyEventAction.keyName.ifBlank {
                    com.slideindex.app.gesture.KeyEventPresets.getDisplayName(ctx.activity, keyEventAction.keyCode)
                }
                val item = QuickLauncherItem.action(
                    keyEventAction,
                    label,
                )
                if (key.fromCreateFolder) {
                    viewModel.addFolderDraftItem(item)
                } else {
                    viewModel.addQuickLauncherPanelItem(key.panelId, item, key.folderIndex)
                }
                ctx.backStack.removeLastOrNull()
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherOpenLink> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        GestureOpenLinkScreen(
            initialUrl = key.initialUrl,
            initialLabel = key.initialLabel,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { url, label ->
                val trimmedUrl = url.trim()
                val trimmedLabel = label.trim()
                val action = GestureAction.OpenLink(url = trimmedUrl, label = trimmedLabel)
                val itemLabel = trimmedLabel.ifBlank { trimmedUrl }
                val item = QuickLauncherItem.action(action, itemLabel)
                if (key.fromCreateFolder) {
                    viewModel.addFolderDraftItem(item)
                } else {
                    viewModel.addQuickLauncherPanelItem(key.panelId, item, key.folderIndex)
                }
                ctx.backStack.removeLastOrNull()
            },
        )
    }

    hiltEntry<AppNavKey.QuickLauncherCreateFolder> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel(ctx.activity)
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val folderDraft by viewModel.folderDraft.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        LaunchedEffect(key.panelId) {
            viewModel.initFolderDraft(key.panelId)
        }
        QuickLauncherCreateFolderScreen(
            settings = settings,
            draft = folderDraft,
            onFolderNameChange = viewModel::updateFolderDraftName,
            onToggleItem = viewModel::toggleFolderDraftItem,
            onAddItem = viewModel::addFolderDraftItem,
            onBack = {
                viewModel.clearFolderDraft()
                ctx.navigateBackTo(AppNavKey.QuickLauncherAdd(key.panelId))
            },
            onConfirmCreateFolder = { folderName, _ ->
                viewModel.commitFolderDraft(folderName)
                ctx.navigateBackTo(AppNavKey.QuickLauncherEdit(key.panelId))
            },
            onOpenExecuteShellCommand = { cmd ->
                ctx.navigate(
                    AppNavKey.QuickLauncherShellCommand(
                        panelId = key.panelId,
                        initialCommand = cmd,
                        fromCreateFolder = true,
                    ),
                )
            },
            onOpenOpenLink = {
                ctx.navigate(
                    AppNavKey.QuickLauncherOpenLink(
                        panelId = key.panelId,
                        fromCreateFolder = true,
                    ),
                )
            },
            onOpenSimulateKeyEvent = {
                ctx.navigate(
                    AppNavKey.QuickLauncherSimulateKeyEvent(
                        panelId = key.panelId,
                        fromCreateFolder = true,
                    ),
                )
            },
            onPickApp = {
                ctx.navigate(
                    AppNavKey.QuickLauncherPickApp(
                        panelId = key.panelId,
                        fromCreateFolder = true,
                    ),
                )
            },
            onMyShortcuts = {
                ctx.navigate(
                    AppNavKey.QuickLauncherMyShortcuts(
                        panelId = key.panelId,
                        fromCreateFolder = true,
                    ),
                )
            },
            onPresetShortcuts = {
                ctx.navigate(
                    AppNavKey.QuickLauncherPresetShortcuts(
                        panelId = key.panelId,
                        fromCreateFolder = true,
                    ),
                )
            },
        )
    }
}
