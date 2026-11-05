package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.slideindex.app.ui.BackTapSettingsScreen
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.shake.ShakeGestureSettings
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.ShakeActionSetSettingsScreen
import com.slideindex.app.ui.ShakeGestureBlacklistScreen
import com.slideindex.app.ui.FaceDownSettingsScreen
import com.slideindex.app.ui.ShakeGesturesScreen
import com.slideindex.app.ui.ShakeIndependentAppSettingsScreen
import com.slideindex.app.ui.ShakeIndependentSensitivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.ShakeHubViewModel

fun NavEntryBuilder.shakeNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.ShakeGestures> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ShakeGesturesScreen(
            settings = shakeSettings.shakeGestureSettings,
            bottomContentPadding = ctx.rootBottomContentPadding,
            bottomNavReselectCount = ctx.bottomNavReselectCount,
            onEnabledChange = { enabled -> viewModel.setEnabled(enabled) },
            onWorkModesChange = viewModel::setWorkModes,
            onIndependentAppShakeEnabledChange = { enabled ->
                viewModel.setIndependentAppShakeEnabled(enabled)
            },
            onGlobalSensitivityChange = { value -> viewModel.setGlobalSensitivity(value) },
            onIndependentSensitivityEnabledChange = { enabled ->
                viewModel.setIndependentSensitivityEnabled(enabled)
            },
            onOpenIndependentSensitivity = { ctx.navigate(AppNavKey.ShakeIndependentSensitivity) },
            onAnimationFeedbackEnabledChange = { enabled -> viewModel.setAnimationFeedbackEnabled(enabled) },
            onVibrationFeedbackEnabledChange = { enabled -> viewModel.setVibrationFeedbackEnabled(enabled) },
            onAnimationColorChange = { color -> viewModel.setAnimationColor(color) },
            onDisableInLandscapeChange = { enabled -> viewModel.setDisableInLandscape(enabled) },
            onOpenLockScreenShakeSettings = { ctx.navigate(AppNavKey.ShakeLockScreenSettings) },
            onOpenIndependentAppShakeSettings = { ctx.navigate(AppNavKey.ShakeIndependentAppSettings) },
            onOpenAppBlacklist = { ctx.navigate(AppNavKey.ShakeGestureBlacklist) },
            onOpenBasicActionPick = { type ->
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPick(
                        target = ShakeActionPickTarget.BASIC,
                        gestureTypeId = type.id,
                    ),
                )
            },
            onOpenFaceDownSettings = { ctx.navigate(AppNavKey.ShakeFaceDownSettings) },
            onOpenBackTapSettings = { ctx.navigate(AppNavKey.ExtensionBackTap) },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureActionPick> { key ->
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        val gestureType = ShakeGestureType.fromId(key.gestureTypeId) ?: ShakeGestureType.LEFT_FLIP
        val returnKey = key.target.returnNavKey(key.packageName)
        val trigger = when (key.target) {
            ShakeActionPickTarget.BASIC,
            ShakeActionPickTarget.FACE_DOWN,
            -> GestureTriggerType.SHORT_SINGLE_TAP
            ShakeActionPickTarget.LOCK_SCREEN,
            ShakeActionPickTarget.PER_APP,
            -> GestureTriggerType.SHORT_SWIPE_IN
        }
        val currentAction = when (key.target) {
            ShakeActionPickTarget.BASIC ->
                shakeSettings.shakeGestureSettings.actionFor(gestureType)
            ShakeActionPickTarget.FACE_DOWN ->
                shakeSettings.faceDownGestureSettings.action
            ShakeActionPickTarget.LOCK_SCREEN ->
                shakeSettings.shakeGestureSettings.lockScreenActions[gestureType] ?: GestureAction.None
            ShakeActionPickTarget.PER_APP ->
                shakeSettings.shakeGestureSettings.effectivePerAppAction(key.packageName, gestureType)
        }
        GestureActionPickerScreen(
            trigger = trigger,
            current = currentAction,
            catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.OverlayTap),
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                applyShakePickedAction(viewModel, key.target, gestureType, key.packageName, action)
                ctx.navigateBackTo(returnKey)
            },
            onOpenMyShortcuts = {
                ctx.navigate(
                    AppNavKey.ShakeGestureActionMyShortcuts(
                        target = key.target,
                        gestureTypeId = key.gestureTypeId,
                        packageName = key.packageName,
                    ),
                )
            },
            onOpenPresetShortcuts = {
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPresetShortcuts(
                        target = key.target,
                        gestureTypeId = key.gestureTypeId,
                        packageName = key.packageName,
                    ),
                )
            },
            onOpenPickApp = {
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPickApp(
                        target = key.target,
                        gestureTypeId = key.gestureTypeId,
                        packageName = key.packageName,
                    ),
                )
            },
            onOpenExecuteShellCommand = { cmd ->
                ctx.navigate(
                    AppNavKey.ShakeGestureActionShellCommand(
                        target = key.target,
                        gestureTypeId = key.gestureTypeId,
                        packageName = key.packageName,
                        initialCommand = cmd,
                    ),
                )
            },
            onOpenSimulateKeyEvent = { keyEvent ->
                ctx.navigate(
                    AppNavKey.ShakeGestureActionSimulateKeyEvent(
                        target = key.target,
                        gestureTypeId = key.gestureTypeId,
                        packageName = key.packageName,
                        initialKeyCode = keyEvent.keyCode,
                        initialKeyName = keyEvent.keyName,
                        initialIsLongPress = keyEvent.isLongPress,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureActionMyShortcuts> { key ->
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by extensionViewModel.settings.collectAsStateWithLifecycle()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        val gestureType = ShakeGestureType.fromId(key.gestureTypeId) ?: ShakeGestureType.LEFT_FLIP
        val returnKey = AppNavKey.ShakeGestureActionPick(key.target, key.gestureTypeId, key.packageName)
        val currentAction = when (key.target) {
            ShakeActionPickTarget.BASIC ->
                shakeSettings.shakeGestureSettings.actionFor(gestureType)
            ShakeActionPickTarget.FACE_DOWN ->
                shakeSettings.faceDownGestureSettings.action
            ShakeActionPickTarget.LOCK_SCREEN ->
                shakeSettings.shakeGestureSettings.lockScreenActions[gestureType] ?: GestureAction.None
            ShakeActionPickTarget.PER_APP ->
                shakeSettings.shakeGestureSettings.effectivePerAppAction(key.packageName, gestureType)
        }
        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { ctx.navigateBackTo(returnKey) },
            onBrowseNewShortcut = {
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPickApp(
                        target = key.target,
                        gestureTypeId = key.gestureTypeId,
                        packageName = key.packageName,
                    ),
                )
            },
            currentAction = currentAction,
            onSelectRadio = { action ->
                applyShakePickedAction(viewModel, key.target, gestureType, key.packageName, action)
                ctx.navigateBackTo(key.target.returnNavKey(key.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureActionPresetShortcuts> { key ->
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        val gestureType = ShakeGestureType.fromId(key.gestureTypeId) ?: ShakeGestureType.LEFT_FLIP
        val returnKey = AppNavKey.ShakeGestureActionPick(key.target, key.gestureTypeId, key.packageName)
        val currentAction = when (key.target) {
            ShakeActionPickTarget.BASIC ->
                shakeSettings.shakeGestureSettings.actionFor(gestureType)
            ShakeActionPickTarget.FACE_DOWN ->
                shakeSettings.faceDownGestureSettings.action
            ShakeActionPickTarget.LOCK_SCREEN ->
                shakeSettings.shakeGestureSettings.lockScreenActions[gestureType] ?: GestureAction.None
            ShakeActionPickTarget.PER_APP ->
                shakeSettings.shakeGestureSettings.effectivePerAppAction(key.packageName, gestureType)
        }
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            currentAction = currentAction,
            onSelectRadio = { action ->
                applyShakePickedAction(viewModel, key.target, gestureType, key.packageName, action)
                ctx.navigateBackTo(key.target.returnNavKey(key.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureActionPickApp> { key ->
        val returnKey = AppNavKey.ShakeGestureActionPick(key.target, key.gestureTypeId, key.packageName)
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPickActivity(
                        target = key.target,
                        gestureTypeId = key.gestureTypeId,
                        packageName = key.packageName,
                        appPackageName = app.packageName,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureActionPickActivity> { key ->
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val gestureType = ShakeGestureType.fromId(key.gestureTypeId) ?: ShakeGestureType.LEFT_FLIP
        ActivityShortcutPickActivityScreen(
            packageName = key.appPackageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                applyShakePickedAction(
                    viewModel,
                    key.target,
                    gestureType,
                    key.packageName,
                    GestureAction.LaunchShortcut.component(
                        "${activity.packageName}/${activity.className}",
                        activity.label,
                    ),
                )
                ctx.navigateBackTo(key.target.returnNavKey(key.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureActionShellCommand> { key ->
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val extensionViewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by extensionViewModel.settings.collectAsStateWithLifecycle()
        val gestureType = ShakeGestureType.fromId(key.gestureTypeId) ?: ShakeGestureType.LEFT_FLIP
        val returnKey = key.target.returnNavKey(key.packageName)
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                applyShakePickedAction(
                    viewModel,
                    key.target,
                    gestureType,
                    key.packageName,
                    GestureAction.ExecuteShellCommand(command),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureBlacklist> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ShakeGestureBlacklistScreen(
            blacklistedPackages = shakeSettings.shakeGestureSettings.blacklistedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onOpenAddApp = { ctx.navigate(AppNavKey.ShakeGestureBlacklistPick) },
            onRemoveBlacklistedApp = { packageName -> viewModel.removeShakeBlacklistedApp(packageName) },
            showPageHint = false,
        )
    }

    hiltEntry<AppNavKey.ShakeGestureBlacklistPick> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ActivityShortcutPickAppScreen(
            titleResId = R.string.shake_gestures_blacklist_section_add,
            excludePackageNames = shakeSettings.shakeGestureSettings.blacklistedPackages,
            cloneMode = com.slideindex.app.ui.picker.AppClonePickMode.DISABLED,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestureBlacklist) },
            onSelectApp = { app ->
                viewModel.addShakeBlacklistedApp(app.packageName)
                ctx.navigateBackTo(AppNavKey.ShakeGestureBlacklist)
            },
        )
    }

    hiltEntry<AppNavKey.ShakeLockScreenSettings> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ShakeActionSetSettingsScreen(
            title = stringResource(R.string.shake_gestures_lock_screen),
            subtitle = stringResource(R.string.shake_gestures_lock_screen_settings_desc) + "\n" +
                stringResource(R.string.shake_work_mode_actions_hint),
            actions = shakeSettings.shakeGestureSettings.lockScreenActions,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onOpenActionPick = { type ->
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPick(
                        target = ShakeActionPickTarget.LOCK_SCREEN,
                        gestureTypeId = type.id,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.ShakeFaceDownSettings> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        FaceDownSettingsScreen(
            faceDownSettings = shakeSettings.faceDownGestureSettings,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onEnabledChange = { enabled -> viewModel.setFaceDownEnabled(enabled) },
            onHoldDurationChange = { ms -> viewModel.setFaceDownHoldDurationMs(ms) },
            onCooldownChange = { ms -> viewModel.setFaceDownCooldownMs(ms) },
            onRequireProximityChange = { enabled -> viewModel.setFaceDownRequireProximity(enabled) },
            onDisableInLandscapeChange = { enabled -> viewModel.setFaceDownDisableInLandscape(enabled) },
            onVibrationFeedbackChange = { enabled -> viewModel.setFaceDownVibrationFeedbackEnabled(enabled) },
            onAudioFeedbackChange = { enabled -> viewModel.setFaceDownAudioFeedbackEnabled(enabled) },
            onAudioFeedbackVolumeChange = { volume -> viewModel.setFaceDownAudioFeedbackVolume(volume) },
            onOpenActionPick = {
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPick(
                        target = ShakeActionPickTarget.FACE_DOWN,
                        gestureTypeId = ShakeGestureType.LEFT_FLIP.id,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.ShakeIndependentSensitivity> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ShakeIndependentSensitivityScreen(
            globalSensitivity = shakeSettings.shakeGestureSettings.globalSensitivity,
            perDirectionSensitivity = shakeSettings.shakeGestureSettings.perDirectionSensitivity,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onSensitivityChange = { type, value -> viewModel.setShakeDirectionSensitivity(type, value) },
        )
    }

    hiltEntry<AppNavKey.ShakeIndependentAppSettings> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ShakeIndependentAppSettingsScreen(
            perAppActions = shakeSettings.shakeGestureSettings.perAppActions,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onOpenAddApp = { ctx.navigate(AppNavKey.ShakeIndependentAppPick) },
            onOpenConfiguredApp = { packageName ->
                ctx.navigate(AppNavKey.ShakePerAppActions(packageName))
            },
            onRemoveAppConfig = { packageName -> viewModel.removePerAppShakeConfig(packageName) },
        )
    }

    hiltEntry<AppNavKey.ShakeIndependentAppPick> {
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        ActivityShortcutPickAppScreen(
            titleResId = R.string.shake_gestures_per_app_add,
            excludePackageNames = shakeSettings.shakeGestureSettings.perAppActions.keys,
            cloneMode = com.slideindex.app.ui.picker.AppClonePickMode.DISABLED,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeIndependentAppSettings) },
            onSelectApp = { app ->
                viewModel.addPerAppShakeConfig(app.packageName)
                ctx.navigate(AppNavKey.ShakePerAppActions(app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.ShakePerAppActions> { key ->
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val shakeSettings by viewModel.shakeUiSettings.collectAsStateWithLifecycle()
        val packageName = key.packageName
        val appLabel = remember(packageName) {
            runCatching {
                ctx.activity.packageManager.getApplicationLabel(
                    ctx.activity.packageManager.getApplicationInfo(packageName, 0),
                ).toString()
            }.getOrDefault(packageName)
        }
        ShakeActionSetSettingsScreen(
            title = stringResource(
                R.string.shake_gestures_per_app_actions_title,
                appLabel,
            ),
            subtitle = stringResource(R.string.shake_gestures_independent_app_desc),
            actions = shakeSettings.shakeGestureSettings.effectivePerAppActions(packageName),
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeIndependentAppSettings) },
            onOpenActionPick = { type ->
                ctx.navigate(
                    AppNavKey.ShakeGestureActionPick(
                        target = ShakeActionPickTarget.PER_APP,
                        gestureTypeId = type.id,
                        packageName = packageName,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionBackTap> {
        BackTapSettingsScreen(
            settingsRepository = ctx.deps.settingsRepository,
            onBack = { ctx.navigateBackTo(AppNavKey.ShakeGestures) },
            onOpenActionPick = { ctx.navigate(AppNavKey.ExtensionBackTapActionPick) },
        )
    }

    hiltEntry<AppNavKey.ExtensionBackTapActionPick> {
        val settings by ctx.deps.settingsRepository.settings.collectAsStateWithLifecycle(
            initialValue = ctx.deps.settingsRepository.readSnapshot(),
        )
        val scope = rememberCoroutineScope()
        val returnKey = AppNavKey.ExtensionBackTap
        val current = settings.backTapSettings.action
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SINGLE_TAP,
            current = current,
            catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.OverlayTap),
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                scope.launch {
                    ctx.deps.settingsRepository.setBackTapAction(action)
                    ctx.navigateBackTo(returnKey)
                }
            },
            onOpenMyShortcuts = { ctx.navigate(AppNavKey.ExtensionBackTapActionMyShortcuts) },
            onOpenPresetShortcuts = { ctx.navigate(AppNavKey.ExtensionBackTapActionPresetShortcuts) },
            onOpenPickApp = { ctx.navigate(AppNavKey.ExtensionBackTapActionPickApp) },
            onOpenExecuteShellCommand = { cmd ->
                ctx.navigate(AppNavKey.ExtensionBackTapActionShellCommand(cmd))
            },
            onOpenSimulateKeyEvent = { keyEvent ->
                ctx.navigate(
                    AppNavKey.ExtensionBackTapActionSimulateKeyEvent(
                        initialKeyCode = keyEvent.keyCode,
                        initialKeyName = keyEvent.keyName,
                        initialIsLongPress = keyEvent.isLongPress,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionBackTapActionMyShortcuts> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val settings by ctx.deps.settingsRepository.settings.collectAsStateWithLifecycle(
            initialValue = ctx.deps.settingsRepository.readSnapshot(),
        )
        val scope = rememberCoroutineScope()
        val returnKey = AppNavKey.ExtensionBackTapActionPick
        val current = settings.backTapSettings.action
        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { ctx.navigateBackTo(returnKey) },
            onBrowseNewShortcut = { ctx.navigate(AppNavKey.ExtensionBackTapActionPickApp) },
            currentAction = current,
            onSelectRadio = { action ->
                scope.launch {
                    ctx.deps.settingsRepository.setBackTapAction(action)
                    ctx.navigateBackTo(AppNavKey.ExtensionBackTap)
                }
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionBackTapActionPresetShortcuts> {
        val settings by ctx.deps.settingsRepository.settings.collectAsStateWithLifecycle(
            initialValue = ctx.deps.settingsRepository.readSnapshot(),
        )
        val scope = rememberCoroutineScope()
        val returnKey = AppNavKey.ExtensionBackTapActionPick
        val current = settings.backTapSettings.action
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            currentAction = current,
            onSelectRadio = { action ->
                scope.launch {
                    ctx.deps.settingsRepository.setBackTapAction(action)
                    ctx.navigateBackTo(AppNavKey.ExtensionBackTap)
                }
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionBackTapActionPickApp> {
        val returnKey = AppNavKey.ExtensionBackTapActionPick
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.ExtensionBackTapActionPickActivity(app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionBackTapActionPickActivity> { key ->
        val scope = rememberCoroutineScope()
        ActivityShortcutPickActivityScreen(
            packageName = key.appPackageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val action = GestureAction.LaunchShortcut.component(
                    "${activity.packageName}/${activity.className}",
                    activity.label,
                )
                scope.launch {
                    ctx.deps.settingsRepository.setBackTapAction(action)
                    ctx.navigateBackTo(AppNavKey.ExtensionBackTap)
                }
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionBackTapActionShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val returnKey = AppNavKey.ExtensionBackTap
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                scope.launch {
                    ctx.deps.settingsRepository.setBackTapAction(
                        GestureAction.ExecuteShellCommand(command),
                    )
                    ctx.navigateBackTo(returnKey)
                }
            },
        )
    }

    hiltEntry<AppNavKey.ShakeGestureActionSimulateKeyEvent> { key ->
        val viewModel: ShakeHubViewModel = hiltViewModel()
        val gestureType = ShakeGestureType.fromId(key.gestureTypeId) ?: ShakeGestureType.LEFT_FLIP
        val returnKey = AppNavKey.ShakeGestureActionPick(key.target, key.gestureTypeId, key.packageName)
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { keyEventAction ->
                applyShakePickedAction(
                    viewModel = viewModel,
                    target = key.target,
                    gestureType = gestureType,
                    packageName = key.packageName,
                    action = keyEventAction,
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.ExtensionBackTapActionSimulateKeyEvent> { key ->
        val scope = rememberCoroutineScope()
        val returnKey = AppNavKey.ExtensionBackTapActionPick
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { keyEventAction ->
                scope.launch {
                    ctx.deps.settingsRepository.setBackTapAction(keyEventAction)
                    ctx.navigateBackTo(returnKey)
                }
            },
        )
    }
}

private fun applyShakePickedAction(
    viewModel: ShakeHubViewModel,
    target: ShakeActionPickTarget,
    gestureType: ShakeGestureType,
    packageName: String,
    action: GestureAction,
) {
    when (target) {
        ShakeActionPickTarget.BASIC -> viewModel.setBasicAction(gestureType, action)
        ShakeActionPickTarget.FACE_DOWN -> viewModel.setFaceDownAction(action)
        ShakeActionPickTarget.LOCK_SCREEN -> viewModel.setLockScreenShakeAction(gestureType, action)
        ShakeActionPickTarget.PER_APP -> viewModel.setPerAppShakeAction(packageName, gestureType, action)
    }
}

/** 独立应用页展示“实际生效”的动作：显式覆盖优先，未覆盖的继承基础动作。 */
private fun ShakeGestureSettings.effectivePerAppAction(
    packageName: String,
    type: ShakeGestureType,
): GestureAction {
    val perApp = perAppActions[packageName]
    if (perApp != null && perApp.containsKey(type)) return perApp.getValue(type)
    return actionFor(type)
}

private fun ShakeGestureSettings.effectivePerAppActions(
    packageName: String,
): Map<ShakeGestureType, GestureAction> {
    val overrides = perAppActions[packageName].orEmpty()
    return ShakeGestureType.entries.associateWith { type ->
        overrides[type] ?: actionFor(type)
    }
}
