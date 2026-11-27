package com.slideindex.app.ui.miuix

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TabRowColors
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全 app 统一的**玻璃分段 Tab 条**（OS4 分段胶囊）。
 *
 * 这是「选择操作类型」页那套 tab 的唯一实现，之前被抄在
 * `AppPickerTabRow`、`ActionCenterScreen`、`GlobalIconLibraryScreen`、
 * `NotificationHistoryFilterBar` 四处，现在收口到这里。
 *
 * 两条路径、无第三种：
 * - **有背板**（[LocalMiuixScreenBackdrop] 或 [LocalMiuixPageBackdrop] 非空）
 *   → `GlassSegmentedTabRow`；玻璃材质透明度由 [LocalMiuixContentUnderBar] 驱动：
 *   内容还没滚到栏下时 `surfaceAlpha = 0`（纯分段、无玻璃底），滚到栏下才升起玻璃 ——
 *   这是 miuix 示例 `GlassPage` 的做法，也是我们四处的既定行为。
 * - **无背板**（模糊不可用 / 纯不透明页面）
 *   → 回退 [MiuixTabRowWithContour] 轮廓 tab，保证任何设备都不崩、不空白。
 *
 * 注意：**不要**把页面录制的背板直接塞给整条 `GlassTabRow` —— 那条 Tab 行若处在
 * 被录制的画面里，会自采样导致渲染树无限嵌套（HWUI 爆栈闪退）。本组件用
 * `GlassSegmentedTabRow` + `surfaceAlpha` 的门控，不会出现该问题。
 */
@Composable
fun MiuixGlassSegmentedTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    contentHorizontalPadding: Dp = 0.dp,
    verticalPadding: Dp = 4.dp,
    contourHost: MiuixTabRowContourHost = MiuixTabRowContourHost.AppScaffold,
    colors: TabRowColors = miuixTabRowContourColors(contourHost),
    /** false = 强制走轮廓 tab（个别要求完全不透明的页面用）。 */
    glass: Boolean = true,
) {
    // 只用「栏级」背板（由 MiuixScaffold 录制、不含栏本身）。
    // **不能**退回 LocalMiuixPageBackdrop：那是整页录制，包含本 Tab 行自己，
    // 采样到自身会让 HWUI 渲染树无限嵌套直接爆栈（OCR 模型页闪退就是这个）。
    // 拿不到栏级背板时一律走轮廓 tab 兜底。
    val backdrop = LocalMiuixScreenBackdrop.current
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
