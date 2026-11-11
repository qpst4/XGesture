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
    const val CURRENT_VERSION = 2

    class Migration(
        val version: Int,
        val block: (MutablePreferences) -> Unit,
    )

    /**
     * 有序迁移表。
     *
     * 既有的一次性修复（旧剪贴板键清理、FV 环启动器键迁移等）仍在 `SettingsRepository.init`
     * 里按原顺序执行，行为不变；后续新增的格式变更一律进这张表。
     */
    val all: List<Migration> = listOf(
        Migration(2) { prefs -> migrateClipboardHistoryFloatHandleWidth(prefs) },
    )

    /**
     * 剪贴历史贴边手柄宽度：旧键是 int（v1.10.x 起就有），新代码改用 float 的 `_v2` 键。
     *
     * 同名换类型会让 DataStore 在**读**的时候抛 `ClassCastException: Integer cannot be cast to Float`
     * —— 真机故障：升级后一启动就崩，无障碍服务被系统记成 crashed，所有浮层挂不上。
     * 这里把旧值搬到新键再删旧键。
     *
     * 读旧值走 `asMap()` 而不是 `prefs[intKey]`：万一那条记录的类型不是 int（例如被旧备份
     * 还原成别的类型），带类型的读取会抛 CCE，而迁移本身绝不能崩。
     */
    private fun migrateClipboardHistoryFloatHandleWidth(prefs: MutablePreferences) {
        val legacyKey = SettingsPreferenceKeys.LEGACY_CLIPBOARD_HISTORY_FLOAT_HANDLE_WIDTH_DP
        val legacy = prefs.asMap()[legacyKey] as? Int ?: return
        val newKey = SettingsPreferenceKeys.CLIPBOARD_HISTORY_FLOAT_HANDLE_WIDTH_DP
        // 新键已经有值（用户升级后已经在 UI 里调过）就不覆盖，只清理旧键。
        if (prefs.asMap()[newKey] == null) {
            prefs[newKey] = legacyWidthToAppearanceDp(legacy)
        }
        prefs.remove(legacyKey)
    }

    /**
     * 旧宽度 → 新外观宽度的换算。
     *
     * 两套量程**不重叠**：旧滑条是 24..50dp，新版是 5..14dp（把手被重新设计得更细）。
     * 所以"原值 + 钳位"会把旧版选了**最细** 24dp 的人推到新版**最粗** 14dp，意图正好反过来。
     * 这里按相对位置映射（24→5dp、50→14dp、旧默认 32→约 7.8dp），保留"他当年想要多细"。
     */
    private fun legacyWidthToAppearanceDp(legacyWidthDp: Int): Float {
        // 旧量程已随旧实现删除，这里按历史常量硬编码（见 e473d6b1 的 HistoryFloatHandleWidth）。
        val legacyMinDp = 24f
        val legacyMaxDp = 50f
        val ratio = (legacyWidthDp.coerceIn(24, 50).toFloat() - legacyMinDp) / (legacyMaxDp - legacyMinDp)
        val newWidthDp = HistoryFloatHandleAppearance.MIN_WIDTH_DP +
            ratio * (HistoryFloatHandleAppearance.MAX_WIDTH_DP - HistoryFloatHandleAppearance.MIN_WIDTH_DP)
        return HistoryFloatHandleAppearance.coerceWidthDp(newWidthDp)
    }

    /** 需要从 [fromVersion] 升级到 [CURRENT_VERSION] 时，按版本号升序执行的迁移。 */
    fun pending(fromVersion: Int): List<Migration> =
        all.filter { it.version > fromVersion }.sortedBy { it.version }
}
