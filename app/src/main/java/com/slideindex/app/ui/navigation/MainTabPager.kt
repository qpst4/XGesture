package com.slideindex.app.ui.navigation

import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import com.slideindex.app.MainActivity
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.settings.BottomNavStyle
import com.slideindex.app.ui.MainBottomNavDestination
import com.slideindex.app.ui.MiuixFloatingBottomNavBar
import com.slideindex.app.ui.MiuixOfficialFloatingBottomNavBar
import com.slideindex.app.ui.miuix.rememberMiuixBlurBackdrop
import dev.chrisbanes.haze.HazeState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val navBarFadeSpec = tween<Float>(durationMillis = MainNavTransitionDurationMs)
private val navBarSlideSpec = tween<IntOffset>(durationMillis = MainNavTransitionDurationMs)

/**
 * 液态玻璃与 Miuix 浮动导航共用的 pager 宿主。
 * 两种样式共用同一棵页面子树，切换样式只换底栏与 backdrop，不会销毁当前页面。
 */
@Composable
internal fun MainTabPagerHost(
    navigateToSettingsPage: (MainBottomNavDestination, AppNavKey) -> Unit,
    bottomNavStyle: BottomNavStyle,
    currentTab: MainBottomNavDestination,
    visitedTabs: SnapshotStateSet<MainBottomNavDestination>,
    backStacks: Map<MainBottomNavDestination, NavBackStack>,
    activity: MainActivity,
    deps: AppDependencies,
    permissionStates: NavPermissionStates,
    swipeDismissEnabled: Boolean,
    floatingPointerAreaPreviewEnabledState: MutableState<Boolean>,
    /**
     * 把底栏实测出的「内容需要让开的高度」回传给宿主，宿主用它算 `rootBottomContentPadding`。
     *
     * 底栏是 [Scaffold] 的 `bottomBar`，真实高度由 Scaffold 量，**不要再写死常量**：
     * miuix `GlassNavigationBar` 的高度会随字号涨，Mishka 液态玻璃的胶囊高度也随
     * label 显隐切换（62/56dp），任何静态数字都会在某个场景下不够或过量。
     */
    onBottomBarClearanceChange: (Dp) -> Unit,
    /**
     * snackbar 槽：交给 [Scaffold] 摆位，它会按**底栏实测高度**把 snackbar 放到胶囊上沿
     * （见 miuix `Scaffold.kt` 的 `snackbarOffsetFromBottom`），不需要任何手写 bottom padding。
     */
    snackbarHost: @Composable () -> Unit = {},
    bottomNavReselectCounts: Map<MainBottomNavDestination, Int>,
    hazeState: HazeState,
    bottomNavUsesHaze: Boolean,
    bottomNavBlurRadiusDp: Float,
    showBottomNavLabels: Boolean,
    isRootDestination: Boolean,
    onBottomNavBlurPreviewChange: (Float) -> Unit,
    onBottomNavBlurPreviewStop: () -> Unit,
    onTabCommitted: (MainBottomNavDestination) -> Unit,
    onTabReselected: (MainBottomNavDestination) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = currentTab.ordinal,
        pageCount = { MainBottomNavDestination.entries.size },
    )
    val mainTabPagerState = rememberMainTabPagerState(pagerState)
    // 横滑走 pager 原生手势：interactionSource 收不到拖动事件，所以自行跟踪“用户正在横滑”。
    // 用于在横滑未落位时挡住 [currentTab] 变化触发的程序化翻页，避免被打断。
    var isPagerDragTracking by remember(pagerState) { mutableStateOf(false) }
    // 捕获层先铺一层不透明底色（与外层 Scaffold 的 containerColor 一致）。
    // 页面背景由各页自己的 Scaffold 画，会跟着 overscroll 拉伸一起被拉走；被拉走后
    // 露出的那一条在捕获层里就是未绘制的透明区，玻璃采样到透明会渲染成灰黑
    // （胶囊指示器最明显：首/末 tab 的胶囊正好贴在这条边上，看起来像"按钮变色"）。
    // 参见 miuix docs/guide/blur.md：「捕获内容有透明区域时，模糊会向透明处扩散出脏色」。
    val liquidGlassBaseColor = MiuixTheme.colorScheme.surface
    val liquidGlassBackdrop = rememberLayerBackdrop {
        drawRect(liquidGlassBaseColor)
        drawContent()
    }
    val floatingNavBackdrop = rememberMiuixBlurBackdrop(bottomNavUsesHaze)
    val activeBackdrop = when (bottomNavStyle) {
        BottomNavStyle.LIQUID_GLASS -> liquidGlassBackdrop
        BottomNavStyle.FLOATING_NAV -> floatingNavBackdrop
        else -> null
    }

    LaunchedEffect(pagerState) {
        launch {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect {
                    mainTabPagerState.syncPage()
                }
        }
        launch {
            // 原生横滑与落位共用一个 scroll 突变：突变期间标记为“正在横滑”，结束即收回。
            snapshotFlow { pagerState.isScrollInProgress }
                .collect { inProgress ->
                    isPagerDragTracking = inProgress
                }
        }
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = MainBottomNavDestination.entries[page]
            visitedTabs.add(tab)
            onTabCommitted(tab)
        }
    }

    LaunchedEffect(currentTab) {
        val target = currentTab.ordinal
        if (pagerState.currentPage != target && !isPagerDragTracking && !mainTabPagerState.isNavigating) {
            mainTabPagerState.animateToPage(target)
        }
    }

    MainTabPagerBackHandler(
        enabled = isRootDestination && pagerState.currentPage != 0,
        onBackToFirstTab = { mainTabPagerState.animateToPage(0) },
    )

    // 对齐 Mishka MainPage：外层 Scaffold 固定铺 background，pager 边缘 overscroll 不会露底。
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = snackbarHost,
        bottomBar = {
            AnimatedVisibility(
                visible = isRootDestination,
                enter = fadeIn(navBarFadeSpec) +
                    slideInVertically(navBarSlideSpec) { fullHeight -> fullHeight },
                exit = fadeOut(navBarFadeSpec) +
                    slideOutVertically(navBarSlideSpec) { fullHeight -> fullHeight },
            ) {
                // 液态玻璃底栏（Mishka 实现）内部自带 navigationBars 留白，这里不再叠加。
                val bottomBarModifier = Modifier
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(bottomBarModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    when (bottomNavStyle) {
                        BottomNavStyle.LIQUID_GLASS -> MiuixFloatingBottomNavBar(
                            backdrop = liquidGlassBackdrop,
                            targetTabIndex = mainTabPagerState.selectedPage,
                            blurRadiusDp = bottomNavBlurRadiusDp,
                            glassEnabled = bottomNavUsesHaze,
                            showLabel = showBottomNavLabels,
                            onTabSelected = { tab ->
                                visitedTabs.add(tab)
                                mainTabPagerState.animateToPage(tab.ordinal)
                            },
                            onTabReselected = onTabReselected,
                        )
                        BottomNavStyle.FLOATING_NAV -> MiuixOfficialFloatingBottomNavBar(
                            selectedIndex = mainTabPagerState.selectedPage,
                            onTabSelected = { tab ->
                                visitedTabs.add(tab)
                                mainTabPagerState.animateToPage(tab.ordinal)
                            },
                            onTabReselected = onTabReselected,
                            showLabel = showBottomNavLabels,
                            backdrop = floatingNavBackdrop,
                            blurEnabled = bottomNavUsesHaze,
                        )
                        else -> Unit
                    }
                }
            }
        },
    ) { innerPadding ->
        // 底栏是 Scaffold 的 bottomBar：存在 bottomBar 时 `innerPadding.bottom` 就是它的**实测高度**
        // （MiuixScaffold 不再叠加 contentWindowInsets，见 Scaffold KDoc：topBar/bottomBar 自己负责 insets）。
        // 页面自己的 Scaffold 已经让开了 systemBars 底部，所以这里只取「底栏高出系统栏的那一截」，
        // 否则两侧各让一次，页面底部会空出一整条。
        val density = LocalDensity.current
        val systemBottom = with(density) {
            WindowInsets.systemBars.union(WindowInsets.displayCutout).getBottom(density).toDp()
        }
        val barClearance = (innerPadding.calculateBottomPadding() - systemBottom).coerceAtLeast(0.dp)
        SideEffect { onBottomBarClearanceChange(barClearance) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(activeBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier),
        ) {
            // 横滑切页交给 pager 原生手势。miuix 的 PagerSwipeNode 在 Initial 阶段抢事件，
            // 会把子级滑条（要到 Main 阶段才消费拖拽）的手势截胡——真机表现为"拖滑条只会切 tab"。
            val pagerGestureEnabled = isRootDestination
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = pagerGestureEnabled,
                pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
                    pagerState,
                    Orientation.Horizontal,
                ),
                beyondViewportPageCount = 1,
                key = { it },
            ) { page ->
                val destination = MainBottomNavDestination.entries[page]
                if (destination !in visitedTabs) return@HorizontalPager
                MainTabNavStackSingle(
                    navigateToSettingsPage = navigateToSettingsPage,
                    destination = destination,
                    backStack = backStacks[destination]!!,
                    activity = activity,
                    deps = deps,
                    permissionStates = permissionStates,
                    swipeDismissEnabled = swipeDismissEnabled,
                    floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                    rootBottomContentPadding = barClearance,
                    bottomNavReselectCount = bottomNavReselectCounts[destination] ?: 0,
                    hazeState = hazeState,
                    bottomNavUsesHaze = bottomNavUsesHaze,
                    isActiveForHaze = page == pagerState.settledPage,
                    onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
                    onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
                )
            }
        }
    }
}

@Composable
private fun MainTabPagerBackHandler(
    enabled: Boolean,
    onBackToFirstTab: () -> Unit,
) {
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = enabled,
        onBackCompleted = onBackToFirstTab,
    )
}
