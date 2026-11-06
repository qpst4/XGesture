package com.slideindex.app.overlay.compositor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.overlay.OverlayWindowTypes

/**
 * Full-screen NOT_TOUCHABLE compositor for overlay visuals that must share a single z-order band.
 * Brightness preview is applied here so presentation windows never lock system brightness.
 */
@SuppressLint("StaticFieldLeak")
object OverlayCompositor {
    private const val TAG = "OverlayCompositor"
    private const val MIN_BRIGHTNESS_PREVIEW = 0.01f

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var rootHost: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var appContext: Context? = null
    private var compositorAttached = false
    private var brightnessPreviewFraction: Float? = null

    fun ensureAttached(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { ensureAttached(context) }
            return
        }
        if (compositorAttached) return

        val hostContext = context.applicationContext
        val wm = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val root = FrameLayout(hostContext).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(hostContext),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "XG:compositor"
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        val added = runCatching { wm.addView(root, params) }
            .onFailure { Log.e(TAG, "ensureAttached: addView failed", it) }
            .isSuccess
        if (!added) return

        windowManager = wm
        rootHost = root
        layoutParams = params
        appContext = hostContext
        compositorAttached = true
        bringCompositorToFront()
        Log.i(TAG, "compositor attached")
    }

    fun detach() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { detach() }
            return
        }
        val wm = windowManager
        val root = rootHost
        if (wm != null && root != null) {
            runCatching { wm.removeView(root) }
        }
        windowManager = null
        rootHost = null
        layoutParams = null
        appContext = null
        compositorAttached = false
        brightnessPreviewFraction = null
    }

    fun setBrightnessPreview(context: Context, fraction: Float?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setBrightnessPreview(context, fraction) }
            return
        }
        brightnessPreviewFraction = null
        clearBrightnessPreview()
    }

    fun clearBrightnessPreview() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { clearBrightnessPreview() }
            return
        }
        brightnessPreviewFraction = null
        val wm = windowManager
        val root = rootHost
        root?.setBackgroundColor(Color.TRANSPARENT)
        if (wm == null || root == null || !compositorAttached) {
            detachIfIdle()
            return
        }
        layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (root.isAttachedToWindow) {
            layoutParams?.let { params ->
                runCatching { wm.updateViewLayout(root, params) }
                    .onFailure { Log.w(TAG, "clearBrightnessPreview failed", it) }
            }
        }
        detachIfIdle()
    }

    fun bringAboveContentPanels() {
        bringCompositorToFront()
    }

    fun bringCompositorToFront() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { bringCompositorToFront() }
            return
        }
        val wm = windowManager ?: return
        val root = rootHost ?: return
        val params = layoutParams ?: return
        if (!root.isAttachedToWindow) return
        runCatching {
            wm.removeView(root)
            wm.addView(root, params)
        }.onFailure { Log.w(TAG, "bringCompositorToFront failed", it) }
    }

    private fun applyBrightnessPreview(fraction: Float) = Unit

    private fun detachIfIdle() {
        if (brightnessPreviewFraction != null) return
        if (!compositorAttached) return
        detach()
    }
}
