@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardFloatEntryClickAction
import com.slideindex.app.settings.ClipboardFloatEntryLongPressAction
import com.slideindex.app.settings.ClipboardFloatListStyle
import com.slideindex.app.settings.ClipboardHistoryCapacity
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.settings.ClipboardOverlayScale
import com.slideindex.app.settings.ExtensionHubSettings
import com.slideindex.app.settings.HistoryFloatHandleWidth
import com.slideindex.app.settings.StashPanelHandleOpenTab
import com.slideindex.app.settings.toMinimalAppSettings
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.clipboard.ClipboardMonitoringUiState
import com.slideindex.app.ui.settings.clipboard.clipboardMonitorSummaryText
import com.slideindex.app.ui.settings.clipboard.rememberClipboardMonitoringUiState
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingSwitchNavigationRow
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import kotlin.math.roundToInt

/** 「暂存夹与剪贴板」一级入口：暂存夹清理 + 剪贴板历史 / 收纳面板 / 小窗子页目录。 */
@Composable
fun StashClipboardSettingsScreen(
    settings: AppSettings,
    clipboardEntryCount: Int,
    stashEntryCount: Int,
    onBack: () -> Unit,
    onOpenClipboardHistory: () -> Unit,
    onOpenStashPanel: () -> Unit,
    onOpenClipboardFloat: () -> Unit,
    onClearStash: () -> Unit,
) {
    var showClearStashDialog by remember { mutableStateOf(false) }
    val monitoringUi = rememberClipboardMonitoringUiState(settings)
    val stashSectionTitle = stringResource(R.string.stash_clipboard_section_stash)
    val navSectionTitle = stringResource(R.string.stash_clipboard_index_nav_section)

    SettingsScreenScaffold(
        title = stringResource(R.string.stash_clipboard_settings_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "stash-section",
            title = stashSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "stash-clear",
            items = listOf(
                settingsCardScopeItem("stash-clear-all") {
                    SettingLinkRow(
                        title = stringResource(R.string.stash_clear_all),
                        subtitle = pluralStringResource(
                            R.plurals.stash_entry_count,
                            stashEntryCount,
                            stashEntryCount,
                        ),
                        enabled = stashEntryCount > 0,
                        onClick = { showClearStashDialog = true },
                    )
                },
            ),
        )
        settingsLazySmallTitle(
            key = "stash-clipboard-nav-section",
            title = navSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "stash-clipboard-nav",
            items = listOf(
                settingsCardScopeItem("clipboard-history") {
                    SettingNavigationRow(
                        icon = { label -> Icon(Icons.Outlined.History, contentDescription = label) },
                        title = stringResource(R.string.stash_clipboard_section_clipboard),
                        subtitle = clipboardIndexHistorySubtitle(settings, monitoringUi, clipboardEntryCount),
                        onClick = onOpenClipboardHistory,
                    )
                },
                settingsCardScopeItem("stash-panel") {
                    SettingNavigationRow(
                        icon = { label -> Icon(Icons.Outlined.Layers, contentDescription = label) },
                        title = stringResource(R.string.floating_panel_title),
                        subtitle = stringResource(R.string.stash_clipboard_index_stash_panel_desc),
                        onClick = onOpenStashPanel,
                    )
                },
                settingsCardScopeItem("clipboard-float") {
                    SettingNavigationRow(
                        icon = { label -> Icon(Icons.Outlined.ContentPaste, contentDescription = label) },
                        title = stringResource(R.string.clipboard_float_section),
                        subtitle = stringResource(R.string.stash_clipboard_index_clipboard_float_desc),
                        onClick = onOpenClipboardFloat,
                    )
                },
            ),
        )
    }

    MiuixConfirmDialog(
        show = showClearStashDialog,
        onDismissRequest = { showClearStashDialog = false },
        title = stringResource(R.string.stash_clear_all_confirm_title),
        message = stringResource(R.string.stash_clear_all_confirm_message),
        onConfirm = onClearStash,
    )
}

@Composable
private fun clipboardIndexHistorySubtitle(
    settings: AppSettings,
    monitoringUi: ClipboardMonitoringUiState,
    entryCount: Int,
): String {
    val count = pluralStringResource(R.plurals.clipboard_history_count, entryCount, entryCount)
    // 以真实运行状态为准（扩展 tab 的卡片共用同一个 helper，避免两处再走偏）。
    val monitor = clipboardMonitorSummaryText(settings.clipboardBackgroundMonitoring, monitoringUi)
    return stringResource(R.string.stash_clipboard_entry_summary, count, monitor)
}

