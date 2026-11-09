package com.slideindex.app.overlay



import android.content.Context

import android.graphics.PixelFormat


import android.os.Handler

import android.os.Looper

import android.util.Log

import android.view.Gravity

import android.view.KeyEvent

import android.view.WindowManager

import androidx.compose.runtime.Composable

import androidx.compose.ui.platform.ComposeView






class OverlayComposeDialogHost(

    context: Context,

    private val applyTheme: @Composable (@Composable () -> Unit) -> Unit = { content ->
        val wrapper = OverlayHostRuntime.themeWrapper
        if (wrapper != null) wrapper.wrap(content) else content()
    },

    private val fullScreen: Boolean = true

) {

    private val appContext: android.app.Application =
        context.applicationContext as android.app.Application

    private fun themedContext(): Context = OverlayCompose.themedContext(appContext)

    private val mainHandler = Handler(Looper.getMainLooper())



    private var owner: OverlayComposeOwner? = null

    private var composeView: ComposeView? = null

    private var layoutParams: WindowManager.LayoutParams? = null

    private var detachedFromWindow = false

    private var backPressedHandler: (() -> Boolean)? = null

    private var onDismissCallback: (() -> Unit)? = null



    val isShowing: Boolean get() = composeView != null



    fun show(

        onBackPressed: (() -> Boolean)? = null,

        onDismiss: (() -> Unit)? = null,

        content: @Composable () -> Unit

    ) {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { show(onBackPressed, onDismiss, content) }

            return

        }

        val windowManager = windowManager() ?: return

        dismiss()

        backPressedHandler = onBackPressed

        onDismissCallback = onDismiss

        val dialogOwner = OverlayComposeOwner()

        owner = dialogOwner

        val view = OverlayCompose.createComposeView(themedContext(), dialogOwner).apply {
            isFocusable = true
            isFocusableInTouchMode = true

            setOnKeyListener { _, keyCode, event ->

                if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {

                    return@setOnKeyListener false

                }

                if (backPressedHandler?.invoke() == true) {

                    true

                } else {

                    dismiss()

                    true

                }

            }

            setContent {

                applyTheme(content)

            }

        }

        val params = buildLayoutParams()

        val added = runCatching {

            OverlayWindowGuard.addView(windowManager, view, params, "compose-dialog")

            composeView = view

            layoutParams = params

            detachedFromWindow = false

        }.onFailure { error ->

            Log.e(TAG, "Failed to show overlay dialog", error)

            owner?.destroy()

            owner = null

            backPressedHandler = null

            onDismissCallback = null

        }.isSuccess

        if (!added) return

        view.requestFocus()
    }

    /** Keep the dialog layout updated without destroying ComposeView hierarchy. */
    fun bringToFront() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { bringToFront() }
            return
        }
        val windowManager = windowManager() ?: return
        val view = composeView ?: return
        val params = layoutParams ?: return
        if (detachedFromWindow) return
        runCatching {
            windowManager.updateViewLayout(view, params)
            // IME 弹出会触发 syncOverlayDialogZOrder → bringToFront；若子 View（如搜索框）
            // 已持有焦点，根 View 再 requestFocus 会抢走焦点并立刻收起输入法。
            val focused = view.findFocus()
            if (focused == null || focused === view) {
                view.requestFocus()
            }
        }.onFailure { error -> Log.e(TAG, "Failed to bring overlay dialog to front", error) }
    }



    fun requestFocus() {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { requestFocus() }

            return

        }

        composeView?.requestFocus()

    }



    fun dismiss() {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { dismiss() }

            return

        }

        val view = composeView ?: return

        windowManager()?.let { wm ->

            runCatching { wm.removeView(view) }

        }

        owner?.destroy()

        composeView = null

        layoutParams = null

        detachedFromWindow = false
        view.requestFocus()
        owner = null

        backPressedHandler = null

        val dismissCb = onDismissCallback

        onDismissCallback = null

        dismissCb?.invoke()

    }



    /** Detach overlay from [WindowManager] while keeping Compose state (visibility alone is unreliable). */

    fun suspendFromWindow() {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { suspendFromWindow() }

            return

        }

        val view = composeView ?: return

        if (detachedFromWindow) return

        windowManager()?.let { wm ->

            runCatching { wm.removeView(view) }

                .onFailure { error -> Log.e(TAG, "Failed to suspend overlay dialog", error) }

        }

        detachedFromWindow = true

    }



    fun restoreToWindow() {

        if (Looper.myLooper() != Looper.getMainLooper()) {

            mainHandler.post { restoreToWindow() }

            return

        }

        val windowManager = windowManager() ?: return

        val view = composeView ?: return

        val params = layoutParams ?: return

        if (!detachedFromWindow) return

        runCatching {

            OverlayWindowGuard.addView(windowManager, view, params, "compose-dialog-restore")

        }.onFailure { error -> Log.e(TAG, "Failed to restore overlay dialog", error) }

        detachedFromWindow = false

    }



    @Deprecated("Use suspendFromWindow / restoreToWindow", ReplaceWith("suspendFromWindow()"))

    fun setVisible(visible: Boolean) {

        if (visible) restoreToWindow() else suspendFromWindow()

    }



    private fun buildLayoutParams(): WindowManager.LayoutParams {

        val host = overlayHostContext() ?: appContext

        return WindowManager.LayoutParams(

            if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,

            if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,

            OverlayWindowTypes.overlayWindowType(host),

            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,

            PixelFormat.TRANSLUCENT

        ).apply {

            gravity = if (fullScreen) Gravity.TOP or Gravity.START else Gravity.CENTER

            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        }

    }



    private fun overlayHostContext(): Context? = OverlayHostRuntime.accessibilityHostContext?.invoke()



    private fun windowManager(): WindowManager? {

        val host = overlayHostContext() ?: run {

            Log.e(TAG, "Cannot show overlay dialog: accessibility service not connected")

            return null

        }

        return host.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    }



    companion object {

        private const val TAG = "OverlayComposeDialogHost"

    }

}


