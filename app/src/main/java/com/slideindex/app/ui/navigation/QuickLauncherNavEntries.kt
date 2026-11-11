package com.slideindex.app.ui.navigation

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
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.launcher.resolveFolderItems
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureOpenLinkScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
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

fun NavEntryBuilder.quickLauncherNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.QuickLauncher> {
        val viewModel: QuickLauncherEditorViewModel = hiltViewModel()
        QuickLauncherEditorScreen(
            viewModel = viewModel,
            onBack = { ctx.navigateBack() },
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
            onBack = { ctx.navigateBackTo(AppNavKey.QuickLauncher) },
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
                ctx.navigateBackTo(AppNavKey.QuickLauncher)
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
