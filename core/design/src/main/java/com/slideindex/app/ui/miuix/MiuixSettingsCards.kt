package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card

/**
 * 类型 1：列表设置卡（灰底上的白 Card，多行开关/箭头/滑条）。
 * 等价于 [groupedCardItems]，作为统一入口。
 */
fun LazyListScope.MiuixListSettingsCard(
    keyPrefix: String,
    items: List<CardItem>,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 12.dp,
    outerHorizontalPadding: Dp = 12.dp,
    insidePadding: PaddingValues = PaddingValues(0.dp),
    selectableGroup: Boolean = false,
) {
    groupedCardItems(
        keyPrefix = keyPrefix,
        items = items,
        outerTopPadding = outerTopPadding,
        outerBottomPadding = outerBottomPadding,
        outerHorizontalPadding = outerHorizontalPadding,
        insidePadding = insidePadding,
        selectableGroup = selectableGroup,
    )
}

/**
 * 类型 2：Tab 在白 Card 内（Tab 条为浅灰，选中 Tab 为白 pill）。
 * 对齐 miuix TabRowSection。
 */
@Composable
fun MiuixTabSettingsCard(
    tabs: List<String>?,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contourHost: MiuixTabRowContourHost = MiuixTabRowContourHost.SurfaceContainer,
    contentTopPadding: Dp? = null,
    outerHorizontalPadding: Dp = 12.dp,
    outerBottomPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val showTabs = !tabs.isNullOrEmpty()
    val resolvedContentTopPadding = contentTopPadding ?: if (showTabs) 12.dp else 0.dp
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = outerHorizontalPadding)
            .padding(bottom = outerBottomPadding),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showTabs) {
                val tabLabels = tabs
                MiuixTabRowWithContourInCard(
                    tabs = tabLabels,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = onTabSelected,
                    contourHost = contourHost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = resolvedContentTopPadding),
                content = content,
            )
        }
    }
}

/** 自定义块：下载/安装进度等临时信息卡（外 12dp，内 16dp）。 */
@Composable
fun MiuixProgressCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}
