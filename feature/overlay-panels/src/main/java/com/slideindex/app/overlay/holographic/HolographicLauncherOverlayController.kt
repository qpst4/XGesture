package com.slideindex.app.overlay.holographic

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.view.Gravity
import android.view.WindowManager
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.settings.HolographicLauncherSettings

class HolographicLauncherOverlayController(
    private val context: Context,
    private val mainHandler: Handler,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: HolographicLauncherOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    val isVisible: Boolean get() = view != null

    fun show(
        apps: List<HolographicLauncherApp>,
        settings: HolographicLauncherSettings,
        listener: HolographicLauncherOverlayView.Listener,
    ): Boolean {
        removeNow()
        if (windowManager == null || apps.isEmpty()) return false

        val usesNativeWindowBlur = resolveNativeWindowBlur(settings)
        val overlayView = HolographicLauncherOverlayView(context, mainHandler)
        overlayView.bind(
            apps = apps,
            settings = settings,
            usesNativeWindowBlur = usesNativeWindowBlur,
            listener = listener,
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
            if (usesNativeWindowBlur && settings.blurDp > 0) {
                val rawBlurPx = (settings.blurDp * context.resources.displayMetrics.density).toInt()
                val clampedBlurPx = rawBlurPx.coerceIn(1, 80)
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                setBlurBehindRadius(clampedBlurPx)
            }
            gravity = Gravity.TOP or Gravity.START
            setFitInsetsTypes(0)
            title = "SlideIndexHolographicLauncher"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        return try {
            windowManager.addView(overlayView, params)
            view = overlayView
            layoutParams = params
            if (usesNativeWindowBlur) {
                overlayView.post {
                    val attachedView = view ?: return@post
                    val attachedParams = layoutParams ?: return@post
                    runCatching { windowManager.updateViewLayout(attachedView, attachedParams) }
                    attachedView.refreshBackgroundBlur()
                }
            } else {
                overlayView.post { view?.refreshBackgroundBlur() }
            }
            true
        } catch (_: Throwable) {
            overlayView.release()
            false
        }
    }

    fun dismiss() {
        view?.close()
    }

    /** 后台补齐图标后重排球面（见 [HolographicLauncherOverlayView.refreshAppIcons]）。 */
    fun refreshAppIcons(apps: List<HolographicLauncherApp>) {
        view?.refreshAppIcons(apps)
    }

    fun removeNow() {
        val current = view
        if (current == null) return
        current.release()
        runCatching {
            windowManager?.removeView(current)
        }
        view = null
        layoutParams = null
    }

    private fun resolveNativeWindowBlur(settings: HolographicLauncherSettings): Boolean {
        if (settings.backgroundStyle != HolographicLauncherSettings.BACKGROUND_BLUR || settings.blurDp <= 0) {
            return false
        }
        return runCatching { windowManager?.isCrossWindowBlurEnabled == true }.getOrDefault(false)
    }
}
