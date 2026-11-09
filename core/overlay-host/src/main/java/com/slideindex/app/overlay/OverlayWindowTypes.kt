package com.slideindex.app.overlay

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager

/**
 * WindowManager overlay types and flags aligned with SideGesture (gulugulu):
 * prefer [TYPE_ACCESSIBILITY_OVERLAY] when the accessibility service is active.
 */
object OverlayWindowTypes {

    /** Prevent overlay windows from overriding system brightness (OEM bugs if unset or re-added). */
    fun ensureNoBrightnessOverride(params: WindowManager.LayoutParams) {
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        params.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }

    fun overlayWindowType(context: Context): Int {
        val a11yHost = OverlayHostRuntime.accessibilityHostContext?.invoke()
        val base = unwrapOverlayHost(context)
        val hostBase = a11yHost?.let { unwrapOverlayHost(it) }
        val isA11yContext = base is android.accessibilityservice.AccessibilityService ||
            (hostBase != null && base == hostBase)
        val a11yEnabled = OverlayHostRuntime.isAccessibilityServiceEnabled?.invoke(context) ?: false
        return if (isA11yContext && a11yEnabled) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
    }

    /** 剥除 [ContextThemeWrapper] / [android.window.WindowContext] 包装，用于窗口类型判定。 */
    internal fun unwrapOverlayHost(context: Context): Context {
        var current = context
        while (current is ContextThemeWrapper) {
            current = current.baseContext
        }
        if (current.javaClass.name == "android.window.WindowContext") {
            val base = (current as? ContextWrapper)?.baseContext
            if (base != null) {
                current = base
            }
        }
        return current
    }

    /**
     * FV 风格圆环启动器及全屏启动器面板：
     * 优先使用 [TYPE_APPLICATION_OVERLAY]，彻底避免部分 OEM（如 Meizu Flyme）对 [TYPE_ACCESSIBILITY_OVERLAY] 窗口强制叠加 80% alpha（0.7998047）导致面板背景发灰、图标发虚半透明的底层系统限制。
     */
    fun fullscreenLauncherWindowType(context: Context): Int {
        if (android.provider.Settings.canDrawOverlays(context)) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        return overlayWindowType(context)
    }

    /**
     * 取词/搜图/搜索等内容面板：固定 [TYPE_APPLICATION_OVERLAY]，
     * 使 [overlayWindowType] 的 chrome（悬浮球、边缘触钮）稳定叠在面板之上。
     */
    fun contentPanelWindowType(@Suppress("UNUSED_PARAMETER") context: Context): Int =
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    /**
     * 边缘捕获/触钮视觉等小块 overlay：统一使用 [overlayWindowType]，
     * 避免因 Window Type 差异导致 TYPE_ACCESSIBILITY_OVERLAY 面板强行压在 TYPE_APPLICATION_OVERLAY 悬浮球上方。
     */
    fun captureOverlayWindowType(context: Context): Int = overlayWindowType(context)

    fun createCaptureParams(
        context: Context,
        title: String? = null,
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            captureOverlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).also {
            title?.let { name -> it.title = name }
            ensureNoBrightnessOverride(it)
            it.flags = it.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            it.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            applyCaptureTouchFlags(it)
        }
    }

    fun createPresentationParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).also {
            ensureNoBrightnessOverride(it)
            applyPresentationPassthroughFlags(it)
        }
    }

    fun applyFullScreen(params: WindowManager.LayoutParams) {
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    fun applyCaptureTouchFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    fun applyPresentationPassthroughFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    fun applyPresentationInteractiveFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    fun applyPreviewPresentationFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    fun applyExclusionPassthroughFlags(params: WindowManager.LayoutParams) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        params.flags = params.flags or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }
}
