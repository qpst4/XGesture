package com.slideindex.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPermissionHelper
import com.slideindex.app.settings.PrivilegeMode
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.navigation.NavPermissionSnapshot
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingRadioRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.StatusRow
import com.slideindex.app.ui.settings.components.StatusTone
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.util.MediaSessionHelper
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.SecureSettingsHelper

/** 按需权限的即时状态：这些权限不进权限快照，进页面时现查，回前台再查一次。 */
private data class OptionalPermissionStates(
    val camera: Boolean,
    val microphone: Boolean,
    val media: Boolean,
    val contacts: Boolean,
    val allFiles: Boolean,
    val unknownSources: Boolean,
    val dnd: Boolean,
    val exactAlarm: Boolean,
)

private fun readOptionalStates(context: Context): OptionalPermissionStates = OptionalPermissionStates(
    camera = PermissionHelper.hasCameraPermission(context),
    microphone = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED,
    media = ClipboardPermissionHelper.hasMediaReadPermission(context),
    contacts = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED,
    allFiles = Environment.isExternalStorageManager(),
    unknownSources = context.packageManager.canRequestPackageInstalls(),
    dnd = PermissionHelper.hasNotificationPolicyAccess(context),
    exactAlarm = (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
        ?.canScheduleExactAlarms() == true,
)

/**
 * 「后台与权限」：由原「特权执行方式」与「App 保活」两页合并而来。
 *
 * 分组顺序按「怎么拿到这项授权」排，而不是按功能重要性：
 * 提权通道（选一条通道）→ 核心授权（缺了就跑不动或功能残缺的七项）→ 安全与调试（ADB / Shizuku 授予）
 * → 厂商后台限制（系统读不到状态，只给入口）→ 按需权限（用到才给）→ 清单声明。
 *
 * 「读取应用列表」在 AOSP 上是安装期普通权限，但小米系 ROM 把它做成用户可撤销的隐私开关
 * （撤销后只能枚举到个位数应用，侧边栏 / 快速启动 / 应用索引一起残），所以它必须留在
 * 核心授权里给状态与入口，不能只写进底部清单声明。
 *
 * 与首页红卡的分工：红卡只在缺东西时出现，负责催与一键修；本页常驻，负责看全貌。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackgroundPermissionsScreen(
    privilegeMode: PrivilegeMode,
    shizukuGranted: Boolean,
    rootAvailable: Boolean,
    permissions: NavPermissionSnapshot,
    hideFromRecents: Boolean,
    accessibilityKeepAliveEnabled: Boolean,
    onBack: () -> Unit,
    onPrivilegeModeChange: (PrivilegeMode) -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestAutoStart: () -> Unit,
    onHideFromRecentsChange: (Boolean) -> Unit,
    onAccessibilityKeepAliveChange: (Boolean) -> Unit,
    onRequestSecureSettingsGrant: () -> Boolean,
    onRequestReadLogsGrant: () -> Boolean,
) {
    val context = LocalContext.current
    var showAdbDialog by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val adbCommand = remember { SecureSettingsHelper.adbGrantCommand(context) }
    val copiedMessage = stringResource(R.string.secure_settings_adb_copied)

    // 相机 / 麦克风 / 通讯录 / 媒体走同一个运行时请求入口，结果回来后刷新状态。
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }

    // 从系统设置页返回（用户手动开完权限）时刷新按需权限状态。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val optional = remember(refreshTick) { readOptionalStates(context) }
    val readLogsGranted = remember(refreshTick) {
        ClipboardPermissionHelper.hasReadLogsPermission(context)
    }
    // 包可见性没有权限码可查，只能实际枚举一次（与引导页 PermissionHelper.hasAppListPermission 同源）。
    val appListGranted = remember(refreshTick) {
        PermissionHelper.hasAppListPermission(context)
    }

    val channelSectionTitle = stringResource(R.string.permissions_section_channel)
    val coreSectionTitle = stringResource(R.string.permissions_section_core)
    val advancedSectionTitle = stringResource(R.string.permissions_section_advanced)
    val vendorSectionTitle = stringResource(R.string.permissions_section_vendor)
    val optionalSectionTitle = stringResource(R.string.permissions_section_optional)
    val optionalHint = stringResource(R.string.permissions_optional_hint)
    val manifestHint = stringResource(R.string.permissions_manifest_hint)

    SettingsScreenScaffold(
        title = stringResource(R.string.background_permissions_title),
        subtitle = stringResource(R.string.background_permissions_desc),
        onBack = onBack,
    ) {
        // ── 提权通道 ────────────────────────────────────────────
        settingsLazySmallTitle(key = "bgperm-channel-title", title = channelSectionTitle)
        groupedCardItems(
            keyPrefix = "bgperm-channel",
            items = buildList {
                add(
                    settingsCardScopeItem("privilege-shizuku") {
                        SettingRadioRow(
                            title = stringResource(R.string.privilege_mode_shizuku),
                            subtitle = stringResource(R.string.privilege_mode_shizuku_desc),
                            selected = privilegeMode == PrivilegeMode.SHIZUKU,
                            onClick = { onPrivilegeModeChange(PrivilegeMode.SHIZUKU) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("privilege-root") {
                        SettingRadioRow(
                            title = stringResource(R.string.privilege_mode_root),
                            subtitle = stringResource(R.string.privilege_mode_root_desc),
                            selected = privilegeMode == PrivilegeMode.ROOT,
                            onClick = { onPrivilegeModeChange(PrivilegeMode.ROOT) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("privilege-status") {
                        val ready = when (privilegeMode) {
                            PrivilegeMode.SHIZUKU -> shizukuGranted
                            PrivilegeMode.ROOT -> rootAvailable
                        }
                        val detail = when (privilegeMode) {
                            PrivilegeMode.SHIZUKU -> if (shizukuGranted) {
                                stringResource(R.string.privilege_mode_status_shizuku_ready)
                            } else {
                                stringResource(R.string.privilege_mode_status_shizuku_missing)
                            }
                            PrivilegeMode.ROOT -> if (rootAvailable) {
                                stringResource(R.string.privilege_mode_status_root_ready)
                            } else {
                                stringResource(R.string.privilege_mode_status_root_missing)
                            }
                        }
                        StatusRow(
                            title = stringResource(R.string.privilege_mode_status_title),
                            pill = if (ready) {
                                stringResource(R.string.privilege_mode_status_pill_ready)
                            } else {
                                stringResource(R.string.privilege_mode_status_pill_missing)
                            },
                            tone = if (ready) StatusTone.Good else StatusTone.Bad,
                            detail = detail,
                            onClick = if (privilegeMode == PrivilegeMode.SHIZUKU && !shizukuGranted) {
                                onRequestShizuku
                            } else {
                                null
                            },
                        )
                    },
                )
            },
        )

        // ── 核心授权 ────────────────────────────────────────────
        settingsLazySmallTitle(key = "bgperm-core-title", title = coreSectionTitle)
        groupedCardItems(
            keyPrefix = "bgperm-core",
            items = buildList {
                add(
                    settingsCardScopeItem("core-accessibility") {
                        PermissionStatusRow(
                            // 权限清单里叫「无障碍服务」；红卡那边用品牌名「X手势」是另一种语境。
                            title = stringResource(R.string.onboarding_accessibility_title),
                            detail = stringResource(R.string.permission_accessibility_service_desc),
                            granted = permissions.accessibilityGranted,
                            onClick = {
                                context.startActivity(PermissionHelper.accessibilitySettingsIntent())
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("core-overlay") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_overlay_title),
                            detail = stringResource(R.string.onboarding_permission_overlay_short),
                            granted = permissions.overlayGranted,
                            onClick = {
                                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("core-app-list") {
                        PermissionStatusRow(
                            title = stringResource(R.string.onboarding_permission_app_list_title),
                            detail = stringResource(R.string.onboarding_permission_app_list_short),
                            granted = appListGranted,
                            onClick = { PermissionHelper.requestAppListPermission(context) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("core-notification") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_notification_title),
                            detail = stringResource(R.string.permission_notification_desc),
                            granted = permissions.notificationGranted,
                            onClick = {
                                if (!permissions.notificationGranted) {
                                    onRequestNotification()
                                } else {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("core-battery") {
                        PermissionStatusRow(
                            title = stringResource(R.string.battery_optimization_title),
                            detail = stringResource(R.string.battery_optimization_desc),
                            granted = permissions.batteryOptimizationExempt,
                            onClick = onRequestBatteryOptimization,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("core-usage-access") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_usage_title),
                            detail = stringResource(R.string.permission_usage_short_desc),
                            granted = permissions.usageAccessGranted,
                            onClick = { PermissionHelper.requestUsageAccess(context) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("core-notification-listener") {
                        PermissionStatusRow(
                            title = stringResource(R.string.message_reminder_permission_listener_title),
                            detail = stringResource(R.string.permission_listener_short_desc),
                            granted = permissions.notificationListenerEnabled,
                            onClick = { MediaSessionHelper.openNotificationListenerSettings(context) },
                        )
                    },
                )
            },
        )

        // ── 安全与调试 ──────────────────────────────────────────
        settingsLazySmallTitle(key = "bgperm-advanced-title", title = advancedSectionTitle)
        groupedCardItems(
            keyPrefix = "bgperm-advanced",
            items = buildList {
                add(
                    settingsCardScopeItem("advanced-write-settings") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_write_settings_title),
                            detail = stringResource(R.string.permission_write_settings_desc),
                            granted = PermissionHelper.canWriteSettings(context),
                            toneWhenMissing = StatusTone.Neutral,
                            onClick = { PermissionHelper.requestWriteSettingsAccess(context) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("advanced-read-logs") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_read_logs_title),
                            detail = stringResource(R.string.permission_read_logs_desc),
                            granted = readLogsGranted,
                            toneWhenMissing = StatusTone.Neutral,
                            onClick = {
                                if (!readLogsGranted && !onRequestReadLogsGrant()) {
                                    // 没有特权通道时给 ADB 命令，用户可去电脑上执行。
                                    copyToClipboard(
                                        context,
                                        ClipboardPermissionHelper.adbGrantReadLogsCommand(context),
                                    )
                                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    },
                )
            },
        )

        // ── 后台保活 ────────────────────────────────────────────
        settingsLazySmallTitle(key = "bgperm-vendor-title", title = vendorSectionTitle)
        groupedCardItems(
            keyPrefix = "bgperm-vendor",
            items = buildList {
                add(
                    settingsCardScopeItem("vendor-auto-start") {
                        SettingLinkRow(
                            title = stringResource(R.string.auto_start_title),
                            subtitle = stringResource(R.string.auto_start_desc),
                            onClick = onRequestAutoStart,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("vendor-hide-recents") {
                        SettingSwitchRow(
                            title = stringResource(R.string.hide_from_recents_title),
                            subtitle = stringResource(R.string.hide_from_recents_desc),
                            checked = hideFromRecents,
                            enabled = true,
                            onCheckedChange = onHideFromRecentsChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("vendor-secure-settings") {
                        SettingSwitchRow(
                            title = stringResource(R.string.secure_settings_title),
                            subtitle = stringResource(R.string.secure_settings_desc),
                            checked = accessibilityKeepAliveEnabled,
                            enabled = true,
                            onCheckedChange = { enabled ->
                                when {
                                    !enabled -> onAccessibilityKeepAliveChange(false)
                                    permissions.writeSecureSettingsGranted -> onAccessibilityKeepAliveChange(true)
                                    onRequestSecureSettingsGrant() -> onAccessibilityKeepAliveChange(true)
                                    else -> showAdbDialog = true
                                }
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("vendor-background-popup") {
                        SettingLinkRow(
                            title = stringResource(R.string.permission_background_popup_title),
                            subtitle = stringResource(R.string.permission_background_popup_desc),
                            onClick = { PermissionHelper.requestAppListPermission(context) },
                        )
                    },
                )
            },
        )

        // ── 按需权限 ────────────────────────────────────────────
        settingsLazySmallTitle(key = "bgperm-optional-title", title = optionalSectionTitle)
        settingsLazyHint(key = "bgperm-optional-hint", text = optionalHint)
        groupedCardItems(
            keyPrefix = "bgperm-optional",
            items = buildList {
                add(
                    settingsCardScopeItem("optional-media") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_media_images_title),
                            detail = stringResource(R.string.permission_media_images_desc),
                            granted = optional.media,
                            toneWhenMissing = StatusTone.Neutral,
                            onClick = {
                                runtimeLauncher.launch(ClipboardPermissionHelper.mediaReadPermission())
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("optional-contacts") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_contacts_title),
                            detail = stringResource(R.string.permission_contacts_desc),
                            granted = optional.contacts,
                            toneWhenMissing = StatusTone.Neutral,
                            onClick = { runtimeLauncher.launch(Manifest.permission.READ_CONTACTS) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("optional-camera") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_camera_title),
                            detail = stringResource(R.string.permission_camera_desc),
                            granted = optional.camera,
                            toneWhenMissing = StatusTone.Neutral,
                            onClick = { runtimeLauncher.launch(Manifest.permission.CAMERA) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("optional-microphone") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_microphone_title),
                            detail = stringResource(R.string.permission_microphone_desc),
                            granted = optional.microphone,
                            toneWhenMissing = StatusTone.Neutral,
                            onClick = { runtimeLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("optional-all-files") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_all_files_title),
                            detail = stringResource(R.string.permission_all_files_desc),
                            granted = optional.allFiles,
                            toneWhenMissing = StatusTone.Neutral,
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("optional-screen-record") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_screen_record_title),
                            detail = stringResource(R.string.permission_screen_record_desc),
                            // 每次调用时由系统弹窗确认，没有常驻授权状态，不显示「未开启」。
                            granted = false,
                            toneWhenMissing = StatusTone.Neutral,
                            statusLabel = stringResource(R.string.permission_state_on_demand),
                            onClick = {},
                        )
                    },
                )
                add(
                    settingsCardScopeItem("optional-unknown-sources") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_unknown_sources_title),
                            detail = stringResource(R.string.permission_unknown_sources_desc),
                            granted = optional.unknownSources,
                            toneWhenMissing = StatusTone.Neutral,
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:${context.packageName}"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("optional-dnd") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_dnd_title),
                            detail = stringResource(R.string.permission_dnd_desc),
                            granted = optional.dnd,
                            toneWhenMissing = StatusTone.Neutral,
                            onClick = { PermissionHelper.requestNotificationPolicyAccess(context) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("optional-exact-alarm") {
                        PermissionStatusRow(
                            title = stringResource(R.string.permission_exact_alarm_title),
                            detail = stringResource(R.string.permission_exact_alarm_desc),
                            granted = optional.exactAlarm,
                            toneWhenMissing = StatusTone.Neutral,
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}"),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                        )
                    },
                )
            },
        )

        // ── 清单声明 ────────────────────────────────────────────
        settingsLazyHint(key = "bgperm-manifest-hint", text = manifestHint)
    }

    if (showAdbDialog) {
        MiuixConfirmDialog(
            show = showAdbDialog,
            onDismissRequest = { showAdbDialog = false },
            title = stringResource(R.string.secure_settings_adb_dialog_title),
            message = stringResource(R.string.secure_settings_adb_dialog_message, adbCommand),
            confirmText = stringResource(R.string.secure_settings_adb_copy),
            dismissOnConfirm = false,
            onConfirm = {
                copyToClipboard(context, adbCommand)
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            },
            dismissText = stringResource(R.string.confirm),
        )
    }
}

/** 一条权限：标题 + 已开启/未开启胶囊 + 用途说明，整行点击去授权。 */
@Composable
private fun SettingsCardScope.PermissionStatusRow(
    title: String,
    detail: String,
    granted: Boolean,
    toneWhenMissing: StatusTone = StatusTone.Bad,
    statusLabel: String? = null,
    onClick: () -> Unit,
) {
    StatusRow(
        title = title,
        pill = statusLabel ?: stringResource(
            if (granted) R.string.permission_state_granted else R.string.permission_state_missing,
        ),
        tone = if (granted) StatusTone.Good else toneWhenMissing,
        detail = detail,
        onClick = onClick,
    )
}

/**
 * 首页入口行：通道没通时先把通道状态亮出来（那是要用户动手的），
 * 通道已就绪时退化为保活摘要——一行只说最该看的那件事。
 */
@Composable
fun SettingsCardScope.BackgroundPermissionsEntryCard(
    privilegeMode: PrivilegeMode,
    privilegedAccessGranted: Boolean,
    batteryOptimizationExempt: Boolean,
    hideFromRecents: Boolean,
    accessibilityKeepAliveEnabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit,
) {
    val channelSubtitle = when (privilegeMode) {
        PrivilegeMode.SHIZUKU -> if (privilegedAccessGranted) {
            stringResource(R.string.privilege_mode_entry_summary_shizuku_ready)
        } else {
            stringResource(R.string.privilege_mode_entry_summary_shizuku_missing)
        }
        PrivilegeMode.ROOT -> if (privilegedAccessGranted) {
            stringResource(R.string.privilege_mode_entry_summary_root_ready)
        } else {
            stringResource(R.string.privilege_mode_entry_summary_root_missing)
        }
    }
    val keepAliveSubtitle = when {
        batteryOptimizationExempt && hideFromRecents ->
            stringResource(R.string.app_keep_alive_entry_summary_all)
        batteryOptimizationExempt ->
            stringResource(R.string.app_keep_alive_entry_summary_battery)
        hideFromRecents ->
            stringResource(R.string.app_keep_alive_entry_summary_recents)
        accessibilityKeepAliveEnabled ->
            stringResource(R.string.secure_settings_enabled_summary)
        else -> stringResource(R.string.app_keep_alive_entry_desc)
    }
    SettingNavigationRow(
        icon = { label ->
            Icon(HomeLeadingIcons.batteryKeepAlive(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.background_permissions_title),
        subtitle = if (privilegedAccessGranted) keepAliveSubtitle else channelSubtitle,
        onClick = onClick,
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("adb_command", text))
}
