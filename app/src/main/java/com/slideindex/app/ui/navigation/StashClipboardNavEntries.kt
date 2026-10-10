package com.slideindex.app.ui.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.ui.ClipboardFloatSettingsScreen
import com.slideindex.app.ui.ClipboardMonitoringSettingsScreen
import com.slideindex.app.ui.ClipboardHistorySettingsScreen
import com.slideindex.app.ui.ShakeGestureBlacklistScreen
import com.slideindex.app.ui.StashClipboardSettingsScreen
import com.slideindex.app.ui.StashPanelSettingsScreen
import com.slideindex.app.ui.picker.ActivityShortcutPickAppScreen
import com.slideindex.app.ui.viewmodel.StashClipboardSettingsViewModel

fun NavEntryBuilder.stashClipboardNavEntries(ctx: MainNavContext) {
    hiltEntry<AppNavKey.StashClipboard> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        val clipboardEntryCount by viewModel.clipboardHistoryRepository.entryCount.collectAsStateWithLifecycle()
        val stashEntries by viewModel.stashRepository.entries.collectAsStateWithLifecycle()
        StashClipboardSettingsScreen(
            settings = settings,
            clipboardEntryCount = clipboardEntryCount,
            stashEntryCount = stashEntries.size,
            onBack = { ctx.navigateBackTo(AppNavKey.ExtensionHub) },
            onOpenClipboardHistory = { ctx.navigate(AppNavKey.ClipboardHistorySettings) },
            onOpenStashPanel = { ctx.navigate(AppNavKey.StashPanelSettings) },
            onOpenClipboardFloat = { ctx.navigate(AppNavKey.ClipboardFloatSettings) },
            onClearStash = viewModel::clearStash,
        )
        LaunchedEffect(permissions.overlayGranted) {
            if (permissions.overlayGranted) {
                viewModel.syncHistoryFloatFromSettings()
            }
        }
        LaunchedEffect(permissions.accessibilityGranted, settings.clipboardFloatEnabled) {
            if (permissions.accessibilityGranted) {
                viewModel.syncClipboardFloatFromSettings()
            }
        }
    }

    hiltEntry<AppNavKey.ClipboardHistorySettings> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val context = LocalContext.current
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        val clipboardEntryCount by viewModel.clipboardHistoryRepository.entryCount.collectAsStateWithLifecycle()
        ClipboardHistorySettingsScreen(
            settings = settings,
            clipboardEntryCount = clipboardEntryCount,
            onBack = { ctx.navigateBackTo(AppNavKey.StashClipboard) },
            onClipboardHistoryMaxEntriesChange = viewModel::setClipboardHistoryMaxEntries,
            onClearClipboardHistory = viewModel::clearClipboardHistory,
            onClipboardScreenshotMonitoringChange = viewModel::setClipboardScreenshotMonitoring,
            onClipboardMonitoringChange = viewModel::setClipboardBackgroundMonitoring,
            onClipboardOverlayEnabledChange = viewModel::setClipboardOverlayEnabled,
            onClipboardOverlayScalePercentChange = viewModel::setClipboardOverlayScalePercent,
            onClipboardPasteFvStyleEnabledChange = viewModel::setClipboardPasteFvStyleEnabled,
            onOpenOverlayPermission = {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            },
            onOpenClipboardMonitoringSettings = {
                ctx.navigate(AppNavKey.ClipboardMonitoringSettings)
            },
        )
    }

    hiltEntry<AppNavKey.ClipboardMonitoringSettings> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val settings by viewModel.settings.collectAsStateWithLifecycle()
        ClipboardMonitoringSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.ClipboardHistorySettings) },
            onClipboardMonitoringChannelChange = viewModel::setClipboardMonitoringChannel,
            onClipboardMonitoringCaptureChange = viewModel::setClipboardMonitoringCapture,
            onOpenClipboardLsposedWhitelist = { ctx.navigate(AppNavKey.ClipboardLsposedWhitelist) },
        )
    }

    hiltEntry<AppNavKey.StashPanelSettings> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val context = LocalContext.current
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        StashPanelSettingsScreen(
            settings = settings,
            onBack = { ctx.navigateBackTo(AppNavKey.StashClipboard) },
            onClipboardHistoryFloatEnabledChange = viewModel::setClipboardHistoryFloatEnabled,
            onClipboardHistoryFloatEnabledLandscapeChange = viewModel::setClipboardHistoryFloatEnabledLandscape,
            onClipboardHistoryFloatLockPositionChange = viewModel::setClipboardHistoryFloatLockPosition,
            onClipboardHistoryFloatHandleWidthChange = viewModel::setClipboardHistoryFloatHandleWidthDp,
            onStashPanelHandleOpenTabChange = viewModel::setStashPanelHandleOpenTab,
            onOpenOverlayPermission = {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            },
        )
    }

    hiltEntry<AppNavKey.ClipboardFloatSettings> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        val permissions = ctx.collectPermissions()
        ClipboardFloatSettingsScreen(
            settings = settings,
            accessibilityGranted = permissions.accessibilityGranted,
            onBack = { ctx.navigateBackTo(AppNavKey.StashClipboard) },
            onRequestAccessibility = { ctx.openAccessibilitySettings() },
            onClipboardFloatEnabledChange = viewModel::setClipboardFloatEnabled,
            onClipboardFloatShowChipChange = viewModel::setClipboardFloatShowChip,
            onClipboardFloatPinPositionChange = viewModel::setClipboardFloatPinPosition,
            onClipboardFloatSingleLineEntryClickActionChange = viewModel::setClipboardFloatSingleLineEntryClickAction,
            onClipboardFloatSingleLineEntryLongPressActionChange =
                viewModel::setClipboardFloatSingleLineEntryLongPressAction,
            onClipboardFloatCardEntryClickActionChange = viewModel::setClipboardFloatCardEntryClickAction,
            onClipboardFloatCardEntryLongPressActionChange = viewModel::setClipboardFloatCardEntryLongPressAction,
            onClipboardFloatListStyleChange = viewModel::setClipboardFloatListStyle,
            onClipboardFloatPasteHapticEnabledChange = viewModel::setClipboardFloatPasteHapticEnabled,
            onClipboardFloatAlphaChange = viewModel::setClipboardFloatAlpha,
            onClipboardFloatAutoDimWhenUnfocusedChange = viewModel::setClipboardFloatAutoDimWhenUnfocused,
            onClipboardFloatAutoCloseSecondsChange = viewModel::setClipboardFloatAutoCloseSeconds,
            onOpenClipboardFloatBlacklist = { ctx.navigate(AppNavKey.ClipboardFloatBlacklist) },
            onResetClipboardFloatLayout = viewModel::resetClipboardFloatLayout,
        )
    }

    hiltEntry<AppNavKey.ClipboardFloatBlacklist> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ShakeGestureBlacklistScreen(
            blacklistedPackages = settings.clipboardFloatBlockedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.ClipboardFloatSettings) },
            onOpenAddApp = { ctx.navigate(AppNavKey.ClipboardFloatBlacklistPick) },
            onRemoveBlacklistedApp = viewModel::removeClipboardFloatBlockedPackage,
            titleRes = R.string.clipboard_float_app_blacklist,
            descriptionRes = R.string.clipboard_float_app_blacklist_page_desc,
            blockedSectionTitleRes = R.string.clipboard_float_blacklist_section_blocked,
            emptyRes = R.string.clipboard_float_blacklist_empty,
            removeActionDescriptionRes = R.string.clipboard_float_blacklist_remove,
            addSectionTitleRes = R.string.clipboard_float_blacklist_section_add,
        )
    }

    hiltEntry<AppNavKey.ClipboardFloatBlacklistPick> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ActivityShortcutPickAppScreen(
            titleResId = R.string.clipboard_float_blacklist_section_add,
            excludePackageNames = settings.clipboardFloatBlockedPackages,
            onBack = { ctx.navigateBackTo(AppNavKey.ClipboardFloatBlacklist) },
            onSelectApp = { app ->
                viewModel.addClipboardFloatBlockedPackage(app.packageName)
                ctx.navigateBackTo(AppNavKey.ClipboardFloatBlacklist)
            },
        )
    }

    hiltEntry<AppNavKey.ClipboardLsposedWhitelist> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ShakeGestureBlacklistScreen(
            blacklistedPackages = settings.clipboardLsposedWhitelist,
            // 本页由「剪贴板后台监听」进入；返回目标是上上级的「剪贴板」页会一次退两级。
            onBack = { ctx.navigateBackTo(AppNavKey.ClipboardMonitoringSettings) },
            onOpenAddApp = { ctx.navigate(AppNavKey.ClipboardLsposedWhitelistPick) },
            onRemoveBlacklistedApp = viewModel::removeClipboardLsposedWhitelistPackage,
            titleRes = R.string.clipboard_lsposed_whitelist,
            descriptionRes = R.string.clipboard_lsposed_whitelist_page_desc,
            blockedSectionTitleRes = R.string.clipboard_lsposed_whitelist_section,
            emptyRes = R.string.clipboard_lsposed_whitelist_empty,
            removeActionDescriptionRes = R.string.clipboard_lsposed_whitelist_remove,
            addSectionTitleRes = R.string.clipboard_lsposed_whitelist_section_add,
        )
    }

    hiltEntry<AppNavKey.ClipboardLsposedWhitelistPick> {
        val viewModel: StashClipboardSettingsViewModel = hiltViewModel()
        val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
        val settings = overlaySettings.toMinimalAppSettings()
        ActivityShortcutPickAppScreen(
            titleResId = R.string.clipboard_lsposed_whitelist_section_add,
            excludePackageNames = settings.clipboardLsposedWhitelist,
            onBack = { ctx.navigateBackTo(AppNavKey.ClipboardLsposedWhitelist) },
            onSelectApp = { app ->
                viewModel.addClipboardLsposedWhitelistPackage(app.packageName)
                ctx.navigateBackTo(AppNavKey.ClipboardLsposedWhitelist)
            },
        )
    }
}
