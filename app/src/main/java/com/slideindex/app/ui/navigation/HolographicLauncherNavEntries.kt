package com.slideindex.app.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.ui.HolographicLauncherAppsScreen
import com.slideindex.app.ui.HolographicLauncherSettingsScreen
import com.slideindex.app.ui.viewmodel.ExtensionSettingsViewModel

fun NavEntryBuilder.holographicLauncherNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.HolographicLauncherSettings> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val holographicSettings = gestureSettings.holographicLauncher
        // 旧「隐藏应用」黑名单 → 选中列表（+ 切到「排除选中应用」）。幂等：旧键清掉后即为空操作。
        LaunchedEffect(Unit) {
            viewModel.migrateHolographicHiddenApps()
        }
        HolographicLauncherSettingsScreen(
            settings = holographicSettings,
            onBack = { ctx.navigateBack() },
            onTimeoutSecondsChange = viewModel::setHolographicLauncherTimeoutSeconds,
            onRotationSensitivityChange = viewModel::setHolographicRotationSensitivity,
            onHapticLevelChange = viewModel::setHolographicHapticLevel,
            onBackgroundStyleChange = viewModel::setHolographicBackgroundStyle,
            onBlurDpChange = viewModel::setHolographicBlurDp,
            onDimPercentChange = viewModel::setHolographicDimPercent,
            onAppModeChange = viewModel::setHolographicAppMode,
            onOpenApps = { ctx.navigate(AppNavKey.HolographicLauncherApps) },
        )
    }

    hiltEntry<AppNavKey.HolographicLauncherApps> {
        val viewModel: ExtensionSettingsViewModel = hiltViewModel()
        val gestureSettings by viewModel.gestureSettings.collectAsStateWithLifecycle()
        val holographic = gestureSettings.holographicLauncher
        HolographicLauncherAppsScreen(
            selectedPackages = holographic.selectedAppPackages,
            mode = holographic.appMode,
            onBack = { ctx.navigateBackTo(AppNavKey.HolographicLauncherSettings) },
            onAppModeChange = viewModel::setHolographicAppMode,
            onAddApp = viewModel::addHolographicLauncherApp,
            onRemoveApp = viewModel::removeHolographicLauncherApp,
            onSetSelection = viewModel::setHolographicLauncherApps,
        )
    }
}
