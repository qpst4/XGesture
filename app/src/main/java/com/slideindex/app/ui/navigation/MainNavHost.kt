@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.slideindex.app.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import com.slideindex.app.MainActivity
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.settings.AppRootSettings
import com.slideindex.app.settings.HomeMainSettings
import com.slideindex.app.settings.BottomNavMode
import com.slideindex.app.settings.BottomNavStyle
import com.slideindex.app.settings.OverlaySettings
import com.slideindex.app.settings.usesBottomNavHaze
import com.slideindex.app.ui.FloatingBottomNavBar
import com.slideindex.app.ui.ClassicFloatingSideNavRailOverlay
import com.slideindex.app.ui.LocalMainNavContentStartInset
import com.slideindex.app.ui.classicFloatingSideNavRailSlotWidth
import com.slideindex.app.ui.mainNavClassicOverlayContentInsets
import com.slideindex.app.ui.mainNavMiuixRailContentInsets
import com.slideindex.app.ui.mainNavRailContentClip
import com.slideindex.app.ui.MainBottomNavDestination
import com.slideindex.app.ui.MainMiuixNavigationRail
import com.slideindex.app.ui.FreeWindowLayoutEditorSession
import com.slideindex.app.ui.MainBottomNavHorizontalPadding
import com.slideindex.app.ui.MainBottomNavOuterPadding
import com.slideindex.app.ui.mainAppPrefersNavigationRail
import com.slideindex.app.ui.mainAppRootBottomContentPadding
import com.slideindex.app.ui.MainMiuixBottomNavOuterPadding
import com.slideindex.app.ui.MainFloatingNavBarBottomOffset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.slideindex.app.update.UpdateHost
import com.slideindex.app.update.UpdateViewModel
import com.slideindex.app.ui.OcrEnginePackMigrationHost
import com.slideindex.app.ui.OnboardingScreen
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.ui.compose.LocalAppDependencies
import com.slideindex.app.ui.feedback.UserMessageSnackbarHost
import com.slideindex.app.ui.miuix.theme.ModuleTheme
import com.slideindex.app.ui.miuix.theme.toModuleThemeSettings
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 底栏显隐动画时长（与 NavDisplay 默认转场大致对齐）。 */
internal const val MainNavTransitionDurationMs = 400
/** 悬浮底栏（宽度 Compact，典型竖屏手机）Tab 切换 */
private const val MAIN_TAB_SWITCH_DURATION_BOTTOM_BAR_MS = 170
/** 侧栏（宽度 ≥ Medium，典型横屏 / 平板）Tab 切换 */
private const val MAIN_TAB_SWITCH_DURATION_RAIL_MS = 220
private val MainTabSwitchSlideOffset = 28.dp
private const val MainTabSwitchInactiveScale = 0.98f
private const val MainBottomNavHostFadeDurationMs = 150

