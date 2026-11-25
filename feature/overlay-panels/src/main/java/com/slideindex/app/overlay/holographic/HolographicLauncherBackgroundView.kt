package com.slideindex.app.overlay.holographic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.slideindex.app.overlay.LocalFrostedGlassDrawable
import com.slideindex.app.overlay.OverlayMaterialSpec
import com.slideindex.app.settings.HolographicLauncherSettings

internal class HolographicLauncherBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val frostedGlassDrawable = LocalFrostedGlassDrawable(this)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fullBounds = RectF()
    private var released = false

    private val density: Float
        get() = resources.displayMetrics.density

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
        frostedGlassDrawable.reset()
        scheduleBlurRefresh()
    }

    private fun scheduleBlurRefresh() {
        if (released) return
        blurRefreshRunnable.arm(4)
        postOnAnimation(blurRefreshRunnable)
    }

    fun release() {
        if (released) return
        released = true
        removeCallbacks(blurRefreshRunnable)
        frostedGlassDrawable.reset()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        fullBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        val spec = OverlayMaterialSpec.fromContext(context)
        if (spec.backdropBlurEnabled) {
            val blurPx = spec.backdropBlurRadiusPx(density)
            frostedGlassDrawable.draw(
                canvas = canvas,
                bounds = fullBounds,
                cornerRadiusPx = 0f,
                blurRadiusPx = blurPx,
                tintColor = Color.TRANSPARENT,
                alpha = 1f,
            )
        }
        val dimColor = spec.backdropDimColor(alphaScale = 1f)
        if (Color.alpha(dimColor) > 0) {
            backgroundPaint.color = dimColor
            canvas.drawRect(fullBounds, backgroundPaint)
        }
    }
}