/** 剪贴板历史子页：容量 / 清空 / 截图监听 / 后台监听。 */
@Composable
fun ClipboardHistorySettingsScreen(
    settings: AppSettings,
    clipboardEntryCount: Int,
    onBack: () -> Unit,
    onClipboardHistoryMaxEntriesChange: (Int) -> Unit,
    onClearClipboardHistory: () -> Unit,
    onClipboardScreenshotMonitoringChange: (Boolean) -> Unit,
    onClipboardMonitoringChange: (Boolean) -> Unit,
    onClipboardOverlayEnabledChange: (Boolean) -> Unit,
    onClipboardOverlayScalePercentChange: (Int) -> Unit,
    onClipboardPasteFvStyleEnabledChange: (Boolean) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenClipboardMonitoringSettings: () -> Unit,
) {
    val context = LocalContext.current
    var showClearClipboardDialog by remember { mutableStateOf(false) }
    val capacityPresets = ClipboardHistoryCapacity.presets
    val capacityIndex = capacityPresets.indexOf(settings.clipboardHistoryMaxEntries).let {
        if (it >= 0) it else capacityPresets.indexOf(100).coerceAtLeast(0)
    }
    val monitoringUi = rememberClipboardMonitoringUiState(settings)
    var mediaReadGranted by remember {
        mutableStateOf(ClipboardPermissionHelper.hasMediaReadPermission(context))
    }
    var pendingScreenshotEnable by remember { mutableStateOf(false) }
    val mediaReadPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        mediaReadGranted = ClipboardPermissionHelper.hasMediaReadPermission(context)
        if (mediaReadGranted) {
            if (pendingScreenshotEnable) {
                pendingScreenshotEnable = false
                onClipboardScreenshotMonitoringChange(true)
            } else if (settings.clipboardScreenshotMonitoring) {
                com.slideindex.app.overlay.OverlayStatePort.sendCommand(
                    context,
                    com.slideindex.app.overlay.OverlayStatePort.COMMAND_SYNC_SCREENSHOT_MONITORING,
                )
            }
        } else {
            pendingScreenshotEnable = false
        }
    }

    fun requestMediaReadPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaReadPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            mediaReadGranted = true
            if (pendingScreenshotEnable) {
                pendingScreenshotEnable = false
                onClipboardScreenshotMonitoringChange(true)
            } else if (settings.clipboardScreenshotMonitoring) {
                com.slideindex.app.overlay.OverlayStatePort.sendCommand(
                    context,
                    com.slideindex.app.overlay.OverlayStatePort.COMMAND_SYNC_SCREENSHOT_MONITORING,
                )
            }
        }
    }

    LaunchedEffect(settings.clipboardScreenshotMonitoring) {
        mediaReadGranted = ClipboardPermissionHelper.hasMediaReadPermission(context)
    }

    val historySectionTitle = stringResource(R.string.stash_clipboard_section_history)
    val pasteBehaviorSectionTitle = stringResource(R.string.clipboard_paste_behavior_section)
    val pasteFvStyleScopeHint = stringResource(R.string.clipboard_paste_fv_style_scope_hint)
    val accessibilityGranted = com.slideindex.app.overlay.OverlayStatePort.isServiceConnected()
    val screenshotSectionTitle = stringResource(R.string.clipboard_screenshot_monitoring_section)
    val backgroundSectionTitle = stringResource(R.string.clipboard_background_monitoring_section)

    SettingsScreenScaffold(
        title = stringResource(R.string.stash_clipboard_section_clipboard),
        subtitle = stringResource(R.string.clipboard_history_settings_desc),
        onBack = onBack,
    ) {
        // 后台监听：整段放在剪贴板页最上面（状态与权限诊断在独立页里）。
        settingsLazySmallTitle(
            key = "clipboard-background-section",
            title = backgroundSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "clipboard-background",
            items = buildList {
                add(
                    settingsCardScopeItem("background-monitoring") {
                        // 与首页「手势动画」同款：一行同时带开关与跳转箭头。
                        SettingSwitchNavigationRow(
                            title = stringResource(R.string.clipboard_background_monitoring_title),
                            subtitle = clipboardMonitoringModeLabel(
                                settings.clipboardBackgroundMonitoringMode,
                            ),
                            checked = settings.clipboardBackgroundMonitoring,
                            enabled = true,
                            onCheckedChange = onClipboardMonitoringChange,
                            onNavigate = onOpenClipboardMonitoringSettings,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("clipboard-overlay") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.clipboard_overlay_enabled_title),
                            subtitle = stringResource(R.string.clipboard_overlay_enabled_desc),
                            checked = settings.clipboardOverlayEnabled,
                            enabled = settings.clipboardBackgroundMonitoring && monitoringUi.overlayGranted,
                            onCheckedChange = { enabled ->
                                if (enabled && !monitoringUi.overlayGranted) {
                                    onOpenOverlayPermission()
                                } else {
                                    onClipboardOverlayEnabledChange(enabled)
                                }
                            },
                        ) {
                            SettingsSliderRow(
                                title = stringResource(R.string.clipboard_overlay_preview_size_title),
                                value = settings.clipboardOverlayScalePercent.toFloat(),
                                valueRange = ClipboardOverlayScale.MIN_PERCENT.toFloat()..
                                    ClipboardOverlayScale.MAX_PERCENT.toFloat(),
                                enabled = true,
                                label = "${settings.clipboardOverlayScalePercent}%",
                                formatLabel = { "${it.roundToInt()}%" },
                                onValueChange = { onClipboardOverlayScalePercentChange(it.roundToInt()) },
                            )
                        }
                    },
                )
            },
        )
        settingsLazySmallTitle(
            key = "clipboard-history-section",
            title = historySectionTitle,
        )
        groupedCardItems(
            keyPrefix = "clipboard-history",
            items = buildList {
                add(
                    settingsCardScopeItem("history-capacity") {
                        SettingDropdownRow(
                            title = stringResource(R.string.clipboard_history_capacity_title),
                            items = capacityPresets.map { clipboardCapacityLabel(it) },
                            selectedIndex = capacityIndex,
                            onSelectedIndexChange = { onClipboardHistoryMaxEntriesChange(capacityPresets[it]) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("clear-history") {
                        SettingLinkRow(
                            title = stringResource(R.string.clipboard_clear_history),
                            subtitle = pluralStringResource(
                                R.plurals.clipboard_history_count,
                                clipboardEntryCount,
                                clipboardEntryCount,
                            ),
                            enabled = clipboardEntryCount > 0,
                            onClick = { showClearClipboardDialog = true },
                        )
                    },
                )
            },
        )
        settingsLazySmallTitle(
            key = "clipboard-paste-behavior-section",
            title = pasteBehaviorSectionTitle,
        )
        settingsLazyTipCard(
            key = "clipboard-paste-fv-scope-tip",
            text = pasteFvStyleScopeHint,
        )
        groupedCardItems(
            keyPrefix = "clipboard-paste-behavior",
            items = listOf(
                settingsCardScopeItem("paste-fv-style") {
                    SettingSwitchRow(
                        title = stringResource(R.string.clipboard_paste_fv_style_enabled),
                        subtitle = stringResource(R.string.clipboard_paste_fv_style_enabled_desc),
                        checked = settings.clipboardPasteFvStyleEnabled,
                        enabled = accessibilityGranted,
                        onCheckedChange = onClipboardPasteFvStyleEnabledChange,
                    )
                },
            ),
        )
        settingsLazySmallTitle(
            key = "clipboard-screenshot-section",
            title = screenshotSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "clipboard-screenshot",
            items = buildList {
                add(
                    settingsCardScopeItem("screenshot-media-read-status") {
                        SettingLinkRow(
                            title = stringResource(R.string.clipboard_media_read_status_title),
                            subtitle = stringResource(
                                if (mediaReadGranted) {
                                    R.string.clipboard_media_read_status_granted
                                } else {
                                    R.string.clipboard_media_read_status_denied
                                },
                            ),
                            onClick = {
                                if (!mediaReadGranted) {
                                    requestMediaReadPermission()
                                }
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("screenshot-monitoring") {
                        SettingSwitchRow(
                            title = stringResource(R.string.clipboard_screenshot_monitoring_title),
                            subtitle = stringResource(R.string.clipboard_screenshot_monitoring_desc),
                            checked = settings.clipboardScreenshotMonitoring,
                            enabled = true,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    pendingScreenshotEnable = false
                                    onClipboardScreenshotMonitoringChange(false)
                                    return@SettingSwitchRow
                                }
                                if (mediaReadGranted) {
                                    onClipboardScreenshotMonitoringChange(true)
                                } else {
                                    pendingScreenshotEnable = true
                                    requestMediaReadPermission()
                                }
                            },
                        )
                    },
                )
            },
        )
    }

    MiuixConfirmDialog(
        show = showClearClipboardDialog,
        onDismissRequest = { showClearClipboardDialog = false },
        title = stringResource(R.string.clipboard_clear_history_confirm_title),
        message = stringResource(R.string.clipboard_clear_history_confirm_message),
        onConfirm = onClearClipboardHistory,
    )

}

/** 收纳面板子页：贴边收纳把手。 */
@Composable
fun StashPanelSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onClipboardHistoryFloatEnabledChange: (Boolean) -> Unit,
    onClipboardHistoryFloatEnabledLandscapeChange: (Boolean) -> Unit,
    onClipboardHistoryFloatLockPositionChange: (Boolean) -> Unit,
    onClipboardHistoryFloatHandleWidthChange: (Int) -> Unit,
    onStashPanelHandleOpenTabChange: (StashPanelHandleOpenTab) -> Unit,
    onOpenOverlayPermission: () -> Unit,
) {
    val context = LocalContext.current
    val overlayPermissionGranted = PermissionHelper.canDrawOverlays(context)
    val floatSectionTitle = stringResource(R.string.clipboard_history_float_section)
    val floatOverlayHint = stringResource(R.string.clipboard_history_float_overlay_permission_hint)
    val handleWidthPresets = HistoryFloatHandleWidth.presets
    val handleWidthIndex = handleWidthPresets.indexOf(settings.clipboardHistoryFloatHandleWidthDp).let {
        if (it >= 0) it else handleWidthPresets.indexOf(HistoryFloatHandleWidth.DEFAULT_DP).coerceAtLeast(0)
    }
    // 「指示条打开面板先显示哪个页签」：三档固定顺序（记住上次 / 闪念 / 剪贴板），照枚举声明顺序渲染。
    val handleOpenTabEntries = StashPanelHandleOpenTab.entries
    val handleOpenTabLabels = handleOpenTabEntries.map { tab ->
        when (tab) {
            StashPanelHandleOpenTab.REMEMBER_LAST ->
                stringResource(R.string.stash_panel_handle_open_tab_remember)
            StashPanelHandleOpenTab.STASH -> stringResource(R.string.stash_panel_tab)
            StashPanelHandleOpenTab.CLIPBOARD -> stringResource(R.string.clipboard_panel_tab)
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.floating_panel_title),
        subtitle = stringResource(R.string.stash_panel_settings_desc),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "clipboard-float-section",
            title = floatSectionTitle,
        )
        if (!overlayPermissionGranted) {
            settingsLazyHint(
                key = "clipboard-float-overlay-hint",
                text = floatOverlayHint,
            )
            groupedCardItems(
                keyPrefix = "clipboard-float-overlay",
                items = buildList {
                    add(
                        settingsCardScopeItem("overlay-permission") {
                            SettingLinkRow(
                                title = stringResource(R.string.clipboard_history_float_open_overlay_permission),
                                subtitle = null,
                                onClick = onOpenOverlayPermission,
                            )
                        },
                    )
                },
            )
        }
        groupedCardItems(
            keyPrefix = "clipboard-float",
            items = buildList {
                add(
                    settingsCardScopeItem("float-enabled") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.clipboard_history_float_enabled_title),
                            subtitle = stringResource(R.string.clipboard_history_float_enabled_desc),
                            checked = settings.clipboardHistoryFloatEnabled,
                            enabled = overlayPermissionGranted,
                            onCheckedChange = onClipboardHistoryFloatEnabledChange,
                        ) {
                            SettingSwitchRow(
                                title = stringResource(R.string.clipboard_history_float_enabled_landscape_title),
                                subtitle = stringResource(R.string.clipboard_history_float_enabled_landscape_desc),
                                checked = settings.clipboardHistoryFloatEnabledLandscape,
                                enabled = true,
                                onCheckedChange = onClipboardHistoryFloatEnabledLandscapeChange,
                            )
                            SettingSwitchRow(
                                title = stringResource(R.string.clipboard_history_float_lock_position_title),
                                subtitle = stringResource(R.string.clipboard_history_float_lock_position_desc),
                                checked = settings.clipboardHistoryFloatLockPosition,
                                enabled = true,
                                onCheckedChange = onClipboardHistoryFloatLockPositionChange,
                            )
                            SettingDropdownRow(
                                title = stringResource(R.string.clipboard_history_float_handle_width_title),
                                items = handleWidthPresets.map { "${it}dp" },
                                selectedIndex = handleWidthIndex,
                                onSelectedIndexChange = {
                                    onClipboardHistoryFloatHandleWidthChange(handleWidthPresets[it])
                                },
                            )
                            // 点指示条 / 从指示条横向拉出，两条路都按这一档走（长按=就地记一条不受影响）。
                            SettingDropdownRow(
                                title = stringResource(R.string.stash_panel_handle_open_tab_title),
                                items = handleOpenTabLabels,
                                selectedIndex = handleOpenTabEntries
                                    .indexOf(settings.stashPanelHandleOpenTab)
                                    .coerceAtLeast(0),
                                onSelectedIndexChange = {
                                    onStashPanelHandleOpenTabChange(handleOpenTabEntries[it])
                                },
                            )
                        }
                    },
                )
            },
        )
    }
}

