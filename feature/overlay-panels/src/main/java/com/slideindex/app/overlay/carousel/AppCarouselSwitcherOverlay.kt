package com.slideindex.app.overlay.carousel

import com.slideindex.app.overlay.overlayHostContext

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.PermissionHelper
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 全新独立应用切换器悬浮窗控制器：
 * 管理 AppCarouselSwitcherView 悬浮窗添加、移除与手势生命周期。
 */
@SuppressLint("StaticFieldLeak")
object AppCarouselSwitcherOverlay {
    private const val TAG = "AppCarouselSwitcher"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeViewRef: WeakReference<AppCarouselSwitcherView>? = null
    private var windowManager: WindowManager? = null
    private var externalTracking = false
    /** 浏览模式在边滑会话结束后保持浮层。 */
    private var persistAfterSessionEnd = false

    val isShowing: Boolean
        get() = activeViewRef?.get() != null

    @SuppressLint("RtlHardcoded")
    fun show(
        context: Context,
        settings: AppSettings,
        anchorX: Float,
        anchorY: Float,
        externalTracking: Boolean = true,
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = CountDownLatch(1)
            mainHandler.post {
                result = show(context, settings, anchorX, anchorY, externalTracking)
                latch.countDown()
            }
            runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
            return result
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

        dismiss()

        val wm = hostContext.getSystemService(WindowManager::class.java)
            ?: run {
                Log.w(TAG, "show: WindowManager unavailable")
                return false
            }
        windowManager = wm

        val view = AppCarouselSwitcherView(hostContext) {
            dismiss()
        }
        view.configure(settings, anchorX, anchorY)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayWindowTypes.fullscreenLauncherWindowType(hostContext),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        return try {
            wm.addView(view, params)
            activeViewRef = WeakReference(view)
            this.externalTracking = externalTracking
            persistAfterSessionEnd = !externalTracking
            if (externalTracking) {
                view.onExternalMove(anchorX, anchorY)
            } else {
                enableDirectTouch()
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "show: addView failed", t)
            releaseOverlayState()
            false
        }
    }

    fun onExternalMove(rawX: Float, rawY: Float) {
        activeViewRef?.get()?.onExternalMove(rawX, rawY)
    }

    fun confirmContinuousRelease(rawX: Float, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { confirmContinuousRelease(rawX, rawY) }
            return
        }
        onExternalMove(rawX, rawY)
        onExternalUp(rawX, rawY, false)
    }

    fun onExternalUp(rawX: Float, rawY: Float, cancelled: Boolean) {
        activeViewRef?.get()?.onExternalUp(rawX, rawY, cancelled)
    }

    fun onExternalCancel() {
        activeViewRef?.get()?.onExternalCancel()
    }

    fun enableDirectTouch() {
        val view = activeViewRef?.get() ?: return
        val wm = windowManager ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        runCatching { wm.updateViewLayout(view, params) }
    }

    fun onGestureSessionEnd() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onGestureSessionEnd() }
            return
        }
        if (persistAfterSessionEnd) return
        dismiss()
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        val view = activeViewRef?.get()
        if (view != null && windowManager != null) {
            runCatching {
                windowManager?.removeViewImmediate(view)
            }
        }
        releaseOverlayState()
    }

    private fun releaseOverlayState() {
        activeViewRef = null
        windowManager = null
        externalTracking = false
        persistAfterSessionEnd = false
    }
}
