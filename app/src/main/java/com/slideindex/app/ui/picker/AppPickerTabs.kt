package com.slideindex.app.ui.picker

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.miuix.LocalMiuixContentUnderBar
import com.slideindex.app.ui.miuix.LocalMiuixPageBackdrop
import com.slideindex.app.ui.miuix.LocalMiuixScreenBackdrop
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import top.yukonga.miuix.kmp.basic.TabRowColors
import com.slideindex.app.ui.miuix.miuixTabRowContourColors
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 挑应用页面统一的 tab 条（动作 / 应用程序 / 快捷方式 / 小组件 的任意子集）。
 *
 * 六个容器（手势动作、快捷启动器悬浮版/全屏版、蜂窝、小组件面板、展开面板）原本各写一遍
 * `MiuixTabRowWithContour`，样式与间距容易漂移；统一到这里后，改样式只需要改一处。
 *
 * 各页面**内容**仍然自己实现：小组件面板有「小组件」页、展开面板有槽位专属操作、
 * 动作页有窗口模式弹窗——这些不是同一张列表能覆盖的，所以只收口 tab 条本身。
 *
 * 有背板时用 OS4 玻璃分段 tab（与「通知滤盒」「图标库」一致）：内容滚到栏下才升起玻璃，
 * 空列表/未滚动退回纯色；选中项用主题主色。没有背板（模糊不可用）时回退轮廓 tab。
 */
@Composable
fun AppPickerTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    // 宿主容器（顶栏搜索区 / 悬浮面板 / 展开面板）已经提供 12dp 水平留白，
    // 这里默认 0，避免再叠一层把 tab 槽缩进 24dp。
    contentHorizontalPadding: Dp = 0.dp,
    verticalPadding: Dp = 4.dp,
    contourHost: MiuixTabRowContourHost = MiuixTabRowContourHost.AppScaffold,
    colors: TabRowColors = miuixTabRowContourColors(contourHost),
    /** false = ??????(???????????,????????????)? */
    glass: Boolean = true,
) {
    val backdrop = LocalMiuixScreenBackdrop.current ?: LocalMiuixPageBackdrop.current
    if (backdrop != null && glass) {
        GlassSegmentedTabRow(
            tabs = tabs,
            selectedIndex = selectedTabIndex,
            onSelect = onTabSelected,
            backdrop = backdrop,
            surfaceAlpha = if (LocalMiuixContentUnderBar.current.value) 1f else 0f,
            selectedContentColor = MiuixTheme.colorScheme.primary,
            contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = modifier.padding(
                horizontal = contentHorizontalPadding,
                vertical = verticalPadding,
            ),
        )
        return
    }
    MiuixTabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        listState = listState,
        contentHorizontalPadding = contentHorizontalPadding,
        verticalPadding = verticalPadding,
        contourHost = contourHost,
        colors = colors,
    )
}
