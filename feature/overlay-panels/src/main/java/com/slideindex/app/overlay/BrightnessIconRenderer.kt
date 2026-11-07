package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.withRotation
import com.slideindex.app.overlay.panels.R
import kotlin.math.roundToInt

object BrightnessIconRenderer {
    private var darkOn: Drawable? = null
    private var darkOff: Drawable? = null

    fun drawAutoBrightnessIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sizePx: Float,
        enabled: Boolean,
        alphaScale: Float
    ) {
        drawAutoSunIcon(
            canvas = canvas,
            cx = cx,
            cy = cy,
            sizePx = sizePx,
            enabled = enabled,
            alphaScale = alphaScale
        )
    }

    private fun drawAutoSunIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sizePx: Float,
        enabled: Boolean,
        alphaScale: Float
    ) {
        val alpha = alphaScale.coerceIn(0f, 1f)
        val iconAlpha = (255f * alpha).roundToInt()
        val mutedAlpha = (165f * alpha).roundToInt()

        val coreRadius = sizePx * 0.235f
        val rayGap = sizePx * 0.055f
        val rayLength = sizePx * 0.105f
        val rayWidth = sizePx * 0.072f
        val rayCorner = rayWidth * 0.42f
        val rayMidRadius = coreRadius + rayGap + rayLength / 2f

        val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (enabled) {
                Color.argb(iconAlpha, 255, 255, 255)
            } else {
                Color.argb(mutedAlpha, 210, 214, 222)
            }
            style = Paint.Style.FILL
        }

        for (index in 0 until 8) {
            val angleRad = Math.toRadians(index * 45.0 - 90.0)
            val rayCx = cx + kotlin.math.cos(angleRad).toFloat() * rayMidRadius
            val rayCy = cy + kotlin.math.sin(angleRad).toFloat() * rayMidRadius
            val rayRect = RectF(
                rayCx - rayWidth / 2f,
                rayCy - rayLength / 2f,
                rayCx + rayWidth / 2f,
                rayCy + rayLength / 2f
            )
            canvas.withRotation(index * 45f, rayCx, rayCy) {
                drawRoundRect(rayRect, rayCorner, rayCorner, rayPaint)
            }
        }

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (enabled) {
                Color.argb(iconAlpha, 255, 255, 255)
            } else {
                Color.argb(mutedAlpha, 210, 214, 222)
            }
        }
        canvas.drawCircle(cx, cy, coreRadius, corePaint)

        val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textSize = coreRadius * 1.08f
            color = if (enabled) {
                Color.argb(iconAlpha, 255, 184, 0)
            } else {
                Color.argb(mutedAlpha, 245, 247, 250)
            }
            isFakeBoldText = true
        }
        val fontMetrics = letterPaint.fontMetrics
        val letterBaseline = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText("A", cx, letterBaseline, letterPaint)
    }

    fun drawDarkMode(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sizePx: Float,
        darkModeEnabled: Boolean,
        alphaScale: Float
    ) {
        ensureDarkIcons(context)
        drawIcon(
            drawable = if (darkModeEnabled) darkOff else darkOn,
            canvas = canvas,
            cx = cx,
            cy = cy,
            sizePx = sizePx,
            alphaScale = alphaScale
        )
    }

    private fun drawIcon(
        drawable: Drawable?,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sizePx: Float,
        alphaScale: Float
    ) {
        val icon = drawable ?: return
        val alpha = (235f * alphaScale.coerceIn(0f, 1f)).roundToInt()
        DrawableCompat.setTint(icon, Color.argb(alpha, 255, 255, 255))
        val half = (sizePx / 2f).roundToInt()
        val left = (cx - half).roundToInt()
        val top = (cy - half).roundToInt()
        icon.setBounds(left, top, left + half * 2, top + half * 2)
        icon.draw(canvas)
    }

    private fun ensureDarkIcons(context: Context) {
        if (darkOn != null) return
        val appContext = context.applicationContext
        darkOn = load(appContext, R.drawable.ic_dark_mode)
        darkOff = load(appContext, R.drawable.ic_light_mode)
    }

    private fun load(context: Context, resId: Int): Drawable? =
        ContextCompat.getDrawable(context, resId)?.constantState?.newDrawable()?.mutate()
}
