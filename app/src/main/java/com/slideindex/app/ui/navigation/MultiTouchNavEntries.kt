package com.slideindex.app.ui.navigation

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.gesture.GestureActionPermissionAuditor
import com.slideindex.app.ui.CloudBackupScreen
import com.slideindex.app.ui.CloudStorageConfigEditorScreen
import com.slideindex.app.ui.CloudStorageSettingsScreen
import com.slideindex.app.ui.DiagnosticLogDetailScreen
import com.slideindex.app.ui.DiagnosticLogListScreen
import com.slideindex.app.ui.ExtensionAboutScreen
import com.slideindex.app.ui.ExtensionHubScreen
import com.slideindex.app.ui.ExternalInvocationHelpScreen
import com.slideindex.app.ui.FreezerAppsPickerScreen
import com.slideindex.app.ui.FreezerHomeScreen
import com.slideindex.app.ui.FreezerSettingsScreen
import com.slideindex.app.ui.LicenseTextScreen
import com.slideindex.app.ui.LauncherShortcutMenuScreen
import com.slideindex.app.ui.MissingGesturePermissionsScreen
import com.slideindex.app.ui.MultiTouchGestureSettingsScreen
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.MultiTouchPathRecordScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.multitouch.MultiPointerBinding
import com.slideindex.app.multitouch.decodePathStrokes
import com.slideindex.app.multitouch.encodePathStrokes
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import com.slideindex.app.ui.PrivacyPolicyScreen
import com.slideindex.app.ui.SettingsBackupScreen
import com.slideindex.app.ui.ThirdPartyNoticesScreen
import com.slideindex.app.ui.viewmodel.CloudBackupViewModel
import com.slideindex.app.ui.viewmodel.CloudStorageSettingsViewModel
import com.slideindex.app.ui.viewmodel.DiagnosticLogViewModel
import com.slideindex.app.ui.viewmodel.ExtensionHubViewModel
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.MultiTouchGestureViewModel
import com.slideindex.app.ui.viewmodel.SettingsBackupViewModel
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.ui.viewmodel.SearchEngineSettingsViewModel
import kotlinx.coroutines.launch