/** 浮动剪贴板小窗子页：外观与交互设置。 */
@Composable
fun ClipboardFloatSettingsScreen(
    settings: AppSettings,
    accessibilityGranted: Boolean,
    onBack: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onClipboardFloatEnabledChange: (Boolean) -> Unit,
    onClipboardFloatShowChipChange: (Boolean) -> Unit,
    onClipboardFloatPinPositionChange: (Boolean) -> Unit,
    onClipboardFloatSingleLineEntryClickActionChange: (ClipboardFloatEntryClickAction) -> Unit,
    onClipboardFloatSingleLineEntryLongPressActionChange: (ClipboardFloatEntryLongPressAction) -> Unit,
    onClipboardFloatCardEntryClickActionChange: (ClipboardFloatEntryClickAction) -> Unit,
    onClipboardFloatCardEntryLongPressActionChange: (ClipboardFloatEntryLongPressAction) -> Unit,
    onClipboardFloatListStyleChange: (ClipboardFloatListStyle) -> Unit,
    onClipboardFloatPasteHapticEnabledChange: (Boolean) -> Unit,
    onClipboardFloatAlphaChange: (Float) -> Unit,
    onClipboardFloatAutoDimWhenUnfocusedChange: (Boolean) -> Unit,
    onClipboardFloatAutoCloseSecondsChange: (Int) -> Unit,
    onOpenClipboardFloatBlacklist: () -> Unit,
    onResetClipboardFloatLayout: () -> Unit,
) {
    val clipboardFloatPageHint = stringResource(R.string.clipboard_float_settings_desc)
    val clipboardFloatA11yHint = stringResource(R.string.clipboard_float_a11y_hint)
    val clipboardFloatScopeHint = stringResource(R.string.clipboard_float_settings_scope_hint)
    val clipboardFloatImeSectionTitle = stringResource(R.string.clipboard_float_ime_section)
    val clipboardFloatAppearanceSectionTitle = stringResource(R.string.clipboard_float_appearance_section)
    val autoCloseOptions = listOf(0, 5, 10, 15, 30, 60)
    val autoCloseLabels = listOf(
        stringResource(R.string.clipboard_float_auto_close_never),
        stringResource(R.string.clipboard_float_auto_close_5s),
        stringResource(R.string.clipboard_float_auto_close_10s),
        stringResource(R.string.clipboard_float_auto_close_15s),
        stringResource(R.string.clipboard_float_auto_close_30s),
        stringResource(R.string.clipboard_float_auto_close_60s),
    )
    val selectedAutoCloseIndex = autoCloseOptions.indexOf(settings.clipboardFloatAutoCloseSeconds).let {
        if (it >= 0) it else 0
    }
    val clickActionEntries = ClipboardFloatEntryClickAction.entries
    val longPressActionEntries = ClipboardFloatEntryLongPressAction.entries
    val singleLineClickIndex = clickActionEntries.indexOf(settings.clipboardFloatSingleLineEntryClickAction).let {
        if (it >= 0) it else 0
    }
    val singleLineLongPressIndex =
        longPressActionEntries.indexOf(settings.clipboardFloatSingleLineEntryLongPressAction).let {
            if (it >= 0) it else 0
        }
    val cardClickIndex = clickActionEntries.indexOf(settings.clipboardFloatCardEntryClickAction).let {
        if (it >= 0) it else 0
    }
    val cardLongPressIndex = longPressActionEntries.indexOf(settings.clipboardFloatCardEntryLongPressAction).let {
        if (it >= 0) it else 0
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.clipboard_float_section),
        pageHint = clipboardFloatPageHint,
        onBack = onBack,
    ) {
        if (!accessibilityGranted) {
            settingsLazyTipCard(
                key = "clipboard-float-a11y-tip",
                text = clipboardFloatA11yHint,
            )
        }
        settingsLazyTipCard(
            key = "clipboard-float-scope-tip",
            text = clipboardFloatScopeHint,
        )
        settingsLazySmallTitle(
            key = "clipboard-float-ime-section",
            title = clipboardFloatImeSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "clipboard-float-ime",
            items = listOf(
                settingsCardScopeItem("float-ime-enabled") {
                    SettingExpandableSwitchRow(
                        title = stringResource(R.string.clipboard_float_enabled_title),
                        subtitle = stringResource(R.string.clipboard_float_enabled_desc),
                        checked = settings.clipboardFloatEnabled,
                        enabled = accessibilityGranted,
                        onCheckedChange = { enabled ->
                            if (!accessibilityGranted) {
                                onRequestAccessibility()
                            } else {
                                onClipboardFloatEnabledChange(enabled)
                            }
                        },
                    ) {
                        SettingSwitchRow(
                            title = stringResource(R.string.clipboard_float_show_chip_title),
                            subtitle = stringResource(R.string.clipboard_float_show_chip_desc),
                            checked = settings.clipboardFloatShowChip,
                            enabled = true,
                            onCheckedChange = onClipboardFloatShowChipChange,
                        )
                    }
                },
            ),
        )
        settingsLazySmallTitle(
            key = "clipboard-float-appearance-section",
            title = clipboardFloatAppearanceSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "clipboard-float-appearance",
            items = buildList {
                add(
                    settingsCardScopeItem("float-pin") {
                        SettingSwitchRow(
                            title = stringResource(R.string.clipboard_float_pin_title),
                            subtitle = stringResource(R.string.clipboard_float_pin_desc),
                            checked = settings.clipboardFloatPanelPinPosition,
                            enabled = true,
                            onCheckedChange = onClipboardFloatPinPositionChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("float-style") {
                        SettingDropdownRow(
                            title = stringResource(R.string.clipboard_float_style_title),
                            items = listOf(
                                stringResource(R.string.clipboard_float_style_single_line),
                                stringResource(R.string.clipboard_float_style_card),
                            ),
                            selectedIndex = if (settings.clipboardFloatListStyle == ClipboardFloatListStyle.SINGLE_LINE) 0 else 1,
                            onSelectedIndexChange = {
                                onClipboardFloatListStyleChange(
                                    if (it == 0) ClipboardFloatListStyle.SINGLE_LINE
                                    else ClipboardFloatListStyle.CARD,
                                )
                            },
                        )
                    },
                )
                when (settings.clipboardFloatListStyle) {
                    ClipboardFloatListStyle.SINGLE_LINE -> {
                        add(
                            settingsCardScopeItem("float-single-click") {
                                SettingDropdownRow(
                                    title = stringResource(R.string.clipboard_float_gesture_click),
                                    items = clickActionEntries.map { clipboardFloatClickActionLabel(it) },
                                    selectedIndex = singleLineClickIndex,
                                    onSelectedIndexChange = {
                                        onClipboardFloatSingleLineEntryClickActionChange(clickActionEntries[it])
                                    },
                                )
                            },
                        )
                        add(
                            settingsCardScopeItem("float-single-long-press") {
                                SettingDropdownRow(
                                    title = stringResource(R.string.clipboard_float_gesture_long_press),
                                    items = longPressActionEntries.map { clipboardFloatLongPressActionLabel(it) },
                                    selectedIndex = singleLineLongPressIndex,
                                    onSelectedIndexChange = {
                                        onClipboardFloatSingleLineEntryLongPressActionChange(longPressActionEntries[it])
                                    },
                                )
                            },
                        )
                    }
                    ClipboardFloatListStyle.CARD -> {
                        add(
                            settingsCardScopeItem("float-card-click") {
                                SettingDropdownRow(
                                    title = stringResource(R.string.clipboard_float_gesture_click),
                                    items = clickActionEntries.map { clipboardFloatClickActionLabel(it) },
                                    selectedIndex = cardClickIndex,
                                    onSelectedIndexChange = {
                                        onClipboardFloatCardEntryClickActionChange(clickActionEntries[it])
                                    },
                                )
                            },
                        )
                        add(
                            settingsCardScopeItem("float-card-long-press") {
                                SettingDropdownRow(
                                    title = stringResource(R.string.clipboard_float_gesture_long_press),
                                    items = longPressActionEntries.map { clipboardFloatLongPressActionLabel(it) },
                                    selectedIndex = cardLongPressIndex,
                                    onSelectedIndexChange = {
                                        onClipboardFloatCardEntryLongPressActionChange(longPressActionEntries[it])
                                    },
                                )
                            },
                        )
                    }
                }
                add(
                    settingsCardScopeItem("float-paste-haptic") {
                        SettingSwitchRow(
                            title = stringResource(R.string.clipboard_float_paste_haptic_title),
                            subtitle = stringResource(R.string.clipboard_float_paste_haptic_desc),
                            checked = settings.clipboardFloatPasteHapticEnabled,
                            enabled = true,
                            onCheckedChange = onClipboardFloatPasteHapticEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("float-opacity") {
                        SettingsSliderRow(
                            title = stringResource(R.string.clipboard_float_opacity_title),
                            value = settings.clipboardFloatAlpha,
                            valueRange = 0.2f..1.0f,
                            enabled = true,
                            label = "${(settings.clipboardFloatAlpha * 100).toInt()}%",
                            formatLabel = { "${(it * 100).toInt()}%" },
                            onValueChange = onClipboardFloatAlphaChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("float-auto-dim") {
                        SettingSwitchRow(
                            title = stringResource(R.string.clipboard_float_auto_dim_title),
                            subtitle = stringResource(R.string.clipboard_float_auto_dim_desc),
                            checked = settings.clipboardFloatAutoDimWhenUnfocused,
                            enabled = true,
                            onCheckedChange = onClipboardFloatAutoDimWhenUnfocusedChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("float-auto-close") {
                        SettingDropdownRow(
                            title = stringResource(R.string.clipboard_float_auto_close_title),
                            items = autoCloseLabels,
                            selectedIndex = selectedAutoCloseIndex,
                            onSelectedIndexChange = {
                                onClipboardFloatAutoCloseSecondsChange(autoCloseOptions[it])
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("float-blacklist") {
                        SettingLinkRow(
                            title = stringResource(R.string.clipboard_float_app_blacklist),
                            subtitle = pluralStringResource(
                                R.plurals.clipboard_float_app_blacklist_desc,
                                settings.clipboardFloatBlockedPackages.size,
                                settings.clipboardFloatBlockedPackages.size,
                                settings.clipboardFloatPasteSuccessCount,
                                settings.clipboardFloatPasteFailCount,
                            ),
                            onClick = onOpenClipboardFloatBlacklist,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("float-reset-layout") {
                        SettingLinkRow(
                            title = stringResource(R.string.clipboard_float_reset_layout),
                            subtitle = null,
                            onClick = onResetClipboardFloatLayout,
                        )
                    },
                )
            },
        )
    }
}

@Composable
fun SettingsCardScope.StashClipboardEntryCard(
    settings: ExtensionHubSettings,
    stashEntryCount: Int,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    val monitoringUi = rememberClipboardMonitoringUiState(settings.toMinimalAppSettings())
    val stashPart = pluralStringResource(
        R.plurals.stash_clipboard_entry_summary_stash,
        stashEntryCount,
        stashEntryCount,
    )
    // 和暂存夹页共用同一个 helper：只看真实运行状态，LSPosed 不再被当成"要 Shizuku"。
    val clipboardPart = clipboardMonitorSummaryText(settings.clipboardBackgroundMonitoring, monitoringUi)
    SettingNavigationRow(
        icon = { label ->
            Icon(HubLeadingIcons.stashClipboard(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.stash_clipboard_entry_title),
        subtitle = stringResource(R.string.stash_clipboard_entry_summary, stashPart, clipboardPart),
        onClick = onClick,
    )
}

@Composable
private fun clipboardFloatClickActionLabel(action: ClipboardFloatEntryClickAction): String = when (action) {
    ClipboardFloatEntryClickAction.PASTE -> stringResource(R.string.clipboard_float_click_action_paste)
    ClipboardFloatEntryClickAction.COPY -> stringResource(R.string.clipboard_float_click_action_copy)
    ClipboardFloatEntryClickAction.COPY_AND_PASTE -> stringResource(R.string.clipboard_float_click_action_copy_and_paste)
}

@Composable
private fun clipboardFloatLongPressActionLabel(action: ClipboardFloatEntryLongPressAction): String = when (action) {
    ClipboardFloatEntryLongPressAction.WORD_TAP -> stringResource(R.string.clipboard_float_long_press_action_word_tap)
    ClipboardFloatEntryLongPressAction.DRAG_DROP -> stringResource(R.string.clipboard_float_long_press_action_drag_drop)
    ClipboardFloatEntryLongPressAction.NONE -> stringResource(R.string.clipboard_float_long_press_action_none)
}

@Composable
internal fun clipboardMonitoringModeLabel(mode: ClipboardMonitoringMode): String = when (mode) {
    ClipboardMonitoringMode.FOLLOW_PRIVILEGE ->
        stringResource(R.string.clipboard_monitoring_mode_follow_privilege)
    ClipboardMonitoringMode.SHIZUKU_LOGS ->
        stringResource(R.string.clipboard_monitoring_mode_shizuku_logs)
    ClipboardMonitoringMode.SHIZUKU_HIDDEN_API ->
        stringResource(R.string.clipboard_monitoring_mode_shizuku_hidden_api)
    ClipboardMonitoringMode.ROOT_LOGS ->
        stringResource(R.string.clipboard_monitoring_mode_root_logs)
    ClipboardMonitoringMode.ROOT_HIDDEN_API ->
        stringResource(R.string.clipboard_monitoring_mode_root_hidden_api)
    ClipboardMonitoringMode.LSPOSED ->
        stringResource(R.string.clipboard_monitoring_mode_lsposed)
    ClipboardMonitoringMode.STANDARD ->
        stringResource(R.string.clipboard_monitoring_mode_standard)
}

@Composable
internal fun clipboardMonitoringModeDescription(
    mode: ClipboardMonitoringMode,
    settings: AppSettings,
): String = when (mode) {
    ClipboardMonitoringMode.FOLLOW_PRIVILEGE -> {
        val effective = mode.effective(settings.privilegeMode)
        val path = when {
            effective.usesRoot -> stringResource(R.string.privilege_mode_root)
            else -> stringResource(R.string.privilege_mode_shizuku)
        }
        stringResource(R.string.clipboard_monitoring_mode_follow_privilege_desc, path)
    }
    ClipboardMonitoringMode.SHIZUKU_LOGS ->
        stringResource(R.string.clipboard_monitoring_mode_shizuku_logs_desc)
    ClipboardMonitoringMode.SHIZUKU_HIDDEN_API ->
        stringResource(R.string.clipboard_monitoring_mode_shizuku_hidden_api_desc)
    ClipboardMonitoringMode.ROOT_LOGS ->
        stringResource(R.string.clipboard_monitoring_mode_root_logs_desc)
    ClipboardMonitoringMode.ROOT_HIDDEN_API ->
        stringResource(R.string.clipboard_monitoring_mode_root_hidden_api_desc)
    ClipboardMonitoringMode.LSPOSED ->
        stringResource(R.string.clipboard_monitoring_mode_lsposed_desc)
    ClipboardMonitoringMode.STANDARD ->
        stringResource(R.string.clipboard_monitoring_mode_standard_desc)
}

@Composable
private fun clipboardCapacityLabel(capacity: Int): String =
    if (capacity == ClipboardHistoryCapacity.UNLIMITED) {
        stringResource(R.string.clipboard_history_capacity_unlimited)
    } else {
        capacity.toString()
    }
