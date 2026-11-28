package com.slideindex.app.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.TriggerZonePreviewRenderer
import com.slideindex.app.settings.AppSettings

private val triggerPairColors = listOf(Color(0xFF7E57C2), Color(0xFF26A69A), Color(0xFFFF7043))

internal fun triggerPairColor(index: Int): Color = triggerPairColors[index % triggerPairColors.size]

private val LocalTriggerLayoutPreview = compositionLocalOf<TriggerLayoutDrawable?> { null }

@Composable
internal fun TriggerLayoutPreviewHost(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val decor = LocalActivity.current?.window?.decorView
    val drawable = remember(decor, enabled) {
        decor?.takeIf { enabled }?.let(::TriggerLayoutDrawable)
    }
    if (decor != null && drawable != null) {
        DisposableEffect(decor, drawable) {
            val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> drawable.resize() }
            decor.overlay.add(drawable)
            decor.addOnLayoutChangeListener(listener)
            drawable.resize()
            onDispose {
                decor.removeOnLayoutChangeListener(listener)
                decor.overlay.remove(drawable)
            }
        }
    }
    CompositionLocalProvider(LocalTriggerLayoutPreview provides drawable, content = content)
}

@Composable
internal fun TriggerLayoutPreview(
    settings: AppSettings,
    draft: TriggerLayoutPreviewDraft? = null,
) {
    val drawable = LocalTriggerLayoutPreview.current
    SideEffect {
        drawable?.update(settings.takeIf { it.triggerLayoutPreviewEnabled }, draft)
    }
}

private class TriggerLayoutDrawable(private val decor: View) : Drawable() {
    private val zone = RectF()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private var labels = emptyList<Pair<TriggerLayoutPreviewBand, RectF>>()
    private var settings: AppSettings? = null
    private var draft: TriggerLayoutPreviewDraft? = null
    private var bands = emptyList<TriggerLayoutPreviewBand>()

    fun update(settings: AppSettings?, draft: TriggerLayoutPreviewDraft?) {
        if (this.settings == settings && this.draft == draft) return
        this.settings = settings
        this.draft = draft
        resize()
    }

    fun resize() {
        setBounds(0, 0, decor.width, decor.height)
        val density = decor.resources.displayMetrics.density
        bands = settings?.let {
            triggerLayoutPreviewBands(it, bounds.width(), bounds.height(), density, draft)
        }.orEmpty()
        positionLabels(density)
        invalidateSelf()
    }

    private fun positionLabels(density: Float) {
        textPaint.textSize = 11f * density
        val height = 16f * density
        val gap = 2f * density
        labels = buildList {
            for (band in bands) {
                val width = maxOf(height, textPaint.measureText(band.configurationNumber.toString()) + 6f * density)
                val x = ((band.left + band.right - width) / 2f).coerceIn(gap, maxOf(gap, bounds.width() - width - gap))
                val y = ((band.top + band.bottom - height) / 2f).coerceIn(gap, maxOf(gap, bounds.height() - height - gap))
                val label = RectF(x, y, x + width, y + height)
                val (stepX, stepY) = when (band.side) {
                    PanelSide.LEFT -> width + gap to 0f
                    PanelSide.RIGHT -> -width - gap to 0f
                    PanelSide.TOP -> 0f to height + gap
                    PanelSide.BOTTOM -> 0f to -height - gap
                }
                // Coincident ranges keep their original outline. Move only their number badges inward.
                while (any { (_, other) -> RectF.intersects(label, other) }) {
                    label.offset(stepX, stepY)
                }
                add(band to label)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (settings == null) return
        val density = decor.resources.displayMetrics.density
        for (band in bands) {
            zone.set(band.left, band.top, band.right, band.bottom)
            TriggerZonePreviewRenderer.drawLayoutBounds(canvas, zone, density, triggerPairColor(band.colorIndex).toArgb())
        }
        val radius = 5f * density
        val baselineOffset = (textPaint.ascent() + textPaint.descent()) / 2f
        for ((band, label) in labels) {
            labelPaint.style = Paint.Style.FILL
            labelPaint.color = android.graphics.Color.rgb(22, 29, 39)
            canvas.drawRoundRect(label, radius, radius, labelPaint)
            labelPaint.style = Paint.Style.STROKE
            labelPaint.strokeWidth = density
            labelPaint.color = triggerPairColor(band.colorIndex).toArgb()
            canvas.drawRoundRect(label, radius, radius, labelPaint)
            canvas.drawText(band.configurationNumber.toString(), label.centerX(), label.centerY() - baselineOffset, textPaint)
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
