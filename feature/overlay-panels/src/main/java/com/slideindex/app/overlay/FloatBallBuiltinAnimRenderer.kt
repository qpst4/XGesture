package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import com.slideindex.app.settings.FloatBallStyleType
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lightweight built-in float-ball animations drawn on Canvas (~20fps), no AVD/Lottie.
 */
object FloatBallBuiltinAnimRenderer {

    private val planePath = Path().apply {
        moveTo(24f, 8f)
        lineTo(38f, 24f)
        lineTo(28f, 24f)
        lineTo(32f, 38f)
        lineTo(24f, 30f)
        lineTo(16f, 38f)
        lineTo(20f, 24f)
        lineTo(10f, 24f)
        close()
    }
    private val planeShadowPath = Path().apply {
        moveTo(24f, 30f)
        lineTo(32f, 38f)
        lineTo(28f, 24f)
        close()
    }

    fun draw(
        canvas: Canvas,
        sizePx: Int,
        alpha: Float,
        styleType: FloatBallStyleType,
        timeMs: Long
    ) {
        if (sizePx <= 0) return
        when (styleType) {
            FloatBallStyleType.ANIMATED_PLANE -> drawPlane(canvas, sizePx, alpha, timeMs)
            FloatBallStyleType.ANIMATED_PULSE -> drawPulse(canvas, sizePx, alpha, timeMs)
            FloatBallStyleType.ANIMATED_ORBIT -> drawOrbit(canvas, sizePx, alpha, timeMs)
            else -> Unit
        }
    }

    private fun drawPlane(canvas: Canvas, sizePx: Int, alpha: Float, timeMs: Long) {
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val scale = sizePx / 48f * 0.88f
        val wobble = sin(timeMs / 700.0).toFloat() * 18f
        val bob = sin(timeMs / 900.0).toFloat() * sizePx * 0.02f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = withAlpha(0xFFFFFFFF.toInt(), alpha)
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 1.6f * scale
            color = withAlpha(0xFF1A237E.toInt(), alpha)
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = withAlpha(0x331A237E.toInt(), alpha)
        }

        canvas.withSave {
            translate(cx, cy + bob)
            scale(scale, scale)
            rotate(wobble)
            translate(-24f, -24f)
            drawPath(planePath, fillPaint)
            drawPath(planeShadowPath, shadowPaint)
            drawPath(planePath, strokePaint)
        }
    }

    private fun drawPulse(canvas: Canvas, sizePx: Int, alpha: Float, timeMs: Long) {
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val base = sizePx * 0.18f
        val pulse = 0.88f + 0.12f * sin(timeMs / 800.0).toFloat()

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(0xFF1565C0.toInt(), alpha)
        }
        canvas.drawCircle(cx, cy, base, corePaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.055f
            color = withAlpha(0xFF42A5F5.toInt(), alpha * 0.85f)
        }
        canvas.drawCircle(cx, cy, sizePx * 0.34f * pulse, ringPaint)
    }

    private fun drawOrbit(canvas: Canvas, sizePx: Int, alpha: Float, timeMs: Long) {
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val orbitR = sizePx * 0.30f
        val angleRad = Math.toRadians((timeMs / 12.0) % 360.0)
        val dotX = cx + orbitR * cos(angleRad).toFloat()
        val dotY = cy + orbitR * sin(angleRad).toFloat()

        val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(0xFF1565C0.toInt(), alpha * 0.55f)
        }
        canvas.drawCircle(cx, cy, sizePx * 0.07f, hubPaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(0xFFFFA726.toInt(), alpha)
        }
        canvas.drawCircle(dotX, dotY, sizePx * 0.09f, dotPaint)
    }

    fun snapshot(
        sizePx: Int,
        alpha: Float,
        styleType: FloatBallStyleType,
        timeMs: Long = 0L
    ): android.graphics.Bitmap? {
        if (sizePx <= 0) return null
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val clip = Path().apply {
            addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW)
        }
        canvas.withClip(clip) {
            draw(this, sizePx, alpha, styleType, timeMs)
        }
        return bitmap
    }

    private fun withAlpha(rgb: Int, opacity: Float): Int {
        val a = (opacity.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (rgb and 0x00FFFFFF)
    }
}
