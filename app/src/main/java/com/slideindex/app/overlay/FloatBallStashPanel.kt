package com.slideindex.app.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.history.HistoryPanelReveal
import com.slideindex.app.overlay.history.HistoryPanelScreen
import com.slideindex.app.overlay.history.StashPanelLaunchState
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme

enum class StashPanelInitialTab {
    Stash,
    Clipboard,
}

/**
 * Stash / clipboard side panel. Window lifecycle is handled by [OverlaySidePanelHost].
 */
object FloatBallStashPanel {
    private val sideHost = OverlaySidePanelHost(TAG)

    private var pendingInitialTab: HistoryFloatingTab = HistoryFloatingTab.Stash
    private val requestedTabOrdinal = mutableIntStateOf(HistoryFloatingTab.Stash.ordinal)
    /** 与 [StashPanelLaunchState.epoch] 同步，供 Compose 订阅。 */
    private val searchBootstrapEpoch = mutableIntStateOf(0)

    val isShowing: Boolean get() = sideHost.isShowing

    /**
     * Attaches the panel window below float-ball chrome so opening it later avoids z-order bumps.
     */
    fun warmUpBelowChrome(context: android.content.Context) {
        if (sideHost.isAttached) return
        val attached = sideHost.attachHidden(
            context = context,
            initialGravityEnd = true,
            content = ::PanelContent,
            dragReveal = { HistoryPanelReveal.dragSession },
            // 窗口跟手：进度给宿主，由它换算成窗口左边缘坐标（窗口只有 78% 宽，见 §0.16.3）。
            revealProgress = { HistoryPanelReveal.windowProgress }
        )
        if (attached) registerExternalUiHooks()
    }

    fun show(
        context: android.content.Context,
        initialTab: StashPanelInitialTab = StashPanelInitialTab.Stash,
        panelSide: PanelSide? = null,
        searchQuery: String? = null
    ): Boolean {
        // 这是"点击/深链打开"这条路：清掉拖动会话，入场交给宿主的滑入动画。
        HistoryPanelReveal.dragSession = false
        HistoryPanelReveal.dragging = false
        HistoryPanelReveal.retractPending = false
        HistoryPanelReveal.open = true
        pendingInitialTab = initialTab.toHistoryFloatingTab()
        requestedTabOrdinal.intValue = pendingInitialTab.ordinal
        val q = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        // 先 show（把 targetVisible=true），再写入 pending/epoch，
        // 避免退出动画中的旧组合在 visible=false 时先抢 consume。
        val shown = sideHost.show(
            context = context,
            initialGravityEnd = panelSide.toStashPanelGravityEnd(),
            content = ::PanelContent,
            dragReveal = { HistoryPanelReveal.dragSession },
            // 窗口跟手：进度给宿主，由它换算成窗口左边缘坐标（窗口只有 78% 宽，见 §0.16.3）。
            revealProgress = { HistoryPanelReveal.windowProgress }
        )
        if (shown && q != null) {
            StashPanelLaunchState.setPendingSearch(
                tabOrdinal = pendingInitialTab.ordinal,
                query = q
            )
            searchBootstrapEpoch.intValue = StashPanelLaunchState.epoch
        }
        if (shown) {
            // 面板要开了 = 它必须看得见、点得着：顺手把"拉起相册前挂起"那件事复位（§0.16.14）。
            // 正常路径上相册回调已经恢复过，这里只是兜底 —— 万一回调丢了（进程被杀等），
            // 不会留下"面板开着但 FLAG_NOT_TOUCHABLE 还挂着、点不动"的死状态。
            sideHost.resumeAfterExternalUi()
            registerExternalUiHooks()
        }
        return shown
    }

