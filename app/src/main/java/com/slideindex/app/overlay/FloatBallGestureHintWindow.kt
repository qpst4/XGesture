package com.slideindex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.isVisible
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.ui.gestureActionImageVector
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlin.math.roundToInt

/**
 * FV-style 24dp gesture hint icon during float-ball drag.
 * Positioned away from the finger so it stays visible while dragging.
 */
internal class FloatBallGestureHintWindow {
    companion object {
        const val HINT_SIZE_DP = 24f
        const val HINT_OFFSET_DP = 48f

        fun hintTopLeftForFingerPx(
            fingerX: Float,
            fingerY: Float,
            dockSide: FloatBallSide,
            hintSizePx: Int,
            gapPx: Int
        ): Pair<Int, Int> {
            val x = when (dockSide) {
                FloatBallSide.LEFT -> (fingerX + gapPx).roundToInt()
                FloatBallSide.RIGHT -> (fingerX - hintSizePx - gapPx).roundToInt()
            }
            val y = (fingerY - hintSizePx - gapPx).roundToInt()
            return x to y
        }
    }

    private var hostContext: Context? = null
    private var windowManager: WindowManager? = null
    private var hintView: ComposeView? = null
    private var hintParams: WindowManager.LayoutParams? = null
    private var owner: OverlayComposeOwner? = null
    private val visibleState: MutableState<Boolean> = mutableStateOf(false)
    private val iconState: MutableState<ImageVector?> = mutableStateOf(null)
    private val tintArgbState = mutableIntStateOf(0xFF6750A4.toInt())
    private var lastAppliedAction: GestureAction? = null
    private var lastAppliedTintArgb: Int? = null
    private var lastAppliedX: Int? = null
    private var lastAppliedY: Int? = null
    private var lastAppliedSizePx: Int? = null

    fun attach(context: Context, wm: WindowManager) {
        hostContext = context
        windowManager = wm
    }

    private fun ensureHintWindow(context: Context, wm: WindowManager): Pair<ComposeView, WindowManager.LayoutParams>? {
        val existingView = hintView
        val existingParams = hintParams
        if (existingView != null && existingParams != null) {
            return existingView to existingParams
        }
        val overlayContext = OverlayCompose.themedContext(context)
        val dialogOwner = OverlayComposeOwner()
        val composeView = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                OverlayAwareModuleTheme {
                    FloatBallGestureHintContent(
                        visibleState = visibleState,
                        iconState = iconState,
                        tintArgbState = tintArgbState
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val added = runCatching { wm.addView(composeView, params) }.isSuccess
        if (!added) {
            OverlayCompose.teardownOverlayCompose(composeView, dialogOwner)
            return null
        }
        hintView = composeView
        hintParams = params
        owner = dialogOwner
        return composeView to params
    }

    fun update(
        action: GestureAction,
        /** 主题强调色（见 `resolveAppAccentArgb`）：调用方解析好再传，窗口本身不认设置。 */
        accentArgb: Int,
        fingerX: Float,
        fingerY: Float,
        dockSide: FloatBallSide,
        density: Float
    ) {
        val context = hostContext ?: return
        val wm = windowManager ?: return
        val (view, params) = ensureHintWindow(context, wm) ?: return
        val hintSizePx = (HINT_SIZE_DP * density).roundToInt()
        val gapPx = (HINT_OFFSET_DP * density).roundToInt()
        val (x, y) = hintTopLeftForFingerPx(
            fingerX = fingerX,
            fingerY = fingerY,
            dockSide = dockSide,
            hintSizePx = hintSizePx,
            gapPx = gapPx
        )
        val layoutUnchanged = lastAppliedAction == action &&
            lastAppliedTintArgb == accentArgb &&
            lastAppliedX == x &&
            lastAppliedY == y &&
            lastAppliedSizePx == hintSizePx &&
            view.isVisible &&
            visibleState.value
        if (layoutUnchanged) return

        val icon = gestureActionImageVector(action)
        if (iconState.value != icon) {
            iconState.value = icon
        }
        if (tintArgbState.intValue != accentArgb) {
            tintArgbState.intValue = accentArgb
        }
        if (!visibleState.value) {
            visibleState.value = true
        }
        lastAppliedAction = action
        lastAppliedTintArgb = accentArgb
        lastAppliedX = x
        lastAppliedY = y
        lastAppliedSizePx = hintSizePx

        val layoutParamsChanged = params.width != hintSizePx ||
            params.height != hintSizePx ||
            params.x != x ||
            params.y != y
        val wasVisible = view.isVisible
        params.width = hintSizePx
        params.height = hintSizePx
        params.x = x
        params.y = y
        if (!wasVisible) {
            view.visibility = View.VISIBLE
        }
        if (layoutParamsChanged || !wasVisible) {
            runCatching { wm.updateViewLayout(view, params) }
        }
    }

    fun hide() {
        if (!visibleState.value && hintView == null) {
            return
        }
        visibleState.value = false
        iconState.value = null
        lastAppliedAction = null
        lastAppliedTintArgb = null
        lastAppliedX = null
        lastAppliedY = null
        lastAppliedSizePx = null
        val view = hintView
        val dialogOwner = owner
        val wm = windowManager
        if (view != null) {
            if (wm != null && view.isAttachedToWindow) {
                runCatching { wm.removeView(view) }
            }
            OverlayCompose.teardownOverlayCompose(view, dialogOwner)
        }
        hintView = null
        hintParams = null
        owner = null
    }

    fun bringToFront() {
        val view = hintView ?: return
        val params = hintParams ?: return
        val wm = windowManager ?: return
        if (!view.isAttachedToWindow) return
        runCatching {
            wm.removeView(view)
            wm.addView(view, params)
        }
    }

    fun detach() {
        hide()
        windowManager = null
        hostContext = null
    }
}

@Composable
private fun FloatBallGestureHintContent(
    visibleState: MutableState<Boolean>,
    iconState: MutableState<ImageVector?>,
    tintArgbState: IntState
) {
    val visible by visibleState
    val icon = iconState.value
    val tint = Color(tintArgbState.intValue)
    if (!visible || icon == null) return
    Box(
        modifier = Modifier
            .size(FloatBallGestureHintWindow.HINT_SIZE_DP.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint
        )
    }
}
