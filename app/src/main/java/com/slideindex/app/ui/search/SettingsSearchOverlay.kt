package com.slideindex.app.ui.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.ui.MainBottomNavDestination
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.miuix.horizontalCutoutPadding
import com.slideindex.app.ui.settings.components.settingsCardItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults

/** 结果列表顶部留白：状态栏 + 搜索胶囊本身（与 `GlassSearchOverlay` 内部 pill 的排版一致）。 */
private val SearchBarSpace = GlassSearchOverlayDefaults.BarHeight +
    GlassSearchOverlayDefaults.VerticalPadding * 2

/**
 * 设置搜索层：全屏 `GlassSearchOverlay` + 结果列表。
 *
 * 选择全屏 overlay 而不是复用页面里的「顶栏底槽展开搜索条」：底槽那套的语义是
 * "过滤当前页"，而这里的结果来自全 App 的页面，两者不是一类交互。
 *
 * 结果点击只做「跳页」：行命中同样跳到所属页面。行内定位需要每页提供 key→index 与
 * 进页定位通道，是另一笔账，见 `SettingsSearch` 的注释。
 */
@Composable
internal fun SettingsSearchOverlay(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onOpenPage: (SettingsSearchPage) -> Unit,
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val items = remember(context) {
        val byKey = SettingsSearchIndex.byKey
        SettingsSearchIndex.pages.flatMap { page ->
            val pageTitle = context.getString(page.title)
            // 副标题走完整路径：`首页 › 交互与外观 › 悬浮窗材质`，而不是只报一个 tab 名。
            val ancestors = settingsSearchAncestors(page, byKey).map { context.getString(it.title) }
            val pagePath = (listOf(context.getString(tabLabelRes(page.tab))) + ancestors)
                .joinToString(PathSeparator)
            val pageHitSummary = pagePath
            val rowHitSummary = if (pagePath == pageTitle) pageTitle else "$pagePath$PathSeparator$pageTitle"
            buildList {
                add(SettingsSearchItem(pageTitle, pageTitle, pageHitSummary, page, isPage = true))
                page.rows.forEach { row ->
                    add(
                        SettingsSearchItem(
                            title = context.getString(row.title),
                            pageTitle = pageTitle,
                            path = rowHitSummary,
                            page = page,
                            isPage = false,
                        ),
                    )
                }
            }
        }
    }
    val resultListState = rememberLazyListState()
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val insetsPadding = insets.asPaddingValues()
    val safeTop = insetsPadding.calculateTopPadding()
    val safeBottom = insetsPadding.calculateBottomPadding()

    GlassSearchOverlay(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        label = stringResource(R.string.settings_search_hint),
        scrollOffset = {
            when {
                resultListState.layoutInfo.totalItemsCount == 0 -> 0f
                resultListState.firstVisibleItemIndex > 0 -> Float.MAX_VALUE
                else -> resultListState.firstVisibleItemScrollOffset.toFloat()
            }
        },
        topInsets = insets.only(WindowInsetsSides.Top),
        horizontalInsetsModifier = Modifier.horizontalCutoutPadding(),
    ) { query ->
        // 段标题/空态文案必须在 composable 上下文里解析：settingsLazySmallTitle 是非
        // composable 的 LazyListScope 扩展，不能在它的参数位置调用 stringResource。
        val pagesSectionTitle = stringResource(R.string.settings_search_section_pages)
        val rowsSectionTitle = stringResource(R.string.settings_search_section_rows)
        val hintText = stringResource(R.string.settings_search_hint)
        val emptyText = stringResource(R.string.settings_search_empty)
        val results = remember(query, items) {
            buildSettingsSearchResults(rankSettingsSearch(items, query))
        }
        LaunchedEffect(query) {
            if (resultListState.firstVisibleItemIndex != 0 ||
                resultListState.firstVisibleItemScrollOffset != 0
            ) {
                resultListState.scrollToItem(0)
            }
        }
        LazyColumn(
            state = resultListState,
            modifier = Modifier.horizontalCutoutPadding(),
            contentPadding = PaddingValues(
                top = safeTop + SearchBarSpace + 8.dp,
                bottom = maxOf(bottomPadding, safeBottom) + 12.dp,
            ),
        ) {
            if (query.isBlank()) {
                item(key = "search-hint") {
                    MiuixHintText(hintText)
                }
                return@LazyColumn
            }
            if (results.isEmpty) {
                item(key = "search-empty") {
                    MiuixHintText(emptyText)
                }
                return@LazyColumn
            }
            if (results.pageHits.isNotEmpty()) {
                settingsLazySmallTitle(
                    key = "search-pages-title",
                    title = pagesSectionTitle,
                )
                groupedCardItems(
                    keyPrefix = "search-pages",
                    items = results.pageHits.map { hit ->
                        settingsCardItem("page:${hit.page.key}") {
                            SearchResultRow(
                                title = hit.title,
                                summary = hit.path,
                                onClick = { onOpenPage(hit.page) },
                            )
                        }
                    },
                )
            }
            if (results.rowHits.isNotEmpty()) {
                settingsLazySmallTitle(
                    key = "search-rows-title",
                    title = rowsSectionTitle,
                )
                groupedCardItems(
                    keyPrefix = "search-rows",
                    items = results.rowHits.map { hit ->
                        settingsCardItem("row:${hit.page.key}:${hit.title}") {
                            SearchResultRow(
                                title = hit.title,
                                summary = hit.path,
                                onClick = { onOpenPage(hit.page) },
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        onClick = onClick,
    )
}

/** tab 名资源 id：拼路径时在 `remember` 块里用，不能走 `stringResource`。 */
private fun tabLabelRes(tab: MainBottomNavDestination): Int = when (tab) {
    MainBottomNavDestination.Home -> R.string.main_nav_home
    MainBottomNavDestination.Shake -> R.string.main_nav_shake
    MainBottomNavDestination.Notification -> R.string.main_nav_notification
    MainBottomNavDestination.Extension -> R.string.main_nav_extension
}

/** 路径分隔符：与系统"面包屑"同款，避免和文案里的顿号混在一起。 */
private const val PathSeparator = " › "
