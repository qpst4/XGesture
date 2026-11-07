package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.slideindex.app.gesture.TriggerCornerMode
import com.slideindex.app.gesture.TriggerDesignKind
import com.slideindex.app.gesture.TriggerHandleDesign
import kotlin.math.max
import kotlin.math.min

object TriggerHandleRenderer {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()

    fun draw(
        canvas: Canvas,
        side: PanelSide,
        design: TriggerHandleDesign,
        density: Float,
        widthPx: Int,
        heightPx: Int
    ) {
        if (!design.isVisible) return
        when (design.kind) {
            TriggerDesignKind.HIDE -> Unit
            TriggerDesignKind.CUSTOM_IMAGE -> Unit
            TriggerDesignKind.CONFIGURABLE_RECTANGLE -> drawConfigurableRectangle(
                canvas = canvas,
                side = side,
                design = design,
                density = density,
                widthPx = widthPx,
                heightPx = heightPx
            )
        }
    }

    private fun drawConfigurableRectangle(
        canvas: Canvas,
        side: PanelSide,
        design: TriggerHandleDesign,
        density: Float,
        widthPx: Int,
        heightPx: Int
    ) {
        val size = dp(design.sizeDp, density)
        val halo = dp(design.haloSizeDp, density)
        val border = dp(design.borderSizeDp, density)
        val radius = dp(design.cornerRadiusDp, density)

        val top = 0f
        val bottom = heightPx.toFloat()
        if (bottom <= top) return

        val bodyRect = bodyRect(
            side = side,
            size = size,
            top = top,
            bottom = bottom,
            widthPx = widthPx,
            heightPx = heightPx
        )

        if (halo > 0f) {
            drawGlow(
                canvas = canvas,
                side = side,
                color = design.haloColor,
                top = 0f,
                bottom = heightPx.toFloat(),
                haloSize = halo,
                widthPx = widthPx
            )
        }

        if (size <= 0f && border <= 0f) return

        val radii = cornerRadii(
            side = side,
            mode = design.cornerMode,
            radius = radius,
            bounds = bodyRect
        )
        path.reset()
        path.addRoundRect(bodyRect, radii, Path.Direction.CW)

        if (border > 0f && Color.alpha(design.borderColor) > 0) {
            strokePaint.strokeWidth = border
            strokePaint.color = design.borderColor
            canvas.drawPath(path, strokePaint)
        }
        if (size > 0f && Color.alpha(design.backgroundColor) > 0) {
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = design.backgroundColor
            canvas.drawPath(path, fillPaint)
        }
    }

    private fun bodyRect(
        side: PanelSide,
        size: Float,
        top: Float,
        bottom: Float,
        widthPx: Int,
        heightPx: Int
    ): RectF {
        val maxSize = max(size, heightPx.toFloat())
        val edgeInset = min(0f, maxSize - size)
        return when (side) {
            PanelSide.LEFT -> RectF(
                edgeInset,
                top,
                (edgeInset + size).coerceAtMost(widthPx.toFloat()),
                bottom
            )
            PanelSide.RIGHT -> RectF(
                (widthPx - edgeInset - size).coerceAtLeast(0f),
                top,
                widthPx - edgeInset,
                bottom
            )
            PanelSide.BOTTOM -> RectF(
                0f,
                (heightPx - edgeInset - size).coerceAtLeast(0f),
                widthPx.toFloat(),
                heightPx - edgeInset
            )
            PanelSide.TOP -> RectF(
                0f,
                edgeInset,
                widthPx.toFloat(),
                (edgeInset + size).coerceAtMost(heightPx.toFloat())
            )
        }
    }

    /**
     * Quick Cursor draws glow with a radial gradient stretched along the edge strip,
     * extending [haloSize] * 2 from the screen edge. [widthPx] must be at least that wide
     * Touch capture windows use trigger width only; glow may clip when the strip is narrower.
     */
    private fun drawGlow(
        canvas: Canvas,
        side: PanelSide,
        color: Int,
        top: Float,
        bottom: Float,
        haloSize: Float,
        widthPx: Int
    ) {
        if (Color.alpha(color) <= 0) return

        val glowRect = when (side) {
            PanelSide.LEFT -> RectF(
                0f,
                top,
                (haloSize * 2f).coerceAtMost(widthPx.toFloat()),
                bottom
            )
            PanelSide.RIGHT -> RectF(
                (widthPx - haloSize * 2f).coerceAtLeast(0f),
                top,
                widthPx.toFloat(),
                bottom
            )
            PanelSide.BOTTOM -> RectF(
                0f,
                (bottom - haloSize * 2f).coerceAtLeast(0f),
                widthPx.toFloat(),
                bottom
            )
            PanelSide.TOP -> RectF(
                0f,
                top,
                widthPx.toFloat(),
                (top + haloSize * 2f).coerceAtMost(bottom)
            )
        }

        val centerX = glowRect.centerX()
        val centerY = glowRect.centerY()
        val gradientRadius = max(glowRect.width(), glowRect.height()) / 2f
        val scaleX = glowRect.width() / glowRect.height().coerceAtLeast(1f)

        val matrix = Matrix().apply {
            setScale(scaleX, 1f, centerX, centerY)
            postTranslate(
                when (side) {
                    PanelSide.LEFT -> -glowRect.width() / 2f
                    PanelSide.RIGHT -> glowRect.width() / 2f
                    PanelSide.BOTTOM, PanelSide.TOP -> 0f
                },
                when (side) {
                    PanelSide.BOTTOM -> -glowRect.height() / 2f
                    PanelSide.TOP -> glowRect.height() / 2f
                    else -> 0f
                }
            )
        }

        val gradient = RadialGradient(
            centerX,
            centerY,
            gradientRadius,
            color,
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        ).apply {
            setLocalMatrix(matrix)
        }

        glowPaint.shader = gradient
        canvas.drawRect(glowRect, glowPaint)
        glowPaint.shader = null
    }

    /**
     * Corner radii follow Quick Cursor semantics:
     * - ALL: every corner uses [radius]
     * - OUTER: only the inner edge (away from the screen bezel) is rounded
     */
    private fun cornerRadii(
        side: PanelSide,
        mode: TriggerCornerMode,
        radius: Float,
        bounds: RectF
    ): FloatArray {
        val capped = radius.coerceAtMost(min(bounds.width(), bounds.height()) / 2f)
        return when (mode) {
            TriggerCornerMode.ALL -> floatArrayOf(
                capped, capped, capped, capped, capped, capped, capped, capped
            )
            TriggerCornerMode.OUTER -> when (side) {
                PanelSide.LEFT -> floatArrayOf(
                    0f, 0f, capped, capped, capped, capped, 0f, 0f
                )
                PanelSide.RIGHT -> floatArrayOf(
                    capped, capped, 0f, 0f, 0f, 0f, capped, capped
                )
                PanelSide.BOTTOM -> floatArrayOf(
                    capped, capped, capped, capped, 0f, 0f, 0f, 0f
                )
                PanelSide.TOP -> floatArrayOf(
                    0f, 0f, 0f, 0f, capped, capped, capped, capped
                )
            }
        }
    }

    private fun dp(value: Float, density: Float): Float = value * density
}
