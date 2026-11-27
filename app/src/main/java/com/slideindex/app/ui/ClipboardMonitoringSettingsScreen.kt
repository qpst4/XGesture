@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.ClipboardMonitoringMode
import com.slideindex.app.settings.ClipboardMonitoringCapture
import com.slideindex.app.settings.ClipboardMonitoringChannel
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.settings.effectiveClipboardMonitoringMode
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.miuixGroupedRowInsets
import com.slideindex.app.ui.settings.clipboard.ClipboardLsposedModuleStatus
import com.slideindex.app.ui.settings.clipboard.isClipboardMonitoringBackendReady
import com.slideindex.app.ui.settings.clipboard.rememberClipboardMonitoringUiState
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingSegmentedRow
import com.slideindex.app.ui.settings.components.SettingRadioRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsCardRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.StatusPill
import com.slideindex.app.ui.settings.components.StatusRow
import com.slideindex.app.ui.settings.components.StatusTone
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsGroupedRowBackground
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.util.PermissionHelper
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「剪贴板后台监听」独立页：状态头 + 模式单选 + 运行环境与权限诊断。
 *
 * 布局参考同类工具：把「现在有没有在监听、走哪条链路、缺什么权限」摆到第一屏。
 */
@Composable
fun ClipboardMonitoringSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onClipboardMonitoringChannelChange: (ClipboardMonitoringChannel) -> Unit,
    onClipboardMonitoringCaptureChange: (ClipboardMonitoringCapture) -> Unit,
    onOpenClipboardLsposedWhitelist: () -> Unit,
) {
    val context = LocalContext.current
    val monitoringUi = rememberClipboardMonitoringUiState(settings)
    val effectiveMode = settings.effectiveClipboardMonitoringMode()
    val activeMode = monitoringUi.activeMode ?: effectiveMode
    val backendReady = settings.isClipboardMonitoringBackendReady(monitoringUi)

    var notificationGranted by remember {
        mutableStateOf(PermissionHelper.hasNotificationPermission(context))
    }
    var batteryExempt by remember {
        mutableStateOf(PermissionHelper.isBatteryOptimizationExempt(context))
    }
    // LSPosed 通道：模块代码是否是当前版本、白名单 hook 是否装上——不是"服务在跑"就算正常。
    var lsposedStatus by remember {
        mutableStateOf(
            ClipboardLsposedModuleStatus.Status(ClipboardLsposedModuleStatus.Readiness.NotReady),
        )
    }
    var readLogsGranted by remember {
        mutableStateOf(ClipboardPermissionHelper.hasReadLogsPermission(context))
    }
    var showShizukuReadLogsDialog by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationGranted = granted || PermissionHelper.hasNotificationPermission(context)
    }
    LaunchedEffect(Unit) {
        ClipboardLsposedModuleStatus.refresh(context, settings.clipboardLsposedWhitelist.size) {
            lsposedStatus = it
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = PermissionHelper.hasNotificationPermission(context)
                batteryExempt = PermissionHelper.isBatteryOptimizationExempt(context)
                readLogsGranted = ClipboardPermissionHelper.hasReadLogsPermission(context)
                ClipboardLsposedModuleStatus.refresh(
                    context,
                    settings.clipboardLsposedWhitelist.size,
                ) { lsposedStatus = it }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val channelLabel = clipboardMonitoringModeLabel(activeMode)
    val principleText = clipboardMonitoringPrinciple(activeMode, settings)
    val lsposedChannelActive = activeMode == ClipboardMonitoringMode.LSPOSED
    val statusLabel: String
    val statusPill: String
    val statusOk: Boolean
    val statusDetail: String
    when {
        !settings.clipboardBackgroundMonitoring -> {
            statusLabel = stringResource(R.string.clipboard_monitoring_state_off)
            statusPill = stringResource(R.string.clipboard_monitoring_pill_off)
            statusOk = false
            statusDetail = principleText
        }
        lsposedChannelActive && lsposedStatus.readiness != ClipboardLsposedModuleStatus.Readiness.Ready -> {
            statusOk = false
            statusDetail = when (lsposedStatus.readiness) {
                ClipboardLsposedModuleStatus.Readiness.StaleModuleCode -> {
                    statusLabel = stringResource(
                        R.string.clipboard_monitoring_lsposed_state_restart_needed,
                    )
                    statusPill = stringResource(
                        R.string.clipboard_monitoring_lsposed_pill_restart_needed,
                    )
                    stringResource(R.string.clipboard_monitoring_lsposed_detail_restart_needed)
                }
                ClipboardLsposedModuleStatus.Readiness.ClipboardHookMissing -> {
                    statusLabel = stringResource(
                        R.string.clipboard_monitoring_lsposed_state_clipboard_missing,
                    )
                    statusPill = stringResource(
                        R.string.clipboard_monitoring_lsposed_pill_clipboard_missing,
                    )
                    stringResource(R.string.clipboard_monitoring_lsposed_detail_clipboard_missing)
                }
                ClipboardLsposedModuleStatus.Readiness.WhitelistStale -> {
                    statusLabel = stringResource(
                        R.string.clipboard_monitoring_lsposed_state_whitelist_stale,
                    )
                    statusPill = stringResource(
                        R.string.clipboard_monitoring_lsposed_pill_whitelist_stale,
                    )
                    stringResource(
                        R.string.clipboard_monitoring_lsposed_detail_whitelist_stale,
                        lsposedStatus.moduleWhitelistSize ?: 0,
                        settings.clipboardLsposedWhitelist.size,
                    )
                }
                else -> {
                    statusLabel = stringResource(
                        R.string.clipboard_monitoring_lsposed_state_not_ready,
                    )
                    statusPill = stringResource(
                        R.string.clipboard_monitoring_lsposed_pill_not_ready,
                    )
                    stringResource(R.string.clipboard_monitoring_lsposed_detail_not_ready)
                }
            }
        }
        monitoringUi.monitorRunning -> {
            statusLabel = stringResource(R.string.clipboard_monitoring_state_running)
            statusPill = stringResource(R.string.clipboard_monitoring_pill_service)
            statusOk = true
            // LSPosed 通道且模块就绪时，把模块手里那份名单的大小写进状态行——
            // 「名单没同步」这类问题不用翻日志（本地数量在下面白名单那一行）。
            val moduleWhitelistSize = lsposedStatus.moduleWhitelistSize
            statusDetail = if (lsposedChannelActive && moduleWhitelistSize != null) {
                stringResource(
                    R.string.clipboard_monitoring_lsposed_detail_whitelist_synced,
                    moduleWhitelistSize,
                    settings.clipboardLsposedWhitelist.size,
                )
            } else {
                principleText
            }
        }
        !backendReady -> {
            statusLabel = stringResource(R.string.clipboard_monitoring_state_blocked)
            statusPill = stringResource(R.string.clipboard_monitoring_pill_blocked)
            statusOk = false
            statusDetail = principleText
        }
        else -> {
            statusLabel = stringResource(R.string.clipboard_monitoring_state_idle)
            statusPill = stringResource(R.string.clipboard_monitoring_pill_idle)
            statusOk = false
            statusDetail = principleText
        }
    }
    // settingsLazySmallTitle 不是 @Composable，标题文字要先取出来。
    val statusSectionTitle = stringResource(R.string.clipboard_monitoring_status_section)
    val channelSectionTitle = stringResource(R.string.clipboard_monitoring_section_channel)
    val captureSectionTitle = stringResource(R.string.clipboard_monitoring_section_capture)
    val whitelistSectionTitle = stringResource(R.string.clipboard_lsposed_whitelist)
    val lsposedWhitelistSubtitle = when {
        settings.clipboardLsposedWhitelist.isEmpty() ->
            stringResource(R.string.clipboard_lsposed_whitelist_empty)
        !settings.clipboardLsposedWhitelist.contains(context.packageName) ->
            stringResource(R.string.clipboard_lsposed_whitelist_self_missing)
        else -> stringResource(
            R.string.clipboard_lsposed_whitelist_subtitle,
            settings.clipboardLsposedWhitelist.size,
        )
    }
    val diagnosticsSectionTitle = stringResource(R.string.clipboard_monitoring_section_diagnostics)
    // 分段控件的标签要在 composable 上下文里取好：LazyListScope 的 content 里没法调 stringResource。
    val captureItems = ClipboardMonitoringCapture.entries
    val captureLabels = captureItems.map { clipboardMonitoringCaptureLabel(it) }
    val captureSubtitle =
        clipboardMonitoringCaptureDescription(settings.clipboardMonitoringCapture)

    SettingsScreenScaffold(
        title = stringResource(R.string.clipboard_monitoring_page_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "clipboard-monitor-status-section",
            title = statusSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "clipboard-monitor-status",
            items = buildList {
                add(
                    settingsCardScopeItem("monitor-state") {
                        StatusRow(
                            title = "$channelLabel · $statusLabel",
                            pill = statusPill,
                            tone = if (statusOk) StatusTone.Good else StatusTone.Bad,
                            detail = statusDetail,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "clipboard-monitor-channel-section",
            title = channelSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "clipboard-monitor-channel",
            items = buildList {
                ClipboardMonitoringChannel.entries.forEach { channel ->
                    add(
                        settingsCardScopeItem("channel-${channel.name}") {
                            SettingRadioRow(
                                title = clipboardMonitoringChannelLabel(channel),
                                subtitle = clipboardMonitoringChannelDescription(channel, settings),
                                selected = settings.clipboardMonitoringChannel == channel,
                                segmentKey = channel.name,
                                onClick = { onClipboardMonitoringChannelChange(channel) },
                            )
                        },
                    )
                }
                add(
                    settingsCardScopeItem("channel-standard-warning") {
                        WarningRow(
                            text = stringResource(R.string.clipboard_monitoring_standard_warning),
                        )
                    },
                )
            },
        )

        // LSPosed 白名单：独立成段（仅该通道下显示），副标题写清从属与放行状态。
        if (settings.clipboardMonitoringChannel == ClipboardMonitoringChannel.LSPOSED) {
            settingsLazySmallTitle(
                key = "clipboard-monitor-lsposed-whitelist-section",
                title = whitelistSectionTitle,
            )
            groupedCardItems(
                keyPrefix = "clipboard-monitor-lsposed-whitelist",
                items = listOf(
                    settingsCardScopeItem("lsposed-whitelist") {
                        SettingLinkRow(
                            title = stringResource(R.string.clipboard_lsposed_whitelist),
                            subtitle = lsposedWhitelistSubtitle,
                            onClick = onOpenClipboardLsposedWhitelist,
                        )
                    },
                ),
            )
        }

        if (settings.clipboardMonitoringChannel.usesCaptureMethod) {
            // 只有两项的枚举不该占一整张单选卡：用全站统一的分段控件行，
            // 副标题跟着当前选择变，等于把原来每项的解释收敛到一行里。
            groupedCardItems(
                keyPrefix = "clipboard-monitor-capture",
                items = buildList {
                    add(
                        settingsCardScopeItem("capture-method") {
                            SettingSegmentedRow(
                                title = captureSectionTitle,
                                subtitle = captureSubtitle,
                                items = captureItems,
                                selected = settings.clipboardMonitoringCapture,
                                labels = captureLabels,
                                onSelect = onClipboardMonitoringCaptureChange,
                            )
                        },
                    )
                    // Shizuku·系统日志 这一档要单独授权读日志（原来在「剪贴板」页，搬到这里跟采集方式放一起）。
                    if (effectiveMode == ClipboardMonitoringMode.SHIZUKU_LOGS && !readLogsGranted) {
                        add(
                            settingsCardScopeItem("capture-read-logs") {
                                SettingLinkRow(
                                    title = stringResource(R.string.clipboard_read_logs_shizuku_grant),
                                    subtitle = null,
                                    onClick = { showShizukuReadLogsDialog = true },
                                )
                            },
                        )
                    }
                },
            )
        }

        settingsLazySmallTitle(
            key = "clipboard-monitor-diagnostics-section",
            title = diagnosticsSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "clipboard-monitor-diagnostics",
            items = buildList {
                add(
                    settingsCardScopeItem("diag-overlay") {
                        DiagnosticRow(
                            title = stringResource(R.string.clipboard_monitoring_diag_overlay),
                            subtitle = stringResource(R.string.clipboard_monitoring_diag_overlay_desc),
                            pill = if (monitoringUi.overlayGranted) {
                                stringResource(R.string.clipboard_monitoring_status_granted)
                            } else {
                                stringResource(R.string.clipboard_monitoring_status_missing)
                            },
                            ok = monitoringUi.overlayGranted,
                            onClick = {
                                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("diag-notification") {
                        DiagnosticRow(
                            title = stringResource(R.string.clipboard_monitoring_diag_notification),
                            subtitle = stringResource(R.string.clipboard_monitoring_diag_notification_desc),
                            pill = if (notificationGranted) {
                                stringResource(R.string.clipboard_monitoring_status_enabled)
                            } else {
                                stringResource(R.string.clipboard_monitoring_status_disabled)
                            },
                            ok = notificationGranted,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !notificationGranted
                                ) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("diag-battery") {
                        DiagnosticRow(
                            title = stringResource(R.string.clipboard_monitoring_diag_battery),
                            subtitle = stringResource(R.string.clipboard_monitoring_diag_battery_desc),
                            pill = if (batteryExempt) {
                                stringResource(R.string.clipboard_monitoring_status_joined)
                            } else {
                                stringResource(R.string.clipboard_monitoring_status_not_joined)
                            },
                            ok = batteryExempt,
                            onClick = {
                                PermissionHelper.requestBatteryOptimizationAccess(context)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("diag-autostart") {
                        DiagnosticRow(
                            title = stringResource(R.string.clipboard_monitoring_diag_autostart),
                            subtitle = stringResource(R.string.clipboard_monitoring_diag_autostart_desc),
                            pill = stringResource(R.string.clipboard_monitoring_action_open_settings),
                            ok = true,
                            onClick = {
                                runCatching {
                                    context.startActivity(PermissionHelper.appListSettingsIntent(context))
                                }
                            },
                        )
                    },
                )
            },
        )
    }

    ClipboardBackgroundReadLogsDialog(
        show = showShizukuReadLogsDialog,
        onDismiss = { showShizukuReadLogsDialog = false },
    )
}

@Composable
internal fun ClipboardBackgroundReadLogsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    MiuixConfirmDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.clipboard_read_logs_shizuku_reminder_title),
        message = stringResource(R.string.clipboard_read_logs_shizuku_reminder_message),
        confirmText = stringResource(R.string.clipboard_read_logs_shizuku_reminder_continue),
        onConfirm = {
            ClipboardPermissionHelper.grantViaShizuku(context)
            onDismiss()
        },
    )
}

@Composable
private fun SettingsCardScope.DiagnosticRow(
    title: String,
    subtitle: String,
    pill: String,
    ok: Boolean,
    onClick: () -> Unit,
) {
    SettingsCardRow(key = "diag-$title") { position ->
        Row(
            modifier = Modifier
                .settingsGroupedRowBackground(position.index, position.count)
                .miuixGroupedRowInsets()
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            StatusPill(
                text = pill,
                tone = if (ok) StatusTone.Good else StatusTone.Bad,
                onClick = onClick,
            )
        }
    }
}

/** 段内的警告提示行（例如「标准公开监听」不能后台监听）。 */
@Composable
private fun SettingsCardScope.WarningRow(text: String) {
    SettingsCardRow(key = "warning-$text") { position ->
        Text(
            text = text,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.error,
            modifier = Modifier
                .settingsGroupedRowBackground(position.index, position.count)
                .miuixGroupedRowInsets()
                .fillMaxWidth(),
        )
    }
}

/** 当前生效链路的「技术原理」说明（状态头与模式区共用）。 */
@Composable
internal fun clipboardMonitoringPrinciple(
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
        stringResource(R.string.clipboard_monitoring_principle_shizuku_logs)
    ClipboardMonitoringMode.SHIZUKU_HIDDEN_API ->
        stringResource(R.string.clipboard_monitoring_principle_shizuku_hidden_api)
    ClipboardMonitoringMode.ROOT_LOGS ->
        stringResource(R.string.clipboard_monitoring_principle_root_logs)
    ClipboardMonitoringMode.ROOT_HIDDEN_API ->
        stringResource(R.string.clipboard_monitoring_principle_root_hidden_api)
    ClipboardMonitoringMode.LSPOSED ->
        stringResource(R.string.clipboard_monitoring_principle_lsposed)
    ClipboardMonitoringMode.STANDARD ->
        stringResource(R.string.clipboard_monitoring_principle_standard)
}

/** 只有「提权 + 采集」这类通道才需要选采集方式。 */
private val ClipboardMonitoringChannel.usesCaptureMethod: Boolean
    get() = this == ClipboardMonitoringChannel.FOLLOW_PRIVILEGE ||
        this == ClipboardMonitoringChannel.SHIZUKU ||
        this == ClipboardMonitoringChannel.ROOT

@Composable
internal fun clipboardMonitoringChannelLabel(channel: ClipboardMonitoringChannel): String =
    when (channel) {
        ClipboardMonitoringChannel.FOLLOW_PRIVILEGE ->
            stringResource(R.string.clipboard_monitoring_mode_follow_privilege)
        ClipboardMonitoringChannel.SHIZUKU ->
            stringResource(R.string.clipboard_monitoring_channel_shizuku)
        ClipboardMonitoringChannel.ROOT ->
            stringResource(R.string.clipboard_monitoring_channel_root)
        ClipboardMonitoringChannel.LSPOSED ->
            stringResource(R.string.clipboard_monitoring_channel_lsposed)
        ClipboardMonitoringChannel.STANDARD ->
            stringResource(R.string.clipboard_monitoring_channel_standard)
    }

@Composable
internal fun clipboardMonitoringChannelDescription(
    channel: ClipboardMonitoringChannel,
    settings: AppSettings,
): String = when (channel) {
    ClipboardMonitoringChannel.FOLLOW_PRIVILEGE -> {
        val path = when (settings.privilegeMode) {
            PrivilegeMode.ROOT -> stringResource(R.string.privilege_mode_root)
            PrivilegeMode.SHIZUKU -> stringResource(R.string.privilege_mode_shizuku)
        }
        stringResource(R.string.clipboard_monitoring_mode_follow_privilege_desc, path)
    }
    ClipboardMonitoringChannel.SHIZUKU ->
        stringResource(R.string.clipboard_monitoring_channel_shizuku_desc)
    ClipboardMonitoringChannel.ROOT ->
        stringResource(R.string.clipboard_monitoring_channel_root_desc)
    ClipboardMonitoringChannel.LSPOSED ->
        stringResource(R.string.clipboard_monitoring_channel_lsposed_desc)
    ClipboardMonitoringChannel.STANDARD ->
        stringResource(R.string.clipboard_monitoring_channel_standard_desc)
}

@Composable
internal fun clipboardMonitoringCaptureLabel(capture: ClipboardMonitoringCapture): String =
    when (capture) {
        ClipboardMonitoringCapture.HIDDEN_API ->
            stringResource(R.string.clipboard_monitoring_capture_hidden_api)
        ClipboardMonitoringCapture.LOGCAT ->
            stringResource(R.string.clipboard_monitoring_capture_logcat)
    }

@Composable
internal fun clipboardMonitoringCaptureDescription(capture: ClipboardMonitoringCapture): String =
    when (capture) {
        ClipboardMonitoringCapture.HIDDEN_API ->
            stringResource(R.string.clipboard_monitoring_capture_hidden_api_desc)
        ClipboardMonitoringCapture.LOGCAT ->
            stringResource(R.string.clipboard_monitoring_capture_logcat_desc)
    }