    /**
     * 跟手拉出：手指在把手上横向拖过阈值时调用（把手侧 `HistoryFloatContent`）。
     *
     * **先把共享状态置上再 show** —— 面板要从屏幕外开始跟手，不能先自己滑进来。
     * 拖动期间面板窗切到"看得见但不吃触摸"，否则它（MATCH_PARENT）会把后续 MOVE 从把手手里抢走。
     */
    fun beginDragReveal(
        context: android.content.Context,
        initialTab: StashPanelInitialTab = StashPanelInitialTab.Stash,
    ): Boolean {
        if (sideHost.isShowing) return false
        HistoryPanelReveal.dragSession = true
        HistoryPanelReveal.dragging = true
        HistoryPanelReveal.dragProgress = 0f
        // 窗口位置由 `windowProgress` 驱动；先显式置 0，保证窗口**第一帧就在屏外**
        // （面板的组合还没跑，值可能还是上一次的 1 ✗）。
        HistoryPanelReveal.windowProgress = 0f
        HistoryPanelReveal.retractPending = false
        HistoryPanelReveal.open = true
        // 「拉出来先落在哪一页」与「点击指示条」共用同一份解析（见 `HistoryFloatService`）：
        // 不写的话拉出会沿用上一次的 ordinal，"设置里选闪念、拖出来却是剪贴板"。
        pendingInitialTab = initialTab.toHistoryFloatingTab()
        requestedTabOrdinal.intValue = pendingInitialTab.ordinal
        val shown = sideHost.show(
            context = context,
            initialGravityEnd = true,
            content = ::PanelContent,
            dragReveal = { HistoryPanelReveal.dragSession },
            // 窗口跟手：进度给宿主，由它换算成窗口左边缘坐标（窗口只有 78% 宽，见 §0.16.3）。
            revealProgress = { HistoryPanelReveal.windowProgress }
        )
        if (shown) sideHost.setTouchable(false)
        if (shown) registerExternalUiHooks()
        return shown
    }

    /**
     * 松手：
     * - [commit] = 过半 → 面板弹簧归位（进度 1），窗保持可触摸；
     * - 否则 → 弹簧收回（进度 0），**收回动画播完**再由面板侧叫宿主关窗
     *   （立刻关会看到面板"啪"地消失，而不是滑回去）。
     */
    fun endDragReveal(commit: Boolean) {
        HistoryPanelReveal.dragging = false
        sideHost.setTouchable(true)
        if (commit) {
            HistoryPanelReveal.open = true
        } else {
            HistoryPanelReveal.open = false
            HistoryPanelReveal.retractPending = true
        }
    }

    fun dismiss() {
        HistoryPanelReveal.open = false
        HistoryPanelReveal.dragging = false
        HistoryPanelReveal.retractPending = false
        sideHost.dismiss()
    }

    /** 面板收回动画播完（面板侧回调）：这时才真正关窗。 */
    fun dismissAfterRetract() {
        sideHost.dismiss()
    }

    /**
     * 动作再触发一次时的「开关」语义：同一个 Tab 再触发就收起，换了 Tab 就切页。
     *
     * 用户反馈过「触发了动作却收不起来」——原来的实现只调 [show]，面板已显示时等于什么都不做。
     * 这里刻意**不**用 [requestBackIfShowing]：那会走完整返回层级，面板里弹着键盘或展开着卡片时
     * 只吃掉一层，用户的「关掉它」意图没达成。
     *
     * @return true = 本次触发生效（收起或打开）。
     */
    fun toggle(
        context: android.content.Context,
        initialTab: StashPanelInitialTab = StashPanelInitialTab.Stash,
        panelSide: PanelSide? = null,
    ): Boolean {
        val launching = initialTab == StashPanelInitialTab.Stash
        if (isShowing && requestedTabOrdinal.intValue == initialTab.toHistoryFloatingTab().ordinal) {
            dismiss()
            return true
        }
        return show(context, initialTab = initialTab, panelSide = panelSide)
    }

    /**
     * §0.16.24：**手势里的"返回"动作访问本面板的唯一入口**
     * （`SlideIndexAccessibilityGestureInjector` 的 `GestureAction.Back` 分支调它）。
     *
     * 为什么不让调用方直接 [dismiss]：那样会**绕过整个返回层级**（真机回归：面板里弹着键盘、
     * 或卡片展开着时，手势返回直接把整个面板关了）。这次返回一律交给面板自己的返回链：
     * ```
     * 本方法 → OverlaySidePanelHost.requestBackIfShowing()
     *   → OverlayViewBackHandler.dispatchBack()   ← 第 0 档「键盘优先」在这里（唯一一份）
     *     → OverlaySidePanelHost.handlePanelBack() ← 第 7/8 档（输入态 / 收面板）
     *       → panelBackInterceptor                   ← 第 1..6 档，注册表见 HistoryPanelScreen 的 KDoc
     * ```
     * ⚠️ **系统返回与手势返回共用这一份层级判定**：调用侧不要再抄 IME 判定、不要再判子层、
     * 更不要自己 `dismiss()`；以后面板里新增覆盖层，去 `HistoryPanelScreen` 的注册表登记。
     *
     * @return true = 这次返回已被面板消费（调用方不要再落 `GLOBAL_ACTION_BACK`）；面板没显示 → false。
     */
    fun requestBackIfShowing(): Boolean = sideHost.requestBackIfShowing()

