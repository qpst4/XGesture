package com.slideindex.app.overlay

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Side-sliding overlay panel host (stash / clipboard history). Wraps [OverlayFullScreenPanelHost]
 * with horizontal enter/exit animation and optional left/right gravity.
 */
class OverlaySidePanelHost(
    private val tag: String = "OverlaySidePanelHost"
) : OverlayPanelVisibility {
    private val panelHost = OverlayFullScreenPanelHost(
        tag = tag,
        layoutParamsFactory = { context, focusable ->
            OverlayPanelLayoutParams.stashClipboardSidePanel(context, focusable)
        },
        onScreenOff = { dismiss() },
        excludeLeftBackEdge = false,
        // §0.16.13：系统把面板窗摘掉（切前台 App、拉起相册等）后必须复位，否则"面板再也打不开"。
        onViewDetached = { onPanelViewDetached() },
    )

    private var panelVisibilityState: MutableTransitionState<Boolean>? = null
    private var panelTargetVisibleState: MutableState<Boolean>? = null
    private var gravityEndState: MutableState<Boolean>? = null
    /**
     * 本次打开是否由「跟手拖动」发起（调用方给的 lambda；host 本身不认识"拖动"这件事）。
     *
     * ⚠️ 它曾经用来决定宿主那层 `AnimatedVisibility` 的 enter/exit 走不走滑入滑出 —— 现在**不参与动画**：
     * 宿主已改成"内容常驻组合、可见性与位移全由面板侧按进度自己画"（见 [attachPanelWindow]），
     * 拖动与点击两条路都不该由宿主再叠一层动画（叠了就是面板被顶在屏外、要滑一段距离才跟手）。
     * 参数与字段先留着，是为了不动 `show` / `attachHidden` 的调用面；后续清理可与同样已成死参数的
     * `revealProgress` 一起删。
     */
    private var dragRevealQuery: () -> Boolean = { false }
    /**
     * 跟手拖出的进度（0 = 完全收起在屏外，1 = 完全拉出）。
     *
     * ⚠️ §0.16.3 曾用它驱动**窗口本身**的 x（`applyPanelX` → `setRevealOffsetPx`），
     * **已被用户打回**：现在窗口满屏且不动，跟手位移由面板内容自己的 `graphicsLayer` 做。
     * 这个查询参数先留着（`show` 的默认值是 `{ 0f }`，没人传就无所谓）。
     */
    private var revealProgressQuery: () -> Float = { 0f }
    /** 算窗口宽度/位置要用（show/attach 时给的宿主 context）。 */
    private var layoutContext: Context? = null
    /** 一次性日志用：拖动路径是否已经离开过最终位置。 */
    private var loggedRevealMove = false
    private var attachedBelowChrome = false
    private var lastShowAttemptElapsedMs = 0L
    private var clipboardInputActive = false
    private var panelBackInterceptor: (() -> Boolean)? = null
    private var backHandler: OverlayViewBackHandler? = null

    override val isAttached: Boolean get() = panelHost.isAttached

    override val isUserVisible: Boolean
        get() = panelHost.isAttached &&
            hasLivePanelWindow &&
            // ⚠️ 读 `targetState` 而不是 `currentState`：内容现在**常驻组合**、宿主不再包
            // `AnimatedVisibility`，`currentState` 没有任何东西去推动（永远是初值 false），
            // 再读它会让 `isShowing` / `requestBackIfShowing` 永久为 false。
            // 语义差别只在两个动画窗口里：打开时立刻算可见（原来是入场动画播完才算）、
            // 收起时立刻算不可见（原来是退场动画播完才算）—— 两者都比原来更贴合"用户此刻能不能操作它"。
            panelVisibilityState?.targetState == true &&
            panelHost.isViewVisible()

    /** User-visible panel; use [isAttached] for warm-up / attach guards. */
    val isShowing: Boolean get() = isUserVisible

    /**
     * 壳子（ComposeView）是否**真的挂在窗口上**。
     *
     * 为什么不能只看 `panelHost.isAttached`：它只判引用在不在，而系统摘窗（切前台 App / 拉起相册 /
     * 无障碍实例被重建）之后，`OverlayFullScreenPanelHost` 的 detach 回调是**尽力而为**的兜底 ——
     * 一旦没兜住，宿主就攥着一个"已经没有窗口的壳子"（真机上连 `isAttachedToWindow` 都跟着骗人，
     * 所以这里还要求建窗时那一代无障碍实例仍在，见 [attachedGeneration]），于是：
     * - 本属性让 [isUserVisible] / [isShowing] 立刻回到 false（否则 `beginDragReveal` 里那句
     *   `if (sideHost.isShowing) return false` 会把跟手拉出**永久**挡掉）；
     * - [show] / [attachHidden] 开头的 [dropStalePanelShell] 会把壳子丢掉，下一次
     *   `attachPanelWindow` 自然重建窗口（同仓库的内容面板早有这条兜底，见
     *   `SearchPanelOverlayWindow.show` 的 stale shell 重建）。
     */
    private val hasLivePanelWindow: Boolean
        get() = panelHost.composeView?.isAttachedToWindow == true &&
            attachedGeneration == SlideIndexAccessibilityService.overlayHostGeneration()

    /**
     * 建这个壳子时，依赖的是哪一代无障碍实例（见 `SlideIndexAccessibilityService.overlayHostGeneration`）。
     *
     * 这是本文件里**唯一可靠的"窗口还活着吗"判据**：`isAttachedToWindow` 在真机上会骗人 ——
     * 无障碍实例被系统重建时，窗口在 WindowManager 侧整片摘掉，而客户端**没收到 detach**
     * （实测：`dumpsys window` 里已经没有面板窗，`isAttachedToWindow` 仍为 true）。
     */
    private var attachedGeneration = -1

    /**
     * 壳子还在手里、窗口却已经没了 → 丢掉它（**必须主线程调用**：[destroy] 自己就是主线程语义）。
     *
     * 这是"收纳面板点不开 / 拉不出"那条死状态的唯一出口：不丢的话 [show] 会一直走
     * `if (panelHost.isAttached)` 那条"已经开着"的分支，只去给一个不存在的窗口设可见性 ——
     * 零日志、零 toast、窗口永远不出现（§0.16.26）。
     */
    private fun dropStalePanelShell(where: String) {
        val view = panelHost.composeView ?: return
        val current = SlideIndexAccessibilityService.overlayHostGeneration()
        val generationStale = attachedGeneration != current
        if (!generationStale && view.isAttachedToWindow) return
        Log.w(
            tag,
            "$where: stale panel shell (attachedGeneration=$attachedGeneration current=$current " +
                "attachedToWindow=${view.isAttachedToWindow}) -> drop it, next attach rebuilds",
        )
        destroy()
    }

    /**
     * Pre-attaches the panel window (GONE) so float-ball chrome added later stays on top
     * without remove/add z-order bumps.
     */
    fun attachHidden(
        context: Context,
        initialGravityEnd: Boolean = true,
        content: @Composable (
            gravityEnd: Boolean,
            panelTargetVisible: Boolean,
            onToggleSide: () -> Unit,
            onDismiss: () -> Unit
        ) -> Unit,
        onAccessibilityRequired: () -> Boolean = {
            PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)
        },
        onHostContext: () -> Context? = { OverlayDependencyAccess.overlayHostContext() },
        dragReveal: () -> Boolean = { false },
        revealProgress: () -> Float = { 0f }
    ): Boolean {
        // §0.16.26：先把"已经没有窗口的壳子"丢掉，否则下面那条 isAttached 会当成"已经挂着"直接返回。
        dropStalePanelShell("attachHidden")
        if (panelHost.isAttached) {
            attachedBelowChrome = true
            return true
        }
        if (!onAccessibilityRequired()) {
            Log.w(tag, "attachHidden: accessibility service not enabled")
            return false
        }
        val hostContext = onHostContext() ?: run {
            Log.w(tag, "attachHidden: overlay host not connected")
            return false
        }
        dragRevealQuery = dragReveal
        revealProgressQuery = revealProgress
        val attached = attachPanelWindow(
            hostContext = hostContext,
            initialGravityEnd = initialGravityEnd,
            content = content
        )
        if (!attached) return false
        panelHost.setViewVisible(false)
        attachedBelowChrome = true
        return attached
    }

    fun show(
        context: Context,
        initialGravityEnd: Boolean = true,
        content: @Composable (
            gravityEnd: Boolean,
            panelTargetVisible: Boolean,
            onToggleSide: () -> Unit,
            onDismiss: () -> Unit
        ) -> Unit,
        onAccessibilityRequired: () -> Boolean = {
            PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)
        },
        onHostContext: () -> Context? = { OverlayDependencyAccess.overlayHostContext() },
        onShown: () -> Unit = { FloatBallOverlay.scheduleChromeAbovePanels() },
        dragReveal: () -> Boolean = { false },
        revealProgress: () -> Float = { 0f }
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            panelHost.runOnMain {
                result = show(
                    context = context,
                    initialGravityEnd = initialGravityEnd,
                    content = content,
                    onAccessibilityRequired = onAccessibilityRequired,
                    onHostContext = onHostContext,
                    onShown = onShown,
                    dragReveal = dragReveal,
                    revealProgress = revealProgress
                )
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }
        dragRevealQuery = dragReveal
        revealProgressQuery = revealProgress

        // §0.16.26：壳子还在手里、窗口已经没了（系统摘窗 / 无障碍实例被重建）→ 先丢掉再重建。
        // 不这么做的话，下面那条 `if (panelHost.isAttached)` 会把这次"打开"当成"已经开着"，
        // 只去给一个不存在的窗口设可见性：零日志、零 toast、窗口永远不出来（用户报的"又打不开了"）。
        dropStalePanelShell("show")

        val now = SystemClock.elapsedRealtime()
        if (now - lastShowAttemptElapsedMs < SHOW_DEBOUNCE_MS) {
            if (panelHost.isAttached) {
                gravityEndState?.value = initialGravityEnd
                setPanelTargetVisible(true)
                panelHost.setViewVisible(true)
                panelHost.composeView?.post {
                    panelVisibilityState?.targetState = true
                }
                notifyPanelShown(onShown)
                return true
            }
            return false
        }
        lastShowAttemptElapsedMs = now

        if (panelHost.isAttached) {
            gravityEndState?.value = initialGravityEnd
            setPanelTargetVisible(true)
            panelHost.setViewVisible(true)
            panelHost.composeView?.post {
                panelVisibilityState?.targetState = true
            }
            notifyPanelShown(onShown)
            return true
        }

        if (!onAccessibilityRequired()) {
            Log.w(tag, "show: accessibility service not enabled")
            return false
        }
        val hostContext = onHostContext() ?: run {
            Log.w(tag, "show: overlay host not connected")
            return false
        }

        val attached = attachPanelWindow(
            hostContext = hostContext,
            initialGravityEnd = initialGravityEnd,
            content = content
        )
        if (!attached) return false
        attachedBelowChrome = false

        setPanelTargetVisible(true)
        panelHost.setViewVisible(true)
        panelHost.composeView?.post {
            panelVisibilityState?.targetState = true
        }
        notifyPanelShown(onShown)
        return attached
    }

    private fun setPanelTargetVisible(visible: Boolean) {
        panelTargetVisibleState?.value = visible
    }

    /**
     * 面板视图被系统摘掉之后的复位（§0.16.13）。
     *
     * 关键是让 [isUserVisible] / [isShowing] 立刻变回 false —— 它们都要求 `panelHost.isAttached`，
     * 而 ref 已经在 `OverlayFullScreenPanelHost` 里清掉了，所以这里只要把动画状态的目标值也压回 false，
     * 下一次点击 / 拖动就会正常走 `show()` → 重新 `attachPanelWindow()`。
     */
    private fun onPanelViewDetached() {
        Log.w(tag, "onPanelViewDetached: reset visibility state so the panel can be reopened")
        panelVisibilityState?.targetState = false
        panelTargetVisibleState?.value = false
        clipboardInputActive = false
        backHandler = null
        panelBackInterceptor = null
        attachedBelowChrome = false
        // 窗已经被系统摘掉：挂起/恢复外部 UI 的动作也一起作废（§0.16.14）。
        // 不然拉起相册前挂起的那个 lambda 会握着一个已经没有窗口的宿主（虽然内部是空操作，
        // 但语义上"面板已不在"，留着只会让人以为挂起还在生效）。
        StashPanelExternalUi.clear()
    }

    private fun attachPanelWindow(
        hostContext: Context,
        initialGravityEnd: Boolean,
        content: @Composable (
            gravityEnd: Boolean,
            panelTargetVisible: Boolean,
            onToggleSide: () -> Unit,
            onDismiss: () -> Unit
        ) -> Unit
    ): Boolean {
        layoutContext = hostContext
        val gravityEndHolder = mutableStateOf(initialGravityEnd)
        gravityEndState = gravityEndHolder
        val visibleState = MutableTransitionState(false)
        panelVisibilityState = visibleState
        val targetVisibleHolder = mutableStateOf(false)
        panelTargetVisibleState = targetVisibleHolder

        panelHost.ensureWindow(hostContext, focusable = false) {
            val gravityEnd by gravityEndHolder
            val panelTargetVisible by targetVisibleHolder
            // ⚠️ 内容**常驻组合**，不再用 `AnimatedVisibility` 包着 —— 这是"跟手"那条手感的根因修复。
            //
            // `AnimatedVisibility(visibleState)` 在 `currentState || targetState` 都为 false 时会把子树
            // **整个摘掉**，而面板收起后这两个值都是 false。于是**每一次**拉出都要现组一棵
            // `HistoryPanelScreen`（真机日志实测 240~420ms：`revealStart` → `panel content composed`），
            // 而这段正好落在用户已经横向拖动的 MOVE 里 —— 表现就是"手指滑了一段距离，面板才出现"。
            //
            // 现在：内容从**预热**（`attachHidden`）起就活着，可见性、位移、淡入全由
            // `HistoryPanelScreen` 按 `HistoryPanelReveal` 的进度自己画（拖动贴手指、点击走弹簧），
            // 本宿主只管窗口的可见性/触摸标志。代价见 `FloatBallStashPanel.warmUpBelowChrome`：
            // 面板的首次组合成本从"每次拉出"挪到了"预热一次"。
            //
            // 窗口位置曾经也在这里跟着拖动进度走（§0.16.3 的移动窗口方案），已被用户打回：
            // 现在窗口不动（满屏），跟手位移由面板内容自己的 `graphicsLayer` 做（`applyPanelX` 保留但无人调用）。
            content(
                gravityEnd,
                panelTargetVisible,
                { gravityEndHolder.value = !gravityEndHolder.value },
                { dismiss() }
            )
        } ?: return false

        val composeView = panelHost.composeView ?: return false
        // 记住"这扇窗是挂在哪一代无障碍实例上的"：实例被系统换掉后它的窗口 token 会被整片摘掉，
        // 而客户端不一定收到 detach，只能靠代次判断（见 [attachedGeneration] / [dropStalePanelShell]）。
        attachedGeneration = SlideIndexAccessibilityService.overlayHostGeneration()
        backHandler?.detach()
        backHandler = OverlayViewBackHandler(composeView, ::handlePanelBack).also {
            it.attach(requestViewFocus = false)
            // §0.16.22：再补一条更早一档的按键拦截（只在"注入 KEYCODE_BACK"那条路上装）。
            // 真机上"面板开着按返回毫无反应"就是靠它兜住的，理由见 `attachKeyFallback` 的 KDoc。
            it.attachKeyFallback()
        }
        return true
    }

    /**
     * 浏览态（没有输入框 / 浮窗）的窗口输入状态。
     *
     * ⚠️ **必须可聚焦**：`FLAG_NOT_FOCUSABLE` 的窗口**收不到系统返回派发** —— 面板开着时按返回 /
     * 边缘手势返回，事件会落到下面那个 App 上（真机实测："返回把底下界面退了，面板还浮在上面"；
     * 而 `handlePanelBack` 本身是好的，只是没人把事件交给它）。
     *
     * 可聚焦 + `FLAG_ALT_FOCUSABLE_IM` 才是想要的组合：**我们收得到返回，但不把输入法抢过来**
     * —— 这个 flag 的语义正是"可聚焦，但不与输入法交互"（`OverlayFullScreenPanelHost.setAltFocusableIm`）。
     * 要打字时再由 [activatePanelInputFocus] 清掉它、把输入法指向本窗（原有行为不变）。
     */
    private fun ensurePanelBrowsingInput() {
        panelHost.setInputActive(active = true, requestRootFocus = false)
        panelHost.setAltFocusableIm(enabled = true)
        val view = panelHost.composeView ?: return
        val handler = backHandler
        if (handler == null) {
            backHandler = OverlayViewBackHandler(view, ::handlePanelBack).also {
                // 现在是真的要收返回键：视图焦点也一并给上（旧版 OnUnhandledKeyEventListener 那条路要用）。
                it.attach(requestViewFocus = true)
                // §0.16.22：同上，兜底那一条按键路也要跟着装（attach / refresh 之后都要重装一次，
                // 因为 refresh() 内部会先把旧的摘掉）。
                it.attachKeyFallback()
            }
        } else {
            // 窗口刚变成可聚焦，注册得重来一次（`OnBackInvokedCallback` 要窗口有 dispatcher）。
            // §0.16.25：`refresh()` **自己**会按最新口径决定装哪条路、并重装按键兜底
            // （内部先 detach 再 attach）。这里**不要**再调 `attachKeyFallback()` ——
            // 那会在系统走 OnBackInvoked 时把 legacy 按键监听也装上，两条路并存正是
            // 魅族 compose 回调 ↔ 注入按键死循环的配方（真机闪退事故）。
            handler.refresh()
        }
    }

    private fun activatePanelInputFocus() {
        panelHost.setInputActive(active = true, requestRootFocus = true)
    }

    /**
     * §0.16.24：**外部"返回"请求**（手势里的"返回"动作）进入本面板返回链的唯一入口。
     *
     * 链路（**全工程只有这一份返回层级判定**）：
     * ```
     * 注入器 GestureAction.Back → FloatBallStashPanel.requestBackIfShowing()
     *   → 本方法（路由：面板是否显示）
     *     → OverlayViewBackHandler.dispatchBack()
     *        ① 输入法弹着 → 收键盘（面板/弹窗/展开态都不动），已消费
     *        ② 否则 onBack() = handlePanelBack()
     *             → panelBackInterceptor（HistoryPanelScreen 里的 1..6 档，注册表见那里的 KDoc）
     *             → clipboardInputActive（第 7 档：交回浏览态）
     *             → dismiss()（第 8 档：收面板）
     * ```
     * ⚠️ 这里只做**路由**（面板是否显示、有没有 handler）；**键盘优先、子层先后**那条判定**只有一份**，
     * 在 `OverlayViewBackHandler.dispatchBack()` 与 `HistoryPanelScreen` 的注册表里。
     * 调用侧（`SlideIndexAccessibilityGestureInjector` → `FloatBallStashPanel.requestBackIfShowing`）
     * **绝不要**自己判 IME、自己判子层、或自己 `dismiss()` —— 抄一份就会漏掉优先级
     * （真机回归：面板里弹着键盘时手势返回把整个面板关了，而正确行为是收键盘、面板留着）。
     *
     * **必须主线程调用**（手势分发的漏斗 `SlideIndexAccessibilityGestureInjector.perform` 本来就在主线程）；
     * 这里要同步返回"是否已消费"，所以不做跨线程投递。
     *
     * @return true = 这次返回已由面板消费（调用方不要再落 `GLOBAL_ACTION_BACK`）；
     *   面板没显示 → false（不吞别人的返回）；极端情况下 handler 还没建起来 → 同样 false
     *   （宁可交回系统返回，也**不绕过**键盘优先规则去直接 `handlePanelBack()`）。
     */
    fun requestBackIfShowing(): Boolean {
        if (!isUserVisible) return false
        val handler = backHandler ?: return false
        return handler.dispatchBack()
    }

    /**
     * 面板自己的返回策略（第 7、8 档）：输入态 → 收面板。完整层级见
     * `HistoryPanelScreen` 的「返回层级注册表」；键盘优先（第 0 档）在 `dispatchBack()` 里，
     * 已经在本方法被调到**之前**判过了。
     */
    private fun handlePanelBack() {
        // §0.16.22 诊断：面板的返回漏斗。真机上"返回键关不掉面板"时，用它分辨是
        // ① 返回键压根没到浮窗（这条日志不出现），还是 ② 到了却被下面某一档吞掉（这条出现、面板不关）。
        Log.i(tag, "handlePanelBack: interceptor=${panelBackInterceptor != null} clipboardInput=$clipboardInputActive")
        // 第 1..6 档（interceptor）命中时**由它自己**打 `back decision` 行（分支名只有它知道）。
        if (panelBackInterceptor?.invoke() == true) return
        if (clipboardInputActive) {
            setClipboardInputActive(false)
            // §0.16.24 诊断：tag 沿用 `OverlayBack`（不新建），格式与其余档位一字不差，
            // 这样 `grep 'back decision'` 能把整条决策链按顺序读出来。
            Log.i(BACK_DECISION_TAG, "back decision consumed=true branch=clipboardInput")
            return
        }
        Log.i(BACK_DECISION_TAG, "back decision consumed=true branch=dismiss")
        dismiss()
    }

    fun setPanelBackInterceptor(interceptor: (() -> Boolean)?) {
        panelBackInterceptor = interceptor
    }

    /**
     * 把「拖动进度」换算成窗口左边缘坐标并套用。
     *
     * 窗口宽 = 屏宽 × [OverlayPanelLayoutParams.SIDE_PANEL_WIDTH_FRACTION]；gravity 是 TOP|START，
     * 所以：
     * - 贴右侧（gravityEnd）：完全拉出 x = 屏宽 − 窗宽，完全收起 x = 屏宽（整个推出右边界）
     * - 贴左侧：完全拉出 x = 0，完全收起 x = −窗宽
     * 中间线性插值 —— 手指拖 130dp 就拉满（进度由把手侧给，见 `PANEL_REVEAL_DRAG_DP`）。
     */
    private fun applyPanelX(progress: Float, gravityEnd: Boolean) {
        val context = layoutContext ?: return
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val panelWidthPx = (screenWidthPx * OverlayPanelLayoutParams.SIDE_PANEL_WIDTH_FRACTION).toInt()
        val visibleX = if (gravityEnd) screenWidthPx - panelWidthPx else 0
        val hiddenX = if (gravityEnd) screenWidthPx else -panelWidthPx
        val p = progress.coerceIn(0f, 1f)
        val x = (hiddenX + (visibleX - hiddenX) * p).toInt()
        // 只在"第一次真的离开最终位置"时打一条：跟手拖动没法在自动化里验，真机排查靠它。
        if (x != visibleX && !loggedRevealMove) {
            loggedRevealMove = true
            android.util.Log.i(tag, "跟手拖动生效：progress=$p windowX=$x (visibleX=$visibleX hiddenX=$hiddenX)")
        } else if (x == visibleX) {
            loggedRevealMove = false
        }
        panelHost.setRevealOffsetPx(x)
    }

    private fun notifyPanelShown(onShown: () -> Unit) {
        FloatBallOverlay.notifyPanelAttachedAboveChrome()
        ensurePanelBrowsingInput()
        onShown()
        panelHost.composeView?.post {
            onShown()
        }
        panelHost.composeView?.postDelayed({
            onShown()
        }, 360L)
    }

    fun dismiss() {
        // §0.16.23：面板已经收起了 —— "外部 UI 回来要恢复面板"不再成立，先把挂起态作废，
        // 免得随后的兜底 resume 把一个 GONE 的 MATCH_PARENT 窗重新点亮成"整屏点不动"。
        StashPanelExternalUi.onHostWindowDismissed()
        panelHost.runOnMain {
            if (!panelHost.isAttached) return@runOnMain
            val visibleState = panelVisibilityState
            setPanelTargetVisible(false)
            visibleState?.targetState = false
            val view = panelHost.composeView
            val owner = panelHost.owner
            if (view == null || owner == null || visibleState == null) return@runOnMain
            panelHost.setInputActive(false)
            panelHost.setAltFocusableIm(enabled = true)
            clipboardInputActive = false
            owner.lifecycleScope.launch(Dispatchers.Main) {
                delay(300)
                if (visibleState.targetState) return@launch
                view.visibility = View.GONE
            }
        }
    }

    fun destroy() {
        panelHost.runOnMain {
            backHandler?.detach()
            backHandler = null
            panelHost.destroy()
            panelVisibilityState = null
            panelTargetVisibleState = null
            gravityEndState = null
            attachedBelowChrome = false
            clipboardInputActive = false
            lastShowAttemptElapsedMs = 0L
        }
    }

    fun setInputActive(active: Boolean, requestRootFocus: Boolean = true) {
        panelHost.setInputActive(active, requestRootFocus)
    }

    fun setClipboardInputActive(active: Boolean) {
        // ⚠️ 内容常驻组合后，这个回调在面板**收起**时也会被调到（组合没被摘掉）——
        // 那时绝不能把面板窗切成可聚焦（会抢输入法、抢系统返回）。只有面板真的可见才认。
        if (active && !isUserVisible) return
        // ⚠️ 幂等：重复调用（组合每次重组都会调一次）会**再抢一次根视图焦点**，
        // 把手里的输入框焦点顶掉 → 字段失焦 → 又调回 false，形成乒乓。
        if (active == clipboardInputActive) return
        clipboardInputActive = active
        if (active) {
            activatePanelInputFocus()
        } else {
            panelHost.composeView?.clearFocus()
            ensurePanelBrowsingInput()
        }
    }

    fun setDragHidden(hidden: Boolean) {
        panelHost.setDragHidden(hidden)
    }

    /**
     * 拉起**外部系统 UI**（相册选择器）前把面板窗挂起（§0.16.14）。
     *
     * 直接复用跟手拖动那套既有的隐藏逻辑 [OverlayFullScreenPanelHost.setDragHidden]：
     * `INVISIBLE` + `FLAG_NOT_TOUCHABLE`。**不另造隐藏逻辑** —— 恢复时同一个方法正好把
     * 可见性与触摸标志一起复位，不会留下"看得见但点不着"这种半吊子状态。
     */
    fun suspendForExternalUi() {
        panelHost.setDragHidden(true)
        // §0.16.23：让挂起状态**只有一个真源**（`StashPanelExternalUi`）—— 它还要按这个状态起看门狗、
        // 并且要能回答"现在能不能再点 ✎"。两侧都幂等，所以重复自报无害。
        StashPanelExternalUi.onHostWindowSuspended()
    }

    /** 外部 UI 回来后恢复面板窗（与 [suspendForExternalUi] 成对）。 */
    fun resumeAfterExternalUi() {
        panelHost.setDragHidden(false)
        // §0.16.23：`FloatBallStashPanel.show` 里那句直接 resume 也要把状态拉回来，
        // 否则陈旧的"挂起中"会挡住用户下一次 ✎（用户实测的"面板消失了"那一幕）。
        StashPanelExternalUi.onHostWindowResumed()
    }

    /** 跟手拖动期间面板窗要"看得见但不吃触摸"（见 [OverlayFullScreenPanelHost.setTouchable]）。 */
    fun setTouchable(touchable: Boolean) {
        panelHost.setTouchable(touchable)
    }

    fun updateBackgroundBlur(context: Context, blurRadiusDp: Int, userEnabled: Boolean = true): Boolean =
        panelHost.updateBackgroundBlur(context, blurRadiusDp, userEnabled)

    companion object {
        private const val SHOW_DEBOUNCE_MS = 300L

        /**
         * §0.16.24：返回决策日志的 tag —— **沿用 [OverlayViewBackHandler] 那个 `OverlayBack`，不新建 tag**
         * （字符串必须与那边逐字一致，`adb logcat -s OverlayBack` 才能把整条返回链一次捞全）。
         *
         * 统一格式：
         * `back decision consumed=<true|false> branch=<ime|interceptor:*|clipboardInput|dismiss|fallthrough>`
         *
         * **一次返回只会出现一行**，因为每一档命中就往回 return：
         * 第 0 档（`ime`）由 `OverlayViewBackHandler.dispatchBack()` 打，第 1..6 档（`interceptor:*`）由
         * `HistoryPanelScreen` 的 `panelBackInterceptor` 自己打，第 7/8 档（`clipboardInput` / `dismiss`）
         * 由 [handlePanelBack] 打，全都没命中（面板没显示）才由注入器打 `fallthrough`。
         */
        private const val BACK_DECISION_TAG = "OverlayBack"
    }
}