/** 全局触控相关入口：扩展 tab 与首页 tab 各自注册一份，返回目标由 [backKey] 决定。 */
fun NavEntryBuilder.multiTouchNavEntries(ctx: MainNavContext, backKey: AppNavKey) {
    hiltEntry<AppNavKey.ExtensionMultiTouchGesture> {
        val viewModel: MultiTouchGestureViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val status by viewModel.status.collectAsStateWithLifecycle()
        MultiTouchGestureSettingsScreen(
            settings = settings,
            status = status,
            onBack = { ctx.navigateBackTo(backKey) },
            onEnabledChange = viewModel::setEnabled,
            onRetry = viewModel::retry,
            onOpenActionPick = { kind, fingers, pending, entryId ->
                ctx.navigate(
                    AppNavKey.ExtensionMultiTouchActionPick(
                        kind.ordinal,
                        fingers,
                        pending,
                        entryId = entryId,
                    ),
                )
            },
            onOpenRecorder = { ctx.navigate(AppNavKey.ExtensionMultiTouchPathRecord()) },
            onOpenPathEditor = { entry ->
                ctx.navigate(
                    AppNavKey.ExtensionMultiTouchPathRecord(
                        entryId = entry.id,
                        initialStrokes = encodePathStrokes(entry.pathStrokes),
                    ),
                )
            },
            onRemoveEntry = viewModel::removeEntry,
        )
    }

    // 录制页禁用页内横移返回：画线时手指横划会带动页面。
    hiltEntry<AppNavKey.ExtensionMultiTouchPathRecord>(
        swipeDismiss = NavSwipeDirection.None,
    ) { key ->
        val viewModel: MultiTouchGestureViewModel = hiltViewModel()
        MultiTouchPathRecordScreen(
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionMultiTouchGesture) },
            onRecordingStateChange = viewModel::setRecording,
            initialStrokes = decodePathStrokes(key.initialStrokes),
            onFinish = { strokes ->
                if (key.entryId.isNotBlank()) {
                    // 重录：只覆盖轨迹，动作保留，直接回列表。
                    viewModel.updatePathStrokes(key.entryId, encodePathStrokes(strokes))
                    ctx.navigateBackTo(AppNavKey.ExtensionMultiTouchGesture)
                } else {
                    ctx.navigate(
                        AppNavKey.ExtensionMultiTouchActionPick(
                            kind = MultiPointerGesture.PATH.ordinal,
                            fingerCount = strokes.size,
                            pendingStrokes = encodePathStrokes(strokes),
                        ),
                    )
                }
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionMultiTouchActionPick> { key ->
        val viewModel: MultiTouchGestureViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val kind = MultiPointerGesture.entries.getOrNull(key.kind) ?: MultiPointerGesture.SWIPE_LEFT
        val current = settings.entries.firstOrNull {
            if (key.entryId.isNotBlank()) {
                it.id == key.entryId
            } else {
                it.kind == kind && it.fingerCount == key.fingerCount
            }
        }?.action ?: GestureAction.None
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SINGLE_TAP,
            current = current,
            catalogPolicy = ActionPickerCatalogPolicy.EdgeGesture,
            onDismiss = { ctx.navigateBackTo(AppNavKey.ExtensionMultiTouchGesture) },
            onSelect = { action ->
                viewModel.applyEntry(
                    kind,
                    key.fingerCount,
                    key.pendingStrokes,
                    action,
                    entryId = key.entryId,
                )
                ctx.navigateBackTo(AppNavKey.ExtensionMultiTouchGesture)
            },
            onOpenMyShortcuts = {
                ctx.navigate(
                    AppNavKey.ExtensionMultiTouchActionMyShortcuts(
                        key.kind, key.fingerCount, key.pendingStrokes, key.entryId,
                    ),
                )
            },
            onOpenPresetShortcuts = {
                ctx.navigate(
                    AppNavKey.ExtensionMultiTouchActionPresetShortcuts(
                        key.kind, key.fingerCount, key.pendingStrokes, key.entryId,
                    ),
                )
            },
            onOpenPickApp = {
                ctx.navigate(
                    AppNavKey.ExtensionMultiTouchActionPickApp(
                        key.kind, key.fingerCount, key.pendingStrokes, key.entryId,
                    ),
                )
            },
            onOpenExecuteShellCommand = { command ->
                ctx.navigate(
                    AppNavKey.ExtensionMultiTouchActionShellCommand(
                        key.kind, key.fingerCount, key.pendingStrokes, command, key.entryId,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionMultiTouchActionMyShortcuts> { key ->
        val viewModel: MultiTouchGestureViewModel = hiltViewModel()
        val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by extensionViewModel.settings.collectAsStateWithLifecycle()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val kind = MultiPointerGesture.entries.getOrNull(key.kind) ?: MultiPointerGesture.SWIPE_LEFT
        val current = settings.entries.firstOrNull {
            if (key.entryId.isNotBlank()) {
                it.id == key.entryId
            } else {
                it.kind == kind && it.fingerCount == key.fingerCount
            }
        }?.action ?: GestureAction.None
        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = {
                ctx.navigateBackTo(
                    AppNavKey.ExtensionMultiTouchActionPick(
                        key.kind, key.fingerCount, key.pendingStrokes, key.entryId,
                    ),
                )
            },
            onBrowseNewShortcut = {
                ctx.navigate(
                    AppNavKey.ExtensionMultiTouchActionPickApp(
                        key.kind, key.fingerCount, key.pendingStrokes, key.entryId,
                    ),
                )
            },
            currentAction = current,
            onSelectRadio = { action ->
                viewModel.applyEntry(
                    kind,
                    key.fingerCount,
                    key.pendingStrokes,
                    action,
                    entryId = key.entryId,
                )
                ctx.navigateBackTo(AppNavKey.ExtensionMultiTouchGesture)
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionMultiTouchActionPresetShortcuts> { key ->
        val viewModel: MultiTouchGestureViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val kind = MultiPointerGesture.entries.getOrNull(key.kind) ?: MultiPointerGesture.SWIPE_LEFT
        val current = settings.entries.firstOrNull {
            if (key.entryId.isNotBlank()) {
                it.id == key.entryId
            } else {
                it.kind == kind && it.fingerCount == key.fingerCount
            }
        }?.action ?: GestureAction.None
        PresetShortcutsFolderScreen(
            onBack = {
                ctx.navigateBackTo(
                    AppNavKey.ExtensionMultiTouchActionPick(
                        key.kind, key.fingerCount, key.pendingStrokes, key.entryId,
                    ),
                )
            },
            currentAction = current,
            onSelectRadio = { action ->
                viewModel.applyEntry(
                    kind,
                    key.fingerCount,
                    key.pendingStrokes,
                    action,
                    entryId = key.entryId,
                )
                ctx.navigateBackTo(AppNavKey.ExtensionMultiTouchGesture)
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionMultiTouchActionPickApp> { key ->
        ActivityShortcutPickAppScreen(
            onBack = {
                ctx.navigateBackTo(
                    AppNavKey.ExtensionMultiTouchActionPick(
                        key.kind, key.fingerCount, key.pendingStrokes, key.entryId,
                    ),
                )
            },
            onSelectApp = { app ->
                ctx.navigate(
                    AppNavKey.ExtensionMultiTouchActionPickActivity(
                        key.kind,
                        key.fingerCount,
                        key.pendingStrokes,
                        app.packageName,
                        key.entryId,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionMultiTouchActionPickActivity> { key ->
        val viewModel: MultiTouchGestureViewModel = hiltViewModel()
        val kind = MultiPointerGesture.entries.getOrNull(key.kind) ?: MultiPointerGesture.SWIPE_LEFT
        ActivityShortcutPickActivityScreen(
            packageName = key.appPackageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                viewModel.applyEntry(
                    kind,
                    key.fingerCount,
                    key.pendingStrokes,
                    GestureAction.LaunchShortcut.component(
                        "${activity.packageName}/${activity.className}",
                        activity.label,
                    ),
                    entryId = key.entryId,
                )
                ctx.navigateBackTo(AppNavKey.ExtensionMultiTouchGesture)
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionMultiTouchActionShellCommand> { key ->
        val viewModel: MultiTouchGestureViewModel = hiltViewModel()
        val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by extensionViewModel.settings.collectAsStateWithLifecycle()
        val kind = MultiPointerGesture.entries.getOrNull(key.kind) ?: MultiPointerGesture.SWIPE_LEFT
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.applyEntry(
                    kind,
                    key.fingerCount,
                    key.pendingStrokes,
                    GestureAction.ExecuteShellCommand(command),
                    entryId = key.entryId,
                )
                ctx.navigateBackTo(AppNavKey.ExtensionMultiTouchGesture)
            },
        )
    }

}
