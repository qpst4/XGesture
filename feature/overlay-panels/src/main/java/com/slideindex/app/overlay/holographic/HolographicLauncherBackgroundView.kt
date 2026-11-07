package com.slideindex.app.overlay.holographic

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.slideindex.app.overlay.LocalFrostedGlassDrawable
import com.slideindex.app.overlay.SystemWallpaperBlurHelper
import com.slideindex.app.settings.HolographicLauncherSettings

internal class HolographicLauncherBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val frostedGlassDrawable = LocalFrostedGlassDrawable(this)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wallpaperPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wallpaperBounds = RectF()
    private val fullBounds = RectF()

    private var backgroundStyle = HolographicLauncherSettings.DEFAULT_BACKGROUND_STYLE
    private var blurDp = HolographicLauncherSettings.DEFAULT_BLUR_DP
    private var dimPercent = HolographicLauncherSettings.DEFAULT_DIM_PERCENT
    private var usesNativeWindowBlur = false
    private var frostedGlassDrawSucceeded = false
    private var wallpaper: Bitmap? = null
    private var released = false

    private val density: Float
        get() = resources.displayMetrics.density

    private val wallpaperCallback = com.slideindex.app.overlay.panels.WallpaperBlurCallback { bitmap ->
        post {
            if (released || backgroundStyle != HolographicLauncherSettings.BACKGROUND_BLUR) {
                return@post
            }
            if (bitmap != null && bitmap.isRecycled) return@post
            wallpaper = bitmap
            invalidate()
        }
    }

    private val systemWallpaperLoad = Runnable {
        if (released || backgroundStyle != HolographicLauncherSettings.BACKGROUND_WALLPAPER_BLUR) return@Runnable
        val blur = blurDp
        val app = context.applicationContext ?: context
        Thread({
            val blurred = SystemWallpaperBlurHelper.loadBlurredSync(app, blur)
            post {
                if (released || backgroundStyle != HolographicLauncherSettings.BACKGROUND_WALLPAPER_BLUR) return@post
                wallpaper = blurred
                invalidate()
            }
        }, "holographic-wallpaper").start()
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        frostedGlassDrawable.reset()
        scheduleBlurRefresh()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blurRefreshRunnable)
        super.onDetachedFromWindow()
    }

    fun applySettings(settings: HolographicLauncherSettings, usesNativeWindowBlur: Boolean) {
        this.backgroundStyle = settings.backgroundStyle
        this.blurDp = settings.blurDp
        this.dimPercent = settings.dimPercent
        this.usesNativeWindowBlur = usesNativeWindowBlur
        frostedGlassDrawSucceeded = false
        loadWallpaperIfNeeded()
        if (isAttachedToWindow) {
            frostedGlassDrawable.reset()
            scheduleBlurRefresh()
        }
    }

    private val blurRefreshRunnable = object : Runnable {
        private var remainingFrames = 0

        fun arm(frames: Int) {
            remainingFrames = frames
        }

        override fun run() {
            if (released || !isAttachedToWindow) return
            invalidate()
            remainingFrames -= 1
            if (remainingFrames > 0) {
                postOnAnimation(this)
            }
        }
    }

    fun refreshBlur() {
        if (released) return
        frostedGlassDrawSucceeded = false
        frostedGlassDrawable.reset()
        loadWallpaperIfNeeded()
        scheduleBlurRefresh()
    }

    private fun scheduleBlurRefresh() {
        if (released || backgroundStyle != HolographicLauncherSettings.BACKGROUND_BLUR) return
        blurRefreshRunnable.arm(4)
        postOnAnimation(blurRefreshRunnable)
    }

    fun release() {
        if (released) return
        released = true
        removeCallbacks(systemWallpaperLoad)
        removeCallbacks(blurRefreshRunnable)
        wallpaper = null
        frostedGlassDrawable.reset()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        fullBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        if (backgroundStyle == HolographicLauncherSettings.BACKGROUND_BLUR) {
            val blurPx = (blurDp * density).toInt().coerceIn(1, 120)
            val drewFrosted = frostedGlassDrawable.draw(
                canvas = canvas,
                bounds = fullBounds,
                cornerRadiusPx = 0f,
                blurRadiusPx = blurPx,
                tintColor = Color.TRANSPARENT,
                alpha = 1f,
            )
            if (drewFrosted) {
                frostedGlassDrawSucceeded = true
            }
        }
        val needsBitmapFallback = backgroundStyle == HolographicLauncherSettings.BACKGROUND_BLUR &&
            (!usesNativeWindowBlur || !frostedGlassDrawSucceeded)
        val drawWallpaperBitmap = wallpaper?.takeUnless { it.isRecycled } != null &&
            (
                backgroundStyle == HolographicLauncherSettings.BACKGROUND_WALLPAPER_BLUR ||
                    needsBitmapFallback
                )
        if (drawWallpaperBitmap) {
            wallpaperPaint.alpha = 255
            wallpaperBounds.set(fullBounds)
            canvas.drawBitmap(wallpaper!!, null, wallpaperBounds, wallpaperPaint)
        }
        val maskAlpha = resolvedMaskAlpha()
        if (maskAlpha > 0) {
            backgroundPaint.color = Color.BLACK
            backgroundPaint.alpha = maskAlpha
            canvas.drawRect(fullBounds, backgroundPaint)
        }
    }

    private fun resolvedMaskAlpha(): Int {
        val clampedDim = dimPercent.coerceIn(
            HolographicLauncherSettings.MIN_DIM_PERCENT,
            HolographicLauncherSettings.MAX_DIM_PERCENT,
        )
        return (255f * clampedDim / 100f).toInt().coerceIn(0, 255)
    }

    private fun loadWallpaperIfNeeded() {
        removeCallbacks(systemWallpaperLoad)
        wallpaper = null
        when (backgroundStyle) {
            HolographicLauncherSettings.BACKGROUND_BLUR -> loadDisplayWallpaper()
            HolographicLauncherSettings.BACKGROUND_WALLPAPER_BLUR -> {
                wallpaper = SystemWallpaperBlurHelper.peekCachedBlurred(context.applicationContext, blurDp)
                if (wallpaper == null || wallpaper?.isRecycled == true) {
                    wallpaper = null
                    post(systemWallpaperLoad)
                }
            }
        }
    }

    private fun loadDisplayWallpaper() {
        val hostContext = context
        if (hostContext !is AccessibilityService) return
        val appContext = hostContext.applicationContext ?: hostContext
        wallpaper = com.slideindex.app.overlay.panels.PanelRuntimeHooks.captureBlurredWallpaper?.invoke(
            hostContext,
            appContext,
            blurDp,
            wallpaperCallback,
        )
        if (wallpaper?.isRecycled == true) {
            wallpaper = null
        }
    }
}
