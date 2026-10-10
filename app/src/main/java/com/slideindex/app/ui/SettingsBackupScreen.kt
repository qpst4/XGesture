@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.miuix.MiuixScrollableConfirmDialog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.viewmodel.SettingsBackupPreviewState
import com.slideindex.app.settings.SettingsDomain
import com.slideindex.app.settings.SettingsBackupImportDiff
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackupScreen(
    onBack: () -> Unit,
    onExport: (includeSensitiveData: Boolean, uri: android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    importPreviewState: SettingsBackupPreviewState?,
    onDismissPreview: () -> Unit,
    onConfirmImport: (android.net.Uri) -> Unit,
    missingPermissionCount: Int,
    onOpenMissingPermissions: () -> Unit,
    onOpenCloudBackup: () -> Unit,
) {
    val resources = LocalResources.current
    var includeSensitiveData by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onExport(includeSensitiveData, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImport(uri)
        }
    }

    val actionsSectionTitle = stringResource(R.string.settings_backup_section_actions)
    val permissionsSectionTitle = stringResource(R.string.settings_backup_section_permissions)
    val cloudSectionTitle = stringResource(R.string.settings_backup_cloud_entry_title)

    SettingsScreenScaffold(
        title = stringResource(R.string.settings_backup_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(key = "backup_actions_section", title = actionsSectionTitle)
        groupedCardItems(
            keyPrefix = "backup_sensitive",
            items = buildList {
                add(
                    settingsCardScopeItem("include-sensitive") {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_backup_include_sensitive),
                            subtitle = stringResource(R.string.settings_backup_sensitive_hint),
                            checked = includeSensitiveData,
                            enabled = true,
                            onCheckedChange = { includeSensitiveData = it },
                        )
                    },
                )
            },
        )

        LazySettingsItem(key = "backup-actions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        val defaultName = resources.getString(
                            R.string.settings_backup_default_filename,
                            System.currentTimeMillis(),
                        )
                        exportLauncher.launch(defaultName)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Icon(
                        MiuixIcons.Download,
                        contentDescription = stringResource(R.string.cd_export_settings),
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = stringResource(R.string.settings_backup_export),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MiuixTheme.textStyles.button,
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
                Button(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/zip", "application/x-zip-compressed", "multipart/x-zip"),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Icon(
                        MiuixIcons.UploadCloud,
                        contentDescription = stringResource(R.string.cd_import_settings),
                        tint = MiuixTheme.colorScheme.onSecondaryVariant,
                    )
                    Text(
                        text = stringResource(R.string.settings_backup_import),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MiuixTheme.textStyles.button,
                        color = MiuixTheme.colorScheme.onSecondaryVariant,
                    )
                }
            }
        }

        // 云端备份（WebDAV / S3 兼容对象存储）：独立的页面，保留策略与远端列表都在那边
        settingsLazySmallTitle(key = "backup_cloud_section", title = cloudSectionTitle)
        groupedCardItems(
            keyPrefix = "backup_cloud",
            items = buildList {
                add(
                    settingsCardScopeItem("cloud-backup") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(
                                    MiuixIcons.UploadCloud,
                                    contentDescription = label,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            title = stringResource(R.string.settings_backup_cloud_entry_title),
                            subtitle = stringResource(R.string.settings_backup_cloud_entry_desc),
                            onClick = onOpenCloudBackup,
                        )
                    },
                )
            },
        )

        if (missingPermissionCount > 0) {            settingsLazySmallTitle(
                key = "backup_permissions_section",
                title = permissionsSectionTitle,
            )
            groupedCardItems(
                keyPrefix = "backup_permissions",
                items = buildList {
                    add(
                        settingsCardScopeItem("missing-permissions") {
                            MissingPermissionsEntryCard(
                                missingCount = missingPermissionCount,
                                onClick = onOpenMissingPermissions,
                            )
                        },
                    )
                },
            )
        }
    }

    if (importPreviewState != null) {
        val preview = importPreviewState.preview
        val context = LocalContext.current
        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
        val dateString = dateFormat.format(Date(preview.exportedAtEpochMs))

        MiuixScrollableConfirmDialog(
            show = true,
            onDismissRequest = onDismissPreview,
            title = stringResource(R.string.settings_backup_preview_title),
            confirmText = stringResource(R.string.settings_backup_preview_confirm),
            onConfirm = { onConfirmImport(importPreviewState.uri) },
            dismissText = stringResource(android.R.string.cancel),
        ) {
            Text(stringResource(R.string.settings_backup_preview_info, dateString, preview.appVersionName))
            Text(
                pluralStringResource(
                    R.plurals.settings_backup_preview_count,
                    preview.totalPreferencesCount,
                    preview.totalPreferencesCount,
                ),
            )

            if (preview.domains.isNotEmpty()) {
                val domainNames = preview.domains.joinToString("、") {
                    settingsDomainLabel(context, it)
                }
                Text(stringResource(R.string.settings_backup_preview_domains, domainNames))
            }

            if (preview.importDiff.hasChanges) {
                SettingsBackupDiffSection(context, preview.importDiff)
            }

            if (preview.hasCloudLlmConfig) {
                Text(stringResource(R.string.settings_backup_preview_cloud_config))
            }

            if (preview.hasOtpRecords || preview.hasNotificationHistory ||
                preview.hasNotificationFilterRules || preview.hasNotificationFilterPreferences ||
                preview.hasOtpAutoFillStats || preview.hasShellOutputHistory ||
                preview.hasClipboardDirectory || preview.hasShareImageOcrHistoryDirectory
            ) {
                val sensitiveItems = listOfNotNull(
                    if (preview.hasOtpRecords) stringResource(R.string.settings_domain_sensitive_otp) else null,
                    if (preview.hasNotificationHistory) stringResource(R.string.settings_domain_sensitive_notification) else null,
                    if (preview.hasNotificationFilterRules) {
                        stringResource(R.string.settings_domain_sensitive_notification_filter_rules)
                    } else {
                        null
                    },
                    if (preview.hasNotificationFilterPreferences) {
                        stringResource(R.string.settings_domain_sensitive_notification_filter_prefs)
                    } else {
                        null
                    },
                    if (preview.hasOtpAutoFillStats) stringResource(R.string.settings_domain_sensitive_otp_stats) else null,
                    if (preview.hasShellOutputHistory) stringResource(R.string.settings_domain_sensitive_shell_history) else null,
                    if (preview.hasClipboardDirectory) stringResource(R.string.settings_domain_sensitive_clipboard) else null,
                    if (preview.hasShareImageOcrHistoryDirectory) {
                        stringResource(R.string.settings_domain_sensitive_share_image_ocr)
                    } else {
                        null
                    },
                ).joinToString("、")
                Text(
                    text = stringResource(R.string.settings_backup_preview_sensitive, sensitiveItems),
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.body2,
                )
            }

            Text(
                text = stringResource(R.string.settings_backup_preview_warning),
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.headline2,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsBackupDiffSection(
    context: android.content.Context,
    diff: SettingsBackupImportDiff,
) {
    if (diff.overwrittenDomainCounts.isNotEmpty()) {
        Text(
            text = stringResource(
                R.string.settings_backup_diff_overwritten,
                formatDomainDiffSummary(context, diff.overwrittenDomainCounts),
            ),
            style = MiuixTheme.textStyles.body2,
        )
    }
    if (diff.newDomainCounts.isNotEmpty()) {
        Text(
            text = stringResource(
                R.string.settings_backup_diff_new,
                formatDomainDiffSummary(context, diff.newDomainCounts),
            ),
            style = MiuixTheme.textStyles.body2,
        )
    }
}

private fun formatDomainDiffSummary(
    context: android.content.Context,
    counts: Map<SettingsDomain, Int>,
): String = counts.entries.joinToString("、") { (domain, count) ->
    "${settingsDomainLabel(context, domain)}（$count）"
}

fun settingsDomainLabel(context: android.content.Context, domain: SettingsDomain): String = when (domain) {
    SettingsDomain.EDGE_GESTURES -> context.getString(R.string.settings_domain_edge)
    SettingsDomain.SHAKE_GESTURES -> context.getString(R.string.settings_domain_shake)
    SettingsDomain.MESSAGE_DANMAKU -> context.getString(R.string.settings_domain_message)
    SettingsDomain.OTP_AUTO_INPUT -> context.getString(R.string.settings_domain_otp)
    SettingsDomain.FLOATING_POINTER -> context.getString(R.string.settings_domain_floating_pointer)
    SettingsDomain.WIDGET_PANEL -> context.getString(R.string.settings_domain_widget_panel)
    SettingsDomain.QUICK_LAUNCHER -> context.getString(R.string.settings_domain_quick_launcher)
    SettingsDomain.FREE_WINDOW -> context.getString(R.string.settings_domain_free_window)
    SettingsDomain.GENERAL -> context.getString(R.string.settings_domain_general)
}
