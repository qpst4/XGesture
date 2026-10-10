package com.slideindex.app.cloudbackup

import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.notification.NotificationFilterPreferences
import com.slideindex.app.notification.NotificationFilterRepository
import com.slideindex.app.notification.NotificationHistoryRepository
import com.slideindex.app.otp.OtpAutoFillStatsRepository
import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.search.SearchHistoryRepository
import com.slideindex.app.service.ShareImageOcrHistoryRepository
import com.slideindex.app.settings.SettingsBackupImportResult
import com.slideindex.app.shell.ShellOutputHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把恢复出来的分区写回各数据源，并刷新内存缓存。
 *
 * 与 [SettingsBackupSectionsProvider] 对称地从 `SettingsBackupViewModel` 抽出：
 * SAF 导入与"从云端恢复"必须做完全一样的落库动作，
 * 漏掉任何一个 reload 都会出现"数据已恢复但界面还是旧的"。
 */
@Singleton
class BackupImportApplier @Inject constructor(
    private val otpRecordsRepository: OtpRecordsRepository,
    private val notificationHistoryRepository: NotificationHistoryRepository,
    private val notificationFilterRepository: NotificationFilterRepository,
    private val notificationFilterPreferences: NotificationFilterPreferences,
    private val otpAutoFillStatsRepository: OtpAutoFillStatsRepository,
    private val shellOutputHistoryRepository: ShellOutputHistoryRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val clipboardHistoryRepository: ClipboardHistoryRepository,
    private val shareImageOcrHistoryRepository: ShareImageOcrHistoryRepository,
) {
    suspend fun apply(result: SettingsBackupImportResult) {
        val sections = result.sensitive
        sections.otpRecordsJson?.let { otpRecordsRepository.importRawJson(it) }
        sections.notificationHistoryJson?.let { notificationHistoryRepository.importRawJson(it) }
        sections.notificationFilterRulesJson?.let { notificationFilterRepository.importRawJson(it, replace = true) }
        sections.notificationFilterPreferencesJson?.let { notificationFilterPreferences.importRawJson(it) }
        sections.otpAutoFillStatsJson?.let { otpAutoFillStatsRepository.importRawJson(it) }
        sections.shellOutputHistoryJson?.let { shellOutputHistoryRepository.importRawJson(it) }
        sections.searchPanelHistoryJson?.let { searchHistoryRepository.importRawJson(it) }
        if (result.importedClipboardDirectory) {
            clipboardHistoryRepository.reloadFromDisk()
        }
        if (result.importedShareImageOcrHistoryDirectory) {
            shareImageOcrHistoryRepository.reloadFromDisk()
        }
    }
}
