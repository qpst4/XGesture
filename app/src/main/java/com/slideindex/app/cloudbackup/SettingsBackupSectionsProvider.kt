package com.slideindex.app.cloudbackup

import com.slideindex.app.notification.NotificationFilterPreferences
import com.slideindex.app.notification.NotificationFilterRepository
import com.slideindex.app.notification.NotificationHistoryRepository
import com.slideindex.app.otp.OtpAutoFillStatsRepository
import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.search.SearchHistoryRepository
import com.slideindex.app.settings.SensitiveBackupSections
import com.slideindex.app.shell.ShellOutputHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 组装"本地设置备份"里的敏感分区。
 *
 * 从 `SettingsBackupViewModel` 抽出来，让**云端备份**和 **SAF 导出**两条路走同一份实现：
 * 之前这段逻辑只在 ViewModel 里，云端备份要复用就只能复制一遍，
 * 一旦新增一个需要备份的数据源（比如新的历史表），两份清单必然走岔。
 */
@Singleton
class SettingsBackupSectionsProvider @Inject constructor(
    private val otpRecordsRepository: OtpRecordsRepository,
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val notificationFilterRepository: NotificationFilterRepository,
    private val notificationFilterPreferences: NotificationFilterPreferences,
    private val otpAutoFillStatsRepository: OtpAutoFillStatsRepository,
    private val shellOutputHistoryRepository: ShellOutputHistoryRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
) {
    /**
     * [includeSensitiveData] 为 false 时仍然带上"非敏感但必须跟随设置一起走"的分区
     * （通知过滤规则/偏好、OTP 自动填充统计、搜索面板历史），与原有行为一致。
     */
    suspend fun build(includeSensitiveData: Boolean): SensitiveBackupSections {
        val notificationFilterRulesJson = notificationFilterRepository.exportRawJson()
        val notificationFilterPreferencesJson = notificationFilterPreferences.exportRawJson()
        val otpAutoFillStatsJson = otpAutoFillStatsRepository.exportRawJson()
        val searchPanelHistoryJson = searchHistoryRepository.exportRawJson()

        if (!includeSensitiveData) {
            return SensitiveBackupSections(
                notificationFilterRulesJson = notificationFilterRulesJson,
                notificationFilterPreferencesJson = notificationFilterPreferencesJson,
                otpAutoFillStatsJson = otpAutoFillStatsJson,
                searchPanelHistoryJson = searchPanelHistoryJson,
            )
        }

        return SensitiveBackupSections(
            otpRecordsJson = otpRecordsRepository.exportRawJson(),
            notificationHistoryJson = notificationHistoryRepository.exportRawJson(),
            notificationFilterRulesJson = notificationFilterRulesJson,
            notificationFilterPreferencesJson = notificationFilterPreferencesJson,
            otpAutoFillStatsJson = otpAutoFillStatsJson,
            shellOutputHistoryJson = shellOutputHistoryRepository.exportRawJson(),
            searchPanelHistoryJson = searchPanelHistoryJson,
            includeDirectories = true,
        )
    }
}
