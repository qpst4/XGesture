package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.slideindex.app.util.PredictiveBackHelper

/**
 * Routes system back (gesture + key) to overlay [ComposeView] windows.
 *
 * **走哪条路由系统说了算，不由用户设置猜**（§0.16.25）：本类按
 * [PredictiveBackHelper.resolveAppBackDispatch] 读到的**系统真实口径**二选一 ——
 * 系统走 `OnBackInvokedCallback` 时只装回调，系统走"兼容注入 `KEYCODE_BACK`"时只装按键监听。
 *
 * 为什么这么严：魅族（Flyme）上这两条**不能并存** —— 系统会为 legacy 监听装
 * [android.view.ViewRootImpl.registerCompatOnBackInvokedCallback]，它与
 * [android.view.ViewRootImpl.injectBackKeyEvents] 会互相触发。真机事故（§0.16.25，2026-10-10）：
 * 两边口径对不上时，一次返回键在这个环里转了约 3900 圈，主线程栈 8MB 撑爆 → `StackOverflowError` 闪退。
 * **按系统口径装路就是防它的主防线**：口径一致时，系统不会为我们注册那个兼容回调，
 * 环也就没有了材料（app 侧无法打断系统内部的递归，所以不要在 app 层加"回声闸"去拦它 ——
 * 试过：拦不住环，却会把"系统注入键送达到浮窗"这条正常路吃掉，让浮窗返回失效）。
 *
 * When predictive back is off at the app level, [OnBackInvokedCallback] is not dispatched;
 * legacy [OnUnhandledKeyEventListenerCompat] handles injected [KeyEvent.KEYCODE_BACK].
 *
 * **键盘优先**（§0.16.7）：键盘弹着时，这一次返回先收键盘、**不**交给浮窗自己的 [onBack]。
 * 普通 App 里这一档是输入法自己吃掉的（IME 有返回回调），而我们是 `TYPE_*_OVERLAY` 浮窗、
 * 且应用级 predictive-back 关掉时返回走"注入 `KEYCODE_BACK`"的兼容路径 —— 返回键**直接注给
 * 聚焦的那个浮窗**，输入法没机会先吃，于是"键盘弹着按返回"会直接把弹窗关掉（用户报的就是这个）。
 * 判断与处理都放在 [dispatchBack] 这个唯一漏斗里，所以**所有用它的浮窗一起受益**。
 */