    fun destroy() {
        sideHost.destroy()
        pendingInitialTab = HistoryFloatingTab.Stash
        requestedTabOrdinal.intValue = HistoryFloatingTab.Stash.ordinal
        StashPanelLaunchState.clearPendingSearch()
        searchBootstrapEpoch.intValue = 0
        sideHost.setPanelBackInterceptor(null)
        // 窗没了，挂起/恢复外部 UI 的动作跟着作废（§0.16.14）。
        StashPanelExternalUi.clear()
    }

    /** 应用内语言切换后销毁预热壳，下次打开收纳面板时用新 Locale 重建。 */
    fun releaseWarmUpForLocale() {
        destroy()
    }

    fun updateWindowInputActiveForClipboard(active: Boolean) {
        sideHost.setClipboardInputActive(active)
    }

    fun setDragHidden(hidden: Boolean) {
        sideHost.setDragHidden(hidden)
    }

    /**
     * 把"拉起外部系统 UI（相册）时挂起面板"的两个动作注册给 overlay 里的 Compose 代码
     * （§0.16.14）。
     *
     * 只有**窗口真的挂上之后**才注册：面板窗被系统摘掉时 [OverlaySidePanelHost] 会把它清掉，
     * 免得留下指向"已经没有窗口的宿主"的动作。
     */
    private fun registerExternalUiHooks() {
        StashPanelExternalUi.suspend = { sideHost.suspendForExternalUi() }
        StashPanelExternalUi.resume = { sideHost.resumeAfterExternalUi() }
    }

    @Composable
    private fun PanelContent(
        gravityEnd: Boolean,
        panelTargetVisible: Boolean,
        onToggleSide: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val context = LocalContext.current
        var settings by remember { mutableStateOf(AppSettings()) }
        val settingsFlow = remember(context) {
            OverlayDependencyAccess.overlayDependencies(context)?.settingsRepository?.settings
        }
        LaunchedEffect(settingsFlow) {
            settingsFlow?.collect { settings = it }
        }
        val panelBlurActive = settings.stashPanelBackgroundBlurEnabled
        val blurRadiusDp = settings.stashPanelBackgroundBlurRadiusDp

        // 系统级背景模糊（API 31+，`FLAG_BLUR_BEHIND` + `setBackgroundBlurRadius`）：
        // 合成器**每帧**重算，所以窗口跟手移动时也是实时模糊 —— 这正是面板内那层自绘
        // 系统级背景模糊（§0.16.3）已按用户要求整批回退：面板内继续用 App 自绘的磨砂罩
        // （`HistoryPanelScreen` 里的 `LocalFrostedGlassBackdrop`），窗口也不再压窄/移动。

        OverlayAwareModuleTheme {
            HistoryPanelScreen(
                gravityEnd = gravityEnd,
                panelTargetVisible = panelTargetVisible,
                panelBlurActive = panelBlurActive,
                blurRadiusDp = blurRadiusDp,
                onDismiss = onDismiss,
                onToggleSide = onToggleSide,
                requestedTabOrdinal = requestedTabOrdinal,
                searchBootstrapEpoch = searchBootstrapEpoch,
                onSearchFocusChanged = { active ->
                    updateWindowInputActiveForClipboard(active)
                },
                onRegisterBackInterceptor = { interceptor ->
                    sideHost.setPanelBackInterceptor(interceptor)
                }
            )
            DisposableEffect(Unit) {
                onDispose {
                    sideHost.setPanelBackInterceptor(null)
                }
            }
        }
    }

    private fun PanelSide?.toStashPanelGravityEnd(): Boolean = when (this) {
        PanelSide.LEFT -> false
        PanelSide.RIGHT -> true
        PanelSide.BOTTOM, PanelSide.TOP, null -> true
    }

    private fun StashPanelInitialTab.toHistoryFloatingTab(): HistoryFloatingTab = when (this) {
        StashPanelInitialTab.Stash -> HistoryFloatingTab.Stash
        StashPanelInitialTab.Clipboard -> HistoryFloatingTab.Clipboard
    }

    private enum class HistoryFloatingTab {
        Stash,
        Clipboard,
    }

    private const val TAG = "FloatBallStashPanel"
}
