package com.slideindex.app.search.shortcuts

import com.slideindex.app.util.ShortcutKind

/**
 * 搜索面板「应用快捷方式」候选项：某个 App 发布的可直接启动的桌面快捷方式。
 *
 * 与任务切换菜单 / 快捷启动器共用同一份数据（[com.slideindex.app.util.AppShortcutLoader]），
 * 因此这里的 [intentUris] 一定是可用 [com.slideindex.app.util.AppShortcutLoader.launchShortcut] 启动的意图。
 */
data class ShortcutSearchEntry(
    val packageName: String,
    val appLabel: String,
    val label: String,
    val shortcutId: String,
    val intentUris: List<String> = emptyList(),
    val kind: ShortcutKind = ShortcutKind.STATIC,
) {
    val dedupeKey: String get() = "$packageName/$shortcutId"
}
