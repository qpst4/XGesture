package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared default float-ball: compact glassy orb with theme tint.
 */
object FloatBallDefaultVisual {

    const val BODY_SCALE = 0.72f

    fun draw(canvas: Canvas, sizePx: Int, colorArgb: Int, opacity: Float) {
        if (sizePx <= 0) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val radius = min(sizePx, sizePx) / 2f * BODY_SCALE
        val safeOpacity = opacity.coerceIn(0.3f, 1f)

        val inner = shadeArgb(colorArgb, safeOpacity, lighten = 0.42f)
        val mid = shadeArgb(colorArgb, safeOpacity, lighten = 0.08f)
        val outer = shadeArgb(colorArgb, safeOpacity, darken = 0.32f)
        paint.shader = RadialGradient(
            cx - radius * 0.28f,
            cy - radius * 0.32f,
            radius * 1.05f,
            intArrayOf(inner, mid, outer),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null

        paint.style = Paint.Style.FILL
        paint.color = withAlpha(0xFFFFFFFF.toInt(), safeOpacity * 0.38f)
        canvas.drawCircle(cx - radius * 0.28f, cy - radius * 0.34f, radius * 0.22f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius * 0.04f
        paint.color = withAlpha(0xFFFFFFFF.toInt(), safeOpacity * 0.28f)
        canvas.drawCircle(cx, cy, radius * 0.98f, paint)
    }

    @Composable
    fun Content(sizeDp: Dp, ballColor: Color) {
        val opacity = ballColor.alpha.coerceIn(0.3f, 1f)
        val baseRgb = ballColor.copy(alpha = 1f)
        val inner = blend(baseRgb, Color.White, 0.42f).copy(alpha = opacity)
        val mid = blend(baseRgb, Color.White, 0.08f).copy(alpha = opacity)
        val outer = blend(baseRgb, Color.Black, 0.32f).copy(alpha = opacity)

        Box(
            modifier = Modifier
                .size(sizeDp)
                .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = size.minDimension / 2f * BODY_SCALE

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(inner, mid, outer),
                        center = Offset(cx - radius * 0.28f, cy - radius * 0.34f),
                        radius = radius * 1.05f
                    ),
                    radius = radius,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.White.copy(alpha = opacity * 0.38f),
                    radius = radius * 0.22f,
                    center = Offset(cx - radius * 0.28f, cy - radius * 0.34f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = opacity * 0.28f),
                    radius = radius * 0.98f,
                    center = Offset(cx, cy),
                    style = Stroke(width = radius * 0.04f)
                )
            }
        }
    }

    private fun shadeArgb(rgb: Int, opacity: Float, lighten: Float = 0f, darken: Float = 0f): Int {
        var r = (rgb shr 16) and 0xFF
        var g = (rgb shr 8) and 0xFF
        var b = rgb and 0xFF
        if (lighten > 0f) {
            r = (r + (255 - r) * lighten).roundToInt()
            g = (g + (255 - g) * lighten).roundToInt()
            b = (b + (255 - b) * lighten).roundToInt()
        }
        if (darken > 0f) {
            r = (r * (1f - darken)).roundToInt()
            g = (g * (1f - darken)).roundToInt()
            b = (b * (1f - darken)).roundToInt()
        }
        return withAlpha((r shl 16) or (g shl 8) or b, opacity)
    }

    private fun withAlpha(rgb: Int, opacity: Float): Int {
        val a = (opacity * 255f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (rgb and 0x00FFFFFF)
    }

    private fun blend(from: Color, toward: Color, fraction: Float): Color {
        val t = fraction.coerceIn(0f, 1f)
        return Color(
            red = from.red + (toward.red - from.red) * t,
            green = from.green + (toward.green - from.green) * t,
            blue = from.blue + (toward.blue - from.blue) * t,
            alpha = from.alpha
        )
    }
}
