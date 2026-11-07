package com.slideindex.app.overlay

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.core.graphics.drawable.toDrawable
import com.slideindex.app.settings.QuickLauncherDisplaySettings

/**
 * Builds clip paths for quick-launcher icon shapes.
 * Adaptive uses the system [AdaptiveIconDrawable] mask when available.
 */
object QuickLauncherIconMask {
    private val tmpUnitBounds = RectF(0f, 0f, 1f, 1f)
    private val tmpMatrix = Matrix()
    private var cachedAdaptiveUnitPath: Path? = null

    fun pathFor(
        shape: Int,
        bounds: RectF,
        out: Path
    ): Path {
        out.rewind()
        when (QuickLauncherDisplaySettings.coerceIconShape(shape)) {
            QuickLauncherDisplaySettings.ICON_SHAPE_CIRCLE -> {
                out.addOval(bounds, Path.Direction.CW)
            }
            QuickLauncherDisplaySettings.ICON_SHAPE_ADAPTIVE -> {
                val unit = adaptiveUnitPath()
                if (unit != null) {
                    out.set(unit)
                    tmpMatrix.reset()
                    tmpMatrix.setRectToRect(tmpUnitBounds, bounds, Matrix.ScaleToFit.FILL)
                    out.transform(tmpMatrix)
                } else {
                    val radius = bounds.width() * 0.30f
                    out.addRoundRect(bounds, radius, radius, Path.Direction.CW)
                }
            }
            else -> {
                out.addRect(bounds, Path.Direction.CW)
            }
        }
        return out
    }

    private fun adaptiveUnitPath(): Path? {
        cachedAdaptiveUnitPath?.let { return it }
        return runCatching {
            val drawable = AdaptiveIconDrawable(
                Color.BLACK.toDrawable(),
                Color.BLACK.toDrawable()
            )
            val viewport = 100
            drawable.setBounds(0, 0, viewport, viewport)
            val mask = Path(drawable.iconMask)
            val maskBounds = RectF()
            mask.computeBounds(maskBounds, true)
            if (maskBounds.isEmpty) return@runCatching null
            val matrix = Matrix()
            matrix.setRectToRect(maskBounds, tmpUnitBounds, Matrix.ScaleToFit.FILL)
            mask.transform(matrix)
            mask.also { cachedAdaptiveUnitPath = it }
        }.getOrNull()
    }
}
