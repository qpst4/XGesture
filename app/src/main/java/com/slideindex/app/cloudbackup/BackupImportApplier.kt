package com.slideindex.app.cloudbackup

import com.slideindex.app.clipboard.ClipboardHistoryRepository
import com.slideindex.app.fvgesture.FvGestureLibrary
import com.slideindex.app.notification.NotificationFilterPreferences
import com.slideindex.app.notification.NotificationFilterRepository
import com.slideindex.app.notification.NotificationHistoryRepository
import com.slideindex.app.otp.OtpAutoFillStatsRepository
import com.slideindex.app.otp.OtpRecordsRepository
import com.slideindex.app.search.SearchHistoryRepository
import com.slideindex.app.service.ShareImageOcrHistoryRepository
import com.slideindex.app.settings.SettingsBackupExternalStore
import com.slideindex.app.settings.SettingsBackupImportResult
import com.slideindex.app.shell.ShellOutputHistoryRepository
import com.slideindex.app.multitouch.MultiTouchGestureSettingsStore
import com.slideindex.app.ui.GlobalIconStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    private val multiTouchGestureSettingsStore: MultiTouchGestureSettingsStore,
    private val externalStores: SettingsBackupExternalStores,
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
        sections.multiTouchGestureJson?.let { multiTouchGestureSettingsStore.importRawJson(it) }

        // 主 DataStore 之外的独立存储（宏、剪贴板历史、stash_meta、动作图标/名称等）。
        if (sections.externalStores.isNotEmpty()) {
            externalStores.import(sections.externalStores)
        }

        // 悬浮球自定义手势是文件型单例：导入时覆盖了 fooView/ 后必须重新读盘，
        // 否则内存里还是旧库，下一次 save() 会把恢复的文件整个覆盖回去。
        withContext(Dispatchers.IO) {
            FvGestureLibrary.of(FvGestureLibrary.TYPE_ICON).load(force = true)
            FvGestureLibrary.of(FvGestureLibrary.TYPE_GENERIC).load(force = true)
        }

        // 图标目录（action_icons / quick_wheel_icons / fv_slot_icons）可能被覆盖：
        // 清位图缓存并通知所有面板重绘；revision 自增让 Compose 侧也失效。
        GlobalIconStore.refreshAfterImport()

        if (result.importedClipboardDirectory &&
            SettingsBackupExternalStore.CLIPBOARD_HISTORY.id !in sections.externalStores
        ) {
            // 旧备份只有 clipboard/history.json 时才走这条迁移；新备份已经带了 DB 的 JSON，
            // 再跑一次会把刚恢复的整表清掉。
            clipboardHistoryRepository.reloadFromDisk()
        }
        if (result.importedShareImageOcrHistoryDirectory) {
            shareImageOcrHistoryRepository.reloadFromDisk()
        }
    }
}
