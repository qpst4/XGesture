package com.slideindex.app.ui.navigation

import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.ActionPickerCatalogPolicy
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.InwardCompoundBranch
import com.slideindex.app.gesture.directionFamily
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.gesture.TriggerRectanglePresetLogic
import com.slideindex.app.gesture.preferredTriggerMode
import com.slideindex.app.multitouch.encodePathStrokes
import com.slideindex.app.ui.MultiTouchGestureSettingsScreen
import com.slideindex.app.ui.viewmodel.MultiTouchGestureViewModel
import com.slideindex.app.gesture.supportsAction
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.overlay.LayoutPreviewContent
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppLaunchPolicy
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowUiSettings
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.slotAction
import com.slideindex.app.settings.activeBubbleStyle
import com.slideindex.app.settings.activeCapsuleStyle
import com.slideindex.app.settings.activeWaveStyle
import com.slideindex.app.settings.androidBackColorSource
import com.slideindex.app.settings.defaultTriggerModeFor
import com.slideindex.app.settings.descRes
import com.slideindex.app.settings.displayTriggerMode
import com.slideindex.app.settings.slotTriggerMode
import com.slideindex.app.settings.gestureConfigSide
import com.slideindex.app.settings.resolvedFreeWindowMode
import com.slideindex.app.settings.resolvedLaunchPolicy
import com.slideindex.app.settings.titleRes
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.withAddedBottomTriggerHandle
import com.slideindex.app.settings.withAddedTopTriggerHandle
import com.slideindex.app.settings.withAddedTriggerHandlePair
import com.slideindex.app.settings.withDefaultTriggerModeSynced
import com.slideindex.app.settings.withGestureSlotsMirroredFromSide
import com.slideindex.app.settings.withRemovedTriggerHandle
import com.slideindex.app.settings.withSlotConfigSynced
import com.slideindex.app.settings.withSyncedTriggerHandle
import com.slideindex.app.settings.withSyncedTriggerHandleDesignState
import com.slideindex.app.settings.withTriggerAlignOppositeDesign
import com.slideindex.app.settings.withTriggerAlignOppositeGestures
import com.slideindex.app.settings.withTriggerAlignOppositeSide
import com.slideindex.app.settings.withUpdatedTriggerHandle
import com.slideindex.app.settings.withUpdatedTriggerHandleDistances
import com.slideindex.app.settings.withUpdatedTriggerHandleEdgeWidth
import com.slideindex.app.settings.withUpdatedTriggerHandleEnabled
import com.slideindex.app.ui.HapticSettingsScreen
import com.slideindex.app.ui.InteractionAppearanceSettingsScreen
import com.slideindex.app.ui.AppKeepAliveSettingsScreen
import com.slideindex.app.ui.PrivilegeModeSettingsScreen
import com.slideindex.app.ui.CornerGestureInteractionScreen
import com.slideindex.app.ui.CornerGestureSettingsScreen
import com.slideindex.app.ui.CornerGestureSlotsSettingsScreen
import com.slideindex.app.ui.ExcludedAppPickScreen
import com.slideindex.app.ui.ExcludedAppsScreen
import com.slideindex.app.ui.FreeWindowLayoutEditorSession
import com.slideindex.app.ui.FreeWindowSettingsScreen
import com.slideindex.app.ui.GestureActionPickerScreen
import com.slideindex.app.ui.GestureAngleSettingsScreen
import com.slideindex.app.ui.SystemBackGestureWidthSettingsScreen
import com.slideindex.app.gesture.PointerSwipeConfig
import com.slideindex.app.ui.GestureExecuteShellCommandScreen
import com.slideindex.app.ui.PointerSwipeConfigScreen
import com.slideindex.app.ui.GestureSimulateKeyEventScreen
import com.slideindex.app.ui.HiddenAppsScreen
import com.slideindex.app.ui.LayoutSettingsScreen
import com.slideindex.app.ui.MainScreen
import com.slideindex.app.ui.QuickLauncherPanelPickScreen
import com.slideindex.app.ui.SettingRadioRow
import com.slideindex.app.ui.SettingsRadioPickerScreen
import com.slideindex.app.ui.ShakeGestureBlacklistScreen
import com.slideindex.app.ui.SideGestureSettingsScreen
import com.slideindex.app.ui.FingertipRingSettingsScreen
import com.slideindex.app.ui.SideGestureSlotConfigScreen
import com.slideindex.app.ui.SideGestureTriggerModePickerScreen
import com.slideindex.app.ui.TriggerAppearanceSettingsScreen
import com.slideindex.app.ui.TriggerCollectionScreen
import com.slideindex.app.ui.TriggerDesignSettingsScreen
import com.slideindex.app.ui.animationstyle.AndroidBackStyleSettingsScreen
import com.slideindex.app.ui.animationstyle.AnimationStyleSelectScreen
import com.slideindex.app.ui.animationstyle.BubbleStyleSettingsScreen
import com.slideindex.app.ui.animationstyle.CapsuleStyleSettingsScreen
import com.slideindex.app.ui.animationstyle.WaveStyleSettingsScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickActivityScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.picker.MyShortcutsFolderScreen
import com.slideindex.app.ui.picker.PresetShortcutsFolderScreen
import com.slideindex.app.ui.requestPermissionForAdjustAction
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.trigger.TriggerLandscapeOrientationEffect
import com.slideindex.app.ui.trigger.TriggerSettingsLandscapeSession
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel
import com.slideindex.app.ui.viewmodel.HomeDetailSettingsViewModel
import com.slideindex.app.ui.viewmodel.HomeViewModel
import com.slideindex.app.ui.viewmodel.KeepAliveSettingsViewModel
import com.slideindex.app.ui.viewmodel.MainNavHomeEffects
import com.slideindex.app.ui.viewmodel.MainNavKeepAliveEffects

