package com.slideindex.app.ui.quicklauncher

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureOpenLinkScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.displayLabelForExecuteShellCommand
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

/**
 * 「向快速启动器面板添加条目」的整页导航宿主。
 *
 * 与设置页 `QuickLauncherNavEntries` 使用**同一套页面**（`QuickLauncherAddPickerScreen`、
 * `QuickLauncherCreateFolderScreen`、快捷键/应用/动作配置页 + `ExtensionSettingsViewModel`），
 * 只是把导航从主路由换成 Activity 内部的页面状态，退出时 finish 透明 Activity。
 *
 * 之前的浮层 sheet 版本是第二套实现（M3 外观、自己维护多选状态），既不符合 Miuix 页面规范，
 * 又和设置页行为不一致；现在面板入口与设置入口共用同一套页面与持久化。
 */
@Composable
fun QuickLauncherAddFlowScreen(
    panelId: String,
    viewModel: ExtensionSettingsViewModel,
    onExit: () -> Unit,
) {
    val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
    val folderDraft by viewModel.folderDraft.collectAsStateWithLifecycle()
    val settings = remember(gestureSettings) { gestureSettings.toMinimalAppSettings() }
    val context = LocalContext.current
    var page by remember(panelId) { mutableStateOf<AddPage>(AddPage.Add) }
    /** 子流程是否属于「新建文件夹」：决定落库到文件夹草稿还是面板，以及返回目标。 */
    var fromCreateFolder by remember(panelId) { mutableStateOf(false) }

    val panel = remember(settings.quickLauncherPanels, panelId) {
        QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels)
            .find { it.id == panelId }
            ?: QuickLauncherPanelDefaults.defaultPanel()
    }
    val draft = folderDraft?.takeIf { it.panelId == panelId }
    val sourceItems = if (fromCreateFolder) draft?.items.orEmpty() else panel.items
    val configuredShortcutKeys = remember(sourceItems) { sourceItems.shortcutKeys() }

    val navigateBack: () -> Unit = {
        when (page) {
            AddPage.Add -> onExit()
            AddPage.CreateFolder -> {
                viewModel.clearFolderDraft()
                fromCreateFolder = false
                page = AddPage.Add
            }
            AddPage.PickApp,
            AddPage.MyShortcuts,
            AddPage.PresetShortcuts,
            -> page = if (fromCreateFolder) AddPage.CreateFolder else AddPage.Add
            is AddPage.PickActivity -> page = AddPage.PickApp
            is AddPage.ShellCommand,
            AddPage.OpenLink,
            AddPage.SimulateKeyEvent,
            -> page = if (fromCreateFolder) AddPage.CreateFolder else AddPage.Add
        }
    }

    BackHandler(onBack = navigateBack)

    when (val current = page) {
        AddPage.Add -> QuickLauncherAddPickerScreen(
            panelId = panelId,
            settings = settings,
            onBack = onExit,
            onToggleItem = { item, added ->
                viewModel.toggleQuickLauncherPanelItem(panelId, item, added, -1)
            },
            onAddItem = { item -> viewModel.addQuickLauncherPanelItem(panelId, item, -1) },
            onPickApp = {
                fromCreateFolder = false
                page = AddPage.PickApp
            },
            onMyShortcuts = {
                fromCreateFolder = false
                page = AddPage.MyShortcuts
            },
            onPresetShortcuts = {
                fromCreateFolder = false
                page = AddPage.PresetShortcuts
            },
            onOpenExecuteShellCommand = { command ->
                fromCreateFolder = false
                page = AddPage.ShellCommand(command)
            },
            onOpenCreateFolder = {
                viewModel.initFolderDraft(panelId)
                page = AddPage.CreateFolder
            },
            onOpenOpenLink = {
                fromCreateFolder = false
                page = AddPage.OpenLink
            },
            onOpenSimulateKeyEvent = {
                fromCreateFolder = false
                page = AddPage.SimulateKeyEvent
            },
        )

        AddPage.CreateFolder -> {
            LaunchedEffect(panelId) { viewModel.initFolderDraft(panelId) }
            QuickLauncherCreateFolderScreen(
                settings = settings,
                draft = draft,
                onBack = {
                    viewModel.clearFolderDraft()
                    fromCreateFolder = false
                    page = AddPage.Add
                },
                onConfirmCreateFolder = { name, _ ->
                    viewModel.commitFolderDraft(name)
                    viewModel.clearFolderDraft()
                    fromCreateFolder = false
                    page = AddPage.Add
                },
                onOpenExecuteShellCommand = { command ->
                    fromCreateFolder = true
                    page = AddPage.ShellCommand(command)
                },
                onOpenOpenLink = {
                    fromCreateFolder = true
                    page = AddPage.OpenLink
                },
                onOpenSimulateKeyEvent = {
                    fromCreateFolder = true
                    page = AddPage.SimulateKeyEvent
                },
                onPickApp = {
                    fromCreateFolder = true
                    page = AddPage.PickApp
                },
                onMyShortcuts = {
                    fromCreateFolder = true
                    page = AddPage.MyShortcuts
                },
                onPresetShortcuts = {
                    fromCreateFolder = true
                    page = AddPage.PresetShortcuts
                },
                onFolderNameChange = viewModel::updateFolderDraftName,
                onToggleItem = viewModel::toggleFolderDraftItem,
                onAddItem = viewModel::addFolderDraftItem,
            )
        }

        AddPage.PickApp -> ActivityShortcutPickAppScreen(
            onBack = navigateBack,
            onSelectApp = { app -> page = AddPage.PickActivity(app.packageName) },
        )

        is AddPage.PickActivity -> ActivityShortcutPickActivityScreen(
            packageName = current.packageName,
            onBack = navigateBack,
            onSelectActivity = { activity ->
                val item = QuickLauncherItem.shortcut(
                    componentFlat = "${activity.packageName}/${activity.className}",
                    label = activity.label,
                )
                if (fromCreateFolder) {
                    viewModel.addFolderDraftItem(item)
                    page = AddPage.CreateFolder
                } else {
                    viewModel.addQuickLauncherPanelItem(panelId, item, -1)
                    page = AddPage.Add
                }
            },
        )

        AddPage.MyShortcuts -> MyShortcutsFolderScreen(
            activityShortcuts = settings.activityShortcuts,
            onBack = navigateBack,
            onBrowseNewShortcut = { page = AddPage.PickApp },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added ->
                if (fromCreateFolder) {
                    viewModel.toggleFolderDraftItem(item, added)
                } else {
                    viewModel.toggleQuickLauncherPanelItem(panelId, item, added, -1)
                }
            },
        )

        AddPage.PresetShortcuts -> PresetShortcutsFolderScreen(
            onBack = navigateBack,
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added ->
                if (fromCreateFolder) {
                    viewModel.toggleFolderDraftItem(item, added)
                } else {
                    viewModel.toggleQuickLauncherPanelItem(panelId, item, added, -1)
                }
            },
        )

        is AddPage.ShellCommand -> GestureExecuteShellCommandScreen(
            initialCommand = current.initialCommand,
            shellCommands = settings.shellCommands,
            onBack = navigateBack,
            onConfirm = { command ->
                val label = displayLabelForExecuteShellCommand(command, settings.shellCommands)
                val item = QuickLauncherItem.action(GestureAction.ExecuteShellCommand(command), label)
                if (fromCreateFolder) {
                    viewModel.addFolderDraftItem(item)
                    page = AddPage.CreateFolder
                } else {
                    viewModel.addQuickLauncherPanelItem(panelId, item, -1)
                    page = AddPage.Add
                }
            },
        )

        AddPage.OpenLink -> GestureOpenLinkScreen(
            initialUrl = "",
            onBack = navigateBack,
            onConfirm = { url, label ->
                val trimmedUrl = url.trim()
                val trimmedLabel = label.trim()
                val item = QuickLauncherItem.action(
                    GestureAction.OpenLink(url = trimmedUrl, label = trimmedLabel),
                    trimmedLabel.ifBlank { trimmedUrl },
                )
                if (fromCreateFolder) {
                    viewModel.addFolderDraftItem(item)
                    page = AddPage.CreateFolder
                } else {
                    viewModel.addQuickLauncherPanelItem(panelId, item, -1)
                    page = AddPage.Add
                }
            },
        )

        AddPage.SimulateKeyEvent -> GestureSimulateKeyEventScreen(
            initialAction = null,
            onBack = navigateBack,
            onConfirm = { keyEvent ->
                val label = keyEvent.keyName.ifBlank {
                    com.slideindex.app.gesture.KeyEventPresets.getDisplayName(context, keyEvent.keyCode)
                }
                val item = QuickLauncherItem.action(keyEvent, label)
                if (fromCreateFolder) {
                    viewModel.addFolderDraftItem(item)
                    page = AddPage.CreateFolder
                } else {
                    viewModel.addQuickLauncherPanelItem(panelId, item, -1)
                    page = AddPage.Add
                }
            },
        )
    }
}

private sealed interface AddPage {
    data object Add : AddPage
    data object CreateFolder : AddPage
    data object PickApp : AddPage
    data class PickActivity(val packageName: String) : AddPage
    data object MyShortcuts : AddPage
    data object PresetShortcuts : AddPage
    data class ShellCommand(val initialCommand: String) : AddPage
    data object OpenLink : AddPage
    data object SimulateKeyEvent : AddPage
}

private fun List<QuickLauncherItem>.shortcutKeys(): Set<String> =
    filter { it.type == QuickLauncherItemType.SHORTCUT }
        .mapNotNull { QuickLauncherItemCodec.shortcutItemKey(it) }
        .toSet()
