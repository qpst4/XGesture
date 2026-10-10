package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.slideindex.app.R
import com.slideindex.app.cloudbackup.CloudBackupCoordinator
import com.slideindex.app.cloudstorage.CloudStorageConfig
import com.slideindex.app.cloudstorage.CloudStorageSettings
import com.slideindex.app.settings.CloudStorageConfigRepository
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.ui.feedback.UserMessageBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 连接测试的界面状态；[success] 为 null 表示还没有测过。 */
data class CloudConnectionTestState(
    val testing: Boolean = false,
    val success: Boolean? = null,
    val message: String? = null,
)

@HiltViewModel
class CloudStorageSettingsViewModel @Inject constructor(
    private val configRepository: CloudStorageConfigRepository,
    private val coordinator: CloudBackupCoordinator,
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

    private val _connectionTest = MutableStateFlow(CloudConnectionTestState())
    val connectionTest: StateFlow<CloudConnectionTestState> = _connectionTest.asStateFlow()

    fun testConnection(config: CloudStorageConfig) {
        if (_connectionTest.value.testing) return
        viewModelScope.launch {
            _connectionTest.value = CloudConnectionTestState(testing = true)
            _connectionTest.value = runCatching { coordinator.testConnection(config) }.fold(
                onSuccess = {
                    CloudConnectionTestState(
                        success = true,
                        message = appContext.getString(R.string.cloud_storage_connection_ok),
                    )
                },
                onFailure = { error ->
                    CloudConnectionTestState(
                        success = false,
                        message = error.message ?: error::class.simpleName,
                    )
                },
            )
        }
    }

    fun dismissConnectionTest() {
        _connectionTest.value = CloudConnectionTestState()
    }

    fun upsertConfig(config: CloudStorageConfig) = launchSettingsWrite {
        configRepository.upsert(config)
    }

    fun removeConfig(configId: String) = launchSettingsWrite {
        configRepository.remove(configId)
    }

    fun setActiveConfig(configId: String) = launchSettingsWrite {
        configRepository.setActive(configId)
    }

    fun setRetentionCount(count: Int) = launchSettingsWrite {
        configRepository.setRetentionCount(count)
    }
}
