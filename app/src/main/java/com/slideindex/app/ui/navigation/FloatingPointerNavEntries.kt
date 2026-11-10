package com.slideindex.app.ui.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.ui.FloatingPointerEdgeActionsSettingsScreen
import com.slideindex.app.ui.FloatingPointerEdgeSideSettingsScreen
import com.slideindex.app.ui.FloatingPointerJoystickSettingsScreen
import com.slideindex.app.ui.FloatingPointerPointerSettingsScreen
import com.slideindex.app.ui.FloatingPointerRadialMenuSettingsScreen
import com.slideindex.app.ui.FloatingPointerSettingsScreen
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.PointerSwipeConfigScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

fun NavEntryBuilder.floatingPointerNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.FloatingPointer> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        val areaPreviewEnabled = ctx.collectAreaPreviewEnabled()
        FloatingPointerSettingsScreen(
            settings = settings,
            areaPreviewEnabled = areaPreviewEnabled,
            previewAccessibilityGranted = permissions.accessibilityGranted,
            onAreaPreviewEnabledChange = { ctx.setFloatingPointerAreaPreviewEnabled(it) },
            // 行程范围预览开启时，拖动灵敏度滑条实时反映到浮层上。
            onSensitivityPreviewStart = {
                if (areaPreviewEnabled) ctx.previewFloatingPointerAreaSensitivityStart()
            },
            onSensitivityPreviewChange = { value ->
                if (areaPreviewEnabled) ctx.previewFloatingPointerAreaSensitivity(value)
            },
            onSensitivityPreviewStop = {
                if (areaPreviewEnabled) ctx.previewFloatingPointerAreaSensitivityEnd()
            },
            onBack = { ctx.backStack.removeLastOrNull() },
            onOpenPointerSettings = { ctx.navigate(AppNavKey.FloatingPointerPointer) },
            onOpenJoystickSettings = { ctx.navigate(AppNavKey.FloatingPointerJoystick) },
            onOpenRadialMenuSettings = { ctx.navigate(AppNavKey.FloatingPointerRadialMenu) },
            onOpenEdgeActionsSettings = { ctx.navigate(AppNavKey.FloatingPointerEdgeActions) },
            onPointerSensitivityChange = viewModel::setFloatingPointerSensitivityFraction,
        )
    }

    hiltEntry<AppNavKey.FloatingPointerPointer> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatingPointerPointerSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onPointerDiameterChange = viewModel::setFloatingPointerPointerDiameterPx,
            onRingThicknessChange = viewModel::setFloatingPointerRingThicknessPx,
            onDotDiameterChange = viewModel::setFloatingPointerDotDiameterPx,
            onRingColorChange = viewModel::setFloatingPointerRingColor,
            onFillColorChange = viewModel::setFloatingPointerFillColor,
            onDotColorChange = viewModel::setFloatingPointerDotColor,
            onClickVisualFeedbackChange = viewModel::setFloatingPointerClickVisualFeedbackEnabled,
            onClickHapticChange = viewModel::setFloatingPointerClickHapticEnabled,
            onRippleColorChange = viewModel::setFloatingPointerRippleColor,
            onRippleSizeChange = viewModel::setFloatingPointerRippleSizeDp,
            onRippleDurationChange = viewModel::setFloatingPointerRippleDurationMs,
            onTrailTypeChange = viewModel::setFloatingPointerTrailType,
            onTrailDurationChange = viewModel::setFloatingPointerTrailDurationMs,
            onTrailColorChange = viewModel::setFloatingPointerTrailColor,
            onHideWhenReleasedChange = viewModel::setFloatingPointerHideWhenJoystickReleased,
            onPointerDesignChange = { design -> viewModel.setFloatingPointerDesignId(design.id) },
            onResetVisualDefaults = viewModel::resetFloatingPointerVisualDefaults,
        )
    }

    hiltEntry<AppNavKey.FloatingPointerJoystick> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatingPointerJoystickSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onJoystickDiameterChange = viewModel::setFloatingPointerJoystickDiameterPx,
            onInnerColorChange = viewModel::setFloatingPointerJoystickInnerColor,
            onOuterColorChange = viewModel::setFloatingPointerJoystickOuterColor,
            onGradientRadiusChange = viewModel::setFloatingPointerJoystickGradientRadiusFraction,
            onHideOnOutsideClickChange = viewModel::setFloatingPointerHideOnOutsideClick,
            onHideOnQuickSwipeChange = viewModel::setFloatingPointerHideOnQuickSwipe,
            onHideWhenIdleChange = viewModel::setFloatingPointerHideWhenIdle,
            onIdleDelayChange = viewModel::setFloatingPointerIdleHideDelayMs,
            onReleaseClickAndDismissChange = viewModel::setFloatingPointerReleaseClickAndDismiss,
            onHoverEnterSelectChange = viewModel::setFloatingPointerHoverEnterSelect,
            onClickDistanceThresholdChange = viewModel::setFloatingPointerClickDistanceThresholdDp,
            onResetVisualDefaults = viewModel::resetFloatingPointerJoystickVisualDefaults,
            onResetBehaviorDefaults = viewModel::resetFloatingPointerJoystickBehaviorDefaults,
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialMenu> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        // 功能环设置页的预览/行图标必须拿到完整 launcher 数据：
        // activityShortcuts 决定快捷方式自定义图标，shellCommands 决定 shell 图标。
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        FloatingPointerRadialMenuSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onAlwaysVisibleChange = viewModel::setFloatingPointerRadialAlwaysVisible,
            onLongPressMsChange = viewModel::setFloatingPointerRadialLongPressMs,
            onOpenLongPressActionPick = {
                ctx.navigate(
                    AppNavKey.FloatingPointerRadialActionPick(FloatingPointerRadialActionTarget.LONG_PRESS),
                )
            },
            onOpenSlotActionPick = { slotIndex ->
                ctx.navigate(
                    AppNavKey.FloatingPointerRadialActionPick(
                        target = FloatingPointerRadialActionTarget.SLOT,
                        slotIndex = slotIndex,
                    ),
                )
            },
            onOpenShellCommand = { slotIndex, command ->
                ctx.navigate(AppNavKey.FloatingPointerRadialShellCommand(slotIndex, command))
            },
            onOpenSwipeConfig = { slotIndex ->
                ctx.navigate(AppNavKey.FloatingPointerRadialSwipeConfig(slotIndex))
            },
            onSlotActionChange = viewModel::setFloatingPointerRadialSlotAction,
            onOuterDiameterChange = viewModel::setFloatingPointerRadialOuterDiameterPx,
            onInnerDiameterChange = viewModel::setFloatingPointerRadialInnerDiameterPx,
            onOuterColorChange = viewModel::setFloatingPointerRadialOuterColor,
            onInnerColorChange = viewModel::setFloatingPointerRadialInnerColor,
            onDividerThicknessChange = viewModel::setFloatingPointerRadialDividerThicknessPx,
            onDividerColorChange = viewModel::setFloatingPointerRadialDividerColor,
            onIconSizeFractionChange = viewModel::setFloatingPointerRadialIconSizeFraction,
            onIconColorChange = viewModel::setFloatingPointerRadialIconColor,
            onResetDesignDefaults = viewModel::resetFloatingPointerRadialDesignDefaults,
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialActionPick> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val returnKey = AppNavKey.FloatingPointerRadialMenu
        val current = when (key.target) {
            FloatingPointerRadialActionTarget.LONG_PRESS -> settings.floatingPointerJoystickLongPressAction
            FloatingPointerRadialActionTarget.SLOT ->
                settings.floatingPointerRadialSlotActions.getOrElse(key.slotIndex) { GestureAction.None }
        }
        val radialSlotPickerKind = when (key.target) {
            FloatingPointerRadialActionTarget.LONG_PRESS -> SlotPickerKind.FloatingPointerRadialLongPress
            FloatingPointerRadialActionTarget.SLOT -> SlotPickerKind.FloatingPointerRadialSlot
        }
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            current = current,
            includePointerGestureActions = true,
            catalogPolicy = ActionPickerCatalogPolicy.Slot(radialSlotPickerKind),
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                when (key.target) {
                    FloatingPointerRadialActionTarget.LONG_PRESS -> {
                        viewModel.setFloatingPointerJoystickLongPressAction(action)
                        ctx.navigateBackTo(returnKey)
                    }
                    FloatingPointerRadialActionTarget.SLOT -> {
                        if (action is GestureAction.SimulatePointerSwipe) {
                            ctx.navigate(AppNavKey.FloatingPointerRadialSwipeConfig(key.slotIndex))
                        } else {
                            viewModel.setFloatingPointerRadialSlotAction(key.slotIndex, action)
                            ctx.navigateBackTo(returnKey)
                        }
                    }
                }
            },
            onOpenMyShortcuts = { ctx.navigate(AppNavKey.FloatingPointerRadialMyShortcuts(key.target, key.slotIndex)) },
            onOpenPresetShortcuts = { ctx.navigate(AppNavKey.FloatingPointerRadialPresetShortcuts(key.target, key.slotIndex)) },
            onOpenPickApp = { ctx.navigate(AppNavKey.FloatingPointerRadialPickApp(key.target, key.slotIndex)) },
            onOpenExecuteShellCommand = { cmd -> ctx.navigate(AppNavKey.FloatingPointerRadialShellCommand(key.slotIndex, cmd)) },
            onOpenSimulateKeyEvent = { keyEvent ->
                ctx.navigate(
                    AppNavKey.FloatingPointerRadialSimulateKeyEvent(
                        key.slotIndex,
                        keyEvent.keyCode,
                        keyEvent.keyName,
                        keyEvent.isLongPress,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialMyShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.FloatingPointerRadialActionPick(key.target, key.slotIndex)
        val current = when (key.target) {
            FloatingPointerRadialActionTarget.LONG_PRESS -> appSettings.floatingPointerJoystickLongPressAction
            FloatingPointerRadialActionTarget.SLOT ->
                appSettings.floatingPointerRadialSlotActions.getOrElse(key.slotIndex) { GestureAction.None }
        }
        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { ctx.navigateBackTo(returnKey) },
            onBrowseNewShortcut = { ctx.navigate(AppNavKey.FloatingPointerRadialPickApp(key.target, key.slotIndex)) },
            currentAction = current,
            onSelectRadio = { action ->
                when (key.target) {
                    FloatingPointerRadialActionTarget.LONG_PRESS -> viewModel.setFloatingPointerJoystickLongPressAction(action)
                    FloatingPointerRadialActionTarget.SLOT -> viewModel.setFloatingPointerRadialSlotAction(key.slotIndex, action)
                }
                ctx.navigateBackTo(AppNavKey.FloatingPointerRadialMenu)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialPresetShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.FloatingPointerRadialActionPick(key.target, key.slotIndex)
        val current = when (key.target) {
            FloatingPointerRadialActionTarget.LONG_PRESS -> appSettings.floatingPointerJoystickLongPressAction
            FloatingPointerRadialActionTarget.SLOT ->
                appSettings.floatingPointerRadialSlotActions.getOrElse(key.slotIndex) { GestureAction.None }
        }
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            currentAction = current,
            onSelectRadio = { action ->
                when (key.target) {
                    FloatingPointerRadialActionTarget.LONG_PRESS -> viewModel.setFloatingPointerJoystickLongPressAction(action)
                    FloatingPointerRadialActionTarget.SLOT -> viewModel.setFloatingPointerRadialSlotAction(key.slotIndex, action)
                }
                ctx.navigateBackTo(AppNavKey.FloatingPointerRadialMenu)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialPickApp> { key ->
        val returnKey = AppNavKey.FloatingPointerRadialActionPick(key.target, key.slotIndex)
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.FloatingPointerRadialPickActivity(key.target, key.slotIndex, app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialPickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val action = GestureAction.LaunchShortcut.component(
                    "${activity.packageName}/${activity.className}",
                    activity.label,
                )
                when (key.target) {
                    FloatingPointerRadialActionTarget.LONG_PRESS -> viewModel.setFloatingPointerJoystickLongPressAction(action)
                    FloatingPointerRadialActionTarget.SLOT -> viewModel.setFloatingPointerRadialSlotAction(key.slotIndex, action)
                }
                ctx.navigateBackTo(AppNavKey.FloatingPointerRadialMenu)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.FloatingPointerRadialMenu
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.setFloatingPointerRadialSlotAction(
                    key.slotIndex,
                    GestureAction.ExecuteShellCommand(command),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialSimulateKeyEvent> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val returnKey = AppNavKey.FloatingPointerRadialActionPick(FloatingPointerRadialActionTarget.SLOT, key.slotIndex)
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { keyEventAction ->
                viewModel.setFloatingPointerRadialSlotAction(
                    key.slotIndex,
                    keyEventAction,
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerRadialSwipeConfig> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val returnKey = AppNavKey.FloatingPointerRadialMenu
        val current = settings.floatingPointerRadialSlotActions.getOrElse(key.slotIndex) { GestureAction.None }
        val initialConfig = (current as? GestureAction.SimulatePointerSwipe)?.config
            ?: PointerSwipeConfig.DEFAULT
        PointerSwipeConfigScreen(
            initialConfig = initialConfig,
            onBack = { ctx.navigateBackTo(returnKey) },
            onConfirm = { config ->
                viewModel.setFloatingPointerRadialSlotAction(
                    key.slotIndex,
                    GestureAction.SimulatePointerSwipe(config),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeActions> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        FloatingPointerEdgeActionsSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointer) },
            onThresholdChange = viewModel::setFloatingPointerEdgeThresholdDp,
            onPreviewSensitivityChange = viewModel::setFloatingPointerEdgePreviewSensitivity,
            onPreviewGlowSizeChange = viewModel::setFloatingPointerEdgePreviewGlowSize,
            onPreviewShowIconChange = viewModel::setFloatingPointerEdgePreviewShowIcon,
            onVisualColorChange = viewModel::setFloatingPointerEdgeVisualColor,
            onOpenSideSettings = { side ->
                ctx.navigate(AppNavKey.FloatingPointerEdgeSideSettings(side.toNavSide()))
            },
            onResetDefaults = viewModel::resetFloatingPointerEdgeDefaults,
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeSideSettings> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val side = key.side.toFloatingPointerEdgeSide()
        FloatingPointerEdgeSideSettingsScreen(
            side = side,
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.FloatingPointerEdgeActions) },
            onEnabledChange = { enabled -> viewModel.setFloatingPointerEdgeBarEnabled(side, enabled) },
            onOpenActionPick = { slotIndex ->
                ctx.navigate(AppNavKey.FloatingPointerEdgeActionPick(key.side, slotIndex))
            },
            onOpenShellCommand = { slotIndex, command ->
                ctx.navigate(AppNavKey.FloatingPointerEdgeShellCommand(key.side, slotIndex, command))
            },
            onAddSlot = { viewModel.addFloatingPointerEdgeBarSlot(side) },
            onRemoveSlot = { slotIndex -> viewModel.removeFloatingPointerEdgeBarSlot(side, slotIndex) },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeActionPick> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeSideSettings(key.side)
        val current = settings.floatingPointerEdgeActionsConfig
            .bar(side)
            .layoutSlots()
            .getOrNull(key.slotIndex)
            ?.action
            ?: GestureAction.None
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            current = current,
            includePointerGestureActions = false,
            catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.OverlayTap),
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                viewModel.setFloatingPointerEdgeBarSlotAction(side, key.slotIndex, action)
                ctx.navigateBackTo(returnKey)
            },
            onOpenMyShortcuts = { ctx.navigate(AppNavKey.FloatingPointerEdgeMyShortcuts(key.side, key.slotIndex)) },
            onOpenPresetShortcuts = { ctx.navigate(AppNavKey.FloatingPointerEdgePresetShortcuts(key.side, key.slotIndex)) },
            onOpenPickApp = { ctx.navigate(AppNavKey.FloatingPointerEdgePickApp(key.side, key.slotIndex)) },
            onOpenExecuteShellCommand = { cmd -> ctx.navigate(AppNavKey.FloatingPointerEdgeShellCommand(key.side, key.slotIndex, cmd)) },
            onOpenSimulateKeyEvent = { keyEvent ->
                ctx.navigate(
                    AppNavKey.FloatingPointerEdgeSimulateKeyEvent(
                        key.side,
                        key.slotIndex,
                        keyEvent.keyCode,
                        keyEvent.keyName,
                        keyEvent.isLongPress,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeMyShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeActionPick(key.side, key.slotIndex)
        val current = appSettings.floatingPointerEdgeActionsConfig
            .bar(side)
            .layoutSlots()
            .getOrNull(key.slotIndex)
            ?.action
            ?: GestureAction.None
        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { ctx.navigateBackTo(returnKey) },
            onBrowseNewShortcut = { ctx.navigate(AppNavKey.FloatingPointerEdgePickApp(key.side, key.slotIndex)) },
            currentAction = current,
            onSelectRadio = { action ->
                viewModel.setFloatingPointerEdgeBarSlotAction(side, key.slotIndex, action)
                ctx.navigateBackTo(AppNavKey.FloatingPointerEdgeSideSettings(key.side))
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgePresetShortcuts> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeActionPick(key.side, key.slotIndex)
        val current = appSettings.floatingPointerEdgeActionsConfig
            .bar(side)
            .layoutSlots()
            .getOrNull(key.slotIndex)
            ?.action
            ?: GestureAction.None
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            currentAction = current,
            onSelectRadio = { action ->
                viewModel.setFloatingPointerEdgeBarSlotAction(side, key.slotIndex, action)
                ctx.navigateBackTo(AppNavKey.FloatingPointerEdgeSideSettings(key.side))
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgePickApp> { key ->
        val returnKey = AppNavKey.FloatingPointerEdgeActionPick(key.side, key.slotIndex)
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.FloatingPointerEdgePickActivity(key.side, key.slotIndex, app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgePickActivity> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val side = key.side.toFloatingPointerEdgeSide()
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val action = GestureAction.LaunchShortcut.component(
                    "${activity.packageName}/${activity.className}",
                    activity.label,
                )
                viewModel.setFloatingPointerEdgeBarSlotAction(side, key.slotIndex, action)
                ctx.navigateBackTo(AppNavKey.FloatingPointerEdgeSideSettings(key.side))
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeShellCommand> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeSideSettings(key.side)
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.setFloatingPointerEdgeBarSlotAction(
                    side,
                    key.slotIndex,
                    GestureAction.ExecuteShellCommand(command),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.FloatingPointerEdgeSimulateKeyEvent> { key ->
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val side = key.side.toFloatingPointerEdgeSide()
        val returnKey = AppNavKey.FloatingPointerEdgeActionPick(key.side, key.slotIndex)
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { keyEventAction ->
                viewModel.setFloatingPointerEdgeBarSlotAction(
                    side,
                    key.slotIndex,
                    keyEventAction,
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }
}
