package com.slideindex.app.ui.miuix

/**
 * Portions derived from Mishka (https://github.com/YuKongA/Mishka)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import com.slideindex.app.ui.LocalMainNavContentStartInset
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private fun PaddingValues.withWideContentPadding(
    sidePadding: Dp,
    structuralStartInset: Dp,
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
): PaddingValues {
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + structuralStartInset + sidePadding,
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection) + sidePadding,
        bottom = calculateBottomPadding(),
    )
}

/** 子页列表顶部的 Mishka 式灰 Card；[subtitle] 仅在同时提供 [pageHint] 时作为 Card 后的补充纯文本。 */
fun LazyListScope.settingsPageHeaderItems(
    pageHint: String?,
    subtitle: String?,
) {
    val resolvedHint = pageHint ?: subtitle
    if (resolvedHint != null) {
        item(key = "page-hint") {
            SettingsPageHintCard(resolvedHint)
        }
    }
    if (pageHint != null && subtitle != null) {
        item(key = "subtitle") {
            MiuixHintText(subtitle)
        }
    }
}

/** TabRow 置于 [TopAppBar.bottomContent] 时的标准边距，与列表内容水平对齐。 */
@Composable
fun MiuixScaffoldTabRowBottomContent(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    ) {
        content()
    }
}

@Composable
fun MiuixListScaffold(
    title: String,
    modifier: Modifier = Modifier,
    pageHint: String? = null,
    subtitle: String? = null,
    showPageHintCard: Boolean = false,
    navigationIcon: @Composable (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    userScrollEnabled: Boolean = true,
    listState: LazyListState? = null,
    bottomContentPadding: Dp = 0.dp,
    /** 悬浮按钮需要预留的底部空间，见 [MiuixSettingsFabInset]；默认 0 = 不做任何预留。 */
    fabInset: Dp = 0.dp,
    /** 页面自建 backdrop 时传入；null 表示由本层创建。 */
    backdrop: LayerBackdrop? = null,
    /** 玻璃弹层槽：渲染在 backdrop 作用域内、Scaffold 之外，既拿得到 backdrop 也不会被录制裁剪。 */
    overlay: @Composable BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val resolvedListState = listState ?: rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val barBackdrop = backdrop ?: rememberMiuixBlurBackdrop()
    val backdropHolder = LocalPageBackdropHolder.current
    SideEffect { backdropHolder?.backdrop = barBackdrop }
    val contentStartInset = LocalMainNavContentStartInset.current
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            Box(Modifier.fillMaxWidth()) {
                MiuixTopAppBar(
                    title = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = contentStartInset),
                    backdrop = barBackdrop,
                    color = barBackdrop.miuixAppBarColor(),
                    scrollBehavior = scrollBehavior,
                    onBack = onBack,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    bottomContent = bottomContent,
                )
            }
        },
        floatingActionButton = floatingActionButton,
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        WideContentBox { sidePadding ->
            LazyColumn(
                state = resolvedListState,
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalCutoutPadding()
                    .then(barBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding.withWideContentPadding(
                    sidePadding = sidePadding,
                    structuralStartInset = contentStartInset,
                    layoutDirection = layoutDirection,
                ),
                overscrollEffect = null,
                userScrollEnabled = userScrollEnabled,
            ) {
                if (showPageHintCard) {
                    settingsPageHeaderItems(pageHint = pageHint, subtitle = subtitle)
                }
                content()
                if (bottomContentPadding > 0.dp) {
                    item(key = "list-bottom-inset") {
                        Spacer(Modifier.height(bottomContentPadding))
                    }
                }
                // 悬浮按钮压在内容之上（Scaffold 的 FAB 不参与布局），占满视口的列表项会被它切掉
                // 一截。这里在列表尾部预留出按钮的高度，滚动到底时最后一行/最后一卡就落在它上面。
                if (fabInset > 0.dp) {
                    item(key = "list-fab-inset") {
                        Spacer(Modifier.height(fabInset))
                    }
                }
            }
        }
    }
    overlay()
    }
}

