package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.slideindex.app.overlay.panels.R
import kotlin.math.roundToInt

object RingerModeIconRenderer {
    private var normal: Drawable? = null
    private var vibrate: Drawable? = null
    private var silent: Drawable? = null

    fun iconResFor(ringerMode: Int): Int = when (ringerMode) {
        AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_ringer_vibrate
        AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_ringer_silent
        else -> R.drawable.ic_ringer_normal
    }

    fun draw(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sizePx: Float,
        ringerMode: Int,
        alphaScale: Float
    ) {
        ensure(context)
        val drawable = when (ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> vibrate
            AudioManager.RINGER_MODE_SILENT -> silent
            else -> normal
        } ?: return
        val alpha = (235f * alphaScale.coerceIn(0f, 1f)).roundToInt()
        DrawableCompat.setTint(drawable, Color.argb(alpha, 255, 255, 255))
        val half = (sizePx / 2f).roundToInt()
        val left = (cx - half).roundToInt()
        val top = (cy - half).roundToInt()
        drawable.setBounds(left, top, left + half * 2, top + half * 2)
        drawable.draw(canvas)
    }

    private fun ensure(context: Context) {
        if (normal != null) return
        val appContext = context.applicationContext
        normal = load(appContext, R.drawable.ic_ringer_normal)
        vibrate = load(appContext, R.drawable.ic_ringer_vibrate)
        silent = load(appContext, R.drawable.ic_ringer_silent)
    }

    private fun load(context: Context, resId: Int): Drawable? =
        ContextCompat.getDrawable(context, resId)?.constantState?.newDrawable()?.mutate()
}
