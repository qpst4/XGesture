package com.slideindex.app.overlay.searchpanel


import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.slideindex.app.overlay.OverlayBlurGate
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayPanelSystemGestureExclusion
import com.slideindex.app.overlay.OverlayTextToolbarProvider
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.settings.SearchPanelBackgroundStyle
import com.slideindex.app.util.PermissionHelper
import kotlin.math.roundToInt

object SearchPanelOverlayWindow {
    private const val TAG = "SearchPanelOverlay"
    private const val IME_RETRY_DELAY_MS = 90L
    private const val IME_MAX_ATTEMPTS = 4
    private const val IME_FOCUS_RETRY_DELAY_MS = 16L
    private val mainHandler = Handler(Looper.getMainLooper())
    /** 文本模式呼出期间为 true：窗口一拿到焦点就抢输入框焦点并请求输入法。 */
    private var imeOnWindowFocusEnabled = false
    private var windowManager: WindowManager? = null
    private var composeViewRef = java.lang.ref.WeakReference<FrameLayout>(null)
    private var composeView: FrameLayout?
        get() = composeViewRef.get()
        set(value) {
            composeViewRef = java.lang.ref.WeakReference(value)
        }
    private var owner: OverlayComposeOwner? = null
    private var screenOffUnregister: (() -> Unit)? = null
    private var appContext: android.app.Application? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var panelVisibilityState: MutableTransitionState<Boolean>? = null
    private var bringAboveToken = 0
    private var bringAboveRunnableA: Runnable? = null
    private var bringAboveRunnableB: Runnable? = null
    private var dismissToken = 0

    /**
     * Home / 最近任务已经关过面板：trampoline 回来的 [restore] 不再把它拉起来。
     *
     * 只有 [show] 会清掉它——用户重新呼出才算"又要用这块面板"。
     */
    private var suppressRestoreAfterSystemNavigation = false

    /** Warm-up keeps [composeView] alive while hidden; only treat visible window as showing. */
    val isShowing: Boolean get() = composeView?.visibility == View.VISIBLE

    /**
     * 拖拽源视图。
     *
     * 必须和 [suspendForDrag] 藏起来的是**同一个 View**：系统拖拽的
     * `ACTION_DRAG_ENDED` 会派到这个发起拖拽的窗口/视图上，藏了别的层会收不到兜底事件。
     * 用窗口内容根（FrameLayout）而不是 Compose 内部的 ComposeView，道理相同。
     */
    val dragHostView: View? get() = composeView

    /** True when [FLAG_BLUR_BEHIND] is active — Compose must not draw screenshot blur. */
    val isNativeBlurActive: Boolean get() = nativeBlurActive
    private var nativeBlurActive = false

    private fun cancelBringAboveRetries() {
        bringAboveRunnableA?.let { mainHandler.removeCallbacks(it) }
        bringAboveRunnableB?.let { mainHandler.removeCallbacks(it) }
        bringAboveRunnableA = null
        bringAboveRunnableB = null
    }

    /** Float ball may finish attaching after the panel window; retry z-order fixes. */
    private fun scheduleBringFloatBallAbovePanels() {
        val token = ++bringAboveToken
        cancelBringAboveRetries()
        fun attempt() {
            if (token != bringAboveToken) return
            com.slideindex.app.overlay.panels.PanelFloatBallHooks.scheduleChromeAbovePanels?.invoke(0L)
        }
        attempt()
        composeView?.post {
            attempt()
            composeView?.postOnAnimation { attempt() }
        }
        val delayedA = Runnable { attempt() }
        val delayedB = Runnable { attempt() }
        bringAboveRunnableA = delayedA
        bringAboveRunnableB = delayedB
        mainHandler.postDelayed(delayedA, 200)
        mainHandler.postDelayed(delayedB, 800)
    }

