package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.honeycombRuntimeItems
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureOpenLinkScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.HoneycombDisplaySettingsScreen
import com.slideindex.app.ui.HoneycombLauncherEditorScreen
import com.slideindex.app.ui.displayLabelForExecuteShellCommand
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.quicklauncher.HoneycombLauncherAddPickerScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.HoneycombLauncherEditorViewModel

fun NavEntryBuilder.honeycombLauncherNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.HoneycombLauncher> {
        val viewModel: HoneycombLauncherEditorViewModel = hiltViewModel()
        HoneycombLauncherEditorScreen(
            viewModel = viewModel,
            onBack = { ctx.navigateBack() },
            onOpenDisplaySettings = { ctx.navigate(AppNavKey.HoneycombDisplaySettings) },
            onAdd = { ctx.navigate(AppNavKey.HoneycombLauncherAdd) },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherAdd> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        HoneycombLauncherAddPickerScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncher) },
            onToggleItem = { item, added -> viewModel.toggleHoneycombItem(item, added) },
            onAddItem = { item -> viewModel.addHoneycombItem(item) },
            onPickApp = { ctx.navigate(AppNavKey.HoneycombLauncherPickApp) },
            onMyShortcuts = { ctx.navigate(AppNavKey.HoneycombLauncherMyShortcuts) },
            onPresetShortcuts = { ctx.navigate(AppNavKey.HoneycombLauncherPresetShortcuts) },
            onOpenExecuteShellCommand = { cmd -> ctx.navigate(AppNavKey.HoneycombLauncherShellCommand(cmd)) },
            onOpenOpenLink = { ctx.navigate(AppNavKey.HoneycombLauncherOpenLink()) },
            onOpenSimulateKeyEvent = { ctx.navigate(AppNavKey.HoneycombLauncherSimulateKeyEvent()) },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherPickApp> {
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.HoneycombLauncherPickActivity(app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherPickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                viewModel.addHoneycombItem(
                    QuickLauncherItem.shortcut(
                        "${activity.packageName}/${activity.className}",
                        activity.label,
                    ),
                )
                ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd)
            },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherMyShortcuts> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val items = remember(settings.honeycombLauncher) {
            settings.honeycombLauncher.honeycombRuntimeItems()
        }
        val configuredShortcutKeys = remember(items) {
            items.filter { it.type == QuickLauncherItemType.SHORTCUT }.mapNotNull { item ->
                QuickLauncherItemCodec.shortcutItemKey(item)
            }.toSet()
        }
        MyShortcutsFolderScreen(
            activityShortcuts = settings.activityShortcuts,
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd) },
            onBrowseNewShortcut = { ctx.navigate(AppNavKey.HoneycombLauncherPickApp) },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added -> viewModel.toggleHoneycombItem(item, added) },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherPresetShortcuts> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val items = remember(settings.honeycombLauncher) {
            settings.honeycombLauncher.honeycombRuntimeItems()
        }
        val configuredShortcutKeys = remember(items) {
            items.filter { it.type == QuickLauncherItemType.SHORTCUT }.mapNotNull { item ->
                QuickLauncherItemCodec.shortcutItemKey(item)
            }.toSet()
        }
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd) },
            configuredShortcutKeys = configuredShortcutKeys,
            onToggle = { item, added -> viewModel.toggleHoneycombItem(item, added) },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = settings.shellCommands,
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd) },
            onConfirm = { command ->
                val label = displayLabelForExecuteShellCommand(command, settings.shellCommands)
                viewModel.addHoneycombItem(
                    QuickLauncherItem.action(
                        GestureAction.ExecuteShellCommand(command),
                        label,
                    ),
                )
                ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd)
            },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherSimulateKeyEvent> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd) },
            onConfirm = { keyEventAction ->
                val label = keyEventAction.keyName.ifBlank {
                    com.slideindex.app.gesture.KeyEventPresets.getDisplayName(ctx.activity, keyEventAction.keyCode)
                }
                viewModel.addHoneycombItem(
                    QuickLauncherItem.action(
                        keyEventAction,
                        label,
                    ),
                )
                ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd)
            },
        )
    }

    hiltEntry<AppNavKey.HoneycombLauncherOpenLink> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        GestureOpenLinkScreen(
            initialUrl = key.initialUrl,
            initialLabel = key.initialLabel,
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd) },
            onConfirm = { url, label ->
                val trimmedUrl = url.trim()
                val trimmedLabel = label.trim()
                val action = GestureAction.OpenLink(url = trimmedUrl, label = trimmedLabel)
                val itemLabel = trimmedLabel.ifBlank { trimmedUrl }
                viewModel.addHoneycombItem(
                    QuickLauncherItem.action(action, itemLabel),
                )
                ctx.navigateBackTo(AppNavKey.HoneycombLauncherAdd)
            },
        )
    }

    hiltEntry<AppNavKey.HoneycombDisplaySettings> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        HoneycombDisplaySettingsScreen(
            display = settings.honeycombDisplay,
            onBack = { ctx.navigateBackTo(AppNavKey.HoneycombLauncher) },
            onDisplayChange = viewModel::setHoneycombDisplaySettings,
        )
    }
}
