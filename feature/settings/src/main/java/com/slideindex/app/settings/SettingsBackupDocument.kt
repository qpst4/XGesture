package com.slideindex.app.settings

import kotlinx.serialization.Serializable

@Serializable
data class SettingsBackupDocument(
    val formatVersion: Int,
    val exportedAtEpochMs: Long,
    val appVersionName: String,
    val preferences: List<SettingsPreferenceEntry>,
    /**
     * 这份备份**只包含**这些模块（`SettingsModule.id`）。空 = 全量备份（旧文件也是空）。
     *
     * 只用于提示与兼容：导入时一律以文件里实际存在的键为准，
     * 不能仅凭这个字段就认定「某模块被备份过」。
     */
    val includedModules: List<String> = emptyList(),
    val otpRecordsJson: String? = null,
    val notificationHistoryJson: String? = null,
    val notificationFilterRulesJson: String? = null,
    val notificationFilterPreferencesJson: String? = null,
    val otpAutoFillStatsJson: String? = null,
    val shellOutputHistoryJson: String? = null,
    val searchPanelHistoryJson: String? = null,
    /** 全局触控（多指手势）的开关 + 条目 + 手绘轨迹；存在独立 SharedPreferences 里。 */
    val multiTouchGestureJson: String? = null,
)

@Serializable
data class SensitiveBackupSections(
    val otpRecordsJson: String? = null,
    val notificationHistoryJson: String? = null,
    val notificationFilterRulesJson: String? = null,
    val notificationFilterPreferencesJson: String? = null,
    val otpAutoFillStatsJson: String? = null,
    val shellOutputHistoryJson: String? = null,
    val searchPanelHistoryJson: String? = null,
    /** 全局触控（多指手势）的开关、条目与手绘轨迹。 */
    val multiTouchGestureJson: String? = null,
    val includeDirectories: Boolean = false,
) {
    val hasAny: Boolean
        get() = includeDirectories ||
            listOf(
                otpRecordsJson,
                notificationHistoryJson,
                notificationFilterRulesJson,
                notificationFilterPreferencesJson,
                otpAutoFillStatsJson,
                shellOutputHistoryJson,
                searchPanelHistoryJson,
                multiTouchGestureJson,
            ).any { !it.isNullOrBlank() }
}

data class SettingsBackupImportResult(
    val preferencesImported: Int,
    val sensitive: SensitiveBackupSections,
    val importedClipboardDirectory: Boolean = false,
    val importedShareImageOcrHistoryDirectory: Boolean = false,
    val importedCloudLlmConfig: Boolean = false,
)

@Serializable
data class SettingsPreferenceEntry(
    val key: String,
    val type: String,
    val value: String,
    val values: List<String> = emptyList(),
)