fun NavEntryBuilder.homeNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.HomeMain> {
        val permissions = ctx.collectPermissions()
        val homeEffects = remember(ctx) { MainNavHomeEffects(ctx) }
        val viewModel: HomeViewModel = hiltViewModel<HomeViewModel, HomeViewModel.Factory> { factory ->
            factory.create(homeEffects)
        }
        val settings by viewModel.homeMainSettings.collectAsStateWithLifecycle()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val extensionHubSettings by viewModel.extensionHubSettings.collectAsStateWithLifecycle()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val keyboardTriggerBehavior = if (isLandscape) {
            gestureSettings.keyboardTriggerBehaviorLandscape
        } else {
            gestureSettings.keyboardTriggerBehaviorPortrait
        }
        val keyboardTriggerNarrowPercent = if (isLandscape) {
            gestureSettings.keyboardTriggerNarrowPercentLandscape
        } else {
            gestureSettings.keyboardTriggerNarrowPercentPortrait
        }
        var privilegedAccessGranted by remember { mutableStateOf(false) }
        var rootAccessGranted by remember { mutableStateOf(false) }
        LaunchedEffect(appSettings.privilegeMode, permissions.shizukuGranted) {
            withContext(Dispatchers.IO) {
                privilegedAccessGranted = com.slideindex.app.util.TaskManagerUtil.hasPrivilegedAccess()
                rootAccessGranted = when (appSettings.privilegeMode) {
                    com.slideindex.app.settings.PrivilegeMode.ROOT ->
                        com.slideindex.app.util.TaskManagerUtil.hasPrivilegedAccess()
                    com.slideindex.app.settings.PrivilegeMode.SHIZUKU -> true
                }
            }
        }
        MainScreen(
            settings = settings,
            cornerGestureSettings = overlaySettings.cornerGestureSettings,
            privilegeMode = appSettings.privilegeMode,
            privilegedAccessGranted = privilegedAccessGranted,
            rootAccessGranted = rootAccessGranted,
            notificationGranted = permissions.notificationGranted,
            shizukuGranted = permissions.shizukuGranted,
            accessibilityGranted = permissions.accessibilityGranted,
            overlayGranted = permissions.overlayGranted,
            batteryOptimizationExempt = permissions.batteryOptimizationExempt,
            onRequestNotification = { viewModel.requestNotificationPermission() },
            onRequestShizuku = { viewModel.requestShizuku() },
            onRequestRootAccess = { ctx.navigate(AppNavKey.HomePrivilegeMode) },
            onRequestAccessibility = { viewModel.openAccessibilitySettings() },
            onRequestOverlay = { ctx.openOverlaySettings() },
            onRequestBatteryOptimization = { ctx.requestBatteryOptimization() },
            onGestureEnabledChange = { enabled -> viewModel.setServiceEnabled(enabled) },
            onOpenPrivilegeModeSettings = { ctx.navigate(AppNavKey.HomePrivilegeMode) },
            onOpenAppKeepAliveSettings = { ctx.navigate(AppNavKey.HomeAppKeepAlive) },
            onOpenFloatBallSettings = { ctx.navigate(AppNavKey.FloatBall) },
            onOpenPickSettings = { ctx.navigate(AppNavKey.FloatBallPick) },
            pickOcrFallbackEnabled = appSettings.floatBallOcrFallbackEnabled,
            extensionHubSettings = extensionHubSettings,
            onOpenSearchPanel = { ctx.navigate(AppNavKey.SearchPanel) },
            onOpenFloatingPointer = { ctx.navigate(AppNavKey.FloatingPointer) },
            onOpenFreeWindowSettings = { ctx.navigate(AppNavKey.HomeFreeWindow) },
            onOpenExcludedAppsSettings = { ctx.navigate(AppNavKey.HomeExcludedApps) },
            onOpenPreviousAppBlacklist = { ctx.navigate(AppNavKey.HomePreviousAppBlacklist) },
            onOpenInteractionAppearanceSettings = { ctx.navigate(AppNavKey.HomeInteractionAppearance) },
            onOpenTriggerCollection = { ctx.navigate(AppNavKey.HomeTriggerCollection) },
            onOpenGestureAppProfiles = { ctx.navigate(AppNavKey.HomeGestureAppProfiles) },
            gestureAppProfileCount = appSettings.gestureProfiles.size,
            onOpenCornerWheel = { ctx.navigate(AppNavKey.HomeCornerGesture) },
            onOpenMultiTouchGesture = { ctx.navigate(AppNavKey.ExtensionMultiTouchGesture) },
            onOpenGestureAngle = { ctx.navigate(AppNavKey.HomeGestureAngle) },
            onOpenSystemBackGestureWidth = { ctx.navigate(AppNavKey.HomeSystemBackGestureWidth) },
            onOpenAnimationStyleSelect = { ctx.navigate(AppNavKey.HomeAnimationStyleSelect) },
            onGestureHintEnabledChange = { enabled -> viewModel.setGestureHintEnabled(enabled) },
            onHideTriggerInLandscapeChange = { enabled -> viewModel.setHideTriggerInLandscape(enabled) },
            onHideTriggerOnLockScreenChange = { enabled -> viewModel.setHideTriggerOnLockScreen(enabled) },
            onHideTriggerOnLauncherChange = { enabled -> viewModel.setHideTriggerOnLauncher(enabled) },
            keyboardTriggerBehavior = keyboardTriggerBehavior,
            keyboardTriggerNarrowPercent = keyboardTriggerNarrowPercent,
            onKeyboardTriggerBehaviorChange = { behavior ->
                viewModel.setKeyboardTriggerBehavior(behavior, isLandscape)
            },
            onKeyboardTriggerNarrowPercentChange = { percent ->
                viewModel.setKeyboardTriggerNarrowPercent(percent, isLandscape)
            },
            bottomContentPadding = ctx.rootBottomContentPadding,
            bottomNavReselectCount = ctx.bottomNavReselectCount,
        )
    }

    hiltEntry<AppNavKey.HomeInteractionAppearance> {
        val permissions = ctx.collectPermissions()
        val homeEffects = remember(ctx) { MainNavHomeEffects(ctx) }
        val viewModel: HomeViewModel = hiltViewModel<HomeViewModel, HomeViewModel.Factory> { factory ->
            factory.create(homeEffects)
        }
        val settings by viewModel.homeMainSettings.collectAsStateWithLifecycle()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val keyboardTriggerBehavior = if (isLandscape) {
            gestureSettings.keyboardTriggerBehaviorLandscape
        } else {
            gestureSettings.keyboardTriggerBehaviorPortrait
        }
        val keyboardTriggerNarrowPercent = if (isLandscape) {
            gestureSettings.keyboardTriggerNarrowPercentLandscape
        } else {
            gestureSettings.keyboardTriggerNarrowPercentPortrait
        }
        val gestureActive = settings.serviceEnabled &&
            permissions.accessibilityGranted &&
            permissions.notificationGranted
        InteractionAppearanceSettingsScreen(
            settings = settings,
            gestureActive = gestureActive,
            keyboardTriggerBehavior = keyboardTriggerBehavior,
            keyboardTriggerNarrowPercent = keyboardTriggerNarrowPercent,
            onKeyboardTriggerBehaviorChange = { behavior ->
                viewModel.setKeyboardTriggerBehavior(behavior, isLandscape)
            },
            onKeyboardTriggerNarrowPercentChange = { percent ->
                viewModel.setKeyboardTriggerNarrowPercent(percent, isLandscape)
            },
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onIconPlateModeChange = viewModel::setIconPlateMode,
            iconPlateMode = com.slideindex.app.settings.IconPlateMode.fromId(settings.iconPlateModeId),
            onOpenHapticSettings = { ctx.navigate(AppNavKey.HomeHapticSettings) },
            onSwipeDismissEnabledChange = viewModel::setSwipeDismissEnabled,
            onPredictiveBackEnabledChange = { enabled ->
                viewModel.setPredictiveBackEnabled(enabled)
                // ⚠️ 这里**只写 true、绝不写 false**：flag 写成 false 会让系统退回"兼容注入 KEYCODE_BACK"，
                // 而那条路在真机上到不了 Activity 的返回分发器 → 边缘手势返回彻底失效（§0.16.26）。
                // 但**写 true + 重建这一次不能省**：真机实测"开关一开、跟手就回来"靠的就是这两下
                // （写完之后必须重建，新窗口才会按新 flag 记账）。
                ctx.activity.applyPredictiveBackEnabled(true)
                ctx.activity.recreateWithoutTransition()
            },
            onAppUiLanguageChange = { language ->
                viewModel.setAppUiLanguage(language)
                com.slideindex.app.util.AppLocaleApplier.apply(ctx.activity, language, refreshOverlays = true)
                ctx.activity.recreateWithoutTransition()
            },
            onDynamicColorChange = viewModel::setDynamicColorEnabled,
            onThemeColorChange = viewModel::setThemeColor,
            onThemePaletteStyleChange = viewModel::setThemePaletteStyle,
            onThemeModeChange = viewModel::setThemeMode,
            onCustomColorChange = viewModel::setCustomColorEnabled,
            onDarkBackgroundStyleChange = viewModel::setDarkBackgroundStyle,
            onThemeColorSpecChange = viewModel::setThemeColorSpec,
            onBottomNavStyleChange = viewModel::setBottomNavStyle,
            onBottomNavModeChange = viewModel::setBottomNavMode,
            onBottomNavGlassEnabledChange = viewModel::setBottomNavGlassEnabled,
            onTopAppBarBlurStyleChange = viewModel::setTopAppBarBlurStyle,
            onUiDensityScaleChange = viewModel::setUiDensityScale,
            onBottomNavBlurRadiusChange = viewModel::setBottomNavBlurRadiusDp,
            onBottomNavBlurPreviewChange = ctx.onBottomNavBlurPreviewChange,
            onBottomNavBlurPreviewStop = ctx.onBottomNavBlurPreviewStop,
            overlayBlurEnabled = settings.overlayBlurEnabled,
            onOverlayBlurEnabledChange = viewModel::setOverlayBlurEnabled,
        )
    }

    hiltEntry<AppNavKey.HomePrivilegeMode> {
        val homeEffects = remember(ctx) { MainNavHomeEffects(ctx) }
        val viewModel: HomeViewModel = hiltViewModel<HomeViewModel, HomeViewModel.Factory> { factory ->
            factory.create(homeEffects)
        }
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        var rootAvailable by remember { mutableStateOf(false) }
        var shizukuReady by remember { mutableStateOf(false) }
        LaunchedEffect(appSettings.privilegeMode) {
            withContext(Dispatchers.IO) {
                shizukuReady = com.slideindex.app.util.TaskManagerUtil.hasShizukuPermission()
                rootAvailable = if (appSettings.privilegeMode == com.slideindex.app.settings.PrivilegeMode.ROOT) {
                    com.slideindex.app.util.TaskManagerUtil.hasPrivilegedAccess()
                } else {
                    false
                }
            }
        }
        PrivilegeModeSettingsScreen(
            privilegeMode = appSettings.privilegeMode,
            shizukuGranted = shizukuReady,
            rootAvailable = rootAvailable,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onPrivilegeModeChange = viewModel::setPrivilegeMode,
            onRequestShizuku = { ctx.requestShizuku() },
        )
    }

    hiltEntry<AppNavKey.HomeHapticSettings> {
        val homeEffects = remember(ctx) { MainNavHomeEffects(ctx) }
        val viewModel: HomeViewModel = hiltViewModel<HomeViewModel, HomeViewModel.Factory> { factory ->
            factory.create(homeEffects)
        }
        val settings by viewModel.homeMainSettings.collectAsStateWithLifecycle()
        HapticSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeInteractionAppearance) },
            onHapticEnabledChange = viewModel::setHapticEnabled,
            onHapticStrengthChange = viewModel::setHapticStrength,
            onHapticShortThresholdEnabledChange = viewModel::setHapticShortThresholdEnabled,
            onHapticCompoundTurnEnabledChange = viewModel::setHapticCompoundTurnEnabled,
            onHapticReturnSwipeEnabledChange = viewModel::setHapticReturnSwipeEnabled,
            onHapticHoverEnabledChange = viewModel::setHapticHoverEnabled,
            onHapticLongPressEnabledChange = viewModel::setHapticLongPressEnabled,
            onHapticLongThresholdEnabledChange = viewModel::setHapticLongThresholdEnabled,
            onHapticSlotLetterEnabledChange = viewModel::setHapticSlotLetterEnabled,
            onHapticSlotAppEnabledChange = viewModel::setHapticSlotAppEnabled,
            onHapticConfirmLaunchEnabledChange = viewModel::setHapticConfirmLaunchEnabled,
            onHapticActionConfirmEnabledChange = viewModel::setHapticActionConfirmEnabled,
        )
    }

    hiltEntry<AppNavKey.HomeAppKeepAlive> {
        val keepAliveEffects = remember(ctx) { MainNavKeepAliveEffects(ctx) }
        val viewModel: KeepAliveSettingsViewModel =
            hiltViewModel<KeepAliveSettingsViewModel, KeepAliveSettingsViewModel.Factory> { factory ->
                factory.create(keepAliveEffects)
            }
        val keepAliveSettings by viewModel.keepAliveUiSettings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        var privilegedAccessGranted by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                privilegedAccessGranted = com.slideindex.app.util.TaskManagerUtil.hasPrivilegedAccess()
            }
        }
        AppKeepAliveSettingsScreen(
            hideFromRecents = keepAliveSettings.hideFromRecents,
            batteryOptimizationExempt = permissions.batteryOptimizationExempt,
            accessibilityKeepAliveEnabled = keepAliveSettings.accessibilityKeepAliveEnabled,
            writeSecureSettingsGranted = permissions.writeSecureSettingsGranted,
            privilegedAccessGranted = privilegedAccessGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onRequestBatteryOptimization = { ctx.requestBatteryOptimization() },
            // 与「剪贴板后台监听」页的「自启动与后台保护管理」一致：直接跳本应用的应用信息/权限详情。
            onRequestAutoStart = {
                runCatching {
                    ctx.activity.startActivity(
                        com.slideindex.app.util.PermissionHelper.appListSettingsIntent(ctx.activity),
                    )
                }
            },
            onHideFromRecentsChange = viewModel::setHideFromRecents,
            onAccessibilityKeepAliveChange = viewModel::setAccessibilityKeepAliveEnabled,
            onRequestSecureSettingsGrant = { ctx.requestSecureSettingsGrant() },
        )
    }

    hiltEntry<AppNavKey.HomeExcludedApps> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        ExcludedAppsScreen(
            settings = settings,
            usageAccessGranted = permissions.usageAccessGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onRequestUsageAccess = { ctx.openUsageAccessSettings() },
            onOpenAddApp = { ctx.navigate(AppNavKey.HomeExcludedAppsPick) },
            onRemoveExcludedApp = { packageName -> viewModel.removeExcludedTriggerApp(packageName) },
            onExcludedAppScopesChange = { packageName, scopes ->
                viewModel.setExcludedAppScopes(packageName, scopes)
            },
        )
    }

    hiltEntry<AppNavKey.HomeExcludedAppsPick> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ExcludedAppPickScreen(
            excludedPackages = settings.excludedAppScopes.keys,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeExcludedApps) },
            onConfirmAdd = { packageName, scopes ->
                viewModel.addExcludedTriggerApp(packageName)
                viewModel.setExcludedAppScopes(packageName, scopes)
            },
        )
    }

    hiltEntry<AppNavKey.HomeFreeWindow> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val freeWindowSettings by viewModel.freeWindowUiSettings.collectAsStateWithLifecycle()
        val settings = freeWindowSettings.toMinimalAppSettings()
        FreeWindowSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onEnabledChange = viewModel::setFreeWindowEnabled,
            onLongPressDurationChange = viewModel::setLongPressLaunchDurationMs,
            onLaunchPolicyChange = viewModel::setAppLaunchPolicyId,
            onFollowTriggerSideChange = viewModel::setFreeWindowFollowTriggerSide,
            onOpenMode = { ctx.navigate(AppNavKey.HomeFreeWindowMode) },
            onOpenLayoutEditor = { FreeWindowLayoutEditorSession.open() },
        )
    }

    hiltEntry<AppNavKey.HomeFreeWindowMode> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val freeWindowSettings by viewModel.freeWindowUiSettings.collectAsStateWithLifecycle()
        val settings = freeWindowSettings.toMinimalAppSettings()
        val selectedMode = settings.resolvedFreeWindowMode()
        val freeWindowModeItems = buildList {
            FreeWindowMode.entries.forEach { mode ->
                add(
                    settingsCardScopeItem("mode-${mode.id}") {
                        SettingRadioRow(
                            title = ctx.activity.getString(mode.titleRes),
                            subtitle = ctx.activity.getString(mode.descRes),
                            selected = mode.id == selectedMode.id,
                            onClick = {
                                viewModel.setFreeWindowModeId(mode.id)
                                ctx.navigateBackTo(AppNavKey.HomeFreeWindow)
                            },
                        )
                    },
                )
            }
        }
        SettingsRadioPickerScreen(
            title = ctx.activity.getString(com.slideindex.app.R.string.free_window_mode_dialog_title),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeFreeWindow) },
            items = freeWindowModeItems,
        )
    }

    hiltEntry<AppNavKey.HomeTriggerCollection> {
        HomeTriggerCollectionRoute(ctx)
    }

    hiltEntry<AppNavKey.HomeTriggerCollectionLandscape> {
        HomeTriggerCollectionRoute(ctx, initialManualLandscapeOverride = true)
    }

    gestureAppProfileNavEntries(ctx)

    multiTouchNavEntries(ctx, AppNavKey.HomeMain)

    hiltEntry<AppNavKey.HomeCornerGesture> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings().copy(
            cornerGestureSettings = overlaySettings.cornerGestureSettings,
        )
        val permissions = ctx.collectPermissions()
        CornerGestureSettingsScreen(
            settings = settings,
            serviceEnabled = ctx.gestureActive(gestureSettings.serviceEnabled, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onEnabledChange = viewModel::setCornerGestureEnabled,
            onLeftEnabledChange = viewModel::setCornerGestureLeftEnabled,
            onRightEnabledChange = viewModel::setCornerGestureRightEnabled,
            onOpenInteractionAppearance = { ctx.navigate(AppNavKey.HomeCornerGestureInteraction) },
            onOpenSlots = { ctx.navigate(AppNavKey.HomeCornerGestureSlots) },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureInteraction> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings().copy(
            cornerGestureSettings = overlaySettings.cornerGestureSettings,
        )
        val permissions = ctx.collectPermissions()
        CornerGestureInteractionScreen(
            settings = settings,
            serviceEnabled = ctx.gestureActive(gestureSettings.serviceEnabled, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeCornerGesture) },
            onVerticalEdgeWidthChange = viewModel::setCornerGestureVerticalEdgeWidthDp,
            onVerticalEdgeHeightChange = viewModel::setCornerGestureVerticalEdgeHeightDp,
            onHorizontalEdgeWidthChange = viewModel::setCornerGestureHorizontalEdgeWidthDp,
            onHorizontalEdgeHeightChange = viewModel::setCornerGestureHorizontalEdgeHeightDp,
            onZonePreviewStart = { ctx.startCornerZonePreview() },
            onZonePreviewStop = { ctx.stopCornerZonePreview() },
            onZonePreviewDimensionsChange = { vWidth, vHeight, hWidth, hHeight ->
                ctx.updateCornerZonePreview(vWidth, vHeight, hWidth, hHeight)
            },
            onTriggerSlopChange = viewModel::setCornerGestureTriggerSlopDp,
            onHideInLandscapeChange = viewModel::setCornerGestureHideInLandscape,
            onLandscapePreventFalseTouchChange = viewModel::setCornerGestureLandscapePreventFalseTouch,
            onOverrideSystemNavChange = viewModel::setCornerGestureOverrideSystemNav,
            onInnerDiameterChange = viewModel::setCornerGestureInnerDiameterDp,
            onRingSpacingChange = viewModel::setCornerGestureRingSpacingDp,
            onBubbleSizeChange = viewModel::setCornerGestureBubbleSizeDp,
            onSlotIconShapeChange = viewModel::setCornerGestureSlotIconShape,
            onCancelOutsideWheelChange = viewModel::setCornerGestureCancelOutsideWheel,
            onProgressiveLayersChange = viewModel::setCornerGestureProgressiveLayers,
            onSlotHapticChange = viewModel::setCornerGestureSlotHaptic,
            onShowSelectedNameChange = viewModel::setCornerGestureShowSelectedName,
            onShowEditButtonChange = viewModel::setCornerGestureShowEditButton,
            onSelectedHintIconSizeChange = viewModel::setCornerGestureSelectedHintIconSizeDp,
            onBackgroundStyleChange = viewModel::setCornerGestureBackgroundStyle,
            onBlurDpChange = viewModel::setCornerGestureBlurDp,
            onDimPercentChange = viewModel::setCornerGestureDimPercent,
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureSlots> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings().copy(
            cornerGestureSettings = overlaySettings.cornerGestureSettings,
        )
        val permissions = ctx.collectPermissions()
        CornerGestureSlotsSettingsScreen(
            settings = settings,
            serviceEnabled = ctx.gestureActive(gestureSettings.serviceEnabled, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeCornerGesture) },
            onUnifiedSlotsChange = viewModel::setCornerGestureUnifiedSlots,
            onLayerCountChange = viewModel::setCornerGestureWheelLayerCount,
            onOpenInnerZoneActionPick = { ctx.navigate(AppNavKey.HomeCornerGestureInnerZoneActionPick) },
            onOpenLeftSlotActionPick = { slotIndex ->
                ctx.navigate(AppNavKey.HomeCornerGestureSlotEditor("left", slotIndex))
            },
            onOpenRightSlotActionPick = { slotIndex ->
                ctx.navigate(AppNavKey.HomeCornerGestureSlotEditor("right", slotIndex))
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureInnerZoneActionPick> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val corner = overlaySettings.cornerGestureSettings
        val returnKey = AppNavKey.HomeCornerGestureSlots
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            current = corner.innerZoneAction,
            catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.CornerInnerZone),
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                viewModel.setCornerGestureInnerZoneAction(action)
                ctx.navigateBackTo(returnKey)
            },
            onOpenMyShortcuts = { ctx.navigate(AppNavKey.HomeCornerGestureInnerZoneMyShortcuts) },
            onOpenPresetShortcuts = { ctx.navigate(AppNavKey.HomeCornerGestureInnerZonePresetShortcuts) },
            onOpenPickApp = { ctx.navigate(AppNavKey.HomeCornerGestureInnerZonePickApp) },
            onOpenExecuteShellCommand = { cmd -> ctx.navigate(AppNavKey.HomeCornerGestureInnerZoneShellCommand(cmd)) },
            onOpenSimulateKeyEvent = { keyEvent ->
                ctx.navigate(
                    AppNavKey.HomeCornerGestureInnerZoneSimulateKeyEvent(
                        initialKeyCode = keyEvent.keyCode,
                        initialKeyName = keyEvent.keyName,
                        initialIsLongPress = keyEvent.isLongPress,
                    ),
                )
            },
            includeCornerInnerZoneActions = true,
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureInnerZoneMyShortcuts> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val corner = overlaySettings.cornerGestureSettings
        val returnKey = AppNavKey.HomeCornerGestureInnerZoneActionPick
        MyShortcutsFolderScreen(
            activityShortcuts = appSettings.activityShortcuts,
            onBack = { ctx.navigateBackTo(returnKey) },
            onBrowseNewShortcut = { ctx.navigate(AppNavKey.HomeCornerGestureInnerZonePickApp) },
            currentAction = corner.innerZoneAction,
            onSelectRadio = { action ->
                viewModel.setCornerGestureInnerZoneAction(action)
                ctx.navigateBackTo(AppNavKey.HomeCornerGestureSlots)
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureInnerZonePresetShortcuts> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val corner = overlaySettings.cornerGestureSettings
        val returnKey = AppNavKey.HomeCornerGestureInnerZoneActionPick
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            currentAction = corner.innerZoneAction,
            onSelectRadio = { action ->
                viewModel.setCornerGestureInnerZoneAction(action)
                ctx.navigateBackTo(AppNavKey.HomeCornerGestureSlots)
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureInnerZonePickApp> {
        val returnKey = AppNavKey.HomeCornerGestureInnerZoneActionPick
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                ctx.navigate(AppNavKey.HomeCornerGestureInnerZonePickActivity(app.packageName))
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureInnerZonePickActivity> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                viewModel.setCornerGestureInnerZoneAction(
                    GestureAction.LaunchShortcut.component(
                        "${activity.packageName}/${activity.className}",
                        activity.label,
                    ),
                )
                ctx.navigateBackTo(AppNavKey.HomeCornerGestureSlots)
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureInnerZoneShellCommand> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.HomeCornerGestureSlots
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                viewModel.setCornerGestureInnerZoneAction(GestureAction.ExecuteShellCommand(command))
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    cornerGestureSlotNavEntries(ctx)

    hiltEntry<AppNavKey.HomeSideGestures> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val side = key.side.toPanelSide()
        SideGestureSettingsScreen(
            side = side,
            handleId = key.handleId,
            settings = settings,
            serviceEnabled = true,
            onBack = {
                ctx.stopTriggerPreview()
                ctx.navigateBackTo(triggerScope.collectionKey)
            },
            onPreviewStart = {
                ctx.startFocusedTriggerPreview(side, key.handleId)
            },
            onPreviewStop = {
                ctx.releaseFocusedTriggerPreview()
            },
            onOpenAppearanceSettings = {
                ctx.navigate(
                    AppNavKey.HomeSideGesturesAppearance(key.side, key.handleId, key.profileId),
                )
            },
            onOpenDesignSettings = {
                ctx.navigate(
                    AppNavKey.HomeSideGesturesDesign(key.side, key.handleId, key.profileId),
                )
            },
            onOpenDefaultModePick = {
                ctx.navigate(
                    AppNavKey.HomeSideGesturesDefaultMode(key.side, key.handleId, key.profileId),
                )
            },
            onOpenSlotConfig = { trigger ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotConfig(
                        side = key.side,
                        handleId = key.handleId,
                        triggerId = trigger.id,
                        profileId = key.profileId,
                    ),
                )
            },
            onAlignOppositeGesturesChange = { enabled, mirrorSourceSide ->
                if (enabled && mirrorSourceSide != null) {
                    triggerScope.mutate(
                        optimistic = true,
                        global = {
                            viewModel.setTriggerAlignOppositeGestures(
                                key.handleId,
                                mirrorSourceSide,
                                true,
                            )
                        },
                    ) { working ->
                        var updated = working.withTriggerAlignOppositeGestures(key.handleId, true)
                        if (mirrorSourceSide.isHorizontalEdge) {
                            // 镜像本身已包含"把该侧默认触发模式同步过去"，与 EdgeSettingsMutator 一致。
                            updated = updated.withGestureSlotsMirroredFromSide(
                                mirrorSourceSide,
                                key.handleId,
                            )
                        }
                        updated
                    }
                } else if (!enabled) {
                    triggerScope.mutate(
                        optimistic = true,
                        global = {
                            viewModel.setTriggerAlignOppositeGestures(key.handleId, side, false)
                        },
                    ) { it.withTriggerAlignOppositeGestures(key.handleId, false) }
                }
            },
            onSwipeHoverDurationChange = { value ->
                triggerScope.mutate(
                    global = { viewModel.setSwipeHoverDurationMs(value) },
                ) { it.withSwipeHoverDurationMs(value) }
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGesturesDefaultMode> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val gesturesKey = AppNavKey.HomeSideGestures(key.side, key.handleId, key.profileId)
        SideGestureTriggerModePickerScreen(
            title = ctx.activity.getString(com.slideindex.app.R.string.default_trigger_mode),
            current = settings.defaultTriggerModeFor(configSide),
            action = GestureAction.None,
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            includeDefaultOption = false,
            onBack = { ctx.navigateBackTo(gesturesKey) },
            onSelect = { mode ->
                triggerScope.mutate(
                    optimistic = true,
                    global = { viewModel.setDefaultTriggerMode(side, mode, key.handleId) },
                ) { it.withDefaultTriggerModeSynced(side, mode, key.handleId) }
                ctx.navigateBackTo(gesturesKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureSlotConfig> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val side = key.side.toPanelSide()
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val gesturesKey = AppNavKey.HomeSideGestures(key.side, key.handleId, key.profileId)
        SideGestureSlotConfigScreen(
            side = side,
            handleId = key.handleId,
            trigger = trigger,
            settings = settings,
            profileId = key.profileId,
            onBack = { ctx.navigateBackTo(gesturesKey) },
            onOpenActionPick = { tierTrigger ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotActionPick(
                        key.side,
                        key.handleId,
                        tierTrigger.id,
                        key.profileId,
                    ),
                )
            },
            onOpenModePick = { tierTrigger ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotModePick(
                        key.side,
                        key.handleId,
                        tierTrigger.id,
                        key.profileId,
                    ),
                )
            },
            onOpenShellCommand = { tierTrigger, command ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotShellCommand(
                        side = key.side,
                        handleId = key.handleId,
                        triggerId = tierTrigger.id,
                        initialCommand = command,
                        profileId = key.profileId,
                    ),
                )
            },
            onOpenQuickLauncherPanel = { tierTrigger, panelId ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotQuickLauncherPanel(
                        side = key.side,
                        handleId = key.handleId,
                        triggerId = tierTrigger.id,
                        panelId = panelId,
                        profileId = key.profileId,
                    ),
                )
            },
            onOpenFingertipRingConfig = { tierTrigger ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureFingertipRing(
                        side = key.side,
                        handleId = key.handleId,
                        triggerId = tierTrigger.id,
                        profileId = key.profileId,
                    ),
                )
            },
            onTriggerDoubleTapIntervalChange = { value ->
                triggerScope.mutate(
                    global = { viewModel.setTriggerDoubleTapIntervalMs(value) },
                ) { it.withTriggerDoubleTapIntervalMs(value) }
            },
            appCarouselSettings = settings.appCarouselSwitcher,
            onAppCarouselSettingsChange = viewModel::setAppCarouselSwitcherSettings,
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureSlotQuickLauncherPanel> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val slotConfigKey = AppNavKey.HomeSideGestureSlotConfig(
            key.side,
            key.handleId,
            key.triggerId,
            key.profileId,
        )
        QuickLauncherPanelPickScreen(
            settings = settings,
            currentPanelId = key.panelId,
            onBack = { ctx.navigateBackTo(slotConfigKey) },
            onSelect = { panel ->
                val action = GestureAction.QuickLauncher(panel.id)
                val mode = settings.slotTriggerMode(configSide, trigger, key.handleId)
                triggerScope.mutate(
                    optimistic = true,
                    global = { viewModel.setSlotConfig(side, trigger, action, mode, key.handleId) },
                ) { it.withSlotConfigSynced(side, trigger, action, mode, key.handleId) }
                ctx.navigateBackTo(slotConfigKey)
            },
        )
    }

    /*
     * 指环的槽位动作（`fingertipRingSlotActions` / 半径 / 图标尺寸 / 槽位数）**不在档快照里**：
     * 它们是全局设置。所以档内那四页一律不可达——槽位配置页里对应的入口在档内被置灰并注明
     * 「全局设置，档内不适用」（见 `SideGestureSlotConfigScreen`）；这里再兜一道，万一被走到
     * 就直接退回触钮集合页，绝不在档内改到全局。
     */
    hiltEntry<AppNavKey.HomeSideGestureFingertipRing> { key ->
        if (key.profileId != null) {
            GestureProfileGlobalOnlyRedirect(ctx, key.profileId)
            return@hiltEntry
        }
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        FingertipRingSettingsScreen(
            settings = appSettings,
            onBack = {
                ctx.navigateBackTo(
                    AppNavKey.HomeSideGestureSlotConfig(key.side, key.handleId, key.triggerId),
                )
            },
            onSlotCountChange = viewModel::setFingertipRingSlotCount,
            onOrbitRadiusChange = viewModel::setFingertipRingOrbitRadiusPx,
            onIconSizeChange = viewModel::setFingertipRingIconSizePx,
            onOpenSlotActionPick = { slotIndex ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureFingertipRingSlotActionPick(
                        side = key.side,
                        handleId = key.handleId,
                        triggerId = key.triggerId,
                        slotIndex = slotIndex,
                    ),
                )
            },
            onOpenShellCommand = { slotIndex, command ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureFingertipRingShellCommand(
                        side = key.side,
                        handleId = key.handleId,
                        triggerId = key.triggerId,
                        slotIndex = slotIndex,
                        initialCommand = command,
                    ),
                )
            },
            onOpenSwipeConfig = { slotIndex ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureFingertipRingSwipeConfig(
                        side = key.side,
                        handleId = key.handleId,
                        triggerId = key.triggerId,
                        slotIndex = slotIndex,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureFingertipRingSlotActionPick> { key ->
        if (key.profileId != null) {
            GestureProfileGlobalOnlyRedirect(ctx, key.profileId)
            return@hiltEntry
        }
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.HomeSideGestureFingertipRing(key.side, key.handleId, key.triggerId)
        val current = appSettings.fingertipRingSlotActions.getOrElse(key.slotIndex) { GestureAction.None }
        GestureActionPickerScreen(
            trigger = GestureTriggerType.SHORT_SWIPE_IN,
            current = current,
            catalogPolicy = ActionPickerCatalogPolicy.Slot(SlotPickerKind.FingertipRing),
            onDismiss = { ctx.navigateBackTo(returnKey) },
            onSelect = { action ->
                if (action is GestureAction.SimulatePointerSwipe) {
                    ctx.navigate(
                        AppNavKey.HomeSideGestureFingertipRingSwipeConfig(
                            key.side, key.handleId, key.triggerId, key.slotIndex,
                        ),
                    )
                } else {
                    viewModel.setFingertipRingSlotAction(key.slotIndex, action)
                    ctx.navigateBackTo(returnKey)
                }
            },
            onOpenExecuteShellCommand = { cmd ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureFingertipRingShellCommand(
                        key.side, key.handleId, key.triggerId, key.slotIndex, cmd,
                    ),
                )
            },
            onOpenMyShortcuts = {},
            onOpenPresetShortcuts = {},
            onOpenPickApp = {},
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureFingertipRingShellCommand> { key ->
        if (key.profileId != null) {
            GestureProfileGlobalOnlyRedirect(ctx, key.profileId)
            return@hiltEntry
        }
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.HomeSideGestureFingertipRing(key.side, key.handleId, key.triggerId)
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = appSettings.shellCommands,
            onBack = { ctx.navigateBackTo(returnKey) },
            onConfirm = { command ->
                viewModel.setFingertipRingSlotAction(key.slotIndex, GestureAction.ExecuteShellCommand(command))
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureFingertipRingSwipeConfig> { key ->
        if (key.profileId != null) {
            GestureProfileGlobalOnlyRedirect(ctx, key.profileId)
            return@hiltEntry
        }
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val appSettings by viewModel.settings.collectAsStateWithLifecycle()
        val returnKey = AppNavKey.HomeSideGestureFingertipRing(key.side, key.handleId, key.triggerId)
        val current = appSettings.fingertipRingSlotActions.getOrElse(key.slotIndex) {
            GestureAction.SimulatePointerSwipe()
        }
        val initial = (current as? GestureAction.SimulatePointerSwipe)?.config
            ?: PointerSwipeConfig()
        PointerSwipeConfigScreen(
            initialConfig = initial,
            onBack = { ctx.navigateBackTo(returnKey) },
            onConfirm = { config ->
                viewModel.setFingertipRingSlotAction(
                    key.slotIndex,
                    GestureAction.SimulatePointerSwipe(config),
                )
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureSlotActionPick> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val context = LocalContext.current
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val slotConfigKey = AppNavKey.HomeSideGestureSlotConfig(
            key.side,
            key.handleId,
            key.triggerId,
            key.profileId,
        )
        val currentAction = settings.slotAction(configSide, trigger, key.handleId)
        val storedMode = settings.slotTriggerMode(configSide, trigger, key.handleId)
        val resolvedMode = settings.displayTriggerMode(configSide, trigger, key.handleId)
        GestureActionPickerScreen(
            trigger = trigger,
            current = currentAction,
            catalogPolicy = ActionPickerCatalogPolicy.EdgeGesture,
            onDismiss = { ctx.navigateBackTo(slotConfigKey) },
            onSelect = { action ->
                requestPermissionForAdjustAction(context, action)
                val resolved = when (action) {
                    is GestureAction.QuickLauncher -> {
                        val panelId = action.panelId.ifBlank {
                            QuickLauncherPanelDefaults.effectivePanels(settings.quickLauncherPanels).first().id
                        }
                        action.copy(panelId = panelId)
                    }
                    else -> action
                }
                val mode = if (!resolvedMode.supportsAction(resolved, trigger)) {
                    resolved.preferredTriggerMode(trigger) ?: GestureTriggerMode.ON_RELEASE
                } else {
                    storedMode
                }
                triggerScope.mutate(
                    optimistic = true,
                    global = { viewModel.setSlotConfig(side, trigger, resolved, mode, key.handleId) },
                ) { it.withSlotConfigSynced(side, trigger, resolved, mode, key.handleId) }
                ctx.navigateBackTo(slotConfigKey)
            },
            onOpenMyShortcuts = {
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotMyShortcuts(
                        key.side, key.handleId, key.triggerId, key.profileId,
                    ),
                )
            },
            onOpenPresetShortcuts = {
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotPresetShortcuts(
                        key.side, key.handleId, key.triggerId, key.profileId,
                    ),
                )
            },
            onOpenPickApp = {
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotPickApp(
                        key.side, key.handleId, key.triggerId, key.profileId,
                    ),
                )
            },
            onOpenExecuteShellCommand = { cmd ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotShellCommand(
                        key.side, key.handleId, key.triggerId, cmd, key.profileId,
                    ),
                )
            },
            onOpenSimulateKeyEvent = { keyEvent ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotSimulateKeyEvent(
                        key.side,
                        key.handleId,
                        key.triggerId,
                        keyEvent.keyCode,
                        keyEvent.keyName,
                        keyEvent.isLongPress,
                        key.profileId,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureSlotMyShortcuts> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val currentAction = settings.slotAction(configSide, trigger, key.handleId)
        val storedMode = settings.slotTriggerMode(configSide, trigger, key.handleId)
        val resolvedMode = settings.displayTriggerMode(configSide, trigger, key.handleId)
        val slotConfigKey = AppNavKey.HomeSideGestureSlotConfig(
            key.side,
            key.handleId,
            key.triggerId,
            key.profileId,
        )
        val returnKey = AppNavKey.HomeSideGestureSlotActionPick(
            key.side,
            key.handleId,
            key.triggerId,
            key.profileId,
        )
        MyShortcutsFolderScreen(
            activityShortcuts = settings.activityShortcuts,
            onBack = { ctx.navigateBackTo(returnKey) },
            onBrowseNewShortcut = {
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotPickApp(
                        key.side, key.handleId, key.triggerId, key.profileId,
                    ),
                )
            },
            currentAction = currentAction,
            onSelectRadio = { action ->
                val mode = if (!resolvedMode.supportsAction(action, trigger)) {
                    action.preferredTriggerMode(trigger) ?: GestureTriggerMode.ON_RELEASE
                } else {
                    storedMode
                }
                triggerScope.mutate(
                    optimistic = true,
                    global = { viewModel.setSlotConfig(side, trigger, action, mode, key.handleId) },
                ) { it.withSlotConfigSynced(side, trigger, action, mode, key.handleId) }
                ctx.navigateBackTo(slotConfigKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureSlotPresetShortcuts> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val currentAction = settings.slotAction(configSide, trigger, key.handleId)
        val storedMode = settings.slotTriggerMode(configSide, trigger, key.handleId)
        val resolvedMode = settings.displayTriggerMode(configSide, trigger, key.handleId)
        val slotConfigKey = AppNavKey.HomeSideGestureSlotConfig(
            key.side,
            key.handleId,
            key.triggerId,
            key.profileId,
        )
        val returnKey = AppNavKey.HomeSideGestureSlotActionPick(
            key.side,
            key.handleId,
            key.triggerId,
            key.profileId,
        )
        PresetShortcutsFolderScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            currentAction = currentAction,
            onSelectRadio = { action ->
                val mode = if (!resolvedMode.supportsAction(action, trigger)) {
                    action.preferredTriggerMode(trigger) ?: GestureTriggerMode.ON_RELEASE
                } else {
                    storedMode
                }
                triggerScope.mutate(
                    optimistic = true,
                    global = { viewModel.setSlotConfig(side, trigger, action, mode, key.handleId) },
                ) { it.withSlotConfigSynced(side, trigger, action, mode, key.handleId) }
                ctx.navigateBackTo(slotConfigKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureSlotPickApp> { key ->
        val returnKey = AppNavKey.HomeSideGestureSlotActionPick(
            key.side,
            key.handleId,
            key.triggerId,
            key.profileId,
        )
        ActivityShortcutPickAppScreen(
            onBack = { ctx.navigateBackTo(returnKey) },
            onSelectApp = { app ->
                ctx.navigate(
                    AppNavKey.HomeSideGestureSlotPickActivity(
                        side = key.side,
                        handleId = key.handleId,
                        triggerId = key.triggerId,
                        packageName = app.packageName,
                        profileId = key.profileId,
                    ),
                )
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureSlotPickActivity> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val storedMode = settings.slotTriggerMode(configSide, trigger, key.handleId)
        val resolvedMode = settings.displayTriggerMode(configSide, trigger, key.handleId)
        val slotConfigKey = AppNavKey.HomeSideGestureSlotConfig(
            key.side,
            key.handleId,
            key.triggerId,
            key.profileId,
        )
        ActivityShortcutPickActivityScreen(
            packageName = key.packageName,
            onBack = { ctx.backStack.removeLastOrNull() },
            onSelectActivity = { activity ->
                val action = GestureAction.LaunchShortcut.component(
                    "${activity.packageName}/${activity.className}",
                    activity.label,
                )
                val mode = if (!resolvedMode.supportsAction(action, trigger)) {
                    action.preferredTriggerMode(trigger) ?: GestureTriggerMode.ON_RELEASE
                } else {
                    storedMode
                }
                triggerScope.mutate(
                    optimistic = true,
                    global = { viewModel.setSlotConfig(side, trigger, action, mode, key.handleId) },
                ) { it.withSlotConfigSynced(side, trigger, action, mode, key.handleId) }
                ctx.navigateBackTo(slotConfigKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureSlotModePick> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val slotConfigKey = AppNavKey.HomeSideGestureSlotConfig(
            key.side,
            key.handleId,
            key.triggerId,
            key.profileId,
        )
        val currentAction = settings.slotAction(configSide, trigger, key.handleId)
        SideGestureTriggerModePickerScreen(
            title = ctx.activity.getString(com.slideindex.app.R.string.slot_pick_trigger_mode),
            current = settings.slotTriggerMode(configSide, trigger, key.handleId),
            action = currentAction,
            trigger = trigger,
            sideDefaultMode = settings.defaultTriggerModeFor(configSide),
            // 触钮不再提供「默认」模式：每个动作初始就是具体模式
            includeDefaultOption = false,
            onBack = { ctx.navigateBackTo(slotConfigKey) },
            onSelect = { mode ->
                triggerScope.mutate(
                    optimistic = true,
                    global = {
                        viewModel.setSlotConfig(side, trigger, currentAction, mode, key.handleId)
                    },
                ) {
                    it.withSlotConfigSynced(side, trigger, currentAction, mode, key.handleId)
                }
                ctx.navigateBackTo(slotConfigKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureSlotShellCommand> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val slotConfigKey = AppNavKey.HomeSideGestureSlotConfig(
            key.side,
            key.handleId,
            key.triggerId,
            key.profileId,
        )
        val storedMode = settings.slotTriggerMode(configSide, trigger, key.handleId)
        GestureExecuteShellCommandScreen(
            initialCommand = key.initialCommand,
            shellCommands = settings.shellCommands,
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { command ->
                val action = GestureAction.ExecuteShellCommand(command)
                triggerScope.mutate(
                    optimistic = true,
                    global = { viewModel.setSlotConfig(side, trigger, action, storedMode, key.handleId) },
                ) { it.withSlotConfigSynced(side, trigger, action, storedMode, key.handleId) }
                ctx.navigateBackTo(slotConfigKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGestureSlotSimulateKeyEvent> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val side = key.side.toPanelSide()
        val configSide = settings.gestureConfigSide(side, key.handleId)
        val trigger = GestureTriggerType.fromId(key.triggerId) ?: GestureTriggerType.SHORT_SWIPE_IN
        val returnKey = AppNavKey.HomeSideGestureSlotActionPick(
            key.side,
            key.handleId,
            key.triggerId,
            key.profileId,
        )
        val storedMode = settings.slotTriggerMode(configSide, trigger, key.handleId)
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { keyEventAction ->
                triggerScope.mutate(
                    optimistic = true,
                    global = {
                        viewModel.setSlotConfig(side, trigger, keyEventAction, storedMode, key.handleId)
                    },
                ) {
                    it.withSlotConfigSynced(side, trigger, keyEventAction, storedMode, key.handleId)
                }
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeCornerGestureInnerZoneSimulateKeyEvent> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val returnKey = AppNavKey.HomeCornerGestureInnerZoneActionPick
        GestureSimulateKeyEventScreen(
            initialAction = GestureAction.SimulateKeyEvent(
                keyCode = key.initialKeyCode,
                keyName = key.initialKeyName,
                isLongPress = key.initialIsLongPress,
            ),
            onBack = { ctx.backStack.removeLastOrNull() },
            onConfirm = { keyEventAction ->
                viewModel.setCornerGestureInnerZoneAction(keyEventAction)
                ctx.navigateBackTo(returnKey)
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGesturesAppearance> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val side = key.side.toPanelSide()
        val gesturesKey = AppNavKey.HomeSideGestures(key.side, key.handleId, key.profileId)
        TriggerAppearanceSettingsScreen(
            side = side,
            handleId = key.handleId,
            settings = settings,
            serviceEnabled = true,
            profileId = key.profileId,
            onBack = { ctx.navigateBackTo(gesturesKey) },
            onShortSwipeDistanceChange = { value ->
                triggerScope.mutate(
                    global = { viewModel.setShortSwipeDistanceDp(side, key.handleId, value) },
                ) { it.withUpdatedTriggerHandleDistances(side, key.handleId, shortSwipeDistanceDp = value) }
            },
            onLongSwipeDistanceChange = { value ->
                triggerScope.mutate(
                    global = { viewModel.setLongSwipeDistanceDp(side, key.handleId, value) },
                ) { it.withUpdatedTriggerHandleDistances(side, key.handleId, longSwipeDistanceDp = value) }
            },
            onEdgeWidthChange = { value ->
                triggerScope.mutate(
                    global = { viewModel.setTriggerEdgeWidthDp(side, key.handleId, value) },
                ) { it.withUpdatedTriggerHandleEdgeWidth(side, key.handleId, value) }
                ctx.refreshFocusedTriggerPreview(side, key.handleId)
            },
            onTriggerVerticalRangeChange = { handleId, top, bottom ->
                triggerScope.mutate(
                    global = { viewModel.setTriggerVerticalRange(side, handleId, top, bottom) },
                ) { working ->
                    // 与 EdgeSettingsMutator.setTriggerVerticalRange 同规则：先夹取交换，左右对齐时镜像到对侧。
                    val newTop = top.coerceIn(0f, 1f)
                    val newBottom = bottom.coerceIn(0f, 1f)
                    val start = minOf(newTop, newBottom)
                    val height = maxOf(newTop, newBottom) - start
                    val sourceHandle = working.triggerHandle(side, handleId)
                    var updated = working.withUpdatedTriggerHandle(side, handleId, start, height)
                    if (side.isHorizontalEdge && sourceHandle?.alignOppositeSide != false) {
                        val otherSide = side.opposite()
                        if (otherSide.isHorizontalEdge &&
                            updated.triggerHandle(otherSide, handleId) != null
                        ) {
                            updated = updated.withUpdatedTriggerHandle(
                                otherSide,
                                handleId,
                                start,
                                height,
                            )
                        }
                    }
                    updated
                }
            },
            onEdgeWidthPreviewChange = { value ->
                ctx.previewTriggerHandleEdgeWidth(side, key.handleId, value)
            },
            onTriggerVerticalRangePreviewChange = { top, bottom ->
                ctx.previewTriggerHandleVerticalRange(side, key.handleId, top, bottom)
            },
            onShortSwipeDistancePreviewChange = { value ->
                ctx.previewTriggerHandleSwipeDistances(
                    side,
                    key.handleId,
                    shortSwipeDistanceDp = value,
                )
            },
            onLongSwipeDistancePreviewChange = { value ->
                ctx.previewTriggerHandleSwipeDistances(
                    side,
                    key.handleId,
                    longSwipeDistanceDp = value,
                )
            },
            // 松手只提交，不当场撤掉临时值：写入是异步的，立刻撤会先回到旧值再跳新值。
            onTriggerLayoutPreviewStop = { ctx.commitTriggerHandleLayoutPreview() },
            onAlignHandlesChange = { enabled ->
                triggerScope.mutate(
                    global = { viewModel.setTriggerAlignOppositeSide(key.handleId, side, enabled) },
                ) { working ->
                    var updated = working.withTriggerAlignOppositeSide(key.handleId, enabled)
                    if (enabled && side.isHorizontalEdge) {
                        val source = updated.triggerHandle(side, key.handleId)
                        if (source != null) {
                            val otherSide = side.opposite()
                            if (otherSide.isHorizontalEdge &&
                                updated.triggerHandle(otherSide, key.handleId) != null
                            ) {
                                updated = updated.withUpdatedTriggerHandle(
                                    side = otherSide,
                                    handleId = key.handleId,
                                    topFraction = source.topFraction,
                                    heightFraction = source.heightFraction,
                                )
                                updated = updated.withSyncedTriggerHandle(
                                    sourceSide = side,
                                    handleId = key.handleId,
                                    handle = source,
                                )
                            }
                        }
                    }
                    updated
                }
                ctx.refreshFocusedTriggerPreview(side, key.handleId)
            },
            onInterceptBackChange = viewModel::setInterceptSystemBackGesture,
            onLimitInterceptLengthChange = viewModel::setLimitMaxInterceptLength,
            onApplyBackGestureRecommendation = {
                viewModel.setInterceptSystemBackGesture(true)
                viewModel.setLimitMaxInterceptLength(true)
            },
            onPreviewStart = {
                ctx.startFocusedTriggerPreview(side, key.handleId)
            },
            onPreviewStop = {
                ctx.clearTriggerHandleLayoutPreview()
                ctx.releaseFocusedTriggerPreview()
            },
            onLayoutPreviewStart = {
                ctx.refreshFocusedTriggerPreview(side, key.handleId)
            },
            onSwipeDistancePreviewStart = {
                ctx.refreshSwipeDistancePreview(side, key.handleId)
            },
            onSwipeDistancePreviewStop = {
                ctx.refreshFocusedTriggerPreview(side, key.handleId)
            },
        )
    }

    hiltEntry<AppNavKey.HomeSideGesturesDesign> { key ->
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val triggerScope = rememberTriggerEditScope(viewModel, key.profileId)
        val settings = triggerScope.settings
        TriggerLandscapeOrientationEffect(triggerScope.landscapeEditing)
        val side = key.side.toPanelSide()
        val gesturesKey = AppNavKey.HomeSideGestures(key.side, key.handleId, key.profileId)
        TriggerDesignSettingsScreen(
            side = side,
            handleId = key.handleId,
            settings = settings,
            serviceEnabled = true,
            onBack = { ctx.navigateBackTo(gesturesKey) },
            onDesignChange = { design ->
                triggerScope.mutate(
                    global = { viewModel.setTriggerHandleDesign(side, key.handleId, design) },
                ) { working ->
                    // 与 EdgeSettingsMutator.setTriggerHandleDesign 同规则：先过矩形预设逻辑再同步对侧。
                    val sourceHandle = working.triggerHandle(side, key.handleId)
                    if (sourceHandle == null) {
                        working
                    } else {
                        working.withSyncedTriggerHandleDesignState(
                            sourceSide = side,
                            handleId = key.handleId,
                            sourceHandle = TriggerRectanglePresetLogic.updateDesign(sourceHandle, design),
                        )
                    }
                }
            },
            onPresetApply = { preset ->
                triggerScope.mutate(
                    global = { viewModel.applyTriggerDesignPreset(side, key.handleId, preset) },
                ) { working ->
                    val sourceHandle = working.triggerHandle(side, key.handleId)
                    if (sourceHandle == null) {
                        working
                    } else {
                        working.withSyncedTriggerHandleDesignState(
                            sourceSide = side,
                            handleId = key.handleId,
                            sourceHandle = TriggerRectanglePresetLogic.switchPreset(sourceHandle, preset),
                        )
                    }
                }
            },
            onAlignOppositeDesignChange = { enabled ->
                triggerScope.mutate(
                    global = { viewModel.setTriggerAlignOppositeDesign(key.handleId, side, enabled) },
                ) { working ->
                    var updated = working.withTriggerAlignOppositeDesign(key.handleId, enabled)
                    if (enabled) {
                        val source = updated.triggerHandle(side, key.handleId)
                        if (source != null) {
                            updated = updated.withSyncedTriggerHandleDesignState(
                                sourceSide = side,
                                handleId = key.handleId,
                                sourceHandle = source,
                            )
                        }
                    }
                    updated
                }
            },
            onResetDefaults = {
                val design = TriggerHandleDesign()
                triggerScope.mutate(
                    global = { viewModel.setTriggerHandleDesign(side, key.handleId, design) },
                ) { working ->
                    val sourceHandle = working.triggerHandle(side, key.handleId)
                    if (sourceHandle == null) {
                        working
                    } else {
                        working.withSyncedTriggerHandleDesignState(
                            sourceSide = side,
                            handleId = key.handleId,
                            sourceHandle = TriggerRectanglePresetLogic.updateDesign(sourceHandle, design),
                        )
                    }
                }
            },
            onPreviewStart = {
                ctx.startTriggerDesignPreview(side, key.handleId)
            },
            onPreviewStop = {
                ctx.clearTriggerHandleLayoutPreview()
                ctx.releaseFocusedTriggerPreview()
            },
            onDesignPreview = { design ->
                ctx.previewTriggerHandleDesign(side, key.handleId, design)
            },
            onDesignPreviewStop = { ctx.commitTriggerHandleLayoutPreview() },
        )
    }

    hiltEntry<AppNavKey.HomeGestureAngle> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        GestureAngleSettingsScreen(
            angles = settings.gestureAngles,
            livePreviewEnabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onSave = { angles -> viewModel.saveGestureAngles(angles) },
            onPreviewStart = { ctx.startGestureAnglesPreview(it) },
            onPreviewAnglesChange = { ctx.updateGestureAnglesPreview(it) },
            onPreviewStop = { ctx.stopGestureAnglesPreview() },
        )
    }

    hiltEntry<AppNavKey.HomeSystemBackGestureWidth> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val permissions = ctx.collectPermissions()
        var privilegedAccessGranted by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                privilegedAccessGranted = com.slideindex.app.util.TaskManagerUtil.hasPrivilegedAccess()
            }
        }
        SystemBackGestureWidthSettingsScreen(
            takeoverTop = settings.systemGestureTakeoverTop,
            takeoverSides = settings.systemGestureTakeoverSides,
            takeoverBottom = settings.systemGestureTakeoverBottom,
            onTakeoverTopChange = viewModel::setSystemGestureTakeoverTop,
            onTakeoverSidesChange = viewModel::setSystemGestureTakeoverSides,
            onTakeoverBottomChange = viewModel::setSystemGestureTakeoverBottom,
            writeSecureSettingsGranted = permissions.writeSecureSettingsGranted,
            privilegedAccessGranted = privilegedAccessGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onRequestSecureSettingsGrant = { ctx.requestSecureSettingsGrant() },
            onRefreshPermissions = { ctx.refreshPermissionState() },
        )
    }

    hiltEntry<AppNavKey.HomeAnimationStyleSelect> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        AnimationStyleSelectScreen(
            settings = settings,
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onStyleSelected = viewModel::setGestureHintStyle,
            onGestureHintFingerOffsetDpChange = viewModel::setGestureHintFingerOffsetDp,
            onOpenStyleConfig = { style ->
                val dest = when (style) {
                    GestureHintStyle.WAVE -> AppNavKey.HomeWaveAnimationStyle
                    GestureHintStyle.CAPSULE -> AppNavKey.HomeCapsuleAnimationStyle
                    GestureHintStyle.BUBBLE -> AppNavKey.HomeBubbleAnimationStyle
                    GestureHintStyle.ANDROID -> AppNavKey.HomeAndroidAnimationStyle
                }
                if (dest != null) ctx.navigate(dest)
            },
        )
    }

    hiltEntry<AppNavKey.HomeWaveAnimationStyle> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        WaveStyleSettingsScreen(
            style = settings.activeWaveStyle(),
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeAnimationStyleSelect) },
            onStyleChange = viewModel::updateWaveStyle,
        )
    }

    hiltEntry<AppNavKey.HomeCapsuleAnimationStyle> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        CapsuleStyleSettingsScreen(
            style = settings.activeCapsuleStyle(),
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeAnimationStyleSelect) },
            onStyleChange = viewModel::updateCapsuleStyle,
        )
    }

    hiltEntry<AppNavKey.HomeBubbleAnimationStyle> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        BubbleStyleSettingsScreen(
            style = settings.activeBubbleStyle(),
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeAnimationStyleSelect) },
            onStyleChange = viewModel::updateBubbleStyle,
        )
    }

    hiltEntry<AppNavKey.HomeAndroidAnimationStyle> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        AndroidBackStyleSettingsScreen(
            colorSource = settings.androidBackColorSource(),
            enabled = ctx.gestureActive(settings, permissions),
            onBack = { ctx.navigateBackTo(AppNavKey.HomeAnimationStyleSelect) },
            onColorSourceChange = viewModel::setAndroidBackColorSource,
        )
    }

    hiltEntry<AppNavKey.HomePreviousAppBlacklist> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ShakeGestureBlacklistScreen(
            blacklistedPackages = settings.previousAppExcludedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeMain) },
            onOpenAddApp = { ctx.navigate(AppNavKey.HomePreviousAppBlacklistPick) },
            onRemoveBlacklistedApp = { packageName ->
                viewModel.removePreviousAppExcludedApp(packageName)
            },
            titleRes = R.string.previous_app_blacklist_title,
            descriptionRes = R.string.previous_app_blacklist_desc,
            blockedSectionTitleRes = R.string.previous_app_blacklist_section_blocked,
            emptyRes = R.string.previous_app_blacklist_empty,
            removeActionDescriptionRes = R.string.previous_app_blacklist_remove,
            addSectionTitleRes = R.string.previous_app_blacklist_section_add,
            addSectionSubtitleRes = R.string.previous_app_blacklist_section_add_desc,
        )
    }

    hiltEntry<AppNavKey.HomePreviousAppBlacklistPick> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        ActivityShortcutPickAppScreen(
            titleResId = R.string.previous_app_blacklist_section_add,
            excludePackageNames = settings.previousAppExcludedPackages,
            // 上一应用黑名单按包名匹配前台，无法区分分身：显示但不可选。
            cloneMode = com.slideindex.app.ui.picker.AppClonePickMode.DISABLED,
            onBack = { ctx.navigateBackTo(AppNavKey.HomePreviousAppBlacklist) },
            onSelectApp = { app ->
                viewModel.addPreviousAppExcludedApp(app.packageName)
                ctx.navigateBackTo(AppNavKey.HomePreviousAppBlacklist)
            },
        )
    }
}

@Composable
private fun HomeTriggerCollectionRoute(
    ctx: MainNavContext,
    initialManualLandscapeOverride: Boolean? = null,
) {
    val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
    val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
    val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
    val settings = gestureSettings.toMinimalAppSettings().copy(
        cornerGestureSettings = overlaySettings.cornerGestureSettings,
    )
    // 手势/系统返回不会走 onBack 回调，这里兜底：离开触钮编辑流时释放方向锁。
    // 释放前会检查「还有没有触钮页面在场」，因此转屏 / 换外壳布局引起的销毁重建不会误释放。
    DisposableEffect(Unit) {
        TriggerSettingsLandscapeSession.onEditorScreenEnter()
        onDispose { TriggerSettingsLandscapeSession.onEditorScreenExit(ctx.activity) }
    }
    TriggerCollectionScreen(
        settings = settings,
        serviceEnabled = true,
        initialManualLandscapeOverride = initialManualLandscapeOverride,
        onEnsureLandscapeInitialized = viewModel::ensureLandscapeTriggerHandlesInitialized,
        onCopyPortraitToLandscape = viewModel::copyPortraitToLandscape,
        onBack = {
            TriggerSettingsLandscapeSession.releaseForExit(ctx.activity)
            ctx.navigateBackTo(AppNavKey.HomeMain)
        },
        onOpenLeftTrigger = { handleId ->
            ctx.navigate(AppNavKey.HomeSideGestures(PanelSide.LEFT.toNavSide(), handleId))
        },
        onOpenRightTrigger = { handleId ->
            ctx.navigate(AppNavKey.HomeSideGestures(PanelSide.RIGHT.toNavSide(), handleId))
        },
        onOpenBottomTrigger = { handleId ->
            ctx.navigate(AppNavKey.HomeSideGestures(PanelSide.BOTTOM.toNavSide(), handleId))
        },
        onOpenTopTrigger = { handleId ->
            ctx.navigate(AppNavKey.HomeSideGestures(PanelSide.TOP.toNavSide(), handleId))
        },
        onAddTriggerPair = viewModel::addTriggerHandlePair,
        onAddBottomTrigger = viewModel::addBottomTriggerHandle,
        onAddTopTrigger = viewModel::addTopTriggerHandle,
        onRemoveTriggerHandle = viewModel::removeTriggerHandle,
        onTriggerHandleEnabledChange = viewModel::setTriggerHandleEnabled,
    )
}

fun NavEntryBuilder.layoutSettingsNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.HomeLayout> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        LayoutSettingsScreen(
            settings = settings,
            serviceEnabled = ctx.gestureActive(gestureSettings.serviceEnabled, permissions),
            onBack = {
                ctx.clearOverlayLayoutPreview()
                ctx.stopLayoutPreview()
                ctx.navigateBackTo(AppNavKey.ExtensionHub)
            },
            onIndexHeightChange = viewModel::setIndexHeightFraction,
            onIndexVerticalPositionChange = viewModel::setIndexVerticalPositionFraction,
            onAppsPerRowChange = viewModel::setAppsPerRow,
            onPanelOpacityChange = viewModel::setPanelOpacity,
            onHideEmptyIndexLettersChange = viewModel::setHideEmptyIndexLetters,
            onOpenHiddenAppsSettings = { ctx.navigate(AppNavKey.HomeHiddenApps) },
            onLayoutPreviewStart = {
                ctx.startLayoutPreview(LayoutPreviewContent.INDEX_ONLY)
            },
            onLayoutPreviewStop = {
                ctx.commitIndexSliderPreviews()
                ctx.stopLayoutPreview()
            },
            onIndexHeightPreviewChange = { fraction ->
                ctx.previewIndexHeightFraction(fraction)
            },
            onIndexVerticalPositionPreviewChange = { fraction ->
                ctx.previewIndexVerticalPositionFraction(fraction)
            },
        )
    }

    hiltEntry<AppNavKey.HomeHiddenApps> {
        val viewModel: HomeDetailSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val settings = gestureSettings.toMinimalAppSettings()
        HiddenAppsScreen(
            hiddenPackages = settings.hiddenAppPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.HomeLayout) },
            onHideApp = viewModel::addHiddenApp,
            onUnhideApp = viewModel::removeHiddenApp,
        )
    }
}
