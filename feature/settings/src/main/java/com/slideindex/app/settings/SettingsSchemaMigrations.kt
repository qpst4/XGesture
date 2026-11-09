package com.slideindex.app.settings

import androidx.datastore.preferences.core.MutablePreferences

/**
 * 设置存储的 schema 版本与迁移表。
 *
 * **为什么需要**：过去的格式演进靠一堆 `*_MIGRATED` 布尔键各自守一次性逻辑
 * （键散在 [SettingsPreferenceKeys]、顺序散在 `SettingsRepository.init`）。
 * 加一个键就得再加一个标记、再挑一个执行位置，顺序错了会互相覆盖。
 * 这里把"版本号 + 有序迁移表"作为唯一入口：
 *
 * - 每次启动读 `settings_schema_version`（缺省 0）；
 * - 依次执行 `version > 当前版本` 的迁移；
 * - 全部成功后写入 [CURRENT_VERSION]。
 *
 * **新增一次格式变更的做法**：
 * 1. 在 [all] 末尾追加 `Migration(CURRENT_VERSION + 1) { prefs -> ... }`；
 * 2. 把 `CURRENT_VERSION` 加一；
 * 3. 单测覆盖 `pending(fromVersion)` 的筛选与顺序。
 *
 * 迁移必须**幂等**且**同步**（只做 Preferences 读写，不要在里面发起 IO）：中途失败时版本号不会前进，下次启动会整批重跑。
 */
internal object SettingsSchemaMigrations {

    /** 当前 schema 版本；每次新增迁移都必须同步加一。 */
    const val CURRENT_VERSION = 1

    class Migration(
        val version: Int,
        val block: (MutablePreferences) -> Unit,
    )

    /**
     * 有序迁移表。
     *
     * 目前为空：既有的一次性修复（旧剪贴板键清理、FV 环启动器键迁移等）仍在
     * `SettingsRepository.init` 里按原顺序执行，行为不变；后续新增的格式变更一律进这张表。
     */
    val all: List<Migration> = emptyList()

    /** 需要从 [fromVersion] 升级到 [CURRENT_VERSION] 时，按版本号升序执行的迁移。 */
    fun pending(fromVersion: Int): List<Migration> =
        all.filter { it.version > fromVersion }.sortedBy { it.version }
}
