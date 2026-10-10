package com.slideindex.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.cloudstorage.CloudBackupEntry
import com.slideindex.app.cloudstorage.CloudStorageSettings
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixScrollableConfirmDialog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.viewmodel.CloudProgressState
import com.slideindex.app.ui.viewmodel.CloudRestorePreview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 云端备份 / 恢复页。
 *
 * 结构对应 ClipShare 的"备份位置选 s3/webdav"流程，但补齐了它缺的三件事：
 * 远端备份列表（按时间倒序，不用再靠通用文件浏览器手点）、远端删除、保留份数。
 */
@Composable
fun CloudBackupScreen(
    settings: CloudStorageSettings,
    backups: List<CloudBackupEntry>,
    loadingBackups: Boolean,
    busy: Boolean,
    progress: CloudProgressState?,
    includeSensitiveData: Boolean,
    restorePreview: CloudRestorePreview?,
    pendingDelete: CloudBackupEntry?,
    onRefresh: () -> Unit,
    onBackupNow: () -> Unit,
    onSetIncludeSensitive: (Boolean) -> Unit,
    onRestore: (CloudBackupEntry) -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissRestore: () -> Unit,
    onRequestDelete: (CloudBackupEntry?) -> Unit,
    onConfirmDelete: () -> Unit,
    onPruneNow: () -> Unit,
    onCancel: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val active = settings.active
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val progressText = when (progress) {
        null -> null
        CloudProgressState.Packing -> stringResource(R.string.cloud_backup_progress_packing)
        is CloudProgressState.Uploading -> progress.percent?.let {
            stringResource(R.string.cloud_backup_progress_uploading, it)
        } ?: stringResource(R.string.cloud_backup_progress_working)

        is CloudProgressState.Downloading -> progress.percent?.let {
            stringResource(R.string.cloud_backup_progress_downloading, it)
        } ?: stringResource(R.string.cloud_backup_progress_working)
    }

    // settingsLazyXxx 是 LazyListScope 扩展、不是 @Composable 上下文，文案要提前取好
    val targetSectionTitle = stringResource(R.string.cloud_backup_target_section)
    val runSectionTitle = stringResource(R.string.cloud_backup_section_run)
    val remoteSectionTitle = stringResource(R.string.cloud_backup_section_remote, backups.size)
    val remoteEmptyHint = stringResource(R.string.cloud_backup_remote_empty)
    val pruneHint = stringResource(R.string.cloud_backup_prune_hint)

    SettingsScreenScaffold(
        title = stringResource(R.string.cloud_backup_title),
        subtitle = if (active == null) {
            stringResource(R.string.cloud_backup_no_target)
        } else {
            stringResource(R.string.cloud_backup_target_subtitle, active.displayName, active.baseDir.ifBlank { "/" })
        },
        onBack = onBack,
    ) {
        settingsLazySmallTitle(key = "cloud-backup-target", title = targetSectionTitle)
        groupedCardItems(
            keyPrefix = "cloud-backup-target",
            items = buildList {
                add(
                    settingsCardScopeItem("cloud-backup-open-settings") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(MiuixIcons.Settings, contentDescription = label, modifier = Modifier.size(24.dp))
                            },
                            title = active?.displayName ?: stringResource(R.string.cloud_storage_not_configured),
                            subtitle = stringResource(R.string.cloud_backup_open_settings_subtitle),
                            onClick = onOpenStorageSettings,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(key = "cloud-backup-run", title = runSectionTitle)
        groupedCardItems(
            keyPrefix = "cloud-backup-run",
            items = buildList {
                add(
                    settingsCardScopeItem("cloud-backup-include-sensitive") {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_backup_include_sensitive),
                            subtitle = stringResource(R.string.settings_backup_sensitive_hint),
                            checked = includeSensitiveData,
                            enabled = !busy,
                            onCheckedChange = onSetIncludeSensitive,
                        )
                    },
                )
            },
        )
        LazySettingsItem(key = "cloud-backup-actions") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onBackupNow,
                        enabled = active != null && !busy,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Icon(
                                MiuixIcons.UploadCloud,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.cloud_backup_start),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (busy) {
                        TextButton(
                            text = stringResource(R.string.cloud_backup_cancel),
                            onClick = onCancel,
                        )
                    }
                }
                progressText?.let { status ->
                    Text(
                        text = status,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                }
            }
        }

        settingsLazySmallTitle(
            key = "cloud-backup-remote",
            title = remoteSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "cloud-backup-remote",
            items = buildList {
                add(
                    settingsCardScopeItem("cloud-backup-refresh") {
                        SettingNavigationRow(
                            icon = { label ->
                                Icon(MiuixIcons.UploadCloud, contentDescription = label, modifier = Modifier.size(24.dp))
                            },
                            title = if (loadingBackups) {
                                stringResource(R.string.cloud_backup_refreshing)
                            } else {
                                stringResource(R.string.cloud_backup_refresh)
                            },
                            subtitle = stringResource(R.string.cloud_backup_refresh_subtitle),
                            enabled = active != null && !loadingBackups,
                            onClick = onRefresh,
                        )
                    },
                )
                backups.forEach { entry ->
                    add(
                        settingsCardScopeItem("cloud-backup-${entry.name}") {
                            SettingNavigationRow(
                                icon = { label ->
                                    Icon(MiuixIcons.File, contentDescription = label, modifier = Modifier.size(24.dp))
                                },
                                title = entry.createdAtEpochMs?.let { timeFormat.format(Date(it)) }
                                    ?: entry.name,
                                subtitle = entrySubtitle(entry, context),
                                enabled = active != null && !busy,
                                onClick = { onRestore(entry) },
                                trailingContent = {
                                    TextButton(
                                        text = stringResource(R.string.cloud_storage_delete),
                                        onClick = { onRequestDelete(entry) },
                                    )
                                },
                            )
                        },
                    )
                }
            },
        )
        if (backups.isEmpty() && !loadingBackups) {
            settingsLazyHint(key = "cloud-backup-empty", text = remoteEmptyHint)
        }

        LazySettingsItem(key = "cloud-backup-prune") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPruneNow,
                    enabled = active != null && !busy && settings.hasRetentionLimit,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Icon(MiuixIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.cloud_backup_prune_now),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        settingsLazyHint(key = "cloud-backup-prune-hint", text = pruneHint)
    }

    restorePreview?.let { pending ->
        val preview = pending.preview
        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
        MiuixScrollableConfirmDialog(
            show = true,
            onDismissRequest = onDismissRestore,
            title = stringResource(R.string.cloud_backup_restore_confirm_title),
            confirmText = stringResource(R.string.cloud_backup_restore_confirm_action),
            onConfirm = onConfirmRestore,
            dismissText = stringResource(android.R.string.cancel),
        ) {
            Text(pending.entry.name)
            Text(
                stringResource(
                    R.string.settings_backup_preview_info,
                    dateFormat.format(Date(preview.exportedAtEpochMs)),
                    preview.appVersionName,
                ),
            )
            Text(
                text = stringResource(R.string.cloud_backup_restore_sensitive_warning),
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.footnote2,
            )
        }
    }

    MiuixConfirmDialog(
        show = pendingDelete != null,
        onDismissRequest = { onRequestDelete(null) },
        title = stringResource(R.string.cloud_backup_delete_confirm_title),
        message = pendingDelete?.name,
        onConfirm = onConfirmDelete,
        confirmText = stringResource(R.string.cloud_storage_delete),
        dismissText = stringResource(android.R.string.cancel),
    )
}

@Composable
private fun entrySubtitle(entry: CloudBackupEntry, context: android.content.Context): String {
    val size = entry.sizeBytes?.let { Formatter.formatFileSize(context, it) }
        ?: stringResource(R.string.cloud_backup_size_unknown)
    val version = entry.appVersionName?.let { stringResource(R.string.cloud_backup_entry_version, it) }
    return if (version == null) size else "$size · $version"
}