    /**
     * 从下层内容面板（取词面板）呼出搜索面板前调用：释放预热壳，让下一次 [show] 重新建窗。
     *
     * 不能靠 `removeView` + `addView` 抢 z-order：搜索面板的 ComposeView 用的是
     * `DisposeOnDetachedFromWindow`，重排会让组合被销毁，重新 attach 后 measure 时
     * 找不到窗口根上的 `ViewTreeLifecycleOwner`，直接崩（真机 logcat 已复现）。
     * 反过来先销毁再 [show]，新窗口最后 add，天然压在取词面板之上。
     */
    fun releaseWarmShellForPickPanel() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { releaseWarmShellForPickPanel() }
            return
        }
        // 无条件销毁：只要残留的是预热壳（甚至上一轮没完全收起的窗口），show() 都只会
        // updateViewLayout（不改 z-order），窗口就停在比取词面板更低的层级。
        // 释放后 show() 重新建窗，新窗口最后 add，天然压在取词面板之上。
        releaseWarmUp()
    }

    fun warmUp(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { warmUp(context) }
            return
        }
        // 预热必须等无障碍宿主就绪：早于它建窗会拿到无效 token（BadTokenException），
        // 之后 show() 的 updateViewLayout 全部失败，面板就变成"窗口在但永远不可见"。
        val hostContext = com.slideindex.app.overlay.panels.PanelRuntimeHooks.overlayHostContext?.invoke() ?: return
        ensureWindow(hostContext)
        if (!isShowing) {
            applyPanelShellPassive()
        }
    }

    fun show(context: Context): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = show(context)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }
        // 之前建窗失败或被系统摘掉的壳子先销毁重建，否则会在死状态里打转。
        if (composeView != null && composeView?.isAttachedToWindow != true) {
            Log.w(TAG, "show: stale shell not attached, rebuilding window")
            destroyWindow()
        }
        ++dismissToken
        suppressRestoreAfterSystemNavigation = false
        if (isShowing) {
            applyPanelShellActive()
            composeView?.post {
                panelVisibilityState?.targetState = true
            }
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.onContentPanelShown?.invoke()
            scheduleBringFloatBallAbovePanels()
            // 面板已经开着时再次呼出（例如键盘被返回键收起后）：把键盘重新拉起来。
            if (imeOnWindowFocusEnabled) {
                focusSearchFieldSoon()
                requestImeShow()
            }
            return true
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }
        val hostContext = com.slideindex.app.overlay.panels.PanelRuntimeHooks.overlayHostContext?.invoke() ?: run {
            Log.w(TAG, "show: accessibility service not connected")
            return false
        }
        ensureWindow(hostContext)
        applyPanelShellActive()
        composeView?.post {
            // 预热阶段建的壳子可能因宿未就绪而没真正挂上（attached=false）：
            // 首帧校验一次，必要时销毁重建并重新激活，避免"状态对、画面没有"的幽灵窗口。
            if (composeView?.isAttachedToWindow != true || composeView?.width == 0) {
                Log.w(TAG, "show: shell not attached after first frame, rebuilding")
                destroyWindow()
                ensureWindow(hostContext)
                applyPanelShellActive()
            }
            panelVisibilityState?.targetState = true
        }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.onContentPanelShown?.invoke()
        scheduleBringFloatBallAbovePanels()
        return composeView != null
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        ++bringAboveToken
        cancelBringAboveRetries()
        val token = ++dismissToken
        SearchPanelSessionState.persistBeforeDismiss?.invoke()
        panelVisibilityState?.targetState = false
        mainHandler.postDelayed({
            if (token != dismissToken) return@postDelayed
            if (panelVisibilityState?.targetState == true) return@postDelayed
            applyPanelShellPassive()
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.onContentPanelHidden?.invoke()
            destroyWindow()
        }, 140L)
    }

    /**
     * Home / 最近任务触发的关闭。
     *
     * 与 [dismiss] 同语义（持久化查询词 + 收回动画），额外做一件事：**作废「[hide] 的临时隐藏」**。
     * 面板为选图 / 权限 trampoline 调过 [hide] 时，回来会走 [restore]；若这中间用户按了 Home，
     * 那次 restore 会把面板重新拉到桌面上。这里立标记，[restore] 看到就跳过，直到下次 [show]。
     */
    fun dismissForSystemNavigation() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissForSystemNavigation() }
            return
        }
        suppressRestoreAfterSystemNavigation = true
        dismiss()
    }

    /** @return true if back was handled (preview dismissed or panel closed). */
    fun handleBack(): Boolean {
        if (!isShowing) return false
        if (SearchPanelSessionState.onBackPressed?.invoke() == true) {
            return true
        }
        dismiss()
        return true
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        applyPanelShellPassive()
    }

    /** 应用内语言切换后销毁预热窗口，下次展示时用新 Configuration 重建。 */
    fun releaseWarmUp() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { releaseWarmUp() }
            return
        }
        dismiss()
        destroyWindow()
    }

    fun restore() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restore() }
            return
        }
        if (suppressRestoreAfterSystemNavigation) {
            Log.i(TAG, "restore skipped: panel dismissed by system navigation")
            return
        }
        if (composeView != null) {
            ++dismissToken
            applyPanelShellActive()
            scheduleBringFloatBallAbovePanels()
        }
    }

    /**
     * 长按拖拽起拖：把面板藏起来但**不销毁**，搜索词、结果、滚动位置都留着。
     *
     * 用 `INVISIBLE` 而不是 `GONE`：源视图自己的 `ACTION_DRAG_ENDED` 还要靠这个
     * 窗口派回来（与收纳面板的 `setDragHidden` 一致）。`FLAG_NOT_FOCUSABLE`
     * 会让输入法跟着收起，避免拖拽期间 IME 还压在屏幕上。
     */
    fun suspendForDrag() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suspendForDrag() }
            return
        }
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        view.clearFocus()
        view.visibility = View.INVISIBLE
        runCatching { wm.updateViewLayout(view, params) }
    }

    /**
     * 拖拽落回【恢复】：面板原样回来。
     *
     * 输入法是否重新出现交给窗口焦点逻辑，这里不额外强制。如果拖拽期间面板已经被
     * 别处关掉（返回键 / 熄屏 / Home），不再把它拉起来。
     */
    fun resumeFromDrag() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { resumeFromDrag() }
            return
        }
        if (suppressRestoreAfterSystemNavigation) return
        if (composeView == null) return
        if (panelVisibilityState?.targetState != true) return
        ++dismissToken
        applyPanelShellActive()
        scheduleBringFloatBallAbovePanels()
    }

    /** Applies cross-window blur when BLUR mode is active (matches honeycomb overlay). */
    fun updateBackgroundBlur(context: Context, backgroundStyle: Int, blurRadiusDp: Int): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = updateBackgroundBlur(context, backgroundStyle, blurRadiusDp)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }
        val wm = windowManager ?: return false
        val view = composeView ?: return false
        val params = layoutParams ?: return false

        val wantsNativeBlur = backgroundStyle == SearchPanelBackgroundStyle.BLUR &&
            blurRadiusDp > 0
        val canNativeBlur = wantsNativeBlur && OverlayBlurGate.isSystemBlurEnabled(wm)
        nativeBlurActive = canNativeBlur

        if (canNativeBlur) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            val density = context.resources.displayMetrics.density
            val rawBlurPx = (blurRadiusDp * density).roundToInt()
            val clampedBlurPx = rawBlurPx.coerceIn(1, 80)
            params.setBlurBehindRadius(clampedBlurPx)
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.setBlurBehindRadius(0)
        }
        runCatching { wm.updateViewLayout(view, params) }
        return nativeBlurActive
    }

    /**
     * 文本搜索呼出时让窗口「获得焦点即弹出输入法」，图片模式或收起时关掉。
     *
     * 交给系统判断弹出时机，省掉自己请求输入法的竞态；图片模式没有可编辑控件，不该弹。
     */
    fun updateSoftInputAlwaysVisible(visible: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updateSoftInputAlwaysVisible(visible) }
            return
        }
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        val flag = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        imeOnWindowFocusEnabled = visible
        val flagOn = params.softInputMode and flag != 0
        if (flagOn == visible) return
        params.softInputMode = if (visible) {
            params.softInputMode or flag
        } else {
            params.softInputMode and flag.inv()
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    /**
     * 明确请求显示输入法。窗口已获得焦点时调用，越早越好。
     *
     * 部分机型 / 输入法会吞掉第一次请求，这里按 [IME_RETRY_DELAY_MS] 间隔最多补发 [IME_MAX_ATTEMPTS] 次；
     * 已经可见就不再打扰。
     */
    fun requestImeShow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { requestImeShow() }
            return
        }
        var issued = 0
        fun issue() {
            val view = composeView ?: return
            if (issued > 0 && isImeVisible(view)) return
            showIme(view)
            issued++
            if (issued < IME_MAX_ATTEMPTS) {
                view.postDelayed({ issue() }, IME_RETRY_DELAY_MS)
            }
        }
        issue()
    }

    /**
     * 窗口刚拿到焦点时调用：抢搜索框焦点并请求输入法。
     *
     * 直接挂窗口焦点回调（View 级）而不是等 Compose 的 `isWindowFocused` 重组，能早 1~3 帧发出请求。
     */
    private fun onWindowFocusGained() {
        if (!imeOnWindowFocusEnabled) return
        focusSearchFieldSoon()
        requestImeShow()
    }

    private fun focusSearchFieldSoon(attempt: Int = 0) {
        val view = composeView ?: return
        val focused = SearchPanelSessionState.focusSearchTextField?.invoke() == true
        if (focused || attempt >= IME_MAX_ATTEMPTS - 1) return
        // 输入框可能还没进入组合，下一帧再试。
        view.postDelayed({ focusSearchFieldSoon(attempt + 1) }, IME_FOCUS_RETRY_DELAY_MS)
    }

    private fun isImeVisible(view: View): Boolean =
        ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun showIme(view: View) {
        val controller = view.windowInsetsController
        val requested = controller != null && runCatching {
            // 平台 WindowInsetsController 的 show() 期望 android.view.WindowInsets.Type 常量，
            // 传 WindowInsetsCompat.Type 会被 lint(IncorrectConstant) 判成非法常量。
            controller.show(WindowInsets.Type.ime())
        }.isSuccess
        if (requested) return
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        // showSoftInput 的 flags 传 0 即可（SHOW_IMPLICIT 已废弃）。
        runCatching { imm?.showSoftInput(view, 0) }
    }

    /** Invisible prefetch shell: must not intercept touches beneath the system UI. */
    private fun applyPanelShellPassive() {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        view.clearFocus()
        view.visibility = View.GONE
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun applyPanelShellActive() {
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        view.visibility = View.VISIBLE
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "updateViewLayout(active) failed", it) }
        if (!view.isAttachedToWindow) {
            Log.w(TAG, "active: view not attached (window likely dropped), flags=0x${params.flags.toString(16)}")
        }
        view.requestFocus()
    }

    /**
     * 面板窗是 TYPE_APPLICATION_OVERLAY（2038），必须用**同类型的 window context** 建窗。
     * 无障碍服务实例的 token 属于 2032，无障碍一旦重绑旧实例的 token 就失效，
     * 表现为 BadTokenException / 系统侧 "Failed looking up window session"，窗口永远不可见。
     */
    private fun resolvePanelHostContext(fallback: Context): Context {
        val appContext = fallback.applicationContext
        val display = runCatching { fallback.display }.getOrNull()
            ?: runCatching {
                (appContext.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager)
                    ?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            }.getOrNull()
            ?: return fallback
        return runCatching {
            appContext.createWindowContext(
                display,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                null,
            )
        }.getOrNull() ?: fallback
    }

    private fun ensureWindow(hostContext: Context) {
        if (composeView != null) return
        appContext = hostContext.applicationContext as android.app.Application
        val panelHostContext = resolvePanelHostContext(hostContext)
        windowManager = panelHostContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        panelVisibilityState = MutableTransitionState(false)

        owner = OverlayComposeOwner()

        composeView = object : FrameLayout(panelHostContext) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        handleBack()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            visibility = View.GONE
            // 窗口一拿到焦点就尽早抢焦点并请求输入法（不等 Compose 重组）。
            viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
                if (hasFocus) onWindowFocusGained()
            }
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            val cv = OverlayCompose.createComposeView(panelHostContext, owner!!).apply {
                setContent {
                    com.slideindex.app.ui.theme.OverlayAwareModuleTheme {
                        OverlayTextToolbarProvider {
                            com.slideindex.app.overlay.searchpanel.SearchPanelContentAccess.content?.content(
                                visibilityState = panelVisibilityState!!,
                                onDismiss = { dismiss() },
                            )
                        }
                    }
                }
            }
            addView(cv, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        // Start passive: warmUp attaches this window early and must not eat screen touches.
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.contentPanelWindowType(hostContext),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "XG:search-panel"
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            windowManager?.addView(composeView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add window", e)
            destroyWindow()
            return
        }
        if (com.slideindex.app.overlay.panels.PanelFloatBallHooks.isShowing?.invoke() == true) {
            com.slideindex.app.overlay.panels.PanelFloatBallHooks.scheduleChromeAbovePanels?.invoke(0L)
        }
        composeView?.let { OverlayPanelSystemGestureExclusion.attach(it) }

        screenOffUnregister = com.slideindex.app.overlay.panels.PanelRuntimeHooks.registerScreenOffDismiss?.invoke(hostContext) { dismiss() }
    }

    private fun destroyWindow() {
        if (composeView == null) return
        cancelBringAboveRetries()
        val frame = composeView
        val dialogOwner = owner
        val childCompose = frame?.let { parent ->
            (0 until parent.childCount)
                .map { parent.getChildAt(it) }
                .filterIsInstance<ComposeView>()
                .firstOrNull()
        }
        try {
            frame?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing window", e)
        }
        OverlayCompose.teardownOverlayCompose(childCompose, dialogOwner)
        frame?.let { OverlayCompose.clearViewTreeOwners(it) }
        composeView = null
        owner = null
        windowManager = null
        layoutParams = null
        nativeBlurActive = false
        imeOnWindowFocusEnabled = false
        panelVisibilityState = null
        screenOffUnregister?.invoke()
        screenOffUnregister = null
        appContext = null
        // 所有关闭路径都收敛在这里：通知「因搜图而隐藏」的取词面板恢复。
        SearchPanelSessionState.onPanelClosed?.invoke()
    }
}
