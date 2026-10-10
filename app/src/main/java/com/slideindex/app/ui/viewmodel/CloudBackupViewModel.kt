package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.slideindex.app.R
import com.slideindex.app.cloudbackup.BackupImportApplier
import com.slideindex.app.cloudbackup.CloudBackupCoordinator
import com.slideindex.app.cloudbackup.CloudBackupStage
import com.slideindex.app.cloudstorage.CloudBackupEntry
import com.slideindex.app.cloudstorage.CloudStorageSettings
import com.slideindex.app.settings.CloudStorageConfigRepository
import com.slideindex.app.settings.SettingsBackupPreview
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 云端操作的进度（用于界面上的进度文案与百分比）。 */
sealed interface CloudProgressState {
    data object Packing : CloudProgressState

    data class Uploading(val percent: Int?) : CloudProgressState

    data class Downloading(val percent: Int?) : CloudProgressState
}

/** 已下载到本地、等待用户确认的恢复预览。 */
data class CloudRestorePreview(
    val entry: CloudBackupEntry,
    val file: File,
    val preview: SettingsBackupPreview,
)

@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    private val coordinator: CloudBackupCoordinator,
    configRepository: CloudStorageConfigRepository,
    private val importApplier: BackupImportApplier,
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {

    val cloudSettings: StateFlow<CloudStorageSettings> = configRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CloudStorageSettings.EMPTY,
        )

    private val _backups = MutableStateFlow<List<CloudBackupEntry>>(emptyList())
    val backups: StateFlow<List<CloudBackupEntry>> = _backups.asStateFlow()

    private val _loadingBackups = MutableStateFlow(false)
    val loadingBackups: StateFlow<Boolean> = _loadingBackups.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _progress = MutableStateFlow<CloudProgressState?>(null)
    val progress: StateFlow<CloudProgressState?> = _progress.asStateFlow()

    private val _includeSensitiveData = MutableStateFlow(false)
    val includeSensitiveData: StateFlow<Boolean> = _includeSensitiveData.asStateFlow()

    private val _restorePreview = MutableStateFlow<CloudRestorePreview?>(null)
    val restorePreview: StateFlow<CloudRestorePreview?> = _restorePreview.asStateFlow()

    private val _pendingDelete = MutableStateFlow<CloudBackupEntry?>(null)
    val pendingDelete: StateFlow<CloudBackupEntry?> = _pendingDelete.asStateFlow()

    private var runningJob: Job? = null

    init {
        // 当前后端发生变化时自动重拉远端列表。
        //
        // 否则会出现这个体验缺口：用户第一次配好云存储后端、退回本页时，
        // 页面还是"没有备份"的空列表（本页 VM 跟着返回栈存活，进页时那次 refresh 早就跑过了），
        // 看起来像备份丢了。恢复备份换掉当前后端后同理。
        viewModelScope.launch {
            cloudSettings
                .map { it.activeConfigId }
                .distinctUntilChanged()
                .collect { configId -> if (configId != null) refresh() }
        }
    }

    fun setIncludeSensitiveData(include: Boolean) {
        _includeSensitiveData.value = include
    }

    /** 拉取远端备份列表；没有配置后端时只清空列表。 */
    fun refresh() {
        val config = cloudSettings.value.active
        if (config == null) {
            _backups.value = emptyList()
            return
        }
        viewModelScope.launch {
            _loadingBackups.value = true
            runCatching { coordinator.listBackups(config) }
                .onSuccess { _backups.value = it }
                .onFailure { error -> showFailure(R.string.cloud_backup_list_failed, error) }
            _loadingBackups.value = false
        }
    }

    fun backupNow() {
        val settings = cloudSettings.value
        val config = settings.active
        if (config == null) {
            userMessageBus.showError(appContext.getString(R.string.cloud_backup_no_target))
            return
        }
        launchOperation {
            runCatching {
                coordinator.backupNow(
                    config = config,
                    includeSensitiveData = _includeSensitiveData.value,
                    retentionCount = settings.retentionCount,
                    onStage = ::onStage,
                )
            }.fold(
                onSuccess = { outcome ->
                    val message = if (outcome.pruned.isEmpty()) {
                        appContext.getString(R.string.cloud_backup_upload_success, outcome.entry.name)
                    } else {
                        appContext.getString(
                            R.string.cloud_backup_upload_success_pruned,
                            outcome.entry.name,
                            outcome.pruned.size,
                        )
                    }
                    userMessageBus.showSuccess(message)
                    refresh()
                },
                onFailure = { error -> showFailure(R.string.cloud_backup_upload_failed, error) },
            )
        }
    }

    /** 下载 + 预览；用户确认后才真正导入。 */
    fun startRestore(entry: CloudBackupEntry) {
        val config = cloudSettings.value.active ?: return
        launchOperation {
            runCatching {
                val file = coordinator.downloadBackup(config, entry, ::onStage)
                val preview = file.inputStream().use { input ->
                    settingsRepository.previewImport(input).getOrThrow()
                }
                CloudRestorePreview(entry = entry, file = file, preview = preview)
            }.fold(
                onSuccess = { _restorePreview.value = it },
                onFailure = { error -> showFailure(R.string.cloud_backup_download_failed, error) },
            )
        }
    }

    fun dismissRestorePreview() {
        _restorePreview.value?.file?.delete()
        _restorePreview.value = null
    }

    fun confirmRestore() {
        val pending = _restorePreview.value ?: return
        launchOperation {
            runCatching {
                val result = pending.file.inputStream().use { input ->
                    settingsRepository.importSettings(input).getOrThrow()
                }
                importApplier.apply(result)
                result
            }.fold(
                onSuccess = { result ->
                    userMessageBus.showSuccess(
                        appContext.resources.getQuantityString(
                            R.plurals.settings_backup_import_success,
                            result.preferencesImported,
                            result.preferencesImported,
                        ),
                    )
                },
                onFailure = { error -> showFailure(R.string.settings_backup_import_failed, error) },
            )
            dismissRestorePreview()
        }
    }

    /** 点"删除"先进确认状态，确认后才真的删远端文件。 */
    fun requestDelete(entry: CloudBackupEntry?) {
        _pendingDelete.value = entry
    }

    fun confirmDelete() {
        val entry = _pendingDelete.value ?: return
        _pendingDelete.value = null
        deleteBackup(entry)
    }

    private fun deleteBackup(entry: CloudBackupEntry) {
        val config = cloudSettings.value.active ?: return
        launchOperation {
            runCatching { coordinator.deleteBackup(config, entry) }.fold(
                onSuccess = {
                    userMessageBus.showSuccess(appContext.getString(R.string.cloud_backup_delete_success, entry.name))
                    refresh()
                },
                onFailure = { error -> showFailure(R.string.cloud_backup_delete_failed, error) },
            )
        }
    }

    /** 手动执行一次保留策略清理。 */
    fun pruneNow() {
        val settings = cloudSettings.value
        val config = settings.active ?: return
        launchOperation {
            runCatching { coordinator.prune(config, settings.retentionCount) }.fold(
                onSuccess = { deleted ->
                    userMessageBus.showSuccess(
                        appContext.getString(R.string.cloud_backup_prune_result, deleted.size),
                    )
                    refresh()
                },
                onFailure = { error -> showFailure(R.string.cloud_backup_prune_failed, error) },
            )
        }
    }

    /**
     * 取消进行中的上传/下载。
     *
     * 取消后不弹提示：协程取消会真正 abort HTTP 请求（OkHttp 的 call.cancel），
     * 界面上的进度条消失、按钮恢复可用本身就已经是反馈；
     * 硬塞一条"已取消"的错误提示反而会让人以为出错了。
     */
    fun cancelRunning() {
        runningJob?.cancel()
        runningJob = null
        _busy.value = false
        _progress.value = null
    }

    private fun launchOperation(block: suspend () -> Unit) {
        if (_busy.value) return
        runningJob = viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } finally {
                _busy.value = false
                _progress.value = null
            }
        }
    }

    private fun onStage(stage: CloudBackupStage) {
        _progress.value = when (stage) {
            CloudBackupStage.Packing -> CloudProgressState.Packing
            is CloudBackupStage.Uploading -> CloudProgressState.Uploading(percentOf(stage.transferred, stage.total))
            is CloudBackupStage.Downloading -> CloudProgressState.Downloading(percentOf(stage.transferred, stage.total))
        }
    }

    private fun percentOf(transferred: Long, total: Long): Int? =
        if (total <= 0) null else ((transferred * 100) / total).toInt().coerceIn(0, 100)

    private fun showFailure(messageRes: Int, error: Throwable) {
        userMessageBus.showError(
            appContext.getString(messageRes) + "：" + (error.message ?: error::class.simpleName.orEmpty()),
        )
    }
}