/** 子页设置：Miuix 顶栏 + LazyColumn（Mishka 严格虚拟化）。 */
@Composable
fun MiuixSettingsScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    pageHint: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") enableBackHandler: Boolean = true,
    overlayMode: Boolean = false,
    scrollContent: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    /** 悬浮按钮需要预留的底部空间，见 [MiuixSettingsFabInset]；默认 0 = 不做任何预留。 */
    fabInset: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val barBackdrop = rememberMiuixBlurBackdrop(enabled = !overlayMode)
    val backdropHolder = LocalPageBackdropHolder.current
    SideEffect { backdropHolder?.backdrop = barBackdrop }
    val contentStartInset = LocalMainNavContentStartInset.current
    CompositionLocalProvider(LocalMiuixScreenBackdrop provides barBackdrop) {
        Scaffold(
            modifier = modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .imePadding(),
            topBar = {
                Box(Modifier.fillMaxWidth()) {
                    MiuixTopAppBar(
                        title = title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = contentStartInset),
                        backdrop = barBackdrop,
                        color = if (overlayMode) {
                            MiuixTheme.colorScheme.surface
                        } else {
                            barBackdrop.miuixAppBarColor()
                        },
                        scrollBehavior = scrollBehavior,
                        onBack = onBack,
                        actions = actions,
                    )
                }
            },
            floatingActionButton = floatingActionButton,
            bottomBar = bottomBar,
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            val listModifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .then(
                    if (!overlayMode) {
                        barBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier
                    } else {
                        Modifier
                    },
                )
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
            val lazyContent: LazyListScope.() -> Unit = {
                settingsPageHeaderItems(pageHint = pageHint, subtitle = subtitle)
                content()
                item(key = "settings-bottom-inset") {
                    Spacer(Modifier.height(8.dp + bottomContentPadding + fabInset))
                }
            }
            if (overlayMode) {
                LazyColumn(
                    modifier = listModifier,
                    contentPadding = innerPadding.withWideContentPadding(
                        sidePadding = 0.dp,
                        structuralStartInset = contentStartInset,
                        layoutDirection = layoutDirection,
                    ),
                    overscrollEffect = null,
                    userScrollEnabled = scrollContent,
                    content = lazyContent,
                )
            } else {
                WideContentBox { sidePadding ->
                    LazyColumn(
                        modifier = listModifier,
                        contentPadding = innerPadding.withWideContentPadding(
                            sidePadding = sidePadding,
                            structuralStartInset = contentStartInset,
                            layoutDirection = layoutDirection,
                        ),
                        overscrollEffect = null,
                        userScrollEnabled = scrollContent,
                        content = lazyContent,
                    )
                }
            }
        }
    }
}

/** 首页 Hub：大标题顶栏 + LazyColumn 内容区（对齐 Mishka：大标题下直接进内容）。 */
@Composable
fun MiuixHubScaffold(
    title: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    bottomContentPadding: Dp = 0.dp,
    content: LazyListScope.() -> Unit,
) {
    MiuixListScaffold(
        title = title,
        modifier = modifier,
        listState = listState,
        bottomContentPadding = 8.dp + bottomContentPadding,
        content = content,
    )
}

@Composable
fun MiuixBackNavigationIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_navigate_back),
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

/**
 * 分组圆角卡片行背景：每行独立 LazyColumn item，保持虚拟化。
 */
@Composable
fun Modifier.miuixGroupedCardItem(
    index: Int,
    count: Int,
    outerHorizontalPadding: Dp = 12.dp,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 0.dp,
    color: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.surfaceContainer,
): Modifier {
    val r = CardDefaults.CornerRadius
    val z = 0.dp
    val top = index == 0
    val bottom = index == count - 1
    return fillMaxWidth()
        .padding(
            start = outerHorizontalPadding,
            end = outerHorizontalPadding,
            top = if (top) outerTopPadding else 0.dp,
            bottom = if (bottom) outerBottomPadding else 0.dp,
        )
        .miuixSquircleSurface(
            color = color,
            topStart = if (top) r else z,
            topEnd = if (top) r else z,
            bottomEnd = if (bottom) r else z,
            bottomStart = if (bottom) r else z,
        )
}