@Composable
fun MainNavHost(
    activity: MainActivity,
    deps: AppDependencies,
    permissionStates: NavPermissionStates,
    initialIntentAction: String? = null,
    initialNavRoute: String? = null,
    showUpdateFromIntent: Boolean = false,
    onNavRouteConsumed: () -> Unit = {},
    onShowUpdateConsumed: () -> Unit = {},
) {
    val settingsSnapshot = remember(deps) { deps.settingsRepository.readSnapshot() }
    val rootSettings by deps.settingsRepository.appRootSettings.collectAsStateWithLifecycle(
        initialValue = AppRootSettings.from(settingsSnapshot),
    )
    val overlayUiSettings by deps.settingsRepository.overlaySettings.collectAsStateWithLifecycle(
        initialValue = OverlaySettings.from(settingsSnapshot),
    )
    val homeMainSettings by deps.settingsRepository.homeMainSettings.collectAsStateWithLifecycle(
        initialValue = HomeMainSettings.from(settingsSnapshot),
    )
    var savedBottomNavTab by rememberSaveable {
        val initialTab = when {
            initialIntentAction == MainActivity.ACTION_OPEN_NOTIFICATION_HISTORY -> {
                MainBottomNavDestination.Notification.name
            }
            initialNavRoute == MainActivity.NAV_ROUTE_EXTENSION_FREEZER ||
                initialNavRoute == MainActivity.NAV_ROUTE_EXTENSION_FREEZER_APPS -> {
                MainBottomNavDestination.Extension.name
            }
            else -> MainBottomNavDestination.Home.name
        }
        mutableStateOf(initialTab)
    }

    val homeBackStack = rememberNavBackStack<AppNavKey>(AppNavKey.HomeMain)

    val shakeBackStack = rememberNavBackStack<AppNavKey>(AppNavKey.ShakeGestures)

    val notificationInitial = if (initialIntentAction == MainActivity.ACTION_OPEN_NOTIFICATION_HISTORY) {
        arrayOf(AppNavKey.NotificationHub, AppNavKey.NotificationHistory)
    } else {
        arrayOf(AppNavKey.NotificationHub)
    }
    val notificationBackStack = rememberNavBackStack<AppNavKey>(*notificationInitial)

    val extensionInitial = when (initialNavRoute) {
        MainActivity.NAV_ROUTE_EXTENSION_FREEZER_APPS -> {
            arrayOf(AppNavKey.ExtensionHub, AppNavKey.ExtensionFreezer, AppNavKey.ExtensionFreezerApps)
        }
        MainActivity.NAV_ROUTE_EXTENSION_FREEZER -> {
            arrayOf(AppNavKey.ExtensionHub, AppNavKey.ExtensionFreezer)
        }
        else -> arrayOf(AppNavKey.ExtensionHub)
    }
    val extensionBackStack = rememberNavBackStack<AppNavKey>(*extensionInitial)

    val backStacks = mapOf(
        MainBottomNavDestination.Home to homeBackStack,
        MainBottomNavDestination.Shake to shakeBackStack,
        MainBottomNavDestination.Notification to notificationBackStack,
        MainBottomNavDestination.Extension to extensionBackStack,
    )
    var deferredTabSelection by remember { mutableStateOf<MainBottomNavDestination?>(null) }
    val currentTab = MainBottomNavDestination.valueOf(savedBottomNavTab)
    val bottomNavSelectedTab = deferredTabSelection ?: currentTab
    val activeBackStack = backStacks[currentTab]!!
    var bottomNavReselectCounts by remember {
        mutableStateOf(MainBottomNavDestination.entries.associateWith { 0 })
    }
    val visitedTabs = remember {
        mutableStateSetOf(currentTab)
    }

    val floatingPointerAreaPreviewEnabledState = rememberSaveable { mutableStateOf(false) }
    val floatingPointerAreaPreviewEnabled by floatingPointerAreaPreviewEnabledState
    val shellPrefersNavigationRail = mainAppPrefersNavigationRail()
    // 小窗编辑态会主动转屏，而本导航壳在宽度跨断点时会在底栏/侧栏之间切换布局。
    // 编辑期间冻结外壳布局，避免转屏时外壳整体换结构带来的跳变与重建。
    val frozenShellPrefersNavigationRail = remember { mutableStateOf(shellPrefersNavigationRail) }
    LaunchedEffect(shellPrefersNavigationRail, FreeWindowLayoutEditorSession.isOpen) {
        if (!FreeWindowLayoutEditorSession.isOpen) {
            frozenShellPrefersNavigationRail.value = shellPrefersNavigationRail
        }
    }
    val prefersNavigationRail = if (FreeWindowLayoutEditorSession.isOpen) {
        frozenShellPrefersNavigationRail.value
    } else {
        shellPrefersNavigationRail
    }
    val bottomNavStyle = BottomNavStyle.fromId(overlayUiSettings.bottomNavStyleId)
    val currentKey = activeBackStack.currentAppNavKey() ?: currentTab.toRootNavKey()
    val isRootDestination = currentKey.isRootDestination()
    val showSideNavRail = prefersNavigationRail
    val showClassicSideNavRail = showSideNavRail && bottomNavStyle == BottomNavStyle.CLASSIC
    val showMiuixSideNavRail = showSideNavRail &&
        (bottomNavStyle == BottomNavStyle.LIQUID_GLASS || bottomNavStyle == BottomNavStyle.FLOATING_NAV)
    val useLiquidGlassBottomNav = bottomNavStyle == BottomNavStyle.LIQUID_GLASS && !showSideNavRail
    val useFloatingNavBottomNav = bottomNavStyle == BottomNavStyle.FLOATING_NAV && !showSideNavRail
    val usePagerBottomNav = useLiquidGlassBottomNav || useFloatingNavBottomNav
    val effectiveBottomNavStyle = when {
        useLiquidGlassBottomNav -> BottomNavStyle.LIQUID_GLASS
        useFloatingNavBottomNav -> BottomNavStyle.FLOATING_NAV
        else -> BottomNavStyle.CLASSIC
    }
    val showBottomNavLabels = BottomNavMode.fromId(
        overlayUiSettings.bottomNavModeId,
    ).showLabels
    val rootBottomContentPadding = mainAppRootBottomContentPadding(
        prefersNavigationRail = showSideNavRail,
        isRootDestination = isRootDestination,
        bottomNavStyle = effectiveBottomNavStyle,
        showBottomNavLabels = showBottomNavLabels,
    )

    SideEffect {
        when (initialNavRoute) {
            MainActivity.NAV_ROUTE_EXTENSION_FREEZER -> {
                val extensionTab = MainBottomNavDestination.Extension.name
                if (savedBottomNavTab != extensionTab) {
                    savedBottomNavTab = extensionTab
                }
                if (extensionBackStack.lastOrNull() != AppNavKey.ExtensionFreezer) {
                    extensionBackStack.clear()
                    extensionBackStack.add(AppNavKey.ExtensionHub)
                    extensionBackStack.add(AppNavKey.ExtensionFreezer)
                }
                onNavRouteConsumed()
            }
            MainActivity.NAV_ROUTE_EXTENSION_FREEZER_APPS -> {
                val extensionTab = MainBottomNavDestination.Extension.name
                if (savedBottomNavTab != extensionTab) {
                    savedBottomNavTab = extensionTab
                }
                if (extensionBackStack.lastOrNull() != AppNavKey.ExtensionFreezerApps) {
                    extensionBackStack.clear()
                    extensionBackStack.add(AppNavKey.ExtensionHub)
                    extensionBackStack.add(AppNavKey.ExtensionFreezer)
                    extensionBackStack.add(AppNavKey.ExtensionFreezerApps)
                }
                onNavRouteConsumed()
            }
        }
    }

    LaunchedEffect(initialIntentAction, initialNavRoute) {
        if (initialIntentAction == MainActivity.ACTION_OPEN_NOTIFICATION_HISTORY) {
            savedBottomNavTab = MainBottomNavDestination.Notification.name
            if (notificationBackStack.lastOrNull() != AppNavKey.NotificationHistory) {
                notificationBackStack.clear()
                notificationBackStack.add(AppNavKey.NotificationHub)
                notificationBackStack.add(AppNavKey.NotificationHistory)
            }
        }
    }

    LaunchedEffect(rootSettings.hideFromRecents) {
        activity.applyHideFromRecents(rootSettings.hideFromRecents)
    }

    // §0.16.26：这里**不再按设置写 flag**，而且这个 flag 现在只有**设置页开关**那一处会写
    // （恒定 `true` + 重建）—— 冷启动一个字都不写，只靠 manifest 的
    // `android:enableOnBackInvokedCallback="true"`，这是通用做法（单一事实来源、没有时序竞争）。
    // 曾经按用户设置写，而默认值就是 false：那会让系统退回"兼容注入 KEYCODE_BACK"，那条注入路
    // 在魅族 21 / Android 16 上到不了 Activity 的返回分发器 → 边缘手势返回彻底失效。
    //
    // §0.16.26 残留（真机日志）：每次成功返回之后，navigationevent 会把注册在窗口上的**动画回调注销**，
    // 窗口于是退到 ViewRootImpl 的 compat 兜底（`mPriority=-1, mIsAnimationCallback=false`）—— 而那个
    // 兜底在这台 ROM 上是坏的，"空档期"内的手势既不跟手也不返回。注销的判据是 `hasEnabledHandlers`：
    // 本仓库的返回 handler 全是按页面条件挂的，根页时一个都不在 ⇒ 空档。
    //
    // 这里挂一个**永远 enabled 的兜底 handler** 占住那个判据（默认 priority = PRIORITY_OVERLAY = 0，
    // 系统的"退出 App"兜底是 PRIORITY_DEFAULT = 1，所以只要本 handler 在，系统兜底不会被直接调）。
    // ⚠️ 因此它**必须自己复刻根页的退出行为**，否则"主页按返回退不出 App"就是回归：
    //   - 当前是根页（栈深 1）→ `finish()`，与系统原本的兜底等价；
    //   - 子页 → 什么都不做，交给该页真正的返回 handler（它们同级但先注册，优先接）。
    val rootBackFallbackState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = rootBackFallbackState,
        isBackEnabled = true,
        onBackCompleted = {
            if (isRootDestination) activity.finish()
        },
    )

    val permissions = permissionStates.collect()

    LaunchedEffect(currentKey, permissions.accessibilityGranted, floatingPointerAreaPreviewEnabled) {
        if (currentKey == AppNavKey.FloatingPointer &&
            permissions.accessibilityGranted &&
            floatingPointerAreaPreviewEnabled
        ) {
            // 预览窗口由无障碍服务所在的进程创建：跟手位置来自边缘触摸事件。
            activity.overlayServiceController.setFloatingPointerAreaPreview(true)
        } else {
            activity.overlayServiceController.setFloatingPointerAreaPreview(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose { activity.overlayServiceController.setFloatingPointerAreaPreview(false) }
    }

    val coroutineScope = rememberCoroutineScope()
    if (!rootSettings.onboardingCompleted) {
        CompositionLocalProvider(LocalAppDependencies provides deps) {
            ModuleTheme(settings = overlayUiSettings.toModuleThemeSettings()) {
                OnboardingScreen(
                    permissions = permissions,
                    settingsRepository = deps.settingsRepository,
                    // 引导里把"真实触钮"亮到屏幕上：只写文字，用户不知道触钮在屏幕哪一段。
                    onPreviewTriggerBar = { active ->
                        if (active) {
                            activity.overlayServiceController.startLayoutPreview(
                                com.slideindex.app.overlay.LayoutPreviewContent.TRIGGER_ONLY,
                            )
                        } else {
                            activity.overlayServiceController.stopLayoutPreview()
                        }
                    },
                    onRequestOverlay = {
                        activity.startActivity(PermissionHelper.overlaySettingsIntent(activity))
                    },
                    onRequestAccessibility = {
                        activity.startActivity(PermissionHelper.accessibilitySettingsIntent())
                    },
                    onRequestNotification = {
                        activity.requestNotificationPermission()
                    },
                    onComplete = {
                        coroutineScope.launch {
                            deps.settingsRepository.setOnboardingCompleted(true)
                            deps.applicationScope.launch(Dispatchers.IO) {
                                deps.appRepository.loadApps()
                                com.slideindex.app.widget.WidgetCatalog.preload(activity.applicationContext)
                            }
                        }
                    },
                )
            }
        }
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val updateViewModel: UpdateViewModel = hiltViewModel(activity)
    val snackbarBottomPadding = when {
        showSideNavRail -> MainBottomNavOuterPadding
        isRootDestination && useLiquidGlassBottomNav ->
            rootBottomContentPadding + MainMiuixBottomNavOuterPadding
        isRootDestination && useFloatingNavBottomNav ->
            rootBottomContentPadding + MainFloatingNavBarBottomOffset
        isRootDestination -> rootBottomContentPadding + MainBottomNavOuterPadding
        else -> 16.dp
    }
    CompositionLocalProvider(LocalAppDependencies provides deps) {
        ModuleTheme(settings = overlayUiSettings.toModuleThemeSettings()) {
            val hazeState = remember { HazeState() }
            val bottomNavUsesHaze = overlayUiSettings.usesBottomNavHaze()
            var bottomNavBlurPreviewRadiusDp by remember { mutableStateOf<Float?>(null) }
            val bottomNavBlurRadiusDp = bottomNavBlurPreviewRadiusDp
                ?: overlayUiSettings.bottomNavBlurRadiusDp
            val onBottomNavBlurPreviewChange: (Float) -> Unit = { value ->
                bottomNavBlurPreviewRadiusDp = value
            }
            val onBottomNavBlurPreviewStop: () -> Unit = {
                bottomNavBlurPreviewRadiusDp = null
            }
            LaunchedEffect(Unit) {
                awaitFrame()
                MainBottomNavDestination.entries.forEach { destination ->
                    if (destination !in visitedTabs) {
                        visitedTabs.add(destination)
                        awaitFrame()
                    }
                }
            }
            LaunchedEffect(deferredTabSelection) {
                val tab = deferredTabSelection ?: return@LaunchedEffect
                awaitFrame()
                if (deferredTabSelection == tab) {
                    savedBottomNavTab = tab.name
                    deferredTabSelection = null
                }
            }
            val onTabSelected: (MainBottomNavDestination) -> Unit = { tab ->
                if (tab == bottomNavSelectedTab) {
                    bottomNavReselectCounts = bottomNavReselectCounts +
                        (tab to bottomNavReselectCounts.getValue(tab) + 1)
                } else {
                    val firstComposition = tab !in visitedTabs
                    visitedTabs.add(tab)
                    if (firstComposition) {
                        deferredTabSelection = tab
                    } else {
                        deferredTabSelection = null
                        savedBottomNavTab = tab.name
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                val navContentModifier = Modifier.fillMaxSize()
                var lastPagerStyle by remember { mutableStateOf(BottomNavStyle.LIQUID_GLASS) }
                val activePagerStyle = if (useFloatingNavBottomNav) {
                    BottomNavStyle.FLOATING_NAV
                } else {
                    BottomNavStyle.LIQUID_GLASS
                }
                if (usePagerBottomNav) {
                    lastPagerStyle = activePagerStyle
                }
                val pagerStyle = if (usePagerBottomNav) activePagerStyle else lastPagerStyle
                val saveableStateHolder = rememberSaveableStateHolder()
                val mainTabNavContent: @Composable () -> Unit = {
                    Crossfade(
                        targetState = usePagerBottomNav,
                        animationSpec = tween(durationMillis = MainBottomNavHostFadeDurationMs),
                        label = "bottomNavHostFade",
                    ) { usePager ->
                        if (usePager) {
                            saveableStateHolder.SaveableStateProvider("pager") {
                                MainTabPagerHost(
                                    bottomNavStyle = pagerStyle,
                                    currentTab = currentTab,
                                    visitedTabs = visitedTabs,
                                    backStacks = backStacks,
                                    activity = activity,
                                    deps = deps,
                                    permissionStates = permissionStates,
                                    swipeDismissEnabled = homeMainSettings.swipeDismissEnabled,
                                    floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                                    rootBottomContentPadding = rootBottomContentPadding,
                                    bottomNavReselectCounts = bottomNavReselectCounts,
                                    hazeState = hazeState,
                                    bottomNavUsesHaze = bottomNavUsesHaze,
                                    bottomNavBlurRadiusDp = bottomNavBlurRadiusDp,
                                    showBottomNavLabels = showBottomNavLabels,
                                    isRootDestination = isRootDestination,
                                    onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
                                    onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
                                    onTabCommitted = { tab ->
                                        deferredTabSelection = null
                                        savedBottomNavTab = tab.name
                                    },
                                    onTabReselected = { tab ->
                                        bottomNavReselectCounts = bottomNavReselectCounts +
                                            (tab to bottomNavReselectCounts.getValue(tab) + 1)
                                    },
                                )
                            }
                        } else {
                            saveableStateHolder.SaveableStateProvider("stacks") {
                                MainTabNavStacks(
                                    currentTab = currentTab,
                                    visitedTabs = visitedTabs,
                                    backStacks = backStacks,
                                    activity = activity,
                                    deps = deps,
                                    permissionStates = permissionStates,
                                    swipeDismissEnabled = homeMainSettings.swipeDismissEnabled,
                                    floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                                    rootBottomContentPadding = rootBottomContentPadding,
                                    bottomNavReselectCounts = bottomNavReselectCounts,
                                    hazeState = hazeState,
                                    bottomNavUsesHaze = bottomNavUsesHaze,
                                    onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
                                    onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
                                )
                            }
                        }
                    }
                }
                if (showSideNavRail) {
                    when {
                        showClassicSideNavRail -> {
                            val classicRailInset = classicFloatingSideNavRailSlotWidth(
                                showLabels = showBottomNavLabels,
                            )
                            Box(modifier = Modifier.fillMaxSize()) {
                                CompositionLocalProvider(
                                    LocalMainNavContentStartInset provides classicRailInset,
                                ) {
                                    Box(
                                        modifier = navContentModifier
                                            .fillMaxSize()
                                            .mainNavClassicOverlayContentInsets()
                                            .mainNavRailContentClip(),
                                    ) {
                                         mainTabNavContent()
                                    }
                                }
                                ClassicFloatingSideNavRailOverlay(
                                    hazeState = hazeState,
                                    glassEnabled = bottomNavUsesHaze,
                                    selected = bottomNavSelectedTab,
                                    blurRadiusDp = bottomNavBlurRadiusDp,
                                    showLabels = showBottomNavLabels,
                                    onDestinationSelected = onTabSelected,
                                    modifier = Modifier.align(Alignment.CenterStart),
                                )
                            }
                        }
                        showMiuixSideNavRail -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                MainMiuixNavigationRail(
                                    selected = bottomNavSelectedTab,
                                    onDestinationSelected = onTabSelected,
                                    modifier = Modifier.fillMaxHeight(),
                                )
                                Box(
                                    modifier = navContentModifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .mainNavMiuixRailContentInsets()
                                        .mainNavRailContentClip(),
                                ) {
                                    CompositionLocalProvider(
                                        LocalMainNavContentStartInset provides 0.dp,
                                    ) {
                                         mainTabNavContent()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = navContentModifier) {
                         mainTabNavContent()
                    }
                    if (isRootDestination && !usePagerBottomNav) {
                        FloatingBottomNavBar(
                            hazeState = hazeState,
                            glassEnabled = bottomNavUsesHaze,
                            selected = bottomNavSelectedTab,
                            blurRadiusDp = bottomNavBlurRadiusDp,
                            showLabels = showBottomNavLabels,
                            onDestinationSelected = onTabSelected,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = MainBottomNavHorizontalPadding)
                                .padding(bottom = MainBottomNavOuterPadding),
                        )
                    }
                }
                UserMessageSnackbarHost(
                    userMessageBus = deps.userMessageBus,
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = snackbarBottomPadding),
                )
                val globalNavContext = remember(
                    activeBackStack,
                    rootBottomContentPadding,
                    bottomNavReselectCounts[currentTab],
                ) {
                    MainNavContext(
                        activity = activity,
                        deps = deps,
                        backStack = activeBackStack,
                        permissionStates = permissionStates,
                        floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                        rootBottomContentPadding = rootBottomContentPadding,
                        bottomNavReselectCount = bottomNavReselectCounts[currentTab] ?: 0,
                    )
                }
                UpdateHost(
                    viewModel = updateViewModel,
                    entryIntentAction = initialIntentAction,
                    showUpdateFromIntent = showUpdateFromIntent,
                    onShowUpdateConsumed = onShowUpdateConsumed,
                )
                OcrEnginePackMigrationHost(
                    onOpenNativeEnginePacks = {
                        savedBottomNavTab = MainBottomNavDestination.Extension.name
                        visitedTabs.add(MainBottomNavDestination.Extension)
                        extensionBackStack.clear()
                        extensionBackStack.add(AppNavKey.ExtensionHub)
                        extensionBackStack.add(AppNavKey.NativeEnginePacks)
                    },
                )
            }
        }
    }
}

@Composable
private fun MainTabNavStacks(
    currentTab: MainBottomNavDestination,
    visitedTabs: SnapshotStateSet<MainBottomNavDestination>,
    backStacks: Map<MainBottomNavDestination, NavBackStack>,
    activity: MainActivity,
    deps: AppDependencies,
    permissionStates: NavPermissionStates,
    swipeDismissEnabled: Boolean,
    floatingPointerAreaPreviewEnabledState: MutableState<Boolean>,
    rootBottomContentPadding: Dp,
    bottomNavReselectCounts: Map<MainBottomNavDestination, Int>,
    hazeState: HazeState,
    bottomNavUsesHaze: Boolean,
    onBottomNavBlurPreviewChange: (Float) -> Unit,
    onBottomNavBlurPreviewStop: () -> Unit,
) {
    val slideDistancePx = with(LocalDensity.current) { MainTabSwitchSlideOffset.toPx() }
    val currentTabIndex = currentTab.ordinal
    val tabSwitchAxisVertical = mainAppPrefersNavigationRail()
    val tabSwitchAnimationSpec = remember(tabSwitchAxisVertical) {
        val durationMs = if (tabSwitchAxisVertical) {
            MAIN_TAB_SWITCH_DURATION_RAIL_MS
        } else {
            MAIN_TAB_SWITCH_DURATION_BOTTOM_BAR_MS
        }
        tween<Float>(durationMillis = durationMs, easing = FastOutSlowInEasing)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .then(
                if (bottomNavUsesHaze) {
                    Modifier.hazeSource(state = hazeState)
                } else {
                    Modifier
                },
            ),
    ) {
        MainBottomNavDestination.entries.forEach { destination ->
            if (destination !in visitedTabs) return@forEach
            val stack = backStacks[destination]!!
            val isActive = destination == currentTab
            val targetAlpha = if (isActive) 1f else 0f
            val targetTranslationX = if (tabSwitchAxisVertical || isActive) {
                0f
            } else {
                when {
                    destination.ordinal < currentTabIndex -> -slideDistancePx
                    destination.ordinal > currentTabIndex -> slideDistancePx
                    else -> 0f
                }
            }
            val targetTranslationY = if (!tabSwitchAxisVertical || isActive) {
                0f
            } else {
                when {
                    destination.ordinal < currentTabIndex -> -slideDistancePx
                    destination.ordinal > currentTabIndex -> slideDistancePx
                    else -> 0f
                }
            }
            val targetScale = if (isActive) 1f else MainTabSwitchInactiveScale
            val tabAlpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tabSwitchAnimationSpec,
                label = "mainTabAlpha-${destination.name}",
            )
            val tabTranslationX by animateFloatAsState(
                targetValue = targetTranslationX,
                animationSpec = tabSwitchAnimationSpec,
                label = "mainTabTranslationX-${destination.name}",
            )
            val tabTranslationY by animateFloatAsState(
                targetValue = targetTranslationY,
                animationSpec = tabSwitchAnimationSpec,
                label = "mainTabTranslationY-${destination.name}",
            )
            val tabScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = tabSwitchAnimationSpec,
                label = "mainTabScale-${destination.name}",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (isActive) 1f else 0f)
                    .graphicsLayer {
                        alpha = tabAlpha
                        translationX = tabTranslationX
                        translationY = tabTranslationY
                        scaleX = tabScale
                        scaleY = tabScale
                    },
            ) {
                MainTabNavStackSingle(
                    destination = destination,
                    backStack = stack,
                    activity = activity,
                    deps = deps,
                    permissionStates = permissionStates,
                    swipeDismissEnabled = swipeDismissEnabled,
                    floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
                    rootBottomContentPadding = rootBottomContentPadding,
                    bottomNavReselectCount = bottomNavReselectCounts[destination] ?: 0,
                    hazeState = null,
                    bottomNavUsesHaze = bottomNavUsesHaze,
                    isActiveForHaze = isActive,
                    onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
                    onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
                )
            }
        }
    }
}

@Composable
internal fun MainTabNavStackSingle(
    destination: MainBottomNavDestination,
    backStack: NavBackStack,
    activity: MainActivity,
    deps: AppDependencies,
    permissionStates: NavPermissionStates,
    swipeDismissEnabled: Boolean,
    floatingPointerAreaPreviewEnabledState: MutableState<Boolean>,
    rootBottomContentPadding: Dp,
    bottomNavReselectCount: Int,
    hazeState: HazeState?,
    bottomNavUsesHaze: Boolean,
    isActiveForHaze: Boolean = true,
    onBottomNavBlurPreviewChange: (Float) -> Unit,
    onBottomNavBlurPreviewStop: () -> Unit,
) {
    val tabNavContext = remember(
        destination,
        backStack,
        rootBottomContentPadding,
        bottomNavReselectCount,
    ) {
        MainNavContext(
            activity = activity,
            deps = deps,
            backStack = backStack,
            permissionStates = permissionStates,
            floatingPointerAreaPreviewEnabledState = floatingPointerAreaPreviewEnabledState,
            rootBottomContentPadding = rootBottomContentPadding,
            bottomNavReselectCount = bottomNavReselectCount,
            onBottomNavBlurPreviewChange = onBottomNavBlurPreviewChange,
            onBottomNavBlurPreviewStop = onBottomNavBlurPreviewStop,
        )
    }
    val swipeBackDirection = when (LocalLayoutDirection.current) {
        LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
        else -> NavSwipeDirection.LeftToRight
    }
    val swipeDismiss = if (swipeDismissEnabled) swipeBackDirection else null
    val registerEntries = remember(destination, tabNavContext, swipeDismiss) {
        mainTabNavEntryProvider(swipeDismiss, destination, tabNavContext)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .mainNavRailContentClip()
            .then(
                if (hazeState != null && bottomNavUsesHaze && isActiveForHaze) {
                    Modifier.hazeSource(state = hazeState)
                } else {
                    Modifier
                },
            ),
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.pop() },
            effects = NavDisplayEffects(
                cornerClipRadius = rememberNavSystemCornerRadius(),
            ),
            content = registerEntries,
        )
    }
}

private fun mainTabNavEntryProvider(
    swipeDismiss: NavSwipeDirection?,
    destination: MainBottomNavDestination,
    tabNavContext: MainNavContext,
): NavEntryBuilder.() -> Unit = {
    NavEntrySwipeDismissScope.current = swipeDismiss
    registerMainTabNavEntries(destination, tabNavContext)
}

private fun NavEntryBuilder.registerMainTabNavEntries(
    destination: MainBottomNavDestination,
    ctx: MainNavContext,
) {
    when (destination) {
        MainBottomNavDestination.Home -> {
            homeNavEntries(ctx)
            floatBallNavEntries(ctx)
            // FooView「自定义手势」列表页（入口在 悬浮球 → 手势 页里）
            fvCustomGestureNavEntries(ctx)
            searchPanelNavEntries(ctx)
            floatingPointerNavEntries(ctx)
        }
        MainBottomNavDestination.Shake -> shakeNavEntries(ctx)
        MainBottomNavDestination.Notification -> notificationNavEntries(ctx)
        MainBottomNavDestination.Extension -> {
            extensionNavEntries(ctx)
            macroNavEntries(ctx)
            pageScrollNavEntries(ctx)
        }
    }
}