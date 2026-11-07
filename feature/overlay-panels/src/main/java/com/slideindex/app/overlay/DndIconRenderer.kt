package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.slideindex.app.overlay.panels.R
import kotlin.math.roundToInt

object DndIconRenderer {
    private var off: Drawable? = null
    private var on: Drawable? = null

    fun draw(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sizePx: Float,
        dndEnabled: Boolean,
        alphaScale: Float
    ) {
        ensure(context)
        val drawable = (if (dndEnabled) on else off) ?: return
        val alpha = (235f * alphaScale.coerceIn(0f, 1f)).roundToInt()
        DrawableCompat.setTint(drawable, Color.argb(alpha, 255, 255, 255))
        val half = (sizePx / 2f).roundToInt()
        val left = (cx - half).roundToInt()
        val top = (cy - half).roundToInt()
        drawable.setBounds(left, top, left + half * 2, top + half * 2)
        drawable.draw(canvas)
    }

    private fun ensure(context: Context) {
        if (off != null) return
        val appContext = context.applicationContext
        off = load(appContext, R.drawable.ic_dnd_off)
        on = load(appContext, R.drawable.ic_dnd_on)
    }

    private fun load(context: Context, resId: Int): Drawable? =
        ContextCompat.getDrawable(context, resId)?.constantState?.newDrawable()?.mutate()
}
