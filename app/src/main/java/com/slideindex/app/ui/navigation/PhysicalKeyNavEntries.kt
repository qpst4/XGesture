package com.slideindex.app.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.physicalkey.PhysicalKeyEntry
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

/** 取当前路由要对齐的那一侧动作：熄屏路由看 screenOffAction，否则看亮屏动作。 */
private fun PhysicalKeyEntry?.actionForRoute(screenOff: Boolean): GestureAction {
    if (this == null) return GestureAction.None
    return if (screenOff) screenOffAction else action
}

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
            onInterceptNativeEventsChange = viewModel::setInterceptNativeEvents,
            onScreenOffTriggerChange = viewModel::setScreenOffTriggerEnabled,
            onMediaPlaybackPrioritizedChange = viewModel::setMediaPlaybackPrioritized,
            onLongPressTimeoutChange = viewModel::setLongPressTimeout,
            onDoubleClickTimeoutChange = viewModel::setDoubleClickTimeout,
            onOpenActionPick = { gesture, screenOff ->
                ctx.navigate(AppNavKey.PhysicalKeyActionPick(gesture.ordinal, screenOff))
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
        val current = settings.entries[gesture].actionForRoute(key.screenOff)

        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SINGLE_TAP,
            current = current,
            catalogPolicy = ActionPickerCatalogPolicy.EdgeGesture,
            onDismiss = { ctx.navigateBackTo(AppNavKey.PhysicalKeySettings) },
            onSelect = { action ->
                viewModel.setGestureAction(gesture, action, key.screenOff)
                ctx.navigateBackTo(AppNavKey.PhysicalKeySettings)
            },
            onOpenMyShortcuts = {
                ctx.navigate(
                    AppNavKey.PhysicalKeyActionMyShortcuts(key.gestureOrdinal, key.screenOff),
                )
            },
            onOpenPresetShortcuts = {
                ctx.navigate(
                    AppNavKey.PhysicalKeyActionPresetShortcuts(key.gestureOrdinal, key.screenOff),
                )
            },
            onOpenPickApp = {
                ctx.navigate(
                    AppNavKey.PhysicalKeyActionPickApp(key.gestureOrdinal, key.screenOff),
                )
            },
            onOpenExecuteShellCommand = { command ->
                ctx.navigate(
                    AppNavKey.PhysicalKeyActionShellCommand(
                        key.gestureOrdinal,
                        command,
                        key.screenOff,
                    ),
                )
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
        val current = settings.entries[gesture].actionForRoute(key.screenOff)

        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = {
                ctx.navigateBackTo(
                    AppNavKey.PhysicalKeyActionPick(key.gestureOrdinal, key.screenOff),
                )
            },
            onBrowseNewShortcut = {
                ctx.navigate(
                    AppNavKey.PhysicalKeyActionPickApp(key.gestureOrdinal, key.screenOff),
                )
            },
            currentAction = current,
            onSelectRadio = { action ->
                viewModel.setGestureAction(gesture, action, key.screenOff)
                ctx.navigateBackTo(AppNavKey.PhysicalKeySettings)
            },
        )
    }

    hiltEntry<AppNavKey.PhysicalKeyActionPresetShortcuts> { key ->
        val viewModel: PhysicalKeyViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val gesture = PhysicalKeyGesture.fromOrdinal(key.gestureOrdinal)
            ?: PhysicalKeyGesture.POWER_LONG_PRESS
        val current = settings.entries[gesture].actionForRoute(key.screenOff)

        PresetShortcutsFolderScreen(
            onBack = {
                ctx.navigateBackTo(
                    AppNavKey.PhysicalKeyActionPick(key.gestureOrdinal, key.screenOff),
                )
            },
            currentAction = current,
            onSelectRadio = { action ->
                viewModel.setGestureAction(gesture, action, key.screenOff)
                ctx.navigateBackTo(AppNavKey.PhysicalKeySettings)
            },
        )
    }

    hiltEntry<AppNavKey.PhysicalKeyActionPickApp> { key ->
        ActivityShortcutPickAppScreen(
            onBack = {
                ctx.navigateBackTo(
                    AppNavKey.PhysicalKeyActionPick(key.gestureOrdinal, key.screenOff),
                )
            },
            onSelectApp = { app ->
                ctx.navigate(
                    AppNavKey.PhysicalKeyActionPickActivity(
                        gestureOrdinal = key.gestureOrdinal,
                        appPackageName = app.packageName,
                        screenOff = key.screenOff,
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
                    key.screenOff,
                )
                ctx.navigateBackTo(AppNavKey.PhysicalKeySettings)
            },
        )
    }

    hiltEntry<AppNavKey.PhysicalKeyActionShellCommand> { key ->
        val viewModel: PhysicalKeyViewModel = hiltViewModel()
        val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by extensionViewModel.settings.collectAsStateWithLifecycle()

        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                val gesture = PhysicalKeyGesture.fromOrdinal(key.gestureOrdinal)
                    ?: PhysicalKeyGesture.POWER_LONG_PRESS
                viewModel.setGestureAction(
                    gesture,
                    GestureAction.ExecuteShellCommand(command),
                    key.screenOff,
                )
                ctx.navigateBackTo(AppNavKey.PhysicalKeySettings)
            },
        )
    }
}
