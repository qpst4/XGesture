package com.slideindex.app.ui.search

import com.slideindex.app.ui.navigation.AppNavKey
import com.slideindex.app.util.PinyinHelper

/**
 * 一条可搜索项：页面命中或行命中。两者都跳到 [page]，行命中只是把标题亮出来，
 * 本轮不做"滚到那一行"（Compose 的 LazyList 没有 scrollToKey，逐页接线成本另算）。
 *
 * @param path 副标题用的完整路径，形如 `首页 › 交互与外观 › 悬浮窗材质`。
 *   页面命中时是"到父页面为止"的路径（标题本身就是当前页），行命中时连页面一起带上。
 */
internal data class SettingsSearchItem(
    val title: String,
    val pageTitle: String,
    val path: String,
    val page: SettingsSearchPage,
    val isPage: Boolean,
)

/** 结果分成「设置页面」与「页面下的设置项」两段。 */
internal data class SettingsSearchResults(
    val pageHits: List<SettingsSearchItem>,
    val rowHits: List<SettingsSearchItem>,
) {
    val isEmpty: Boolean get() = pageHits.isEmpty() && rowHits.isEmpty()
}

/** 结果上限：再多的命中用户也只会看前几屏，截断能省掉一整个长列表的组合开销。 */
internal const val SettingsSearchResultLimit = 80

/** 四个 tab 的根页：路径从这里往下算，根页自己的标题不重复进路径。 */
private val SettingsSearchTabRoots = setOf(
    AppNavKey.HomeMain,
    AppNavKey.ShakeGestures,
    AppNavKey.NotificationHub,
    AppNavKey.ExtensionHub,
)

/**
 * 从 [page] 往上数祖先页（根在前、父页在后），遇到 tab 根页就停。
 *
 * 只用于拼副标题路径，所以带防环上限：`parent` 是导航推断出来的，理论上不该成环，
 * 但填错了也不该让搜索卡死。
 */
internal fun settingsSearchAncestors(
    page: SettingsSearchPage,
    byKey: Map<AppNavKey, SettingsSearchPage>,
): List<SettingsSearchPage> {
    val chain = ArrayDeque<SettingsSearchPage>()
    var cursor = page.parent?.let(byKey::get)
    var depth = 0
    while (cursor != null && depth++ < 8) {
        if (cursor.key in SettingsSearchTabRoots) break
        chain.addFirst(cursor)
        cursor = cursor.parent?.let(byKey::get)
    }
    return chain.toList()
}

/**
 * 三档排序：全等 > 前缀 > 包含，最后补拼音（`xuanfuqiu` 能命中「悬浮球」）。
 *
 * 不抄 miuix 示例里那套「词边界」档：它靠大写字母与非字母数字判边界，中文永远不命中，
 * 照搬只会多一档死代码。
 */
internal fun rankSettingsSearch(
    items: List<SettingsSearchItem>,
    query: String,
    limit: Int = SettingsSearchResultLimit,
): List<SettingsSearchItem> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return items
        .mapNotNull { item -> matchTier(item.title, q)?.let { item to it } }
        .sortedWith(
            compareBy<Pair<SettingsSearchItem, Int>> { it.second }
                .thenBy { if (it.first.isPage) 0 else 1 }
                // 同一行可能挂在多个"共用同一个 Screen 文件"的页面上（剪贴板浮窗那一家四口），
                // 路径短的先留下，结果里就不会出现四条一模一样的「背景模糊」。
                .thenBy { it.first.path.length }
                .thenBy { it.first.title.length }
                .thenBy { it.first.title },
        )
        .map { it.first }
        .distinctBy { it.title }
        .take(limit)
}

/** 供 UI 直接消费：页面命中与行命中各成一段。 */
internal fun buildSettingsSearchResults(results: List<SettingsSearchItem>): SettingsSearchResults =
    SettingsSearchResults(
        pageHits = results.filter { it.isPage },
        rowHits = results.filterNot { it.isPage },
    )

private fun matchTier(title: String, q: String): Int? {
    val lower = title.lowercase()
    return when {
        lower == q -> 0
        lower.startsWith(q) -> 1
        lower.contains(q) -> 2
        PinyinHelper.sortKey(title).contains(q) -> 3
        else -> null
    }
}
