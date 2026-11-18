package com.slideindex.app.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.physicalkey.PhysicalKeyGesture
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.PhysicalKeySettingsScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.PhysicalKeyViewModel
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder

/** 实体按键助手相关路由注册。 */
fun NavEntryBuilder.physicalKeyNavEntries(ctx: MainNavContext, backKey: AppNavKey) {
    hiltEntry<AppNavKey.PhysicalKeySettings> {
        val viewModel: PhysicalKeyViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val status by viewModel.status.collectAsStateWithLifecycle()
        val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.refreshDiagnostics()
        }

        PhysicalKeySettingsScreen(
            settings = settings,
            status = status,
            diagnostics = diagnostics,
            onBack = { ctx.navigateBackTo(backKey) },
            onEnabledChange = viewModel::setEnabled,
            onMediaPlaybackPrioritizedChange = viewModel::setMediaPlaybackPrioritized,
            onLongPressTimeoutChange = viewModel::setLongPressTimeout,
            onDoubleClickTimeoutChange = viewModel::setDoubleClickTimeout,
            onOpenActionPick = { gesture ->
                ctx.navigate(AppNavKey.PhysicalKeyActionPick(gesture.ordinal))
            },
            onRefreshDiagnostics = viewModel::refreshDiagnostics,
            onRetry = viewModel::retry,
        )
    }

    hiltEntry<AppNavKey.PhysicalKeyActionPick> { key ->
        val viewModel: PhysicalKeyViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val gesture = PhysicalKeyGesture.fromOrdinal(key.gestureOrdinal)
            ?: PhysicalKeyGesture.POWER_LONG_PRESS
        val current = settings.entries[gesture]?.action ?: GestureAction.None

        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SINGLE_TAP,
            current = current,
            catalogPolicy = ActionPickerCatalogPolicy.EdgeGesture,
            onDismiss = { ctx.navigateBackTo(AppNavKey.PhysicalKeySettings) },
            onSelect = { action ->
                viewModel.setGestureAction(gesture, action)
                ctx.navigateBackTo(AppNavKey.PhysicalKeySettings)
            },
            onOpenMyShortcuts = {
                ctx.navigate(AppNavKey.PhysicalKeyActionMyShortcuts(key.gestureOrdinal))
            },
            onOpenPresetShortcuts = {
                ctx.navigate(AppNavKey.PhysicalKeyActionPresetShortcuts(key.gestureOrdinal))
            },
            onOpenPickApp = {
                ctx.navigate(AppNavKey.PhysicalKeyActionPickApp(key.gestureOrdinal))
            },
            onOpenExecuteShellCommand = { command ->
                ctx.navigate(AppNavKey.PhysicalKeyActionShellCommand(key.gestureOrdinal, command))
            },
        )
    }

    hiltEntry<AppNavKey.PhysicalKeyActionMyShortcuts> { key ->
        val viewModel: PhysicalKeyViewModel = hiltViewModel()
        val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by extensionViewModel.settings.collectAsStateWithLifecycle()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val gesture = PhysicalKeyGesture.fromOrdinal(key.gestureOrdinal)
            ?: PhysicalKeyGesture.POWER_LONG_PRESS
        val current = settings.entries[gesture]?.action ?: GestureAction.None

        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { ctx.navigateBackTo(AppNavKey.PhysicalKeyActionPick(key.gestureOrdinal)) },
            onBrowseNewShortcut = {
                ctx.navigate(AppNavKey.PhysicalKeyActionPickApp(key.gestureOrdinal))
            },
            currentAction = current,
            onSelectRadio = { action ->
                viewModel.setGestureAction(gesture, action)
                ctx.navigateBackTo(AppNavKey.PhysicalKeySettings)
            },
        )
    }

    hiltEntry<AppNavKey.PhysicalKeyActionPresetShortcuts> { key ->
        val viewModel: PhysicalKeyViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val gesture = PhysicalKeyGesture.fromOrdinal(key.gestureOrdinal)
            ?: PhysicalKeyGesture.POWER_LONG_PRESS
        val current = settings.entries[gesture]?.action ?: GestureAction.None

        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.PhysicalKeyActionPick(key.gestureOrdinal)) },
            currentAction = current,
            onSelectRadio = { action ->
                viewModel.setGestureAction(gesture, action)
                ctx.navigateBackTo(AppNavKey.PhysicalKeySettings)
            },
        )
    }

    hiltEntry<AppNavKey.PhysicalKeyActionPickApp> { key ->
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.PhysicalKeyActionPick(key.gestureOrdinal)) },
            onSelectApp = { app ->
                ctx.navigate(
                    AppNavKey.PhysicalKeyActionPickActivity(
                        gestureOrdinal = key.gestureOrdinal,
                        appPackageName = app.packageName,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.PhysicalKeyActionPickActivity> { key ->
        val viewModel: PhysicalKeyViewModel = hiltViewModel()
        val gesture = PhysicalKeyGesture.fromOrdinal(key.gestureOrdinal)
            ?: PhysicalKeyGesture.POWER_LONG_PRESS

        ActivityShortcutPickActivityScreen(
            packageName = key.appPackageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                viewModel.setGestureAction(
                    gesture,
                    GestureAction.LaunchShortcut.component(
                        "${activity.packageName}/${activity.className}",
                        activity.label,
                    ),
                )
                ctx.navigateBackTo(AppNavKey.PhysicalKeySettings)
            },
        )
    }

    hiltEntry<AppNavKey.PhysicalKeyActionShellCommand> { key ->
        val viewModel: PhysicalKeyViewModel = hiltViewModel()
        val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by extensionViewModel.settings.collectAsStateWithLifecycle()
        val gesture = PhysicalKeyGesture.fromOrdinal(key.gestureOrdinal)
            ?: PhysicalKeyGesture.POWER_LONG_PRESS

        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.setGestureAction(gesture, GestureAction.ExecuteShellCommand(command))
                ctx.navigateBackTo(AppNavKey.PhysicalKeySettings)
            },
        )
    }
}