class OverlayViewBackHandler(
    private val view: View,
    private val onBack: () -> Unit,
) {
    private var backInvokedCallback: OnBackInvokedCallback? = null
    private var unhandledKeyListener: ViewCompat.OnUnhandledKeyEventListenerCompat? = null
    private var attachListener: View.OnAttachStateChangeListener? = null
    private var usesUnhandledKeyBackListener = false
    /** §0.16.22：是否已经装了"更早一档"的 [View.setOnKeyListener] 兜底（见 [attachKeyFallback]）。 */
    private var keyFallbackInstalled = false
    private var handlingBack = false
    private var registerAttempts = 0

    /**
     * §0.16.25：**系统真实口径**（不是用户设置）。决定装 `OnBackInvokedCallback` 还是按键监听，
     * 装错一次就会在魅族上形成注入死循环（见类注释）。
     */
    private var appBackDispatch = PredictiveBackHelper.AppBackDispatch.NONE

    fun attach(requestViewFocus: Boolean = true) {
        refreshAppBackDispatch()
        if (requestViewFocus) {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
        }
        if (shouldUseOnBackInvoked()) {
            registerAttempts = 0
            scheduleRegisterOnBackInvoked()
        } else {
            registerUnhandledKeyBackListener()
        }
    }

    /** Retry registration after the overlay window becomes focusable or predictive-back toggles. */
    fun refresh() {
        refreshAppBackDispatch()
        detachInternal(clearHandlingFlag = false)
        if (shouldUseOnBackInvoked()) {
            registerAttempts = 0
            scheduleRegisterOnBackInvoked()
        } else {
            registerUnhandledKeyBackListener()
        }
    }

    /**
     * 重新读一次系统口径。
     *
     * ⚠️ 用户设置翻转时系统 flag 也变了（`MainActivity.applyPredictiveBackEnabled`），
     * 但**已经装上的拦截路不会自己跟着换** —— 所以设置翻转那条路必须显式调 [refresh]；
     * 这里保证 [attach] / [refresh] / [attachKeyFallback] 每次都读的是最新值。
     */
    private fun refreshAppBackDispatch() {
        val resolved = PredictiveBackHelper.resolveAppBackDispatch(view.context)
        if (resolved != appBackDispatch) {
            Log.i(
                TAG,
                "返回键口径：${appBackDispatch.label} → ${resolved.label}（按系统实际派发装路，避免魅族注入死循环）",
            )
        }
        appBackDispatch = resolved
    }

    /**
     * §0.16.22：再补一条**更早一档**的返回键拦截（[View.setOnKeyListener]）。
     *
     * 真机事故（闪念面板开着，返回键完全无反应；点面板外遮罩却能关）之后的复盘：
     * [ViewCompat.OnUnhandledKeyEventListenerCompat] 只在"**整棵视图树都没人处理**这个事件"时
     * 才被调用 —— 而浮窗里是 Compose，`AndroidComposeView` 的按键分发（焦点系统 / 节点上的
     * `onPreviewKeyEvent`）只要把返回键判成"已处理"，那个"未处理"回调就**永远没机会跑**，
     * 于是浮窗没有任何存活的返回路径（本仓库里确实存在 Compose 侧消费 `Key.Back` 的先例，
     * 见 `PickResultInteractiveText` 的 `onPreviewKeyEvent`）。
     *
     * [View.dispatchKeyEvent] 里 `OnKeyListener` 是**第一顺位**（早于 `event.dispatch()` 走视图树），
     * 拿到的是同一支返回键，且动作仍然汇进**同一个漏斗** [dispatchBack] ——
     * 所以"键盘弹着先收键盘"（§0.16.7）与重入保护照旧生效，不另造一套语义。
     *
     * ⚠️ 只在"注入 `KEYCODE_BACK`"这条路（[shouldUseOnBackInvoked] 为 false）装：
     * OnBackInvoked 与 legacy 按键监听**不能并存**（Flyme 上会形成
     * `registerCompatOnBackInvokedCallback` ↔ `injectBackKeyEvents` 的循环，见类注释）。
     * ⚠️ 只被"面板窗"这一条路调用（`OverlaySidePanelHost`），其余浮窗行为不变。
     */
    fun attachKeyFallback() {
        // 上一次装的先清掉：反复 show / 设置翻转都会走到这里，保证同一时刻只有一条按键路。
        if (keyFallbackInstalled) {
            view.setOnKeyListener(null)
            keyFallbackInstalled = false
        }
        refreshAppBackDispatch()
        if (shouldUseOnBackInvoked()) return
        keyFallbackInstalled = true
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            // 与 unhandled 监听同一套语义：DOWN 直接消费（不漏给下面的 App），UP 才动作。
            if (event.action == KeyEvent.ACTION_UP) dispatchBack()
            true
        }
    }

    private fun shouldUseOnBackInvoked(): Boolean =
        appBackDispatch == PredictiveBackHelper.AppBackDispatch.ON_BACK_INVOKED

    private fun scheduleRegisterOnBackInvoked() {
        fun tryRegister() {
            if (backInvokedCallback != null || usesUnhandledKeyBackListener) return
            val dispatcher = resolveBackInvokedDispatcher()
            if (dispatcher != null) {
                registerOnBackInvoked(dispatcher)
                return
            }
            if (!view.isAttachedToWindow) return
            if (registerAttempts++ < MAX_ON_BACK_REGISTER_ATTEMPTS) {
                view.post { tryRegister() }
            }
        }

        if (view.isAttachedToWindow) {
            view.post { tryRegister() }
            return
        }

        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                view.removeOnAttachStateChangeListener(this)
                attachListener = null
                view.post { tryRegister() }
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        attachListener = listener
        view.addOnAttachStateChangeListener(listener)
    }

    private fun resolveBackInvokedDispatcher(): OnBackInvokedDispatcher? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        view.findOnBackInvokedDispatcher()?.let { return it }
        val root = view.rootView
        if (root !== view) {
            root.findOnBackInvokedDispatcher()?.let { return it }
        }
        return null
    }

    private fun registerOnBackInvoked(dispatcher: OnBackInvokedDispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = OnBackInvokedCallback { dispatchBack() }
        backInvokedCallback = callback
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
    }

    /**
     * 本浮窗的**返回唯一漏斗**：键盘优先 → 浮窗自己的 [onBack]。
     *
     * 三个来源**共用这一份判定**：
     * ① `OnBackInvokedCallback`（predictive-back 开着时）；
     * ② legacy 按键监听（`OnUnhandledKeyEventListener` / 无参 `setOnKeyListener`，注入 `KEYCODE_BACK`）；
     * ③ **外部"返回"请求** —— 手势里的"返回"动作那条路（`SlideIndexAccessibilityGestureInjector`
     *    → `FloatBallStashPanel.requestBackIfShowing` → `OverlaySidePanelHost.requestBackIfShowing`）。
     *
     * ⚠️ **键盘优先（§0.16.7）只在这里判**（[hideImeAndConsumeBack]）：键盘弹着时这一次返回归键盘，
     * 收键盘、**不动浮窗**。调用侧（手势那条路、各浮窗自己）**不要再抄一份 IME 判定、也不要自己
     * `dismiss()`** —— 抄一份必然漏掉这条优先级（真机回归：面板里弹着键盘时，手势返回把面板直接关了）。
     *
     * @return true = 这次返回由本浮窗消费，调用方不要再把它交给系统（`GLOBAL_ACTION_BACK`）。
     */
    fun dispatchBack(): Boolean {
        if (handlingBack) {
            // 同一浮窗的返回正在处理中（Flyme 的 compat 回调与按键注入可能同时到）：这一次同样算
            // "已被我们认领"，绝不能返回 false 让它漏给下层 App。
            return true
        }
        handlingBack = true
        try {
            // §0.16.22 诊断：真机上"返回键到底有没有走到浮窗的返回漏斗"只能靠这条日志分辨
            // （没有它就只能靠猜；`adb logcat -s OverlayBack`）。
            Log.i(TAG, "dispatchBack: 收到一次返回（按键或手势请求），交给 onBack（键盘优先规则在内）")
            // 键盘弹着 → 这一次返回归键盘（见类注释）。收完就消费掉，不给浮窗自己的 onBack。
            if (hideImeAndConsumeBack()) {
                // §0.16.24 诊断：第 0 档「键盘优先」命中。这条路**不会**再调 [onBack]，
                // 所以面板链上第 1..8 档一次都不会打日志 —— 这是本次返回**唯一的一行**
                // `back decision`（统一格式见 [OverlaySidePanelHost] 里的常量注释）。
                Log.i(TAG, "back decision consumed=true branch=ime")
                return true
            }
            onBack()
            return true
        } finally {
            view.post { handlingBack = false }
        }
    }

    /**
     * 键盘弹着就收键盘并返回 `true`（这一次返回被消费掉）。
     *
     * ⚠️ **只收键盘，绝不碰焦点**：`clearFocus()` / 把窗口切回"不与输入法交互"的状态之后，
     * 用户再点同一个输入框，键盘可能就弹不出来了（面板那边的输入态是靠状态变化驱动的）。
     */
    private fun hideImeAndConsumeBack(): Boolean {
        if (!isImeVisible()) return false
        val token = view.windowToken ?: return false
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return false
        runCatching { imm.hideSoftInputFromWindow(token, 0) }
        return true
    }

    /**
     * 键盘是否弹着。
     *
     * 两步判据（与 `OverlayImeInsets.rememberOverlayImeBottomHeight` 同一套）：
     * ① 窗口 insets 直接报了 IME 高度 → 最权威；
     * ② 覆盖窗上 `WindowInsets.ime` 常常是 0，退回"可见区域比根视图矮一截"。
     *
     * ⚠️ ② 的门槛**不能是 >0**：手势条/导航栏本身就会占掉几十 px，那样键盘没弹也会被误判成
     * "弹着"，于是把第一下返回吞掉（表现成"按返回没反应"）。键盘占屏高 30% 以上，
     * 这里要求超过 [IME_MIN_HEIGHT_FRACTION] 才认。
     */
    private fun isImeVisible(): Boolean {
        val insets = ViewCompat.getRootWindowInsets(view)
        if ((insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0) > 0) return true

        val rootHeight = view.rootView.height
        if (rootHeight <= 0) return false
        val visibleFrame = Rect()
        view.getWindowVisibleDisplayFrame(visibleFrame)
        return rootHeight - visibleFrame.bottom > rootHeight * IME_MIN_HEIGHT_FRACTION
    }

    private fun registerUnhandledKeyBackListener() {
        if (usesUnhandledKeyBackListener) return
        usesUnhandledKeyBackListener = true
        val keyListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
            if (event.keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                return@OnUnhandledKeyEventListenerCompat false
            }
            dispatchBack()
            true
        }
        unhandledKeyListener = keyListener
        ViewCompat.addOnUnhandledKeyEventListener(view, keyListener)
    }

    fun detach() {
        detachInternal(clearHandlingFlag = true)
    }

    private fun detachInternal(clearHandlingFlag: Boolean) {
        attachListener?.let { view.removeOnAttachStateChangeListener(it) }
        attachListener = null
        registerAttempts = 0
        if (keyFallbackInstalled) {
            // §0.16.22：与 attachKeyFallback 成对（refresh() 会先 detach 再按最新设置重装）。
            view.setOnKeyListener(null)
            keyFallbackInstalled = false
        }
        if (usesUnhandledKeyBackListener) {
            unhandledKeyListener?.let { listener ->
                ViewCompat.removeOnUnhandledKeyEventListener(view, listener)
            }
            unhandledKeyListener = null
            usesUnhandledKeyBackListener = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback?.let { callback ->
                resolveBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
            }
        }
        backInvokedCallback = null
        if (clearHandlingFlag) {
            handlingBack = false
        }
    }

    private companion object {
        private const val TAG = "OverlayBack"
        private const val MAX_ON_BACK_REGISTER_ATTEMPTS = 12

        /**
         * 「可见区域矮了多少」才算键盘弹着（占根视图高度的比例）。
         *
         * 键盘一般占 30%~45%，手势条/导航栏只有 2%~5%，取 15% 两头都安全。
         */
        private const val IME_MIN_HEIGHT_FRACTION = 0.15f
    }
}
