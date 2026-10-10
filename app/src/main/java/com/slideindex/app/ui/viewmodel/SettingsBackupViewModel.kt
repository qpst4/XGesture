package com.slideindex.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.slideindex.app.BuildConfig
import com.slideindex.app.R
import com.slideindex.app.cloudbackup.BackupImportApplier
import com.slideindex.app.cloudbackup.SettingsBackupSectionsProvider
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.slideindex.app.settings.SettingsBackupPreview
import com.slideindex.app.gesture.GestureActionPermissionAuditor

/**
 * 本地（SAF）设置备份页的状态持有者。
 *
 * 敏感分区的组装与落库已抽到 [SettingsBackupSectionsProvider] / [BackupImportApplier]，
 * 与"云端备份/恢复"共用同一份实现——这两条路必须做完全一样的事，
 * 否则新增一个数据源时总有一条会漏。
 */
@HiltViewModel
class SettingsBackupViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    private val sectionsProvider: SettingsBackupSectionsProvider,
    private val importApplier: BackupImportApplier,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {

    fun exportSettings(
        includeSensitiveData: Boolean,
        uri: Uri,
    ) {
        viewModelScope.launch {
            runCatching {
                val sensitive = sectionsProvider.build(includeSensitiveData)
                appContext.contentResolver.openOutputStream(uri)?.use { output ->
                    settingsRepository.exportSettings(BuildConfig.VERSION_NAME, sensitive, output).getOrThrow()
                } ?: error("Unable to open output stream")
            }.fold(
                onSuccess = {
                    userMessageBus.showSuccess(
                        appContext.getString(R.string.settings_backup_export_success),
                    )
                },
                onFailure = {
                    userMessageBus.showError(
                        appContext.getString(R.string.settings_backup_export_failed),
                    )
                },
            )
        }
    }

    private val _importPreviewState = MutableStateFlow<SettingsBackupPreviewState?>(null)
    val importPreviewState: StateFlow<SettingsBackupPreviewState?> = _importPreviewState.asStateFlow()

    private val _navigateToMissingPermissions = MutableStateFlow(false)
    val navigateToMissingPermissions: StateFlow<Boolean> = _navigateToMissingPermissions.asStateFlow()

    fun consumeNavigateToMissingPermissions() {
        _navigateToMissingPermissions.value = false
    }

    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    settingsRepository.previewImport(input).getOrThrow()
                } ?: error("Unable to read backup file")
            }.fold(
                onSuccess = { preview ->
                    _importPreviewState.value = SettingsBackupPreviewState(
                        uri = uri,
                        preview = preview
                    )
                },
                onFailure = {
                    userMessageBus.showError(
                        appContext.getString(R.string.settings_backup_import_failed)
                    )
                }
            )
        }
    }

    fun dismissPreview() {
        _importPreviewState.value = null
    }

    fun confirmImport(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    settingsRepository.importSettings(input).getOrThrow()
                } ?: error("Unable to open input stream")
            }.fold(
                onSuccess = { result ->
                    importApplier.apply(result)
                    userMessageBus.showSuccess(
                        appContext.resources.getQuantityString(
                            R.plurals.settings_backup_import_success,
                            result.preferencesImported,
                            result.preferencesImported,
                        ),
                    )
                    dismissPreview()
                    if (GestureActionPermissionAuditor.auditMissingPermissions(
                            appContext,
                            settingsRepository.readSnapshot(),
                        ).isNotEmpty()
                    ) {
                        _navigateToMissingPermissions.value = true
                    }
                },
                onFailure = {
                    userMessageBus.showError(
                        appContext.getString(R.string.settings_backup_import_failed),
                    )
                    dismissPreview()
                }
            )
        }
    }
}

data class SettingsBackupPreviewState(
    val uri: Uri,
    val preview: SettingsBackupPreview,
)
