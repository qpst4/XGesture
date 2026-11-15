package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.notification.ActiveNotificationEntry
import com.slideindex.app.notification.NotificationFilterPreferences
import com.slideindex.app.notification.NotificationFilterRepository
import com.slideindex.app.notification.NotificationFilterRule
import com.slideindex.app.notification.NotificationFilterSettings
import com.slideindex.app.notification.NotificationHistoryItem
import com.slideindex.app.notification.NotificationHistoryRepository
import com.slideindex.app.notification.NotificationReplayResult
import com.slideindex.app.notification.NotificationRestoreResult
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

@HiltViewModel
class NotificationHistoryViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    userMessageBus: UserMessageBus,
    @ApplicationContext context: Context,
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val notificationFilterRepository: NotificationFilterRepository,
    private val notificationFilterPreferences: NotificationFilterPreferences,
    private val appRepository: AppRepository,
) : SettingsViewModel(settingsRepository, userMessageBus, context) {
    val items: StateFlow<List<NotificationHistoryItem>> = notificationHistoryRepository.items
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val rules: StateFlow<List<NotificationFilterRule>> = notificationFilterRepository.rules
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val filterSettings: StateFlow<NotificationFilterSettings> =
        notificationFilterPreferences.settings
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = NotificationFilterSettings(),
            )

    private val _refreshGeneration = MutableStateFlow(0)
    val refreshGeneration: StateFlow<Int> = _refreshGeneration.asStateFlow()

    init {
        // 「实时」tab 的数据来自监听服务发布的快照流：快照到达后重算一次，
        // 否则首次进入页面时会停留在空列表（快照比首帧慢）。
        viewModelScope.launch {
            com.slideindex.app.overlay.OverlayStatePort.activeNotificationSnapshots.collect { snapshots ->
                if (snapshots != null) _refreshGeneration.value += 1
            }
        }
    }

    private val _replayOpenAppDialog = MutableStateFlow<NotificationReplayResult.Failure?>(null)
    val replayOpenAppDialog: StateFlow<NotificationReplayResult.Failure?> = _replayOpenAppDialog.asStateFlow()

    fun loadApps() {
        viewModelScope.launch {
            runCatching { appRepository.loadApps() }.onFailure {
                userMessageBus.showError(appContext.getString(R.string.settings_save_failed))
            }
        }
    }

    fun refreshActive() {
        // 重新发布一次通知栏快照（刚进页面 / 隐藏&恢复后）。
        com.slideindex.app.overlay.OverlayStatePort.requestActiveNotificationsPublish(appContext)
        _refreshGeneration.value += 1
    }

    fun hideNotification(item: NotificationHistoryItem, listenerEnabled: Boolean) {
        if (!listenerEnabled) {
            userMessageBus.showError(appContext.getString(R.string.notification_hide_listener_required))
            return
        }
        viewModelScope.launch {
            runCatching {
                val hiddenFromShade = notificationFilterRepository.hideItemFromShade(item)
                item.id.takeIf { it.isNotBlank() }?.let { id ->
                    notificationHistoryRepository.markHidden(id, true).getOrThrow()
                }
                refreshActive()
                val messageRes = when {
                    hiddenFromShade -> R.string.notification_hidden_success
                    item.notificationKey != null -> R.string.notification_hide_failed
                    else -> R.string.notification_hidden_success
                }
                if (messageRes == R.string.notification_hide_failed) {
                    userMessageBus.showError(appContext.getString(messageRes))
                } else {
                    userMessageBus.showSuccess(appContext.getString(messageRes))
                }
            }.onFailure {
                userMessageBus.showError(appContext.getString(R.string.notification_hide_failed))
            }
        }
    }

    fun deleteItem(id: String) = launchRepositoryWrite {
        notificationHistoryRepository.delete(id)
    }

    fun clearAll() = launchRepositoryWrite {
        notificationHistoryRepository.clearAll()
    }

    fun replay(item: NotificationHistoryItem) {
        viewModelScope.launch {
            runCatching {
                when (val result = notificationHistoryRepository.replay(item)) {
                    is NotificationReplayResult.Success -> Unit
                    is NotificationReplayResult.Failure -> handleReplayFailure(result)
                }
            }.onFailure {
                userMessageBus.showError(
                    appContext.getString(R.string.notification_history_reopen_failed_reason, it.message.orEmpty()),
                )
            }
        }
    }

    fun replayActive(entry: ActiveNotificationEntry) {
        viewModelScope.launch {
            runCatching {
                when (val result = notificationHistoryRepository.replayActive(entry)) {
                    is NotificationReplayResult.Success -> Unit
                    is NotificationReplayResult.Failure -> handleReplayFailure(result)
                }
            }.onFailure {
                userMessageBus.showError(
                    appContext.getString(R.string.notification_history_reopen_failed_reason, it.message.orEmpty()),
                )
            }
        }
    }

    fun restoreSnoozed(item: NotificationHistoryItem) {
        viewModelScope.launch {
            runCatching {
                val result = notificationHistoryRepository.restoreToShade(item, notificationFilterRepository)
                showRestoreResult(result)
                refreshActive()
            }.onFailure {
                userMessageBus.showError(appContext.getString(R.string.notification_restore_unsnooze_failed))
            }
        }
    }

    fun upsertRule(rule: NotificationFilterRule) = launchRepositoryWrite {
        notificationFilterRepository.upsertRuleSuspend(rule)
    }

    fun removeRule(id: String) = launchRepositoryWrite {
        notificationFilterRepository.removeRuleSuspend(id)
    }

    fun setRuleEnabled(id: String, enabled: Boolean) = launchRepositoryWrite {
        notificationFilterRepository.setRuleEnabledSuspend(id, enabled)
    }

    fun setGroupHistoryByApp(enabled: Boolean) = launchRepositoryWrite {
        notificationFilterPreferences.setGroupHistoryByApp(enabled)
    }

    fun setNotificationHistoryEnabled(enabled: Boolean) = launchRepositoryWrite {
        notificationFilterPreferences.setNotificationHistoryEnabled(enabled)
    }

    fun setNotificationHistoryMaxCount(count: Int) = launchRepositoryWrite {
        val prefsResult = notificationFilterPreferences.setNotificationHistoryMaxCount(count)
        if (prefsResult.isFailure) return@launchRepositoryWrite prefsResult
        notificationHistoryRepository.applyMaxCountLimit(count)
    }

    fun restoreAllSnoozed(listenerEnabled: Boolean): Int {
        if (!listenerEnabled) {
            userMessageBus.showError(appContext.getString(R.string.notification_hide_listener_required))
            return -1
        }
        return notificationHistoryRepository.restoreAllSnoozed()
    }

    fun exportRulesJson(): String = notificationFilterRepository.exportRulesJson()

    fun dismissReplayOpenAppDialog() {
        _replayOpenAppDialog.value = null
    }

    fun openReplayTargetApp(packageName: String) {
        notificationHistoryRepository.openTargetApp(packageName)
        dismissReplayOpenAppDialog()
    }

    fun getCachedAppInfo(packageName: String): AppInfo? =
        appRepository.getCachedAppInfo(packageName)

    suspend fun resolveAppInfo(packageName: String): AppInfo? =
        appRepository.resolveAppInfo(packageName)

    fun ensureAppInfo(packageName: String): AppInfo? =
        appRepository.ensureAppInfo(packageName)

    suspend fun loadAppsForPicker(): List<AppInfo> =
        runCatching { appRepository.loadApps() }.getOrElse { emptyList() }

    fun searchApps(apps: List<AppInfo>, query: String): List<AppInfo> =
        appRepository.searchApps(apps, query)

    fun getCachedAppLabel(packageName: String): String? = getCachedAppInfo(packageName)?.label

    fun getActiveNotifications(historyItems: List<NotificationHistoryItem>): List<ActiveNotificationEntry> =
        notificationHistoryRepository.getActiveNotifications(historyItems)

    fun getActiveNotificationKeys(): Set<String> =
        notificationHistoryRepository.getActiveNotificationKeys()

    private fun handleReplayFailure(failure: NotificationReplayResult.Failure) {
        if (failure.offerOpenApp && failure.packageName != null) {
            _replayOpenAppDialog.value = failure
        } else {
            userMessageBus.showError(
                appContext.getString(R.string.notification_history_reopen_failed_reason, failure.reason),
            )
        }
    }

    private fun showRestoreResult(result: NotificationRestoreResult) {
        val messageRes = when (result) {
            NotificationRestoreResult.RESTORED_TO_SHADE -> R.string.notification_restore_success_shade
            NotificationRestoreResult.RULE_REMOVED_ONLY -> R.string.notification_restore_success_rule_only
            NotificationRestoreResult.UNSNOOZE_FAILED -> R.string.notification_restore_unsnooze_failed
        }
        when (result) {
            NotificationRestoreResult.UNSNOOZE_FAILED -> userMessageBus.showError(appContext.getString(messageRes))
            else -> userMessageBus.showSuccess(appContext.getString(messageRes))
        }
    }
}
