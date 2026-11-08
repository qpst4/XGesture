package com.slideindex.app.overlay.volumepanel

import com.slideindex.app.overlay.overlayHostContext

/**
 * Portions derived from EdgeGesture (https://github.com/evilgodxu/EdgeGesture)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayViewBackHandler
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.rememberOverlayImeBottomHeight
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.PermissionHelper

private const val VOLUME_PANEL_SCRIM_ANIM_MS = 220
/** Spring 退场略长于 scrim，避免窗口过早移除。 */
private const val VOLUME_PANEL_DISMISS_MS = 380L
private val panelSlideSpringSpec = spring<IntOffset>(dampingRatio = 0.8f, stiffness = 300f)

object VolumePanelOverlayWindow {
    private const val TAG = "VolumePanelOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var composeViewRef = java.lang.ref.WeakReference<ComposeView>(null)
    private var composeView: ComposeView?
        get() = composeViewRef.get()
        set(value) {
            composeViewRef = java.lang.ref.WeakReference(value)
        }
    private var owner: OverlayComposeOwner? = null
    private var panelVisibilityState: MutableTransitionState<Boolean>? = null
    private var screenOffUnregister: (() -> Unit)? = null
    private var backHandlerRef = java.lang.ref.WeakReference<OverlayViewBackHandler>(null)
    private var backHandler: OverlayViewBackHandler?
        get() = backHandlerRef.get()
        set(value) {
            backHandlerRef = java.lang.ref.WeakReference(value)
        }
    private var bringAboveToken = 0
    private var dismissToken = 0

    val isShowing: Boolean
        get() = composeView != null && (panelVisibilityState?.targetState == true)

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
        if (isShowing) {
            return true
        }
        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }
        val hostContext = overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                return false
            }
        ensureWindow(hostContext)
        ++dismissToken
        panelVisibilityState?.targetState = true
        screenOffUnregister = com.slideindex.app.overlay.panels.PanelRuntimeHooks.registerScreenOffDismiss
            ?.invoke(hostContext) { dismiss() }
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.onContentPanelShown?.invoke()
        scheduleBringFloatBallAbovePanels()
        composeView?.requestFocus()
        return true
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        if (composeView == null) return
        val token = ++dismissToken
        panelVisibilityState?.targetState = false
        screenOffUnregister?.invoke()
        screenOffUnregister = null
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.onContentPanelHidden?.invoke()
        ++bringAboveToken
        mainHandler.postDelayed({
            if (token != dismissToken) return@postDelayed
            if (panelVisibilityState?.targetState == true) return@postDelayed
            cleanup()
        }, VOLUME_PANEL_DISMISS_MS)
    }

    fun handleBack(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    private fun scheduleBringFloatBallAbovePanels() {
        val token = ++bringAboveToken
        fun attempt() {
            if (token != bringAboveToken) return
            com.slideindex.app.overlay.panels.PanelFloatBallHooks.scheduleChromeAbovePanels?.invoke(0L)
        }
        attempt()
        composeView?.post {
            attempt()
            composeView?.postOnAnimation { attempt() }
        }
        mainHandler.postDelayed({ attempt() }, 200)
        mainHandler.postDelayed({ attempt() }, 800)
    }

    private fun ensureWindow(context: Context) {
        if (composeView != null) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val visibilityState = MutableTransitionState(false)
        panelVisibilityState = visibilityState
        val overlayContext = OverlayCompose.themedContext(context)
        val composeOwner = OverlayComposeOwner()
        owner = composeOwner

        val view = OverlayCompose.createComposeView(overlayContext, composeOwner).apply {
            setContent {
                com.slideindex.app.overlay.panels.PanelRuntimeHooks.appDependenciesWrapper
                    ?.content {
                        OverlayAwareModuleTheme {
                            VolumePanelOverlayRoot(
                                visibilityState = visibilityState,
                                onDismiss = { dismiss() },
                            )
                        }
                    } ?: OverlayAwareModuleTheme {
                        VolumePanelOverlayRoot(
                            visibilityState = visibilityState,
                            onDismiss = { dismiss() },
                        )
                    }
            }
        }
        composeView = view
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.contentPanelWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = TAG
        }
        backHandler = OverlayViewBackHandler(view) { handleBack() }.also { it.attach() }
        wm.addView(view, params)
    }

    private fun cleanup() {
        if (panelVisibilityState?.targetState == true) return
        val view = composeView
        val wm = windowManager
        backHandler?.detach()
        backHandler = null
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
        val dialogOwner = owner
        owner = null
        composeView = null
        OverlayCompose.teardownOverlayCompose(view, dialogOwner)
        windowManager = null
        panelVisibilityState = null
    }
}

@Composable
private fun VolumePanelOverlayRoot(
    visibilityState: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
) {
    val scrimAnimSpec = tween<Float>(VOLUME_PANEL_SCRIM_ANIM_MS)
    val dismissInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }
    // overlay 窗里 WindowInsets.ime 常常是 0，必须用这个（与历史面板同一条理由）。
    // 面板固定在屏幕底部，键盘弹起会把下半截（搜索框 / 槽位列表）盖住：
    // 这里整体抬起 IME 的高度，列表那一层再按同一数值收短（见 ExpandPanelSlotPicker 的 listHeight）。
    val imeBottom = rememberOverlayImeBottomHeight()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = fadeIn(scrimAnimSpec),
            exit = fadeOut(scrimAnimSpec),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = fadeIn(scrimAnimSpec) + slideInVertically(panelSlideSpringSpec) { it },
            exit = fadeOut(scrimAnimSpec) + slideOutVertically(panelSlideSpringSpec) { it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp + imeBottom)
                    .clickable(
                        interactionSource = panelInteraction,
                        indication = null,
                        onClick = {},
                    ),
            ) {
                VolumePanelContent(onDismiss = onDismiss, imeBottom = imeBottom)
            }
        }
    }
}
