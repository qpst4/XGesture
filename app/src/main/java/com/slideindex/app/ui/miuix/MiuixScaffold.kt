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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import com.slideindex.app.ui.LocalMainNavContentStartInset
import com.slideindex.app.ui.mainNavStartInset
import com.slideindex.app.ui.LocalMainNavTitleAlpha
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.glass.GlassCardDefaults
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 三个脚手架共用的 Scaffold inset 配置：水平方向交给 `horizontalCutoutPadding()`，避免重复计算。 */
internal val MiuixScaffoldContentWindowInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Vertical)

/**
 * 三个脚手架共用的玻璃顶栏：标题缩进、`titleAlpha`、backdrop、actions 间距统一在这里，
 * 各页面只传内容。
 */
@Composable
internal fun MiuixGlassTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    bottomContent: @Composable () -> Unit = {},
    contentUnderBar: Boolean? = null,
) {
    val contentStartInset = LocalMainNavContentStartInset.current
    Box(Modifier.fillMaxWidth()) {
        AdaptiveTopAppBar(
            modifier = Modifier.fillMaxWidth(),
            contentModifier = Modifier.mainNavStartInset(contentStartInset),
            backdrop = backdrop,
            title = title,
            titleAlpha = LocalMainNavTitleAlpha.current,
            scrollBehavior = scrollBehavior,
            contentUnderBar = contentUnderBar,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    }
}

private fun PaddingValues.withListContentPadding(
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
): PaddingValues = PaddingValues(
    start = calculateStartPadding(layoutDirection),
    top = calculateTopPadding(),
    end = calculateEndPadding(layoutDirection),
    bottom = calculateBottomPadding(),
)

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
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    userScrollEnabled: Boolean = true,
    listState: LazyListState? = null,
    bottomContentPadding: Dp = 0.dp,
    /** 悬浮按钮需要预留的底部空间，见 [MiuixSettingsFabInset]；默认 0 = 不做任何预留。 */
    fabInset: Dp = 0.dp,
    bottomBar: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val resolvedListState = listState ?: rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val barBackdrop = rememberMiuixBlurBackdrop()
    // 供顶栏内的玻璃组件（如 GlassSegmentedTabRow）决定 surfaceAlpha：内容滚到栏下才升起玻璃。
    val contentUnderBar = remember(resolvedListState) {
        derivedStateOf { resolvedListState.canScrollBackward }
    }
    // 让顶栏 actions 与列表项里的玻璃浮层都能取到页面背板（玻璃菜单采样用）。
    CompositionLocalProvider(
        LocalMiuixScreenBackdrop provides barBackdrop,
        LocalMiuixContentUnderBar provides contentUnderBar,
    ) {
        Scaffold(
        contentWindowInsets = MiuixScaffoldContentWindowInsets,
            modifier = modifier.imePadding(),
            topBar = {
                MiuixGlassTopBar(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    backdrop = barBackdrop,
                    contentUnderBar = contentUnderBar.value,
                    navigationIcon = { navigationIcon?.invoke() },
                    actions = actions,
                    bottomContent = bottomContent,
                )
            },
            floatingActionButton = floatingActionButton,
            bottomBar = bottomBar,
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            LazyColumn(
                state = resolvedListState,
                modifier = Modifier
                    .fillMaxHeight()
                    // layerBackdrop 必须在 inset/padding 之前（Mishka 迁移指南 §6.1）：
                    // 放在 horizontalCutoutPadding 之后会把录制的图层缩窄、起点右移，
                    // 顶栏从 (0,0) 全宽采样，横竖屏/刘海切换就会出现模糊截断错位。
                    .then(barBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                    .horizontalCutoutPadding()
                    .scrollEndHaptic()
                    .miuixOverScrollVertical(scrollBehavior)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding.withListContentPadding(layoutDirection),
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
    /** 顶栏下方的附加内容（可展开搜索条等），与 [MiuixListScaffold] 的 `bottomContent` 一致。 */
    bottomContent: @Composable () -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    /** 悬浮按钮需要预留的底部空间，见 [MiuixSettingsFabInset]；默认 0 = 不做任何预留。 */
    fabInset: Dp = 0.dp,
    bottomBar: @Composable () -> Unit = {},
    /** 外部持有的滚动状态（可拖拽排序的卡片段需要它来定位 from/to 项）。 */
    listState: LazyListState? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val barBackdrop = rememberMiuixBlurBackdrop(enabled = !overlayMode)
    val resolvedListState = listState ?: rememberLazyListState()
    // 供顶栏 bottomContent 里的玻璃组件（如 GlassSegmentedTabRow）决定 surfaceAlpha。
    val contentUnderBar = remember(resolvedListState) {
        derivedStateOf { resolvedListState.canScrollBackward }
    }
    CompositionLocalProvider(
        LocalMiuixScreenBackdrop provides barBackdrop,
        LocalMiuixContentUnderBar provides contentUnderBar,
    ) {
        Scaffold(
        contentWindowInsets = MiuixScaffoldContentWindowInsets,
            modifier = modifier
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .imePadding(),
            topBar = {
                MiuixGlassTopBar(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    backdrop = if (overlayMode) null else barBackdrop,
                    contentUnderBar = contentUnderBar.value,
                    navigationIcon = {
                        if (onBack != null) {
                            MiuixGlassBackNavigationIcon(onBack)
                        }
                    },
                    actions = actions,
                    bottomContent = bottomContent,
                )
            },
            floatingActionButton = floatingActionButton,
            bottomBar = bottomBar,
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            val listModifier = Modifier
                .fillMaxSize()
                // 同上：layerBackdrop 必须先于 inset/padding（Mishka 迁移指南 §6.1）。
                .then(
                    if (!overlayMode) {
                        barBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier
                    } else {
                        Modifier
                    },
                )
                .horizontalCutoutPadding()
                .scrollEndHaptic()
                .miuixOverScrollVertical(scrollBehavior)
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
                    state = resolvedListState,
                    modifier = listModifier,
                    contentPadding = innerPadding.withListContentPadding(layoutDirection),
                    overscrollEffect = null,
                    userScrollEnabled = scrollContent,
                    content = lazyContent,
                )
            } else {
                LazyColumn(
                    state = resolvedListState,
                    modifier = listModifier,
                    contentPadding = innerPadding.withListContentPadding(layoutDirection),
                    overscrollEffect = null,
                    userScrollEnabled = scrollContent,
                    content = lazyContent,
                )
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

/** OS4 玻璃顶栏里的返回按钮：胶囊材质/尺寸由 [GlassIconButton] 从顶栏上下文继承。 */
@Composable
fun MiuixGlassBackNavigationIcon(onBack: () -> Unit) {
    GlassIconButton(onClick = onBack) {
        Icon(
            imageVector = MiuixIcons.Glass.ChevronBackward,
            contentDescription = stringResource(R.string.cd_navigate_back),
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * 分组圆角卡片行背景：每行独立 LazyColumn item，保持虚拟化。
 *
 * 玻璃浮层内自动换成 `GlassCardDefaults.color()` 的半透明填充（与 `CardSegment` 同口径），
 * 普通页面保持不透明 `surfaceContainer`。
 */
@Composable
fun Modifier.miuixGroupedCardItem(
    index: Int,
    count: Int,
    outerHorizontalPadding: Dp = 12.dp,
    outerTopPadding: Dp = 0.dp,
    outerBottomPadding: Dp = 0.dp,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
): Modifier {
    val r = CardDefaults.CornerRadius
    val z = 0.dp
    val top = index == 0
    val bottom = index == count - 1
    val resolvedColor = when {
        color != androidx.compose.ui.graphics.Color.Unspecified -> color
        LocalMiuixGlassSurface.current -> GlassCardDefaults.color()
        else -> MiuixTheme.colorScheme.surfaceContainer
    }
    return fillMaxWidth()
        .padding(
            start = outerHorizontalPadding,
            end = outerHorizontalPadding,
            top = if (top) outerTopPadding else 0.dp,
            bottom = if (bottom) outerBottomPadding else 0.dp,
        )
        .miuixSquircleSurface(
            color = resolvedColor,
            topStart = if (top) r else z,
            topEnd = if (top) r else z,
            bottomEnd = if (bottom) r else z,
            bottomStart = if (bottom) r else z,
        )
}
