package com.slideindex.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages a single full-screen overlay [ComposeView] window: attach, focus, screen-off, destroy.
 * Used by side panels, translate panel, and pick-result panel.
 */
class OverlayFullScreenPanelHost(
    private val tag: String,
    private val layoutParamsFactory: (Context, Boolean) -> WindowManager.LayoutParams =
        { context, focusable -> OverlayPanelLayoutParams.fullScreenOverlay(context, focusable) },
    private val onScreenOff: () -> Unit = {},
    private val excludeLeftBackEdge: Boolean = true,
    /**
     * 系统把我们的窗口摘掉时回调（§0.16.13 的修复）。
     *
     * 例如 Flyme 在切前台 App / 拉起别的 Activity 时会隐藏无障碍覆盖层，我们的 ComposeView 随之
     * `onViewDetachedFromWindow` —— 但**原来不清 `composeViewRef`**，于是 `isAttached` /
     * `isViewVisible()` 一直为 true，宿主就"以为面板还开着"：用户再点只会走 dismiss，
     * 表现为**面板再也打不开**（只能强停 App 才好）。
     */
    private val onViewDetached: () -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val screenOffDismissReceiver = ScreenOffDismissReceiver { onScreenOff() }
    private var appContext: Context? = null

    val isAttached: Boolean get() = composeView != null

    val composeView: ComposeView? get() = composeViewRef

    val owner: OverlayComposeOwner? get() = ownerRef

    private var composeViewRef: ComposeView? = null
    private var ownerRef: OverlayComposeOwner? = null

    fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun ensureWindow(
        context: Context,
        focusable: Boolean = false,
        content: @Composable () -> Unit
    ): OverlayComposeOwner? {
        if (composeViewRef != null) return ownerRef

        val dialogOwner = OverlayComposeOwner()
        val overlayContext = OverlayCompose.themedContext(context)
        val compose = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setContent { content() }
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            dialogOwner.destroy()
            Log.w(tag, "ensureWindow: WindowManager unavailable")
            return null
        }

        val params = layoutParamsFactory(context, focusable)
        val added = runCatching { wm.addView(compose, params) }
            .onFailure { Log.e(tag, "addView failed", it) }
            .isSuccess
        if (!added) {
            dialogOwner.destroy()
            return null
        }
        if (FloatBallOverlay.isShowing) {
            FloatBallOverlay.notifyPanelAttachedAboveChrome()
        }
        OverlayPanelSystemGestureExclusion.attach(compose, excludeLeftBackEdge = excludeLeftBackEdge)

        windowManager = wm
        composeViewRef = compose
        ownerRef = dialogOwner
        layoutParams = params
        appContext = context
        screenOffDismissReceiver.register(context)
        // §0.16.13：窗口被系统摘掉时（不只是我们自己 hide）要把宿主状态复位，否则"面板打不开"。
        compose.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                if (composeViewRef !== v) return
                Log.w(tag, "panel view detached by system -> reset host state")
                composeViewRef = null
                ownerRef = null
                layoutParams = null
                windowManager = null
                runCatching { screenOffDismissReceiver.unregister() }
                runCatching { onViewDetached() }
            }
        })
        return dialogOwner
    }

    fun setViewVisible(visible: Boolean) {
        composeViewRef?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setDragHidden(hidden: Boolean) {
        runOnMain {
            val wm = windowManager ?: return@runOnMain
            val view = composeViewRef ?: return@runOnMain
            val params = layoutParams ?: return@runOnMain
            if (hidden) {
                view.visibility = View.INVISIBLE
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                view.visibility = View.VISIBLE
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    /**
     * 只切「可触摸」，不动可见性。
     *
     * 跟手拉出用的：面板窗**看得见**（跟着手指走），但拖动期间不能抢把手那个窗的手势 ——
     * 面板窗是 MATCH_PARENT，只要它还吃触摸，把手就拿不到后续的 MOVE。
     */
    fun setTouchable(touchable: Boolean) {
        runOnMain {
            val wm = windowManager ?: return@runOnMain
            val view = composeViewRef ?: return@runOnMain
            val params = layoutParams ?: return@runOnMain
            params.flags = if (touchable) {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    /**
     * 面板窗的**水平位置**（px，窗口左边缘坐标；gravity 是 TOP|START，所以正数向右，左右侧都不用反号）。
     *
     * 跟手拖出用它：窗口只有 78% 宽（见 [OverlayPanelLayoutParams.stashClipboardSidePanel]），
     * 移动**窗口本身**（而不是窗口里的内容）才能让系统级背景模糊跟着实时重算 ——
     * 这也是"拖动过程也是实时模糊"的关键（自绘 RenderEffect 快照一平移就失效，只剩白 tint）。
     *
     * 值没变就直接返回：拖动每帧都会调，避免无谓的 `updateViewLayout`。
     */
    fun setRevealOffsetPx(px: Int) {
        runOnMain {
            val wm = windowManager ?: return@runOnMain
            val view = composeViewRef ?: return@runOnMain
            val params = layoutParams ?: return@runOnMain
            if (params.x == px) return@runOnMain
            params.x = px
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    /**
     * Cross-window blur for translucent overlay panels (API 31+, [WindowManager.isCrossWindowBlurEnabled]).
     * Returns true when [FLAG_BLUR_BEHIND] is active — Compose should use semi-transparent surfaces.
     */
    fun updateBackgroundBlur(context: Context, blurRadiusDp: Int, userEnabled: Boolean = true): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = updateBackgroundBlur(context, blurRadiusDp, userEnabled)
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }
        val wm = windowManager ?: return false
        val view = composeViewRef ?: return false
        val params = layoutParams ?: return false

        val wantsNativeBlur = blurRadiusDp > 0 && userEnabled
        val canNativeBlur = wantsNativeBlur && OverlayBlurGate.isSystemBlurEnabled(wm)

        if (canNativeBlur) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            val density = context.resources.displayMetrics.density
            val rawBlurPx = (blurRadiusDp * density).roundToInt()
            params.setBlurBehindRadius(rawBlurPx.coerceIn(1, 80))
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.setBlurBehindRadius(0)
        }
        runCatching { wm.updateViewLayout(view, params) }
        return canNativeBlur
    }

    fun setInputActive(active: Boolean, requestRootFocus: Boolean = true) {
        runOnMain {
            val wm = windowManager ?: return@runOnMain
            val view = composeViewRef ?: return@runOnMain
            val params = layoutParams ?: return@runOnMain
            params.flags = if (active) {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() and
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
            } else {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            runCatching { wm.updateViewLayout(view, params) }
            if (active) {
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                if (requestRootFocus) {
                    view.requestFocus()
                }
            } else {
                view.clearFocus()
            }
        }
    }

    fun setAltFocusableIm(enabled: Boolean) {
        runOnMain {
            val wm = windowManager ?: return@runOnMain
            val view = composeViewRef ?: return@runOnMain
            val params = layoutParams ?: return@runOnMain
            params.flags = if (enabled) {
                params.flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            } else {
                params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
            }
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    fun dismissAnimated(
        hideDelayMs: Long = 300L,
        onHidden: () -> Unit = {}
    ) {
        runOnMain {
            setInputActive(false)
            val view = composeViewRef
            val currentOwner = ownerRef
            if (view == null || currentOwner == null) {
                onHidden()
                return@runOnMain
            }
            currentOwner.lifecycleScope.launch(Dispatchers.Main) {
                delay(hideDelayMs)
                destroy()
                onHidden()
            }
        }
    }

    fun destroy() {
        runOnMain {
            setInputActive(false)
            val currentOwner = ownerRef
            val view = composeViewRef
            val wm = windowManager
            if (currentOwner == null || view == null || wm == null) return@runOnMain

            runCatching { wm.removeView(view) }
            screenOffDismissReceiver.unregister()
            view.post { currentOwner.destroy() }

            composeViewRef = null
            ownerRef = null
            layoutParams = null
            windowManager = null
            appContext = null
        }
    }
}
