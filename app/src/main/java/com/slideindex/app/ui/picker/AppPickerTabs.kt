package com.slideindex.app.ui.picker

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.ui.miuix.MiuixTabRowContourHost
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import top.yukonga.miuix.kmp.basic.TabRowColors
import com.slideindex.app.ui.miuix.miuixTabRowContourColors

/**
 * 挑应用页面统一的 tab 条（动作 / 应用程序 / 快捷方式 / 小组件 的任意子集）。
 *
 * 六个容器（手势动作、快捷启动器悬浮版/全屏版、蜂窝、小组件面板、展开面板）原本各写一遍
 * `MiuixTabRowWithContour`，样式与间距容易漂移；统一到这里后，改样式只需要改一处。
 *
 * 各页面**内容**仍然自己实现：小组件面板有「小组件」页、展开面板有槽位专属操作、
 * 动作页有窗口模式弹窗——这些不是同一张列表能覆盖的，所以只收口 tab 条本身。
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
) {
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
