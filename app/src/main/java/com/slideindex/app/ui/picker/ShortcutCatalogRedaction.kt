package com.slideindex.app.ui.picker

import com.slideindex.app.util.AppShortcutLoader

/**
 * 系统快捷方式目录被脱敏时的降级策略。
 *
 * 背景：部分设备（厂商 / 企业策略 / 隐私 hook）的 ShortcutService 会把 `ShortcutInfo`
 * 打成脱敏形式——名字和 id 都是 `***`（Android 框架 `ShortcutInfo.toString()` 的固定写法）。
 * 这种条目既认不出名字，也无法启动（id 不是真 id），但**条目数不为 0**，
 * 所以不会命中「目录为空才回落静态清单」的老兜底。
 *
 * 策略（阈值判定、整表切换、**不做跨源合并**）：
 * - 逐条丢弃 id 被脱敏的条目；
 * - 若「全部条目都被脱敏」的 App 数达到 [MIN_REDACTED_APPS_FOR_STATIC]，或所有 App 都如此，
 *   判定为系统性脱敏，整表改用静态清单目录（由调用方执行）；
 * - 静态清单也为空时，调用方退回本类给出的 [Plan.usableGroups]。
 *
 * 只切不并：两套数据源的 id 命名空间不同（系统 `shortcutId` vs 静态 `包名/类名(intents)`），
 * 跨源去重永远对不上，合并会出重复条目 / 重复列表 key，所以这里刻意不做合并。
 */
object ShortcutCatalogRedaction {

    /** Android 框架脱敏输出里 id / label 的固定占位。 */
    const val REDACTED_MARKER = "***"

    /** 多少个不同 App 全被脱敏，就认定系统数据不可用（正常设备一个都不会有）。 */
    const val MIN_REDACTED_APPS_FOR_STATIC = 3

    data class Plan(
        /** 去掉脱敏条目后的目录；无脱敏时与入参是同一批对象。 */
        val usableGroups: List<AppShortcutLoader.AppShortcutGroup>,
        /** 「所有条目都被脱敏」的 App 数。 */
        val redactedAppCount: Int,
        /** 是否应整表改用静态清单目录。 */
        val useStatic: Boolean,
    )

    /**
     * 判定单个快捷方式 id 是否被系统脱敏。
     *
     * 只看 id，不看 label：`label == "***"` 但 id 是真实的条目仍然能启动，不该被丢掉。
     */
    fun isRedactedId(shortcutId: String?): Boolean {
        val id = shortcutId?.trim().orEmpty()
        return id.isNotEmpty() && id.startsWith(REDACTED_MARKER)
    }

    fun plan(groups: List<AppShortcutLoader.AppShortcutGroup>): Plan {
        if (groups.isEmpty()) return Plan(usableGroups = groups, redactedAppCount = 0, useStatic = false)

        var redactedApps = 0
        val usableGroups = ArrayList<AppShortcutLoader.AppShortcutGroup>(groups.size)
        for (group in groups) {
            val shortcuts = group.shortcuts
            val kept = shortcuts.filterNot { isRedactedId(it.shortcutId) }
            if (kept.isEmpty() && shortcuts.isNotEmpty()) redactedApps++
            if (kept.isNotEmpty()) {
                usableGroups += if (kept.size == shortcuts.size) group else group.copy(shortcuts = kept)
            }
        }

        // 「全部 App 都被脱敏」也要切：设备上本来就没几个 App 有快捷方式时，
        // 单靠数量阈值（>= 3）永远等不到，会一直显示一屏 `***`。
        val allRedacted = redactedApps == groups.size
        val useStatic = redactedApps >= MIN_REDACTED_APPS_FOR_STATIC || allRedacted
        return Plan(usableGroups = usableGroups, redactedAppCount = redactedApps, useStatic = useStatic)
    }
}
