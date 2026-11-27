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
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.glass.GlassCardDefaults

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
 * 类型 2A：支持虚拟化拆段的 Tab 列表卡片（Tab 条自动作为顶段圆角，下属各行独立为后续分段）。
 * 彻底替换 `item { MiuixTabSettingsCard { ... } }` 长列表反模式。
 */
fun LazyListScope.MiuixGroupedTabSettingsCard(
    keyPrefix: String,
    tabs: List<String>?,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<CardItem>,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 12.dp,
    outerHorizontalPadding: Dp = 12.dp,
    insidePadding: PaddingValues = PaddingValues(0.dp),
    contourHost: MiuixTabRowContourHost = MiuixTabRowContourHost.SurfaceContainer,
) {
    val showTabs = !tabs.isNullOrEmpty()
    val allItems = buildList {
        if (showTabs && tabs != null) {
            add(
                CardItem(key = "tab-header") {
                    MiuixTabRowWithContourInCard(
                        tabs = tabs,
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = onTabSelected,
                        contourHost = contourHost,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            )
        }
        addAll(items)
    }
    groupedCardItems(
        keyPrefix = keyPrefix,
        items = allItems,
        outerTopPadding = outerTopPadding,
        outerBottomPadding = outerBottomPadding,
        outerHorizontalPadding = outerHorizontalPadding,
        insidePadding = insidePadding,
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
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = outerHorizontalPadding)
        .padding(bottom = outerBottomPadding)
    val body: @Composable ColumnScope.() -> Unit = {
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
    // 玻璃浮层内换成 GlassCard（半透明填充，透出下层材质）；普通页面保持不透明 Card。
    if (LocalMiuixGlassSurface.current) {
        GlassCard(
            modifier = cardModifier,
            cornerRadius = MiuixGlassCardCornerRadius,
            insideMargin = MiuixGlassCardInsideMargin,
        ) {
            body()
        }
    } else {
        Card(
            modifier = cardModifier,
            insideMargin = PaddingValues(16.dp),
            content = body,
        )
    }
}

/** 自定义块：下载/安装进度等临时信息卡（外 12dp，内 16dp）。 */
@Composable
fun MiuixProgressCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp)
    if (LocalMiuixGlassSurface.current) {
        GlassCard(
            modifier = cardModifier,
            cornerRadius = MiuixGlassCardCornerRadius,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content,
            )
        }
    } else {
        Card(modifier = cardModifier) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content,
            )
        }
    }
}

/** 玻璃卡片圆角：与全站 Card 的 16dp 对齐（`CardDefaults.CornerRadius`）。 */
private val MiuixGlassCardCornerRadius = GlassCardDefaults.CornerRadius

/** 玻璃卡片内边距：与 `Card(insideMargin = 16.dp)` 保持一致。 */
private val MiuixGlassCardInsideMargin = PaddingValues(16.dp)
