package com.slideindex.app.cloudstorage

import kotlinx.serialization.Serializable

/**
 * 云备份的全部持久化状态：后端配置列表 + 当前选中项 + 远端保留份数。
 *
 * [retentionCount] 语义：大于 0 表示远端只保留最新的 N 份备份，其余在每次上传成功后清理；
 * 小于等于 0 表示不限制（对齐 ClipShare 的"远端无限堆积、只能手工删"的老行为，作为可选退路）。
 */
@Serializable
data class CloudStorageSettings(
    val configs: List<CloudStorageConfig> = emptyList(),
    val activeConfigId: String? = null,
    val retentionCount: Int = DEFAULT_RETENTION_COUNT,
) {
    val active: CloudStorageConfig?
        get() = configs.firstOrNull { it.id == activeConfigId }

    val hasRetentionLimit: Boolean
        get() = retentionCount > 0

    companion object {
        const val DEFAULT_RETENTION_COUNT = 5
        const val MAX_RETENTION_COUNT = 100

        val EMPTY = CloudStorageSettings()
    }
}
